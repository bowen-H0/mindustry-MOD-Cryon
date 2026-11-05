package cryon.Fx;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import mindustry.entities.Effect;
import mindustry.gen.Unit;
import mindustry.graphics.*;

/** Original layered effects for Eon's charge, impact, collapse and final detonation. */
public final class EonCombatFx{
    public static final Color phase = Color.valueOf("bda6ff"), deep = Color.valueOf("6550b8");

    public static final Effect charge = new Effect(90f, 600f, e -> {
        Draw.color(phase);
        Lines.stroke(2f + e.fin() * 3f);
        for(int i = 0; i < 3; i++)
            Lines.arc(e.x, e.y, 18f + (1f - e.fin()) * (85f + i * 15f), 0.22f, e.time * 4f + i * 120f);
        Draw.blend(Blending.additive);
        Fill.light(e.x, e.y, 40, 45f + e.fin() * 45f,
            Tmp.c1.set(phase).a(e.fin() * 0.6f), Tmp.c2.set(deep).a(0f));
        Draw.color(Color.white);
        Fill.circle(e.x, e.y, 3f + 12f * e.finpow());
        Angles.randLenVectors(e.id, 22, 20f + 130f * e.fout(), (x, y) -> {
            Draw.color(phase);
            Fill.circle(e.x + x, e.y + y, 1.5f + e.fin() * 2f);
        });
        Draw.blend(); Draw.reset();
    });

    public static final Effect muzzle = new Effect(42f, 700f, e -> {
        Draw.color(Color.white, phase, e.fin());
        Drawf.tri(e.x, e.y, 42f * e.fout(), 160f * e.fout(), e.rotation);
        Drawf.tri(e.x, e.y, 32f * e.fout(), 70f * e.fout(), e.rotation + 180f);
        Lines.stroke(7f * e.fout());
        Lines.circle(e.x, e.y, 12f + 95f * e.finpow());
        Draw.blend(Blending.additive);
        Fill.light(e.x, e.y, 36, 115f * e.fout(), Tmp.c1.set(phase).a(0.7f * e.fout()), Tmp.c2.set(phase).a(0f));
        Draw.blend(); Draw.reset();
    });

    public static final Effect initial = explosion(85f, false), finalBlast = explosion(125f, true);

    private static Effect explosion(float lifetime, boolean terminal){
        return new Effect(lifetime, 1800f, e -> {
            float r = e.rotation, progress = e.finpow();
            Draw.blend(Blending.additive);
            Fill.light(e.x, e.y, 60, r * (0.8f + e.fin() * 0.4f),
                Tmp.c1.set(phase).a(0.65f * e.fout()), Tmp.c2.set(deep).a(0f));
            Draw.color(Color.white, phase, e.fin());
            Lines.stroke((terminal ? 22f : 13f) * e.fout());
            Lines.circle(e.x, e.y, 12f + r * progress);
            Lines.stroke(5f * e.fout());
            Lines.circle(e.x, e.y, r * Mathf.clamp((e.fin() - 0.12f) / 0.88f));
            Fill.circle(e.x, e.y, r * 0.2f * e.fout() * e.fout());
            for(int i = 0; i < (terminal ? 8 : 4); i++){
                float angle = i * (terminal ? 45f : 90f) + 22.5f;
                Drawf.tri(e.x, e.y, (terminal ? 24f : 15f) * e.fout(),
                    r * (terminal ? 1.4f : 0.9f) * e.fout(), angle);
            }
            Angles.randLenVectors(e.id, terminal ? 52 : 32, r * 1.2f * progress, (x, y) -> {
                float angle = Mathf.angle(x, y);
                Lines.stroke(3f * e.fout());
                Lines.lineAngle(e.x + x, e.y + y, angle, 10f + 42f * e.fout());
            });
            Draw.blend(); Draw.reset();
            Drawf.light(e.x, e.y, r * 2f, phase, e.fout());
        });
    }

    public static void reactor(Unit unit){
        float rotation = unit.rotation - 90f;
        float x = unit.x + Angles.trnsx(rotation, 0f, -66.4f);
        float y = unit.y + Angles.trnsy(rotation, 0f, -66.4f);
        Draw.blend(Blending.additive);
        Draw.color(phase);
        Draw.alpha(0.48f);
        Lines.stroke(1.2f);
        Lines.arc(x, y, 12f, 0.32f, Time.time * 1.7f);
        Lines.arc(x, y, 9f, 0.32f, -Time.time * 2.1f + 180f);
        Fill.light(x, y, 32, 23f, Tmp.c1.set(phase).a(0.25f + Mathf.absin(8f, 0.1f)), Tmp.c2.set(phase).a(0f));
        Draw.blend(); Draw.reset();
    }

    public static void drive(Unit unit){
        float rotation = unit.rotation - 90f;
        float thrust = 0.7f + Math.min(unit.vel.len(), 1f) * 0.5f;
        Draw.blend(Blending.additive);
        for(int side = -1; side <= 1; side += 2){
            float x = unit.x + Angles.trnsx(rotation, side * 3.6f, -174f);
            float y = unit.y + Angles.trnsy(rotation, side * 3.6f, -174f);
            float pulse = 1f + Mathf.absin(Time.time + side * 11f, 4f, 0.12f);
            Draw.color(deep);
            Drawf.tri(x, y, 5.5f * pulse, 80f * thrust, unit.rotation + 180f);
            Draw.color(phase);
            Drawf.tri(x, y, 3.0f * pulse, 58f * thrust, unit.rotation + 180f);
            Draw.color(Color.white);
            Drawf.tri(x, y, 1.4f, 38f * thrust, unit.rotation + 180f);
            Drawf.light(x, y, 60f, phase, 0.75f);
        }
        Draw.blend(); Draw.reset();
    }
}
