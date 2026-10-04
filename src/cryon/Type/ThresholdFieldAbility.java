package cryon.Type;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.abilities.Ability;
import mindustry.gen.*;
import mindustry.graphics.*;

/** 视界能量场:中心发光球 + 范围圈;范围内敌人减速;同一时间只攻击一个目标,伸出两条能量刃切割。 */
public class ThresholdFieldAbility extends Ability{
    public float range = 380f;
    public float slowDuration = 45f;
    public float bladeDamage = 600f;     // 每条刃每次伤害
    public float bladeReload = 10f;
    public int bladeCount = 2;
    public float bladeWidth = 4.5f;
    public float bladeSwing = 34f;       // 刃尖在目标周围的摆动幅度
    public boolean targetAir = true, targetGround = true;

    public float coreRadius = 14f;
    public float centerOffsetX = 0f, centerOffsetY = 0f;   // 跟随机体朝向
    public boolean drawRing = true;
    public float layer = ReflectShieldAbility.layerReflectShield + 10f;

    public Color color = Color.valueOf("ffa31a");
    public Color goldColor = Color.valueOf("ffd75e");

    public static final Effect bladeHitFx = new Effect(18f, e -> {
        Draw.blend(Blending.additive);
        Draw.color(Color.white, e.color, e.fin());
        Angles.randLenVectors(e.id, 6, 4f + 22f * e.finpow(), (x, y) -> {
            Fill.circle(e.x + x, e.y + y, 0.8f + 2.6f * e.fout());
        });
        Lines.stroke(2f * e.fout());
        Lines.circle(e.x, e.y, 4f + 14f * e.fin());
        Draw.blend();
        Draw.reset();
    });

    protected Teamc target;
    protected float bladeProg, bladeTimer, retargetTimer;
    protected float lastTx, lastTy;

    public ThresholdFieldAbility(){}

    public ThresholdFieldAbility(float range, float bladeDamage, float bladeReload){
        this.range = range;
        this.bladeDamage = bladeDamage;
        this.bladeReload = bladeReload;
    }

    protected Vec2 center(Unit unit){
        return Tmp.v3.trns(unit.rotation - 90f, centerOffsetX, centerOffsetY).add(unit.x, unit.y);
    }

    @Override
    public void update(Unit unit){
        Vec2 c = center(unit);
        float cx = c.x, cy = c.y;

        // 1. 范围内敌人缓慢
        Units.nearbyEnemies(unit.team, Tmp.r1.setCentered(cx, cy, range * 2f), u -> {
            if(u.within(cx, cy, range + u.hitSize / 2f) && u.checkTarget(targetAir, targetGround)){
                u.apply(StatusEffects.slow, slowDuration);
            }
        });

        // 2. 只锁定一个目标
        if(target != null && Units.invalidateTarget(target, unit.team, cx, cy, range)) target = null;
        if(target == null && (retargetTimer += Time.delta) >= 15f){
            retargetTimer = 0f;
            target = Units.closestTarget(unit.team, cx, cy, range,
                    u -> u.checkTarget(targetAir, targetGround), b -> targetGround);
        }
        if(target != null){
            lastTx = target.getX();
            lastTy = target.getY();
        }

        bladeProg = Mathf.approachDelta(bladeProg, target != null ? 1f : 0f, 1f / 20f);

        // 3. 能量刃完全伸出后开始切割
        if(target != null && bladeProg >= 0.99f && (bladeTimer += Time.delta) >= bladeReload){
            bladeTimer = 0f;
            float dmg = bladeDamage * Vars.state.rules.unitDamage(unit.team) * unit.damageMultiplier;
            for(int i = 0; i < bladeCount; i++){
                if(target instanceof Unit u){
                    u.damagePierce(dmg);
                }else if(target instanceof Building b){
                    b.damage(unit.team, dmg);
                }
                bladeHitFx.at(target.getX() + Mathf.range(8f), target.getY() + Mathf.range(8f), 0f, color);
            }
        }
    }

    /** 纯发光线:多层叠加的粗细线(外晕橙 -> 金 -> 白热芯),两端带光晕,轻微脉动。 */
    protected void blade(float x, float y, float x2, float y2, float width){
        float w = width * (1f + Mathf.absin(Time.time, 4f, 0.12f));

        Draw.color(color);
        Draw.alpha(0.12f);
        Lines.stroke(w * 4.5f);
        Lines.line(x, y, x2, y2);
        Draw.alpha(0.25f);
        Lines.stroke(w * 2.6f);
        Lines.line(x, y, x2, y2);
        Draw.alpha(0.55f);
        Lines.stroke(w * 1.5f);
        Lines.line(x, y, x2, y2);

        Draw.color(goldColor);
        Draw.alpha(0.95f);
        Lines.stroke(w * 0.8f);
        Lines.line(x, y, x2, y2);

        Draw.color(Color.white);
        Draw.alpha(1f);
        Lines.stroke(w * 0.35f);
        Lines.line(x, y, x2, y2);

        // 刃尖光晕
        Fill.light(x2, y2, 24, w * 3.5f, Tmp.c1.set(Color.white).a(0.9f), Tmp.c2.set(color).a(0f));
    }

    @Override
    public void draw(Unit unit){
        super.draw(unit);
        Vec2 c = center(unit);
        float cx = c.x, cy = c.y;

        float z = Draw.z();
        Draw.z(layer);
        Draw.blend(Blending.additive);

        if(drawRing){
            float a = 0.4f + Mathf.absin(Time.time, 30f, 0.1f);
            Fill.light(cx, cy, 64, range, Tmp.c1.set(color).a(0f), Tmp.c2.set(color).a(0.1f));
            Draw.color(color);
            Draw.alpha(a * 0.3f);
            Lines.stroke(7f);
            Lines.circle(cx, cy, range);
            Draw.alpha(a);
            Lines.stroke(1.5f);
            Lines.circle(cx, cy, range);
            // 缓慢旋转的刻度弧
            Draw.color(goldColor);
            Draw.alpha(0.8f);
            Lines.stroke(2.5f);
            for(int i = 0; i < 4; i++){
                Lines.arc(cx, cy, range, 0.06f, Time.time * 0.4f + i * 90f);
            }
        }

        // 中心发光球
        float pulse = 1f + Mathf.absin(Time.time, 10f, 0.12f) + (bladeProg > 0.5f ? 0.15f : 0f);
        float r = coreRadius * pulse;
        Fill.light(cx, cy, 48, r * 4.5f, Tmp.c1.set(color).a(0.5f), Tmp.c2.set(color).a(0f));
        Fill.light(cx, cy, 40, r * 2.2f, Tmp.c1.set(Color.white).a(0.9f), Tmp.c2.set(color).a(0f));
        Draw.color(goldColor);
        Fill.circle(cx, cy, r);
        Draw.color(Color.white);
        Fill.circle(cx, cy, r * 0.6f);
        Draw.color(goldColor);
        Draw.alpha(0.9f);
        Lines.stroke(2f);
        Lines.arc(cx, cy, r * 1.6f, 0.25f, Time.time * 3f);
        Lines.arc(cx, cy, r * 1.6f, 0.25f, Time.time * 3f + 180f);
        Drawf.light(cx, cy, range * 0.8f, color, 0.8f);

        // 能量刃:刃尖在目标两侧交错摆动,像剪刀切割
        if(bladeProg > 0.01f){
            float dx = lastTx - cx, dy = lastTy - cy;
            float dist = Mathf.dst(dx, dy);
            float baseAng = Mathf.angle(dx, dy);
            for(int i = 0; i < bladeCount; i++){
                float swing = Mathf.sin(Time.time * 0.18f + i * Mathf.PI * 2f / bladeCount) * bladeSwing;
                Tmp.v1.trns(baseAng + 90f, swing).add(lastTx, lastTy);
                float p = Interp.pow2Out.apply(bladeProg);
                float ex = cx + (Tmp.v1.x - cx) * p, ey = cy + (Tmp.v1.y - cy) * p;
                blade(cx, cy, ex, ey, bladeWidth * (0.4f + 0.6f * bladeProg));
            }
        }

        Draw.blend();
        Draw.reset();
        Draw.z(z);
    }
}