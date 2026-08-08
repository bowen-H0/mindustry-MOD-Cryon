package example;

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
import static mindustry.Vars.*;

public class CryonPlanetGenerator extends PlanetGenerator{
    public float heightScl = 0.9f, octaves = 8, persistence = 0.7f, heightPow = 3f, heightMult = 1.6f;

    //TODO inline/remove
    public static float arkThresh = 0.28f, arkScl = 0.83f;
    public static int arkSeed = 7, arkOct = 2;
    public static float liqThresh = 0.64f, liqScl = 87f, redThresh = 3.1f, noArkThresh = 0.3f;
    public static int crystalSeed = 8, crystalOct = 2;
    public static float crystalScl = 0.9f, crystalMag = 0.3f;
    public static float airThresh = 0.13f, airScl = 14;
    private static Block iceWall;
    private static Block iceFloor;
    private static Block iceRock;
    private static Block cryonVent;
    private static Block cryonRockVent;
    private static Block oreCrystalSand;
    private static Block oreDryIce;
    private static Block oreMagnesium;
    private static Block oreSalt;
    private static Block oreAluminum;
    static {
        iceWall = Vars.content.block("cryon-cryon-ice-wall");
        if(iceWall == null) {
            Log.warn("cryon-ice-wall not found, using stone wall");
            iceWall = Blocks.stoneWall;
        }

        iceFloor = Vars.content.block("cryon-cryon-ice");
        if(iceFloor == null) {
            Log.warn("cryon-ice not found, using snow");
            iceFloor = Blocks.snow;
        }

        iceRock = Vars.content.block("cryon-cryon-ice-rock");
        if(iceRock == null) {
            Log.warn("cryon-ice-rock not found, using stone");
            iceRock = Blocks.stone;
        }

        cryonVent = Vars.content.block("cryon-cryon-vent");
        if(cryonVent == null) {
            Log.warn("cryon-vent not found, using rhyoliteVent");
            cryonVent = Blocks.rhyoliteVent;
        }

        cryonRockVent = Vars.content.block("cryon-cryon-rock-vent");
        if(cryonRockVent == null) {
            Log.warn("cryon-rock-vent not found, using rhyoliteVent");
            cryonRockVent = Blocks.rhyoliteVent;
        }

        oreCrystalSand = Vars.content.block("cryon-ore-crystal-sand");
        if(oreCrystalSand == null) {
            Log.warn("ore-crystal-sand not found, using oreCopper");
            oreCrystalSand = Blocks.oreCopper;
        }

        oreDryIce = Vars.content.block("cryon-ore-dry-ice");
        if(oreDryIce == null) {
            Log.warn("ore-dry-ice not found, using oreLead");
            oreDryIce = Blocks.oreLead;
        }

        oreMagnesium = Vars.content.block("cryon-ore-magnesium");
        if(oreMagnesium == null) {
            Log.warn("ore-magnesium not found, using oreTitanium");
            oreMagnesium = Blocks.oreTitanium;
        }

        oreSalt = Vars.content.block("cryon-ore-salt");
        if(oreSalt == null) {
            Log.warn("ore-salt not found, using oreCoal");
            oreSalt = Blocks.oreCoal;
        }

        oreAluminum = Vars.content.block("cryon-ore-aluminum");
        if(oreAluminum == null) {
            Log.warn("ore-aluminum not found, using oreThorium");
            oreAluminum = Blocks.oreThorium;
        }
    }

    Block[] terrain = {iceFloor, iceRock};

    {
        baseSeed = 2;
        defaultLoadout = Loadouts.basicBastion;
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
        tile.floor = getBlock(position);
        tile.block = iceWall;

        if(Ridged.noise3d(seed + 1, position.x, position.y, position.z, 2, airScl) > airThresh){
            tile.block = Blocks.air;
        }
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

        //ores
        pass((x, y) -> {
            if(!nearWall(x, y)){
                float n1 = noise(x + 150, y + x*2 + 100, 4, 0.8f, 55f, 1f);
                float n2 = noise(x + 999, y + 600 - x, 4, 0.8f, 55f, 1f);
                float n3 = noise(x + 2345, y - x*3 + 800, 4, 0.8f, 55f, 1f);
                float n4 = noise(x + 4567, y + x*1.5f + 200, 4, 0.8f, 55f, 1f);
                float n5 = noise(x + 7890, y - x*2.5f + 400, 4, 0.8f, 55f, 1f);

                if(n1 > 0.72f){
                    ore = oreCrystalSand;
                }else if(n2 > 0.72f){
                    ore = oreDryIce;
                }else if(n3 > 0.72f){
                    ore = oreMagnesium;
                }else if(n4 > 0.72f){
                    ore = oreSalt;
                }else if(n5 > 0.72f){
                    ore = oreAluminum;
                }
            }
        });

        trimDark();

        int minVents = rand.random(6, 9);
        int ventCount = 0;

        //vents
        outer:
        for(Tile tile : tiles){
            var floor = tile.floor();
            if((floor == iceFloor || floor == iceRock) && rand.chance(0.002)){
                int radius = 2;
                for(int x = -radius; x <= radius; x++){
                    for(int y = -radius; y <= radius; y++){
                        Tile other = tiles.get(x + tile.x, y + tile.y);
                        if(other == null || (other.floor() != iceFloor && other.floor() != iceRock) || other.block().solid){
                            continue outer;
                        }
                    }
                }

                ventCount ++;
                Block vent = floor == iceRock ? cryonRockVent : cryonVent;
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
                    if(floor != iceFloor && floor != iceRock){
                        continue;
                    }

                    Block vent = floor == iceRock ? cryonRockVent : cryonVent;
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
                                    Block targetFloor = dest.floor() == iceRock ? iceRock : iceFloor;
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
        state.rules.winWave = (int)(10*difficulty);
        state.rules.spawns.clear();
        //todo Find out why WinWave always returns 30.

        //T3
        UnitType peak  = Vars.content.unit("cryon-peak");
        UnitType umbra = Vars.content.unit("cryon-umbra");
        UnitType murex = Vars.content.unit("cryon-murex");
        //ground
        UnitType buffer   = Vars.content.unit("cryon-buffer");
        UnitType guardian = Vars.content.unit("cryon-guardian");

        //air
        UnitType bolide = Vars.content.unit("cryon-bolide");
        UnitType littorina = Vars.content.unit("cryon-littorina");
        UnitType natica = Vars.content.unit("cryon-natica");

        boolean groundAttack = Mathf.randomBoolean();

        // This might be too difficult.
        if(groundAttack){

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

        }else{

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

        }


        state.rules.waveSpacing = 60 * 30;
    }
}