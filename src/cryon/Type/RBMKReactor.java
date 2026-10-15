package cryon.Type;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.Geometry;
import arc.math.geom.Point2;
import arc.scene.ui.Slider;
import arc.scene.ui.layout.Table;
import arc.struct.IntSeq;
import arc.struct.IntSet;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.*;
import arc.util.io.*;
import cryon.Content.CryonItems;
import mindustry.content.*;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.logic.LAccess;
import mindustry.logic.Senseable;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.ConstructBlock;
import mindustry.world.blocks.heat.HeatBlock;
import mindustry.world.blocks.heat.HeatConsumer;
import mindustry.world.blocks.power.PowerGenerator;
import mindustry.world.meta.BlockGroup;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import arc.Events;
import mindustry.game.EventType;
import static mindustry.Vars.*;

/**
 * 核反应堆系统（全部 1x1）：
 *  - ReactorPart(Kind.fuel)       燃料棒
 *  - ReactorPart(Kind.moderator)  慢化剂柱
 *  - ReactorPart(Kind.reflector)  反射器
 *  - ReactorPart(Kind.control)    控制棒（贴图 name-rod：插入越深越暗）
 *  - ReactorPart(Kind.source)     中子源
 *  - StirlingGenerator            斯特林发电机
 *
 * 新增：
 *  - 增殖系数 k 统计（产生中子 / 损失中子，滑动平均）
 *  - 爆炸：普通部件过热/被摧毁 -> 3.5 格小爆炸；燃料棒 -> 至少 25 格，
 *          并按 power = (总通量/nukeFlux)*(k/nukeK) 计算核爆：半径/伤害 ∝ power²
 *  - 切伦科夫辐射（平时蓝色辉光+粒子），超临界（k>1）蓝色爆闪
 */
public class RBMKReactor {

    public enum Kind { fuel, moderator, reflector, control, source }

    /** 所有可调参数（每个方块自己持有一份） */
    public static class NeutronProps {
        public float step = 5f;
        public float moveFast = 0.8f, moveSlow = 0.6f;
        public float fastDecay = 0.005f, slowDecay = 0.005f;
        public float absorbFast = 0f, absorbSlow = 0f;
        public float maxFlux = 400f;

        // 反射器
        public boolean reflector = false;
        public float reflect = 0.92f, transmit = 0.03f;

        // 慢化剂
        public float modRate = 0.5f;

        // 控制棒（depth 0..1）
        public float ctrlBase = 0.005f, ctrlSlow = 0.9f, ctrlFast = 0.5f;

        // 燃料棒
        public Item fuelItem = CryonItems.neutronite;
        public float pSlow = 0.25f, pFast = 0.006f;
        public float nu = 2.2f;
        public float heatPerFission = 3f;
        public float burnPerFission = 0.0004f;

        // 中子源
        public float sourceRate = 3f;

        // 热量
        public float conduct = 0.2f, dissipate = 0.004f;
        public float meltHeat = 1000f;

        // ===== 爆炸 =====
        /** 普通部件爆炸：半径(格) / 伤害 */
        public float explosionRadius = 3.5f, explosionDamage = 120f;
        /** 燃料棒爆炸的保底：半径(格) / 伤害 */
        public float fuelExplosionRadius = 25f, fuelExplosionDamage = 2000f;
        /** 核爆公式参数 */
        public float nukeFlux = 3000f;
        public float nukeK = 1.05f;
        public float baseRadius = 20f;
        public float baseDamage = 999999f;
        /** 热量达到 meltHeat 的这个比例后才开始蓝色爆闪（越接近熔毁越频繁） */
        public float flashStart = 0.8f;
        /** 核爆半径上限（格），防止卡死 */
        public float maxNukeRadius = 120f;

        // 辐射
        public float radiationRange = 4f, radiationDamage = 0.08f;
        public float radiationCap = 3f;

        // ===== 原版热量桥接 =====
        public float nativeHeatRatio = 100;

        // ===== 核污染 =====
        /** 泄漏辐射施加  的持续时间(tick) */
        public float contamDuration = 60f * 4f;
        /** 沉降区半径 = 爆炸半径 * 此系数 */
        public float falloutRadiusScale = 0.6f;
        /** 沉降区存续时间(tick)，默认 3 分钟，半径会逐渐缩小到 40% */
        public float falloutLife = 60f * 60f * 3f;
        /** 沉降区内每次刷新的状态持续时间(tick) */
        public float falloutStatusTime = 60f * 5f;

        // ===== 控制棒石墨头 =====
        /** 石墨头占行程比例：深度 < 此值时石墨头位于堆芯内 */
        public float tipSpan = 0.25f;
        /** 石墨头在堆芯内时的慢化率（快中子 -> 慢中子） */
        public float tipModRate = 0.35f;
        /** 石墨头排开吸收体：吸收率最多降低的比例 */
        public float tipDisplace = 0.6f;
        /** 下插瞬间的正反应性脉冲强度（正 scram 效应） */
        public float tipSurge = 6f;
    }
    static final Color traceColor = Color.valueOf("ffd37f1a");
    static final Color cherenkovColor = Color.valueOf("4aa8ff");

    /** 切伦科夫粒子 */
    public static final Effect cherenkovFx = new Effect(40f, e -> {
        Draw.color(cherenkovColor, Color.white, e.fout() * 0.3f);
        Draw.alpha(e.fout() * 0.8f);
        Fill.circle(e.x, e.y, 0.5f + e.fout() * 0.9f);
    });

    /** 超临界蓝色爆闪 */
    public static final Effect supercritFx = new Effect(18f, 100f, e -> {
        Draw.color(Color.valueOf("9fd8ff"), Color.white, e.fout() * 0.6f);
        Draw.alpha(e.fout());
        Lines.stroke(e.fout() * 2.5f);
        Lines.circle(e.x, e.y, 2f + e.fin() * tilesize * 2.5f);
        Fill.circle(e.x, e.y, e.fout() * 4f);
        Drawf.light(e.x, e.y, tilesize * (3f + e.fin() * 4f), cherenkovColor, e.fout() * 0.8f);
    });

    /** 爆炸特效：data 传入半径（世界单位），冲击波/火球/粒子都按半径缩放 */
    public static final Effect blastFx = new Effect(70f, 2600f, e -> {
        float r = e.data instanceof Float f ? f : 40f;
        float wave = r * e.finpow();

        // 内部火球
        Draw.color(Color.valueOf("ffd37f"), Color.valueOf("ff6a2a"), e.fin());
        Draw.alpha(e.fout() * e.fout() * 0.55f);
        Fill.circle(e.x, e.y, wave * 0.85f);

        // 中心白闪
        Draw.color(Color.white, Color.valueOf("ffd37f"), e.fin());
        Draw.alpha(e.fout() * e.fout() * e.fout());
        Fill.circle(e.x, e.y, Math.max(4f, r * 0.25f * e.fout()));

        // 外圈冲击波，边缘正好扩散到伤害半径
        Draw.color(Color.white, Color.valueOf("ffb060"), e.fin());
        Draw.alpha(e.fout());
        Lines.stroke(Math.max(1.5f, r * 0.06f) * e.fout());
        Lines.circle(e.x, e.y, wave);

        // 飞溅粒子
        Draw.color(Color.valueOf("ffb060"), Color.gray, e.fin());
        Angles.randLenVectors(e.id, (int) Mathf.clamp(r / 4f, 8f, 60f), wave, (dx, dy) -> {
            Fill.circle(e.x + dx, e.y + dy, e.fout() * Math.max(1.5f, r * 0.04f));
        });

        Drawf.light(e.x, e.y, wave * 1.3f, Color.valueOf("ffb060"), e.fout() * 0.8f);
    });

    /** 堆部件接口 */
    public interface NeutronNode {
        NeutronCore neutron();
        NeutronProps props();
    }
    // ------------------------------------------------------------------
//  核污染
// ------------------------------------------------------------------
    public static class FalloutZone {
        public float x, y, radius, life, maxLife, tick;
        public NeutronProps p;
    }

    public static final Seq<FalloutZone> zones = new Seq<>();
    static boolean falloutHooked = false;

    static StatusEffect contamStatus() {
        return content.statusEffect("cryon-radiated");
    }

    /** 对范围内所有单位施加污染 DOT */
    static void applyContam(float x, float y, float range, float duration) {
        StatusEffect st = contamStatus();
        if (st == null) return;
        Units.nearby(null, x, y, range, u -> u.apply(st, duration));
    }

    static void spawnFallout(float x, float y, float radius, NeutronProps p) {
        hookFallout();
        FalloutZone z = new FalloutZone();
        z.x = x; z.y = y; z.radius = radius; z.p = p;
        z.life = z.maxLife = p.falloutLife;
        zones.add(z);
    }

    static void hookFallout() {
        if (falloutHooked) return;
        falloutHooked = true;

        Events.on(EventType.WorldLoadEvent.class, e -> zones.clear());

        Events.run(EventType.Trigger.update, () -> {
            if (state.isPaused() || zones.isEmpty()) return;
            for (int i = zones.size - 1; i >= 0; i--) {
                FalloutZone z = zones.get(i);
                z.life -= Time.delta;
                if (z.life <= 0f) { zones.remove(i); continue; }

                float f = z.life / z.maxLife;
                float r = z.radius * (0.4f + 0.6f * f);

                z.tick += Time.delta;
                if (z.tick >= 20f) {
                    z.tick = 0f;
                    applyContam(z.x, z.y, r, z.p.falloutStatusTime);
                }

                if (!headless && Mathf.chanceDelta(Math.min(0.9f, r / tilesize * 0.05f))) {
                    Tmp.v1.trns(Mathf.random(360f), r * Mathf.sqrt(Mathf.random()));
                    cherenkovFx.at(z.x + Tmp.v1.x, z.y + Tmp.v1.y);
                }
            }
        });
    }
    /** 中子 / 热量的状态与通用逻辑 */
    public static class NeutronCore {
        public float fast, slow, inFast, inSlow, heat, leak, acc;

        /** k 统计：本步产生/损失 + 滑动平均 */
        public float prodStep, lossStep, prodAvg, lossAvg;
        public boolean supercritical = false;
        public boolean exploded = false;

        /** 上一次核爆的位置/半径/时间，用来抑制连锁重复爆炸 */
        static float nukeX, nukeY, nukeR, nukeTime = -99999f;

        /** ===== 调试 ===== */
        public static boolean debug = false;
        public static int debugEvery = 5;
        public static int debugFuelLog = 20;
        public int stepCount = 0;
        public String tag = "?";

        /** 危险度 0..1：热量超过 flashStart*meltHeat 才大于 0，到 meltHeat 为 1 */
        public float danger(NeutronProps p) {
            float start = p.flashStart;
            return Mathf.clamp((heat / p.meltHeat - start) / Math.max(0.01f, 1f - start));
        }

        public float kLocal() {
            return lossAvg > 0.0001f ? prodAvg / lossAvg : 0f;
        }

        public boolean begin(Building b, NeutronProps p) {
            acc += b.delta();
            if (acc < p.step) return false;
            acc -= p.step;

            fast += inFast;
            slow += inSlow;
            inFast = inSlow = 0f;
            lossStep += fast * p.absorbFast + slow * p.absorbSlow;
            fast *= 1f - p.absorbFast;
            slow *= 1f - p.absorbSlow;

            stepCount++;

            if (debug && stepCount % debugEvery == 0) {
                Log.info("[RBMK][BEGIN] @ @(@,@) step=@ fast=@ slow=@ inF=@ inS=@ heat=@ leak=@",
                        tag, b.block.name, b.tileX(), b.tileY(), stepCount,
                        Strings.fixed(fast, 3), Strings.fixed(slow, 3),
                        Strings.fixed(inFast, 3), Strings.fixed(inSlow, 3),
                        Strings.fixed(heat, 2), Strings.fixed(leak, 2));
            }
            return true;
        }

        /** 每 tick 调用：切伦科夫粒子 + 超临界爆闪 */
        public void visuals(Building b, NeutronProps p) {
            if (headless) return;
            float f = Mathf.clamp((fast + slow) / (p.maxFlux * 0.25f));
            if (f > 0.02f && Mathf.chanceDelta(0.04f + f * 0.25f)) {
                cherenkovFx.at(b.x + Mathf.range(3.5f), b.y + Mathf.range(3.5f));
            }
            float dg = danger(p);
            if (dg > 0f && Mathf.chanceDelta(0.04f + dg * 0.4f)) {
                supercritFx.at(b.x, b.y);
            }
        }

        /** draw() 里调用：蓝色辉光 */
        public void drawGlow(Building b, NeutronProps p) {
            float f = Mathf.clamp((fast + slow) / (p.maxFlux * 0.25f));
            if (f > 0.02f) {
                float pulse = 0.8f + 0.2f * Mathf.absin(Time.time, 6f, 1f);
                float a = f * 0.35f * pulse;
                float dg = danger(p);
                if (dg > 0f) a = Math.min(1f, a + 0.4f * dg * Mathf.absin(Time.time, 2.5f, 1f));
                Draw.z(Layer.blockOver + 0.1f);
                Draw.blend(Blending.additive);
                Draw.color(cherenkovColor, a);
                Fill.rect(b.x, b.y, b.block.size * tilesize, b.block.size * tilesize);
                Draw.blend();
                Draw.reset();
                Drawf.light(b.x, b.y, tilesize * (1.5f + f * 2f), cherenkovColor, 0.25f * f);
            }
        }

        private void finishTally() {
            prodAvg = Mathf.lerp(prodAvg, prodStep, 0.1f);
            lossAvg = Mathf.lerp(lossAvg, lossStep, 0.1f);
            prodStep = lossStep = 0f;
        }

        public void end(Building b, NeutronProps p) {
            float fd = fast * p.fastDecay, sd = slow * p.slowDecay;
            fast -= fd;
            slow -= sd;
            lossStep += fd + sd;
            if (fast > p.maxFlux) { lossStep += fast - p.maxFlux; fast = p.maxFlux; }
            if (slow > p.maxFlux) { lossStep += slow - p.maxFlux; slow = p.maxFlux; }

            // 中子扩散
            float of = fast * p.moveFast, os = slow * p.moveSlow;
            fast -= of;
            slow -= os;
            float sf = of / 4f, ss = os / 4f;

            int sentF = 0, sentS = 0, leaked = 0, reflected = 0;
            for (int i = 0; i < 4; i++) {
                Building o = b.nearby(Geometry.d4x(i), Geometry.d4y(i));
                if (o instanceof NeutronNode) {
                    NeutronNode n = (NeutronNode) o;
                    NeutronProps q = n.props();
                    if (q.reflector) {
                        inFast += sf * q.reflect;
                        inSlow += ss * q.reflect;
                        n.neutron().leak += (sf + ss) * q.transmit;
                        lossStep += (sf + ss) * Math.max(0f, 1f - q.reflect - q.transmit);
                    } else {
                        n.neutron().inFast += sf;
                        n.neutron().inSlow += ss;
                    }
                } else if (o == null) {
                    leak += sf + ss;
                } else {
                    inFast += sf * 0.3f;
                    inSlow += ss * 0.3f;
                    lossStep += (sf + ss) * 0.7f;
                }
            }

            // 热传导
            float heatBefore = heat;
            for (int i = 0; i < 4; i++) {
                Building o = b.nearby(Geometry.d4x(i), Geometry.d4y(i));
                if (o instanceof NeutronNode) {
                    NeutronCore c = ((NeutronNode) o).neutron();
                    if (heat > c.heat) {
                        float t = (heat - c.heat) * p.conduct * 0.25f;
                        heat -= t;
                        c.heat += t;
                    }
                }
            }
            float heatConducted = heatBefore - heat;

            heat = Math.max(0f, heat - heat * p.dissipate);

            if (debug && stepCount % debugEvery == 0) {
                Log.info("[RBMK][END]   @ @(@,@) step=@ fast=@ slow=@ heat=@ (out=@) conductOut=@ sentF=@ sentS=@ leakN=@ reflectN=@",
                        tag, b.block.name, b.tileX(), b.tileY(), stepCount,
                        Strings.fixed(fast, 3), Strings.fixed(slow, 3),
                        Strings.fixed(heat, 2), Strings.fixed(heatConducted, 3),
                        Strings.fixed(heatConducted, 3), sentF, sentS, leaked, reflected);
            }

            // 过热 -> 自毁（爆炸在 onDestroyed 里统一处理）
            if (heat > p.meltHeat) {
                Log.warn("[RBMK] @(@,@) MELTDOWN heat=@ > @", b.block.name, b.tileX(), b.tileY(), heat, p.meltHeat);
                finishTally();
                b.kill();
                return;
            }

            // 泄漏辐射
            if (leak > 0.5f) {
                final float dmg = Math.min(leak * p.radiationDamage, p.radiationCap);
                final float range = tilesize * p.radiationRange;
                Units.nearby(null, b.x, b.y, range, u -> u.damage(dmg));
                applyContam(b.x, b.y, range, p.contamDuration);   // 新增

            }
            lossStep += leak;
            leak = 0f;
            heat = Math.max(0f, heat - heat * p.dissipate);

            finishTally();
        }

        /** 统计与 b 相连的整个堆：{总通量, k} */
        private static float[] reactorStats(Building b) {
            Seq<Building> q = new Seq<>();
            ObjectSet<Building> seen = new ObjectSet<>();
            q.add(b);
            seen.add(b);
            float flux = 0f, prod = 0f, loss = 0f;
            int i = 0;
            while (i < q.size && i < 4096) {
                Building c = q.get(i++);
                NeutronCore n = ((NeutronNode) c).neutron();
                flux += n.fast + n.slow;
                prod += n.prodAvg;
                loss += n.lossAvg;
                for (Building o : c.proximity) {
                    if (o instanceof NeutronNode && seen.add(o)) q.add(o);
                }
            }
            return new float[]{flux, loss > 1e-4f ? prod / loss : 0f};
        }

        /** 爆炸入口：过热 / 被摧毁都走这里 */
        public void explode(Building b, NeutronProps p, boolean fuel) {
            if (exploded) return;
            exploded = true;

            // 刚被核爆波及的部件不再重复爆炸
            if (Time.time - nukeTime < 180f && Mathf.dst(b.x, b.y, nukeX, nukeY) < nukeR) return;

            float radiusTiles = p.explosionRadius;
            float damage = p.explosionDamage;
            boolean big = false;

            if (fuel) {
                float[] s = reactorStats(b);
                float totalFlux = s[0], k = s[1];

                float fluxFactor = totalFlux / p.nukeFlux;
                float kFactor = k / p.nukeK;
                float power = fluxFactor * kFactor;

                float nukeRadius = p.baseRadius * power * power;
                float nukeDamage = p.baseDamage * power * power;

                radiusTiles = Math.min(p.maxNukeRadius, Math.max(p.fuelExplosionRadius, nukeRadius));
                damage = Math.max(p.fuelExplosionDamage, nukeDamage);
                big = true;

                nukeX = b.x;
                nukeY = b.y;
                nukeR = radiusTiles * tilesize;
                nukeTime = Time.time;

                Log.info("[RBMK] NUKE flux=@ k=@ power=@ radius=@ dmg=@",
                        Strings.fixed(totalFlux, 1), Strings.fixed(k, 3), Strings.fixed(power, 3),
                        Strings.fixed(radiusTiles, 1), Strings.fixed(damage, 0));
            }

            float radius = radiusTiles * tilesize;
            // 核爆无视墙体（complete），小爆炸按原版衰减
            Damage.damage(null, b.x, b.y, radius, damage, big);
            if (big) spawnFallout(b.x, b.y, radius * p.falloutRadiusScale, p);
            applyContam(b.x, b.y, radius, p.falloutStatusTime);                   // 爆炸瞬间也直接污染
            blastFx.at(b.x, b.y, 0f, (Float) radius);
            Sounds.explosion.at(b.x, b.y);
            Effect.shake(Math.min(40f, 3f + radiusTiles * 0.8f), big ? 60f : 15f, b.x, b.y);
        }

        public void write(Writes w) {
            w.f(fast); w.f(slow); w.f(inFast); w.f(inSlow); w.f(heat);
        }

        public void read(Reads r) {
            fast = r.f(); slow = r.f(); inFast = r.f(); inSlow = r.f(); heat = r.f();
        }
    }

    static void addBars(Block b, NeutronProps p) {
        b.addBar("heat", (Building e) -> new Bar("热量", Pal.lightOrange,
                () -> Mathf.clamp(((NeutronNode) e).neutron().heat / p.meltHeat)));
        b.addBar("flux", (Building e) -> new Bar("中子通量", Pal.accent,
                () -> Mathf.clamp((((NeutronNode) e).neutron().fast + ((NeutronNode) e).neutron().slow) / (p.maxFlux * 0.25f))));
    }

    // ------------------------------------------------------------------
    //  燃料棒 / 慢化剂 / 反射器 / 控制棒 / 中子源
    // ------------------------------------------------------------------
    public static class ReactorPart extends Block {
        public final Kind kind;
        public NeutronProps np = new NeutronProps();
        /** 控制棒贴图：name-rod */
        public TextureRegion rodRegion;
        public boolean hasRod = false;
        public float lastDepth = -1f;
        public ReactorPart(String name, Kind kind) {
            super(name);
            this.kind = kind;
            update = true;
            solid = true;
            destructible = true;
            buildType = ReactorPartBuild::new;

            switch (kind) {
                case fuel:
                    hasItems = true;
                    itemCapacity = 10;
                    break;
                case moderator:
                    np.absorbSlow = 0.005f;
                    break;
                case reflector:
                    np.reflector = true;
                    break;
                case control:
                    configurable = true;
                    logicConfigurable = true;
                    saveConfig = true;
                    config(Integer.class, (ReactorPartBuild b, Integer v) -> {
                        if (v != null) b.depth = Mathf.clamp(v, 0, 100);
                    });
                    break;
                default:
                    break;
            }
        }

        @Override
        public void load() {
            super.load();
            if (kind == Kind.control) {
                hasRod = Core.atlas.has(name + "-rod");
                rodRegion = Core.atlas.find(name + "-rod");
            }
        }

        @Override
        public void setBars() {
            super.setBars();
            addBars(this, np);
        }

        public class ReactorPartBuild extends Building implements NeutronNode, HeatBlock {
            public NeutronCore core = new NeutronCore();
            public int depth = 100;
            public float fuelLeft = 0f;
            /** 仅用于绘制的平滑深度 */
            public float smoothDepth = 100f;

            @Override public NeutronCore neutron() { return core; }
            @Override public NeutronProps props() { return np; }

            @Override
            public float heat() {
                int consumers = 0;
                for (int i = 0; i < 4; i++) {
                    Building o = nearby(i);
                    if (o != null && o instanceof HeatConsumer) consumers++;
                }
                return core.heat / np.nativeHeatRatio / Math.max(1, consumers);
            }
            @Override public float heatFrac() { return Mathf.clamp(core.heat / np.meltHeat); }
            @Override public float warmup()   { return heatFrac(); }

            @Override
            public boolean acceptItem(Building source, Item item) {
                return kind == Kind.fuel && item == np.fuelItem && items.get(item) < itemCapacity;
            }
            @Override
            public void control(LAccess type, double p1, double p2, double p3, double p4) {
                if (kind == Kind.control && type == LAccess.config) {
                    // control config rod 50  -> 深度 50%
                    configure(Mathf.clamp((int) p1, 0, 100));
                    return;
                }
                super.control(type, p1, p2, p3, p4);
            }

            @Override
            public double sense(LAccess sensor) {
                if (sensor == LAccess.heat) return core.heat;
                if (kind == Kind.control) {
                    if (sensor == LAccess.progress) return depth / 100.0;  // 0..1
                    if (sensor == LAccess.config) return depth;            // 0..100
                }
                return super.sense(sensor);
            }

            @Override
            public Object senseObject(LAccess sensor) {
                // 交给 sense() 返回数字，而不是对象
                if (kind == Kind.control && sensor == LAccess.config) return Senseable.noSensed;
                return super.senseObject(sensor);
            }
            /** 被摧毁（含过热自毁）都会爆炸；燃料棒走核爆 */
            @Override
            public void onDestroyed() {
                super.onDestroyed();
                core.explode(this, np, kind == Kind.fuel);
            }

            @Override
            public void updateTile() {
                core.visuals(this, np);
                if (!core.begin(this, np)) return;

                core.tag = kind.name() + "@" + tileX() + "," + tileY();

                switch (kind) {
                    case moderator: {
                        float before = core.fast;
                        float c = core.fast * np.modRate;
                        core.fast -= c;
                        core.slow += c;
                        if (NeutronCore.debug && core.stepCount % NeutronCore.debugEvery == 0) {
                            Log.info("[RBMK][MOD]  @ fast @ -> @, slow += @", core.tag,
                                    Strings.fixed(before, 3), Strings.fixed(core.fast, 3), Strings.fixed(c, 3));
                        }
                        break;
                    }
                    case control: {
                        float d = depth / 100f;
                        if (lastDepth < 0f) lastDepth = d;

                        // 石墨头在堆芯内的程度：完全拔出=1，超过 tipSpan=0
                        float g = Mathf.clamp(1f - d / Math.max(0.01f, np.tipSpan));

                        float absorbS = Mathf.clamp(np.ctrlBase + d * np.ctrlSlow);
                        float absorbF = Mathf.clamp(np.ctrlBase + d * np.ctrlFast);

                        if (g > 0f) {
                            // 1) 石墨头排开吸收体 -> 吸收减少
                            absorbS *= 1f - np.tipDisplace * g;
                            absorbF *= 1f - np.tipDisplace * g;

                            // 2) 石墨头充当慢化剂：快中子 -> 慢中子（中子总数守恒，但裂变截面更大）
                            float c = core.fast * np.tipModRate * g;
                            core.fast -= c;
                            core.slow += c;

                            // 3) 下插瞬间的正反应性脉冲（正 scram 效应）
                            float moved = d - lastDepth;
                            if (moved > 0f) {
                                float surge = (core.fast + core.slow) * np.tipSurge * moved * g;
                                core.slow += surge;
                                core.prodStep += surge;   // 计入 k 统计，会让 k 短暂升高
                                if (NeutronCore.debug && core.stepCount % NeutronCore.debugEvery == 0) {
                                    Log.info("[RBMK][TIP]  @ graphite surge=@ (moved=@ g=@)", core.tag,
                                            Strings.fixed(surge, 3), Strings.fixed(moved, 4), Strings.fixed(g, 2));
                                }
                            }
                        }
                        lastDepth = d;

                        float slowBefore = core.slow, fastBefore = core.fast;
                        core.slow *= 1f - absorbS;
                        core.fast *= 1f - absorbF;
                        core.lossStep += (slowBefore - core.slow) + (fastBefore - core.fast);
                        break;
                    }
                    case source:
                        if (enabled) {
                            core.fast += np.sourceRate;
                            if (NeutronCore.debug && core.stepCount % NeutronCore.debugEvery == 0) {
                                Log.info("[RBMK][SRC]  @ emit fast += @ (now @)", core.tag, np.sourceRate, Strings.fixed(core.fast, 3));
                            }
                        } else if (NeutronCore.debug && core.stepCount % NeutronCore.debugEvery == 0) {
                            Log.info("[RBMK][SRC]  @ DISABLED", core.tag);
                        }
                        break;
                    case fuel: {
                        if (fuelLeft <= 0f && items.get(np.fuelItem) > 0) {
                            items.remove(np.fuelItem, 1);
                            fuelLeft = 1f;
                        }
                        if (fuelLeft > 0f && enabled) {
                            float fs = core.slow * np.pSlow, ff = core.fast * np.pFast, f = fs + ff;
                            core.slow -= fs;
                            core.fast -= ff;
                            core.fast += f * np.nu;
                            core.heat += f * np.heatPerFission;
                            fuelLeft = Math.max(0f, fuelLeft - f * np.burnPerFission);

                            // k 统计：裂变消耗 f 个中子，产生 f*nu 个
                            core.lossStep += f;
                            core.prodStep += f * np.nu;

                            if (NeutronCore.debug && (f > 0.0001f || core.stepCount % NeutronCore.debugFuelLog == 0)) {
                                Log.info("[RBMK][FUEL] @ fission f=@ (from slow=@ fast=@) -> fast+=@ heat+=@ fuelLeft=@",
                                        core.tag, Strings.fixed(f, 4),
                                        Strings.fixed(fs, 4), Strings.fixed(ff, 4),
                                        Strings.fixed(f * np.nu, 3), Strings.fixed(f * np.heatPerFission, 3),
                                        Strings.fixed(fuelLeft, 3));
                            }
                        } else if (NeutronCore.debug && core.stepCount % NeutronCore.debugFuelLog == 0) {
                            Log.info("[RBMK][FUEL] @ idle fuelLeft=@ enabled=@", core.tag, fuelLeft, enabled);
                        }
                        break;
                    }
                    default:
                        break;
                }

                core.end(this, np);
            }

            @Override
            public void buildConfiguration(Table table) {
                if (kind != Kind.control) return;
                table.table(Styles.black6, t -> {
                    t.defaults().pad(4f);
                    t.label(() -> "控制棒插入深度: " + depth + "%").row();
                    Slider s = new Slider(0f, 100f, 1f, false);
                    s.setValue(depth);
                    s.moved(v -> configure((int) v));
                    s.update(() -> { if (!s.isDragging()) s.setValue(depth); });
                    t.add(s).width(200f).row();
                    t.table(r -> {
                        r.defaults().size(60f, 30f).pad(2f);
                        r.button("0", () -> configure(0));
                        r.button("1", () -> configure(50));
                        r.button("2", () -> configure(100));
                    });
                });
            }

            @Override
            public Object config() {
                return kind == Kind.control ? (Object) depth : null;
            }



            @Override
            public void draw() {
                super.draw();

                // 控制棒：插入越深越暗，拔出越亮
                if (kind == Kind.control) {
                    smoothDepth = Mathf.lerpDelta(smoothDepth, depth, 0.15f);
                    float br = Mathf.lerp(1f, 0.2f, smoothDepth / 100f);
                    if (hasRod) {
                        Draw.color(br, br, br, 1f);
                        Draw.rect(rodRegion, x, y);
                        Draw.reset();
                    } else {
                        // 没有 -rod 贴图时，用黑色遮罩代替
                        Draw.color(Color.black, (1f - br) * 0.7f);
                        Fill.rect(x, y, size * tilesize, size * tilesize);
                        Draw.reset();
                    }
                }

                float a = Mathf.clamp(core.heat / np.meltHeat) * 0.6f;
                if (a > 0.01f) {
                    Draw.z(Layer.blockOver);
                    Draw.color(Pal.lightOrange, a);
                    Fill.rect(x, y, size * tilesize, size * tilesize);
                    Draw.reset();
                }

                // 切伦科夫辉光 / 超临界闪烁
                core.drawGlow(this, np);
            }

            @Override
            public void write(Writes write) {
                super.write(write);
                core.write(write);
                write.i(depth);
                write.f(fuelLeft);
            }

            @Override
            public void read(Reads read, byte revision) {
                super.read(read, revision);
                core.read(read);
                depth = read.i();
                fuelLeft = read.f();
                smoothDepth = depth;
                lastDepth = depth / 100f;
            }
        }
    }

    // ------------------------------------------------------------------
    //  斯特林发电机
    // ------------------------------------------------------------------
    public static class StirlingGenerator extends PowerGenerator {
        public NeutronProps np = new NeutronProps();
        public float minHeat = 80f, ratedHeat = 500f;
        public float heatDrain = 6f;

        public StirlingGenerator(String name) {
            super(name);
            update = true;
            solid = true;
            np.absorbSlow = 0.01f;
            np.absorbFast = 0.005f;
            buildType = StirlingBuild::new;
        }

        @Override
        public void setBars() {
            super.setBars();
            addBars(this, np);
        }

        public class StirlingBuild extends GeneratorBuild implements NeutronNode {
            public NeutronCore core = new NeutronCore();

            @Override public NeutronCore neutron() { return core; }
            @Override public NeutronProps props() { return np; }

            @Override
            public void onDestroyed() {
                super.onDestroyed();
                core.explode(this, np, false);
            }

            @Override
            public void updateTile() {
                core.visuals(this, np);
                if (!core.begin(this, np)) return;

                core.tag = "gen@" + tileX() + "," + tileY();

                float eff = Mathf.clamp((core.heat - minHeat) / (ratedHeat - minHeat));
                float effBefore = productionEfficiency;
                productionEfficiency = enabled ? eff : 0f;

                float drained = heatDrain * productionEfficiency;
                core.heat = Math.max(0f, core.heat - drained);

                if (NeutronCore.debug && core.stepCount % NeutronCore.debugEvery == 0) {
                    Log.info("[RBMK][GEN]  @ heat=@ min=@ rated=@ eff=@ (was @) enabled=@ drain=@ power=@",
                            core.tag, Strings.fixed(core.heat, 2), minHeat, ratedHeat,
                            Strings.fixed(eff, 3), Strings.fixed(effBefore, 3), enabled,
                            Strings.fixed(drained, 3), Strings.fixed(productionEfficiency, 3));
                }

                core.end(this, np);
            }

            @Override
            public void draw() {
                super.draw();
                core.drawGlow(this, np);
            }

            @Override
            public double sense(LAccess sensor) {
                if (sensor == LAccess.heat) return core.heat;
                return super.sense(sensor);
            }

            @Override
            public void write(Writes write) {
                super.write(write);
                core.write(write);
            }

            @Override
            public void read(Reads read, byte revision) {
                super.read(read, revision);
                core.read(read);
            }
        }
    }

    // ------------------------------------------------------------------
    //  反应堆输入端口（建在堆外，远距离向燃料棒送料）
    // ------------------------------------------------------------------
    public static class ReactorFeeder extends Block {
        public int range = 12;
        public float transportTime = 5f;

        public ReactorFeeder(String name) {
            super(name);
            update = true;
            solid = true;
            hasItems = true;
            itemCapacity = 30;
            configurable = true;
            saveConfig = true;
            unloadable = false;
            group = BlockGroup.transportation;
            allowConfigInventory = false;

            // 点击链接：传入绝对坐标
            config(Integer.class, (ReactorFeederBuild b, Integer v) -> {
                b.link = (v == null || v < 0) ? -1 : v;
            });
            // 蓝图/复制粘贴：传入相对偏移
            config(Point2.class, (ReactorFeederBuild b, Point2 p) -> {
                b.link = Point2.pack(b.tileX() + p.x, b.tileY() + p.y);
            });
        }

        @Override
        public void setStats() {
            super.setStats();
            stats.add(Stat.range, range, StatUnit.blocks);
        }

        public boolean linkValid(Tile tile, Tile other) {
            if (tile == null || other == null || tile == other) return false;
            int dx = Math.abs(tile.x - other.x), dy = Math.abs(tile.y - other.y);
            if (dx > range || dy > range) return false;
            return other.block() instanceof ReactorPart rp && rp.kind == Kind.fuel;
        }

        public class ReactorFeederBuild extends Building {
            public int link = -1;
            public float transportCounter;

            /** 建造时自动连接范围内最近的、还没被其他输入端口连接的燃料棒 */
            @Override
            public void playerPlaced(Object config) {
                super.playerPlaced(config);
                if (link == -1 && config == null) autoLink();
            }

            public void autoLink() {
                int cx = tileX(), cy = tileY();

                // 已被其他输入端口占用的燃料棒
                IntSet used = new IntSet();
                for (int dx = -range; dx <= range; dx++) {
                    for (int dy = -range; dy <= range; dy++) {
                        Building b = world.build(cx + dx, cy + dy);
                        if (b instanceof ReactorFeederBuild f && f != this && f.link != -1) used.add(f.link);
                    }
                }

                Building best = null;
                float bestDst = Float.MAX_VALUE;
                for (int dx = -range; dx <= range; dx++) {
                    for (int dy = -range; dy <= range; dy++) {
                        Building b = world.build(cx + dx, cy + dy);
                        if (b == null || used.contains(b.pos())) continue;
                        if (!linkValid(tile, b.tile)) continue;
                        float d = Mathf.dst2(x, y, b.x, b.y);
                        if (d < bestDst) {
                            bestDst = d;
                            best = b;
                        }
                    }
                }

                if (best != null) link = best.pos();
            }

            private Building target() {
                if (link == -1) return null;
                Tile t = world.tile(link);
                if (!linkValid(tile, t)) return null;
                return t.build;
            }
            /** 当前链接的相对偏移，无链接返回 null */
            public Point2 relLink() {
                return link == -1 ? null : Point2.unpack(link).sub(tileX(), tileY());
            }

            @Override
            public Object config() {
                return relLink();   // 蓝图保存的是相对坐标；null 时粘贴后会自动 autoLink
            }
            @Override
            public boolean acceptItem(Building source, Item item) {
                if (items.total() >= itemCapacity) return false;
                Building target = target();
                if (target == null) return true;
                return target.acceptItem(this, item);
            }

            @Override
            public void handleItem(Building source, Item item) {
                items.add(item, 1);
            }

            @Override
            public void updateTile() {
                Building target = target();

                if (target == null && link != -1) {
                    Tile t = world.tile(link);
                    boolean building = t != null && t.build instanceof ConstructBlock.ConstructBuild;
                    if (!building) link = -1;
                    return;
                }

                transportCounter += edelta();
                while (transportCounter >= transportTime) {
                    Item item = items.take();
                    if (item == null) break;

                    if (target == null) {
                        items.add(item, 1);
                        items.undoFlow(item);
                        break;
                    }

                    if (target.acceptItem(this, item)) {
                        target.handleItem(this, item);
                    } else {
                        items.add(item, 1);
                        items.undoFlow(item);
                        break;
                    }
                    transportCounter -= transportTime;
                }
                if (transportCounter > transportTime) transportCounter = transportTime;
            }

            @Override
            public void buildConfiguration(Table table) {
                table.table(Styles.black6, t -> {
                    t.defaults().pad(4f);
                    t.label(() -> link == -1 ? "未链接（点击方块再点燃料棒）"
                            : "已链接 → " + Point2.x(link) + "," + Point2.y(link)).row();
                    t.button("清除链接", () -> configure(-1)).size(120f, 30f).disabled(b -> link == -1);
                });
            }

            @Override
            public boolean onConfigureBuildTapped(Building other) {
                if (linkValid(tile, other.tile)) {
                    if (link == other.pos()) {
                        configure(-1);
                    } else {
                        configure(other.pos());
                    }
                    return false;
                }
                if (other == this) {
                    configure(-1);
                    return false;
                }
                return true;
            }


            @Override
            public void drawConfigure() {
                Drawf.select(x, y, block.size * tilesize / 2f + 2f, Pal.accent);
                if (link != -1) {
                    Tile other = world.tile(link);
                    if (other != null && other.build != null) {
                        Drawf.select(other.build.x, other.build.y,
                                other.block().size * tilesize / 2f + 2f, Pal.place);
                    }
                }
            }

            @Override
            public void draw() {
                super.draw();
                if (link == -1) return;
                Tile other = world.tile(link);
                if (other == null || other.build == null) return;

                float tx = other.build.x, ty = other.build.y;
                float mx = tx, my = y;

                Draw.z(Layer.power);

                Draw.alpha(0.20f);
                Lines.stroke(1.6f);
                Lines.line(x, y, mx, my);
                Lines.line(mx, my, tx, ty);

                Draw.alpha(0.45f);
                Fill.circle(mx, my, 2.4f);

                Draw.alpha(0.15f);
                float step = tilesize * 3f;
                float l1 = Math.abs(mx - x), l2 = Math.abs(ty - my);
                for (float d = step; d < l1 - 2f; d += step) {
                    Fill.rect(x + Math.signum(mx - x) * d, y, 2f, 5.5f);
                }
                for (float d = step; d < l2 - 2f; d += step) {
                    Fill.rect(mx, my + Math.signum(ty - my) * d, 5.5f, 2f);
                }

                Draw.alpha(0.30f);
                Lines.stroke(3f);
                Lines.circle(tx, ty, other.block().size * tilesize / 2f + 1f);

                Draw.alpha(1f);
                Draw.reset();
            }

            @Override
            public byte version() {
                return 1;   // 0 = 旧版绝对坐标, 1 = 相对坐标
            }

            @Override
            public void write(Writes write) {
                super.write(write);
                write.bool(link != -1);
                if (link != -1) {
                    write.s(Point2.x(link) - tileX());
                    write.s(Point2.y(link) - tileY());
                }
                write.f(transportCounter);
            }

            @Override
            public void read(Reads read, byte revision) {
                super.read(read, revision);
                if (revision == 0) {
                    // 旧格式：int 绝对坐标（pos），-1 表示无链接
                    link = read.i();
                } else {
                    link = read.bool() ? Point2.pack(tileX() + read.s(), tileY() + read.s()) : -1;
                }
                transportCounter = read.f();
            }
        }
    }

    // ------------------------------------------------------------------
    //  智能核反应堆管理器（自动调节控制棒防止融毁）
    // ------------------------------------------------------------------
    public static class ReactorController extends Block {
        public int linkRange = 20;
        public int sampleRange = 8;
        public float coldHeat = 200f;
        public float hotHeat = 800f;
        public float depthSpeed = 30f;
        public int maxLinks = 16;

        public ReactorController(String name) {
            super(name);
            update = true;
            solid = true;
            configurable = true;
            saveConfig = false;
            buildType = ReactorControllerBuild::new;
            saveConfig = true;

            config(Integer.class, (ReactorControllerBuild b, Integer v) -> {
                if (v == null || v < 0) {
                    b.links.clear();
                } else if (b.links.contains(v)) {
                    b.links.removeValue(v);
                } else if (b.links.size < maxLinks) {
                    b.links.add(v);
                }
            });
            config(Point2[].class, (ReactorControllerBuild b, Point2[] arr) -> {
                b.links.clear();
                for (Point2 p : arr) {
                    if (b.links.size >= maxLinks) break;
                    b.links.add(Point2.pack(b.tileX() + p.x, b.tileY() + p.y));
                }
            });
        }

        @Override
        public void setStats() {
            super.setStats();
            stats.add(Stat.range, linkRange, StatUnit.blocks);
        }

        public boolean linkValid(Tile tile, Tile other) {
            if (tile == null || other == null || tile == other) return false;
            int dx = Math.abs(tile.x - other.x), dy = Math.abs(tile.y - other.y);
            if (dx > linkRange || dy > linkRange) return false;
            return other.block() instanceof ReactorPart rp && rp.kind == Kind.control;
        }

        public class ReactorControllerBuild extends Building {
            public IntSeq links = new IntSeq();
            public float currentDepth = 0f;
            public float sampledHeat = 0f;

            @Override
            public void updateTile() {
                for (int i = links.size - 1; i >= 0; i--) {
                    Tile t = world.tile(links.items[i]);
                    if (t != null && t.build instanceof ConstructBlock.ConstructBuild) continue;
                    if (!linkValid(tile, t)) links.removeIndex(i);
                }

                sampledHeat = sampleMaxHeat();

                if (links.size == 0) return;

                float targetDepth = Mathf.clamp(
                        (sampledHeat - coldHeat) / (hotHeat - coldHeat) * 100f, 0f, 100f);

                currentDepth = Mathf.approachDelta(currentDepth, targetDepth, depthSpeed);

                int depthInt = Math.round(currentDepth);
                for (int i = 0; i < links.size; i++) {
                    Tile t = world.tile(links.items[i]);
                    if (t == null || t.build == null) continue;
                    if (t.build instanceof ReactorPart.ReactorPartBuild rp) {
                        if (rp.depth != depthInt) rp.depth = depthInt;
                    }
                }
            }
            @Override
            public Object config() {
                if (links.size == 0) return null;
                Point2[] out = new Point2[links.size];
                for (int i = 0; i < links.size; i++) {
                    out[i] = Point2.unpack(links.items[i]).sub(tileX(), tileY());
                }
                return out;
            }

            private float sampleMaxHeat() {
                float max = 0f;
                int cx = tileX(), cy = tileY();
                for (int dx = -sampleRange; dx <= sampleRange; dx++) {
                    for (int dy = -sampleRange; dy <= sampleRange; dy++) {
                        Building b = world.build(cx + dx, cy + dy);
                        if (b instanceof NeutronNode nn) {
                            max = Math.max(max, nn.neutron().heat);
                        }
                    }
                }
                return max;
            }

            @Override
            public boolean onConfigureBuildTapped(Building other) {
                if (other == this) {
                    configure(-1);
                    return false;
                }
                if (linkValid(tile, other.tile)) {
                    configure(other.pos());
                    return false;
                }
                return true;
            }


            @Override
            public void buildConfiguration(Table table) {
                table.table(Styles.black6, t -> {
                    t.defaults().pad(4f);
                    t.label(() -> "链接控制棒: " + links.size + " / " + maxLinks).row();
                    t.label(() -> "采样最高温: " + (int) sampledHeat).row();
                    t.label(() -> "当前控制棒深度: " + Math.round(currentDepth) + "%").row();
                    t.button("清空链接", () -> configure(-1)).size(140f, 32f)
                            .disabled(b -> links.size == 0);
                });
            }

            @Override
            public void drawConfigure() {
                Drawf.select(x, y, block.size * tilesize / 2f + 2f, Pal.accent);
                for (int i = 0; i < links.size; i++) {
                    Tile t = world.tile(links.items[i]);
                    if (t != null && t.build != null) {
                        Drawf.select(t.build.x, t.build.y,
                                t.block().size * tilesize / 2f + 2f, Pal.place);
                    }
                }
            }

            @Override
            public void draw() {
                super.draw();

                for (int i = 0; i < links.size; i++) {
                    Tile t = world.tile(links.items[i]);
                    if (t == null || t.build == null) continue;

                    float tx = t.build.x, ty = t.build.y;
                    float dx = tx - x, dy = ty - y;
                    float adx = Math.abs(dx), ady = Math.abs(dy);
                    float sx = Math.signum(dx), sy = Math.signum(dy);

                    float px, py;
                    if (adx >= ady) { px = x + sx * (adx - ady); py = y; }
                    else            { px = x; py = y + sy * (ady - adx); }

                    Draw.z(Layer.power);

                    Draw.color(traceColor);
                    Lines.stroke(1f);
                    Lines.line(x, y, px, py);
                    Lines.line(px, py, tx, ty);

                    boolean bend = (px != x || py != y) && (px != tx || py != ty);
                    if (bend) {
                        Fill.circle(px, py, 1.5f);
                    }

                    Lines.stroke(1f);
                    Lines.circle(tx, ty, 2f);
                }

                Draw.reset();
            }

            @Override
            public byte version() {
                return 1;
            }

            @Override
            public void write(Writes write) {
                super.write(write);
                write.s(links.size);
                for (int i = 0; i < links.size; i++) {
                    write.s(Point2.x(links.items[i]) - tileX());
                    write.s(Point2.y(links.items[i]) - tileY());
                }
                write.f(currentDepth);
            }

            @Override
            public void read(Reads read, byte revision) {
                super.read(read, revision);
                links.clear();
                if (revision == 0) {
                    // 旧格式：int 数量 + 若干 int 绝对坐标
                    int n = read.i();
                    for (int i = 0; i < n; i++) links.add(read.i());
                } else {
                    int n = read.s();
                    for (int i = 0; i < n; i++) {
                        links.add(Point2.pack(tileX() + read.s(), tileY() + read.s()));
                    }
                }
                currentDepth = read.f();
            }
        }
    }
}