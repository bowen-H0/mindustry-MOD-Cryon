package cryon.Type;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.Angles;
import mindustry.entities.Effect;
import mindustry.graphics.Drawf;

/** Monochrome rings and rotating light blades, following Abyss's halo structure. */
public final class ThemisLawFx{
    public static final Color gold = Color.valueOf("d9c99a");

    private ThemisLawFx(){}

    public static void halo(float x, float y, float radius, float alpha){
        if(radius <= 0f) return;
        Draw.color(gold, alpha);
        Lines.stroke(Math.max(1.5f, radius * 0.035f));
        Lines.circle(x, y, radius);
        Draw.reset();
    }

    public static void corona(float x, float y, float radius, float rotation, float alpha, float warmup){
        if(radius <= 0f) return;
        Draw.color(gold, alpha);
        Lines.stroke(2.8f);
        Lines.circle(x, y, radius);
        Lines.stroke(2f);
        Lines.poly(x, y, 8, radius * 0.91f, rotation);
        Lines.stroke(1.6f);
        Lines.poly(x, y, 8, radius * 0.79f, -rotation * 1.5f);
        Lines.stroke(2.2f);
        Lines.poly(x, y, 6, radius * 0.58f, -rotation * 1.2f);
        Lines.stroke(2.4f);
        Lines.circle(x, y, radius * 0.36f);
        Lines.stroke(1.3f);
        Lines.circle(x, y, radius * 0.29f);

        // The same two opposed blade orbits as Abyss, enlarged for the elder.
        for(int i = 0; i < 8; i++){
            float angle = i * 45f + rotation * 0.8f;
            float px = x + Angles.trnsx(angle, radius);
            float py = y + Angles.trnsy(angle, radius);
            Drawf.tri(px, py, 7f + warmup * 3f, 16f + warmup * 20f, angle);
        }
        for(int i = 0; i < 6; i++){
            float angle = i * 60f - rotation * 1.2f;
            float px = x + Angles.trnsx(angle, radius * 0.58f);
            float py = y + Angles.trnsy(angle, radius * 0.58f);
            Drawf.tri(px, py, 5f + warmup * 2f, 12f + warmup * 12f, angle + 180f);
        }
        Lines.stroke(1.5f);
        for(int i = 0; i < 6; i++){
            float angle = i * 60f + rotation * 2f;
            Lines.square(x + Angles.trnsx(angle, radius * 0.22f),
                y + Angles.trnsy(angle, radius * 0.22f), 3f + warmup * 2f, angle + 45f);
        }
        Draw.reset();
    }

    public static final Effect verdict = new Effect(65f, 1100f, e -> {
        halo(e.x, e.y, e.rotation * (0.85f + e.fin() * 0.2f), e.fout());
    });
}
