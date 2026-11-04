package cryon.Content;

import arc.Events;
import arc.graphics.Color;
import arc.util.noise.Simplex;
import cryon.Features.CryonContent;
import cryon.Type.CryonDepthsDrift;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.ctype.ContentType;
import mindustry.game.EventType.*;
import mindustry.game.Rules;
import mindustry.type.Weather;
import mindustry.world.*;
import mindustry.world.meta.Env;

/** Tantros-style seabed and flora, including safe terrain conversion for existing bases. */
public final class CryonDepthsEnvironment{
    private static final String version = "cryon-tantros-seabed-v1";
    public static final int environment = Env.terrestrial | Env.underwater;
    private static Tiles preparedTiles;

    private CryonDepthsEnvironment(){}

    public static void load(){ new CryonDepthsDrift(); }

    public static void init(){
        Events.on(WorldLoadBeginEvent.class, event -> preparedTiles = null);
        Events.on(ResetEvent.class, event -> preparedTiles = null);
        // WorldLoadEnd is still in generating mode, before native indexes, shadows and
        // renderer caches are initialized. Convert old terrain in that batch.
        Events.on(WorldLoadEndEvent.class, event -> refresh());
        Events.on(WorldLoadEvent.class, event -> refresh());
        Events.on(SaveLoadEvent.class, event -> refresh());
    }

    public static void applyRules(Rules rules){
        cryon.Features.DeepSeaRules.apply(rules);
        rules.env = environment;
        rules.lighting = true;
        rules.ambientLight = Color.valueOf("416e8188");
        rules.fire = false;
        Weather drift = Vars.content.getByName(ContentType.weather, "cryon-abyssal-drift");
        Weather.WeatherEntry entry = rules.weather.find(e -> e.weather == drift);
        if(entry == null && drift != null) entry = new Weather.WeatherEntry(drift);
        rules.weather.clear();
        if(entry != null){
            entry.always = true; entry.intensity = 1f; entry.cooldown = 0f;
            rules.weather.add(entry);
        }
    }

    private static float biome(int x, int y){ return Simplex.noise2d(271, 3, 0.55, 1f / 33f, x, y); }

    private static Block reef(int x, int y, Block blackSilt){
        float n = biome(x, y);
        return n < 0.38f ? Blocks.redmat : n > 0.64f ? blackSilt : Blocks.bluemat;
    }

    /** Change environment tiles only. Player structures, ore overlays, cores and items survive. */
    public static void applyTerrain(Tiles tiles){
        Block ice = CryonContent.block("cryon-ice-rock"), dark = CryonContent.block("cryon-dark-ice-rock");
        Block vent = CryonContent.block("cryon-rock-vent"), iceWall = CryonContent.block("cryon-ice-rock-wall");
        Block blueBed = CryonContent.block("abyss-blue-bed"), redBed = CryonContent.block("abyss-red-bed");
        Block siltBed = CryonContent.block("abyss-silt-bed"), shallow = CryonContent.block("abyss-shallows");
        Block blackSilt = CryonContent.block("abyss-black-silt"), basalt = CryonContent.block("abyss-pillow-basalt");
        // One summed-area table replaces the 5x5 building search for every empty tile.
        int stride = tiles.width + 1;
        int[] buildings = new int[stride * (tiles.height + 1)];
        for(int y = 0; y < tiles.height; y++){
            int row = 0;
            for(int x = 0; x < tiles.width; x++){
                if(tiles.get(x, y).build != null) row++;
                buildings[(y + 1) * stride + x + 1] = buildings[y * stride + x + 1] + row;
            }
        }
        for(Tile tile : tiles){
            Block floor = tile.floor();
            if(floor == Blocks.deepwater){
                float n = biome(tile.x, tile.y);
                tile.setFloor((n < 0.36f ? redBed : n > 0.65f ? siltBed : blueBed).asFloor());
            }else if(floor == Blocks.water){
                tile.setFloor(shallow.asFloor());
            }else if(floor == Blocks.darksand){
                tile.setFloor(blackSilt.asFloor());
            }else if(floor == ice || floor == dark){
                tile.setFloor(reef(tile.x, tile.y, blackSilt).asFloor());
            }else if(floor == vent){
                tile.setFloor(basalt.asFloor());
            }
            if(tile.block() == iceWall) tile.setBlock(Blocks.darksand.asFloor().wall);
            if(tile.block() != Blocks.air || tile.overlay() != Blocks.air) continue;
            boolean red = tile.floor() == redBed || tile.floor() == Blocks.redmat;
            boolean blue = tile.floor() == blueBed || tile.floor() == shallow || tile.floor() == Blocks.bluemat;
            if(!red && !blue) continue;
            // Keep construction room and delivery lanes around existing buildings and the core.
            int x1 = Math.max(0, tile.x - 2), y1 = Math.max(0, tile.y - 2);
            int x2 = Math.min(tiles.width, tile.x + 3), y2 = Math.min(tiles.height, tile.y + 3);
            if(buildings[y2 * stride + x2] - buildings[y1 * stride + x2]
                    - buildings[y2 * stride + x1] + buildings[y1 * stride + x1] > 0) continue;
            float density = Simplex.noise2d(97, 2, 0.5, 0.09, tile.x, tile.y);
            float scatter = arc.math.Mathf.randomSeed(tile.pos() * 31L + 271);
            if(red){
                if(density > 0.48f && scatter < 0.08f) tile.setBlock(Blocks.redweed);
            }else if(blue){
                if(density > 0.45f && scatter < 0.025f) tile.setBlock(Blocks.purbush);
                else if(density > 0.5f && scatter > 0.994f) tile.setBlock(Blocks.yellowCoral);
            }
        }
        Vars.state.rules.tags.put(version, "true");
    }

    private static void refresh(){
        if(Vars.state == null || (Vars.net != null && Vars.net.client())) return;
        Rules rules = Vars.state.rules;
        var planet = rules.sector == null ? rules.planet : rules.sector.planet;
        if(planet == null || !planet.name.equals("cryon-cryon-depths")) return;
        if(preparedTiles != Vars.world.tiles){
            boolean changed = !rules.tags.getBool(version);
            if(changed) applyTerrain(Vars.world.tiles);
            // These compatibility replacements add no deposits or layout to authored maps.
            // Resolve block names once, rather than allocating/looking them up for every tile.
            Block vent = CryonContent.block("cryon-rock-vent"), silt = CryonContent.block("abyss-black-silt");
            Block basalt = CryonContent.block("abyss-pillow-basalt");
            for(Tile tile : Vars.world.tiles){
                if(tile.floor() == Blocks.darksand){ tile.setFloor(silt.asFloor()); changed = true; }
                if(tile.floor() == vent){ tile.setFloor(basalt.asFloor()); changed = true; }
            }
            preparedTiles = Vars.world.tiles;
            // Native minimap initialization will run once after a batched load.
            if(changed && !Vars.world.isGenerating() && !Vars.headless && Vars.renderer != null) Vars.renderer.minimap.updateAll();
        }
        rules.tags.put(version, "true");
        applyRules(rules);
        // Old saves may still contain active snow or storm weather states.
        mindustry.gen.Groups.weather.copy().each(state -> {
            if(!state.weather.name.equals("cryon-abyssal-drift")) state.remove();
        });
    }
}
