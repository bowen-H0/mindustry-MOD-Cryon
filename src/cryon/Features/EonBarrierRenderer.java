package cryon.Features;

import arc.graphics.Blending;
import arc.graphics.g2d.*;
import arc.graphics.gl.Shader;
import arc.math.Angles;
import arc.util.Time;
import mindustry.Vars;
import mindustry.graphics.Layer;

/** One shader sprite per hit ship, with no screen-sized framebuffers or runtime pixel reads. */
public final class EonBarrierRenderer{
    private static Shader shader;

    public static void draw(TextureRegion region, float x, float y, float rotation,
                            float worldScale, float impactAngle, float intensity){
        Draw.draw(Layer.flyingUnit + 1f, () -> {
            if(shader == null) shader = new Shader(Vars.tree.get("shaders/eon-barrier.vert"),
                Vars.tree.get("shaders/eon-barrier.frag"));
            Shader previous = Draw.getShader();
            Blending previousBlend = Draw.getBlend();
            Draw.flush();
            Draw.shader(shader);
            shader.bind();
            shader.setUniformf("u_region", region.u, region.v, region.u2 - region.u, region.v2 - region.v);
            shader.setUniformf("u_size", region.width * worldScale, region.height * worldScale);
            shader.setUniformf("u_distanceScale", EonHullField.distanceRange * worldScale);
            shader.setUniformf("u_impact", Angles.trnsx(impactAngle, 1f), Angles.trnsy(impactAngle, 1f));
            shader.setUniformf("u_intensity", intensity);
            shader.setUniformf("u_time", Time.time);
            try{
                Draw.blend(Blending.additive);
                Draw.color();
                Draw.rect(region, x, y, region.width * worldScale, region.height * worldScale, rotation);
                Draw.flush();
            }finally{
                Draw.shader(previous);
                Draw.blend(previousBlend);
                Draw.reset();
            }
        });
    }
}
