import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.struct.Seq;
import arc.util.*;
import java.lang.reflect.Method;
import java.nio.file.Files;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.entities.bullet.BulletType;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.net.Net;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.turrets.*;

/** Actual marker creation through update, hit and fusion, using the launcher's live range. */
public class BeaconRangeCheck{
    static Seq<?> markers;
    static Method fuse;
    static void check(boolean value, String message){ if(!value) throw new AssertionError(message); }
    static Bullet source(BulletType profile, Object owner, Team team, float x, float y){
        Bullet bullet = Bullet.create(); bullet.type = profile; bullet.team = team;
        bullet.owner = (Entityc)owner; bullet.set(10, 10);
        bullet.aimX = x; bullet.aimY = y; bullet.lifetime = profile.lifetime;
        return bullet;
    }
    static void test(BulletType profile, Turret.TurretBuild gun, float x, float y, boolean allowed, String name) throws Exception{
        for(int entry = 0; entry < 3; entry++){
            markers.clear();
            Bullet bullet = source(profile, gun, gun.team, x, y);
            if(entry == 0) profile.update(bullet);
            else if(entry == 1) profile.hit(bullet, gun.x, gun.y);
            else fuse.invoke(profile, bullet, x, y);
            check((markers.size == 1) == allowed, name + " entry=" + entry + " markers=" + markers.size);
        }
    }
    public static void main(String[] args){
        try{ run(); System.exit(0); }catch(Throwable error){ error.printStackTrace(); System.exit(1); }
    }
    static void run() throws Exception{
        Core.app = new MockApplication(); Core.files = new MockFiles();
        Core.graphics = new MockGraphics(); Core.audio = new MockAudio(); Core.settings = new Settings();
        Core.settings.setDataDirectory(new Fi(Files.createTempDirectory("cryon-beacon-range-").toFile()));
        Core.bundle = I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(Core.settings.getDataDirectory().child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(Core.settings.getDataDirectory().child("mods/unitlaunch.jar"));
        Vars.headless = true; Vars.loadLocales = false; Vars.net = new Net(null);
        Vars.platform = new mindustry.core.Platform(){};
        mindustry.core.Version.init(); Vars.init();
        Vars.content.createBaseContent(); Vars.content.createModContent(); Vars.content.init();
        var mod = Vars.mods.locateMod("cryon"); check(mod != null && !mod.hasContentErrors(), "Packaged content errors");
        Class<?> cls = mod.main.getClass().getClassLoader().loadClass("cryon.Type.BeaconBulletType");
        markers = (Seq<?>)cls.getField("markers").get(null);
        fuse = cls.getMethod("fuseAt", Bullet.class, float.class, float.class);
        Vars.logic = new Logic(); Vars.logic.reset();
        Vars.world.loadGenerator(180, 120, tiles -> {
            for(int x = 0; x < 180; x++) for(int y = 0; y < 120; y++)
                tiles.set(x, y, new Tile(x, y, Blocks.stone, Blocks.air, Blocks.air));
        });
        Vars.state.rules.defaultTeam = Team.sharded;
        Vars.state.set(mindustry.core.GameState.State.playing); Time.delta = 1f;
        PowerTurret block = (PowerTurret)Vars.content.block("cryon-beacon");
        for(Team team : new Team[]{Team.sharded, Team.crux}){
            Vars.world.tile(80, 50).setBlock(block, team);
            Turret.TurretBuild gun = (Turret.TurretBuild)Vars.world.build(80, 50);
            float r = gun.range();
            test(block.shootType, gun, gun.x, gun.y, true, "center");
            for(int side = 0; side < 4; side++){
                float dx = side == 0 ? 1 : side == 1 ? -1 : 0;
                float dy = side == 2 ? 1 : side == 3 ? -1 : 0;
                test(block.shootType, gun, gun.x + dx * r, gun.y + dy * r, true, "boundary");
                test(block.shootType, gun, gun.x + dx * (r + 0.1f), gun.y + dy * (r + 0.1f), false, "outside boundary");
            }
            test(block.shootType, gun, gun.x + r * 0.6f, gun.y + r * 0.8f, true, "diagonal boundary");
            test(block.shootType, gun, Float.NaN, gun.y, false, "NaN");
            test(block.shootType, gun, Float.POSITIVE_INFINITY, gun.y, false, "infinity");
            test(block.shootType, gun, -1f, -1f, false, "unset aim");
            // Source bullets start at (10,10); the turret center, rather than muzzle/motion, is authoritative.
            float old = block.range; block.range += 100f;
            test(block.shootType, gun, gun.x + r + 50f, gun.y, true, "live increased range");
            block.range = old;
            test(block.shootType, gun, gun.x + r + 50f, gun.y, false, "restored range");
            gun.tile.remove();
        }
        Unit unit = UnitTypes.flare.spawn(Team.sharded, 600, 500);
        markers.clear();
        fuse.invoke(block.shootType, source(block.shootType, unit, unit.team, 0, 0), unit.x + unit.range(), unit.y);
        check(markers.size == 1, "Unit launcher boundary rejected");
        markers.clear();
        fuse.invoke(block.shootType, source(block.shootType, unit, unit.team, 0, 0), unit.x + unit.range() + 1f, unit.y);
        check(markers.isEmpty(), "Unit launcher marks outside its range");
        Time.clear();
        System.out.println("BEACON_RANGE_PASS: update/hit/fusion paths, player/enemy, exact radial boundaries, live launcher range, muzzle independence, invalid aim, unit launcher");
    }
}
