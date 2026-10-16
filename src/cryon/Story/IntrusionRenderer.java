package cryon.Story;

import arc.Core;
import arc.func.Cons;
import arc.math.Angles;
import cryon.Script.VirusIntrusionScript;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.Texture.TextureFilter;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.gl.Shader;
import arc.util.Log;
import mindustry.game.EventType.*;
import mindustry.graphics.Layer;

import static mindustry.Vars.*;

/** World-only point-structure postprocess; scene UI is drawn afterwards and stays crisp. */
public final class IntrusionRenderer {
    private static FrameBuffer buffer;
    private static Shader shader;
    private static boolean installed, failed;
    // Keep bullet edges readable while reducing framebuffer pixels/memory to about 56%.
    private static final float renderScale = 0.75f;
    private static final Color gammaColor = Color.valueOf("ffd37f");
    private static final Color enemyColor = Color.valueOf("ce8053");
    private static final Color friendlyColor = Color.valueOf("8c9d91");
    private static final Color bossColor = Color.valueOf("ad625c");
    private static float markerPixel;
    private static final Cons<Bullet> bulletMarker = bullet -> {
        if(bullet.team == state.rules.waveTeam){
            point(bullet.x, bullet.y, Math.max(1.6f, markerPixel * 1.8f), enemyColor);
        }else if(bullet.team == state.rules.defaultTeam){
            point(bullet.x, bullet.y, Math.max(0.85f, markerPixel * 1.15f), friendlyColor);
        }
    };

    private IntrusionRenderer() {}

    public static void install(){
        if(installed || headless) return;
        installed = true;
        Events.run(Trigger.draw, IntrusionRenderer::draw);
        Events.on(ResetEvent.class, event -> dispose());
        Events.on(WorldLoadEvent.class, event -> dispose());
        Core.app.addListener(new arc.ApplicationListener(){
            @Override public void dispose(){ IntrusionRenderer.dispose(); }
        });
    }

    private static boolean active(){
        return !headless && IntrusionMap.active() && state.isGame() && !state.isEditor()
                && world.width() > 0 && world.height() > 0;
    }

    private static void draw(){
        if(!active()){
            if(buffer != null || shader != null) dispose();
            return;
        }
        if(!failed){
            try{
                ensureResources();
            }catch(Exception error){
                dispose();
                failed = true;
                Log.err("[CryonIntrusion] Point shader unavailable; keeping map boundary mask.", error);
            }
        }
        if(!failed){
            Draw.draw(Layer.min - 1f, () -> buffer.begin(Color.black));
            Draw.draw(Layer.max + 1f, () -> {
                buffer.end();
                buffer.blit(shader);
            });
        }
        // Full-resolution recognition marks bypass the scene color compression.
        Draw.draw(Layer.max + 1.5f, IntrusionRenderer::drawRecognitionMarks);
        // Independent of the shader: the plastanium walls and everything outside the combat rectangle are blacked out.
        Draw.draw(Layer.max + 2f, IntrusionRenderer::maskOutsideMap);
    }

    private static void ensureResources(){
        if(shader == null){
            shader = new Shader(tree.get("shaders/screenspace.vert"), tree.get("shaders/intrusion-wireframe.frag")){
                @Override public void apply(){
                    setUniformi("u_texture", 0);
                    setUniformf("u_pixel", 1f / buffer.getWidth(), 1f / buffer.getHeight());
                    setUniformf("u_origin", Core.camera.position.x - Core.camera.width / 2f,
                            Core.camera.position.y - Core.camera.height / 2f);
                    setUniformf("u_worldsize", Core.camera.width, Core.camera.height);
                    setUniformf("u_bounds", IntrusionMap.visibleMinX, IntrusionMap.visibleMinY,
                            IntrusionMap.visibleMaxX, IntrusionMap.visibleMaxY);
                }
            };
        }
        int width = Math.max(2, Math.round(Core.graphics.getWidth() * renderScale));
        int height = Math.max(2, Math.round(Core.graphics.getHeight() * renderScale));
        if(buffer == null){
            buffer = new FrameBuffer(width, height);
            buffer.getTexture().setFilter(TextureFilter.linear);
        }else if(buffer.getWidth() != width || buffer.getHeight() != height){
            buffer.resize(width, height);
            buffer.getTexture().setFilter(TextureFilter.linear);
        }
    }

    private static void drawRecognitionMarks(){
        VirusIntrusionScript script = VirusIntrusionScript.current();
        if(script == null || script.disarmed() || script.finished()) return;
        markerPixel = Core.camera.width / Math.max(1, Core.graphics.getWidth());
        float x0 = Core.camera.position.x - Core.camera.width / 2f;
        float y0 = Core.camera.position.y - Core.camera.height / 2f;
        Groups.bullet.intersect(x0 - 8f, y0 - 8f, Core.camera.width + 16f, Core.camera.height + 16f, bulletMarker);
        Unit boss = script.virusNode();
        if(boss != null && !boss.dead){
            float radius = Math.max(20f, boss.hitSize * 0.65f);
            for(int i = 0; i < 12; i++){
                float angle = i * 30f;
                point(boss.x + Angles.trnsx(angle, radius), boss.y + Angles.trnsy(angle, radius),
                        Math.max(1.1f, markerPixel * 1.6f), bossColor);
            }
        }
        Unit gamma = script.controlledGamma();
        if(gamma != null && !gamma.dead){
            // Five stable flight-shape vertices plus the center identify the player and heading.
            float r = Math.max(6f, gamma.hitSize * 0.65f);
            float dot = Math.max(0.8f, markerPixel * 1.4f);
            for(int i = 0; i < 5; i++){
                float angle = gamma.rotation + (i == 0 ? 0f : i == 1 ? 120f : i == 2 ? -120f : i == 3 ? 160f : -160f);
                float distance = i < 3 ? r : r * 0.65f;
                point(gamma.x + Angles.trnsx(angle, distance), gamma.y + Angles.trnsy(angle, distance), dot, gammaColor);
            }
            point(gamma.x, gamma.y, Math.max(1.05f, markerPixel * 1.7f), gammaColor);
        }
        Draw.reset();
    }

    private static void point(float x, float y, float radius, Color color){
        // A small matrix of separated square dots, rather than a filled disk.
        // Limit each marker's grid density even at close zoom on low-end devices.
        float spacing = Math.max(markerPixel * 3f / renderScale, radius * 0.75f);
        float extent = Math.max(radius, spacing * 0.6f);
        float dotSize = spacing * 0.38f;
        int cells = (int)(extent / spacing);
        for(int iy = -cells; iy <= cells; iy++){
            for(int ix = -cells; ix <= cells; ix++){
                float dx = ix * spacing, dy = iy * spacing;
                if(dx * dx + dy * dy > extent * extent) continue;
                Draw.color(Color.black);
                Fill.square(x + dx, y + dy, dotSize * 0.5f + markerPixel * 0.4f);
                Draw.color(color);
                Fill.square(x + dx, y + dy, dotSize * 0.5f);
            }
        }
    }

    private static void maskOutsideMap(){
        float x0 = Core.camera.position.x - Core.camera.width / 2f;
        float y0 = Core.camera.position.y - Core.camera.height / 2f;
        float x1 = x0 + Core.camera.width, y1 = y0 + Core.camera.height;
        float minX = IntrusionMap.visibleMinX, minY = IntrusionMap.visibleMinY;
        float maxX = IntrusionMap.visibleMaxX, maxY = IntrusionMap.visibleMaxY;
        Draw.color(Color.black);
        rectangle(x0, y0, Math.min(x1, minX), y1);
        rectangle(Math.max(x0, maxX), y0, x1, y1);
        float insideLeft = Math.max(x0, minX), insideRight = Math.min(x1, maxX);
        rectangle(insideLeft, y0, insideRight, Math.min(y1, minY));
        rectangle(insideLeft, Math.max(y0, maxY), insideRight, y1);
        Draw.reset();
    }

    private static void rectangle(float x0, float y0, float x1, float y1){
        if(x1 > x0 && y1 > y0) Fill.crect(x0, y0, x1 - x0, y1 - y0);
    }

    private static void dispose(){
        if(buffer != null){ buffer.dispose(); buffer = null; }
        if(shader != null){ shader.dispose(); shader = null; }
        failed = false;
    }
}
