import arc.*;
import arc.files.Fi;
import arc.func.*;
import arc.math.geom.*;
import arc.mock.*;
import arc.struct.Seq;
import arc.util.*;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.Iterator;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.entities.EntityGroup;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.net.Net;
import mindustry.world.*;

/** Counts real index visits and game-update scans rather than relying on noisy wall-clock FPS. */
public class DeepSeaPerformanceCheck{
    static void check(boolean valid, String message){ if(!valid) throw new AssertionError(message); }
    static final class CountingTeams extends Seq<Teams.TeamData>{
        int iterations;
        CountingTeams(){ super(Teams.TeamData.class); }
        @Override public Iterator<Teams.TeamData> iterator(){ iterations++; return super.iterator(); }
    }
    static final class CountingUnits extends EntityGroup<Unit>{
        int scans;
        CountingUnits(){ super(Unit.class, true, true); }
        @Override public void each(Cons<Unit> action){ scans++; super.each(action); }
    }
    static final class DetectorTree extends QuadTree<Unit>{
        int queries, candidates;
        DetectorTree(){ super(new Rect(0, 0, 1024, 1024)); }
        @Override public boolean intersect(float x, float y, float width, float height, Boolf<Unit> predicate){
            queries++;
            return super.intersect(x, y, width, height, unit -> { candidates++; return predicate.get(unit); });
        }
    }
    static final class BuildingTree extends QuadTree<Building>{
        int queries, candidates;
        BuildingTree(){ super(new Rect(0, 0, 1024, 1024)); }
        @Override public boolean intersect(float x, float y, float width, float height, Boolf<Building> predicate){
            queries++;
            return super.intersect(x, y, width, height, build -> { candidates++; return predicate.get(build); });
        }
        @Override public void intersect(float x, float y, float width, float height, Cons<Building> action){
            queries++;
            super.intersect(x, y, width, height, build -> { candidates++; action.get(build); });
        }
    }
    public static void main(String[] args) throws Exception{
        Core.app = new MockApplication(); Core.files = new MockFiles(); Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Core.settings = new Settings(); Core.settings.setDataDirectory(new Fi(Files.createTempDirectory("cryon-ocean-performance-").toFile()));
        Core.bundle = I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(Core.settings.getDataDirectory().child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(Core.settings.getDataDirectory().child("mods/unitlanuch.jar"));
        Vars.headless = true; Vars.loadLocales = false; Vars.net = new Net(null); Vars.platform = new mindustry.core.Platform(){};
        mindustry.core.Version.init(); Vars.init(); Vars.content.createBaseContent(); Vars.content.createModContent(); Vars.content.init();
        var mod = Vars.mods.locateMod("cryon"); check(!mod.hasContentErrors(), "Packaged content errors");
        ClassLoader loader = mod.main.getClass().getClassLoader();
        Class<?> allies = loader.loadClass("cryon.Features.MapAlliances");
        allies.getMethod("install").invoke(null);
        Vars.logic = new Logic(); Vars.logic.reset();
        allies.getMethod("setAlly", Rules.class, Team.class, boolean.class).invoke(null, Vars.state.rules, Team.green, true);
        Vars.world.loadGenerator(100, 100, tiles -> {
            for(int x = 0; x < 100; x++) for(int y = 0; y < 100; y++) tiles.set(x, y, new Tile(x, y, Blocks.water, Blocks.air, Blocks.air));
            tiles.get(10, 10).setBlock(Blocks.coreShard, Team.sharded);
            tiles.get(20, 10).setBlock(Blocks.coreShard, Team.green);
            tiles.get(80, 80).setBlock(Blocks.coreShard, Team.crux);
            tiles.get(65, 62).setBlock(Blocks.copperWall, Team.crux);
            tiles.get(63, 65).setBlock(Blocks.copperWall, Team.sharded);
        });
        Vars.state.teams.updateTeamStats();
        CountingTeams active = new CountingTeams(); active.addAll(Vars.state.teams.active); Vars.state.teams.active = active;
        allies.getMethod("refresh").invoke(null); active.iterations = 0;
        Method enter = allies.getMethod("enterTeam", Team.class), exit = allies.getMethod("exit", loader.loadClass("cryon.Features.MapAlliances$Scope"));
        int updates = 2000;
        for(int i = 0; i < updates; i++){ Object scope = enter.invoke(null, Team.sharded); exit.invoke(null, scope); }
        check(active.iterations == updates, "Entity updates still rebuild every team's diplomacy: " + active.iterations + " traversals");
        check(enter.invoke(null, Team.crux) == null, "Unallied root updates still allocate alternate team indexes");
        // Core-index invalidation must still update relationships immediately.
        Team.sharded.data().coreEnemies = new Team[]{Team.green, Team.crux};
        Object playerScope = enter.invoke(null, Team.sharded);
        check(!java.util.Arrays.asList(Team.sharded.data().coreEnemies).contains(Team.green), "Core changes were not detected");
        Object enemyScope = enter.invoke(null, Team.crux);
        check(enemyScope != null && Vars.state.teams.present.contains(data -> data.team == Team.green), "Nested enemy callbacks have a filtered allied view");
        exit.invoke(null, enemyScope); exit.invoke(null, playerScope);

        Unit sub = Vars.content.unit("cryon-pammelas").create(Team.sharded); sub.set(500, 500); sub.add();
        sub.apply(Vars.content.statusEffect("cryon-deep-diving"));
        DetectorTree unitTree = new DetectorTree(); Team.crux.data().unitTree = unitTree;
        for(int i = 0; i < 400; i++){
            Unit detector = UnitTypes.dagger.create(Team.crux); detector.set(505, 505); detector.add(); unitTree.insert(detector);
        }
        BuildingTree observerBuildings = new BuildingTree(), foreignBuildings = new BuildingTree();
        observerBuildings.insert(Vars.world.tile(65, 62).build);
        foreignBuildings.insert(Vars.world.tile(63, 65).build);
        Team.crux.data().buildingTree = observerBuildings; Team.sharded.data().buildingTree = foreignBuildings;
        Method detected = sub.type.getClass().getMethod("detected", Unit.class, Team.class);
        for(int i = 0; i < 100; i++) check((boolean)detected.invoke(sub.type, sub, Team.crux), "Detector failed to reveal the submarine");
        check(unitTree.queries == 100 && unitTree.candidates == 100, "Detection scans every nearby unit instead of stopping at one");
        unitTree.clear();
        check((boolean)detected.invoke(sub.type, sub, Team.crux), "Observer building cannot reveal the submarine");
        check(observerBuildings.queries == 1 && foreignBuildings.queries == 0, "Detection queried other factions' buildings");
        observerBuildings.clear();
        check(!(boolean)detected.invoke(sub.type, sub, Team.crux), "A foreign building revealed the submarine");
        int queries = unitTree.queries;
        check((boolean)detected.invoke(sub.type, sub, Team.green) && unitTree.queries == queries, "Friendly visibility performs enemy detector searches");

        Class<?> drift = loader.loadClass("cryon.Type.CryonDepthsDrift");
        Method spanMethod = drift.getDeclaredMethod("particleSpan", float.class, float.class); spanMethod.setAccessible(true);
        int largest = 0;
        for(float[] view : new float[][]{{800, 600}, {4000, 2400}, {16000, 12000}, {1000000, 100000}, {1, 1000000}}){
            float span = (float)spanMethod.invoke(null, view[0], view[1]);
            for(float x : new float[]{0, -137.5f, 999.25f}) for(float y : new float[]{0, -613.25f, 87.5f}){
                int cells = ((int)Math.ceil((x + view[0]) / span) - (int)Math.floor(x / span) + 3)
                    * ((int)Math.ceil((y + view[1]) / span) - (int)Math.floor(y / span) + 3);
                check(cells <= 512, "Zoomed-out weather exceeds the particle budget: " + cells);
                largest = Math.max(largest, cells);
            }
        }
        check((float)spanMethod.invoke(null, 800f, 600f) == 100f, "Normal-view weather density changed");

        Class<?> traits = loader.loadClass("cryon.Features.PlanetTraits"); traits.getMethod("install").invoke(null);
        EntityGroup<Unit> originalUnits = Groups.unit;
        CountingUnits units = new CountingUnits(); Groups.unit = units;
        Vars.state.set(mindustry.core.GameState.State.playing);
        for(int tick = 0; tick < 30; tick++){ Time.delta = 1; Events.fire(Trigger.afterGameUpdate); }
        Groups.unit = originalUnits;
        check(units.scans == 1, "Cold maps still rescan every unit every tick: " + units.scans);
        System.out.println("DEEPSEA_PERFORMANCE_PASS: " + updates + " scoped updates without repeated relationship rebuilding; one detector visit per query; observer-only building indexes; weather <= " + largest + "/512 cells; environment exposure 1 scan per 30 ticks");
    }
}
