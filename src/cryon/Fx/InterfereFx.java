package cryon.Fx;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import mindustry.entities.Effect;
import mindustry.graphics.*;

/** Procedural EMP visuals shared by the projectile, detonation and status. */
public final class InterfereFx{
    public static final Color blue = Color.valueOf("80cfff");
    public static final float radius = 40f;
    private InterfereFx(){}

    public static final Effect pulse = new Effect(42f, 130f, e -> {
        float progress = Interp.pow3Out.apply(e.fin());
        Draw.color(blue);
        Draw.alpha(0.2f * e.fout());
        Fill.circle(e.x, e.y, radius * progress);
        Draw.alpha(1f);
        Lines.stroke(3.2f * e.fout());
        Lines.circle(e.x, e.y, radius * progress);
        Draw.color(Color.white);
        Lines.stroke(1.5f * e.fout());
        Lines.circle(e.x, e.y, radius * progress * 0.86f);
        e.scaled(12f, flash -> {
            Draw.color(Color.white);
            Fill.circle(e.x, e.y, 8f * flash.fout());
            Lines.stroke(2f * flash.fout());
            Lines.circle(e.x, e.y, 4f + 19f * flash.fin());
        });
        Draw.color(blue, Color.white, e.fout());
        Lines.stroke(1.6f * e.fout());
        for(int i = 0; i < 12; i++){
            float angle = i * 30f + Mathf.randomSeed(e.id, 25f);
            float length = radius * progress * Mathf.randomSeed(e.id + i + 1, 0.65f, 1f);
            float dx = Angles.trnsx(angle, length), dy = Angles.trnsy(angle, length);
            float bend = Mathf.randomSeed(e.id + i + (int)(e.time / 4f) * 31, -8f, 8f) * e.fout();
            float mx = e.x + dx * 0.55f + Angles.trnsx(angle + 90f, bend);
            float my = e.y + dy * 0.55f + Angles.trnsy(angle + 90f, bend);
            Lines.line(e.x + dx * 0.22f, e.y + dy * 0.22f, mx, my);
            Lines.line(mx, my, e.x + dx, e.y + dy);
        }
        Drawf.light(e.x, e.y, 100f, blue, 0.85f * e.fout());
        Draw.reset();
    }).layer(Layer.effect + 0.01f);

    public static final Effect ionTrail = new Effect(18f, e -> {
        Draw.color(blue, Color.white, e.fout());
        Lines.stroke(1.3f * e.fout());
        float angle = e.rotation + 90f;
        float length = 3f + 5f * e.fin();
        float dx = Angles.trnsx(angle, length), dy = Angles.trnsy(angle, length);
        Lines.line(e.x - dx, e.y - dy, e.x + dy * 0.5f, e.y - dx * 0.5f);
        Lines.line(e.x + dy * 0.5f, e.y - dx * 0.5f, e.x + dx, e.y + dy);
        Draw.reset();
    });

    public static final Effect muzzle = new Effect(16f, e -> {
        Draw.color(blue, Color.white, e.fout());
        Lines.stroke(2.2f * e.fout());
        Lines.circle(e.x, e.y, 2f + 10f * e.fin());
        Drawf.tri(e.x, e.y, 5f * e.fout(), 22f * e.fout(), e.rotation);
        Drawf.light(e.x, e.y, 42f, blue, e.fout());
        Draw.reset();
    });
}
