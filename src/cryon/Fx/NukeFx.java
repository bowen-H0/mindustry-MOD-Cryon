package cryon.Fx;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.util.Time;
import cryon.Script.NukeStrikeScript;
import mindustry.entities.Effect;
import mindustry.graphics.Drawf;

import static arc.graphics.g2d.Draw.color;
import static arc.graphics.g2d.Lines.stroke;

/**
 * 核弹打击特效。
 * hud 是一个长寿命特效，每帧读取脚本的实时状态绘制：
 * 处理器上的倒计时盘 -> 落点标记(爆炸半径/致死区) -> 导弹坠落 -> 闪光/火球/冲击波/蘑菇云。
 */
public class NukeFx {
    static final Color danger = Color.valueOf("ff3b30");
    static final Color amber = Color.valueOf("ffb300");
    static final Color hot = Color.valueOf("ff8a1f");
    static final Color glow = Color.valueOf("fff1b0");
    static final Color ash = Color.valueOf("2a1f1c");
    static final Color smoke = Color.valueOf("5a4a44");
    private static final Color tmp = new Color();

    private static void dashedCircle(float x, float y, float r, int segs, float rot){
        for(int i = 0; i < segs; i++){
            Lines.arc(x, y, r, 0.5f / segs, rot + 360f / segs * i);
        }
    }

    /** 辐射标志：三扇叶 + 中心点 */
    private static void trefoil(float x, float y, float r, float rot){
        for(int i = 0; i < 3; i++){
            Fill.arc(x, y, r, 1f / 6f, rot + 120f * i - 30f);
        }
        Fill.circle(x, y, r * 0.2f);
    }

    public static final Effect hud = new Effect(3600f, 9000f, e -> {
        if(!(e.data instanceof NukeStrikeScript)) return;
        NukeStrikeScript s = (NukeStrikeScript)e.data;
        if(!s.alive || s.hudEpoch != (int)e.rotation) return;

        int ph = s.phase;
        if(ph == NukeStrikeScript.LOCK || ph == NukeStrikeScript.FALL){
            drawTarget(s);
        }
        if(ph == NukeStrikeScript.FALL) drawMissile(s);
        if(ph == NukeStrikeScript.BLAST) drawBlast(s);
        if(s.showStation) drawStation(s, e.x, e.y);
        Draw.reset();
    });

    /** 处理器位置：倒计时盘 + 辐射标志，时间越少闪得越快 */
    private static void drawStation(NukeStrikeScript s, float cx, float cy){
        float t = Time.time;
        boolean armed = s.phase >= NukeStrikeScript.COUNT && s.phase <= NukeStrikeScript.FALL;
        float urgency = armed ? 1f - Mathf.clamp(s.remaining / Math.max(1f, s.totalTicks)) : 0f;
        float blink = Mathf.absin(t, Mathf.lerp(40f, 5f, urgency), 1f);

        tmp.set(amber).lerp(danger, urgency);
        Drawf.light(cx, cy, 90f + 40f * blink * (armed ? 1f : 0f), tmp, armed ? 0.8f : 0.3f);

        color(tmp);
        Draw.alpha(armed ? 0.7f : 0.3f);
        stroke(1.4f);
        Lines.circle(cx, cy, 40f);
        Lines.circle(cx, cy, 32f);
        for(int i = 0; i < 48; i++){
            float a = 7.5f * i + t * 0.05f;
            Lines.lineAngle(cx + Mathf.cosDeg(a) * 32f, cy + Mathf.sinDeg(a) * 32f, a + 180f, i % 4 == 0 ? 7f : 3f);
        }
        if(armed){
            Draw.alpha(1f);
            stroke(4f);
            float frac = s.phase == NukeStrikeScript.COUNT
                    ? Mathf.clamp(s.remaining / Math.max(1f, s.totalTicks))
                    : Mathf.clamp(s.remaining / Math.max(1f, s.lockLead)) * 0.02f;
            Lines.arc(cx, cy, 46f, frac, 90f);
        }

        color(tmp);
        Draw.alpha(armed ? 0.6f + 0.4f * blink : 0.45f);
        trefoil(cx, cy, 15f, t * (armed ? 0.5f + urgency * 2f : 0.2f));
    }

    /** 落点标记：爆炸半径、致死区、倒计时弧、辐射标志 */
    private static void drawTarget(NukeStrikeScript s){
        if(s.simpleWarning){
            float radius = Mathf.clamp(s.blastRadius * 0.08f, 32f, 64f);
            float remaining = s.phase == NukeStrikeScript.LOCK
                    ? s.remaining + s.fallTime : Math.max(0f, s.fallTime - s.fallTimer);
            NukeMarkerFx.draw(s.tx, s.ty, radius, remaining, s.totalTicks + s.fallTime,
                    s.strikeId + s.build.pos());
            NukeMarkerFx.drawLight(s.tx, s.ty, radius, remaining);
            return;
        }
        float t = Time.time, R = s.blastRadius, x = s.tx, y = s.ty;
        float lp = s.phase == NukeStrikeScript.LOCK ? 1f - Mathf.clamp(s.remaining / s.lockLead) : 1f;
        float blink = Mathf.absin(t, Mathf.lerp(24f, 4f, lp), 1f);
        float w = Math.max(3f, R * 0.01f);

        color(danger);
        Draw.alpha(0.05f + 0.13f * blink);
        Fill.circle(x, y, R);
        Draw.alpha(0.9f);
        stroke(w);
        Lines.circle(x, y, R);

        // 致死区（0.6R 内一切直接蒸发）
        color(hot);
        Draw.alpha(0.12f + 0.2f * blink);
        Fill.circle(x, y, R * 0.6f);
        Draw.alpha(0.9f);
        stroke(w * 0.6f);
        dashedCircle(x, y, R * 0.6f, 64, t * 0.3f);

        // 放射刻线
        stroke(Math.max(1.5f, w * 0.3f));
        Draw.alpha(0.5f);
        for(int i = 0; i < 36; i++){
            float a = 10f * i + t * 0.1f;
            Lines.lineAngle(x + Angles.trnsx(a, R * 0.62f), y + Angles.trnsy(a, R * 0.62f), a, R * 0.34f);
        }

        // 倒计时弧
        color(Color.white);
        Draw.alpha(1f);
        stroke(w * 1.4f);
        float frac = s.phase == NukeStrikeScript.LOCK ? Mathf.clamp(s.remaining / s.lockLead) : 0f;
        Lines.arc(x, y, R * 1.03f, frac, 90f);

        // 中心辐射标志
        color(amber, danger, lp);
        Draw.alpha(0.55f + 0.45f * blink);
        trefoil(x, y, Math.min(R * 0.18f, 160f), t * 0.4f);
        Drawf.light(x, y, R * 1.5f, danger, 0.25f + 0.25f * blink);
    }

    /** 导弹自高空坠落 */
    private static void drawMissile(NukeStrikeScript s){
        float fp = Mathf.clamp(s.fallTimer / s.fallTime);
        float x = s.tx, y = s.ty;
        float h = 3200f * (1f - Interp.pow2In.apply(fp));
        float my = y + h;

        // 尾焰
        color(hot);
        Draw.alpha(0.8f);
        Fill.quad(x - 14f, my + 20f, x + 14f, my + 20f, x + 4f, my + 420f, x - 4f, my + 420f);
        color(glow);
        Fill.quad(x - 7f, my + 20f, x + 7f, my + 20f, x + 2f, my + 260f, x - 2f, my + 260f);
        // 弹体（头朝下）
        color(Color.lightGray);
        Draw.alpha(1f);
        Fill.quad(x - 12f, my + 90f, x + 12f, my + 90f, x + 12f, my + 10f, x - 12f, my + 10f);
        Drawf.tri(x, my, 24f, 40f, 270f);
        color(danger);
        Fill.quad(x - 12f, my + 60f, x + 12f, my + 60f, x + 12f, my + 48f, x - 12f, my + 48f);
        Drawf.light(x, my + 30f, 260f, hot, 0.9f);

        if(!s.simpleWarning){
            // 地面：逼近的冲击预兆
            color(Color.white);
            Draw.alpha(fp * 0.6f);
            stroke(2f + fp * 4f);
            Lines.circle(x, y, 20f + (1f - fp) * 220f);
        }
    }

    /** 闪光 + 火球 + 冲击波 + 蘑菇云 + 余烬 */
    private static void drawBlast(NukeStrikeScript s){
        float t = Time.time, R = s.blastRadius, x = s.tx, y = s.ty;
        float bt = s.blastTimer;
        float wp = Mathf.clamp(bt / s.waveTime);
        float fade = Mathf.clamp(1f - (bt - 360f) / (s.blastTotal - 360f));

        // 焦土
        color(ash);
        Draw.alpha(0.35f * Mathf.clamp(bt / 60f) * fade);
        Fill.circle(x, y, R * 0.6f * Interp.pow2Out.apply(wp));

        // 火球：多层，先膨胀后缓慢收缩
        float fp = Interp.pow3Out.apply(Mathf.clamp(bt / 90f));
        float fr = R * 0.34f * fp * (1f - 0.45f * Mathf.clamp((bt - 90f) / 420f));
        color(danger);
        Draw.alpha(0.55f * fade);
        Fill.circle(x, y, fr);
        color(hot);
        Draw.alpha(0.75f * fade);
        Fill.circle(x, y, fr * 0.78f);
        color(amber);
        Draw.alpha(0.9f * fade);
        Fill.circle(x, y, fr * 0.55f);
        color(Color.white);
        Draw.alpha(fade);
        Fill.circle(x, y, fr * 0.3f);

        // 冲击波（与伤害判定同步的前沿）
        float wr = s.waveR;
        color(Color.white, hot, wp);
        Draw.alpha(Mathf.clamp(1.2f - wp));
        stroke(Math.max(3f, R * 0.035f) * (1f - wp * 0.7f));
        Lines.circle(x, y, wr);
        color(danger);
        Draw.alpha(Mathf.clamp(1.2f - wp) * 0.7f);
        stroke(Math.max(2f, R * 0.02f) * (1f - wp * 0.7f));
        Lines.circle(x, y, wr * 0.93f);
        color(smoke);
        Draw.alpha(0.18f * (1f - wp));
        stroke(R * 0.09f * (1f - wp * 0.5f));
        Lines.circle(x, y, wr * 0.9f);

        // 蘑菇云：柱 + 菌盖，向屏幕上方升起
        float rise = Interp.pow2Out.apply(Mathf.clamp(bt / 420f));
        float stemH = R * 1.0f * rise;
        float capR = R * 0.3f * (0.35f + 0.65f * Interp.pow2Out.apply(Mathf.clamp(bt / 500f)));
        float sw0 = R * 0.045f, sw1 = R * 0.085f;
        tmp.set(hot).lerp(smoke, Mathf.clamp(bt / 500f));
        color(tmp);
        Draw.alpha(0.85f * fade);
        Fill.quad(x - sw0, y, x + sw0, y, x + sw1, y + stemH, x - sw1, y + stemH);
        color(glow);
        Draw.alpha(0.5f * fade * (1f - Mathf.clamp(bt / 300f)));
        Fill.quad(x - sw0 * 0.4f, y, x + sw0 * 0.4f, y, x + sw1 * 0.4f, y + stemH, x - sw1 * 0.4f, y + stemH);
        // 菌盖
        for(int i = 0; i < 9; i++){
            float a = 40f * i + t * 0.3f;
            float ox = Mathf.cosDeg(a) * capR * 0.62f;
            float oy = Mathf.sinDeg(a) * capR * 0.28f;
            tmp.set(i % 2 == 0 ? hot : danger).lerp(smoke, Mathf.clamp(bt / 450f));
            color(tmp);
            Draw.alpha(0.85f * fade);
            Fill.circle(x + ox, y + stemH + oy, capR * 0.5f);
        }
        tmp.set(amber).lerp(smoke, Mathf.clamp(bt / 380f));
        color(tmp);
        Draw.alpha(0.9f * fade);
        Fill.circle(x, y + stemH, capR * 0.55f);
        // 基座尘环
        color(smoke);
        Draw.alpha(0.3f * fade);
        Fill.circle(x, y, R * 0.12f + R * 0.1f * rise);

        // 余烬
        color(hot);
        for(int i = 0; i < 80; i++){
            float a = Mathf.randomSeed(i * 7L, 0f, 360f);
            float d = R * (0.05f + Mathf.randomSeed(i * 13L, 0f, 0.95f)) * Interp.pow2Out.apply(wp);
            Draw.alpha(fade * (0.5f + 0.5f * Mathf.absin(t + i * 3f, 10f, 1f)));
            Fill.square(x + Angles.trnsx(a, d), y + Angles.trnsy(a, d), 3f + Mathf.randomSeed(i * 17L, 0f, 4f), a + t * 3f);
        }

        // 致盲闪光：开始瞬间铺满屏幕
        if(bt < 45f){
            color(Color.white);
            Draw.alpha((1f - bt / 45f) * 0.9f);
            Fill.circle(x, y, 4000f);
        }

        Drawf.light(x, y, R * 2.5f * fade + 200f, hot, fade);
    }
}
