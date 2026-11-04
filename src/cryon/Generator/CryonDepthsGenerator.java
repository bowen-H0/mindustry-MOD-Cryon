package cryon.Generator;

import arc.graphics.Color;
import arc.math.Mathf;
import arc.math.geom.Vec3;
import arc.struct.Seq;
import arc.struct.StringMap;
import arc.util.noise.Simplex;
import cryon.Features.CryonContent;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.content.UnitTypes;
import mindustry.game.*;
import mindustry.maps.generators.PlanetGenerator;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.turrets.ItemTurret;

/** Tantros-like seabed: living red/blue reefs, connected sea lanes and sheltered bases. */
public class CryonDepthsGenerator extends PlanetGenerator{
    private static final Color sea = Color.valueOf("272766"), shore = Color.valueOf("5057a6");

    public CryonDepthsGenerator(){
        baseSeed = 271;
        defaultLoadout = new Schematic(Seq.with(new Schematic.Stile(block("core-pioneer"), 1, 1, null, (byte)0)), new StringMap(), 4, 4);
    }

    private static Block block(String name){ return CryonContent.block(name); }
    private float ocean(Vec3 p){ return Simplex.noise3d(baseSeed, 4, 0.55, 2.7, p.x + 10, p.y + 10, p.z + 10); }
    @Override public float getHeight(Vec3 p){ return 0f; }
    @Override public void getColor(Vec3 p, Color out){ out.set(sea).lerp(shore, ocean(p)); }
    @Override public int getSectorSize(Sector sector){ return 224; }
    @Override public void generateSector(Sector sector){ sector.generateEnemyBase = sector.id == 2 || sector.id == 4 || sector.id == 5; }
    @Override public boolean allowLanding(Sector sector){ return sector.preset != null && sector.preset.unlocked(); }
    @Override protected void genTile(Vec3 p, TileGen tile){ tile.floor = Blocks.deepwater; tile.block = Blocks.air; }

    private void island(int x, int y, int radius){
        for(int dx = -radius - 3; dx <= radius + 3; dx++) for(int dy = -radius - 3; dy <= radius + 3; dy++){
            Tile tile = tiles.get(x + dx, y + dy);
            if(tile == null) continue;
            float distance = Mathf.sqrt(dx * dx + dy * dy);
            float edge = radius + Simplex.noise2d(seed, 2, 0.5, 0.16, x + dx, y + dy) * 2f;
            if(distance < edge + 2f){
                tile.setFloor((distance < edge ? block("cryon-ice-rock") : Blocks.water).asFloor());
                tile.setBlock(Blocks.air);
            }
        }
    }

    private void ore(int x, int y, String name){
        for(int dx = -3; dx <= 3; dx++) for(int dy = -3; dy <= 3; dy++){
            Tile t = tiles.get(x + dx, y + dy);
            if(t != null && dx * dx + dy * dy <= 10 && !t.floor().isLiquid) t.setOverlay(block("ore-" + name));
        }
    }

    @Override protected void generate(){
        int stage = Math.min(5, sector.id);
        int px = width / 4, py = height / 5, ex = width * 3 / 4, ey = height * 4 / 5;
        for(Tile tile : tiles){
            tile.setFloor(Blocks.deepwater.asFloor());
            int edge = Math.min(Math.min(tile.x, tile.y), Math.min(width - 1 - tile.x, height - 1 - tile.y));
            float ridge = Simplex.noise2d(seed + stage * 17, 3, 0.55, 0.045, tile.x, tile.y);
            tile.setBlock(edge < 3 + ridge * 7 ? block("cryon-ice-rock-wall") : Blocks.air);
            if(tile.block() != Blocks.air) tile.setFloor(block("cryon-dark-ice-rock").asFloor());
            tile.setOverlay(Blocks.air);
        }
        // Keep the middle of the sea open even as resource-island positions vary.
        island(px, py, 23);
        island(ex, ey, 11);
        island(39, 117, 17);
        island(184, 88, 18);
        island(108, 157, 14);
        island(112, 49, 13);
        if(stage >= 1) island(145, 113, 13 + stage);
        if(stage >= 2) island(61, 176, 15 + stage);
        if(stage == 3){ island(101, 106, 18); island(100, 205, 10); }
        if(stage >= 4){ island(105, 104, 16); island(196, 148, 12); }
        // Keep a clear supply strip at the edge of the starting island.
        for(int x = px + 21; x <= px + 24; x++) for(int y = py - 5; y <= py - 3; y++){
            tiles.get(x, y).setFloor(block("cryon-ice-rock").asFloor());
        }
        ore(px - 11, py - 7, "aluminum");
        ore(px - 11, py + 7, "magnesium");
        ore(px + 4, py + 14, "dry-ice");
        ore(px + 13, py + 5, "crystal-sand");
        ore(px + 5, py - 12, "salt");
        ore(35, 115, "aluminum"); ore(44, 121, "magnesium");
        ore(180, 85, "crystal-sand"); ore(189, 93, "dry-ice");
        ore(105, 157, "magnesium"); ore(115, 48, "aluminum");
        // Scrap supplies titanium through the existing Cryon processing chain.
        for(int dx = -3; dx <= 3; dx++) for(int dy = -3; dy <= 3; dy++){
            tiles.get(108 + dx, 164 + dy).setOverlay(Blocks.oreScrap);
        }
        for(int dx = -1; dx <= 1; dx++) for(int dy = -1; dy <= 1; dy++){
            tiles.get(px + 7 + dx, py - 4 + dy).setFloor(block("cryon-rock-vent").asFloor());
        }
        Schematics.placeLaunchLoadout(px, py);
        // Retain the starting power supply for the player's industry.
        Tile power = tiles.get(px + 7, py - 4);
        power.setBlock(block("hydrothermal-generator"), Team.sharded);
        Tile node = tiles.get(px + 17, py - 4);
        node.setFloor(block("cryon-ice-rock").asFloor());
        node.setBlock(block("aluminum-node"), Team.sharded);
        node.build.configureAny(power.pos());
        Vars.state.rules.defaultTeam = Team.sharded;
        Vars.state.rules.waveTeam = Team.crux;
        Vars.state.rules.attackMode = stage == 2 || stage >= 4;
        Vars.state.rules.winWave = Vars.state.rules.attackMode ? 0 : 12 + stage * 6;
        Vars.state.rules.waves = true;
        Vars.state.rules.waveSpacing = 60f * (stage == 0 ? 85 : 65);
        Vars.state.rules.spawns.clear();
        SpawnGroup boats = new SpawnGroup(UnitTypes.risso);
        boats.begin = 0; boats.spacing = 2; boats.unitScaling = 4; boats.max = 5 + stage;
        Vars.state.rules.spawns.add(boats);
        SpawnGroup air = new SpawnGroup(UnitTypes.flare);
        air.begin = 4; air.spacing = 3; air.unitScaling = 5; air.max = 3 + stage;
        Vars.state.rules.spawns.add(air);
        if(stage >= 1){
            SpawnGroup escort = new SpawnGroup(UnitTypes.minke);
            escort.begin = 8; escort.spacing = 5; escort.unitScaling = 8; escort.max = 1 + stage / 2;
            Vars.state.rules.spawns.add(escort);
        }
        tiles.get(ex - 18, ey - 12).setOverlay(Blocks.spawn);
        if(Vars.state.rules.attackMode){
            tiles.get(ex, ey).setBlock(Blocks.coreShard, Team.crux);
            for(int side : new int[]{-1, 1}){
                Tile turret = tiles.get(ex + side * 6, ey - 4);
                turret.setBlock(stage >= 4 ? Blocks.salvo : Blocks.duo, Team.crux);
                if(turret.build != null && turret.block() instanceof ItemTurret) turret.build.items.add(Items.copper, 200);
                for(int dx = -2; dx <= 2; dx++) tiles.get(ex + side * 6 + dx, ey - 6).setBlock(Blocks.copperWall, Team.crux);
            }
        }
        sector.info.attack = Vars.state.rules.attackMode;
        sector.info.winWave = Vars.state.rules.winWave;
        var core = Team.sharded.core();
        core.items.add(CryonContent.item("aluminum"), 650);
        core.items.add(CryonContent.item("magnesium"), 400);
        core.items.add(Items.graphite, 180);
        core.items.add(Items.silicon, 160);
        if(stage >= 2) core.items.add(Items.titanium, 100);
        Vars.state.rules.loadout = ItemStack.list(CryonContent.item("aluminum"), 650,
                CryonContent.item("magnesium"), 400, Items.graphite, 180, Items.silicon, 160);
        cryon.Content.CryonDepthsEnvironment.applyTerrain(tiles);
        cryon.Content.CryonDepthsEnvironment.applyRules(Vars.state.rules);
    }
}
