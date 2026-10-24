package cryon.Fx;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import mindustry.entities.Effect;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Trail;
import cryon.Features.SpaceJumpRiftRenderer;
import mindustry.gen.Unit;
import mindustry.type.UnitType.UnitEngine;
import arc.struct.Seq;
import arc.math.geom.Vec2;
import arc.util.Time;
import arc.util.Tmp;
import arc.graphics.Blending;

/** Heavy, oppressive jump effects with a global crowd budget so 40 simultaneous jumps stay readable. */
public class SpaceJumpFx{
    public static void load(){
        // Initialize effect IDs on clients and servers before any jump packets arrive.
    }

    public static final Color color = Color.valueOf("8cddff");
    private static final Color warningColor = Color.valueOf("ffad75");
    private static final float glowZ = Layer.light + 1f;

    private static final Color[] palettes = {
            Color.valueOf("7bdfff"), Color.valueOf("70ffd5"), Color.valueOf("b9a0ff"),
            Color.valueOf("91a5ff"), Color.valueOf("dbbeff")
    };

    public static Color tint(int level){ return palettes[Mathf.clamp(level, 1, 5) - 1]; }

    // ================= 全局画面预算 =================

    private static final int MAX_PILLARS = 3, MAX_BURSTS = 6, MAX_LIGHTS = 4, MAX_WELLS = 6;
    private static long lastFrame = -1;
    private static int cur, prev, pillars, bursts, lights, wells;
    /** 全局强度 0.3~1：同屏越多越低。 */
    private static float scale = 1f;

    private static void tick(){
        long f = Core.graphics.getFrameId();
        if(f == lastFrame) return;
        lastFrame = f;
        prev = cur; cur = 0;
        pillars = bursts = lights = wells = 0;
        float target = Mathf.clamp((float)Math.sqrt(4f / Math.max(prev, 4)), 0.3f, 1f);
        scale = Mathf.lerp(scale, target, 0.15f);
    }

    /** 登记一个可见的特效；不在镜头内返回 false，调用方应直接跳过绘制。 */
    private static boolean enter(float x, float y, float r){
        tick();
        if(!Core.camera.bounds(Tmp.r1).grow(r * 3f).contains(x, y)) return false;
        cur++;
        return true;
    }

    private static void light(float x, float y, float r, Color c, float a){
        if(lights >= MAX_LIGHTS) return;
        lights++;
        Drawf.light(x, y, r, c, a * scale);
    }

    // ================= 发光线条：窄淡光晕 + 亮芯 =================

    private static final float HALO_A = 0.12f, HALO_W = 2.2f;

    private static boolean halo(){ return scale > 0.6f; }

    private static void gLine(float x1, float y1, float x2, float y2, float w, float a){
        a *= scale; if(a <= 0.01f) return;
        if(halo()){ Draw.alpha(a * HALO_A); Lines.stroke(w * HALO_W); Lines.line(x1, y1, x2, y2); }
        Draw.alpha(a); Lines.stroke(w); Lines.line(x1, y1, x2, y2);
    }
    private static void gPoly(float x, float y, int sides, float r, float rot, float w, float a){
        a *= scale; if(a <= 0.01f) return;
        if(halo()){ Draw.alpha(a * HALO_A); Lines.stroke(w * HALO_W); Lines.poly(x, y, sides, r, rot); }
        Draw.alpha(a); Lines.stroke(w); Lines.poly(x, y, sides, r, rot);
    }
    private static void gCircle(float x, float y, float r, float w, float a){
        a *= scale; if(w <= 0f || a <= 0.01f) return;
        if(halo()){ Draw.alpha(a * HALO_A); Lines.stroke(w * HALO_W); Lines.circle(x, y, r); }
        Draw.alpha(a); Lines.stroke(w); Lines.circle(x, y, r);
    }
    private static void gArc(float x, float y, float r, float frac, float rot, float w, float a){
        a *= scale; if(a <= 0.01f) return;
        if(halo()){ Draw.alpha(a * HALO_A); Lines.stroke(w * HALO_W); Lines.arc(x, y, r, frac, rot); }
        Draw.alpha(a); Lines.stroke(w); Lines.arc(x, y, r, frac, rot);
    }
    private static void gEllipse(float x, float y, float r, float sy, float rot, float w, float a){
        a *= scale; if(a <= 0.01f) return;
        if(halo()){ Draw.alpha(a * HALO_A); Lines.stroke(w * HALO_W); Lines.ellipse(x, y, r, 1f, sy, rot); }
        Draw.alpha(a); Lines.stroke(w); Lines.ellipse(x, y, r, 1f, sy, rot);
    }
    private static void gSquare(float x, float y, float half, float rot, float w, float a){
        a *= scale; if(a <= 0.01f) return;
        if(halo()){ Draw.alpha(a * HALO_A); Lines.stroke(w * HALO_W); Lines.square(x, y, half, rot); }
        Draw.alpha(a); Lines.stroke(w); Lines.square(x, y, half, rot);
    }

    /** 外圈：四段弧 + 四个朝内的小锁定箭头（拥挤时省掉箭头）。 */
    private static void shellRing(float x, float y, float r, float rot, float w, float a){
        gCircle(x, y, r, w * 0.5f, a * 0.2f);
        for(int i = 0; i < 4; i++) gArc(x, y, r, 0.2f, rot + i * 90f, w * 1.2f, a);
        if(scale < 0.5f) return;
        for(int i = 0; i < 4; i++){
            float ang = rot + 45f + i * 90f;
            Draw.alpha(a * 0.7f * scale);
            Drawf.tri(x + Angles.trnsx(ang, r * 1.2f), y + Angles.trnsy(ang, r * 1.2f),
                    2.5f + w * 1f, 7f + w * 3f, ang + 180f);
        }
    }

    // ================= 压迫感部件 =================

    private static void darkWell(float x, float y, float r, float a){
        a *= scale;
        if(a <= 0.01f || wells >= MAX_WELLS) return;
        wells++;
        float z = Draw.z();
        Blending prev = Draw.getBlend();
        Draw.z(Layer.effect - 1f);
        Draw.blend(Blending.normal);
        Draw.color(Tmp.c1.set(0.01f, 0.02f, 0.06f, 1f));
        Draw.alpha(a * 0.5f); Fill.circle(x, y, r);
        Draw.alpha(a * 0.35f); Fill.circle(x, y, r * 0.7f);
        Draw.blend(prev); Draw.z(z);
    }

    private static void pillar(float x, float y, float w, float h, int tier, float a){
        a *= scale;
        if(a <= 0.01f || pillars >= MAX_PILLARS) return;
        pillars++;
        Blending prev = Draw.getBlend();
        Draw.blend(Blending.additive);
        float cb = Tmp.c1.set(tint(tier)).a(a * 0.26f).toFloatBits();
        float ct = Tmp.c2.set(tint(tier)).a(0f).toFloatBits();
        Fill.quad(x - w, y, cb, x + w, y, cb, x + w * 0.3f, y + h, ct, x - w * 0.3f, y + h, ct);
        float cb2 = Tmp.c3.set(tint(tier)).lerp(Color.white, 0.4f).a(a * 0.3f).toFloatBits();
        Fill.quad(x - w * 0.12f, y, cb2, x + w * 0.12f, y, cb2, x + w * 0.05f, y + h * 0.6f, ct, x - w * 0.05f, y + h * 0.6f, ct);
        Draw.blend(prev);
    }

    /** 冲击：超出预算的只画一圈冲击波，不画闪光、尖刺和碎片。 */
    private static void burst(float x, float y, float r, float fin, int tier, int seed, float w){
        if(fin <= 0f || fin >= 1f) return;
        boolean full = bursts < MAX_BURSTS;
        if(full) bursts++;
        float out = 1f - fin;
        Blending prev = Draw.getBlend();
        Draw.blend(Blending.additive);
        Draw.color(tint(tier));
        float e1 = Interp.pow4Out.apply(fin);

        if(!full){
            gCircle(x, y, r * (0.5f + 2f * e1), w * (1.2f * out + 0.3f), 0.4f * out);
            Draw.blend(prev);
            return;
        }

        float flashT = Mathf.clamp(fin / 0.12f);
        float flash = 1f - flashT;
        if(flash > 0f){
            Draw.color(tint(tier), Color.white, 0.2f);
            Draw.alpha(0.12f * flash * flash * scale);
            Fill.circle(x, y, r * (0.4f + 0.5f * Interp.pow3Out.apply(flashT)));
            Draw.color(tint(tier));
        }

        gCircle(x, y, r * (0.5f + 2.4f * e1), w * (1.8f * out + 0.3f), 0.55f * out);
        float f2 = Mathf.clamp((fin - 0.12f) / 0.88f);
        if(f2 > 0f) gCircle(x, y, r * (0.4f + 1.8f * Interp.pow3Out.apply(f2)), w * (1f * (1f - f2) + 0.2f), 0.3f * (1f - f2));

        int n = Math.max(4, (int)((8 + tier) * Mathf.clamp(scale * 1.4f, 0.4f, 1f)));
        for(int i = 0; i < n; i++){
            float ang = i * 360f / n + Mathf.randomSeed(seed * 31L + i, -9f, 9f);
            float rnd = Mathf.randomSeed(seed * 17L + i, 0.5f, 1.4f);
            float d0 = r * (0.6f + 1.6f * e1 * rnd);
            Draw.alpha(0.45f * out * scale);
            Drawf.tri(x + Angles.trnsx(ang, d0), y + Angles.trnsy(ang, d0),
                    (1.2f + tier * 0.4f) * out + 0.2f, r * 0.4f * rnd * out + 3f, ang);
        }
        int m = Math.max(3, (int)((6 + tier) * Mathf.clamp(scale * 1.4f, 0.4f, 1f)));
        for(int i = 0; i < m; i++){
            float ang = Mathf.randomSeed(seed * 13L + i, 0f, 360f);
            float rnd = Mathf.randomSeed(seed * 7L + i, 0.8f, 2.4f);
            float d = r * rnd * e1;
            Draw.alpha(0.5f * out * scale);
            Fill.square(x + Angles.trnsx(ang, d), y + Angles.trnsy(ang, d),
                    (0.8f + rnd * 0.8f) * out + 0.2f, ang + fin * 200f);
        }
        Draw.blend(prev);
    }

    // ================= 蓄力 =================

    public static void charge(Unit unit, float tx, float ty, float progress, int level, Trail[] trails){
        float radius = level >= 4 ? digitalHalfSize(unit) : unit.hitSize * 0.65f + 25f;
        if(enter(unit.x, unit.y, radius)) drawCharge(unit, progress, level, trails, radius);
        if(progress > 0.4f) Effect.shake((0.6f + level * 0.6f) * progress * progress, 6f, unit.x, unit.y);
    }

    private static void drawCharge(Unit unit, float progress, int level, Trail[] trails, float radius){
        float z = Draw.z();
        Blending blend = Draw.getBlend();
        try{
            if(progress > 0f) darkWell(unit.x, unit.y, radius * (2.2f - 0.6f * progress), progress);

            Draw.z(glowZ);
            Draw.blend(Blending.additive);
            if(level <= 3 && trails != null && scale > 0.5f) for(int i = 0; i < trails.length; i++){
                trails[i].draw(tint(level), unit.type.engines.get(i).radius * (0.7f + progress));
            }
            if(progress > 0f){
                if(level <= 3){
                    SpaceJumpRiftRenderer.submit(unit.x, unit.y, radius, unit.rotation,
                            progress, progress, level, 0f);
                    signature(unit.x, unit.y, radius, unit.rotation, progress, level, Time.time, false);
                }
                particles(unit.x, unit.y, radius, progress, level, Time.time, unit.id);
                converge(unit.x, unit.y, radius, progress, level, Time.time);
            }
            Draw.blend(Blending.additive);
            if(level <= 3) for(UnitEngine engine : unit.type.engines){
                float ex = unit.x + Angles.trnsx(unit.rotation - 90f, engine.x, engine.y);
                float ey = unit.y + Angles.trnsy(unit.rotation - 90f, engine.x, engine.y);
                float angle = unit.rotation - 90f + engine.rotation;
                Draw.color(tint(level)); Draw.alpha(0.5f * scale);
                Drawf.tri(ex, ey, engine.radius * (1f + progress * 0.6f),
                        engine.radius * (3f + progress * (4f + level)), angle);
                if(scale > 0.5f){
                    Draw.color(tint(level), Color.white, 0.4f); Draw.alpha(0.35f * scale);
                    Fill.circle(ex, ey, engine.radius * (0.35f + progress * 0.3f));
                }
            }
            light(unit.x, unit.y, radius * 2.2f, tint(level), progress * 0.15f);
        }finally{
            Draw.blend(blend); Draw.reset(); Draw.z(z);
        }
    }

    private static void converge(float x, float y, float r, float p, int tier, float time){
        Draw.color(tint(tier));
        float speed = 1f / (70f - p * 35f);
        int rings = scale > 0.5f ? 2 : 1;
        for(int i = 0; i < rings; i++){
            float cycle = (time * speed + i / (float)rings) % 1f;
            float rr = r * (2.4f - 1.5f * Interp.pow2In.apply(cycle));
            gCircle(x, y, rr, 1f + p * 1.2f, Mathf.sin(cycle * Mathf.pi) * (0.1f + p * 0.3f));
        }
    }

    private static void particles(float x, float y, float radius, float progress, int tier, float time, int seed){
        Draw.blend(Blending.additive);
        int count = Math.max(3, (int)((8 + tier * 4) * Mathf.clamp(scale * 1.4f, 0.3f, 1f)));
        for(int i = 0; i < count; i++){
            float cycle = (time / (60f - tier * 4f) + Mathf.randomSeed(seed * 127L + i)) % 1f;
            float angle = i * 137.5f + cycle * (tier == 3 ? 160f : 40f);
            float distance = radius * (1f - cycle) * (1.8f + progress);
            Draw.color(tint(tier));
            Draw.alpha(Mathf.sin(cycle * Mathf.pi) * progress * 0.4f * scale);
            float px = x + Angles.trnsx(angle, distance), py = y + Angles.trnsy(angle, distance);
            if(tier >= 4) Fill.square(px, py, (0.8f + tier * 0.3f) * cycle, 45f);
            else Drawf.tri(px, py, (1.2f + tier * 0.4f) * cycle, (6f + tier * 2f) * cycle, angle + 180f);
        }
    }

    // ================= 各等级轮廓 =================

    private static void signature(float x, float y, float r, float heading, float p, int tier, float time, boolean warning){
        Draw.blend(Blending.additive);
        Draw.color(tint(tier));
        float breathe = Mathf.absin(time, 34f, 1f);
        float a = warning ? 0.3f + 0.12f * breathe : 0.15f + p * 0.35f;
        float w = 1.2f + p * 1.4f;
        float spin = warning ? 0f : time * 0.4f;

        shellRing(x, y, r * (1.15f + 0.6f * (1f - p)), heading + spin, w, a * 0.85f);

        switch(tier){
            case 1:
                gPoly(x, y, 4, r * (1f - p * 0.25f), heading, w, a);
                for(int i = 0; i < 2; i++){
                    float ang = heading + 180f * i;
                    float cs = Mathf.cosDeg(ang), sn = Mathf.sinDeg(ang);
                    gLine(x + cs * r * 1.1f, y + sn * r * 1.1f,
                            x + cs * r * (1.4f + 0.4f * p), y + sn * r * (1.4f + 0.4f * p), w, a);
                }
                break;
            case 2:
                gPoly(x, y, 3, r, heading - 90f + spin * 0.6f, w, a);
                gPoly(x, y, 3, r * (0.85f - p * 0.3f), heading + 90f - spin * 0.6f, w, a * 0.8f);
                for(int i = 0; i < 3; i++){
                    float ang = heading + i * 120f;
                    Draw.alpha(a * scale);
                    Fill.circle(x + Angles.trnsx(ang, r), y + Angles.trnsy(ang, r), 1.5f + p * 1.2f);
                }
                break;
            case 3:
                for(int i = 0; i < 3; i++){
                    gEllipse(x, y, r, 0.32f + i * 0.08f, heading + i * 60f + spin, w, a * 0.9f);
                    float ang = time * (i % 2 == 0 ? 1.2f : -1.2f) + i * 120f;
                    Draw.alpha(a * scale);
                    Fill.circle(x + Angles.trnsx(ang, r), y + Angles.trnsy(ang, r), 1.6f + p * 1.2f);
                }
                break;
            case 4:
            default:
                digitalFrame(x, y, r, p, tier, warning, a, w);
                break;
        }
    }

    public static float digitalHalfSize(Unit unit){
        float extent = unit.hitSize * 0.5f;
        if(unit.type.region != null) extent = Math.max(extent,
                Math.max(unit.type.region.width, unit.type.region.height) * unit.type.region.scl() * Draw.scl * 0.5f);
        if(unit.type.fullIcon != null) extent = Math.max(extent,
                Math.max(unit.type.fullIcon.width, unit.type.fullIcon.height) * unit.type.fullIcon.scl() * Draw.scl * 0.5f);
        for(UnitEngine engine : unit.type.engines){
            extent = Math.max(extent, (float)Math.sqrt(engine.x * engine.x + engine.y * engine.y) + engine.radius);
        }
        return extent * 1.08f + 6f;
    }

    private static void digitalFrame(float x, float y, float size, float p, int tier, boolean warning, float a, float w){
        float l = size * 0.4f;
        for(int sx : Mathf.signs){
            for(int sy : Mathf.signs){
                float cx = x + sx * size, cy = y + sy * size;
                gLine(cx, cy, cx - sx * l, cy, w * 1.2f, a);
                gLine(cx, cy, cx, cy - sy * l, w * 1.2f, a);
            }
        }
        gSquare(x, y, size, 0f, w * 0.5f, a * 0.25f);
        if(warning) return;

        float scanY = y - size + size * 2f * p;
        gLine(x - size, scanY, x + size, scanY, w, a);
    }

    public static void digitalCube(float x, float y, float halfSize, float progress, int tier, boolean departure){
        if(departure) return;
        float collapse = departure ? progress : Mathf.clamp((progress - 0.38f) / 0.62f);
        if(collapse >= 1f) return;
        Blending prev = Draw.getBlend();
        Draw.blend(Blending.normal);
        Draw.color(tint(tier), Color.white, 0.48f);
        if(collapse <= 0f){
            Fill.square(x, y, halfSize);
        }else{
            // 拥挤时降低体素分辨率，减少 1600 次 Fill 的开销
            int cells = scale > 0.6f ? 40 : 20;
            float step = halfSize * 2f / cells;
            for(int ix = 0; ix < cells; ix++) for(int iy = 0; iy < cells; iy++){
                float grain = Mathf.randomSeed(ix * 157L + iy * 61L + tier * 991L);
                float alpha = Mathf.clamp((grain - collapse) / 0.06f);
                if(alpha <= 0f) continue;
                Draw.alpha(alpha);
                Fill.square(x - halfSize + (ix + 0.5f) * step,
                        y - halfSize + (iy + 0.5f) * step, step * 0.5f);
            }
        }
        Draw.blend(Blending.additive);
        Draw.color(tint(tier));
        gSquare(x, y, halfSize, 0f, 1.6f, 0.6f * (1f - collapse));
        Draw.blend(prev);
        Draw.reset();
    }

    // ================= 预警 / 预览 =================

    public static void warning(Unit unit, float x, float y, float progress, int level){
        float radius = level >= 4 ? digitalHalfSize(unit) : unit.hitSize * 0.65f + 25f;
        if(!enter(x, y, radius)) return;
        float z = Draw.z();
        Blending blend = Draw.getBlend();
        try{
            darkWell(x, y, radius * 2f, 0.2f + 0.35f * progress);
            Draw.z(glowZ);
            signature(x, y, radius, unit.rotation, progress, level, Time.time, true);
            Draw.blend(Blending.additive); Draw.color(tint(level));
            gCircle(x, y, radius * (2.2f - 1.2f * Interp.pow2In.apply(progress)), 1.4f, 0.12f + 0.25f * progress);
            float yy = y - radius * 1.9f;
            gLine(x - radius, yy, x + radius, yy, 0.7f, 0.15f);
            gLine(x - radius, yy, x - radius + radius * 2f * progress, yy, 1.4f, 0.55f);
            light(x, y, radius * 1.6f, tint(level), 0.07f);
        }finally{
            Draw.blend(blend); Draw.reset(); Draw.z(z);
        }
    }

    public static void preview(Unit unit, float x, float y, boolean ready){
        float radius = unit.hitSize * 0.65f + 18f;
        if(!enter(x, y, radius)) return;
        float z = Draw.z();
        Blending blend = Draw.getBlend();
        Draw.z(glowZ);
        Draw.blend(Blending.additive);
        Draw.color(ready ? color : warningColor);
        Lines.stroke(1f);
        Draw.alpha(0.35f * scale);
        Lines.dashLine(unit.x, unit.y, x, y, Math.max(2, Math.min(80, (int)(unit.dst(x, y) / 12f))));
        shellRing(x, y, radius, Time.time * 0.6f, 1f, 0.45f);
        gPoly(x, y, 4, radius * 0.55f, 45f, 0.9f, 0.35f);
        gLine(x - 8f, y, x + 8f, y, 0.9f, 0.45f);
        gLine(x, y - 8f, x, y + 8f, 0.9f, 0.45f);
        Draw.blend(blend); Draw.reset(); Draw.z(z);
    }

    // ================= 跃迁 =================

    private static class Wake{
        final Seq<Vec2> engines = new Seq<>();
        final Seq<Float> radii = new Seq<>();
        final Seq<Float> angles = new Seq<>();
        final Seq<Trail> curves = new Seq<>();
        float dx, dy, level;
    }

    public static void fold(Unit unit, float ox, float oy, float x, float y, float rotation, int level, Trail[] trails){
        FoldVisual visual = new FoldVisual(level >= 4 ? digitalHalfSize(unit) * 2f : unit.hitSize, level);
        if(level <= 3) departureEffects[level - 1].at(ox, oy, rotation, tint(level), visual);
        arrivalEffects[level - 1].at(x, y, rotation, tint(level), visual);
        // 震动取最大值而非叠加，这里再按强度略降，避免多发时镜头狂抖
        float shakeScale = Mathf.lerp(0.6f, 1f, scale);
        Effect.shake((2f + level * 1.5f) * shakeScale, 14f + level * 4f, ox, oy);
        Effect.shake((4f + level * 2.5f) * shakeScale, 24f + level * 5f, x, y);
        if(level >= 4) return;
        Wake wake = new Wake();
        wake.dx = x - ox; wake.dy = y - oy; wake.level = level;
        for(int i = 0; i < unit.type.engines.size; i++){
            UnitEngine engine = unit.type.engines.get(i);
            wake.engines.add(new Vec2(engine.x, engine.y).rotate(rotation - 90f).add(ox, oy));
            wake.radii.add(engine.radius);
            wake.angles.add(rotation - 90f + engine.rotation);
            if(trails != null && i < trails.length) wake.curves.add(trails[i].copy());
        }
        engineWake.at((ox + x) / 2f, (oy + y) / 2f, rotation, color, wake);
    }

    public static final Effect engineWake = new Effect(80f, 2600f, e -> {
        if(!(e.data instanceof Wake)) return;
        tick();
        Wake wake = (Wake)e.data;
        Blending previous = Draw.getBlend();
        Draw.blend(Blending.additive);
        Color wakeColor = tint((int)wake.level);
        float punch = Interp.pow3Out.apply(e.fout());
        // 尾焰是长条，叠加最容易发白，拥挤时额外压低
        float s = scale * scale;
        for(int i = 0; i < wake.engines.size; i++){
            Vec2 engine = wake.engines.get(i);
            float width = wake.radii.get(i) * (1.2f + wake.level * 0.15f) * punch;
            float x2 = engine.x + wake.dx, y2 = engine.y + wake.dy;
            if(halo()){
                Draw.color(wakeColor); Draw.alpha(0.14f * e.fout() * s);
                Lines.stroke(width * 2.2f);
                Lines.line(engine.x, engine.y, x2, y2);
            }
            Draw.color(wakeColor, Color.white, 0.5f); Draw.alpha(0.5f * e.fout() * s);
            Lines.stroke(Math.max(0.1f, width * 0.35f));
            Lines.line(engine.x, engine.y, x2, y2);
            int tris = scale > 0.5f ? 5 + (int)wake.level : 2;
            for(int j = 0; j < tris; j++){
                float t = (j / (float)tris + e.fin() * 0.6f) % 1f;
                Draw.color(wakeColor); Draw.alpha((1f - t) * e.fout() * 0.4f * s);
                Drawf.tri(engine.x + wake.dx * t, engine.y + wake.dy * t,
                        width * 1.3f, (14f + wake.level * 8f) * e.fout(), wake.angles.get(i));
            }
            if(scale > 0.5f && i < wake.curves.size) wake.curves.get(i).draw(wakeColor, width * 0.8f);
        }
        Draw.blend(previous); Draw.reset();
    }).layer(glowZ);

    public static class FoldVisual{
        public final float size;
        public final int level;
        public FoldVisual(float size, int level){ this.size = size; this.level = level; }
    }

    public static final Effect[] departureEffects = makeEffects(true);
    public static final Effect[] arrivalEffects = makeEffects(false);

    private static Effect[] makeEffects(boolean departure){
        Effect[] effects = new Effect[5];
        for(int i = 0; i < effects.length; i++){
            final int tier = i + 1;
            float life = tier >= 4
                    ? (departure ? 32f : cryon.Type.SpaceJumpAbility.reconstructionTime(tier) + 18f)
                    : (departure ? 36f : 62f);
            effects[i] = new Effect(life, 1800f, e -> {
                if(tier >= 4 && departure) return;
                FoldVisual visual = e.data instanceof FoldVisual ? (FoldVisual)e.data : new FoldVisual(30f, tier);
                float radius = tier >= 4 ? visual.size * 0.5f : visual.size * 0.65f + 25f;
                if(!enter(e.x, e.y, radius)) return;
                float z = Draw.z(); Blending blend = Draw.getBlend();
                try{
                    float aperture = Math.min(1f, e.fin() / 0.12f) * Mathf.pow(e.fout(), 0.6f);
                    float energy = e.fout();
                    float impact = e.fin();
                    if(tier >= 4){
                        float rt = cryon.Type.SpaceJumpAbility.reconstructionTime(tier);
                        aperture = departure ? e.fin() : Mathf.clamp(e.time / rt);
                        energy = departure ? e.fout() : 1f - Mathf.clamp((e.time - rt) / 18f);
                        impact = departure ? e.fin() : Mathf.clamp((e.time - rt) / 18f);
                    }
                    boolean shaded = SpaceJumpRiftRenderer.submit(e.x, e.y, radius, e.rotation,
                            aperture, energy, tier, departure ? 1f : 2f);
                    Draw.blend(Blending.additive); Draw.color(tint(tier));
                    float f = e.fout();
                    float moving = departure ? e.fout() : e.fin();

                    if(tier >= 4){
                        if(!shaded) digitalCube(e.x, e.y, radius, aperture, tier, departure);
                    }else{
                        shellRing(e.x, e.y, radius * (0.6f + 0.8f * Interp.pow3Out.apply(e.fin())),
                                e.rotation + e.time * 2f, 1.6f * f + 0.3f, 0.5f * f);
                        if(tier == 1){
                            for(int sign : Mathf.signs){
                                float ang = e.rotation + (sign < 0 ? 180f : 0f);
                                gLine(e.x, e.y, e.x + Angles.trnsx(ang, radius * 2.4f * f),
                                        e.y + Angles.trnsy(ang, radius * 2.4f * f), 1.5f, 0.55f * f);
                            }
                            gPoly(e.x, e.y, 4, radius * e.fin(), e.rotation, 1.6f * f + 0.3f, 0.55f * f);
                        }else if(tier == 2){
                            for(int j = 0; j < 3; j++){
                                float a = e.rotation + j * 120f;
                                float distance = radius * moving;
                                gPoly(e.x + Angles.trnsx(a, distance), e.y + Angles.trnsy(a, distance),
                                        3, radius * 0.4f * f, a + e.time * 2f, 1.6f * f + 0.3f, 0.55f * f);
                            }
                            gPoly(e.x, e.y, 3, radius * (0.6f + e.fin()), e.rotation, 1.6f * f + 0.3f, 0.5f * f);
                        }else{
                            int ell = scale > 0.5f ? 3 : 2;
                            for(int j = 0; j < ell; j++){
                                gEllipse(e.x, e.y, radius * (0.4f + e.fin() * 1.8f), 0.35f,
                                        e.rotation + j * 60f + e.time * 0.8f, 1.6f * f + 0.3f, 0.5f * f);
                            }
                        }
                    }

                    float pillarA = departure ? Interp.pow2Out.apply(f) : (tier >= 4 ? Mathf.clamp(1f - impact * 1.2f) * (aperture > 0.9f ? 1f : 0f) : f);
                    if(tier <= 3) pillar(e.x, e.y, radius * (0.3f + 0.25f * f), 320f + radius * 2f, tier, pillarA * 0.7f);

                    if(tier <= 3) burst(e.x, e.y, radius, impact, tier, e.id, 1.2f);

                    light(e.x, e.y, radius * 2.2f, tint(tier), e.fout() * 0.15f);
                }finally{
                    Draw.blend(blend); Draw.reset(); Draw.z(z);
                }
            }).layer(tier >= 4 ? Layer.weather + 2.1f : glowZ);
        }
        return effects;
    }
}