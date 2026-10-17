package cryon.Story;

import arc.files.Fi;
import arc.struct.StringMap;
import cryon.CryonJavaMod;
import cryon.Type.JavaProcessor.JavaProcessorBuild;
import mindustry.content.Blocks;
import mindustry.game.Rules;
import mindustry.game.Team;
import mindustry.io.MapIO;
import mindustry.maps.Map;
import mindustry.world.Tile;

import static mindustry.Vars.*;

/** Deterministic, editor-compatible native Mindustry map. */
public final class IntrusionMap {
    public static final String mapKey = "cryon-intrusion-map";
    public static final int width = 144, height = 144;
    public static final int left = 24, right = 119, bottom = 24, top = 119;
    public static final float visibleMinX = (left - 0.5f) * 8f, visibleMinY = (bottom - 0.5f) * 8f;
    public static final float visibleMaxX = (right + 0.5f) * 8f, visibleMaxY = (top + 0.5f) * 8f;
    public static final float playerX = (width - 1) * 4f, playerY = 42 * 8f;
    public static final float nodeX = (width - 1) * 4f, nodeY = (height - 1) * 4f;

    public static boolean active(){ return state != null && state.rules.tags.getBool(mapKey); }

    public static Rules rules(){
        Rules rules = new Rules();
        rules.tags.put(mapKey, "true");
        rules.waves = false;
        rules.waveTimer = false;
        rules.attackMode = false;
        rules.canGameOver = false;
        rules.defaultTeam = Team.sharded;
        rules.waveTeam = Team.crux;
        rules.blockWhitelist = true; // An empty whitelist bans every player build plan.
        rules.buildSpeedMultiplier = 0f;
        rules.unitBuildSpeedMultiplier = 0f;
        rules.logicUnitControl = false;
        rules.possessionAllowed = false;
        rules.schematicsAllowed = false;
        rules.allowEditRules = false;
        rules.allowLogicData = false;
        rules.allowEditWorldProcessors = false;
        rules.ghostBlocks = false;
        rules.fire = false;
        rules.fog = false;
        rules.lighting = false;
        rules.unitCap = 1;
        rules.teams.get(Team.sharded).unitHealthMultiplier = 300f / 220f;
        rules.teams.get(Team.sharded).unitDamageMultiplier = 1.5f;
        rules.modeName = "Isolation / Gamma → Transliminal";
        return rules;
    }

    public static void generate(){
        state.rules = rules();
        state.map = new Map(StringMap.of("name", "病毒隔离空间", "author", "Project Farstar",
                "description", "Gamma to Transliminal combat / 伽马升级凌际弹幕反击地图"));
        state.map.width = width;
        state.map.height = height;
        world.loadGenerator(width, height, tiles -> {
            for(int x = 0; x < width; x++){
                for(int y = 0; y < height; y++){
                    boolean rim = x < 4 || x >= width - 4 || y < 4 || y >= height - 4;
                    tiles.set(x, y, new Tile(x, y, IntrusionContent.gridFloor, Blocks.air,
                            rim ? Blocks.darkMetal : Blocks.air));
                }
            }
            // A tangible facility around the playfield; clear central space for flight/combat.
            for(int x = left; x <= right; x++){
                tiles.getn(x, bottom - 2).setBlock(Blocks.plastaniumWall, Team.sharded);
                tiles.getn(x, top + 2).setBlock(Blocks.plastaniumWall, Team.crux);
            }
            for(int y = bottom - 2; y <= top + 2; y++){
                tiles.getn(left - 2, y).setBlock(Blocks.plastaniumWall, Team.sharded);
                tiles.getn(right + 2, y).setBlock(Blocks.plastaniumWall, Team.crux);
            }
            tiles.getn(56, 14).setBlock(Blocks.coreNucleus, Team.sharded);
            for(int x : new int[]{24, 40, 72, 88}){
                tiles.getn(x, 14).setBlock(Blocks.memoryCell, Team.sharded);
                tiles.getn(x, 136).setBlock(Blocks.memoryCell, Team.crux);
                tiles.getn(x, 9).setBlock(Blocks.logicDisplay, Team.sharded);
            }
            tiles.getn(56, 7).setBlock(CryonJavaMod.javaProcessor, Team.sharded);
            JavaProcessorBuild processor = (JavaProcessorBuild)tiles.getn(56, 7).build;
            processor.setScript("virus-intrusion");
        });
    }

    /** Produces a standard .msav map which can also be opened in the game editor. */
    public static void export(Fi target) throws java.io.IOException {
        target.parent().mkdirs();
        MapIO.writeMap(target, state.map);
    }
}
