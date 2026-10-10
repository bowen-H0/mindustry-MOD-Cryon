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
import cryon.Script.OrbitalStrikeScript;
import mindustry.entities.Effect;
import mindustry.graphics.Drawf;

import static arc.graphics.g2d.Draw.color;
import static arc.graphics.g2d.Lines.stroke;

/**
 * 轨道打击特效集合。
 * hud：一个长寿命特效，每帧读取脚本的实时状态来绘制（核心、测量图、瞄准镜、光束）。
 * mark / impact：一次性特效。
 * 主色(测量图/核心)通过 effect.at(..., color, data) 传入，危险色固定为红。
 */
public class OrbitalFx {
    static final Color danger = Color.valueOf("ff3b30");
    static final Color hot = Color.valueOf("ffd2a8");
    private static final Color tmp = new Color();

    // ---------- 绘制工具 ----------
    private static void orbitPoint(float cx, float cy, float rx, float ry, float tilt, float ang){
        Tmp.v1.set(rx * Mathf.cosDeg(ang), ry * Mathf.sinDeg(ang)).rotate(tilt).add(cx, cy);
    }

    private static void orbit(float cx, float cy, float rx, float ry, float tilt, int seg){
        float px = 0f, py = 0f;
        for(int i = 0; i <= seg; i++){
            orbitPoint(cx, cy, rx, ry, tilt, 360f / seg * i);
            if(i > 0) Lines.line(px, py, Tmp.v1.x, Tmp.v1.y);
            px = Tmp.v1.x; py = Tmp.v1.y;
        }
    }

    private static void dashedCircle(float x, float y, float r, int segs, float rot){
        for(int i = 0; i < segs; i++){
            Lines.arc(x, y, r, 0.5f / segs, rot + 360f / segs * i);
        }
    }

    // ---------- 主 HUD ----------
    public static final Effect hud = new Effect(3600f, 8000f, e -> {
        if(!(e.data instanceof OrbitalStrikeScript)) return;
        OrbitalStrikeScript s = (OrbitalStrikeScript)e.data;
        if(!s.alive || s.hudEpoch != (int)e.rotation) return;

        drawChart(s, e.color, e.x, e.y);
        if(s.reticleAlpha > 0.01f) drawReticle(s, e.color);
        drawCore(e.color, e.x, e.y);
        Draw.reset();
    });

    /** 中心发光核心 */
    private static void drawCore(Color chart, float cx, float cy){
        float pulse = Mathf.absin(Time.time, 18f, 1f);

        Drawf.light(cx, cy, 100f + 40f * pulse, chart, 0.9f);

        color(chart);
        Draw.alpha(0.3f);
        Fill.circle(cx, cy, 26f + 4f * pulse);

        Draw.alpha(1f);
        Fill.circle(cx, cy, 14f + 3f * pulse);

        color(Color.white);
        Fill.circle(cx, cy, 6f);

        color(chart);
        Draw.alpha(0.4f);
        stroke(2f);
        Lines.circle(cx, cy, 34f);
    }

    /** 处理器位置的测量图：刻度环、方位角、轨道平面 + 卫星、到打击点的测距线 */
    private static void drawChart(OrbitalStrikeScript s, Color c, float cx, float cy){
        float t = Time.time;
        float CR = 84f;

        color(c);
        Draw.alpha(0.55f);
        stroke(1.3f);
        Lines.circle(cx, cy, CR);
        Lines.circle(cx, cy, CR - 8f);
        Draw.alpha(0.3f);
        dashedCircle(cx, cy, CR * 0.55f, 24, t * 0.3f);

        // 射程环（淡虚线）
        Draw.alpha(0.18f);
        stroke(1.2f);
        dashedCircle(cx, cy, s.range, Math.max(48, (int)(s.range / 6f)), -t * 0.05f);
        Draw.alpha(0.55f);
        stroke(1.3f);

        Draw.alpha(0.7f);
        stroke(1f);
        float rot = t * 0.03f;
        for(int i = 0; i < 72; i++){
            float a = 5f * i + rot;
            float len = i % 6 == 0 ? 9f : (i % 2 == 0 ? 5f : 3f);
            Lines.lineAngle(cx + Mathf.cosDeg(a) * (CR - 8f), cy + Mathf.sinDeg(a) * (CR - 8f), a + 180f, len);
        }
        Draw.alpha(0.8f);
        for(int i = 0; i < 4; i++){
            Drawf.tri(cx + Angles.trnsx(i * 90f, CR + 4f), cy + Angles.trnsy(i * 90f, CR + 4f), 5f, 9f, i * 90f);
        }

        float ra = s.reticleAlpha;
        if(ra < 0.05f) return;

        float bearing = Angles.angle(cx, cy, s.rx, s.ry);

        // 方位角标记
        color(danger);
        Draw.alpha(ra);
        stroke(4f);
        Lines.arc(cx, cy, CR + 6f, 0.05f, bearing - 9f);
        Drawf.tri(cx + Angles.trnsx(bearing, CR + 16f), cy + Angles.trnsy(bearing, CR + 16f), 6f, 10f, bearing);

        // 轨道平面对准目标方位，卫星沿轨道运行（带拖尾）
        color(c);
        Draw.alpha(0.45f * ra);
        stroke(1f);
        orbit(cx, cy, 140f, 46f, bearing, 64);
        float ang = t * 1.4f;
        for(int k = 10; k >= 1; k--){
            orbitPoint(cx, cy, 140f, 46f, bearing, ang - k * 5f);
            Draw.alpha(ra * (1f - k / 11f));
            Fill.circle(Tmp.v1.x, Tmp.v1.y, 2.2f * (1f - k / 12f));
        }
        orbitPoint(cx, cy, 140f, 46f, bearing, ang);
        float sx = Tmp.v1.x, sy = Tmp.v1.y;
        color(Color.white);
        Draw.alpha(ra);
        Fill.square(sx, sy, 4.5f, 45f);
        Drawf.light(sx, sy, 36f, c, 0.7f * ra);

        // 卫星 -> 打击点 的瞄准线（蓄力越久越亮）
        float ch = s.phase == OrbitalStrikeScript.TRACK ? Mathf.clamp(s.timer / s.chargeTime) : 1f;
        color(danger);
        Draw.alpha(ra * (0.12f + 0.55f * ch));
        stroke(1f);
        Lines.line(sx, sy, s.rx, s.ry);

        // 测距虚线 + 每 6 段一个刻度
        float dist = Mathf.dst(cx, cy, s.rx, s.ry);
        int n = Math.min((int)(dist / 16f), 300);
        color(c);
        Draw.alpha(0.5f * ra);
        for(int i = 2; i < n; i++){
            float d0 = i * 16f;
            float px = cx + Angles.trnsx(bearing, d0), py = cy + Angles.trnsy(bearing, d0);
            Lines.lineAngle(px, py, bearing, 8f);
            if(i % 6 == 0){
                Lines.lineAngle(px, py, bearing + 90f, 6f);
                Lines.lineAngle(px, py, bearing - 90f, 6f);
            }
        }
    }

    /** 打击点：蓄力追踪 -> 锁定标记 -> 光束 -> 余烬 */
    private static void drawReticle(OrbitalStrikeScript s, Color chart){
        float t = Time.time, a = s.reticleAlpha, R = s.radius, x = s.rx, y = s.ry;

        switch(s.phase){
            case OrbitalStrikeScript.TRACK -> {
                float ch = Mathf.clamp(s.timer / s.chargeTime);
                tmp.set(chart).lerp(danger, ch);
                color(tmp);
                Draw.alpha(a * 0.8f);
                stroke(1.5f);
                dashedCircle(x, y, R, 28, -t * 0.4f);
                for(int i = 0; i < 4; i++){
                    Lines.lineAngle(x + Angles.trnsx(i * 90f, R * 0.12f), y + Angles.trnsy(i * 90f, R * 0.12f), i * 90f, R * 0.28f);
                }
                // 向目标收束的角括号
                float br = R * (1f + (1f - ch) * 1.4f) + 6f;
                Draw.alpha(a);
                stroke(3f);
                for(int i = 0; i < 4; i++){
                    float ba = 90f * i + 45f;
                    Lines.arc(x, y, br, 0.07f, ba - 12.6f);
                    Drawf.tri(x + Angles.trnsx(ba, br + 8f), y + Angles.trnsy(ba, br + 8f), 5f, 9f, ba + 180f);
                }
                // 蓄力进度弧
                Lines.arc(x, y, R + 14f, ch, 90f);
                Fill.circle(x, y, 2f + ch * 2f);
            }
            case OrbitalStrikeScript.LOCK -> {
                float p = Mathf.clamp(s.timer / s.lockTime);
                float blink = Mathf.absin(t, Mathf.lerp(18f, 4f, p), 1f);
                color(danger);
                Draw.alpha(a * (0.08f + 0.2f * blink));
                Fill.circle(x, y, R);
                Draw.alpha(a);
                stroke(2.5f);
                Lines.circle(x, y, R);
                Draw.alpha(a * 0.6f);
                stroke(1f);
                for(int i = 0; i < 16; i++){
                    float ang = 22.5f * i + t * 0.2f;
                    Lines.lineAngle(x + Angles.trnsx(ang, R * 0.25f), y + Angles.trnsy(ang, R * 0.25f), ang, R * 0.7f);
                }
                Draw.alpha(a * (0.6f + 0.4f * blink));
                stroke(2.5f);
                float k = R * 0.25f;
                Lines.line(x - k, y - k, x + k, y + k);
                Lines.line(x - k, y + k, x + k, y - k);
                // 固定角括号 + 倒计时弧
                Draw.alpha(a);
                stroke(3f);
                for(int i = 0; i < 4; i++){
                    float ba = 90f * i + 45f;
                    Lines.arc(x, y, R + 6f, 0.07f, ba - 12.6f);
                }
                color(hot);
                Draw.alpha(a);
                stroke(4f);
                Lines.arc(x, y, R + 14f, 1f - p, 90f);
            }
            case OrbitalStrikeScript.STRIKE -> {
                float p = Mathf.clamp(s.timer / s.strikeTime);
                float grow = Interp.pow2Out.apply(Mathf.clamp(p / 0.08f));
                float fade = p < 0.75f ? 1f : (1f - p) / 0.25f;
                float env = grow * fade;
                float w = R * 0.5f * env * (1f + 0.08f * Mathf.sin(t, 1.5f, 1f));
                float top = y + 1800f;

                // 地面灼烧圈
                color(danger);
                Draw.alpha(0.3f * env);
                Fill.circle(x, y, R * 0.95f);
                Draw.alpha(env);
                stroke(3f);
                Lines.circle(x, y, R);

                // 光束三层：红外晕 -> 暖色 -> 白芯
                color(danger);
                Draw.alpha(0.55f * env);
                Fill.quad(x - w * 1.5f, top, x + w * 1.5f, top, x + w * 1.1f, y, x - w * 1.1f, y);
                color(hot);
                Draw.alpha(0.85f * env);
                Fill.quad(x - w, top, x + w, top, x + w * 0.7f, y, x - w * 0.7f, y);
                color(Color.white);
                Draw.alpha(env);
                Fill.quad(x - w * 0.45f, top, x + w * 0.45f, top, x + w * 0.3f, y, x - w * 0.3f, y);

                // 光束内的流动光丝
                stroke(1.5f);
                Draw.alpha(0.8f * env);
                for(int i = 0; i < 6; i++){
                    float off = Mathf.randomSeedRange(i * 11L, w * 0.9f);
                    float start = y + ((t * 40f + i * 170f) % 900f);
                    Lines.line(x + off, start, x + off, start + 120f);
                }

                // 落点冲击：扩散环 + 火星
                color(hot);
                Draw.alpha(env);
                stroke(2f + 3f * (1f - p));
                Lines.circle(x, y, R * (0.3f + p * 0.8f));
                color(Color.white);
                Fill.circle(x, y, R * 0.35f * env);
                color(hot);
                for(int i = 0; i < 24; i++){
                    float ang = Mathf.randomSeed(i * 7L, 0f, 360f);
                    float d = R * (0.2f + Mathf.randomSeed(i * 13L, 0f, 0.9f)) * (0.5f + 0.5f * Mathf.absin(t + i * 5f, 12f, 1f));
                    Fill.square(x + Angles.trnsx(ang, d), y + Angles.trnsy(ang, d), 2.5f * env, ang + t * 4f);
                }
                Drawf.light(x, y, R * 3.5f * env, danger, env);
                Drawf.light(x, y + 200f, 300f * env, hot, 0.5f * env);
            }
            default -> {
                // COOLDOWN：余烬消散
                float p = Mathf.clamp(s.timer / s.cooldownTime);
                float fade = 1f - p;
                color(danger);
                Draw.alpha(a * fade * 0.6f);
                stroke(2f);
                Lines.circle(x, y, R * (1f - p * 0.2f));
                Draw.alpha(a * fade * 0.12f);
                Fill.circle(x, y, R);
                color(hot);
                for(int i = 0; i < 14; i++){
                    float ang = Mathf.randomSeed(i * 3L, 0f, 360f);
                    float d = R * Mathf.randomSeed(i * 5L, 0.1f, 0.95f);
                    Draw.alpha(a * fade);
                    Fill.square(x + Angles.trnsx(ang, d), y + Angles.trnsy(ang, d), 1.5f + Mathf.absin(t + i * 7f, 10f, 1.5f), 45f);
                }
            }
        }
    }

    // ---------- 一次性特效 ----------

    /** 锁定标记：红圈向落点收束并闪光。e.rotation = 打击半径 */
    public static final Effect mark = new Effect(45f, 800f, e -> {
        float R = Math.max(e.rotation, 20f);
        color(danger);
        stroke(e.fout() * 4f + 0.5f);
        Lines.circle(e.x, e.y, R * (1f + e.fout() * 1.5f));
        color(hot);
        stroke(e.fout() * 2f + 0.3f);
        Lines.circle(e.x, e.y, R * (0.4f + e.fin() * 0.6f));
        color(danger);
        for(int i = 0; i < 4; i++){
            float a = 45f + 90f * i;
            float d = R * (1.2f + e.fout() * 0.8f);
            Drawf.tri(e.x + Angles.trnsx(a, d), e.y + Angles.trnsy(a, d), 8f * e.fout() + 1f, 16f, a + 180f);
        }
        Drawf.light(e.x, e.y, R * 2f * e.fout(), danger, 0.8f * e.fout());
    });

    /** 光束落地冲击：致盲闪光 + 双层冲击波 + 放射光刺 + 火星。e.rotation = 打击半径 */
    public static final Effect impact = new Effect(70f, 1400f, e -> {
        float R = Math.max(e.rotation, 20f);

        e.scaled(14f, c -> {
            color(Color.white);
            Draw.alpha(c.fout());
            Fill.circle(e.x, e.y, R * 1.3f * c.fout());
        });
        e.scaled(50f, c -> {
            color(hot, danger, c.fin());
            stroke(c.fout() * 6f + 0.5f);
            Lines.circle(e.x, e.y, R * 0.3f + c.finpow() * R * 1.6f);
        });
        e.scaled(65f, c -> {
            color(danger);
            stroke(c.fout() * 3f + 0.3f);
            Lines.circle(e.x, e.y, R * 0.2f + c.finpow() * R * 2.2f);
        });

        for(int i = 0; i < 2; i++){
            color(i == 0 ? danger : hot);
            float m = i == 0 ? 1f : 0.55f;
            for(int j = 0; j < 14; j++){
                float rot = 360f / 14f * j + Mathf.randomSeedRange(e.id + j, 10f);
                float len = (R * 0.8f + Mathf.randomSeed(e.id + j * 3, 0f, R * 0.8f)) * e.finpow() * m + 8f;
                Drawf.tri(e.x, e.y, 12f * e.fout() * m, len, rot);
            }
        }

        color(hot);
        for(int i = 0; i < 36; i++){
            float a = Mathf.randomSeed(e.id + i, 0f, 360f);
            float d = (R * 0.2f + Mathf.randomSeed(e.id + i * 3, 0f, R * 1.4f)) * e.finpow();
            Fill.square(e.x + Angles.trnsx(a, d), e.y + Angles.trnsy(a, d), 3f * e.fout(), a + e.fin() * 200f);
        }
        Drawf.light(e.x, e.y, R * 4f * e.fout(), danger, e.fout());
    });
}