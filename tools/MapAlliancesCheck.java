import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.struct.*;
import arc.util.*;
import arc.math.geom.Vec2;
import java.lang.reflect.*;
import java.nio.file.Files;
import mindustry.*;
import mindustry.ai.types.CommandAI;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.input.InputHandler;
import mindustry.net.Net;
import mindustry.world.*;
import mindustry.world.blocks.defense.turrets.*;

/** Diplomacy against real native unit, building, bullet, splash, AI and save code. */
public class MapAlliancesCheck{
    static Class<?> alliances, scopeClass;
    static Method setAlly, allied, enter, exit, refresh;
    static void check(boolean value, String message){ if(!value) throw new AssertionError(message); }
    static void set(Team team, boolean value) throws Exception{ setAlly.invoke(null, Vars.state.rules, team, value); }
    static boolean allied(Team first, Team second) throws Exception{ return (boolean)allied.invoke(null, first, second); }
    static Object enter(Teamc actor) throws Exception{ return enter.invoke(null, actor); }
    static void exit(Object scope) throws Exception{ exit.invoke(null, scope); }
    static void refresh() throws Exception{ refresh.invoke(null); }
    static Unit spawn(mindustry.type.UnitType type, Team team, float x, float y){
        Unit unit = type.create(team); unit.set(x, y); unit.add(); return unit;
    }
    static void index(){
        Vars.state.teams.updateTeamStats();
        for(Team team : new Team[]{Team.sharded, Team.green, Team.blue, Team.crux, Team.get(171)}){
            team.data().tree().clear();
            for(Unit unit : Groups.unit) if(unit.team == team) team.data().tree().insert(unit);
        }
    }
    public static void main(String[] args) throws Exception{
        Core.app = new MockApplication(); Core.files = new MockFiles(); Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Core.settings = new Settings(); Core.settings.setDataDirectory(new Fi(Files.createTempDirectory("cryon-allies-check-").toFile()));
        Core.bundle = I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(Core.settings.getDataDirectory().child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(Core.settings.getDataDirectory().child("mods/unitlanuch.jar"));
        Vars.headless = true; Vars.loadLocales = false; Vars.net = new Net(null);
        Vars.platform = new mindustry.core.Platform(){};
        mindustry.core.Version.init(); Vars.init(); Vars.content.createBaseContent(); Vars.content.createModContent(); Vars.content.init();
        var mod = Vars.mods.locateMod("cryon"); check(mod != null && !mod.hasContentErrors(), "Packaged content errors");
        alliances = mod.main.getClass().getClassLoader().loadClass("cryon.Features.MapAlliances");
        scopeClass = mod.main.getClass().getClassLoader().loadClass("cryon.Features.MapAlliances$Scope");
        setAlly = alliances.getMethod("setAlly", Rules.class, Team.class, boolean.class);
        allied = alliances.getMethod("allied", Team.class, Team.class);
        enter = alliances.getMethod("enter", Teamc.class); exit = alliances.getMethod("exit", scopeClass); refresh = alliances.getMethod("refresh");
        alliances.getMethod("install").invoke(null);
        Vars.logic = new Logic(); Vars.logic.reset();
        Team pioneer = Team.get(171);
        check(pioneer.name.equals("pioneer") && java.util.Arrays.asList(Team.baseTeams).contains(pioneer), "Pioneer absent from native editor team choices");
        set(Team.green, true); set(Team.blue, true); set(pioneer, true);
        Vars.state.rules.attackMode = true; Vars.state.rules.waves = false;
        check(allied(Team.sharded, Team.green) && allied(Team.green, Team.sharded) && allied(Team.green, Team.blue), "Alliance is not symmetric/shared");
        check(!allied(Team.green, Team.crux) && !allied(Team.sharded, Team.derelict), "Enemy/derelict became an ally");
        check(Team.green.rules().rtsAi && Team.green.rules().buildAi, "Ally AI was not enabled");
        Vars.world.loadGenerator(90, 90, tiles -> {
            for(int x = 0; x < 90; x++) for(int y = 0; y < 90; y++) tiles.set(x, y, new Tile(x, y, Blocks.stone, Blocks.air, Blocks.air));
            tiles.get(15, 15).setBlock(Blocks.coreShard, Team.sharded);
            tiles.get(28, 15).setBlock(Blocks.coreShard, Team.green);
            tiles.get(15, 28).setBlock(Blocks.coreShard, Team.blue);
            tiles.get(28, 28).setBlock(Blocks.coreShard, pioneer);
            tiles.get(70, 70).setBlock(Blocks.coreShard, Team.crux);
            tiles.get(40, 40).setBlock(Blocks.duo, Team.sharded);
            tiles.get(43, 40).setBlock(Blocks.duo, Team.green);
            tiles.get(48, 40).setBlock(Blocks.duo, Team.crux);
        });
        // Exercise every available build/unit class, including custom constructors and save IDs.
        int blockCount = 0, unitCount = 0;
        for(Block block : Vars.content.blocks()) if(block.hasBuilding()){
            Building build = block.buildType.get();
            check(build.getClass().getName().startsWith("cryon.generated.AllianceEntity"), "Unadapted building " + block.name);
            blockCount++;
        }
        for(var type : Vars.content.units()) if(type.constructor != null){
            Unit unit = type.create(Team.green);
            check(unit.getClass().getName().startsWith("cryon.generated.AllianceEntity"), "Unadapted unit " + type.name);
            check(unit.classId() >= 0, "Unit lost its save ID");
            unitCount++;
        }
        Unit player = spawn(UnitTypes.flare, Team.sharded, 320, 328);
        Unit friend = spawn(UnitTypes.flare, Team.green, 328, 328);
        Unit friend2 = spawn(UnitTypes.flare, Team.blue, 336, 328);
        Unit pioneerUnit = spawn(UnitTypes.flare, pioneer, 320, 344);
        Unit enemy = spawn(UnitTypes.flare, Team.crux, 365, 328);
        index(); refresh();
        check(allied(Team.sharded, pioneer) && !pioneerUnit.targetable(Team.sharded)
            && !Vars.state.teams.canInteract(Team.sharded, pioneer) && pioneer.rules().rtsAi && pioneer.rules().buildAi,
            "Pioneer alliance did not preserve independent ownership and AI");
        check(friend.controller() instanceof CommandAI && friend.team == Team.green, "Ally lost its independent command AI/team");
        check(!friend.targetable(Team.sharded) && !player.targetable(Team.green) && enemy.targetable(Team.sharded), "Unit targetability ignores alliances");
        check(!Vars.state.teams.canInteract(Team.sharded, Team.green), "Player gained allied building control");
        check(UnitTypes.flare.create(Team.sharded).controller() instanceof CommandAI, "Player command AI was replaced");
        Player human = Player.create(); human.team(Team.sharded); human.unit(player);
        CommandAI friendAI = (CommandAI)friend.controller();
        InputHandler.commandUnits(human, new int[]{friend.id}, null, null, new Vec2(500, 500), false, true);
        check(friendAI.targetPos == null && friendAI.attackTarget == null, "Player issued a move order to an ally");
        InputHandler.unitControl(human, friend);
        check(friend.controller() == friendAI && human.unit() == player, "Player possessed an allied unit");
        human.clearUnit(); player.controller(player.type.createController(player));
        for(Team target : Team.sharded.data().coreEnemies) check(target != Team.green && target != Team.blue, "Ally core is an attack destination");
        check(Team.green.data().rtsAi != null && Team.green.data().buildAi != null, "Independent faction AI is missing");
        var originalPresent = Vars.state.teams.present; var originalActive = Vars.state.teams.active;
        Object playerScope = enter(player);
        check(Units.closestTarget(Team.sharded, player.x, player.y, 140) == enemy, "Player targeted an allied unit");
        check(Units.findEnemyTile(Team.sharded, 320, 320, 150, b -> true).team == Team.crux, "Player targeted an allied building");
        Object allyScope = enter(friend);
        check(Units.closestTarget(Team.green, friend.x, friend.y, 140) == enemy, "Ally targeted the player/another ally");
        float playerHealth = player.health, friendHealth = friend.health, enemyHealth = enemy.health;
        Damage.damage(Team.green, player.x, player.y, 120, 20, true, true, false);
        check(player.health == playerHealth && friend2.health == friend2.maxHealth, "Allied splash damaged friendly units");
        check(enemy.health < enemyHealth, "Ally no longer damages enemies");
        exit(allyScope);
        check(Units.closestTarget(Team.sharded, player.x, player.y, 140) == enemy,
            "Nested callbacks reintroduced allies into the outer targeting scope");
        check(!Vars.state.teams.present.contains(data -> data.team == Team.green || data.team == Team.blue), "Nested scope leaked ally indexes");
        exit(playerScope);
        check(Vars.state.teams.present == originalPresent && Vars.state.teams.active == originalActive, "Search scope leaked global team lists");
        var own = Vars.world.tile(40, 40).build; var friendly = Vars.world.tile(43, 40).build; var hostile = Vars.world.tile(48, 40).build;
        float ownHealth = own.health, friendlyHealth = friendly.health, hostileHealth = hostile.health;
        own.damage(Team.green, 100); friendly.damage(Team.sharded, 100); hostile.damage(Team.sharded, 10);
        check(own.health == ownHealth && friendly.health == friendlyHealth && hostile.health < hostileHealth, "Attributed building damage ignores diplomacy");
        BasicBulletType shot = new BasicBulletType(0, 30); shot.lifetime = 100; shot.collidesAir = true;
        Bullet bullet = shot.create(friend, Team.green, player.x, player.y, 0);
        check(!player.collides(bullet) && !bullet.collides(player) && !own.collide(bullet), "Friendly direct-hit collision was allowed");
        check(bullet.collides(enemy) && hostile.collide(bullet), "Enemy collisions were disabled");
        bullet.collision(player, player.x, player.y);
        check(player.health == playerHealth, "Friendly collision callback caused damage");
        bullet.remove();
        // Hitscan initialization can run from delayed fire callbacks, outside any shooter update.
        LaserBulletType laser = new LaserBulletType(3);
        laser.length = 120; laser.pierceCap = 1; laser.collidesAir = true; laser.collidesGround = false;
        float enemyBeforeLaser = enemy.health;
        Bullet beam = laser.create(friend, Team.green, player.x - 16, player.y, 0);
        check(player.health == playerHealth && friend2.health == friend2.maxHealth, "Hitscan beam damaged an ally");
        check(enemy.health < enemyBeforeLaser, "An allied unit consumed the beam's piercing limit");
        beam.remove();
        // Deferred tile damage retains the source team after the originating scope has ended.
        Damage.tileDamage(Team.green, 40, 40, 4, 50);
        for(int i = 0; i < 3; i++){ Time.delta = 1; Time.update(); }
        check(own.health == ownHealth, "Deferred allied explosion damaged a building");
        // Real native turret update must reject a stale friendly target and choose an enemy.
        var turret = (ItemTurret.ItemTurretBuild)own;
        turret.handleItem(null, Items.copper);
        turret.target = friend;
        for(int i = 0; i < 40; i++){ Time.delta = 1; Time.time++; turret.update(); }
        check(turret.target == enemy, "Player turret did not select the real enemy");
        var allyTurret = (ItemTurret.ItemTurretBuild)friendly;
        player.set(allyTurret.x + 1, allyTurret.y + 1); index();
        allyTurret.handleItem(null, Items.copper); allyTurret.target = player;
        for(int i = 0; i < 40; i++){ Time.delta = 1; Time.time++; allyTurret.update(); }
        check(allyTurret.target == enemy, "Ally turret did not reject the nearby player and select the enemy");
        // Let the native faction planner issue its own attack order, with no player command.
        Team.green.rules().rtsMinSquad = 1; Team.green.rules().rtsMaxSquad = 1; Team.green.rules().rtsMinWeight = 0;
        Team.green.rules().aiCoreSpawn = false;
        for(int i = 0; i < 260; i++){ Time.time++; Team.green.data().rtsAi.update(); }
        check(friendAI.attackTarget != null && friendAI.attackTarget.team() == Team.crux, "Ally AI did not autonomously attack an enemy");
        check(Team.green.core().items != Team.sharded.core().items && !Team.green.rules().cheat && !Team.green.rules().infiniteResources,
            "Allied resources were merged or made free");
        // Native serialized rules preserve both membership and independent AI configuration.
        Rules copied = JsonIO.read(Rules.class, JsonIO.write(Vars.state.rules));
        check(copied.tags.get("cryon-allied-teams").contains("4") && copied.teams.get(Team.green).rtsAi, "Map rules lost diplomacy/AI");
        Fi mapFile = Core.settings.getDataDirectory().child("allied-map.msav");
        StringMap tags = new StringMap(); tags.put("name", "Alliance map check"); tags.put("rules", JsonIO.write(Vars.state.rules));
        MapIO.writeMap(mapFile, new mindustry.maps.Map(mapFile, Vars.world.width(), Vars.world.height(), tags, true));
        Rules mapRules = MapIO.createMap(mapFile, true).rules();
        check(mapRules.tags.get("cryon-allied-teams").equals(copied.tags.get("cryon-allied-teams")) && mapRules.teams.get(Team.green).rtsAi,
            "Native map serialization lost alliance settings");
        check(mapRules.tags.get("cryon-allied-teams").contains("171") && mapRules.teams.get(pioneer).rtsAi, "Pioneer map alliance/AI lost");
        Fi save = Core.settings.getDataDirectory().child("allied-world.msav");
        SaveIO.write(save); Vars.logic.reset(); SaveIO.load(save);
        check(allied(Team.sharded, Team.green) && allied(Team.green, Team.blue), "Save/reload lost the alliance");
        check(allied(Team.sharded, pioneer) && Vars.world.tile(28, 28).team() == pioneer
            && Groups.unit.contains(u -> u.team == pioneer && !u.targetable(Team.sharded)), "Save/reload lost Pioneer identity/alliance");
        Unit saved = Groups.unit.find(u -> u.team == Team.green && u.type == UnitTypes.flare);
        check(saved != null && !saved.targetable(Team.sharded) && saved.controller() instanceof CommandAI, "Loaded ally lost ownership/behavior");
        check(Vars.world.tile(43, 40).build.team == Team.green && !Vars.world.tile(43, 40).build.interactable(Team.sharded), "Loaded ally building became player-controlled");
        set(Team.green, false);
        check(!allied(Team.sharded, Team.green) && saved.targetable(Team.sharded), "Removing an alliance did not restore hostility");
        check(java.util.Arrays.asList(Team.sharded.data().coreEnemies).contains(Team.green), "Removing an ally did not restore its enemy core");
        set(Team.green, true);
        for(Unit unit : Groups.unit.copy()) if(unit.team == Team.crux) unit.remove();
        Team.crux.core().tile.setBlock(Blocks.air);
        Vars.state.teams.updateTeamStats();
        Vars.state.set(mindustry.core.GameState.State.playing);
        Events.fire(Trigger.afterGameUpdate);
        check(Vars.state.gameOver, "Friendly cores prevented attack-map victory");
        // Unrelated maps use ordinary providers and ordinary hostile relations.
        Vars.logic.reset();
        check(!allied(Team.sharded, Team.green), "Alliance leaked into another map");
        check(!UnitTypes.flare.create(Team.green).getClass().getName().startsWith("cryon.generated."), "Unconfigured map was given adapted units");
        Vars.state.rules.defaultTeam = Team.get(170); set(Team.green, true);
        check(allied(Team.get(170), Team.green) && !allied(Team.sharded, Team.green), "Alliance was hardcoded to the blue player team");
        Vars.state.rules.defaultTeam = pioneer;
        check(allied(pioneer, Team.green) && !allied(Team.sharded, pioneer), "Pioneer cannot be the map player faction");
        System.out.println("MAP_ALLIANCES_PASS: " + blockCount + " building providers, " + unitCount + " unit providers; bilateral targeting, faction AI, collision, splash/deferred damage, native turret, ownership, save/reload and map isolation");
    }
}
