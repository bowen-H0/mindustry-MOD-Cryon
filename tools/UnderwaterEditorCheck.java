package mindustry.editor;

import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.*;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Arrays;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.GameState.State;
import mindustry.core.Logic;
import mindustry.game.*;
import mindustry.gen.Building;
import mindustry.io.*;
import mindustry.net.Net;
import mindustry.world.*;
import mindustry.world.meta.Env;

/** Real editor tiles/history and map serialization; only the graphics renderer is replaced. */
public class UnderwaterEditorCheck{
    static final String[] seabeds = {"pillow-basalt", "pressure-slate", "obsidian", "limestone", "black-silt",
        "white-clay", "frozen-silt", "silica-crust", "ice-crystal-floor", "titanium-bed", "nickel-bed", "metal-nodules"};
    static Method convert;
    static void check(boolean valid, String message){ if(!valid) throw new AssertionError(message); }
    static void convert() throws Exception{ convert.invoke(null); }
    static short[] floors(){
        short[] result = new short[Vars.world.width() * Vars.world.height()];
        for(Tile tile : Vars.world.tiles) result[tile.array()] = tile.floorID();
        return result;
    }
    static void sameFloors(short[] expected){ check(Arrays.equals(expected, floors()), "Floor layout changed unexpectedly"); }
    static void flooded(){
        for(Tile tile : Vars.world.tiles){
            check(tile.floor().isLiquid && tile.floor().liquidDrop == Liquids.water,
                "Dry/non-water floor at " + tile.x + "," + tile.y + ": " + tile.floor());
            check(tile.floor().supportsOverlay, "Underwater floor hides ore overlays");
        }
        check((Vars.state.rules.env & Env.underwater) != 0, "Missing underwater environment");
    }
    public static void main(String[] args) throws Exception{
        Core.app = new MockApplication(); Core.files = new MockFiles(); Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Fi data = new Fi(Files.createTempDirectory("cryon-underwater-editor-").toFile());
        Core.settings = new Settings(); Core.settings.setDataDirectory(data);
        Core.bundle = I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlanuch.jar"));
        Vars.headless = true; Vars.loadLocales = false; Vars.net = new Net(null);
        Vars.platform = new mindustry.core.Platform(){};
        mindustry.core.Version.init(); Vars.init();
        Vars.content.createBaseContent(); Vars.content.createModContent(); Vars.content.init();
        var mod = Vars.mods.locateMod("cryon");
        check(mod != null && !mod.hasContentErrors(), "Packaged content errors: " + mod.erroredContent);
        convert = mod.main.getClass().getClassLoader().loadClass("cryon.Features.UnderwaterEditor").getMethod("convert");
        Vars.logic = new Logic();
        Vars.editor = new MapEditor(); Vars.editor.renderer = new NoGraphicsRenderer();
        Vars.editor.beginEdit(60, 44);
        Block[] samples = {Blocks.stone, Blocks.sand, Blocks.ice, Blocks.snow, Blocks.basalt, Blocks.dacite,
            Blocks.darksand, Blocks.shale, Blocks.salt, Blocks.water, Blocks.deepwater, Blocks.taintedWater,
            Blocks.slag, Blocks.tar, Blocks.redmat, Blocks.bluemat,
            Vars.content.block("cryon-cryon-ice-rock"), Vars.content.block("cryon-cryon-dark-ice-rock")};
        Vars.editor.load(() -> {
            for(Tile tile : Vars.editor.tiles()){
                int sample = tile.x / 10 + (tile.y / 11) * 6;
                tile.setFloor(sample < seabeds.length
                    ? Vars.content.block("cryon-abyss-" + seabeds[sample]).asFloor()
                    : samples[(sample - seabeds.length) % samples.length].asFloor());
                if(tile.x % 17 == 0) tile.setOverlay(Blocks.oreCopper);
                if(tile.x == 2 && tile.y % 3 == 0) tile.setBlock(Blocks.stoneWall);
            }
            for(int i = 0; i < samples.length; i++) Vars.editor.tile(i + 8, 4).setFloor(samples[i].asFloor());
            Vars.editor.tile(50, 38).setOverlay(Blocks.spawn);
            Vars.editor.tile(32, 25).setBlock(Blocks.coreShard, Team.sharded);
            Vars.editor.tile(32, 20).setBlock(Blocks.conveyor, Team.sharded, 1);
            Vars.editor.tile(35, 20).setBlock(Blocks.message, Team.sharded);
        });
        Vars.editor.tile(32, 25).build.items.add(Items.copper, 321);
        Vars.editor.tile(35, 20).build.configureAny("underwater conversion check");
        Vars.state.rules.env = Env.terrestrial | Env.oxygen;
        Vars.state.rules.waveTeam = Team.green; Vars.state.rules.bannedUnits.add(UnitTypes.mono);
        int oldEnv = Vars.state.rules.env, oldBans = Vars.state.rules.bannedUnits.size;
        short[] original = floors(), overlays = new short[original.length], blocks = new short[original.length];
        for(Tile tile : Vars.editor.tiles()){
            overlays[tile.array()] = tile.overlayID(); blocks[tile.array()] = tile.blockID();
        }
        Building core = Vars.editor.tile(32, 25).build;
        // A pending brush stroke must be flushed into a separate undo step.
        Vars.editor.tile(59, 43).setFloor(Blocks.sand.asFloor());
        short[] before = floors();
        for(String name : seabeds){
            var wet = Vars.content.block("cryon-abyss-flooded-" + name).asFloor();
            var dry = Vars.content.block("cryon-abyss-" + name).asFloor();
            check(wet.itemDrop == dry.itemDrop && wet.playerUnmineable == dry.playerUnmineable,
                "Seabed mineral deposit erased: " + name);
            check(wet.isLiquid && wet.liquidDrop == Liquids.water && wet.supportsOverlay, "Invalid flooded floor: " + name);
        }
        convert(); flooded();
        short[] after = floors();
        convert(); sameFloors(after); // Must not create another history entry.
        Vars.editor.undo(); sameFloors(before);
        check(Vars.state.rules.env == oldEnv, "Undo did not restore the environment");
        Vars.editor.undo(); sameFloors(original);
        Vars.editor.redo(); sameFloors(before);
        Vars.editor.redo(); sameFloors(after); flooded();
        for(Tile tile : Vars.editor.tiles()){
            check(tile.overlayID() == overlays[tile.array()], "Ore/spawn overlay changed");
            check(tile.blockID() == blocks[tile.array()], "Building/wall changed");
        }
        check(Vars.editor.tile(32, 25).build == core && core.items.get(Items.copper) == 321, "Core/inventory changed");
        check(Vars.editor.tile(32, 20).build.rotation == 1, "Conveyor rotation changed");
        check("underwater conversion check".equals(Vars.editor.tile(35, 20).build.config()), "Building configuration changed");
        check(Vars.state.rules.waveTeam == Team.green && Vars.state.rules.bannedUnits.size == oldBans, "Campaign restrictions applied");
        // A brush edit after a repeated click must remain a normal operation.
        convert();
        Vars.editor.tile(59, 43).setFloor(Blocks.sand.asFloor()); Vars.editor.flushOp();
        Vars.editor.undo(); sameFloors(after); flooded();
        Vars.editor.undo(); sameFloors(before);
        Vars.editor.redo(); flooded();
        // Rules-only changes also need a complete undo/redo step.
        Vars.state.rules.env = oldEnv;
        convert(); flooded();
        Vars.editor.undo(); sameFloors(after);
        check(Vars.state.rules.env == oldEnv, "Rules-only conversion cannot be undone");
        Vars.editor.redo(); flooded();
        Vars.editor.tags.put("name", "Underwater editor verification");
        Vars.editor.tags.put("rules", JsonIO.write(Vars.state.rules));
        Fi mapFile = data.child("underwater.msav");
        MapIO.writeMap(mapFile, Vars.editor.createMap(mapFile));
        var savedMap = MapIO.createMap(mapFile, true);
        Vars.editor.beginEdit(savedMap);
        sameFloors(after); flooded();
        for(Tile tile : Vars.editor.tiles()){
            check(tile.overlayID() == overlays[tile.array()], "Saved map lost an ore/spawn overlay");
            check(tile.blockID() == blocks[tile.array()], "Saved map lost a building/wall");
        }
        Vars.state.set(State.playing);
        short[] gameplay = floors(); convert(); sameFloors(gameplay);
        System.out.println("UNDERWATER_EDITOR_PASS: 2640 water-covered tiles, 12 seabed materials, mineral deposits, buildings/configuration/inventory, ores/spawn, single-step undo/redo, idempotence, rules-only undo, map save/reload and gameplay guard");
    }
    /** Keep the native editor/history code; omit only calls that require a GL context. */
    static class NoGraphicsRenderer extends EditorRenderer{
        @Override public void resize(int width, int height){}
        @Override void updateStatic(int x, int y){}
        @Override void updateBlock(Tile tile){}
        @Override void updateBlock(int x, int y){}
    }
}
