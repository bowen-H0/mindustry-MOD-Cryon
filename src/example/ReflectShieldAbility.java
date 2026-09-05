package example;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.struct.IntMap;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.entities.abilities.Ability;
import mindustry.gen.*;

//AI-generated
//独立的“紫盾”反弹护盾能力：持续反弹范围内的敌方子弹，累计承受伤害达到上限后护盾破裂并进入冷却
public class ReflectShieldAbility extends Ability {

    /** 护盾反弹判定半径 */
    public float reflectRadius = 80f;
    /** 反弹护盾能承受的最大伤害，达到后破裂 */
    public float reflectMaxDamage = 5000f;
    /** 反弹角度偏移范围（相对入射方向） */
    public float reflectAngleMin = 120f;
    public float reflectAngleMax = 180f;
    /** 护盾破裂后的冷却时间（tick），冷却期间不反弹、不拦截伤害 */
    public float shieldCooldown = 300f;

    // data[0] = 已承受伤害
    // data[1] = 冷却剩余时间（0 表示护盾激活）
    private static final IntMap<float[]> stateMap = new IntMap<>();

    private float[] getData(Unit unit) {
        float[] d = stateMap.get(unit.id);
        if (d == null) {
            d = new float[]{0f, 0f};
            stateMap.put(unit.id, d);
        }
        return d;
    }

    @Override
    public void update(Unit unit) {
        float[] d = getData(unit);

        if (d[1] > 0f) {
            // 冷却中
            d[1] -= Time.delta;
            if (d[1] < 0f) d[1] = 0f;
            return;
        }

        reflectBullets(unit, d);

        if (d[0] >= reflectMaxDamage) {
            shieldBreak(unit, d);
        }
    }

    private void reflectBullets(Unit unit, float[] d) {
        Groups.bullet.intersect(
                unit.x - reflectRadius, unit.y - reflectRadius,
                reflectRadius * 2f, reflectRadius * 2f,
                b -> {
                    if (b.team == unit.team || b.absorbed) return;
                    if (!b.within(unit, reflectRadius)) return;

                    float speed = b.vel.len();
                    float currentAngle = Mathf.angle(b.vel.x, b.vel.y);
                    float newAngle = currentAngle + Mathf.random(reflectAngleMin, reflectAngleMax);
                    b.vel.set(Mathf.cosDeg(newAngle) * speed, Mathf.sinDeg(newAngle) * speed);
                    b.team = unit.team;
                    b.owner = unit;
                    Fx.hitBulletSmall.at(b.x, b.y);

                    d[0] += b.damage();
                }
        );
    }

    /** 由外部伤害系统调用，拦截并累积直接命中的伤害；冷却中返回原始伤害（不拦截） */
    public float interceptDamage(Unit unit, float damage) {
        float[] d = getData(unit);
        if (d[1] > 0f) return damage;

        d[0] += damage;
        return 0f;
    }

    private void shieldBreak(Unit unit, float[] d) {
        d[0] = 0f;
        d[1] = shieldCooldown;
        Fx.shieldBreak.at(unit.x, unit.y, unit.rotation, Color.white, unit);
    }

    @Override
    public void draw(Unit unit) {
        float[] d = getData(unit);

        if (d[1] > 0f) return; // 冷却中不绘制

        float progress = d[0] / reflectMaxDamage;
        float pulse = Mathf.sin(Time.time, 25f, 1f) * 0.12f + 0.88f;

        Draw.z(75f);
        Lines.stroke(2f + (1f - progress) * 2f, Color.valueOf("cc44ff"));
        Draw.alpha(0.6f * pulse);
        Lines.circle(unit.x, unit.y, reflectRadius);
        Draw.alpha(0.12f * (1f - progress) * pulse);
        Fill.circle(unit.x, unit.y, reflectRadius);
        Draw.reset();
    }

    @Override
    public void death(Unit unit) {
        stateMap.remove(unit.id);
    }
}