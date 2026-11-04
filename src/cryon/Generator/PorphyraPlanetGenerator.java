package cryon.Generator;

import arc.graphics.Color;
import arc.math.Mathf;
import arc.math.geom.Vec3;
import arc.struct.Seq;
import arc.struct.StringMap;
import arc.util.noise.Simplex;
import cryon.Features.CryonContent;
import cryon.Features.NormaTeam;
import cryon.Content.PorphyraEnvironment;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.Schematic;
import mindustry.game.Schematics;
import mindustry.game.SpawnGroup;
import mindustry.game.Team;
import mindustry.maps.generators.PlanetGenerator;
import mindustry.type.Sector;
import mindustry.type.UnitType;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.TileGen;
import mindustry.world.blocks.defense.turrets.ItemTurret;

/** Recrystallized crust, impact glass and pale phase-change mountain chains. */
public class PorphyraPlanetGenerator extends PlanetGenerator{
    private static final String[] ores = {"ore-rhenium", "ore-technetium", "ore-promethium", "ore-neptunium", "ore-oganesson"};

    public PorphyraPlanetGenerator(){
        baseSeed = 83;
        defaultLoadout = new Schematic(Seq.with(new Schematic.Stile(block("core-thread"), 1, 1, null, (byte)0)),
                new StringMap(), 3, 3);
    }

    private Block block(String name){
        Block result = CryonContent.block(name);
        if(result == null) throw new IllegalStateException("Missing Porphyra block: " + name);
        return result;
    }

    private float noise(Vec3 pos){
        return Simplex.noise3d(baseSeed, 4, 0.55, 2.2, pos.x + 10, pos.y + 10, pos.z + 10);
    }

    @Override public float getHeight(Vec3 pos){ return 0.15f + noise(pos) * 0.35f; }

    @Override public void getColor(Vec3 pos, Color out){
        float n = noise(pos);
        out.set(Color.valueOf(n > 0.68f ? "d6c9df" : n < 0.32f ? "34213e" : "805592"));
    }

    @Override public int getSectorSize(Sector sector){ return 192; }

    @Override public void generateSector(Sector sector){ sector.generateEnemyBase = sector.id < 10; }

    @Override protected void genTile(Vec3 pos, TileGen tile){
        float n = noise(pos);
        tile.floor = block(n > 0.68f ? "porphyra-pale-stone" : n < 0.32f ? "porphyra-glass" : "porphyra-crystal");
        tile.block = Blocks.air;
    }

    private float sample(int offset, float x, float y, float scale){
        return Simplex.noise2d(seed + sector.id * 137 + offset, 3, 0.55, 1f / scale, x, y);
    }

    private void clearArea(int x, int y, int radius){
        erase(x, y, radius);
        for(int dx = -radius; dx <= radius; dx++) for(int dy = -radius; dy <= radius; dy++){
            Tile tile = tiles.get(x + dx, y + dy);
            if(tile != null && dx * dx + dy * dy <= radius * radius){
                tile.setOverlay(Blocks.air);
                if(tile.floor().isLiquid) tile.setFloor(block("porphyra-glass").asFloor());
            }
        }
    }

    private void vein(int x, int y, int radius, Block ore){
        for(int dx = -radius; dx <= radius; dx++) for(int dy = -radius; dy <= radius; dy++){
            Tile tile = tiles.get(x + dx, y + dy);
            if(tile != null && !tile.floor().isLiquid && !tile.block().solid && tile.overlay() == Blocks.air
                    && dx * dx + dy * dy <= radius * radius) tile.setOverlay(ore);
        }
    }

    @Override protected void generate(){
        int stage = Math.min(9, Math.max(0, sector.id));
        int oreTier = Math.min(4, stage / 2);
        Block crystal = block("porphyra-crystal"), glass = block("porphyra-glass"), pale = block("porphyra-pale-stone");
        Block glassWall = block("porphyra-glass-wall"), mountain = block("porphyra-pale-wall");
        for(Tile tile : tiles){
            float phase = sample(3, tile.x, tile.y, 72f);
            float ridge = sample(29, tile.x, tile.y, 32f);
            boolean high = phase > 0.68f || ridge > 0.71f;
            boolean vitrified = phase < 0.31f;
            tile.setFloor((high ? pale : vitrified ? glass : crystal).asFloor());
            tile.setOverlay(Blocks.air);
            boolean edge = tile.x < 2 || tile.y < 2 || tile.x >= width - 2 || tile.y >= height - 2;
            tile.setBlock(edge || (high && ridge > 0.57f) || (vitrified && ridge < 0.25f)
                    ? (vitrified ? glassWall : mountain) : Blocks.air);
        }

        // Side basins keep both liquids available without flooding the central assault route.
        for(Tile tile : tiles){
            if(tile.x < 3 || tile.y < 3 || tile.x >= width - 3 || tile.y >= height - 3) continue;
            float brineDistance = Mathf.dst(tile.x, tile.y, width / 6f, height * 0.46f);
            float slurryDistance = Mathf.dst(tile.x, tile.y, width * 5f / 6f, height * 0.54f);
            float shore = 12f + sample(97, tile.x, tile.y, 18f) * 5f;
            String liquid = brineDistance < shore ? "gamma-brine" : slurryDistance < shore ? "crystal-slurry" : null;
            if(liquid != null){
                float distance = liquid.equals("gamma-brine") ? brineDistance : slurryDistance;
                tile.setBlock(Blocks.air);
                tile.setFloor(block("porphyra-" + liquid + (distance < 6f ? "-deep" : "")).asFloor());
            }
        }

        int spawnX = width / 2, spawnY = height / 5;
        int enemyX = width / 2 + rand.random(-width / 5, width / 5), enemyY = height * 4 / 5;
        clearArea(spawnX, spawnY, 18);
        clearArea(enemyX, enemyY, stage == 9 ? 19 : 15);
        int steps = Math.max(Math.abs(enemyX - spawnX), enemyY - spawnY);
        for(int i = 0; i <= steps; i++){
            float t = i / (float)steps;
            int x = Math.round(Mathf.lerp(spawnX, enemyX, t) + Mathf.sin(t * Mathf.PI2) * width * 0.05f);
            clearArea(x, Math.round(Mathf.lerp(spawnY, enemyY, t)), 6);
        }
        inverseFloodFill(tiles.get(spawnX, spawnY));

        for(int tier = 0; tier <= oreTier; tier++){
            Block ore = block(ores[tier]);
            int count = 28 - tier * 4;
            for(int i = 0; i < count; i++){
                int x = rand.random(8, width - 9), y = rand.random(8, height - 9);
                if(Mathf.dst(x, y, spawnX, spawnY) > 19f) vein(x, y, rand.random(2, 4), ore);
            }
        }
        Block gamma = block("ore-gamma-crystal");
        for(int i = 0; i < 24; i++){
            int x = rand.random(8, width - 9), y = rand.random(8, height - 9);
            if(Mathf.dst(x, y, spawnX, spawnY) > 19f) vein(x, y, rand.random(2, 4), gamma);
        }
        // Two starter resources are guaranteed inside the cleared landing area.
        vein(spawnX + 6, spawnY - 10, 3, gamma);
        vein(spawnX - 10, spawnY + 3, 3, block(ores[0]));
        if(oreTier >= 1) vein(spawnX + 10, spawnY + 3, 3, block(ores[1]));
        // Every campaign map is an assault on an occupied defensive position.
        String enemyCore = stage == 9 ? "ring-control-center"
                : stage >= 8 ? "core-kernel" : stage >= 4 ? "core-process" : "core-thread";
        tiles.get(enemyX, enemyY).setBlock(block(enemyCore), NormaTeam.team);
        placeDefenses(spawnX, spawnY, enemyX, enemyY, stage);
        int waveX = Math.min(width - 9, enemyX + 22);
        clearArea(waveX, enemyY + 5, 3);
        tiles.get(waveX, enemyY + 5).setOverlay(Blocks.spawn);
        cryon.Features.PorphyraCampaign.setFactions(Vars.state.rules);
        Schematics.placeLaunchLoadout(spawnX, spawnY);
        Vars.state.rules.waves = true;
        Vars.state.rules.attackMode = true;
        sector.info.attack = true;
        Vars.state.rules.waveTeam = NormaTeam.team;
        Vars.state.rules.winWave = 0;
        Vars.state.rules.waveSpacing = 60f * 85f;
        Vars.state.rules.spawns.clear();
        String[] hover = {"receptor", "synapse", "ganglion", "cortex", "connectome"};
        String[] tanks = {"monomer", "dimer", "oligomer", "polymer", "macromolecule"};
        String[] air = {"particle", "momentum", "inertia", "gravity", "singularity"};
        int enemyTier = Math.min(4, stage / 2);
        addWave(unit(hover[0]), 0, 3, 7);
        addWave(unit(tanks[0]), 3, 4, 6);
        addWave(unit(air[0]), 6, 5, 5);
        for(int tier = 1; tier <= enemyTier; tier++){
            int first = 7 + tier * 5;
            addWave(unit(hover[tier]), first, 6 + tier, 5 - tier);
            addWave(unit(tanks[tier]), first + 2, 7 + tier, 5 - tier);
            addWave(unit(air[tier]), first + 4, 8 + tier, 5 - tier);
        }
        PorphyraEnvironment.applyWeather(Vars.state.rules);
    }

    private void placeDefenses(int spawnX, int spawnY, int enemyX, int enemyY, int stage){
        String[] turret = {"etch", "excite", "pulse", "pulse", "deflect", "deflect",
                "detune", "overload", "overload", "collapse"};
        String[] ammo = {"rhenium", "rhenium", "technetium", "technetium", "promethium",
                "promethium", "neptunium", "neptunium", "neptunium", "oganesson"};
        int lines = 1 + stage / 3;
        for(int line = 0; line < lines; line++){
            float t = (line + 1f) / (lines + 1f);
            int cx = Math.round(Mathf.lerp(spawnX, enemyX, t));
            int cy = Math.round(Mathf.lerp(spawnY, enemyY, t));
            for(int side : new int[]{-1, 1}){
                int x = cx + side * (8 + stage / 4);
                clearArea(x, cy, 4);
                Tile tile = tiles.get(x, cy);
                if(tile == null) continue;
                tile.setBlock(block("porphyra-" + turret[stage]), NormaTeam.team);
                if(tile.build instanceof ItemTurret.ItemTurretBuild){
                    Item item = CryonContent.item(ammo[stage]);
                    for(int i = 0; i < 24; i++) tile.build.handleItem(null, item);
                }
            }
        }
    }

    private UnitType unit(String name){
        UnitType result = CryonContent.unit(name);
        if(result == null) throw new IllegalStateException("Missing Porphyra unit: " + name);
        return result;
    }

    private void addWave(UnitType type, int begin, int spacing, int max){
        SpawnGroup group = new SpawnGroup(type);
        group.begin = begin;
        group.spacing = spacing;
        group.unitAmount = 1;
        group.unitScaling = 5f;
        group.max = max;
        Vars.state.rules.spawns.add(group);
    }
}
