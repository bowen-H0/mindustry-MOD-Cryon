import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.struct.Seq;
import arc.util.*;
import arc.util.io.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.Files;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.units.AIController;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.net.Net;
import mindustry.type.*;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.blocks.units.UnitFactory;

/** Packaged production, authoritative request/shot lifecycle, native weapon cooldown and AI. */
public class SkywatchSupportCheck{
    static ClassLoader loader;
    static Class<?> support, beaconClass;
    static Seq<?> markers;
    static Method designate;
    static BulletType beacon;
    static void check(boolean value, String message){ if(!value) throw new AssertionError(message); }
    static boolean flag(Object object, String field) throws Exception{ return object.getClass().getField(field).getBoolean(object); }
    static int owner(Object marker) throws Exception{ return marker.getClass().getField("claimedBy").getInt(marker); }
    static Object claim(Unit unit) throws Exception{ return support.getMethod("claim", Unit.class).invoke(null, unit); }
    static void mark(Team team, float x, float y) throws Exception{ mark(beacon, team, x, y); }
    static void mark(BulletType profile, Team team, float x, float y) throws Exception{
        Bullet source = Bullet.create(); source.type = profile; source.team = team; source.set(x, y);
        designate.invoke(profile, source, x, y);
    }
    static AIController ai(Unit unit) throws Exception{
        check(unit.controller().getClass().getName().equals("cryon.Type.SkywatchAI"), "Unit does not use autonomous Skywatch AI");
        return (AIController)unit.controller();
    }
    static void index(){
        Vars.state.teams.updateTeamStats();
        for(Team team : new Team[]{Team.sharded, Team.crux, Team.green}){
            team.data().tree().clear();
            for(Unit unit : Groups.unit) if(unit.team == team && !unit.dead) team.data().tree().insert(unit);
        }
    }
    static void ticks(int count){
        for(int i = 0; i < count; i++){
            Time.delta = 1f;
            for(Bullet bullet : Groups.bullet.copy()) if(bullet.isAdded()) bullet.update();
            Time.update(); Events.fire(Trigger.update);
        }
    }
    static void fire(Unit unit){
        for(var mount : unit.mounts) mount.weapon.update(unit, mount);
        for(Bullet bullet : Groups.bullet.copy()) if(bullet.isAdded()) bullet.type.update(bullet);
    }
    public static void main(String[] args){
        try{ run(); System.exit(0); }catch(Throwable error){ error.printStackTrace(); System.exit(1); }
    }
    static void run() throws Exception{
        Core.app = new MockApplication(); Core.files = new MockFiles();
        Core.graphics = new MockGraphics(); Core.audio = new MockAudio(); Core.settings = new Settings();
        Core.settings.setDataDirectory(new Fi(Files.createTempDirectory("cryon-skywatch-").toFile()));
        Core.bundle = I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(Core.settings.getDataDirectory().child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(Core.settings.getDataDirectory().child("mods/unitlaunch.jar"));
        Vars.headless = true; Vars.loadLocales = false; Vars.net = new Net(null);
        Vars.platform = new mindustry.core.Platform(){};
        mindustry.core.Version.init(); Vars.init();
        Vars.content.createBaseContent(); Vars.content.createModContent(); Vars.content.init();
        var mod = Vars.mods.locateMod("cryon"); check(mod != null && !mod.hasContentErrors(), "Packaged content errors");
        loader = mod.main.getClass().getClassLoader();
        loader.loadClass("cryon.TechTree.CryonTechTree").getMethod("load").invoke(null);
        support = loader.loadClass("cryon.Features.OrbitalSupport"); support.getMethod("install").invoke(null);
        beaconClass = loader.loadClass("cryon.Type.BeaconBulletType");
        markers = (Seq<?>)beaconClass.getField("markers").get(null);
        designate = beaconClass.getMethod("fuseAt", Bullet.class, float.class, float.class);
        beacon = ((PowerTurret)Vars.content.block("cryon-beacon")).shootType;
        Timer.instance().clear(); Time.clear(); Time.setDeltaProvider(() -> 1f);
        Vars.logic = new Logic(); Vars.logic.reset();
        Vars.world.loadGenerator(180, 100, tiles -> {
            for(int x = 0; x < 180; x++) for(int y = 0; y < 100; y++)
                tiles.set(x, y, new Tile(x, y, Blocks.stone, Blocks.air, Blocks.air));
        });
        Vars.state.rules.defaultTeam = Team.sharded; Vars.state.rules.canGameOver = false;
        Vars.state.rules.unitCap = 100; Vars.state.set(mindustry.core.GameState.State.playing); Time.delta = 1f;
        UnitType type = Vars.content.unit("cryon-skywatch");
        UnitFactory pad = (UnitFactory)Vars.content.block("cryon-orbital-deployment-pad");
        check(pad != null && pad.size == 4 && type != null && type.range > ((PowerTurret)Vars.content.block("cryon-beacon")).range,
                "Missing support pad or longer-range orbital unit");
        check(!type.playerControllable && !type.logicControllable && !type.allowChangeCommands,
                "Skywatch exposes a player control entry point");
        check(type.techNode != null && type.techNode.parent.content == pad && pad.techNode != null, "Missing research branch");
        Vars.world.tile(10, 10).setBlock(pad, Team.sharded);
        UnitFactory.UnitFactoryBuild factory = (UnitFactory.UnitFactoryBuild)Vars.world.build(10, 10);
        int plan = pad.plans.indexOf(value -> value.unit == type); check(plan >= 0, "No Skywatch production plan");
        factory.currentPlan = plan;
        for(ItemStack cost : pad.plans.get(plan).requirements) factory.items.add(cost.item, cost.amount);
        factory.progress = pad.plans.get(plan).time; factory.updateTile();
        check(factory.payload != null && factory.payload.unit.type == type && factory.items.total() == 0, "Native factory production failed");
        Unit enemy = UnitTypes.dagger.spawn(Team.crux, 500, 300); enemy.health = enemy.maxHealth = 10000f; index();
        Unit idle = type.spawn(Team.sharded, 400, 300);
        AIController idleAI = ai(idle);
        for(int i = 0; i < 400; i++){
            idleAI.updateUnit(); fire(idle); ticks(1);
            check(!idle.isShooting, "Unmarked nearby enemy triggers autonomous attack");
        }
        check(enemy.health == 10000f && markers.isEmpty(), "Idle Skywatch attacked an unmarked enemy");
        idle.aim(enemy.x, enemy.y); idle.controlWeapons(true, true); idle.mounts[0].reload = 0f;
        fire(idle);
        check(enemy.health == 10000f && markers.isEmpty(), "Unmarked shot bypasses projectile validation");
        idle.remove(); ticks(20);
        mark(Team.sharded, 500, 300);
        check(markers.size == 1 && flag(markers.first(), "supportRequired"), "Player beacon did not create a request");
        ticks(120);
        check(enemy.health == 10000f && markers.size == 1, "Player beacon damaged a target without Skywatch");
        mark(Team.sharded, 500, 300); check(markers.size == 1, "Repeated marks create duplicate pending requests");
        Unit watch = type.spawn(Team.sharded, 400, 300), second = type.spawn(Team.sharded, 1000, 700);
        AIController pilot = ai(watch); ai(second); index();
        Object request = claim(watch);
        check(request != null && owner(request) == watch.id && claim(second) == null, "Two units reserved the same request");
        watch.mounts[0].reload = 0f; pilot.updateUnit(); fire(watch);
        check(markers.isEmpty() && enemy.health < 10000f, "Unit did not execute an immediate direct attack / created a marker");
        float reload = watch.mounts[0].reload; check(reload > 100f, "Native firing cooldown not applied");
        float afterHit = enemy.health;
        ticks(120); check(enemy.health == afterHit && markers.isEmpty(), "Beam causes another delayed or despawn strike");
        mark(Team.sharded, watch.x, watch.y); watch.mounts[0].reload = 0f;
        pilot.updateUnit(); fire(watch);
        check(markers.isEmpty(), "Near-unit designation was moved by minimum aim distance");
        ticks(120);
        // One executor cannot process a burst of marks faster than its native weapon reload.
        mark(Team.sharded, 500, 300); mark(Team.sharded, 550, 350); mark(Team.sharded, 600, 300);
        watch.mounts[0].reload = 0f; pilot.updateUnit(); fire(watch);
        check(markers.size == 2, "One shot consumed multiple beacon requests");
        for(int i = 0; i < 179; i++){ ticks(1); pilot.updateUnit(); fire(watch); }
        check(markers.size == 2, "Busy executor exceeded its 3-second firing cadence");
        ticks(1); pilot.updateUnit(); fire(watch);
        check(markers.size == 1, "Executor did not service the next request when reloaded");
        markers.clear();
        float unmarkedHealth = enemy.health;
        for(int i = 0; i < 200; i++){ pilot.updateUnit(); fire(watch); ticks(1); }
        check(!watch.isShooting && enemy.health == unmarkedHealth && markers.isEmpty(),
                "Finishing/cancelling requests resumes autonomous enemy attacks");
        // A remote request takes priority over a closer ordinary enemy and requires approaching.
        mark(Team.sharded, 900, 300); watch.set(400, 300); watch.vel.setZero(); pilot.updateUnit();
        check(watch.aimX == 900f && !watch.isShooting && watch.vel.len() > 0f,
                "AI ignores distant request or fires outside range: aim=" + watch.aimX + " shoot=" + watch.isShooting
                        + " speed=" + watch.vel.len() + " range=" + watch.range() + " nativeSpeed=" + watch.speed() + " dt=" + Time.delta);
        watch.set(700, 300); watch.mounts[0].reload = reload; pilot.updateUnit(); fire(watch);
        check(flag(markers.first(), "supportRequired"), "Unit ignored cooldown when supporting another request");
        watch.mounts[0].reload = 0f; pilot.updateUnit(); fire(watch);
        check(markers.isEmpty(), "Unit cannot fire on an empty marked position without creating markers");
        ticks(120);
        Unit friendly = UnitTypes.dagger.spawn(Team.sharded, 900, 600); friendly.health = friendly.maxHealth = 10000f; index();
        mark(Team.crux, 900, 600);
        check(!flag(markers.first(), "supportRequired"), "Enemy beacon incorrectly requires support");
        ticks(120); check(friendly.health < 10000f, "Enemy beacon lost automatic orbital strike");
        // Destroyed support units release reservations; surviving units can take over.
        mark(Team.sharded, 900, 650); request = claim(watch); check(request != null, "No pending request to reserve");
        watch.kill(); Events.fire(Trigger.update);
        check(owner(request) == -1 && claim(second) == request, "Dead Skywatch keeps its reservation");
        second.controller(new mindustry.ai.types.FlyingAI()); Events.fire(Trigger.update);
        check(owner(request) == -1, "Leaving support command keeps request reserved");
        type.update(second); ai(second);
        ticks(1201); check(markers.isEmpty(), "Unanswered request never expires");
        // An online human team is a player team even on a headless server with no Vars.player.
        Player human = Player.create(); human.team(Team.green); Groups.player.add(human);
        check((Boolean)support.getMethod("playerTeam", Team.class).invoke(null, Team.green), "Server ignores online player teams");
        Groups.player.remove(human);
        Vars.state.rules.defaultTeam = Team.green;
        check((Boolean)support.getMethod("playerTeam", Team.class).invoke(null, Team.green)
                && !(Boolean)support.getMethod("playerTeam", Team.class).invoke(null, Team.crux), "Player team is hardcoded");
        Vars.state.rules.defaultTeam = Team.sharded;
        // Native damage remains friendly/alliance safe for designated Skywatch strikes.
        Unit ally = UnitTypes.dagger.spawn(Team.green, 1100, 600);
        Unit victim = UnitTypes.dagger.spawn(Team.crux, 1100, 600);
        ally.health = ally.maxHealth = victim.health = victim.maxHealth = 10000f;
        Vars.state.rules.tags.put("cryon-allied-teams", String.valueOf(Team.green.id)); index();
        mark(Team.sharded, 1100, 600); claim(second);
        type.weapons.first().bullet.create(second, Team.sharded, 900, 600, 0f, -1f, 1f, 1f, null, null, 1100, 600);
        check(ally.health == 10000f && victim.health < 10000f, "Support column damages allied units");
        check(markers.isEmpty(), "Designated shot created another marker");
        BulletType fused = beacon.copy(); beaconClass.getField("fusedTargeting").setBoolean(fused, true);
        mark(fused, Team.sharded, 1100f, 600f);
        check(!flag(markers.first(), "supportRequired"), "Standalone beacon change leaked into fused weapon behavior");
        ticks(120);
        mark(Team.crux, 900, 600); float hp = friendly.health;
        Events.fire(new ResetEvent()); ticks(120);
        check(markers.isEmpty() && friendly.health == hp, "World reset leaves a stale strike callback");
        Player driver = Player.create(); driver.team(Team.sharded);
        Unit original = UnitTypes.dagger.spawn(Team.sharded, 700, 600); driver.unit(original);
        mindustry.input.InputHandler.unitControl(driver, second);
        check(driver.unit() == original && !second.playerControllable() && !second.isCommandable(),
                "Native possession/control validation accepts Skywatch");
        check(!type.allowCommand(second, mindustry.ai.UnitCommand.moveCommand), "RTS move command is allowed");
        driver.unit(second); type.update(second);
        check(driver.unit() != second && !second.isPlayer(), "Forced possession or old player save retains control");
        second.controller(new mindustry.ai.types.LogicAI()); type.update(second); ai(second);
        second.controller(new mindustry.ai.types.CommandAI()); type.update(second); ai(second);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); second.write(Writes.get(new DataOutputStream(bytes)));
        Unit restored = (Unit)EntityMapping.map(second.classId()).get();
        restored.read(Reads.get(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        check(restored.type == type && !restored.checkTarget(true, true) && !restored.playerControllable()
                && !restored.isCommandable(), "Saved Skywatch loses orbital/autonomous restrictions");
        System.out.println("SKYWATCH_CHECK_PASS: production/research, no unsupported player strike, real unit firing/cooldown, priority/range, enemy legacy strike, reservations, command change/death, expiry/reset, server teams, serialization");
    }
}
