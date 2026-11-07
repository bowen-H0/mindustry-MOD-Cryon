package cryon.Features;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.g2d.Draw;
import arc.graphics.gl.Shader;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.Vars;
import mindustry.graphics.Layer;

/** Flat world-space field, rendered with its own interference shader, without a lifted quad. */
public final class OrbitalBarrierRenderer{
    private static Shader shader;

    public static void draw(float ax, float ay, float bx, float by, float width,
                            float impact, float strength, boolean phased){
        // The membrane emits light itself; the night mask should only darken terrain.
        Draw.draw(Layer.light + 0.03f, () -> {
            if(shader == null) shader = new Shader(Vars.tree.get("shaders/orbital-barrier.vert"),
                Vars.tree.get("shaders/orbital-barrier.frag"));
            Shader previous = Draw.getShader();
            Blending blend = Draw.getBlend();
            Draw.flush();
            try{
                Draw.shader(shader); Draw.blend(Blending.normal);
                shader.bind();
                shader.setUniformf("u_start", ax, ay);
                shader.setUniformf("u_end", bx, by);
                shader.setUniformf("u_width", width);
                shader.setUniformf("u_time", Time.time);
                shader.setUniformf("u_impact", impact);
                shader.setUniformf("u_strength", strength);
                shader.setUniformf("u_phase", phased ? 1f : 0f);
                Draw.color();
                Draw.rect(Core.atlas.white(), (ax + bx) / 2f, (ay + by) / 2f,
                    Mathf.dst(ax, ay, bx, by) + width, width, Mathf.angle(bx - ax, by - ay));
                Draw.flush();
            }finally{
                Draw.shader(previous); Draw.blend(blend); Draw.reset();
            }
        });
    }
}
