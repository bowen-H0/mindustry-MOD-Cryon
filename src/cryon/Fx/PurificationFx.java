package cryon.Fx;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.util.Tmp;
import cryon.Type.OrbitalPowerNode;
import mindustry.Vars;
import mindustry.entities.Effect;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;

public final class PurificationFx{
    public static final Color blue = Color.valueOf("7edcff");
    private static final Color hot = Color.valueOf("e9fdff");
    private static final Color azure = Color.valueOf("3295ef");
    private static final Color smoke = Color.valueOf("344357");

    private static float skyHeight(float y){
        return Core.camera == null ? 1400f :
            Math.max(1400f, Core.camera.position.y + Core.camera.height / 2f + 300f - y);
    }

    private static float skyOffset(float x, float height){
        return height * 0.65f + (Core.camera == null ? 0f :
            OrbitalPowerNode.parallaxOffset(x, Core.camera.position.x, 1f, 180f));
    }

    /** A moving, tapered energy rod with the orbital node's camera parallax. */
    public static void drawDescent(float x, float y, float radius, float progress){
        float height = skyHeight(y);
        float offset = skyOffset(x, height);
        float head = 1f - Interp.pow2In.apply(progress);
        float tail = Math.min(1f, head + 0.18f + progress * 0.16f);
        float hx = x + offset * head, hy = y + height * head;
        float tx = x + offset * tail, ty = y + height * tail;
        float width = (2.5f + radius * 0.065f) * (0.35f + progress * 0.65f);
        Draw.z(Layer.effect + 0.2f);
        Draw.color(azure);
        Draw.alpha(0.22f);
        Lines.stroke(width * 4f);
        Lines.line(tx, ty, hx, hy);
        Draw.color(blue);
        Draw.alpha(0.85f);
        Lines.stroke(width * 1.5f);
        Lines.line(tx, ty, hx, hy);
        Draw.color(hot);
        Draw.alpha(1f);
        Lines.stroke(width * 0.5f);
        Lines.line(tx, ty, hx, hy);
        Fill.circle(hx, hy, width * 0.75f);
        float angle = Angles.angle(tx, ty, hx, hy);
        Drawf.tri(hx, hy, width * 1.5f, width * 6f, angle);
        for(int i = 0; i < 3; i++){
            float part = Mathf.lerp(head, tail, (i + 1f) / 4f);
            float px = x + offset * part, py = y + height * part;
            Draw.color(blue);
            Draw.alpha(0.35f);
            Lines.stroke(1.5f);
            Lines.lineAngle(px, py, angle + 55f, width * 4f);
            Lines.lineAngle(px, py, angle - 55f, width * 4f);
        }
        Drawf.light(hx, hy, width * 10f, blue, 0.8f);
        Draw.reset();
    }

    public static final Effect beam = new Effect(26f, 6000f, e -> {
        float height = skyHeight(e.y);
        float offset = skyOffset(e.x, height);
        float width = (8f + e.rotation * 0.2f) * e.fout();
        Draw.z(Layer.effect + 0.1f);
        // Broken sheets retain gaps and separate layers along the slanted path.
        for(int lane = -1; lane <= 1; lane++){
            for(int segment = 0; segment < 18; segment++){
                if((segment + lane + e.id) % 7 == 0) continue;
                float a = segment / 18f, b = (segment + 0.82f) / 18f;
                float ax = e.x + offset * a + lane * width * (0.55f + a);
                float ay = e.y + height * a;
                float bx = e.x + offset * b + lane * width * (0.55f + b);
                float by = e.y + height * b;
                float energy = 0.5f + 0.5f * Mathf.sin(e.time * 0.6f - segment + lane);
                Draw.color(blue);
                Draw.alpha((0.12f + energy * 0.18f) * e.fout());
                Lines.stroke(width * (0.4f + a * 0.2f));
                Lines.line(ax, ay, bx, by);
            }
        }
        Draw.color(blue);
        Draw.alpha(0.7f * e.fout());
        Lines.stroke(width * 0.7f);
        Lines.line(e.x + offset, e.y + height, e.x, e.y);
        Draw.color(hot);
        Draw.alpha(e.fout());
        Lines.stroke(width * 0.2f);
        Lines.line(e.x + offset, e.y + height, e.x, e.y);
        Fill.light(e.x, e.y, 36, e.rotation * 0.65f,
            Tmp.c1.set(blue).a(0.5f * e.fout()), Tmp.c2.set(azure).a(0f));
        Draw.reset();
    });

    public static final Effect impact = new Effect(78f, 1400f, e -> {
        float radius = e.rotation;
        float expansion = Interp.pow3Out.apply(Mathf.clamp(e.time / 32f));
        Draw.z(Layer.effect);
        Draw.color(smoke);
        Draw.alpha(0.38f * e.fout());
        Fill.circle(e.x, e.y, radius * 0.52f * expansion);
        Draw.color(hot, blue, e.fin());
        Draw.alpha(e.fout());
        Lines.stroke(12f * e.fout());
        Lines.circle(e.x, e.y, radius * expansion);
        Draw.color(azure);
        Draw.alpha(0.7f * e.fout());
        Lines.stroke(22f * e.fout());
        Lines.circle(e.x, e.y, radius * expansion * 0.88f);
        Angles.randLenVectors(e.id, Vars.mobile ? 8 : 14, radius * expansion * 0.85f, (x, y) -> {
            Draw.color(smoke);
            Draw.alpha(0.6f * e.fout());
            Fill.circle(e.x + x, e.y + y, radius * (0.08f + e.fin() * 0.07f) * e.fout());
            Draw.color(blue, azure, e.fin());
            Draw.alpha(e.fout());
            Drawf.tri(e.x + x, e.y + y, 4f * e.fout(),
                radius * 0.28f * e.fout(), Angles.angle(x, y));
        });
        // A lower fireball and an elevated rolling cap give the blast volume.
        e.scaled(26f, fireball -> {
            float ball = radius * 0.36f * (0.35f + 0.65f * Interp.pow3Out.apply(fireball.fin()));
            Draw.color(azure);
            Draw.alpha(0.8f * fireball.fout());
            Fill.circle(e.x, e.y, ball);
            Draw.color(blue);
            Draw.alpha(fireball.fout());
            Fill.circle(e.x, e.y, ball * 0.78f);
            Draw.color(hot);
            Fill.circle(e.x, e.y, ball * 0.45f * fireball.fout());
        });
        float rise = Interp.pow2Out.apply(Mathf.clamp((e.time - 7f) / 38f));
        float altitude = radius * 0.38f * rise;
        float cap = radius * (0.12f + 0.12f * rise);
        Draw.color(azure, smoke, e.fin());
        Draw.alpha(rise * e.fout() * 0.6f);
        Fill.quad(e.x - radius * 0.055f, e.y, e.x + radius * 0.055f, e.y,
            e.x + cap * 0.36f, e.y + altitude, e.x - cap * 0.36f, e.y + altitude);
        for(int i = 0; i < 7; i++){
            float angle = i * (360f / 7f) + e.time * 0.7f;
            float x = e.x + Mathf.cosDeg(angle) * cap * 0.72f;
            float y = e.y + altitude + Mathf.sinDeg(angle) * cap * 0.3f;
            Draw.color(i % 2 == 0 ? blue : azure, smoke, e.fin());
            Draw.alpha(rise * e.fout() * 0.72f);
            Fill.circle(x, y, cap * 0.6f);
        }
        e.scaled(9f, flash -> {
            Draw.color(Color.white);
            Draw.alpha(flash.fout());
            Fill.circle(e.x, e.y, radius * 0.35f * flash.fout());
            Drawf.tri(e.x, e.y, radius * 0.08f * flash.fout(),
                radius * 1.3f * flash.fout(), 62f);
            Drawf.tri(e.x, e.y, radius * 0.08f * flash.fout(),
                radius * 0.75f * flash.fout(), 242f);
        });
        Drawf.light(e.x, e.y, radius * 2.2f, blue, 0.8f * e.fout());
        Draw.reset();
    });
}
