package example;

import arc.files.Fi;
import arc.graphics.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.Seq;
import arc.util.*;
import arc.util.noise.*;
import mindustry.Vars;
import mindustry.ai.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.maps.generators.*;
import mindustry.type.UnitType;
import mindustry.world.*;
import mindustry.world.blocks.environment.*;
import mindustry.world.meta.*;
//Modified from the erekir generator.
//The script most prone to crashing.
//The API updates here might be a bit frequent.
//
// === Changelog (this revision) ===
// 1. Block/unit lookups now go through CryonContent instead of manual
//    Vars.content.block/unit + fallback boilerplate.
// 2. Ore veins are now small clusters (~10 tiles each) instead of large
//    continuous noise-threshold fields.
// 3. Added more enemy tiers (benignitas, comet, umbra/salus/sagitta/blaze/
//    charonia as late-game "elite" spawns).
// 4. Caves are more open (airThresh/airScl tuned).
// 5. cryon-ice-rock now generates its own wall variant, cryon-ice-rock-wall.
// 6. Added scattered cryon-broken-ice floor patches.
// 7. Added scattered shattered-crystal / shattered-ice rubble near walls.
// 8. Added a rare, small cryo-fluid biome (cryo-fluid-ice floor,
//    cryofluid-wall walls, cryofluid puddles).
// 9. Added a "depth" pass that rarely turns cryon-ice-rock into
//    cryon-dark-ice-rock further from spawn, with its own vent variant
//    (cryon-dark-rock-vent) and a small chance of titanium ore on it.
import java.io.IOException;

import static mindustry.Vars.*;

public class CryonPlanetGenerator extends PlanetGenerator{
    public float heightScl = 0.9f, octaves = 8, persistence = 0.7f, heightPow = 3f, heightMult = 1.6f;

    //TODO inline/remove
    public static float arkThresh = 0.28f, arkScl = 0.83f;
    public static int arkSeed = 7, arkOct = 2;
    public static float liqThresh = 0.64f, liqScl = 87f, redThresh = 3.1f, noArkThresh = 0.3f;
    public static int crystalSeed = 8, crystalOct = 2;
    public static float crystalScl = 0.9f, crystalMag = 0.3f;

    //caves: lower threshold + larger scale => bigger, more open chambers
    public static float airThresh = 0.08f, airScl = 22f;

    //ore veins: small clusters instead of large continuous fields.
    //oreClusterCount is an absolute target count (NOT a per-tile chance),
    //so density no longer depends on map size - a tiny per-tile chance was
    //the reason ore could fail to spawn at all on smaller maps.
    public static int oreClusterCount = 120;
    public static int oreClusterMinRadius = 4, oreClusterMaxRadius = 6;

    //broken ice: scattered coherent patches replacing plain ice floor
    public static float brokenIceThresh = 0.66f, brokenIceScl = 30f;

    //shattered crystal/ice rubble decoration near walls
    public static float shatterChance = 0.012f;

    //dark ice rock: large connected patches via coherent noise (roughly half
    //of all ice rock ends up dark), instead of independent per-tile chance
    public static float darkRockScl = 70f, darkRockThresh = 0.5f;
    //titanium veins on dark rock: small clusters of ~5 tiles, not scattered singles.
    //also an absolute target count for the same reason as oreClusterCount.
    public static int titaniumVeinCount = 25;
    public static int titaniumVeinMinRadius = 2, titaniumVeinMaxRadius = 3;

    //big cryo-fluid biome: a cluster of several overlapping blobs sized so
    //the whole region covers roughly 1/10 of the map, dotted with many
    //small puddles instead of a single central pool
    public static float cryoBiomeAppearChance = 0.35f;
    public static float cryoBiomeAreaFraction = 0.1f;
    public static int cryoBiomeBlobCount = 6, cryoBiomeBlobCountVariance = 3;
    public static float cryoBiomePuddleChance = 0.006f;
    public static int cryoBiomePuddleMinRadius = 2, cryoBiomePuddleMaxRadius = 4;
    //patches of sticky-cryofluid-floor within the cryo-fluid ice areas
    public static float stickyFluidThresh = 0.6f, stickyFluidScl = 25f;

    private static Block iceWall;
    private static Block iceRockWall;
    private static Block iceFloor;
    private static Block iceRock;
    private static Block darkIceRock;
    private static Block brokenIce;
    private static Block shatteredCrystal;
    private static Block shatteredIce;
    private static Block cryonVent;
    private static Block cryonRockVent;
    private static Block darkRockVent;
    private static Block cryoFluidIce;
    private static Block cryoFluidWall;
    private static Block cryoFluidPool;
    private static Block stickyCryoFluidFloor;
    private static Block cryoFluidVent;
    private static Block oreCrystalSand;
    private static Block oreDryIce;
    private static Block oreMagnesium;
    private static Block oreSalt;
    private static Block oreAluminum;
    private static Block oreTitanium;
    public static Schematic cryonLoadout;

    //small helper: fetch via CryonContent, warn + fall back if missing
    private static Block block(String id, Block fallback){
        Block b = CryonContent.block(id);
        if(b == null){
            Log.warn("[Cryon] block '" + id + "' not found, using fallback '" + fallback.name + "'");
            return fallback;
        }
        return b;
    }

    static {
        iceWall = block("cryon-ice-wall", Blocks.stoneWall);
        iceRockWall = block("cryon-ice-rock-wall", iceWall);
        iceFloor = block("cryon-ice", Blocks.snow);
        iceRock = block("cryon-ice-rock", Blocks.stone);
        darkIceRock = block("cryon-dark-ice-rock", iceRock);
        brokenIce = block("cryon-broken-ice", iceFloor);

        shatteredCrystal = block("shattered-crystal", Blocks.sporeCluster);
        shatteredIce = block("shattered-ice", iceFloor);

        cryonVent = block("cryon-vent", Blocks.rhyoliteVent);
        cryonRockVent = block("cryon-rock-vent", Blocks.rhyoliteVent);
        darkRockVent = block("cryon-dark-rock-vent", cryonRockVent);

        cryoFluidIce = block("cryo-fluid-ice", iceFloor);
        cryoFluidWall = block("cryofluid-wall", iceWall);
        cryoFluidPool = block("pooled-cryofluid", Blocks.water);
        stickyCryoFluidFloor = block("sticky-cryofluid-floor", cryoFluidIce);
        cryoFluidVent = block("cryon-cryofluid-vent", cryonVent);

        oreCrystalSand = block("ore-crystal-sand", Blocks.oreCopper);
        oreDryIce = block("ore-dry-ice", Blocks.oreLead);
        oreMagnesium = block("ore-magnesium", Blocks.oreTitanium);
        oreSalt = block("ore-salt", Blocks.oreCoal);
        oreAluminum = block("ore-aluminum", Blocks.oreThorium);
        oreTitanium = block("ore-titanium", Blocks.oreTitanium);

        Fi file = Vars.tree.get("schematics/core-pioneer.msch");
        if(file.exists()){
            try {
                cryonLoadout = Schematics.read(file);
                Log.info("[Cryon] Loaded core-pioneer loadout from file");
            } catch (IOException e) {
                Log.err("[Cryon] Failed to load core-pioneer.msch", e);
                cryonLoadout = Loadouts.basicShard;  // fallback
            }
        } else {
            Log.warn("[Cryon] core-pioneer.msch not found, using basicShard as fallback");
            cryonLoadout = Loadouts.basicShard;
        }

    }

    Block[] terrain = {iceFloor, iceRock};

    {
        baseSeed = 2;
        defaultLoadout = cryonLoadout;
    }

    @Override
    public float getHeight(Vec3 position){
        return Mathf.pow(rawHeight(position), heightPow) * heightMult;
    }

    @Override
    public void getColor(Vec3 position, Color out){
        Block block = getBlock(position);
        out.set(block.mapColor).a(1f - block.albedo);
    }

    @Override
    public float getSizeScl(){
        return 2000 * 1.07f * 6f / 5f;
    }

    float rawHeight(Vec3 position){
        return Simplex.noise3d(seed, octaves, persistence, 1f/heightScl, 10f + position.x, 10f + position.y, 10f + position.z);
    }

    float rawTemp(Vec3 position){
        return position.dst(0, 0, 1)*2.2f - Simplex.noise3d(seed, 8, 0.54f, 1.4f, 10f + position.x, 10f + position.y, 10f + position.z) * 2.9f;
    }

    Block getBlock(Vec3 position){
        float height = rawHeight(position);
        height *= 1.2f;
        height = Mathf.clamp(height);
        return terrain[Mathf.clamp((int)(height * terrain.length), 0, terrain.length - 1)];
    }

    @Override
    public void genTile(Vec3 position, TileGen tile){
        Block floor = getBlock(position);
        tile.floor = floor;
        //cryon-ice-rock gets its own matching wall variant
        tile.block = (floor == iceRock) ? iceRockWall : iceWall;

        if(Ridged.noise3d(seed + 1, position.x, position.y, position.z, 2, airScl) > airThresh){
            tile.block = Blocks.air;
        }
    }

    //some mod content models small decorations as solid-less Wall blocks
    //rather than Floor overlays; this avoids ClassCastException either way
    private static void placeOverlay(Tile tile, Block overlayBlock){
        if(overlayBlock instanceof mindustry.world.blocks.environment.Floor){
            tile.setOverlay(overlayBlock);
        }else if(!tile.block().solid){
            tile.setBlock(overlayBlock);
        }
    }

    //plants a roughly-10-tile ore cluster centered on (cx, cy)
    void placeOreCluster(int cx, int cy, Block ore){
        placeOreCluster(cx, cy, ore, oreClusterMinRadius, oreClusterMaxRadius);
    }

    //plants a cluster of the given ore, radius range controls the size
    //(minRadius=maxRadius=1 gives a ~5-tile vein, 1-2 gives ~5-13 tiles)
    void placeOreCluster(int cx, int cy, Block ore, int minRadius, int maxRadius){
        int crad = rand.random(minRadius, maxRadius);
        float crad2 = crad * crad + 1f;
        for(int dx = -crad; dx <= crad; dx++){
            for(int dy = -crad; dy <= crad; dy++){
                int rx = dx + cx, ry = dy + cy;
                float n = noise(rx, ry + rx * 1.3f, 2, 0.6f, 6f, 2f);
                if(dx * dx + dy * dy <= crad2 - n){
                    Tile dest = tiles.get(rx, ry);
                    if(dest != null && !dest.block().solid && dest.overlay() == Blocks.air && !nearWall(rx, ry)){
                        placeOverlay(dest, ore);
                    }
                }
            }
        }
    }

    //big, organic-looking cryo-fluid region made of several overlapping
    //blobs whose combined area is roughly cryoBiomeAreaFraction of the map
    void genCryoFluidBiome(int spawnX, int spawnY, int endX, int endY){
        if(!rand.chance(cryoBiomeAppearChance)) return;

        float targetArea = width * height * cryoBiomeAreaFraction;
        int blobs = Math.max(1, cryoBiomeBlobCount + rand.random(-cryoBiomeBlobCountVariance, cryoBiomeBlobCountVariance));
        float blobArea = targetArea / blobs;
        int blobRadius = Math.max(3, (int)Math.sqrt(blobArea / Math.PI));

        //find a region center far enough from spawn/end to fit the whole cluster
        int centerX = width/2, centerY = height/2;
        boolean found = false;
        for(int attempt = 0; attempt < 30 && !found; attempt++){
            centerX = rand.random(blobRadius + 4, Math.max(blobRadius + 4, width - blobRadius - 4));
            centerY = rand.random(blobRadius + 4, Math.max(blobRadius + 4, height - blobRadius - 4));
            if(Mathf.dst(centerX, centerY, spawnX, spawnY) > blobRadius * 2.5f && Mathf.dst(centerX, centerY, endX, endY) > blobRadius * 2.5f){
                found = true;
            }
        }
        if(!found) return;

        //scatter several overlapping blobs around the region center so the
        //biome reads as one large, organic zone rather than a neat circle
        for(int i = 0; i < blobs; i++){
            float jitter = blobRadius * 1.1f;
            int cx = centerX + (int)rand.range(jitter);
            int cy = centerY + (int)rand.range(jitter);
            int radius = (int)(blobRadius * rand.random(0.8f, 1.2f));
            carveCryoBlob(cx, cy, radius);
        }
    }

    //carves one blob of cryo-fluid ice/walls, seeding many small puddles as it goes
    void carveCryoBlob(int cx, int cy, int radius){
        float rad2 = radius * radius + 1f;
        for(int dx = -radius; dx <= radius; dx++){
            for(int dy = -radius; dy <= radius; dy++){
                int rx = dx + cx, ry = dy + cy;
                float n = noise(rx + 500, ry + 500, 3, 0.6f, radius * 0.6f, rad2 * 0.5f);
                if(dx * dx + dy * dy <= rad2 - n){
                    Tile dest = tiles.get(rx, ry);
                    if(dest == null) continue;

                    dest.setFloor(cryoFluidIce.asFloor());
                    if(dest.block().isStatic()){
                        dest.setBlock(cryoFluidWall);
                    }

                    //patches of sticky cryofluid floor within the ice area
                    if(!dest.block().solid){
                        float sn = noise(rx + 1500, ry + 1500, 3, 0.6f, stickyFluidScl, 1f);
                        if(sn > stickyFluidThresh){
                            dest.setFloor(stickyCryoFluidFloor.asFloor());
                        }
                    }

                    //many small puddles scattered through the biome, not one big pool
                    if(!dest.block().solid && dest.overlay() == Blocks.air && rand.chance(cryoBiomePuddleChance)){
                        placeCryoPuddle(rx, ry);
                    }
                }
            }
        }
    }

    //a single small puddle of pooled-cryofluid
    void placeCryoPuddle(int cx, int cy){
        int radius = rand.random(cryoBiomePuddleMinRadius, cryoBiomePuddleMaxRadius);
        float rad2 = radius * radius + 1f;
        for(int dx = -radius; dx <= radius; dx++){
            for(int dy = -radius; dy <= radius; dy++){
                int rx = dx + cx, ry = dy + cy;
                float n = noise(rx + 900, ry + 900, 2, 0.6f, radius * 1.2f, rad2 * 0.6f);
                if(dx * dx + dy * dy <= rad2 - n){
                    Tile dest = tiles.get(rx, ry);
                    if(dest != null && !dest.block().solid){
                        dest.setFloor(cryoFluidPool.asFloor());
                    }
                }
            }
        }
    }

    //maps a floor type to its matching vent block
    private static Block ventFor(Block floor){
        if(floor == darkIceRock) return darkRockVent;
        if(floor == iceRock) return cryonRockVent;
        if(floor == stickyCryoFluidFloor) return cryoFluidVent;
        return cryonVent;
    }

    private static boolean isVentableFloor(Block floor){
        return floor == iceFloor || floor == iceRock || floor == darkIceRock || floor == stickyCryoFluidFloor;
    }

    @Override
    protected void generate(){
        float length = width/2.6f;
        Vec2 trns = Tmp.v1.trns(rand.random(360f), length);
        int
                spawnX = (int)(trns.x + width/2f), spawnY = (int)(trns.y + height/2f),
                endX = (int)(-trns.x + width/2f), endY = (int)(-trns.y + height/2f);
        float maxd = Mathf.dst(width/2f, height/2f);

        erase(spawnX, spawnY, 15);
        brush(pathfind(spawnX, spawnY, endX, endY, tile -> (tile.solid() ? 300f : 0f) + maxd - tile.dst(width/2f, height/2f)/10f, Astar.manhattan), 9);
        erase(endX, endY, 15);

        //make sure enemies have room
        erase(endX, endY, 6);

        tiles.getn(endX, endY).setOverlay(Blocks.spawn);

        //broken ice: scattered coherent patches over plain ice floor
        for(Tile tile : tiles){
            if(tile.floor() == iceFloor && !tile.block().solid){
                float n = noise(tile.x + 3000, tile.y + 3000, 4, 0.6f, brokenIceScl, 1f);
                if(n > brokenIceThresh){
                    tile.setFloor(brokenIce.asFloor());
                }
            }
        }

        //dark ice rock: large connected patches (coherent noise threshold),
        //covering roughly half of all ice rock tiles - not scattered pixels
        for(Tile tile : tiles){
            if(tile.floor() == iceRock && noise(tile.x, tile.y, 4, 0.6f, darkRockScl, 1f) > darkRockThresh){
                tile.setFloor(darkIceRock.asFloor());
            }
        }

        //titanium veins on dark rock: small ~5-tile clusters, guaranteed count
        //(sampled at random valid tiles rather than a tiny per-tile chance)
        {
            int placed = 0, attempts = 0, maxAttempts = titaniumVeinCount * 60;
            while(placed < titaniumVeinCount && attempts++ < maxAttempts){
                int x = rand.random(0, width - 1), y = rand.random(0, height - 1);
                Tile tile = tiles.get(x, y);
                if(tile != null && tile.floor() == darkIceRock && !tile.block().solid
                        && tile.overlay() == Blocks.air && !nearWall(x, y)){
                    placeOreCluster(x, y, oreTitanium, titaniumVeinMinRadius, titaniumVeinMaxRadius);
                    placed++;
                }
            }
        }

        //ores: small clusters (~10 tiles each), guaranteed count regardless
        //of map size (sampled at random valid tiles, not a tiny per-tile chance)
        Block[] oreTypes = {oreCrystalSand, oreDryIce, oreMagnesium, oreSalt, oreAluminum};
        {
            int placed = 0, attempts = 0, maxAttempts = oreClusterCount * 60;
            while(placed < oreClusterCount && attempts++ < maxAttempts){
                int x = rand.random(0, width - 1), y = rand.random(0, height - 1);
                Tile tile = tiles.get(x, y);
                if(tile != null && !tile.block().solid && tile.overlay() == Blocks.air && !nearWall(x, y)){
                    placeOreCluster(x, y, oreTypes[rand.random(oreTypes.length - 1)]);
                    placed++;
                }
            }
        }

        //shattered crystal/ice rubble: sparse decoration near walls
        for(Tile tile : tiles){
            if(!tile.block().solid && tile.overlay() == Blocks.air && nearWall(tile.x, tile.y) && rand.chance(shatterChance)){
                placeOverlay(tile, rand.chance(0.5f) ? shatteredCrystal : shatteredIce);
            }
        }

        //rare, small cryo-fluid biome
        genCryoFluidBiome(spawnX, spawnY, endX, endY);

        trimDark();

        int minVents = rand.random(6, 9);
        int ventCount = 0;

        //vents
        outer:
        for(Tile tile : tiles){
            var floor = tile.floor();
            if(isVentableFloor(floor) && rand.chance(0.002)){
                int radius = 2;
                for(int x = -radius; x <= radius; x++){
                    for(int y = -radius; y <= radius; y++){
                        Tile other = tiles.get(x + tile.x, y + tile.y);
                        if(other == null || !isVentableFloor(other.floor()) || other.block().solid){
                            continue outer;
                        }
                    }
                }

                ventCount ++;
                Block vent = ventFor(floor);
                for(var pos : SteamVent.offsets){
                    Tile other = tiles.get(pos.x + tile.x + 1, pos.y + tile.y + 1);
                    other.setFloor(vent.asFloor());
                }
            }
        }

        int iterations = 0;
        int maxIterations = 5;

        //try to add additional vents, but only several times to prevent infinite loops in bad maps
        while(ventCount < minVents && iterations++ < maxIterations){
            outer:
            for(Tile tile : tiles){
                if(rand.chance(0.00018 * (1 + iterations)) && !Mathf.within(tile.x, tile.y, spawnX, spawnY, 5f)){
                    int radius = 1;
                    for(int x = -radius; x <= radius; x++){
                        for(int y = -radius; y <= radius; y++){
                            Tile other = tiles.get(x + tile.x, y + tile.y);
                            if(other == null || other.block().solid || other.floor().attributes.get(Attribute.steam) != 0){
                                continue outer;
                            }
                        }
                    }

                    var floor = tile.floor();
                    if(floor != iceFloor && floor != iceRock && floor != darkIceRock){
                        continue;
                    }

                    Block vent = floor == darkIceRock ? darkRockVent : floor == iceRock ? cryonRockVent : cryonVent;
                    ventCount ++;
                    for(var pos : SteamVent.offsets){
                        Tile other = tiles.get(pos.x + tile.x + 1, pos.y + tile.y + 1);
                        other.setFloor(vent.asFloor());
                    }

                    int crad = rand.random(6, 14), crad2 = crad * crad;
                    for(int cx = -crad; cx <= crad; cx++){
                        for(int cy = -crad; cy <= crad; cy++){
                            int rx = cx + tile.x, ry = cy + tile.y;
                            float rcy = cy + cx*0.9f;
                            if(cx*cx + rcy*rcy <= crad2 - noise(rx, ry + rx * 2f, 2, 0.7f, 8f, crad2 * 1.1f)){
                                Tile dest = tiles.get(rx, ry);
                                if(dest != null && dest.floor().attributes.get(Attribute.steam) == 0){
                                    Block targetFloor = dest.floor() == iceRock ? iceRock
                                            : dest.floor() == darkIceRock ? darkIceRock
                                              : iceFloor;
                                    dest.setFloor(targetFloor.asFloor());
                                    if(dest.block().isStatic()){
                                        dest.setBlock(iceWall);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        for(Tile tile : tiles){
            if(tile.overlay().needsSurface && !tile.floor().hasSurface()){
                tile.setOverlay(Blocks.air);
            }
        }

        decoration(0.017f);

        state.rules.env = sector.planet.defaultEnv;
        state.rules.placeRangeCheck = true;

        Schematics.placeLaunchLoadout(spawnX, spawnY);
        state.rules.waves = true;
        state.rules.waveTeam = Team.crux;
        float difficulty = sector.threat * 10f;
        state.rules.spawns.clear();

        if(sector.generateEnemyBase){
            state.rules.attackMode = true;
            state.rules.waves = false;

            Tile enemyCoreTile = tiles.getn(endX, endY);
            enemyCoreTile.setBlock(Blocks.coreShard, Team.crux);

            Seq<Tile> enemyCores = Seq.with(enemyCoreTile);
            new BaseGenerator().generate(tiles, enemyCores, tiles.getn(spawnX, spawnY), Team.crux, sector, sector.threat);

            sector.info.attack = true;

        }else{
            state.rules.waves = true;
            state.rules.waveTeam = Team.crux;
            state.rules.winWave = (int)(10*difficulty);

            //todo Find out why WinWave always returns 30.

            //T1 (early / scout)
            UnitType benignitas = CryonContent.unit("benignitas");
            UnitType comet = CryonContent.unit("comet");

            //T1-T2 ground/air
            UnitType buffer = CryonContent.unit("buffer");
            UnitType littorina = CryonContent.unit("littorina");
            UnitType guardian = CryonContent.unit("guardian");
            UnitType natica = CryonContent.unit("natica");

            //T3
            UnitType peak = CryonContent.unit("peak");
            UnitType umbra = CryonContent.unit("umbra");
            UnitType murex = CryonContent.unit("murex");

            //T4 / elite, late-game only
            UnitType salus = CryonContent.unit("salus");
            UnitType sagitta = CryonContent.unit("sagitta");
            UnitType blaze = CryonContent.unit("blaze");
            UnitType charonia = CryonContent.unit("charonia");

            boolean groundAttack = Mathf.randomBoolean();

            // This might be too difficult.
            if(groundAttack){

                state.rules.spawns.add(new SpawnGroup(benignitas){{
                    unitAmount = 5;
                    max = (int)(30 * difficulty);
                    spacing = Math.max(1, 3 - (int)(difficulty / 5));
                    unitScaling = 1;
                }});

                state.rules.spawns.add(new SpawnGroup(buffer){{
                    unitAmount = 4;
                    max = (int)(25 * difficulty);
                    spacing = Math.max(1, 3 - (int)(difficulty / 5));
                    unitScaling = 1;
                }});

                state.rules.spawns.add(new SpawnGroup(guardian){{
                    begin = 5;
                    unitAmount = 2;
                    max = (int)(18 * difficulty);
                    spacing = Math.max(1, 3 - (int)(difficulty / 5));
                    unitScaling = 2;
                }});

                state.rules.spawns.add(new SpawnGroup(peak){{
                    begin = 10;
                    unitAmount = 1;
                    max = (int)(10 * difficulty);
                    spacing = Math.max(1, 3 - (int)(difficulty / 5));
                    unitScaling = 3;
                }});

                state.rules.spawns.add(new SpawnGroup(umbra){{
                    begin = 16;
                    unitAmount = 1;
                    max = (int)(6 * difficulty);
                    spacing = Math.max(2, 4 - (int)(difficulty / 5));
                    unitScaling = 3;
                }});

                state.rules.spawns.add(new SpawnGroup(salus){{
                    begin = 24;
                    unitAmount = 1;
                    max = (int)(3 * difficulty);
                    spacing = Math.max(3, 6 - (int)(difficulty / 5));
                    unitScaling = 4;
                }});

            }else{

                state.rules.spawns.add(new SpawnGroup(comet){{
                    unitAmount = 5;
                    max = (int)(30 * difficulty);
                    spacing = Math.max(1, 3 - (int)(difficulty / 5));
                    unitScaling = 1;
                }});

                state.rules.spawns.add(new SpawnGroup(littorina){{
                    unitAmount = 3;
                    max = (int)(25 * difficulty);
                    spacing = Math.max(1, 3 - (int)(difficulty / 5));
                    unitScaling = 1;
                }});

                state.rules.spawns.add(new SpawnGroup(natica){{
                    begin = 5;
                    unitAmount = 2;
                    max = (int)(18 * difficulty);
                    spacing = Math.max(1, 3 - (int)(difficulty / 5));
                    unitScaling = 2;
                }});

                state.rules.spawns.add(new SpawnGroup(murex){{
                    begin = 10;
                    unitAmount = 1;
                    max = (int)(10 * difficulty);
                    spacing = Math.max(1, 3 - (int)(difficulty / 5));
                    unitScaling = 3;
                }});

                state.rules.spawns.add(new SpawnGroup(sagitta){{
                    begin = 18;
                    unitAmount = 1;
                    max = (int)(6 * difficulty);
                    spacing = Math.max(2, 4 - (int)(difficulty / 5));
                    unitScaling = 3;
                }});

                state.rules.spawns.add(new SpawnGroup(charonia){{
                    begin = 26;
                    unitAmount = 1;
                    max = (int)(3 * difficulty);
                    spacing = Math.max(3, 6 - (int)(difficulty / 5));
                    unitScaling = 4;
                }});

            }
        }

        if(sector.hasEnemyBase()){

            state.rules.attackMode = sector.info.attack = true;
        }
    }
}