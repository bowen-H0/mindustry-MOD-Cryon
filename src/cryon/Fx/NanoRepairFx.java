package cryon.Fx;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.Angles;
import arc.math.Mathf;
import mindustry.entities.Effect;
import mindustry.graphics.Layer;

/** Small green repair motes rise around the actual healed target, including moving units. */
public final class NanoRepairFx{
    public static final Effect particles = new Effect(38f, 400f, e -> {
        float radius = Mathf.clamp(e.rotation * 0.65f, 10f, 90f);
        Draw.color(e.color, Color.white, 0.35f);
        Draw.alpha(e.fslope() * 0.85f);
        for(int i = 0; i < 7; i++){
            float angle = Mathf.randomSeed(e.id * 17L + i, 360f) + e.fin() * 32f;
            float spread = radius * Mathf.randomSeed(e.id * 31L + i, 0.75f, 1.1f);
            float px = e.x + Angles.trnsx(angle, spread * (1f - e.fin() * 0.3f));
            float py = e.y + Angles.trnsy(angle, spread * 0.75f) + e.fin() * (18f + radius * 0.5f);
            float size = 1.3f + Mathf.randomSeed(e.id * 13L + i, 1.2f);
            Fill.square(px, py, size * (0.5f + e.fout() * 0.5f), 45f);
            Lines.stroke(0.65f * e.fout());
            Lines.line(px, py - 3f * e.fout(), px, py - 1f);
        }
        Draw.reset();
    }).followParent(true).layer(Layer.light + 0.05f);
}
