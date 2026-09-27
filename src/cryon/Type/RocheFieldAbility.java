package cryon.Type;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import cryon.Features.ReflectShieldRenderer;
import mindustry.entities.*;
import mindustry.entities.abilities.Ability;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;

import static mindustry.Vars.*;

public class RocheFieldAbility extends Ability{
    private static final Seq<Healthc> all = new Seq<>();
    private static final Vec2 tmp = new Vec2();

    // ---------- 特效(颜色由 e.color 传入) ----------
    // 命中:简单红色粒子
    public static final Effect hitFx = new Effect(26f, e -> {
        Draw.color(Color.white, e.color, e.fin());
        Angles.randLenVectors(e.id, 8, 4f + 20f * e.finpow(), (x, y) -> {
            Fill.circle(e.x + x, e.y + y, 0.6f + 2.2f * e.fout());
        });
        Draw.reset();
    });

    // 子弹被星吞掉
    public static final Effect absorbFx = new Effect(16f, e -> {
        Draw.color(Color.white, e.color, e.fin());
        Lines.stroke(1.6f * e.fout());
        Lines.circle(e.x, e.y, 3f + 8f * e.fin());
        Angles.randLenVectors(e.id, 4, 2f + 10f * e.finpow(), (x, y) -> {
            Fill.circle(e.x + x, e.y + y, 0.2f + 0.8f * e.fout());
        });
        Draw.reset();
    });

    // 吸血激光:从星连到友军(data = 友军)
    public static final Effect drainFx = new Effect(18f, 600f, e -> {
        if(!(e.data instanceof Position p)) return;
        float fout = e.fout();

        Draw.color(e.color);
        Draw.alpha(0.5f * fout);
        Lines.stroke(7f * fout);
        Lines.line(e.x, e.y, p.getX(), p.getY());

        Draw.color(Color.white);
        Draw.alpha(fout);
        Lines.stroke(2.2f * fout);
        Lines.line(e.x, e.y, p.getX(), p.getY());
        Draw.reset();
    });

    // ---------- 范围伤害 ----------
    public float damage = 600f;        // 每次脉冲对每个目标的伤害
    public float reload = 180f;        // 脉冲间隔(tick),180 = 3 秒
    public float range = 420f;
    public int maxTargets = 30;
    public boolean targetGround = true, targetAir = true, hitBuildings = true, hitUnits = true;

    // ---------- 三颗星 ----------
    public int stars = 3;
    public float orbitRatio = 0.5f;     // 公转轨道半径 = range * 这个值(即范围的一半)
    public float orbitSpeed = 0.6f;     // 公转速度(度/tick)
    public float coreRadius = 7f;       // 核心光球半径
    public float spikeLength = 110f;    // 横向光锥长度(最长)
    public float verticalScale = 0.65f; // 纵向光锥相对横向的长度
    public float diagScale = 0.3f;      // 斜向短光锥相对长度
    public float glowScale = 9f;        // 外晕半径 = coreRadius * 这个值
    public float absorbRadius = 18f;    // 吞子弹半径

    public boolean drawRing = true;     // 是否画范围圈
    public boolean centerStar = true;   // 机体中心大星
    public float centerCore = 6f;
    public float centerSpike = 170f;
    public float centerOffsetX = 0f, centerOffsetY = -30f;   // 跟随机体朝向

    public float layer = ReflectShieldAbility.layerReflectShield + 10f;
    // ---------- 友军吸血 ----------
    public float drainDamage = 200f;   // 每次对友军造成的伤害
    public float drainReload = 15f;    // 间隔(tick)
    public float drainHealMult = 1f;   // 回血 = 友军实际受到的伤害 * 这个倍率

    public Color color = Color.valueOf("ff2a2a");

    protected float timer, drainTimer, flash,wave;

    public RocheFieldAbility(){}

    public RocheFieldAbility(float damage, float reload, float range){
        this.damage = damage;
        this.reload = reload;
        this.range = range;
    }

    /** 第 i 颗星的位置。角度只和世界时间有关,不跟机体朝向。 */
    protected void starPos(Unit unit, int i){
        float ang = Time.time * orbitSpeed + i * 360f / stars + (unit.id % 360);
        tmp.trns(ang, range * orbitRatio).add(unit.x, unit.y);
    }

    @Override
    public void addStats(Table t){
        if(Core.bundle.has(getBundle() + ".description")){
            t.add(Core.bundle.get(getBundle() + ".description")).wrap().width(descriptionWidth);
            t.row();
        }
        t.add(Core.bundle.format("bullet.range", Strings.autoFixed(range / tilesize, 2)));
        t.row();
        t.add(abilityStat("firingrate", Strings.autoFixed(60f / reload, 2)));
        t.row();
        t.add(abilityStat("maxtargets", maxTargets));
        t.row();
        t.add(Core.bundle.format("bullet.damage", damage));
    }

    /** 一根光锥:从宽到窄叠 4 层,外层红色柔光,内层白热。角度由调用者固定传入。 */
    protected void spike(float x, float y, float angle, float length, float width){
        Draw.color(color);
        Draw.alpha(0.25f);
        Drawf.tri(x, y, width * 2.4f, length, angle);
        Draw.alpha(0.55f);
        Drawf.tri(x, y, width * 1.5f, length * 0.8f, angle);
        Draw.color(Color.white);
        Draw.alpha(0.85f);
        Drawf.tri(x, y, width * 0.8f, length * 0.6f, angle);
        Draw.alpha(1f);
        Drawf.tri(x, y, width * 0.35f, length * 0.4f, angle);
    }

    @Override
    public void draw(Unit unit){
        super.draw(unit);

        Draw.z(layer);
        Draw.blend(Blending.additive);   // 叠加混合,发光才够刺眼

        // ===== 范围圈 =====
        if(drawRing){
            float charge = Mathf.clamp(timer / reload);
            float a = 0.4f + Mathf.absin(Time.time, 30f, 0.1f) + flash * 0.2f;

            // 外圈柔光 + 细亮线
            Draw.color(color);
            Draw.alpha(a * 0.3f);
            Lines.stroke(7f);
            Lines.circle(unit.x, unit.y, range);
            Draw.alpha(a);
            Lines.stroke(1.5f);
            Lines.circle(unit.x, unit.y, range);

            // 下一次脉冲的蓄力进度弧
            Draw.color(Color.white);
            Draw.alpha(0.8f);
            Lines.stroke(2.5f);
            Lines.arc(unit.x, unit.y, range, charge, 90f);

            // 脉冲触发后从中心扩散到边缘的环
            if(wave > 0f){
                Draw.color(color);
                Draw.alpha(wave * 0.8f);
                Lines.stroke(4f * wave + 1f);
                Lines.circle(unit.x, unit.y, range * (1f - wave));
            }
        }

        // ===== 三颗星 =====
        for(int i = 0; i < stars; i++){
            starPos(unit, i);
            float sx = tmp.x, sy = tmp.y;
            float pulse = 1f + Mathf.absin(Time.time + i * 25f, 9f, 0.15f) + flash * 0.5f;
            float r = coreRadius * pulse;

            // 大范围红色外晕(径向渐变)
            Fill.light(sx, sy, 40, r * glowScale,
                    Tmp.c1.set(color).a(0.5f), Tmp.c2.set(color).a(0f));
            // 中层亮晕
            Fill.light(sx, sy, 32, r * glowScale * 0.4f,
                    Tmp.c1.set(Color.white).a(0.7f), Tmp.c2.set(color).a(0f));

            // 光锥:角度写死,永远水平/垂直,不随公转或机体转动
            float len = spikeLength * pulse;
            for(int k = 0; k < 4; k++){
                float ang = k * 90f;                       // 0 右 / 90 上 / 180 左 / 270 下
                float l = (k % 2 == 0) ? len : len * verticalScale;
                spike(sx, sy, ang, l, r * 1.2f);
            }
            for(int k = 0; k < 4; k++){
                spike(sx, sy, 45f + k * 90f, len * diagScale, r * 0.8f);   // 固定的斜向短锥
            }

            // 核心:白热球
            Fill.light(sx, sy, 28, r * 2.4f,
                    Tmp.c1.set(Color.white).a(1f), Tmp.c2.set(color).a(0f));
            Draw.color(Color.white);
            Fill.circle(sx, sy, r * 0.9f);

            Drawf.light(sx, sy, spikeLength * 2.5f, color, 0.9f);
        }

        Draw.blend();
        Draw.reset();
    }

    /** 每帧对所有带此能力的单位调用一次 */
    public static void drawAll(){
        Groups.unit.each(u -> {
            for(Ability a : u.abilities){
                if(a instanceof RocheFieldAbility r) r.drawOverlay(u);
            }
        });
    }

    /** 画一颗星。光锥角度写死,不自转。 */
    protected void drawStar(float sx, float sy, float coreR, float spikeLen, float phase){
        float pulse = 1f + Mathf.absin(Time.time + phase, 9f, 0.12f) + flash * 0.4f;
        float r = coreR * pulse;

        Fill.light(sx, sy, 40, r * 3.5f, Tmp.c1.set(color).a(0.35f), Tmp.c2.set(color).a(0f));
        Fill.light(sx, sy, 32, r * 3.5f * 0.45f, Tmp.c1.set(Color.white).a(0.6f), Tmp.c2.set(color).a(0f));

        float len = spikeLen * pulse;
        for(int k = 0; k < 4; k++){
            spike(sx, sy, k * 90f, (k % 2 == 0) ? len : len * verticalScale, r * 0.6f);
        }
        for(int k = 0; k < 4; k++){
            spike(sx, sy, 45f + k * 90f, len * diagScale, r * 0.4f);
        }

        Fill.light(sx, sy, 28, r * 1.6f, Tmp.c1.set(Color.white).a(1f), Tmp.c2.set(color).a(0f));
        Draw.color(Color.white);
        Fill.circle(sx, sy, r * 0.7f);
    }

    public void drawOverlay(Unit unit){
        // 简单视锥裁剪
        if(!Core.camera.bounds(Tmp.r1).grow(range * 2f).contains(unit.x, unit.y)) return;

        float z = Draw.z();
        Draw.z(layer);
        Draw.blend(Blending.additive);

        if(drawRing){
            float charge = Mathf.clamp(timer / reload);
            float a = 0.4f + Mathf.absin(Time.time, 30f, 0.1f) + flash * 0.2f;
            Draw.color(color);
            Draw.alpha(a * 0.3f);
            Lines.stroke(7f);
            Lines.circle(unit.x, unit.y, range);
            Draw.alpha(a);
            Lines.stroke(1.5f);
            Lines.circle(unit.x, unit.y, range);
            Draw.color(Color.white);
            Draw.alpha(0.8f);
            Lines.stroke(2.5f);
            Lines.arc(unit.x, unit.y, range, charge, 90f);
            if(wave > 0f){
                Draw.color(color);
                Draw.alpha(wave * 0.8f);
                Lines.stroke(4f * wave + 1f);
                Lines.circle(unit.x, unit.y, range * (1f - wave));
            }
        }

        for(int i = 0; i < stars; i++){
            starPos(unit, i);
            drawStar(tmp.x, tmp.y, coreRadius, spikeLength, i * 25f);
        }

        if(centerStar){
            tmp.trns(unit.rotation - 90f, centerOffsetX, centerOffsetY).add(unit.x, unit.y);
            drawStar(tmp.x, tmp.y, centerCore, centerSpike, 0f);
        }

        Draw.blend();
        Draw.color();
        Draw.z(z);
    }
    @Override
    public void update(Unit unit){
        flash = Mathf.lerpDelta(flash, 0f, 0.1f);
        wave = Math.max(0f, wave - Time.delta / 40f);

        // ===== 1. 星吞掉敌方子弹 =====
        for(int i = 0; i < stars; i++){
            starPos(unit, i);
            float sx = tmp.x, sy = tmp.y;
            Groups.bullet.intersect(sx - absorbRadius, sy - absorbRadius, absorbRadius * 2f, absorbRadius * 2f, b -> {
                if(b.team != unit.team && b.type.absorbable && b.within(sx, sy, absorbRadius + b.type.hitSize / 2f)){
                    absorbFx.at(b.x, b.y, 0f, color);
                    b.absorb();
                    flash = 1f;
                }
            });
        }

        // ===== 2. 每隔几秒,范围内敌人/建筑掉血 =====
        if((timer += Time.delta) >= reload){
            timer = 0f;
            wave = 1f;
            all.clear();

            if(hitUnits){
                Units.nearby(null, unit.x, unit.y, range, other -> {
                    if(other.team != unit.team && other.checkTarget(targetAir, targetGround) && other.targetable(unit.team)){
                        all.add(other);
                    }
                });
            }
            if(hitBuildings && targetGround){
                Units.nearbyBuildings(unit.x, unit.y, range, b -> {
                    if((b.team != Team.derelict || state.rules.coreCapture) && b.team != unit.team && b.block.targetable && !b.block.privileged){
                        all.add(b);
                    }
                });
            }

            all.sort(h -> h.dst2(unit.x, unit.y));
            int len = Math.min(all.size, maxTargets);
            float scaledDamage = damage * state.rules.unitDamage(unit.team) * unit.damageMultiplier;

            for(int i = 0; i < len; i++){
                Healthc h = all.get(i);
                if(h instanceof Building b){
                    b.damage(unit.team, scaledDamage);
                }else{
                    h.damage(scaledDamage);
                }
                hitFx.at(h.x(), h.y(), 0f, color);
            }
        }

        // ===== 3. 自己没满血时,三颗星向最近的友军放红色激光吸血 =====
        if(unit.health < unit.maxHealth && (drainTimer += Time.delta) >= drainReload){
            drainTimer = 0f;
            for(int i = 0; i < stars; i++){
                if(unit.health >= unit.maxHealth) break;

                starPos(unit, i);
                float sx = tmp.x, sy = tmp.y;
                Unit ally = Units.closest(unit.team, sx, sy, range, u -> u != unit && u.within(unit, range));
                if(ally == null) continue;

                // 真实伤害:不走 damage/damagePierce 的伤害管线(不受护盾、护甲、易伤等任何加成/减免影响),
                // 直接手动扣 health,回血量就是实际扣掉的血量
                float taken = Math.min(drainDamage, ally.health);
                ally.health -= taken;
                ally.clampHealth();
                unit.heal(taken * drainHealMult);

                drainFx.at(sx, sy, 0f, color, ally);
                hitFx.at(ally.x, ally.y, 0f, color);
            }
        }
    }
}