package cryon.Story;

import arc.Core;
import mindustry.gen.Groups;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.Blending;
import arc.graphics.Gl;
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
    private static final FrameBuffer[] combatBuffers = new FrameBuffer[4];
    private static Shader shader, combatShader;
    private static boolean installed, failed;
    private static final float renderScale = 0.75f;
    // Player, enemy, enemy bullets, player bullets.
    private static final Color[] combatColors = {
        Color.valueOf("ffd454"), Color.valueOf("e95750"),
        Color.valueOf("be78ed"), Color.valueOf("579eff")
    };
    private static final Blending combatBlending = new Blending(Gl.one, Gl.oneMinusSrcAlpha);
    private static int combatIndex;

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
            if(buffer != null || combatBuffers[0] != null || shader != null) dispose();
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
            // Divert native combat draws out of the background; redraw by team below.
            captureCombat(Layer.flyingUnitLow, 0.5f);
            captureCombat(Layer.flyingUnit, 0.5f);
            // Bloom begins at bullet - 0.02; stay strictly inside its buffer lifetime.
            captureCombat(Layer.bullet, 0.01f);
            // Keep native lightning/charge effects in the combat color layers too.
            captureCombat(Layer.effect, 0.01f);
            Draw.draw(Layer.max + 1f, () -> {
                eachEffect(effect -> {
                    if(defaultEffectLayer(effect) && !combatEffect(effect)) effect.draw();
                });
                buffer.end();
                buffer.blit(shader);
                drawCombatLayers();
            });
        }
        // Independent of the shader: the plastanium walls and everything outside the combat rectangle are blacked out.
        Draw.draw(Layer.max + 2f, IntrusionRenderer::maskOutsideMap);
    }

    private static void captureCombat(float layer, float radius){
        Draw.drawRange(layer, radius,
                () -> combatBuffers[0].begin(Color.clear),
                () -> combatBuffers[0].end());
    }

    private static void drawCombatLayers(){
        for(int i = 0; i < combatBuffers.length; i++){
            final int index = i;
            FrameBuffer target = combatBuffers[i];
            target.begin(Color.clear);
            if(i < 2){
                Groups.unit.each(unit -> {
                    if(unit.team == (index == 0 ? state.rules.defaultTeam : state.rules.waveTeam)) unit.draw();
                });
            }else{
                Groups.bullet.each(bullet -> {
                    if(bullet.team == (index == 2 ? state.rules.waveTeam : state.rules.defaultTeam)) bullet.draw();
                });
            }
            if(i >= 2){
                eachEffect(effect -> {
                    if(defaultEffectLayer(effect) && combatEffect(effect)
                            && (effect.color.equals(combatColors[2]) ? index == 2 : index == 3)) effect.draw();
                });
            }
            Draw.reset();
            target.end();
            combatIndex = i;
            combatBlending.apply();
            target.blit(combatShader);
            Blending.normal.apply();
        }
    }

    private static void eachEffect(arc.func.Cons<mindustry.gen.EffectState> consumer){
        Groups.draw.each(drawable -> {
            if(drawable instanceof mindustry.gen.EffectState) consumer.get((mindustry.gen.EffectState)drawable);
        });
    }
    private static boolean defaultEffectLayer(mindustry.gen.EffectState effect){
        return Math.abs(effect.effect.layer - Layer.effect) < 0.01f;
    }
    private static boolean combatEffect(mindustry.gen.EffectState effect){
        return effect.effect == mindustry.content.Fx.lightning || effect.effect == mindustry.content.Fx.lightningCharge
                || effect.effect == mindustry.content.Fx.lancerLaserCharge;
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
        if(combatShader == null){
            combatShader = new Shader(tree.get("shaders/screenspace.vert"), tree.get("shaders/intrusion-combat.frag")){
                @Override public void apply(){
                    FrameBuffer target = combatBuffers[combatIndex];
                    setUniformi("u_texture", 0);
                    setUniformf("u_pixel", 1f / target.getWidth(), 1f / target.getHeight());
                    setUniformf("u_origin", Core.camera.position.x - Core.camera.width / 2f,
                            Core.camera.position.y - Core.camera.height / 2f);
                    setUniformf("u_worldsize", Core.camera.width, Core.camera.height);
                    setUniformf("u_tint", combatColors[combatIndex].r, combatColors[combatIndex].g, combatColors[combatIndex].b);
                }
            };
        }
        int unitWidth = Math.max(2, Core.graphics.getWidth());
        int unitHeight = Math.max(2, Core.graphics.getHeight());
        for(int i = 0; i < combatBuffers.length; i++){
            if(combatBuffers[i] == null){
                combatBuffers[i] = new FrameBuffer(unitWidth, unitHeight);
                combatBuffers[i].getTexture().setFilter(TextureFilter.linear);
            }else if(combatBuffers[i].getWidth() != unitWidth || combatBuffers[i].getHeight() != unitHeight){
                combatBuffers[i].resize(unitWidth, unitHeight);
                combatBuffers[i].getTexture().setFilter(TextureFilter.linear);
            }
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
        for(int i = 0; i < combatBuffers.length; i++){
            if(combatBuffers[i] != null){ combatBuffers[i].dispose(); combatBuffers[i] = null; }
        }
        if(combatShader != null){ combatShader.dispose(); combatShader = null; }
        if(shader != null){ shader.dispose(); shader = null; }
        failed = false;
    }
}
