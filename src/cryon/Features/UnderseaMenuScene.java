package cryon.Features;

import arc.Core;
import arc.graphics.Camera;
import arc.graphics.Color;
import arc.graphics.Texture;
import arc.graphics.g2d.Batch;
import arc.graphics.g2d.CacheBatch;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.SpriteCache;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.gl.Shader;
import arc.math.Mat;
import arc.math.Mathf;
import arc.struct.ObjectSet;
import arc.util.noise.Simplex;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.core.World;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.Tiles;

/** An isolated marine map, cached with the same native tile draws as the default menu. */
final class UnderseaMenuScene{
    private static final int size = 96;
    private final Tiles map = new Tiles(size, size);
    private final Camera camera = new Camera();
    private final Mat projection = new Mat();
    private final ObjectSet<Block> floraTypes = new ObjectSet<>();
    private CacheBatch batch;
    private FrameBuffer environmentBuffer;
    private Shader environmentShader;
    private int terrainCache, floraCount;

    UnderseaMenuScene(){
        World previous = Vars.world;
        Tiles previousTiles = previous.tiles;
        boolean previousGenerating = previous.isGenerating();
        int previousChanges = previous.tileChanges, previousFloorChanges = previous.floorChanges;
        Batch previousBatch = Core.batch;
        Mat previousProjection = new Mat(Draw.proj());
        Draw.flush();
        try{
            // Floor blending needs a world context. Restore it after building the cache;
            // no map-load events, player tiles or game simulation are involved.
            previous.setGenerating(true);
            previous.tiles = map;
            generate();
            batch = new CacheBatch(new SpriteCache(size * size * 16, false));
            Core.batch = batch;
            batch.beginCache();
            for(Tile tile : map) tile.floor().drawBase(tile);
            terrainCache = batch.endCache();
        }catch(RuntimeException error){
            if(batch != null) batch.dispose();
            throw error;
        }finally{
            Core.batch = previousBatch;
            previous.tiles = previousTiles;
            previous.tileChanges = previousChanges;
            previous.floorChanges = previousFloorChanges;
            previous.setGenerating(previousGenerating);
            Draw.proj(previousProjection);
            Draw.reset();
        }
    }

    private static Block block(String name){
        Block result = Vars.content.block("cryon-abyss-" + name);
        if(result == null) throw new IllegalStateException("Missing marine map content: " + name);
        return result;
    }

    private void generate(){
        Block blue = block("blue-bed"), shallow = block("shallows"), silt = block("silt-bed"), basalt = block("pillow-basalt");
        Block[] life = {block("glass-kelp"), block("ribbon-kelp"), block("lantern-weed"), block("tube-sponge"),
                block("frost-fan"), block("glow-anemone"), block("lantern-coral"), Blocks.purbush, Blocks.yellowCoral, Blocks.redweed};
        Block rocks = block("basalt-boulder");
        for(int x = 0; x < size; x++) for(int y = 0; y < size; y++){
            long seed = x * 73856093L ^ y * 19349663L;
            float reef = Simplex.noise2d(813, 3, 0.55, 0.035, x, y);
            float habitat = Simplex.noise2d(57, 2, 0.5, 0.105, x, y);
            float channel = Math.abs(y - (47f + 11f * Mathf.sin(x * 0.09f) + 5f * Mathf.sin(x * 0.17f)));
            boolean openWater = channel < 3.8f;
            Block floor = openWater ? blue : channel < 6.7f ? shallow : reef < 0.29f ? silt
                    : reef > 0.55f ? Blocks.redmat : Blocks.bluemat;
            boolean stone = !openWater && Simplex.noise2d(341, 2, 0.55, 0.12, x, y) > 0.79f;
            if(stone) floor = basalt;
            Block prop = Blocks.air;
            if(stone){
                if(Mathf.randomSeed(seed + 51) < 0.16f) prop = rocks;
            }else if(!openWater && Mathf.randomSeed(seed + 17) < 0.22f + habitat * 0.48f){
                int kind = Mathf.randomSeed(seed + 23, 0, life.length - 1);
                if(floor == Blocks.redmat && kind < 4) kind = Mathf.randomSeed(seed + 29, 5, 9);
                prop = life[kind];floraCount++;floraTypes.add(prop);
            }else if(openWater && Mathf.randomSeed(seed + 31) < 0.025f){
                prop = life[3];floraCount++;floraTypes.add(prop);
            }
            map.set(x, y, new Tile(x, y, floor, Blocks.air, prop));
        }
    }

    void draw(float x, float y, float width, float height, float time){
        Draw.flush();projection.set(Draw.proj());
        float scale = Math.max(width / 440f, height / 330f) * (1.02f + 0.025f * Mathf.sin(time * 0.025f));
        camera.resize(width / scale, height / scale);
        camera.position.set(size * 4f + 65f * Mathf.sin(time * 0.012f),
                size * 4f + 42f * Mathf.sin(time * 0.017f + 0.8f));
        camera.update();
        int screenW = Core.graphics.getWidth(), screenH = Core.graphics.getHeight();
        float quality = Math.min(1f, (Vars.mobile ? 1080f : 1600f) / Math.max(screenW, screenH));
        int renderW = Math.max(2, (int)(screenW * quality)), renderH = Math.max(2, (int)(screenH * quality));
        if(environmentShader == null) environmentShader = new Shader(
                Vars.tree.get("shaders/screenspace.vert"), Vars.tree.get("shaders/menu-ocean.frag"));
        if(environmentBuffer == null){
            environmentBuffer = new FrameBuffer(renderW, renderH);
            environmentBuffer.getTexture().setFilter(Texture.TextureFilter.linear);
        }else environmentBuffer.resize(renderW, renderH);
        environmentBuffer.begin(Color.black);
        try{
            Draw.proj(camera);batch.setProjection(camera.mat);
            batch.beginDraw();batch.drawCache(terrainCache);batch.endDraw();
            // Waving seaweed uses native deformable quads, which must use SpriteBatch,
            // rather than the static menu cache. Draw only plants inside the camera.
            Tiles previousTiles = Vars.world.tiles;
            try{
                Vars.world.tiles = map;
                int minX = Math.max(0, (int)((camera.position.x - camera.width / 2f) / 8f) - 3);
                int maxX = Math.min(size - 1, (int)((camera.position.x + camera.width / 2f) / 8f) + 3);
                int minY = Math.max(0, (int)((camera.position.y - camera.height / 2f) / 8f) - 3);
                int maxY = Math.min(size - 1, (int)((camera.position.y + camera.height / 2f) / 8f) + 3);
                for(int ty = maxY; ty >= minY; ty--) for(int tx = minX; tx <= maxX; tx++){
                    Tile tile = map.getn(tx, ty);
                    if(tile.block() != Blocks.air) tile.block().drawBase(tile);
                }
                Draw.flush();
            }finally{Vars.world.tiles = previousTiles;}
            Draw.color(0.70f, 0.91f, 0.91f, 0.16f);
            for(int i = 0; i < 150; i++){
                float px = Mathf.mod(Mathf.randomSeed(i * 47L, size * 8f) + time * 0.22f, size * 8f);
                float py = Mathf.mod(Mathf.randomSeed(i * 71L + 5, size * 8f) + time * 0.35f, size * 8f);
                Fill.circle(px, py, Mathf.randomSeed(i * 13L + 8, 0.15f, 0.42f));
            }
        }finally{Draw.flush();environmentBuffer.end();Draw.proj(projection);Draw.reset();}
        environmentShader.bind();
        environmentShader.setUniformf("u_resolution", (float)renderW, (float)renderH);
        environmentShader.setUniformf("u_time", time);
        environmentBuffer.blit(environmentShader);
        // Bubbles and drifting plankton belong to the background, never the menu UI.
        Draw.proj(camera);
        for(int i = 0; i < 34; i++){
            float px = Mathf.randomSeed(i * 173L + 19, size * 8f) + Mathf.sin(time * 0.16f + i) * 1.7f;
            float py = Mathf.mod(Mathf.randomSeed(i * 97L + 29, size * 8f) + time * (0.6f + i % 3 * 0.18f), size * 8f);
            Draw.color(0.68f, 0.91f, 0.94f, 0.24f);Lines.stroke(0.16f);
            Lines.circle(px, py, Mathf.randomSeed(i * 41L, 0.5f, 1.2f));
        }
        Draw.flush();Draw.proj(projection);Draw.reset();
        float shade = Color.toFloatBits(0f, 0.025f, 0.045f, 0.50f), clear = Color.toFloatBits(0f, 0f, 0f, 0f);
        Fill.quad(x, y, shade, x + width * 0.65f, y, clear,
                x + width * 0.65f, y + height, clear, x, y + height, shade);
        Draw.reset();
    }

    void dispose(){
        if(batch != null){ batch.dispose(); batch = null; }
        if(environmentBuffer != null){ environmentBuffer.dispose(); environmentBuffer = null; }
        if(environmentShader != null){ environmentShader.dispose(); environmentShader = null; }
    }
}
