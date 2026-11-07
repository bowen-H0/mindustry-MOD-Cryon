package cryon.Features;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.g2d.Draw;
import arc.graphics.gl.Shader;
import arc.util.Time;
import mindustry.Vars;
import mindustry.graphics.Layer;

/** World-fixed projection with sparse hollow squares that expand and fade. */
public final class RealityAnchorRenderer{
    private static Shader shader;
    public static void draw(float x, float y, float extent, float strength){
        Draw.draw(Layer.light + 0.025f, () -> {
            if(shader == null) shader = new Shader(Vars.tree.get("shaders/reality-anchor.vert"), Vars.tree.get("shaders/reality-anchor.frag"));
            Shader old = Draw.getShader(); Blending blend = Draw.getBlend(); Draw.flush();
            try{
                Draw.shader(shader); Draw.blend(Blending.normal); shader.bind();
                shader.setUniformf("u_center", x, y); shader.setUniformf("u_extent", extent);
                shader.setUniformf("u_time", Time.time); shader.setUniformf("u_strength", strength);
                Draw.color(); Draw.rect(Core.atlas.white(), x, y, extent * 2f, extent * 2f); Draw.flush();
            }finally{ Draw.shader(old); Draw.blend(blend); Draw.reset(); }
        });
    }
}
