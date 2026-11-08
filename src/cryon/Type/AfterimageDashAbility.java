package cryon.Type;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.Vec2;
import arc.util.Time;
import cryon.Features.MapAlliances;
import cryon.Features.MechAudio;
import mindustry.Vars;
import mindustry.content.StatusEffects;
import mindustry.entities.Units;
import mindustry.entities.abilities.Ability;
import mindustry.gen.*;
import mindustry.graphics.Layer;

/** A short automatic ground dash, using native movement collisions and local chromatic echoes. */
public class AfterimageDashAbility extends Ability{
    public final float duration = 12, reload = 180, maxDistance = 48, range = 144;
    private float cooldown, remaining, distanceLeft, facing, fade, sampleTime, windup, settle;
    private float anchorX, anchorY;
    private boolean burstSoundPlayed, brakeSoundPlayed;
    public static final float windupDuration = 3, settleDuration = 4, fadeDuration = 18;
    private Teamc target;
    private float[] trailX = new float[8], trailY = new float[8], trailRotation = new float[8], trailBlade = new float[8];
    private int trailHead, trailCount;
    private Vec2 ghostShoulder=new Vec2(),ghostWrist=new Vec2(),ghostElbow=new Vec2();
    private static final Color[] echoes = {Color.valueOf("80ffd1"), Color.valueOf("70faff"), Color.valueOf("a187ff"), Color.valueOf("ff5aae")};

    @Override public Ability copy(){
        AfterimageDashAbility copy = (AfterimageDashAbility)super.copy();
        copy.trailX = new float[8]; copy.trailY = new float[8];
        copy.trailRotation = new float[8]; copy.trailBlade = new float[8];
        copy.trailHead = copy.trailCount = 0;
        copy.ghostShoulder=new Vec2();copy.ghostWrist=new Vec2();copy.ghostElbow=new Vec2();
        copy.cooldown = copy.remaining = copy.fade = copy.sampleTime = copy.windup = copy.settle = 0;
        copy.target = null;
        copy.burstSoundPlayed = copy.brakeSoundPlayed = false;
        return copy;
    }

    public boolean active(){ return remaining > 0; }
    public float cooldown(){ return cooldown; }
    public int trailCount(){ return trailCount; }
    public boolean windingUp(){ return windup > 0; }
    public boolean settling(){ return settle > 0; }

    @Override public void init(mindustry.type.UnitType type){
        type.clipSize = Math.max(type.clipSize, (maxDistance + type.range + type.hitSize) * 2);
    }

    public static float chroma(Unit unit){
        float amount = 0;
        for(Ability ability : unit.abilities) if(ability instanceof AfterimageDashAbility){
            AfterimageDashAbility dash = (AfterimageDashAbility)ability;
            amount = Math.max(amount, dash.windup > 0 ? 0.75f * (1 - dash.windup / windupDuration)
                : dash.active() ? 1f : Mathf.clamp(dash.fade / fadeDuration));
        }else if(ability instanceof ExtremeOverclockAbility){
            amount = Math.max(amount, ((ExtremeOverclockAbility)ability).intensity(unit));
        }
        return amount;
    }

    @Override public String localized(){ return Core.bundle.get("ability.supersonic-dash"); }
    @Override public void addStats(arc.scene.ui.layout.Table table){
        table.add(Core.bundle.get("ability.supersonic-dash.description")).wrap().width(320).row();
    }

    private boolean blocked(Unit unit){
        return unit.dead || !unit.isAdded() || !unit.canShoot() || unit.speedMultiplier <= 0.01f
            || unit.hasEffect(StatusEffects.unmoving);
    }

    @Override public void update(Unit unit){
        cooldown = Math.max(0, cooldown - Time.delta);
        fade = Math.max(0, fade - Time.delta);
        if(blocked(unit)){
            if(active()) finish(unit);
            remaining = windup = settle = 0;
            return;
        }
        if(settle > 0){
            holdPosition(unit);
            settle = Math.max(0, settle - Time.delta);
            return;
        }
        if(!active()){
            if(cooldown > 0) return;
            target = Units.closestTarget(unit.team, unit.x, unit.y, range,
                other -> !other.isFlying() && !MapAlliances.allied(unit.team, other.team),
                build -> !MapAlliances.allied(unit.team, build.team));
            if(target != null && !unit.within(target, unit.type.range * 0.85f + 8)){
                facing = unit.angleTo(target);
                distanceLeft = Math.min(maxDistance, unit.dst(target) - unit.type.range * 0.7f - 8);
            }else{
                // Ordinary player/AI walking can use the same burst without an enemy.
                if(unit.vel.len2() < 0.15f * 0.15f) return;
                facing = unit.vel.angle();
                target = null;
                distanceLeft = maxDistance;
            }
            remaining = duration;
            if(distanceLeft < 12){ remaining = 0; return; }
            cooldown = reload;
            windup = windupDuration;
            burstSoundPlayed = brakeSoundPlayed = false;
            MechAudio.play(MechAudio.Cue.dashCharge, unit, unit.x, unit.y,
                ((EnergyBladeWeapon)unit.mounts[0].weapon).bladeScale);
            anchorX = unit.x; anchorY = unit.y;
            trailHead = trailCount = 0;
            sampleTime = 0;
            record(unit);
        }
        // Controllers run after abilities and may refill velocity every frame. Undo that
        // ordinary walking here so charge/brake pauses and burst distance remain real.
        holdPosition(unit);
        if(target != null && (Units.invalidateTarget(target, unit.team, unit.x, unit.y, range + maxDistance)
                || MapAlliances.allied(unit.team, target.team()))){
            finish(unit);
            return;
        }
        float delta = Time.delta;
        if(windup > 0){
            float pause = Math.min(windup, delta);
            windup -= pause; delta -= pause;
            if(!Vars.net.client() || unit.isLocal()) unit.vel.setZero();
            fade = fadeDuration;
            if(delta <= 0) return;
        }
        float travel = Math.min(distanceLeft, maxDistance / duration * Math.min(delta, remaining));
        if(!burstSoundPlayed && travel > 0){
            burstSoundPlayed = true;
            MechAudio.play(MechAudio.Cue.dashBurst, unit, unit.x, unit.y,
                ((EnergyBladeWeapon)unit.mounts[0].weapon).bladeScale);
        }
        if(!Vars.net.client() || unit.isLocal()){
            unit.vel.setZero();
            // Substeps also protect against tunnelling when a frame has a large delta.
            int steps = Math.max(1, (int)Math.ceil(travel / 2f));
            float step = travel / steps;
            for(int i = 0; i < steps; i++){
                float dx = Angles.trnsx(facing, step), dy = Angles.trnsy(facing, step);
                float margin = unit.hitSize / 2;
                if(unit.x + dx < margin || unit.y + dy < margin
                    || unit.x + dx > Vars.world.unitWidth() - margin || unit.y + dy > Vars.world.unitHeight() - margin){
                    finish(unit); break;
                }
                float x = unit.x, y = unit.y;
                unit.move(dx, dy);
                float moved = Mathf.dst(x, y, unit.x, unit.y);
                distanceLeft -= moved;
                if(moved < step * 0.85f){ finish(unit); break; }
            }
        }else{
            // Remote positions use the engine's snapshots; only the visual timer runs here.
            distanceLeft -= travel;
        }
        sampleTime += delta;
        if(sampleTime >= 2){ sampleTime %= 2; record(unit); }
        fade = fadeDuration;
        anchorX = unit.x; anchorY = unit.y;
        remaining = Math.max(0, remaining - delta);
        if(remaining <= 0 || distanceLeft <= 0.01f || (target != null && unit.within(target, unit.type.range * 0.7f + 8))) finish(unit);
    }

    private void finish(Unit unit){
        if(burstSoundPlayed && !brakeSoundPlayed){
            brakeSoundPlayed = true;
            MechAudio.play(MechAudio.Cue.dashBrake, unit, unit.x, unit.y,
                ((EnergyBladeWeapon)unit.mounts[0].weapon).bladeScale);
        }
        remaining = windup = 0;
        settle = settleDuration;
        fade = fadeDuration;
        anchorX = unit.x; anchorY = unit.y;
        if(!Vars.net.client() || unit.isLocal()) unit.vel.setZero();
    }

    private void holdPosition(Unit unit){
        if(!Vars.net.client() || unit.isLocal()){
            unit.move(anchorX - unit.x, anchorY - unit.y);
            unit.vel.setZero();
        }
    }

    private void record(Unit unit){
        trailX[trailHead] = unit.x; trailY[trailHead] = unit.y;
        trailRotation[trailHead] = unit.rotation;
        EnergyBladeWeapon.BladeMount blade = (EnergyBladeWeapon.BladeMount)unit.mounts[0];
        trailBlade[trailHead] = unit.rotation + EnergyBladeWeapon.poseAngle(blade, blade.age);
        trailHead = (trailHead + 1) % trailX.length;
        trailCount = Math.min(trailCount + 1, trailX.length);
    }

    @Override public void draw(Unit unit){
        if(fade <= 0 || trailCount == 0) return;
        float z = Draw.z(), strength = Mathf.clamp(fade / fadeDuration);
        EnergyBladeWeapon weapon = (EnergyBladeWeapon)unit.mounts[0].weapon;
        float scale = weapon.bladeScale;
        Draw.z(Layer.groundUnit - 0.03f);
        for(int age = trailCount - 1; age >= 0; age--){
            int index = (trailHead - age - 1 + trailX.length) % trailX.length;
            Color tint = echoes[age % echoes.length];
            float alpha = 0.7f * strength * (1 - age / (float)(trailX.length + 1));
            Draw.color(Color.white, alpha); Draw.mixcol(tint, 1);
            Draw.rect(unit.type.baseRegion, trailX[index], trailY[index], trailRotation[index] - 90);
            // Keep arms and the shared hilt in each silhouette, so echoes read as full mechs.
            float bladeAngle = trailBlade[index], bodyAngle = trailRotation[index];
            for(int side=-1;side<=1;side+=2){
                Vec2 shoulder = ghostShoulder.trns(bodyAngle, 0, side * 5 * scale).add(trailX[index], trailY[index]);
                Vec2 wrist = ghostWrist.trns(bladeAngle, (side == 1 ? 9 : 5) * scale).add(trailX[index], trailY[index]);
                Vec2 joint = EnergyBladeWeapon.elbow(shoulder, wrist, side, 7 * scale,ghostElbow);
                ghostArm(weapon.upperArmRegion, shoulder, joint, side, scale);
                ghostArm(weapon.forearmRegion, joint, wrist, side, scale);
            }
            Draw.rect(unit.type.region, trailX[index], trailY[index], trailRotation[index] - 90);
            Draw.rect(weapon.hiltRegion, trailX[index] + Angles.trnsx(bladeAngle, 7 * scale),
                trailY[index] + Angles.trnsy(bladeAngle, 7 * scale), weapon.hiltRegion.width * Draw.scl * scale,
                weapon.hiltRegion.height * Draw.scl * scale, bladeAngle - 90);
            Draw.mixcol(); Draw.color(tint, alpha);
            Lines.stroke(2.5f * scale);
            Lines.lineAngle(trailX[index] + Angles.trnsx(trailBlade[index], 11 * scale),
                trailY[index] + Angles.trnsy(trailBlade[index], 11 * scale), trailBlade[index], 18 * scale);
        }
        Draw.reset(); Draw.z(z);
    }

    private void ghostArm(TextureRegion region, Vec2 start, Vec2 end, int side, float scale){
        Draw.rect(region, (start.x + end.x) / 2, (start.y + end.y) / 2,
            region.width * Draw.scl * scale * side, start.dst(end) + 2 * scale, start.angleTo(end) - 90);
    }
}
