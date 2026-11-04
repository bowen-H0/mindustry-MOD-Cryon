import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.game.*;
import mindustry.net.Net;
import mindustry.type.*;
import mindustry.world.*;
import java.util.ArrayDeque;

/** Packaged maps, independent progress, shared submarine research and open sea routes. */
public class CryonDepthsCheck{
    static void check(boolean valid, String message){ if(!valid) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception{
        Core.app = new MockApplication(); Core.files = new MockFiles();
        Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Core.settings = new Settings(); Core.settings.setDataDirectory(new Fi("/tmp/cryon-depths-check"));
        Core.bundle = I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(Core.settings.getDataDirectory().child("mods/cryon.jar"));
        Vars.headless = true; Vars.loadLocales = false; Vars.net = new Net(null);
        Vars.platform = new mindustry.core.Platform(){};
        mindustry.core.Version.init(); Vars.init();
        Vars.content.createBaseContent(); Vars.content.createModContent(); Vars.content.init();
        var mod = Vars.mods.list().find(m -> m.name.equals("cryon"));
        check(mod != null && !mod.hasContentErrors(), "Packaged content errors: " + mod.erroredContent);
        var loader = mod.main.getClass().getClassLoader();
        // Only the research root is needed here; the full client tree also refreshes GUI loadouts.
        var root = new TechTree.TechNode(null, Vars.content.block("cryon-core-pioneer"), ItemStack.empty);
        Vars.content.planet("cryon-cryon").techTree = root;
        new TechTree.TechNode(root, Vars.content.block("cryon-unit-projector"), ItemStack.empty);
        check(Vars.content.block("cryon-abyssal-shipyard") == null, "Removed shipyard remains registered");
        var layers = loader.loadClass("cryon.Features.CryonCampaignLayers");
        var environment = loader.loadClass("cryon.Content.CryonDepthsEnvironment");
        layers.getMethod("configure").invoke(null);
        layers.getMethod("shareResearch").invoke(null);
        Planet surface = Vars.content.planet("cryon-cryon"), depths = Vars.content.planet("cryon-cryon-depths");
        check(surface != depths && surface.sectors != depths.sectors, "Sector namespaces were shared");
        check(surface.position == depths.position, "Campaign layers have separate physical positions");
        check(surface.getRotation() == depths.getRotation(), "Campaign layers have separate rotations");
        check(surface.techTree != null && surface.techTree == depths.techTree, "Technology is not shared");
        check(!depths.allowLaunchToNumbered && !depths.allowSectorInvasion, "Ocean route can be bypassed");
        check(surface.sectors.get(17).preset == null || !surface.sectors.get(17).preset.name.contains("depths"), "Surface preset replaced");
        surface.getStartSector().info.name = "surface-progress"; surface.getStartSector().saveInfo();
        depths.getStartSector().info.name = "ocean-progress"; depths.getStartSector().saveInfo();
        surface.getStartSector().loadInfo(); depths.getStartSector().loadInfo();
        check("surface-progress".equals(surface.getStartSector().info.name)
                && "ocean-progress".equals(depths.getStartSector().info.name), "Sector progress overwrote another layer");
        Vars.logic = new Logic();
        String[] names = (String[])layers.getField("stages").get(null);
        int shown = layers.getField("shownStages").getInt(null);
        for(int stage = 0; stage < names.length; stage++){
            SectorPreset preset = Vars.content.sector("cryon-" + names[stage]);
            check(preset != null && preset.planet == depths && preset.generator.map != null, "Missing ocean map " + stage);
            check((preset.techNode != null) == (stage < shown), "Wrong campaign research visibility " + stage);
            check((preset.sector.preset == preset) == (stage < shown), "Wrong star-map mission visibility " + stage);
            if(stage > 0 && stage < shown){
                final int previous = stage - 1;
                check(preset.techNode.objectives.contains(o -> o instanceof Objectives.SectorComplete
                        && ((Objectives.SectorComplete)o).preset == Vars.content.sector("cryon-" + names[previous])), "Broken route " + stage);
            }
            Vars.logic.reset(); Vars.state.rules.sector = preset.sector; Vars.state.rules.planet = depths;
            Vars.world.loadGenerator(224, 224, tiles -> preset.generator.generate(tiles, new WorldParams()));
            int water = 0, aluminum = 0, magnesium = 0, flora = 0, reefs = 0, oldIce = 0;
            Tile spawn = null;
            for(Tile tile : Vars.world.tiles){
                if(tile.floor().isLiquid) water++;
                if(tile.block() == Blocks.redweed || tile.block() == Blocks.purbush || tile.block() == Blocks.yellowCoral) flora++;
                if(tile.floor() == Blocks.redmat || tile.floor() == Blocks.bluemat) reefs++;
                if(tile.floor().name.equals("cryon-cryon-ice-rock") || tile.floor().name.equals("cryon-cryon-dark-ice-rock")) oldIce++;
                if(tile.overlay().name.equals("cryon-ore-aluminum")) aluminum++;
                if(tile.overlay().name.equals("cryon-ore-magnesium")) magnesium++;
                if(tile.overlay() == Blocks.spawn) spawn = tile;
                check(!tile.block().name.equals("cryon-abyssal-shipyard"), "Removed shipyard remains on map " + stage);
            }
            check(water > 224 * 224 * 0.65f, "Underground is not an ocean: " + stage);
            check((Vars.state.rules.env & mindustry.world.meta.Env.underwater) != 0, "Map is not underwater");
            check(flora > 100 && reefs > 100 && oldIce == 0, "Tantros seabed/flora missing: " + stage);
            check(Vars.state.rules.weather.size == 1 && Vars.state.rules.weather.first().weather.name.equals("cryon-abyssal-drift"), "Wrong underwater weather");
            check(depths.defaultCore.supportsEnv(Vars.state.rules.env) && ((mindustry.world.blocks.storage.CoreBlock)depths.defaultCore).unitType.supportsEnv(Vars.state.rules.env), "Underwater core cannot operate");
            check(aluminum > 20 && magnesium > 20, "Starter ores missing: " + stage);
            check(spawn != null && spawn.floor().isLiquid, "Missing naval spawn: " + stage);
            check(waterPath(spawn, Vars.world.tile(224 / 4 + 26, 224 / 5 + 1)), "Island blocks the shipping lane: " + stage);
            check(Team.sharded.core() != null && Team.sharded.core().block == depths.defaultCore, "Wrong landing core: " + stage);
            check((stage == 2 || stage >= 4) == Vars.state.rules.attackMode, "Wrong victory conditions: " + stage);
            check(Vars.state.rules.spawns.contains(g -> g.type == UnitTypes.risso), "No naval wave: " + stage);
            check(Vars.content.unit("cryon-minimus").techNode.parent.content == Vars.content.block("cryon-unit-projector"),
                "Minimus research no longer starts at the unit projector");
            System.out.println("OCEAN_STAGE_PASS " + stage + ": " + water + " water tiles; shipyard removed, viable ore and open naval route");
        }
        System.out.println("CRYON_LAYERS_PASS: six ocean maps without shipyards, separate progress, shared technology and submarine production research");
        Fi legacy = new Fi("/tmp/cryon-layers-render-8/saves/sector-cryon-cryon-depths-0.msav");
        if(legacy.exists()){
            Vars.logic.reset(); mindustry.io.SaveIO.load(legacy);
            check(Vars.state.rules.sector != null && Vars.state.rules.sector.planet == depths, "Existing underground save lost its campaign layer");
            check(Team.sharded.core() != null && Team.sharded.core().block == depths.defaultCore, "Existing underground save lost its core");
            long oreBefore = oreHash();
            var buildings = new java.util.HashMap<Integer, mindustry.gen.Building>();
            var inventory = new java.util.HashMap<Integer, String>();
            for(Tile tile : Vars.world.tiles) if(tile.isCenter() && tile.build != null){
                buildings.put(tile.pos(), tile.build);
                inventory.put(tile.pos(), tile.build.items == null ? "" : tile.build.items.toString());
            }
            environment.getMethod("init").invoke(null);
            Events.fire(new mindustry.game.EventType.WorldLoadEvent());
            check(oreHash() == oreBefore, "Seabed update removed ore overlays");
            for(var entry : buildings.entrySet()){
                var build = Vars.world.tile(entry.getKey()).build;
                check(build == entry.getValue(), "Seabed update replaced an existing building");
                check(inventory.get(entry.getKey()).equals(build.items == null ? "" : build.items.toString()), "Seabed update lost inventory");
            }
            long migrated = terrainHash();
            Events.fire(new mindustry.game.EventType.SaveLoadEvent(false));
            check(terrainHash() == migrated, "Repeated load changed the terrain again");
            check((Vars.state.rules.env & mindustry.world.meta.Env.underwater) != 0, "Existing save did not become underwater");
            System.out.println("CRYON_LEGACY_LAYER_SAVE_PASS: previous underground campaign save loads at the same named sector");
            System.out.println("TANTROS_MIGRATION_PASS: seabed update preserves buildings, inventory and ores and is repeatable");
        }
        Vars.logic.reset(); Vars.state.rules.sector = surface.getStartSector(); Vars.state.rules.planet = surface;
        Vars.world.loadGenerator(20, 20, tiles -> {
            for(int x = 0; x < 20; x++) for(int y = 0; y < 20; y++)
                tiles.set(x, y, new Tile(x, y, Vars.content.block("cryon-cryon-ice-rock"), Blocks.air, Blocks.air));
        });
        Events.fire(new mindustry.game.EventType.WorldLoadEvent());
        for(Tile tile : Vars.world.tiles) check(tile.floor().name.equals("cryon-cryon-ice-rock"), "Underground environment changed the surface");
        check((Vars.state.rules.env & mindustry.world.meta.Env.underwater) == 0, "Surface received underwater rules");
        System.out.println("TANTROS_SURFACE_GUARD_PASS: surface terrain and environment are preserved");
    }
    static long oreHash(){ long hash = 0; for(Tile t : Vars.world.tiles) hash += (long)t.overlay().name.hashCode() * (t.array() + 1); return hash; }
    static long terrainHash(){ long hash = 0; for(Tile t : Vars.world.tiles) hash += (long)(t.floor().name.hashCode() * 31 + t.block().name.hashCode()) * (t.array() + 1); return hash; }
    static boolean waterPath(Tile from, Tile to){
        boolean[] seen = new boolean[224 * 224]; ArrayDeque<Tile> queue = new ArrayDeque<>(); queue.add(from);
        while(!queue.isEmpty()){
            Tile tile = queue.remove();
            if(tile == to) return true;
            for(int d = 0; d < 4; d++){
                Tile next = tile.nearby(d);
                if(next != null && next.floor().isLiquid && !next.block().solid && !seen[next.array()]){
                    seen[next.array()] = true; queue.add(next);
                }
            }
        }
        return false;
    }
}
