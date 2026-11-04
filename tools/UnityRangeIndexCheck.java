import arc.*;
import arc.files.Fi;
import arc.func.Cons;
import arc.mock.*;
import arc.struct.Seq;
import arc.util.*;
import java.lang.reflect.Method;
import java.nio.file.Files;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.GameState.State;
import mindustry.core.Logic;
import mindustry.entities.units.BuildPlan;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.io.SaveIO;
import mindustry.logic.Ranged;
import mindustry.net.Net;
import mindustry.world.*;
import mindustry.world.blocks.ConstructBlock;

/** Native editor tiles, worker construction, turret quadtrees and saved Unity recipes. */
public class UnityRangeIndexCheck{
    static Class<?> blueprint;
    static Method install, begin;
    static Object recipe(String turret, String ammo) throws Exception{
        return blueprint.getMethod("decodeStored", String.class).invoke(null,
            "{\"name\":\"range-check\",\"modules\":[{\"turret\":\"" + turret + "\",\"ammo\":\"" + ammo + "\"}]}");
    }
    static void check(boolean value, String message){ if(!value) throw new AssertionError(message); }
    static int indexed(Building build){
        Seq<Building> entries = new Seq<>(); build.team.data().turretTree.getObjects(entries);
        return entries.count(b -> b == build);
    }
    static void range(Building build, Object recipe, boolean empty) throws Exception{
        install.invoke(build, recipe);
        check(build instanceof Ranged, "Unity building lacks Ranged: " + build.getClass());
        float actual = ((Ranged)build).range();
        Object fusion = build.getClass().getField("fusion").get(build);
        float expected = empty ? 0f : fusion.getClass().getField("range").getFloat(fusion);
        check(actual == expected && Float.isFinite(actual), "Indexed range differs from fused weapon range");
        check(indexed(build) == 1, "Range update lost or duplicated the turret index");
        if(actual > 0){
            Seq<Building> hits = new Seq<>();
            build.team.data().turretTree.intersect(build.x + actual - 1f, build.y - 1f, 2f, 2f, (Cons<Building>)hits::add);
            check(hits.contains(build, true), "Expanded range cannot be found by native turret queries");
        }
    }
    public static void main(String[] args) throws Exception{
        Core.app = new MockApplication(); Core.files = new MockFiles(); Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Fi data = new Fi(Files.createTempDirectory("cryon-unity-range-").toFile());
        Core.settings = new Settings(); Core.settings.setDataDirectory(data); Core.bundle = I18NBundle.createEmptyBundle();
        new Fi(args.length == 0 ? "build/libs/mindustry-MOD-JavaDesktop.jar" : args[0]).copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlanuch.jar"));
        Vars.headless = true; Vars.loadLocales = false; Vars.net = new Net(null); Vars.platform = new mindustry.core.Platform(){};
        mindustry.core.Version.init(); Vars.init(); Vars.content.createBaseContent(); Vars.content.createModContent(); Vars.content.init();
        var mod = Vars.mods.locateMod("cryon"); check(mod != null && !mod.hasContentErrors(), "Packaged content errors");
        var loader = mod.main.getClass().getClassLoader();
        Class<?> alliances = loader.loadClass("cryon.Features.MapAlliances");
        alliances.getMethod("install").invoke(null);
        blueprint = loader.loadClass("cryon.Unity.UnityBlueprint");
        Class<?> unityBuild = loader.loadClass("cryon.Type.UnityTurret$UnityBuild");
        install = unityBuild.getMethod("install", blueprint); begin = unityBuild.getMethod("begin", blueprint);
        Block platform = Vars.content.block("cryon-unity"), finished = Vars.content.block("cryon-unity-finished");
        Object small = recipe("duo", "copper"), large = recipe("foreshadow", "surge-alloy");
        Object empty = blueprint.getConstructor().newInstance();
        String encoded = (String)blueprint.getMethod("encode").invoke(small);
        Vars.logic = new Logic();
        for(boolean allies : new boolean[]{true, false}){
            Vars.logic.reset(); Vars.state.rules.placeRangeCheck = false;
            if(allies) alliances.getMethod("setAlly", Rules.class, Team.class, boolean.class).invoke(null, Vars.state.rules, Team.green, true);
            Vars.state.set(State.playing);
            Vars.world.loadGenerator(96, 96, tiles -> {
                for(int x = 0; x < 96; x++) for(int y = 0; y < 96; y++)
                    tiles.set(x, y, new mindustry.editor.EditorTile(x, y, Blocks.stone.id, Blocks.air.id, Blocks.air.id));
                tiles.get(12, 12).setBlock(Blocks.coreShard, Team.sharded);
                tiles.get(80, 12).setBlock(Blocks.coreShard, Team.green);
                // Force quadtree subdivision so expanding from zero range changes its node.
                for(int x = 8; x < 90; x += 12) for(int y = 60; y < 90; y += 12) tiles.get(x, y).setBlock(Blocks.duo, Team.sharded);
                tiles.get(20, 20).setBlock(platform, Team.sharded);
            });
            for(var item : Vars.content.items()) Team.sharded.core().items.add(item, 10000);
            Tile location = Vars.world.tile(20, 20);
            Building original = location.build;
            System.out.println("UNITY_RANGE_REPRO: allies=" + allies + ", entity=" + original.getClass().getName()
                + ", parent=" + original.getClass().getSuperclass().getName() + ", ranged=" + (original instanceof Ranged));
            begin.invoke(original, small);
            Unit worker = UnitTypes.poly.create(Team.sharded); worker.set(original.x + 24, original.y); worker.add();
            worker.addBuild(new BuildPlan(location.x, location.y, 0, finished, "install:" + encoded), false);
            // Matches the reported UnitEntity -> Build.beginPlace -> EditorTile -> removeIndex path.
            Time.delta = 1f; worker.updateBuildLogic();
            check(location.block() != platform && location.build instanceof ConstructBlock.ConstructBuild, "Native upgrade did not begin");
            check(indexed(original) == 0, "Old platform remained in turret index after upgrade began");
            for(int i = 0; i < 10000 && location.block() != finished; i++){ Time.time++; worker.updateBuildLogic(); }
            check(location.block() == finished, "Native Unity construction never completed");
            worker.clearBuilding();
            Building build = location.build;
            check((build.getClass().getName().startsWith("cryon.generated.AllianceEntity")) == allies, "Wrong alliance adapter selection");
            check(!build.team.data().turretTree.leaf, "Fixture did not subdivide native turret tree");
            for(int i = 0; i < 3; i++){ range(build, large, false); range(build, small, false); range(build, empty, true); }
            range(build, large, false);
            Fi save = data.child("unity-range-" + allies + ".msav");
            SaveIO.write(save); Vars.logic.reset(); SaveIO.load(save);
            build = Vars.world.tile(20, 20).build;
            check(build instanceof Ranged && ((Ranged)build).range() > 400f && indexed(build) == 1, "Save/reload lost fused range/index");
            range(build, small, false);
            Vars.state.set(State.playing);
            worker = UnitTypes.poly.create(Team.sharded); worker.set(build.x + 24, build.y); worker.add();
            worker.addBuild(new BuildPlan(20, 20), false); worker.updateBuildLogic();
            check(Vars.world.tile(20, 20).block() != finished && indexed(build) == 0, "Native deconstruction did not remove turret index");
            Vars.world.tile(20, 20).setBlock(Blocks.air);
            System.out.println("UNITY_RANGE_CASE_PASS: allies=" + allies + "; native upgrade, expanded/shrunk/empty ranges, save/reload and deconstruction");
        }
        for(Block block : Vars.content.blocks()) if(block.attacks && block.buildType != null){
            check(block.newBuilding() instanceof Ranged, "Attacking block lacks Ranged: " + block.name);
        }
        System.out.println("UNITY_RANGE_INDEX_PASS: real editor construction and native turret indexes, with and without alliances");
    }
}
