package cryon.Features;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.g2d.Draw;
import arc.graphics.gl.Shader;
import arc.util.Time;
import mindustry.Vars;
import mindustry.graphics.Layer;

/** A single procedural mist sheet per matrix; no particle flood or framebuffer allocations. */
public final class NanoMistRenderer{
    private static Shader shader;
    public static void draw(float x, float y, float radius, float intensity, int seed){
        Draw.draw(Layer.light + 0.04f, () -> {
            if(shader == null) shader = new Shader(Vars.tree.get("shaders/nano-mist.vert"), Vars.tree.get("shaders/nano-mist.frag"));
            Shader previous = Draw.getShader(); Blending blend = Draw.getBlend();
            Draw.flush();
            try{
                Draw.shader(shader); Draw.blend(Blending.normal); shader.bind();
                shader.setUniformf("u_center", x, y); shader.setUniformf("u_radius", radius);
                shader.setUniformf("u_time", Time.time); shader.setUniformf("u_intensity", intensity);
                shader.setUniformf("u_seed", seed % 997);
                Draw.color(); Draw.rect(Core.atlas.white(), x, y, radius * 2f, radius * 2f); Draw.flush();
            }finally{ Draw.shader(previous); Draw.blend(blend); Draw.reset(); }
        });
    }
}
