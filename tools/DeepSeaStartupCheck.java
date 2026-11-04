import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.*;
import java.nio.file.Files;
import java.util.Iterator;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.io.SaveIO;
import mindustry.net.Net;
import mindustry.world.*;
import mindustry.world.blocks.environment.Floor;

/** Checks migration ordering before renderer/index initialization, parity and duplicate-load cost. */
public class DeepSeaStartupCheck{
    static class CountingTiles extends Tiles{
        long visits;
        CountingTiles(int width, int height){ super(width, height); }
        @Override public Iterator<Tile> iterator(){
            Iterator<Tile> original = super.iterator();
            return new Iterator<Tile>(){
                public boolean hasNext(){ return original.hasNext(); }
                public Tile next(){ visits++; return original.next(); }
            };
        }
    }
    static class MigrationTile extends Tile{
        static int unbatched;
        MigrationTile(int x, int y, Block floor){ super(x, y, floor, Blocks.air, Blocks.air); }
        @Override public void setFloor(Floor floor){
            if(floor() != floor && !Vars.world.isGenerating()) unbatched++;
            super.setFloor(floor);
        }
    }
    static void check(boolean valid, String message){ if(!valid) throw new AssertionError(message); }
    static long hash(Tiles tiles){
        long result = 19;
        for(Tile tile : tiles){
            result = result * 31 + tile.floor().name.hashCode();
            result = result * 31 + tile.block().name.hashCode();
            result = result * 31 + tile.overlay().name.hashCode();
        }
        return result;
    }
    public static void main(String[] args) throws Exception{
        boolean baseline = args.length > 0 && args[0].equals("--baseline");
        Core.app = new MockApplication(); Core.files = new MockFiles(); Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Core.settings = new Settings(); Core.settings.setDataDirectory(new Fi(Files.createTempDirectory("cryon-ocean-startup-").toFile()));
        Core.bundle = I18NBundle.createEmptyBundle();
        new Fi(System.getProperty("cryon.test.jar", "build/libs/mindustry-MOD-JavaDesktop.jar")).copyTo(Core.settings.getDataDirectory().child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(Core.settings.getDataDirectory().child("mods/unitlanuch.jar"));
        Vars.headless = true; Vars.loadLocales = false; Vars.net = new Net(null); Vars.platform = new mindustry.core.Platform(){};
        mindustry.core.Version.init(); Vars.init(); Vars.content.createBaseContent(); Vars.content.createModContent(); Vars.content.init();
        ClassLoader loader = Vars.mods.locateMod("cryon").main.getClass().getClassLoader();
        Class<?> environment = loader.loadClass("cryon.Content.CryonDepthsEnvironment"); environment.getMethod("init").invoke(null);
        if(args.length > 1){
            var meta = SaveIO.getMeta(new Fi(args[1]));
            System.out.println("OCEAN_SAVE_META: planet=" + (meta.rules.sector == null ? meta.rules.planet : meta.rules.sector.planet)
                + ", terrainMigration=" + meta.rules.tags.get("cryon-tantros-seabed-v1", "false")
                + ", size=" + meta.tags.get("width", "?") + "x" + meta.tags.get("height", "?"));
        }
        Vars.logic = new Logic(); Vars.logic.reset();
        Vars.state.rules.planet = Vars.content.planet("cryon-cryon-depths");
        CountingTiles tiles = new CountingTiles(180, 160);
        Vars.world.beginMapLoad(); Vars.world.tiles = tiles;
        Block[] floors = {Blocks.deepwater, Blocks.water, Blocks.darksand,
            Vars.content.block("cryon-cryon-ice-rock"), Vars.content.block("cryon-cryon-dark-ice-rock"),
            Vars.content.block("cryon-cryon-rock-vent"), Vars.content.block("cryon-abyss-black-silt")};
        for(int x = 0; x < tiles.width; x++) for(int y = 0; y < tiles.height; y++){
            MigrationTile tile = new MigrationTile(x, y, floors[(x / 13 + y / 11) % floors.length]); tiles.set(x, y, tile);
            if(x % 23 == 0) tile.setOverlay(Vars.content.block("cryon-ore-aluminum"));
            if(x == 0 || y == 0) tile.setBlock(Vars.content.block("cryon-cryon-ice-rock-wall"));
        }
        tiles.get(20, 20).setBlock(Blocks.coreShard, Team.sharded);
        tiles.get(80, 70).setOverlay(Blocks.spawn);
        var core = tiles.get(20, 20).build; core.items.add(Items.copper, 321);
        MigrationTile.unbatched = 0;
        long started = System.nanoTime(); Vars.world.endMapLoad(); long elapsed = System.nanoTime() - started;
        long terrain = hash(tiles), priorVisits = tiles.visits;
        Events.fire(new SaveLoadEvent(false)); long duplicateVisits = tiles.visits - priorVisits;
        check(tiles.get(20, 20).build == core && core.items.get(Items.copper) == 321, "Migration changed the base/inventory");
        check(tiles.get(80, 70).overlay() == Blocks.spawn, "Migration removed spawn overlay");
        System.out.println("OCEAN_STARTUP_METRICS: hash=" + terrain + ", unbatchedFloorChanges=" + MigrationTile.unbatched
            + ", duplicateTileVisits=" + duplicateVisits + ", endLoadMs=" + elapsed / 1000000.0);
        if(baseline) return;
        check(MigrationTile.unbatched == 0, "Terrain migrated after map generation/index initialization");
        check(duplicateVisits == 0, "SaveLoad repeats the full-world compatibility scan");
        check(terrain == Long.parseLong(System.getProperty("cryon.expected.terrain")), "Optimization changed legacy terrain/vegetation layout");
        // Native World.resize may reuse the same Tiles instance for a same-sized map.
        Vars.world.beginMapLoad(); tiles.get(60, 60).setFloor(Blocks.darksand.asFloor()); Vars.world.endMapLoad();
        check(tiles.get(60, 60).floor() == Vars.content.block("cryon-abyss-black-silt"), "The next map reused a stale migration cache");
        System.out.println("DEEPSEA_STARTUP_PASS: batched pre-index terrain migration, identical floor/ore/plant layout, no duplicate scan, preserved base/inventory/spawn and same-sized map reload");
    }
}
