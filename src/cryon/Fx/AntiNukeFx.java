package cryon.Fx;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Time;
import cryon.Script.NukeStrikeScript;
import mindustry.entities.Effect;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;

/** Neutralized warhead: a large, harmless light sphere, with no nuclear blast. */
public class AntiNukeFx{
    private static final Color exhaust = Color.valueOf("72dfff"), hull = Color.valueOf("dce7ef");

    public static final Effect launch = new Effect(65f, 180f, e -> {
        for(int i = 0; i < 12; i++){
            float angle = i * 30f, distance = 5f + e.fin() * (13f + i % 3 * 4f);
            Draw.color(Color.gray, 0.48f * e.fout());
            Fill.circle(e.x + Angles.trnsx(angle, distance),
                e.y + Angles.trnsy(angle, distance), 2f + 5f * e.fin());
        }
        Draw.color(e.color, e.fout());
        Lines.stroke(1.5f * e.fout());
        Lines.circle(e.x, e.y, 5f + e.fin() * 15f);
        Draw.reset();
    }).layer(Layer.blockOver + 0.5f);

    public static final Effect interceptor = new Effect(600f, 9000f, e -> {
        if(!(e.data instanceof NukeStrikeScript)) return;
        NukeStrikeScript strike = (NukeStrikeScript)e.data;
        if(!strike.alive || !strike.intercepting || strike.phase != NukeStrikeScript.FALL) return;
        float x = strike.interceptorX, y = strike.interceptorY;
        float angle = Angles.angle(strike.interceptorLastX, strike.interceptorLastY, x, y);
        if(Mathf.dst2(strike.interceptorLastX, strike.interceptorLastY, x, y) < 0.01f) angle = 90f;
        // A readable fuselage, fins and nose, independent of weather/processor HUD visibility.
        Draw.color(exhaust, 0.8f);
        Drawf.tri(x + Angles.trnsx(angle + 180f, 9f), y + Angles.trnsy(angle + 180f, 9f),
            8f, 27f + Mathf.absin(Time.time, 2f, 6f), angle + 180f);
        Draw.color(Color.white);
        Lines.stroke(4.5f);
        Lines.lineAngle(x, y, angle + 180f, 13f);
        Draw.color(hull);
        Drawf.tri(x, y, 7f, 10f, angle);
        Draw.color(exhaust);
        Drawf.tri(x + Angles.trnsx(angle + 180f, 10f), y + Angles.trnsy(angle + 180f, 10f),
            12f, 7f, angle);
        Draw.color(Color.lightGray, 0.28f);
        Lines.stroke(2f);
        Lines.line(e.x, e.y, x, y);
        Draw.reset();
        Lines.stroke(1f);
    }).layer(Layer.flyingUnit + 2f);

    public static final Effect neutralized = new Effect(120f, 700f, e -> {
        float attack = 1f - Mathf.pow(1f - Mathf.clamp(e.time / 7f), 3f);
        float fade = Mathf.clamp((120f - e.time) / 75f);
        float size = attack * (1f - 0.18f * e.fin());
        float flash = Mathf.clamp(1f - e.time / 18f);
        // A sharp initial flash and tapered rays give the impact a fast attack.
        Draw.color(Color.white, 0.32f * flash);
        Fill.circle(e.x, e.y, 210f * attack);
        for(int i = 0; i < 16; i++){
            float angle = i * 22.5f + Mathf.randomSeed(e.id + i, -7f, 7f);
            float length = Mathf.randomSeed(e.id * 31L + i, 130f, 250f) * attack;
            Draw.color(e.color, 0.75f * flash);
            Drawf.tri(e.x, e.y, 13f * flash, length, angle);
        }
        // Two short-lived pressure fronts, separate from the lingering light sphere.
        for(int i = 0; i < 2; i++){
            float progress = Mathf.clamp((e.time - i * 7f) / 35f);
            if(progress <= 0f || progress >= 1f) continue;
            float expansion = 1f - Mathf.pow(1f - progress, 3f);
            Draw.color(i == 0 ? Color.white : e.color, (1f - progress) * 0.85f);
            Lines.stroke((i == 0 ? 8f : 5f) * (1f - progress));
            Lines.circle(e.x, e.y, 18f + 230f * expansion);
        }
        // Broad translucent halo around a dense, bright core; ordinary alpha blending throughout.
        for(int i = 5; i >= 1; i--){
            Draw.color(e.color, (0.05f + (5 - i) * 0.018f) * fade);
            Fill.circle(e.x, e.y, (95f + i * 15f) * size);
        }
        Draw.color(e.color, 0.92f * fade);
        Fill.circle(e.x, e.y, 87f * size);
        Draw.color(Color.white, fade);
        Fill.circle(e.x, e.y, 65f * size);
        // Fast sparks carry momentum outward while the central ball hangs in the air.
        for(int i = 0; i < 20; i++){
            float angle = Mathf.randomSeed(e.id * 73L + i, 0f, 360f);
            float progress = Mathf.clamp(e.time / 45f);
            float distance = (1f - Mathf.pow(1f - progress, 2f))
                * Mathf.randomSeed(e.id * 97L + i, 120f, 230f);
            Draw.color(e.color, (1f - progress) * 0.9f);
            Lines.stroke(2f * (1f - progress));
            Lines.lineAngle(e.x + Angles.trnsx(angle, distance),
                e.y + Angles.trnsy(angle, distance), angle, 10f * (1f - progress));
        }
        Draw.reset();
        Lines.stroke(1f);
    }).layer(Layer.flyingUnit + 3f);
}
