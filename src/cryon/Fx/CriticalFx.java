package cryon.Fx;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Interp;
import mindustry.Vars;
import mindustry.entities.Effect;
import mindustry.graphics.Drawf;

/** Main-shell punctuation, with fixed geometry and smaller mobile particle budgets. */
public class CriticalFx{
    public static final Color pink = Color.valueOf("ff4fa3");

    public static final Effect shoot = new Effect(38f, 360f, e -> {
        Draw.color(e.color);
        Lines.stroke(5f * e.fout());
        Lines.circle(e.x, e.y, 8f + 60f * Interp.pow3Out.apply(e.fin()));
        for(int sign : arc.math.Mathf.signs){
            Drawf.tri(e.x, e.y, 22f * e.fout(), 110f * e.fout(), e.rotation + sign * 90f);
            Drawf.tri(e.x, e.y, 12f * e.fout(), 145f * e.fout(), e.rotation + sign * 14f);
        }
        Draw.color(Color.white);
        Drawf.tri(e.x, e.y, 12f * e.fout(), 100f * e.fout(), e.rotation);
        Fill.circle(e.x, e.y, 15f * e.fout());
        Drawf.light(e.x, e.y, 180f * e.fout(), e.color, 0.8f * e.fout());
        Draw.reset();
    });

    public static final Effect impact = new Effect(52f, 460f, e -> {
        float expand = Interp.pow3Out.apply(e.fin());
        Draw.color(e.color);
        Lines.stroke(6f * e.fout());
        Lines.circle(e.x, e.y, 5f + 92f * expand);
        Lines.stroke(2.5f * e.fout());
        Lines.circle(e.x, e.y, 4f + 61f * expand);
        // Sparse radial fractures read as an impact rather than a cloud of smoke.
        Angles.randLenVectors(e.id, Vars.mobile ? 10 : 20, 12f + 108f * expand, (x, y) -> {
            float angle = Angles.angle(x, y);
            Draw.color(e.color);
            Drawf.tri(e.x + x, e.y + y, 4f * e.fout(), 24f * e.fout(), angle);
            Draw.color(Color.white);
            Fill.circle(e.x + x, e.y + y, 2f * e.fout());
        });
        e.scaled(12f, flash -> {
            Draw.color(Color.white);
            Fill.circle(e.x, e.y, 28f * flash.fout());
            for(int i = 0; i < 4; i++){
                Drawf.tri(e.x, e.y, 9f * flash.fout(), 100f * flash.fout(), e.rotation + i * 90f);
            }
        });
        Drawf.light(e.x, e.y, 200f * e.fout(), e.color, 0.85f * e.fout());
        Draw.reset();
    });

    public static final Effect satelliteHit = new Effect(22f, 160f, e -> {
        Draw.color(e.color, Color.white, e.fout());
        Lines.stroke(2f * e.fout());
        Lines.circle(e.x, e.y, 3f + 32f * e.fin());
        Angles.randLenVectors(e.id, Vars.mobile ? 3 : 6, 36f * e.fin(), (x, y) ->
            Fill.square(e.x + x, e.y + y, 2f * e.fout(), 45f));
        Draw.reset();
    });
}
