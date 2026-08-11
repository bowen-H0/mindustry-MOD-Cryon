package example;

import arc.Core;
import arc.audio.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.entities.abilities.Ability;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import arc.math.geom.*;
import mindustry.entities.*;
import static mindustry.Vars.*;
import mindustry.world.meta.*;

public class FluxBarrierAbility extends Ability {

    public float radius = 60f;
    public int sides = 6;
    public float rotation = 0f;

    public float maxShieldHeat = 300f;
    public float heatDissipationRate = 200f;
    public float heatCooldownImmuneTime = 60f;

    public Sound breakSound = Sounds.shieldBreak;
    public Sound hitSound = Sounds.shieldHit;
    public float hitSoundVolume = 0.12f;

    protected float shieldHeat = 0f;
    protected float heatCooldownTimer = 0f;
    protected float maxHeatCooldownTime = 0f;
    protected float heatCooldownImmune = 0f;
    protected float radscl = 0f;
    protected float hit = 0f;
    protected float warmup = 0f;

    private static float realRad;
    private static Unit paramUnit;
    private static FluxBarrierAbility paramField;

    private static final Cons<Bullet> shieldConsumer = b -> {
        if (b.team == paramUnit.team || !b.type.absorbable || b.absorbed) return;
        if (!Intersector.isInRegularPolygon(paramField.sides, paramUnit.x, paramUnit.y,
                realRad, paramField.rotation, b.x(), b.y())) return;
        // todo It should be different from ForceProjector.
        b.absorb();
        Fx.absorb.at(b);
        paramField.hitSound.at(b.x, b.y, 1f + Mathf.range(0.1f), paramField.hitSoundVolume);
        paramField.hit = 1f;

        float damage = b.type().shieldDamage(b);
        float heatGenerated = damage * damage * damage * 0.001f;
        // todo This numerical design needs improvement.
        paramField.shieldHeat += heatGenerated;
        if (paramField.shieldHeat > paramField.maxShieldHeat) {
            paramField.shieldHeat = paramField.maxShieldHeat;
        }

        if (paramField.heatCooldownImmune <= 0 &&
                (paramField.heatCooldownTimer <= 0 || paramField.maxHeatCooldownTime < damage)) {
            paramField.heatCooldownTimer = damage * 1.5f;
            paramField.maxHeatCooldownTime = damage;
        }
    };

    public FluxBarrierAbility() {}

    public FluxBarrierAbility(float radius, float maxShieldHeat, float heatDissipationRate) {
        this.radius = radius;
        this.maxShieldHeat = maxShieldHeat;
        this.heatDissipationRate = heatDissipationRate;
    }

    public FluxBarrierAbility(float radius, float maxShieldHeat, float heatDissipationRate,
                              int sides, float rotation) {
        this.radius = radius;
        this.maxShieldHeat = maxShieldHeat;
        this.heatDissipationRate = heatDissipationRate;
        this.sides = sides;
        this.rotation = rotation;
    }

    @Override
    public void created(Unit unit) {
        shieldHeat = 0f;
        radscl = 0f;
        warmup = 0f;
    }

    @Override
    public void update(Unit unit) {
        if (shieldHeat >= maxShieldHeat) {
            Fx.shieldBreak.at(unit.x, unit.y, radius, unit.type.shieldColor(unit));
            breakSound.at(unit.x, unit.y);
            unit.kill();
            return;
        }

        warmup = Mathf.lerpDelta(warmup, 1f, 0.1f);
        radscl = Mathf.lerpDelta(radscl, warmup, 0.05f);
        realRad = radscl * radius;

        if (heatCooldownTimer > 0) {
            heatCooldownTimer -= Time.delta;
            if (heatCooldownTimer <= 0) {
                heatCooldownImmune = heatCooldownImmuneTime;
            }
        } else if (heatCooldownImmune > 0) {
            heatCooldownImmune -= Time.delta;
        } else {
            // 正常散热
            if (shieldHeat > 0) {
                float dissipation = heatDissipationRate * Time.delta / 60f;
                shieldHeat = Math.max(0f, shieldHeat - dissipation);
            }
        }

        if (hit > 0f) {
            hit -= Time.delta / 5f;
        }
        if (realRad > 0.001f) {
            paramUnit = unit;
            paramField = this;
            Groups.bullet.intersect(
                    unit.x - realRad, unit.y - realRad,
                    realRad * 2f, realRad * 2f,
                    shieldConsumer
            );
        }
        Units.nearbyEnemies(unit.team, unit.x, unit.y, realRad + 10f, enemy -> {
            float overlapDst = (enemy.hitSize / 2f + realRad) - enemy.dst(unit);
            if(overlapDst > 0){
                if(overlapDst > enemy.hitSize * 1.5f){
                    enemy.kill();
                }else{
                    enemy.vel.setZero();
                    enemy.move(Tmp.v1.set(enemy).sub(unit).setLength(overlapDst + 0.01f));
                    if(Mathf.chanceDelta(0.12f)){
                        Fx.circleColorSpark.at(enemy.x, enemy.y, unit.team.color);
                    }
                }
            }
        });
    }

    @Override
    public void death(Unit unit) {
        if (radscl > 0.01f) {
            Fx.shieldBreak.at(unit.x, unit.y, radius, unit.type.shieldColor(unit));
            breakSound.at(unit.x, unit.y);
        }
    }
    @Override
    public void addStats(Table t){
        t.add(Core.bundle.format("bullet.range", Strings.autoFixed(radius / tilesize, 2))).row();
        t.add(Core.bundle.get("ability.stat.max_temperature") + ": " + Strings.autoFixed(maxShieldHeat, 2)).row();
        t.add(abilityStat("repairspeed", Strings.autoFixed(heatDissipationRate, 2) + StatUnit.perSecond.localized())).row();
    }
    @Override
    public void draw(Unit unit) {
        float r = radscl * radius;
        if (r <= 0.001f) return;

        Draw.color(unit.type.shieldColor(unit), Color.white, Mathf.clamp(hit));

        if (renderer.animateShields) {
            Draw.z(FluxBarrier.layerFluxShield + 0.001f * hit);
            Fill.poly(unit.x, unit.y, sides, r, rotation);
        } else {
            Draw.z(FluxBarrier.layerFluxShield);
            Lines.stroke(1.5f);
            Draw.alpha(0.09f + Mathf.clamp(0.08f * hit));
            Fill.poly(unit.x, unit.y, sides, r, rotation);
            Draw.alpha(1f);
            Lines.poly(unit.x, unit.y, sides, r, rotation);
            Draw.reset();
        }

        Draw.reset();
    }

    @Override
    public void displayBars(Unit unit, Table bars) {
        bars.add(new Bar(
                "stat.temperature",
                Pal.lightishOrange,
                () -> shieldHeat / maxShieldHeat
        )).row();
    }
}