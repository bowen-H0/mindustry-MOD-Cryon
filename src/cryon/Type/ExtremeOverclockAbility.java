package cryon.Type;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.Time;
import cryon.Content.ShadowMech;
import cryon.Features.*;
import mindustry.Vars;
import mindustry.ai.types.GroundAI;
import mindustry.ai.types.CommandAI;
import mindustry.entities.*;
import mindustry.entities.abilities.Ability;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.UnitType;

/** Temporary self-only burst, with actual faster sensing and no shared UnitType mutation. */
public class ExtremeOverclockAbility extends Ability{
    public static final float timeScale = 3f;
    public final float duration = 180, reload = 480, perceptionRange = 240;
    private float cooldown, scan, fade, sample;
    private Teamc focus;
    private float[] xs = new float[7], ys = new float[7], rotations = new float[7];
    private int head, count;
    public static final Color cyan = Color.valueOf("84fff0"), violet = Color.valueOf("b58aff");
    public static final Effect ignition = new Effect(30, 320, e -> {
        Draw.color(cyan, violet, e.fin());
        Lines.stroke(2.8f * e.fout());
        for(int side : new int[]{0, 180}) Lines.arc(e.x, e.y, 10 + e.finpow() * 60, 0.27f, e.rotation + side);
        Angles.randLenVectors(e.id, 12, 6 + e.finpow() * 65, (x, y) ->
            Drawf.tri(e.x + x, e.y + y, 2 * e.fout(), 10 * e.fout(), Mathf.angle(x, y)));
        Drawf.light(e.x, e.y, 85 * e.fout(), cyan, 0.65f * e.fout());
        Draw.reset();
    });

    public static boolean enhanced(Unit unit){
        return ShadowMech.extremeOverclock != null && unit.hasEffect(ShadowMech.extremeOverclock);
    }
    public float cooldown(){ return cooldown; }
    public float intensity(Unit unit){ return enhanced(unit) ? 0.85f + Mathf.absin(Time.time, 2, 0.15f) : fade / 15f; }

    @Override public Ability copy(){
        ExtremeOverclockAbility value = (ExtremeOverclockAbility)super.copy();
        value.xs = new float[7]; value.ys = new float[7]; value.rotations = new float[7];
        value.head = value.count = 0; value.cooldown = value.scan = value.fade = value.sample = 0; value.focus = null;
        return value;
    }
    @Override public void init(UnitType type){ type.clipSize = Math.max(type.clipSize, perceptionRange * 2 + 50); }
    @Override public String localized(){ return Core.bundle.get("ability.extreme-overclock"); }
    @Override public void addStats(arc.scene.ui.layout.Table table){
        table.add(Core.bundle.get("ability.extreme-overclock.description")).wrap().width(330).row();
    }

    private Teamc sense(Unit unit){
        return Units.closestTarget(unit.team, unit.x, unit.y, perceptionRange,
            enemy -> !enemy.isFlying() && !MapAlliances.allied(unit.team, enemy.team),
            build -> !MapAlliances.allied(unit.team, build.team));
    }

    @Override public void update(Unit unit){
        cooldown = Math.max(0, cooldown - Time.delta);
        fade = Math.max(0, fade - Time.delta);
        if(unit.dead || !unit.isAdded()) return;
        scan -= Time.delta;
        if(scan <= 0){ focus = sense(unit); scan = enhanced(unit) ? 15f / timeScale : 15f; }
        if(cooldown <= 0 && !enhanced(unit) && focus != null
            && !Units.invalidateTarget(focus, unit.team, unit.x, unit.y, perceptionRange)
            && !MapAlliances.allied(unit.team, focus.team()) && unit.canShoot() && unit.speedMultiplier > 0.01f){
            unit.apply(ShadowMech.extremeOverclock, duration);
            cooldown = reload;
            head = count = 0;
            ignition.at(unit.x, unit.y, unit.rotation, cyan);
            MechAudio.play(MechAudio.Cue.dashBurst, unit, unit.x, unit.y, 2.8f);
        }
        if(!enhanced(unit)) return;
        fade = 15;
        // Native lookAt already multiplies turning by the status speed multiplier.
        sample += Time.delta;
        if(sample >= 2){
            sample %= 2;
            xs[head] = unit.x; ys[head] = unit.y; rotations[head] = unit.rotation;
            head = (head + 1) % xs.length; count = Math.min(count + 1, xs.length);
        }
    }

    @Override public void draw(Unit unit){
        float strength = intensity(unit);
        if(strength <= 0) return;
        float z = Draw.z();
        Draw.z(Layer.groundUnit - 0.02f);
        for(int age = count - 1; age >= 0; age--){
            int index = (head - age - 1 + xs.length) % xs.length;
            Draw.color(Color.white, strength * 0.48f * (1 - age / 8f));
            Draw.mixcol(age % 2 == 0 ? cyan : violet, 1);
            Draw.rect(unit.type.region, xs[index], ys[index], rotations[index] - 90);
        }
        Draw.mixcol(); Draw.z(Layer.effect);
        for(int i = 0; i < 3; i++){
            Draw.color(i % 2 == 0 ? cyan : violet, 0.65f * strength);
            Lines.stroke((1.4f - i * 0.25f) * strength);
            Lines.arc(unit.x, unit.y, unit.hitSize * 0.7f + i * 3, 0.16f,
                Time.time * (i % 2 == 0 ? 3 : -2.5f) + i * 120);
        }
        if(enhanced(unit) && focus != null && !Units.invalidateTarget(focus, unit.team, unit.x, unit.y, perceptionRange)
            && !MapAlliances.allied(unit.team, focus.team())
            && (!(focus instanceof Unit) || !((Unit)focus).inFogTo(unit.team))){
            Draw.color(cyan, strength * 0.75f); Lines.stroke(1);
            float radius = focus instanceof Unit ? ((Unit)focus).hitSize * 0.6f + 4 : 15;
            for(int i = 0; i < 4; i++){
                float angle = i * 90 + Time.time * 0.5f;
                float x = focus.x() + Angles.trnsx(angle, radius), y = focus.y() + Angles.trnsy(angle, radius);
                Lines.lineAngle(x, y, angle + 135, 5); Lines.lineAngle(x, y, angle - 135, 5);
            }
        }
        if(enhanced(unit)){
            Groups.bullet.intersect(unit.x - perceptionRange, unit.y - perceptionRange,
                perceptionRange * 2f, perceptionRange * 2f, bullet -> {
                    if(!bullet.isAdded() || MapAlliances.allied(unit.team, bullet.team)
                        || !unit.within(bullet, perceptionRange)) return;
                    Draw.color(cyan, 0.7f);
                    Lines.stroke(0.8f);
                    Lines.circle(bullet.x, bullet.y, Math.max(2f, bullet.hitSize * 0.65f));
                    Lines.lineAngle(bullet.x, bullet.y, bullet.rotation() + 180f, 8f);
                });
        }
        Drawf.light(unit.x, unit.y, 65, cyan, 0.35f * strength);
        Draw.reset(); Draw.z(z);
    }

    /** Keep native movement, attack, queue and stance commands for player/RTS units. */
    public static class PerceptionCommandAI extends CommandAI{
        @Override public Teamc findTarget(float x, float y, float range, boolean air, boolean ground){
            return super.findTarget(x, y, enhanced(unit) ? Math.max(range, 240) : range, air, ground);
        }
        @Override public boolean checkTarget(Teamc target, float x, float y, float range){
            return super.checkTarget(target, x, y, enhanced(unit) ? Math.max(range, 240) : range);
        }
        @Override public boolean retarget(){ return enhanced(unit) ? timer.get(0, 6) : super.retarget(); }
    }

    /** Autonomous enemy AI; extended awareness does not extend the sword's hit range. */
    public static class PerceptionAI extends GroundAI{
        @Override public Teamc findTarget(float x, float y, float range, boolean air, boolean ground){
            if(!enhanced(unit)) return super.findTarget(x, y, range, air, ground);
            return Units.closestTarget(unit.team, x, y, Math.max(range, 240),
                enemy -> enemy.checkTarget(air, ground) && !MapAlliances.allied(unit.team, enemy.team),
                build -> ground && !MapAlliances.allied(unit.team, build.team));
        }
        @Override public boolean checkTarget(Teamc target, float x, float y, float range){
            return super.checkTarget(target, x, y, enhanced(unit) ? Math.max(range, 240) : range);
        }
        @Override public boolean retarget(){ return enhanced(unit) ? timer.get(0, 6) : super.retarget(); }
        @Override public void updateMovement(){
            if(enhanced(unit) && target != null && !Units.invalidateTarget(target, unit.team, unit.x, unit.y, 240)
                && !MapAlliances.allied(unit.team, target.team())){
                moveTo(target, unit.range() * 0.7f); faceTarget();
            }else super.updateMovement();
        }
    }
}
