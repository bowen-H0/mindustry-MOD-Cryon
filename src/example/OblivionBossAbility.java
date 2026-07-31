package example;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.struct.IntMap;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.abilities.Ability;
import mindustry.gen.*;
import mindustry.type.UnitType;
//AI-generated
public class OblivionBossAbility extends Ability {

    public float absorbMaxCharge = 8000f;
    public float absorbHealPerSecond = 80f;
    public float reflectRadius = 80f;
    public float phaseDuration = 180f;
    public float phaseEnergyDrain = 20f;
    public int phantomCount = 6;
    public String phantomTypeName = "flare";
    public float spawnInterval = 600f;
    public float absorbCapacity = 20000f;
    public float reflectMaxDamage = 5000f;
    public float shieldCooldown = 300f;  // 冷却5秒
    public float lastHealth;
    public float dpsTimer = 0f;
    public float realDps = 0f;
    public float maxDpsThreshold = 200f;
    public static final int STATE_ABSORB  = 0;
    public static final int STATE_REFLECT = 1;
    public static final int STATE_PHASE   = 2;

    private static final IntMap<float[]> stateMap = new IntMap<>();

    private float[] getData(Unit unit) {
        float[] d = stateMap.get(unit.id);
        if (d == null) {
            d = new float[]{STATE_ABSORB, 0f, 0f, 0f, 0f, 0f};
            stateMap.put(unit.id, d);
        }
        return d;
    }

    @Override
    public void update(Unit unit) {
        float[] d = getData(unit);
        int state = (int) d[0];

        d[5] += Time.delta;
        if (d[5] >= spawnInterval) {
            d[5] = 0f;
            spawnPhantoms(unit);
        }

        if (state == STATE_ABSORB) {
            dpsTimer += Time.delta;
            float hpLoss = lastHealth - unit.health;
            if (hpLoss > 0) {
                realDps = hpLoss / (dpsTimer / 60f);
            }

            if (dpsTimer >= 60f) {
                if (realDps > maxDpsThreshold) {
                    shieldBreak(unit);
                    enterReflect(unit, d);
                    dpsTimer = 0f;
                    lastHealth = unit.health;
                    return;
                }
                dpsTimer = 0f;
                lastHealth = unit.health;
            }

            absorbBullets(unit, d);
        } else if (state == STATE_REFLECT) {
            reflectBullets(unit, d);
        }

        switch (state) {
            case STATE_ABSORB  -> updateAbsorb(unit, d);
            case STATE_REFLECT -> updateReflect(unit, d);
            case STATE_PHASE   -> updatePhase(unit, d);
        }
    }

    private void absorbBullets(Unit unit, float[] d) {
        Groups.bullet.intersect(
                unit.x - reflectRadius, unit.y - reflectRadius,
                reflectRadius * 2f, reflectRadius * 2f,
                b -> {
                    if (b.team == unit.team || b.absorbed) return;
                    if (!b.within(unit, reflectRadius)) return;

                    b.absorb();
                    Fx.absorb.at(b);

                    float dmg = b.damage();
                    d[1] = Math.min(d[1] + dmg * 0.5f, absorbMaxCharge);
                    unit.heal(dmg * 0.5f);
                }
        );
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
                    float newAngle = currentAngle + Mathf.random(120f, 180f);
                    b.vel.set(Mathf.cosDeg(newAngle) * speed, Mathf.sinDeg(newAngle) * speed);
                    b.team = unit.team;
                    b.owner = unit;
                    Fx.hitBulletSmall.at(b.x, b.y);

                    d[2] += b.damage();
                }
        );
    }

    private void updateAbsorb(Unit unit, float[] d) {
        if (d[5] > 0f) {
            d[5] -= Time.delta;
            return;
        }

        unit.heal(absorbHealPerSecond * Time.delta / 60f);

        if (d[1] >= absorbMaxCharge) {
            enterPhase(unit, d);
        }
    }

    private void updateReflect(Unit unit, float[] d) {
        reflectBullets(unit, d);

        if (d[2] >= reflectMaxDamage) {
            shieldBreak(unit);
            enterAbsorb(unit, d);
        }
    }

    private void updatePhase(Unit unit, float[] d) {
        d[3] -= Time.delta;
        d[1] -= phaseEnergyDrain * Time.delta;

        if (d[3] <= 0f || d[1] <= 0f) {
            enterReflect(unit, d);
        }
    }

    private void enterAbsorb(Unit unit, float[] d) {
        d[0] = STATE_ABSORB;
        d[1] = 0f;
        lastHealth = unit.health;
        dpsTimer = 0f;
        realDps = 0f;
        Fx.shieldBreak.at(unit.x, unit.y, 0f, Color.green, unit);
    }

    private void enterReflect(Unit unit, float[] d) {
        d[0] = STATE_REFLECT;
        d[2] = 0f;
        dpsTimer = 0f;
        realDps = 0f;
        lastHealth = unit.health;
        Fx.shieldBreak.at(unit.x, unit.y, 0f, Color.purple, unit);
    }

    private void enterPhase(Unit unit, float[] d) {
        d[0] = STATE_PHASE;
        d[3] = phaseDuration;
        Fx.unitSpawn.at(unit.x, unit.y, 0f, Color.white, unit);
    }

    public float interceptDamage(Unit unit, float damage) {
        float[] d = getData(unit);
        int state = (int) d[0];

        if (state == STATE_PHASE) return 0f;
        if (state == STATE_ABSORB) {
            d[1] = Math.min(d[1] + damage, absorbMaxCharge);
            return 0f;
        }
        if (state == STATE_REFLECT) {
            d[2] += damage;
            return 0f;
        }
        return damage;
    }



    private void spawnPhantoms(Unit unit) {
        UnitType phantomType = Vars.content.units().find(u -> u.name.equals(phantomTypeName));
        if (phantomType == null) return;

        for (int i = 0; i < phantomCount; i++) {
            float angle = (360f / phantomCount) * i;
            float spawnX = unit.x + Mathf.cosDeg(angle) * 60f;
            float spawnY = unit.y + Mathf.sinDeg(angle) * 60f;
            phantomType.spawn(unit.team, spawnX, spawnY);
        }
    }

    private void shieldBreak(Unit unit) {
        Fx.shieldBreak.at(unit.x, unit.y, unit.rotation, Color.white, unit);
    }

    @Override
    public void draw(Unit unit) {
        float[] d = getData(unit);
        int state = (int) d[0];

        switch (state) {
            case STATE_ABSORB  -> drawAbsorbShield(unit, d);
            case STATE_REFLECT -> drawReflectShield(unit, d);
            case STATE_PHASE   -> drawPhaseEffect(unit, d);
        }
    }

    private void drawAbsorbShield(Unit unit, float[] d) {
        float progress = d[1] / absorbMaxCharge;
        float radius = unit.type.hitSize * 1.6f + 4f;
        float pulse = Mathf.sin(Time.time, 30f, 1f) * 0.1f + 0.9f;

        Draw.z(75f);
        Lines.stroke(2.5f + progress * 1.5f, Color.valueOf("44ef76"));
        Draw.alpha(0.55f * pulse);
        Lines.circle(unit.x, unit.y, radius);
        Draw.alpha(0.85f);
        Lines.arc(unit.x, unit.y, radius + 4f, progress, -90f);
        Draw.alpha(0.15f * progress * pulse);
        Fill.circle(unit.x, unit.y, radius);
        Draw.reset();
    }

    private void drawReflectShield(Unit unit, float[] d) {
        float progress = d[2] / reflectMaxDamage;
        float pulse = Mathf.sin(Time.time, 25f, 1f) * 0.12f + 0.88f;

        Draw.z(75f);
        Lines.stroke(2f + (1f - progress) * 2f, Color.valueOf("cc44ff"));
        Draw.alpha(0.6f * pulse);
        Lines.circle(unit.x, unit.y, reflectRadius);
        Draw.alpha(0.12f * (1f - progress) * pulse);
        Fill.circle(unit.x, unit.y, reflectRadius);
        Draw.reset();
    }

    private void drawPhaseEffect(Unit unit, float[] d) {
        float progress = d[3] / phaseDuration;
        float pulse = Mathf.sin(Time.time, 15f, 1f) * 0.3f + 0.7f;

        Draw.z(76f);
        Draw.alpha(0.25f * progress * pulse);
        Fill.circle(unit.x, unit.y, unit.type.hitSize * 2f);
        Lines.stroke(1.5f, Color.white);
        Draw.alpha(0.5f * progress * pulse);
        Lines.dashCircle(unit.x, unit.y, unit.type.hitSize * 1.8f);
        Draw.reset();
    }

    @Override
    public void death(Unit unit) {
        stateMap.remove(unit.id);
        unit.hitSize = unit.type.hitSize;
    }
}