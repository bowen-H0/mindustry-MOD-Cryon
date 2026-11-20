package cryon.Fx;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import mindustry.entities.Effect;
import mindustry.graphics.*;

public final class FaultFx{
    public static final Color orange = Color.valueOf("ffab68"), pale = Color.valueOf("ffe3bd");
    private FaultFx(){}
    private static float px(float x, float rotation, float forward, float side){
        return x + Angles.trnsx(rotation, forward, side);
    }
    private static float py(float y, float rotation, float forward, float side){
        return y + Angles.trnsy(rotation, forward, side);
    }
    /** A curved cup whose center is recessed behind its two forward shoulders. */
    public static void cup(float x, float y, float rotation, float spread, float depth){
        for(int i = 0; i < 18; i++){
            float a = -1f + i / 9f, b = -1f + (i + 1) / 9f;
            Lines.line(px(x, rotation, depth * a * a, spread * a), py(y, rotation, depth * a * a, spread * a),
                    px(x, rotation, depth * b * b, spread * b), py(y, rotation, depth * b * b, spread * b));
        }
    }
    public static final Effect charge = new Effect(24f, 90f, e -> {
        Draw.color(orange, pale, e.fin());
        Lines.stroke(0.8f + e.fin() * 1.4f);
        cup(e.x, e.y, e.rotation, 15f - 10f * e.fin(), 10f - 7f * e.fin());
        for(int i = 0; i < 8; i++){
            float angle = e.rotation + i * 45f;
            float length = 4f + 20f * e.fout();
            Fill.circle(e.x + Angles.trnsx(angle, length), e.y + Angles.trnsy(angle, length), 0.7f + e.fin());
        }
        Drawf.light(e.x, e.y, 40f, orange, 0.6f * e.fin());
        Draw.reset();
    }).followParent(true).rotWithParent(true);

    public static final Effect shoot = new Effect(25f, 110f, e -> {
        Draw.color(orange);
        Lines.stroke(3.5f * e.fout());
        cup(e.x, e.y, e.rotation, 8f + 15f * e.fin(), 5f + 14f * e.fin());
        Draw.color(pale, Color.white, e.fout());
        Lines.stroke(1.5f * e.fout());
        cup(e.x, e.y, e.rotation, 5f + 12f * e.fin(), 3f + 10f * e.fin());
        for(int sign : Mathf.signs){
            float angle = e.rotation + sign * 68f;
            Drawf.tri(e.x, e.y, 3f * e.fout(), 22f * e.fout(), angle);
        }
        Drawf.light(e.x, e.y, 70f, orange, e.fout());
        Draw.reset();
    });

    public static final Effect hit = new Effect(20f, 70f, e -> {
        Draw.color(pale, orange, e.fin());
        Lines.stroke(1.8f * e.fout());
        Lines.circle(e.x, e.y, 2f + 10f * e.fin());
        for(int i = 0; i < 6; i++){
            float angle = e.rotation + Mathf.randomSeed(e.id + i, -85f, 85f);
            float length = 3f + 17f * e.fin();
            Lines.lineAngle(e.x + Angles.trnsx(angle, length), e.y + Angles.trnsy(angle, length), angle, 6f * e.fout());
        }
        Drawf.light(e.x, e.y, 35f, orange, e.fout());
        Draw.reset();
    });
}
