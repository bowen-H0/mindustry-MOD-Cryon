package cryon.Fx;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import mindustry.entities.Effect;
import mindustry.graphics.Layer;

/** Countdown and blast visuals; actual electrical attacks are native projectile entities. */
public final class EmpFx{
    public static final Color blue = Color.valueOf("88e8ff");
    public static final Effect burst = new Effect(60f, 400f, e -> {
        Draw.color(blue, Color.white, e.fout());
        Lines.stroke(4f * e.fout()); Lines.circle(e.x, e.y, e.rotation * e.finpow());
        Lines.stroke(1.5f * e.fout()); Lines.circle(e.x, e.y, e.rotation * e.finpow() * 0.65f);
        for(int i = 0; i < 24; i++){
            float angle = i * 15f, radius = e.rotation * e.finpow();
            Fill.square(e.x + Angles.trnsx(angle, radius), e.y + Angles.trnsy(angle, radius), 2f * e.fout(), angle + 45f);
        }
        Draw.reset();
    }).layer(Layer.bullet + 0.2f);
    public static final Effect charge = new Effect(24f, e -> {
        Draw.color(blue);
        Lines.stroke(e.fout()); Lines.square(e.x, e.y, 3f + e.fin() * 9f, 45f);
        Draw.reset();
    }).layer(Layer.blockOver);

}
