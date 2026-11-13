import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.*;
import java.nio.file.Files;
import mindustry.*;
import mindustry.async.AsyncProcess;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.net.Net;
import mindustry.type.UnitType;
import mindustry.world.Tile;

/** Uses the real native solver to check orbital-only separation and snapshot lifecycle. */
public class OrbitalCollisionCheck{
    static void check(boolean value, String message){ if(!value) throw new AssertionError(message); }
    static void step(AsyncProcess physics, int ticks){
        for(int i = 0; i < ticks; i++){
            physics.begin(); if(physics.shouldProcess()) physics.process(); physics.end();
        }
    }
    static void clear(){ for(Unit unit : Groups.unit.copy()) unit.remove(); }
    public static void main(String[] args){
        try{ run(); System.exit(0); }catch(Throwable error){ error.printStackTrace(); System.exit(1); }
    }
    static void run() throws Exception{
        Core.app = new MockApplication(); Core.files = new MockFiles();
        Core.graphics = new MockGraphics(); Core.audio = new MockAudio(); Core.settings = new Settings();
        Core.settings.setDataDirectory(new Fi(Files.createTempDirectory("cryon-orbital-collision-").toFile()));
        Core.bundle = I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(Core.settings.getDataDirectory().child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(Core.settings.getDataDirectory().child("mods/unitlaunch.jar"));
        Vars.headless = true; Vars.loadLocales = false; Vars.net = new Net(null);
        Vars.platform = new mindustry.core.Platform(){};
        mindustry.core.Version.init(); Vars.init();
        Vars.content.createBaseContent(); Vars.content.createModContent(); Vars.content.init();
        var mod = Vars.mods.locateMod("cryon"); check(mod != null && !mod.hasContentErrors(), "Packaged content errors");
        Class<?> type = mod.main.getClass().getClassLoader().loadClass("cryon.Features.OrbitalPhysics");
        int count = Vars.asyncCore.processes.size;
        type.getMethod("install").invoke(null); type.getMethod("install").invoke(null);
        check(Vars.asyncCore.processes.size == count + 1, "Physics process missing or installed twice");
        AsyncProcess physics = Vars.asyncCore.processes.peek();
        Vars.logic = new Logic(); Vars.logic.reset();
        Vars.world.loadGenerator(120, 100, tiles -> {
            for(int x = 0; x < 120; x++) for(int y = 0; y < 100; y++)
                tiles.set(x, y, new Tile(x, y, Blocks.stone, Blocks.air, Blocks.air));
        });
        Vars.state.set(mindustry.core.GameState.State.playing);
        Vars.state.rules.unitCap = 100; Vars.state.rules.canGameOver = false; Time.delta = 1f;
        UnitType star = Vars.content.unit("cryon-startrail"), watch = Vars.content.unit("cryon-skywatch");
        Unit a = star.spawn(Team.sharded, 400, 300), b = watch.spawn(Team.sharded, 402, 300);
        step(physics, 40);
        check(a.dst(b) >= (a.hitSize + b.hitSize) * 0.59f, "Friendly Startrail / Skywatch remain overlapped");
        clear(); step(physics, 1); check(!physics.shouldProcess(), "Removed units retained collision bodies");
        a = star.spawn(Team.sharded, 400, 300); b = watch.spawn(Team.crux, 402, 300);
        step(physics, 40);
        check(a.dst(b) >= (a.hitSize + b.hitSize) * 0.59f, "Enemy orbital units pass through each other");
        clear(); step(physics, 1);
        a = star.spawn(Team.sharded, 400, 300); b = star.spawn(Team.sharded, 400, 300);
        step(physics, 60);
        check(Float.isFinite(a.x) && Float.isFinite(b.x) && a.dst(b) >= (a.hitSize + b.hitSize) * 0.59f,
                "Exact-center overlap did not resolve safely");
        clear(); step(physics, 1);
        a = star.spawn(Team.sharded, 400, 300);
        Unit plane = UnitTypes.flare.spawn(Team.sharded, 400, 300);
        Unit ground = UnitTypes.dagger.spawn(Team.crux, 400, 300);
        step(physics, 30);
        check(a.x == 400f && a.y == 300f && plane.x == 400f && plane.y == 300f
                && ground.x == 400f && ground.y == 300f, "Orbital physics touches ordinary air/ground layers");
        clear(); step(physics, 1);
        // Main-thread gameplay movement must be preserved when the worker correction is applied.
        a = star.spawn(Team.sharded, 400, 300); b = star.spawn(Team.sharded, 402, 300);
        physics.begin(); physics.process();
        a.move(0f, 7f); b.move(0f, 7f); physics.end();
        check(a.y == 307f && b.y == 307f, "Physics overwrote main-thread unit movement");
        physics.begin(); physics.process(); b.remove(); physics.end();
        step(physics, 1);
        clear(); physics.reset(); check(!physics.shouldProcess(), "Reset kept stale physics state");
        a = watch.spawn(Team.sharded, 400, 300); b = watch.spawn(Team.crux, 402, 300);
        step(physics, 40); check(a.dst(b) > 18f, "Collision failed after reset/reinitialization");
        clear(); step(physics, 1);
        // Exercise the actual AsyncCore worker/end handoff, not only direct solver calls.
        a = star.spawn(Team.sharded, 400, 300); b = watch.spawn(Team.sharded, 402, 300);
        for(int i = 0; i < 40; i++){ Vars.asyncCore.begin(); Vars.asyncCore.end(); }
        check(a.dst(b) > 15f, "Registered asynchronous orbital solver did not separate units");
        check(!star.physics && !watch.physics, "Orbital units entered the ordinary air physics world");
        System.out.println("ORBITAL_COLLISION_PASS: friendly/enemy/mixed hulls, exact overlap, isolated air/ground layers, native solver, snapshot movement, removal/reset, AsyncCore integration");
    }
}
