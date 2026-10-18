package cryon.Generator;

import arc.graphics.Color;
import arc.math.Mathf;
import arc.math.geom.Vec3;
import arc.struct.Seq;
import arc.struct.StringMap;
import arc.util.noise.Simplex;
import cryon.Content.AravisEnvironment;
import cryon.Features.CryonContent;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.Schematic;
import mindustry.game.Schematics;
import mindustry.game.SpawnGroup;
import mindustry.maps.generators.PlanetGenerator;
import mindustry.type.Sector;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.TileGen;

/** Small, dry survival sectors with a guaranteed landing area and supply veins. */
public class AravisPlanetGenerator extends PlanetGenerator {
    public AravisPlanetGenerator(){
        baseSeed = 37;
        defaultLoadout = new Schematic(Seq.with(new Schematic.Stile(block("core-conquest"), 1, 1, null, (byte)0)),
                new StringMap(), 3, 3);
    }

    private Block block(String name){
        Block result = CryonContent.block(name);
        if(result == null) throw new IllegalStateException("Missing Aravis terrain: " + name);
        return result;
    }

    private float terrainHeight(Vec3 position){
        return Simplex.noise3d(baseSeed, 4, 0.55, 2.4,
                position.x + 10f, position.y + 10f, position.z + 10f);
    }

    @Override
    public float getHeight(Vec3 position){
        return 0.15f + terrainHeight(position) * 0.25f;
    }

    @Override
    public void getColor(Vec3 position, Color out){
        out.set(terrainHeight(position) > 0.58f ? Color.valueOf("a8855c") : Color.valueOf("9c9284"));
    }

    @Override
    public int getSectorSize(Sector sector){
        return 192;
    }

    @Override
    public void generateSector(Sector sector){
        // Random sectors are survival maps; named sectors keep their authored rules.
        sector.generateEnemyBase = false;
    }

    @Override
    protected void genTile(Vec3 position, TileGen tile){
        tile.floor = block(terrainHeight(position) > 0.58f ? "gravel-sand" : "dune-sand");
        tile.block = Blocks.air;
    }

    private float sample(int offset, float x, float y, float scale){
        return Simplex.noise2d(seed + sector.id * 131 + offset, 3, 0.55, 1f / scale, x, y);
    }

    private void clearArea(int x, int y, int radius){
        erase(x, y, radius);
        for(int dx = -radius; dx <= radius; dx++){
            for(int dy = -radius; dy <= radius; dy++){
                Tile tile = tiles.get(x + dx, y + dy);
                if(tile != null && dx * dx + dy * dy <= radius * radius){
                    tile.setOverlay(Blocks.air);
                }
            }
        }
    }

    private void vein(int x, int y, int radius, Block ore){
        for(int dx = -radius; dx <= radius; dx++){
            for(int dy = -radius; dy <= radius; dy++){
                Tile tile = tiles.get(x + dx, y + dy);
                if(tile != null && !tile.block().solid && tile.overlay() == Blocks.air
                        && dx * dx + dy * dy <= radius * radius){
                    tile.setOverlay(ore);
                }
            }
        }
    }

    @Override
    protected void generate(){
        Block dunes = block("dune-sand"), sand = block("aeolian-sand");
        Block red = block("red-sand"), gravel = block("gravel-sand");
        Block duneWall = block("dune-sand-wall"), sandWall = block("aeolian-sand-wall");
        Block redWall = block("red-sand-wall");
        Block iron = block("ore-ferrum"), manganese = block("ore-manganese"), nickel = block("ore-nickel");

        for(Tile tile : tiles){
            float biome = sample(0, tile.x, tile.y, 80f);
            Block ground = biome < 0.36f ? red : biome > 0.64f ? gravel
                    : sample(11, tile.x, tile.y, 36f) > 0.53f ? sand : dunes;
            tile.setFloor(ground.asFloor());
            tile.setOverlay(Blocks.air);
            boolean edge = tile.x < 2 || tile.y < 2 || tile.x >= width - 2 || tile.y >= height - 2;
            boolean ridge = sample(23, tile.x, tile.y, 26f) > 0.61f;
            tile.setBlock(edge || ridge ? (ground == red ? redWall : ground == sand ? sandWall : duneWall) : Blocks.air);
        }

        int spawnX = width / 2, spawnY = height / 5;
        int enemyX = width / 2 + rand.random(-width / 5, width / 5), enemyY = height * 4 / 5;
        clearArea(spawnX, spawnY, 18);
        clearArea(enemyX, enemyY, 12);
        // Wide, gently curved route guarantees that ground waves can reach the core.
        int steps = Math.max(Math.abs(enemyX - spawnX), enemyY - spawnY);
        for(int i = 0; i <= steps; i++){
            float t = i / (float)steps;
            int x = Math.round(Mathf.lerp(spawnX, enemyX, t) + Mathf.sin(t * Mathf.PI2) * width * 0.06f);
            int y = Math.round(Mathf.lerp(spawnY, enemyY, t));
            erase(x, y, 6);
        }
        inverseFloodFill(tiles.get(spawnX, spawnY));

        Block[] ores = {iron, Blocks.oreCopper, Blocks.oreCoal, manganese, nickel};
        for(Block ore : ores){
            int count = ore == iron ? 25 : ore == nickel ? 10 : 18;
            for(int i = 0; i < count; i++){
                int x = rand.random(8, width - 9), y = rand.random(8, height - 9);
                if(Mathf.dst(x, y, spawnX, spawnY) > 20f){
                    vein(x, y, rand.random(2, 4), ore);
                }
            }
        }
        // Fixed nearby veins prevent an unlucky seed from blocking the early economy.
        vein(spawnX - 10, spawnY + 2, 3, iron);
        vein(spawnX + 10, spawnY + 2, 3, Blocks.oreCopper);
        vein(spawnX - 7, spawnY + 12, 3, Blocks.oreCoal);
        vein(spawnX + 7, spawnY + 12, 2, manganese);
        vein(enemyX + 7, enemyY, 3, nickel);
        tiles.get(enemyX, enemyY).setOverlay(Blocks.spawn);
        Schematics.placeLaunchLoadout(spawnX, spawnY);

        Vars.state.rules.attackMode = false;
        Vars.state.rules.waves = true;
        Vars.state.rules.winWave = 30;
        Vars.state.rules.waveSpacing = 60f * 90f;
        Vars.state.rules.spawns.clear();
        addWave("jaspis", 0, 1, 4, 12);
        addWave("obsidianum", 9, 1, 5, 6);
        addWave("nitrum", 19, 1, 6, 3);
        AravisEnvironment.applyWeather(Vars.state.rules);
    }

    private void addWave(String name, int begin, int amount, int spacing, int max){
        UnitType unit = CryonContent.unit(name);
        if(unit == null) throw new IllegalStateException("Missing Aravis wave unit: " + name);
        SpawnGroup group = new SpawnGroup(unit);
        group.begin = begin;
        group.unitAmount = amount;
        group.spacing = spacing;
        group.unitScaling = 4f;
        group.max = max;
        Vars.state.rules.spawns.add(group);
    }
}
