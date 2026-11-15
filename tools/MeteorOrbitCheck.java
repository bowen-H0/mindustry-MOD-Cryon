import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.*;
import java.lang.reflect.Method;
import java.nio.file.Files;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.entities.bullet.*;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.net.Net;
import mindustry.type.Item;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.turrets.*;

/** Packaged Meteor targeting, guidance, orbital impact and air-only splash. */
public class MeteorOrbitCheck{
    static void check(boolean value, String message){ if(!value) throw new AssertionError(message); }
    public static void main(String[] args){
        try{ run(); System.exit(0); }catch(Throwable error){ error.printStackTrace(); System.exit(1); }
    }
    static void index(){
        Vars.state.teams.updateTeamStats();
        for(Team team : new Team[]{Team.sharded, Team.crux}){
            team.data().tree().clear();
            for(Unit unit : Groups.unit) if(unit.team == team) team.data().tree().insert(unit);
        }
    }
    static void run() throws Exception{
        Core.app = new MockApplication(); Core.files = new MockFiles();
        Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Core.settings = new Settings();
        Core.settings.setDataDirectory(new Fi(Files.createTempDirectory("cryon-meteor-").toFile()));
        Core.bundle = I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(Core.settings.getDataDirectory().child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(Core.settings.getDataDirectory().child("mods/unitlaunch.jar"));
        Vars.headless = true; Vars.loadLocales = false; Vars.net = new Net(null);
        Vars.platform = new mindustry.core.Platform(){};
        mindustry.core.Version.init(); Vars.init();
        Vars.content.createBaseContent(); Vars.content.createModContent(); Vars.content.init();
        var mod = Vars.mods.locateMod("cryon");
        check(mod != null && !mod.hasContentErrors(), "Packaged content errors");
        mod.main.getClass().getClassLoader().loadClass("cryon.Features.MapAlliances").getMethod("install").invoke(null);
        Vars.logic = new Logic(); Vars.logic.reset();
        Vars.world.loadGenerator(100, 100, tiles -> {
            for(int x = 0; x < 100; x++) for(int y = 0; y < 100; y++)
                tiles.set(x, y, new Tile(x, y, Blocks.stone, Blocks.air, Blocks.air));
        });
        Vars.state.set(mindustry.core.GameState.State.playing);
        Vars.state.rules.canGameOver = false; Vars.state.rules.unitCap = 100; Time.delta = 1f;
        ItemTurret meteor = (ItemTurret)Vars.content.block("cryon-meteor");
        Item alloy = Vars.content.item("cryon-farstar-alloy");
        BulletType titanium = meteor.ammoTypes.get(Items.titanium), enhanced = meteor.ammoTypes.get(alloy);
        check(titanium != null && enhanced != null, "Missing Meteor ammo");
        check(enhanced.damage > titanium.damage && enhanced.splashDamage > titanium.splashDamage
            && enhanced.homingPower > titanium.homingPower && enhanced.speed > titanium.speed
            && enhanced.status == StatusEffects.freezing, "Farstar upgrade missing");
        Vars.world.tile(30, 37).setBlock(meteor, Team.sharded);
        ItemTurret.ItemTurretBuild gun = (ItemTurret.ItemTurretBuild)Vars.world.build(30, 37);
        Unit orbit = Vars.content.unit("cryon-startrail").spawn(Team.crux, 400, 300);
        Method find = gun.getClass().getDeclaredMethod("findTarget"); find.setAccessible(true);
        Method valid = gun.getClass().getDeclaredMethod("validateTarget"); valid.setAccessible(true);
        for(Item ammo : new Item[]{Items.titanium, alloy}){
            gun.ammo.clear(); gun.totalAmmo = 0;
            check(gun.acceptItem(null, ammo), "Ammo not accepted: " + ammo.name);
            gun.handleStack(ammo, 4, null); index(); find.invoke(gun);
            check(gun.target == orbit && (boolean)valid.invoke(gun), "Cannot acquire/retain orbital target");
            BulletType type = meteor.ammoTypes.get(ammo);
            float weave = type.weaveMag; type.weaveMag = 0f;
            Bullet guided = type.create(gun, Team.sharded, 330, 270, 0f);
            guided.time = 20f; type.update(guided);
            check(guided.vel.angle() > 0.1f && guided.vel.angle() < 90f, "Missile does not home toward orbit");
            type.weaveMag = weave; guided.remove();
            Unit air = UnitTypes.flare.spawn(Team.crux, 400, 300);
            Unit ground = UnitTypes.dagger.spawn(Team.crux, 400, 300);
            Unit ally = Vars.content.unit("cryon-startrail").spawn(Team.sharded, 400, 300);
            for(Unit unit : new Unit[]{orbit, air, ground, ally}) unit.health = unit.maxHealth = 10000f;
            index();
            Bullet impact = type.create(gun, Team.sharded, 400, 300, 0f);
            check(impact.collides(orbit) && impact.collides(air) && !impact.collides(ground)
                && !impact.collides(ally), "Wrong missile collision layers");
            float before = orbit.health;
            impact.collision(orbit, orbit.x, orbit.y);
            check(orbit.health < before, "Orbital impact dealt no damage");
            check(air.health < 10000f && ground.health == 10000f && ally.health == 10000f,
                "Splash must hit air while excluding ground and allies");
            if(ammo == alloy) check(orbit.hasEffect(StatusEffects.freezing), "Orbital target not frozen");
            check(!orbit.checkTarget(true, true) && !orbit.targetable(Team.sharded), "Orbital scope leaked");
            air.remove(); ground.remove(); ally.remove();
        }
        gun.tile.remove();
        Vars.world.tile(30, 37).setBlock(Blocks.scatter, Team.sharded);
        ItemTurret.ItemTurretBuild ordinary = (ItemTurret.ItemTurretBuild)Vars.world.build(30, 37);
        ordinary.handleStack(Items.lead, 5, null); index();
        Method nativeFind = Turret.TurretBuild.class.getDeclaredMethod("findTarget"); nativeFind.setAccessible(true);
        nativeFind.invoke(ordinary);
        check(ordinary.target == null, "Ordinary AA gained orbital targeting");
        System.out.println("METEOR_ORBIT_PASS: both ammo inputs, orbital targeting/validation, guidance, impact, air-only splash, freezing, normal AA isolation");
    }
}
