package cryon.Features;

import arc.Core;
import arc.Events;
import arc.graphics.Blending;
import arc.graphics.Gl;
import arc.graphics.Texture;
import arc.graphics.g2d.Draw;
import arc.graphics.gl.GLOnlyTextureData;
import arc.graphics.gl.Shader;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType.DisposeEvent;
import mindustry.graphics.Layer;

/** Batched screen-space folds and destination squares. Native source points use a separate capture pass. */
public class SpaceJumpRiftRenderer{
    private static final int max = 16;
    private static final float layer = Layer.weather + 2f;
    private static final float[] positions = new float[max * 4], states = new float[max * 4];
    private static Texture scene;
    private static RiftShader shader;
    private static int count;
    private static boolean scheduled, failed;

    public static void install(){
        Events.on(DisposeEvent.class, event -> {
            if(scene != null) scene.dispose();
            if(shader != null) shader.dispose();
            scene = null; shader = null; count = 0;
            scheduled = false;
        });
    }

    public static boolean submit(float x, float y, float size, float rotation, float aperture,
                                 float energy, int tier, float phase){
        if(Vars.headless || failed || Core.camera == null) return false;
        float reach = size * 3.6f;
        if(Math.abs(x - Core.camera.position.x) > Core.camera.width / 2f + reach
            || Math.abs(y - Core.camera.position.y) > Core.camera.height / 2f + reach) return false;
        if(count >= (Vars.mobile ? 4 : max)) return false;
        if(!scheduled){ scheduled = true; Draw.draw(layer, SpaceJumpRiftRenderer::render); }
        int k = count * 4;
        positions[k] = x; positions[k + 1] = y; positions[k + 2] = size;
        positions[k + 3] = rotation * arc.math.Mathf.degRad;
        states[k] = aperture; states[k + 1] = energy; states[k + 2] = tier; states[k + 3] = phase;
        count++;
        return true;
    }

    private static Texture resized(Texture texture){
        int width = Core.graphics.getWidth(), height = Core.graphics.getHeight();
        if(texture == null || texture.width != width || texture.height != height){
            if(texture != null) texture.dispose();
            texture = new Texture(new GLOnlyTextureData(width, height, 0, Gl.rgb, Gl.rgb, Gl.unsignedByte));
            texture.setFilter(Texture.TextureFilter.linear);
            texture.setWrap(Texture.TextureWrap.clampToEdge);
        }
        return texture;
    }

    private static void copyScreen(Texture texture){
        texture.bind(0);
        Gl.copyTexSubImage2D(Gl.texture2d, 0, 0, 0, 0, 0, texture.width, texture.height);
    }

    private static void render(){
        Blending previous = Draw.getBlend();
        try{
            if(count == 0) return;
            if(shader == null) shader = new RiftShader();
            Draw.flush(); scene = resized(scene);
            Blending.normal.apply();
            copyScreen(scene);
            Draw.blit(scene, shader);
        }catch(Throwable error){
            failed = true;
            Log.err("[SpaceJump] fold shader unavailable; using geometry effects", error);
        }finally{
            previous.apply();
            count = 0; scheduled = false;
        }
    }

    private static class RiftShader extends Shader{
        RiftShader(){ super(Vars.tree.get("shaders/screenspace.vert"), Vars.tree.get("shaders/space-fold.frag")); }
        @Override
        public void apply(){
            setUniformi("u_scene", 0);
            setUniformf("u_offset", Core.camera.position.x - Core.camera.width / 2f,
                Core.camera.position.y - Core.camera.height / 2f);
            setUniformf("u_texsize", Core.camera.width, Core.camera.height);
            setUniformf("u_time", Time.time / 60f); setUniformi("u_count", count);
            for(int i = 0; i < max; i++){
                int k = i * 4;
                setUniformf("u_fold[" + i + "]", positions[k], positions[k + 1], positions[k + 2], positions[k + 3]);
                setUniformf("u_state[" + i + "]", states[k], states[k + 1], states[k + 2], states[k + 3]);
            }
        }
    }
}
