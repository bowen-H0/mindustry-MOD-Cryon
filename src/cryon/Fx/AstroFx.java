package cryon.Fx;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.entities.Effect;
import mindustry.graphics.Drawf;

import static arc.graphics.g2d.Draw.color;
import static arc.graphics.g2d.Lines.stroke;

/**
 * 天文观测特效集合。
 * 主色通过 effect.at(x, y, rot, color) 传入（读取 e.color），副色固定为星光色。
 * 所有动画角度都基于 Time.time，多个重叠的观测仪特效会严丝合缝地对齐。
 */
public class AstroFx {
    static final Color star = Color.valueOf("fff6d8");

    /** 沿倾斜椭圆取点，结果写入 Tmp.v1（已加上圆心偏移） */
    private static void orbitPoint(float cx, float cy, float rx, float ry, float tilt, float ang){
        Tmp.v1.set(rx * Mathf.cosDeg(ang), ry * Mathf.sinDeg(ang)).rotate(tilt).add(cx, cy);
    }

    private static void orbit(float cx, float cy, float rx, float ry, float tilt, int seg){
        float px = 0, py = 0, fx = 0, fy = 0;
        for(int i = 0; i <= seg; i++){
            orbitPoint(cx, cy, rx, ry, tilt, 360f / seg * i);
            if(i == 0){ fx = Tmp.v1.x; fy = Tmp.v1.y; }
            else Lines.line(px, py, Tmp.v1.x, Tmp.v1.y);
            px = Tmp.v1.x; py = Tmp.v1.y;
        }
    }

    /** 浑天仪观测阵：三层轨道环 + 行星 + 黄道刻度 + 星座连线 + 扫描波束。持续 4 秒，淡入淡出 */
    public static final Effect observatory = new Effect(240f, 900f, e -> {
        Color back = e.color;
        float t = Time.time;
        float alpha = Mathf.clamp(e.fslope() * 3f);
        float cx = e.x, cy = e.y;

        Drawf.light(cx, cy, 280f, back, 0.7f * alpha);

        // ---- 黄道外环 + 刻度 ----
        float R = 190f;
        color(back);
        Draw.alpha(alpha * 0.8f);
        stroke(1.6f);
        Lines.circle(cx, cy, R);
        Lines.circle(cx, cy, R - 10f);
        float rot = t * 0.05f;
        for(int i = 0; i < 48; i++){
            float a = 360f / 48f * i + rot;
            float len = i % 4 == 0 ? 14f : 6f;
            Lines.lineAngle(cx + Mathf.cosDeg(a) * (R - 10f), cy + Mathf.sinDeg(a) * (R - 10f), a + 180f, len * 0.6f);
            Lines.lineAngle(cx + Mathf.cosDeg(a) * R, cy + Mathf.sinDeg(a) * R, a, len * 0.6f);
        }

        // ---- 三层倾斜轨道环 + 行星（带拖尾） ----
        float[] rx = {70f, 110f, 150f};
        float[] ry = {28f, 40f, 55f};
        float[] tiltBase = {20f, 80f, 140f};
        float[] speed = {1.6f, -1.0f, 0.6f};
        for(int i = 0; i < 3; i++){
            float tilt = tiltBase[i] + t * 0.15f * (i % 2 == 0 ? 1f : -1f) * (i + 1);
            color(back);
            Draw.alpha(alpha * 0.55f);
            stroke(1.1f);
            orbit(cx, cy, rx[i], ry[i], tilt, 64);

            float ang = t * speed[i] + i * 90f;
            for(int k = 10; k >= 0; k--){
                orbitPoint(cx, cy, rx[i], ry[i], tilt, ang - k * 4f * Mathf.sign(speed[i]));
                color(back, star, 1f - k / 10f);
                Draw.alpha(alpha * (1f - k / 11f));
                Fill.circle(Tmp.v1.x, Tmp.v1.y, (1f + i * 0.4f) * (1f - k / 14f) + 0.5f);
            }
            orbitPoint(cx, cy, rx[i], ry[i], tilt, ang);
            color(star);
            Draw.alpha(alpha);
            Fill.circle(Tmp.v1.x, Tmp.v1.y, 3f + i);
            Drawf.light(Tmp.v1.x, Tmp.v1.y, 28f, back, 0.6f * alpha);
        }

        // ---- 星座：固定种子，缓慢漂移 + 闪烁 + 连线 ----
        int n = 9;
        float lastX = 0, lastY = 0;
        for(int i = 0; i < n; i++){
            long seed = i * 31 + 7;
            float a = Mathf.randomSeed(seed, 0f, 360f) + t * 0.03f;
            float r = Mathf.randomSeed(seed + 1, 95f, 175f);
            float sx = cx + Mathf.cosDeg(a) * r, sy = cy + Mathf.sinDeg(a) * r;
            if(i > 0){
                color(back);
                Draw.alpha(alpha * 0.35f);
                stroke(0.9f);
                Lines.line(lastX, lastY, sx, sy);
            }
            float tw = 1.5f + 1.5f * Mathf.absin(t + i * 13f, 14f, 1f);
            color(star);
            Draw.alpha(alpha);
            Fill.circle(sx, sy, tw);
            Drawf.tri(sx, sy, tw * 0.9f, tw * 6f, 0f);
            Drawf.tri(sx, sy, tw * 0.9f, tw * 6f, 90f);
            Drawf.tri(sx, sy, tw * 0.9f, tw * 6f, 180f);
            Drawf.tri(sx, sy, tw * 0.9f, tw * 6f, 270f);
            lastX = sx; lastY = sy;
        }

        // ---- 扫描波束：渐隐拖影 ----
        float sw = t * 2.2f;
        for(int k = 0; k < 12; k++){
            float f = 1f - k / 12f;
            color(back);
            Draw.alpha(alpha * f * 0.5f);
            stroke(2.5f * f + 0.3f);
            Lines.lineAngle(cx, cy, sw - k * 3f, R - 10f);
        }
        float tipX = cx + Mathf.cosDeg(sw) * (R - 10f), tipY = cy + Mathf.sinDeg(sw) * (R - 10f);
        color(star);
        Draw.alpha(alpha);
        Fill.circle(tipX, tipY, 3.5f);
        Drawf.light(tipX, tipY, 40f, back, 0.8f * alpha);

        // ---- 中心恒星 + 旋转光芒 ----
        float pulse = Mathf.absin(t, 20f, 1f);
        color(back, star, 0.5f + 0.5f * pulse);
        Draw.alpha(alpha);
        Fill.circle(cx, cy, 6f + 2f * pulse);
        color(star);
        Draw.alpha(alpha);
        Fill.circle(cx, cy, 3f);
        for(int i = 0; i < 4; i++){
            Drawf.tri(cx, cy, 5f, 42f + 10f * pulse, t * 0.8f + 90f * i);
        }

        Draw.reset();
    });

    /** 彗星：从 e.rotation 方向划过，带双层尾焰与碎屑。 e.rotation = 飞行方向 */
    public static final Effect comet = new Effect(80f, 700f, e -> {
        Color back = e.color;
        float alpha = Mathf.clamp(e.fslope() * 4f);
        float dist = 520f * Interp.pow2Out.apply(e.fin());
        float px = e.x + Angles.trnsx(e.rotation, dist);
        float py = e.y + Angles.trnsy(e.rotation, dist);
        float rot = e.rotation + 180f;

        color(back);
        Draw.alpha(alpha);
        Drawf.tri(px, py, 10f, 130f * (0.4f + 0.6f * e.fout()), rot);
        color(star);
        Draw.alpha(alpha);
        Drawf.tri(px, py, 5f, 70f * (0.4f + 0.6f * e.fout()), rot);

        // 尾部碎屑
        color(back);
        for(int i = 0; i < 10; i++){
            float off = 10f + Mathf.randomSeed(e.id + i, 0f, 90f);
            float side = Mathf.randomSeedRange(e.id + i * 3, 8f) * (off / 100f);
            float bx = px + Angles.trnsx(rot, off) + Angles.trnsx(rot + 90f, side);
            float by = py + Angles.trnsy(rot, off) + Angles.trnsy(rot + 90f, side);
            Draw.alpha(alpha * (1f - off / 110f));
            Fill.square(bx, by, 1.5f + Mathf.randomSeed(e.id + i * 5, 0f, 1.5f), 45f);
        }

        color(star);
        Draw.alpha(alpha);
        Fill.circle(px, py, 4f);
        Drawf.light(px, py, 70f, back, 0.8f * alpha);
        Draw.reset();
    }).layer(110f);

    /** 超新星脉冲：双层冲击环 + 放射光刺 + 碎片。 */
    public static final Effect nova = new Effect(80f, 800f, e -> {
        Color back = e.color;

        e.scaled(50f, c -> {
            color(Color.white, back, c.fin());
            stroke(c.fout() * 5f + 0.3f);
            Lines.circle(e.x, e.y, c.finpow() * 260f);
        });
        e.scaled(70f, c -> {
            color(back);
            stroke(c.fout() * 2.5f + 0.2f);
            Lines.circle(e.x, e.y, c.finpow() * 180f);
        });

        for(int i = 0; i < 2; i++){
            color(i == 0 ? back : star);
            float m = i == 0 ? 1f : 0.55f;
            for(int j = 0; j < 8; j++){
                float rot = 360f / 8f * j + 22.5f * i;
                Drawf.tri(e.x, e.y, 14f * e.fout() * m, (150f + (j % 2) * 60f) * e.finpow() * m + 10f, rot);
            }
        }

        color(star);
        for(int i = 0; i < 24; i++){
            float a = Mathf.randomSeed(e.id + i, 0f, 360f);
            float d = (30f + Mathf.randomSeed(e.id + i * 3, 0f, 190f)) * e.finpow();
            Fill.square(e.x + Mathf.cosDeg(a) * d, e.y + Mathf.sinDeg(a) * d, 2.5f * e.fout(), a + e.fin() * 180f);
        }

        Draw.alpha(e.fout());
        color(Color.white);
        Fill.circle(e.x, e.y, 14f * e.fout());
        Drawf.light(e.x, e.y, 300f * e.fout(), back, e.fout());
        Draw.reset();
    });
}