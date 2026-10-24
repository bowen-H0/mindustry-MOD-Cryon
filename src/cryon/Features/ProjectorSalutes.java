package cryon.Features;

import arc.Events;
import arc.math.*;
import arc.struct.ObjectMap;
import arc.util.Time;
import mindustry.Vars;
import mindustry.ai.types.CommandAI;
import mindustry.ai.types.LogicAI;
import mindustry.entities.Units;
import mindustry.entities.units.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;

/** Client-side ceremony: temporarily pose idle units only during rendering. */
public class ProjectorSalutes{
    public static final float radius = 96f;
    private static final float cooldown = 900f;
    private static final ObjectMap<Unit, Salute> salutes = new ObjectMap<>();
    private static boolean initialized;

    public static void init(){
        if(initialized || Vars.headless) return;
        initialized = true;
        Events.run(Trigger.preDraw, () -> pose());
        Events.run(Trigger.postDraw, () -> restore());
        Events.on(ResetEvent.class, event -> {
            restore();
            salutes.clear();
        });
    }

    public static void offer(Building source){
        if(Vars.headless || !source.enabled || source.efficiency <= 0.001f) return;
        Units.nearby(source.team, source.x, source.y, radius, unit -> {
            if(!unit.within(source, radius) || !idle(unit)) return;
            Salute salute = salutes.get(unit);
            if(salute == null){
                salute = new Salute(unit, source);
                salutes.put(unit, salute);
            }
            // A unit can only salute one projector at once.
            if(salute.source == source) salute.lastSeen = Time.time;
        });
    }

    private static boolean idle(Unit unit){
        if(!unit.isAdded() || unit.dead || unit.isPlayer()
            || !(unit.controller() instanceof AIController)
            || unit.controller() instanceof LogicAI
            || unit.vel.len2() > 0.0025f || unit.isShooting
            || unit.mineTile != null || !unit.plans.isEmpty()) return false;
        if(unit.controller() instanceof CommandAI){
            CommandAI ai = (CommandAI)unit.controller();
            if(ai.hasCommand() || ai.attackTarget != null || ai.targetPos != null
                || !ai.commandQueue.isEmpty()) return false;
        }
        for(WeaponMount mount : unit.mounts){
            if(mount.shoot || mount.charging || mount.target != null || mount.bullet != null) return false;
        }
        // Don't start a ceremony when combat is about to begin.
        return Units.closestTarget(unit.team, unit.x, unit.y, Math.max(unit.range(), 120f)) == null;
    }

    private static void pose(){
        float now = Time.time;
        var iterator = salutes.entries();
        while(iterator.hasNext()){
            var entry = iterator.next();
            Unit unit = entry.key;
            Salute s = entry.value;
            if(!unit.isAdded() || unit.mounts.length != s.mountRotations.length || !s.source.isValid() || unit.team != s.source.team
                || !unit.within(s.source, radius) || now - s.lastSeen > 35f){
                iterator.remove();
                continue;
            }
            if(!s.source.enabled || s.source.efficiency <= 0.001f || !idle(unit)){
                if(s.start >= 0f) s.next = now + cooldown;
                s.start = -1f;
                s.idleSince = now;
                continue;
            }
            if(s.start < 0f){
                if(now < s.next || now - s.idleSince < 45f) continue;
                s.start = now;
                s.lastPoseTime = now;
                s.stage = 0;
                s.visualRotation = unit.rotation;
                for(int i = 0; i < unit.mounts.length; i++){
                    s.visualMountRotations[i] = unit.mounts[i].rotation;
                }
            }
            float dt = Math.max(0f, now - s.lastPoseTime);
            s.lastPoseTime = now;
            float facing = unit.angleTo(s.source);
            s.rotation = unit.rotation;
            s.posed = true;
            boolean reached = true;
            if(unit instanceof Tankc){
                for(int i = 0; i < unit.mounts.length; i++){
                    WeaponMount mount = unit.mounts[i];
                    s.mountRotations[i] = mount.rotation;
                    if(!mount.weapon.rotate) continue;
                    float target = s.stage == 2 ? mount.rotation : facing - unit.rotation;
                    if(mount.weapon.rotationLimit < 360f){
                        target = mount.weapon.baseRotation + Mathf.clamp(
                            (Mathf.mod(target - mount.weapon.baseRotation + 180f, 360f) - 180f),
                            -mount.weapon.rotationLimit / 2f, mount.weapon.rotationLimit / 2f);
                    }
                    // Same per-tick limit as Weapon.update; never force a timed interpolation.
                    s.visualMountRotations[i] = Angles.moveToward(s.visualMountRotations[i], target,
                        Math.max(0f, mount.weapon.rotateSpeed) * dt);
                    mount.rotation = s.visualMountRotations[i];
                    reached &= Angles.angleDist(mount.rotation, target) < 0.1f;
                }
            }else{
                float target = s.stage == 2 ? s.rotation : facing;
                // Match Unit.lookAt, including status effects on movement/turning speed.
                s.visualRotation = Angles.moveToward(s.visualRotation, target,
                    Math.max(0f, unit.type.rotateSpeed * unit.speedMultiplier()) * dt);
                unit.rotation = s.visualRotation;
                reached = Angles.angleDist(unit.rotation, target) < 0.1f;
            }
            if(s.stage == 0 && reached){
                s.stage = 1;
                s.holdSince = now;
            }else if(s.stage == 1 && now - s.holdSince >= 45f){
                s.stage = 2;
            }else if(s.stage == 2 && reached){
                s.start = -1f;
                s.next = now + cooldown;
            }
        }
    }

    private static void restore(){
        for(var entry : salutes){
            Salute s = entry.value;
            if(!s.posed) continue;
            entry.key.rotation = s.rotation;
            if(entry.key instanceof Tankc){
                for(int i = 0; i < Math.min(entry.key.mounts.length, s.mountRotations.length); i++){
                    entry.key.mounts[i].rotation = s.mountRotations[i];
                }
            }
            s.posed = false;
        }
    }

    private static class Salute{
        final Building source;
        final float[] mountRotations, visualMountRotations;
        float start = -1f, next, idleSince = Time.time, lastSeen = Time.time, rotation;
        float visualRotation, lastPoseTime, holdSince;
        int stage;
        boolean posed;
        Salute(Unit unit, Building source){
            this.source = source;
            mountRotations = new float[unit.mounts.length];
            visualMountRotations = new float[unit.mounts.length];
        }
    }
}
