package cryon.Features;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.Blending;
import arc.graphics.Gl;
import arc.graphics.Texture;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.gl.GLOnlyTextureData;
import arc.graphics.gl.Shader;
import arc.graphics.g2d.Draw;
import arc.scene.ui.layout.Scl;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import cryon.Type.ReflectShieldAbility;

public class ReflectShieldRenderer {

    private static final int MAX = 8;

    private static FrameBuffer lensBuffer;
    private static Texture sceneTex;
    private static LensShader lensShader;
    private static boolean lensFailed = false;

    private static final float[] data = new float[MAX * 4];
    private static int count = 0;

    public static void submit(float x, float y, float radius, float progress){
        if(lensFailed) return;
        if(count == 0) drawReflectShields();
        if(count >= MAX) return;
        int i = count * 4;
        data[i] = x;
        data[i + 1] = y;
        data[i + 2] = radius;
        data[i + 3] = progress;
        count++;
    }

    public static class LensShader extends Shader {
        public LensShader() {
            super(
                    Vars.tree.get("shaders/screenspace.vert"),
                    Vars.tree.get("shaders/gravitational-lensing.frag")
            );
        }

        @Override
        public void apply() {
            setUniformf("u_time", Time.time / Scl.scl(1f));
            setUniformf("u_offset",
                    Core.camera.position.x - Core.camera.width / 2f,
                    Core.camera.position.y - Core.camera.height / 2f);
            setUniformf("u_texsize", Core.camera.width, Core.camera.height);
            setUniformi("u_count", count);
            for(int i = 0; i < MAX; i++){
                int k = i * 4;
                setUniformf("u_bh[" + i + "]", data[k], data[k + 1], data[k + 2], data[k + 3]);
            }
            setUniformi("u_scene", 1);
            sceneTex.bind(1);
            lensBuffer.getTexture().bind(0);
        }
    }

    private static void ensureResources(){
        int w = Core.graphics.getWidth(), h = Core.graphics.getHeight();

        if(lensBuffer == null) lensBuffer = new FrameBuffer();
        if(lensBuffer.getWidth() != w || lensBuffer.getHeight() != h) lensBuffer.resize(w, h);

        if(sceneTex == null || sceneTex.width != w || sceneTex.height != h){
            if(sceneTex != null) sceneTex.dispose();
            // GPU storage only: no full-screen CPU Pixmap or duplicate upload.
            sceneTex = new Texture(new GLOnlyTextureData(w, h, 0, Gl.rgb, Gl.rgb, Gl.unsignedByte));
            sceneTex.setFilter(Texture.TextureFilter.linear);
            sceneTex.setWrap(Texture.TextureWrap.clampToEdge);
        }
    }

    public static void drawReflectShields() {

        Draw.drawRange(ReflectShieldAbility.layerReflectShield, 1f,
                () -> {
                    if(count == 0) return;
                    if(lensShader == null){
                        try{
                            lensShader = new LensShader();
                        }catch(Throwable t){
                            lensFailed = true;
                            count = 0;
                            Log.err("[ReflectShield] shader FAILED to compile", t);
                            return;
                        }
                    }
                    ensureResources();
                    Draw.flush();
                    sceneTex.bind();
                    Gl.copyTexSubImage2D(Gl.texture2d, 0, 0, 0, 0, 0, sceneTex.width, sceneTex.height);
                    lensBuffer.begin(Color.clear);
                },
                () -> {
                    if(count == 0) return;
                    Draw.flush();
                    lensBuffer.end();
                    try{
                        composite(() -> lensBuffer.blit(lensShader));
                    }finally{
                        count = 0;
                    }
                }
        );
    }

    private static void composite(Runnable blit){
        // FrameBuffer.blit bypasses SpriteBatch. Its logical blend may be normal while
        // the last flushed weather mesh left the GPU in additive mode.
        Blending previous = Draw.getBlend();
        Gl.enable(Gl.blend);
        Blending.normal.apply();
        try{
            blit.run();
        }finally{
            previous.apply();
        }
    }
}
