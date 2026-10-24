package cryon.Type;

import arc.Core;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Time;
import cryon.Features.SpaceJump;
import cryon.Fx.SpaceJumpFx;
import mindustry.Vars;
import mindustry.ai.UnitCommand;
import mindustry.ai.UnitStance;
import mindustry.ai.types.CommandAI;
import mindustry.entities.abilities.Ability;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import cryon.Features.SpaceJumpAudio;
import mindustry.graphics.Trail;
import mindustry.type.UnitType.UnitEngine;
import mindustry.ui.Bar;

/** Server-authoritative turn -> charge -> hop, followed by digital reconstruction for large hulls. */
public class SpaceJumpAbility extends Ability{
    public float reload = 60f * 5f;
    public float minimumTimeSaved = 120f;
    public float maximumJumpTimeRatio = 0.5f;
    public float maxDistance = 8f * 120f;
    public float minimumJumpDistance = 8f * 40f;
    private float cooldown, charge, originX, originY, targetX, targetY, facing, reconstruction;
    private boolean active, automatic, finishing;
    private int level;
    private Trail[] engineTrails;

    /** Use hull maximum health so damage does not change a ship's visual class mid-charge. */
    public static int level(Unit unit){
        float health = unit.maxHealth;
        return health >= 16000f ? 5 : health >= 8000f ? 4 : health >= 2000f ? 3 : health >= 500f ? 2 : 1;
    }

    public boolean active(){ return active; }
    public boolean distantEnough(Unit unit, Vec2 destination){
        float minimum = Math.max(8f, minimumJumpDistance);
        return unit.dst2(destination) >= minimum * minimum;
    }
    public void cancelManual(Unit unit){ if(active && !automatic) cancel(unit); }
    public boolean reservesLanding(){ return active || reconstructing() || finishing; }
    public float landingX(){ return targetX; }
    public float landingY(){ return targetY; }
    public static float reconstructionTime(int level){ return level >= 5 ? 72f : level == 4 ? 54f : 0f; }
    public boolean reconstructing(){ return reconstruction > 0f; }
    public boolean digitalizing(){ return level >= 4 && (active || reconstructing()); }
    public boolean ready(Unit unit){ return SpaceJump.allowed(unit) && !active && !reconstructing() && cooldown <= 0f && !unit.dead && unit.isFlying(); }
    public float chargeProgress(){ return active ? Mathf.clamp(charge / chargeTime()) : 0f; }
    public float chargeTime(){ return 45f + level * 18f; }

    @Override
    public String localized(){ return Core.bundle.get("ability.cryon-space-jump"); }

    @Override
    public void addStats(arc.scene.ui.layout.Table table){
        table.add(Core.bundle.get("ability.cryon-space-jump.description")).wrap().width(350f);
    }

    @Override
    public void displayBars(Unit unit, arc.scene.ui.layout.Table bars){
        if(!SpaceJump.enabled() || !SpaceJump.allowed(unit)) return;
        bars.add(new Bar(() -> active ? Core.bundle.get("cryon.space-jump.charging") : localized(),
            () -> SpaceJumpFx.color, () -> active ? chargeProgress() : 1f - cooldown / reload)).row();
    }

    @Override
    public void update(Unit unit){
        cooldown = Math.max(0f, cooldown - Time.delta);
        if(!SpaceJump.enabled() || !SpaceJump.allowed(unit) || unit.dead || !unit.isFlying()){
            cancel(unit);
            reconstruction = 0f;
            return;
        }
        if(reconstructing()){
            reconstruction = Math.max(0f, reconstruction - Time.delta);
            unit.set(targetX, targetY); unit.rotation = facing; unit.vel.setZero();
            finishing = true;
            return;
        }
        if(active){
            if(!Vars.net.client() && automatic && !canAutoJump(unit)){
                cancel(unit);
                return;
            }
            // Anchor during spool-up; use precisely the unit's normal turn rate, including statuses.
            unit.set(originX, originY);
            unit.vel.setZero();
            if(!Vars.net.client()){
                Vec2 safe = SpaceJumpLanding.outsideEnemyFlux(unit, new Vec2(targetX, targetY));
                if(safe != null && distantEnough(unit, safe)) safe = SpaceJumpFormation.plan(unit, safe, maxDistance, Math.max(8f, minimumJumpDistance), new Vec2());
                if(safe == null || !distantEnough(unit, safe)){
                    cancel(unit);
                    return;
                }
                if(Mathf.dst2(targetX, targetY, safe.x, safe.y) > 0.01f){
                    if(automatic && !worthwhile(unit, safe, chargeTime() - charge)){
                        cancel(unit);
                        return;
                    }
                    redirect(safe.x, safe.y);
                    SpaceJump.syncRedirect(unit, targetX, targetY);
                }
            }
            facing = Angles.moveToward(facing, Angles.angle(originX, originY, targetX, targetY),
                unit.type.rotateSpeed * unit.speedMultiplier() * Time.delta);
            unit.rotation = facing;
            if(Angles.angleDist(facing, Angles.angle(originX, originY, targetX, targetY)) <= 2f){
                charge = Math.min(chargeTime(), charge + Time.delta);
            }
            updateTrails(unit);
            if(!Vars.net.client() && charge >= chargeTime()) complete(unit);
            return;
        }
        if(Vars.net.client() || cooldown > 0f || !canAutoJump(unit)) return;
        CommandAI ai = (CommandAI)unit.controller();
        float x, y;
        if(ai.attackTarget != null){
            x = ai.attackTarget.x(); y = ai.attackTarget.y();
            float distance = unit.dst(x, y);
            float stop = Math.max(unit.hitSize, unit.range() * 0.8f);
            if(distance <= stop) return;
            float fraction = (distance - stop) / distance;
            x = unit.x + (x - unit.x) * fraction;
            y = unit.y + (y - unit.y) * fraction;
        }else if(ai.targetPos != null){
            x = ai.targetPos.x; y = ai.targetPos.y;
        }else return;
        if(worthwhile(unit, x, y) && requestJump(unit, x, y)) automatic = true;
    }

    /** Compare the actual, range/flux-limited hop with flying that same displacement. */
    public boolean worthwhile(Unit unit, float x, float y){
        Vec2 destination = landing(unit, x, y, new Vec2());
        return destination != null && distantEnough(unit, destination) && worthwhile(unit, destination, 45f + level(unit) * 18f);
    }

    private boolean worthwhile(Unit unit, Vec2 destination, float chargeRemaining){
        float speed = Math.max(unit.speed(), unit.vel.len());
        if(speed <= 0.001f) return false;
        float angle = Math.max(0f, Angles.angleDist(unit.rotation, unit.angleTo(destination)) - 2f);
        float turnSpeed = unit.type.rotateSpeed * unit.speedMultiplier();
        if(angle > 0f && turnSpeed <= 0.001f) return false;
        float turnTime = angle <= 0f ? 0f : angle / turnSpeed;
        float flightTime = unit.dst(destination) / speed;
        float jumpTime = turnTime + Math.max(0f, chargeRemaining) + reconstructionTime(level(unit));
        return jumpTime <= flightTime * maximumJumpTimeRatio
            && flightTime - jumpTime >= minimumTimeSaved;
    }

    private boolean canAutoJump(Unit unit){
        if(!(unit.controller() instanceof CommandAI)) return false;
        CommandAI ai = (CommandAI)unit.controller();
        return !ai.hasStance(UnitStance.patrol) && !ai.hasStance(UnitStance.holdPosition)
            && (ai.currentCommand() == UnitCommand.moveCommand || ai.currentCommand() == UnitCommand.loopPayloadCommand)
            && ai.hasCommand();
    }

    /** Accept an order; movement happens only after alignment and charging finish. */
    public boolean jump(Unit unit, float x, float y){
        return SpaceJump.manualEnabled() && requestJump(unit, x, y);
    }

    private boolean requestJump(Unit unit, float x, float y){
        if(!SpaceJump.enabled() || !SpaceJump.allowed(unit) || Vars.net.client() || active || reconstructing() || cooldown > 0f || unit.dead || !unit.isFlying()
            || !Float.isFinite(x) || !Float.isFinite(y)) return false;
        Vec2 landing = landing(unit, x, y, new Vec2());
        if(landing == null || !distantEnough(unit, landing)) return false;
        begin(unit, landing.x, landing.y, unit.x, unit.y, unit.rotation, level(unit));
        SpaceJump.syncStart(unit, targetX, targetY, originX, originY, facing, level);
        return true;
    }

    /** Shared by preview and server execution so the displayed landing obeys the same limits. */
    public Vec2 landing(Unit unit, float x, float y, Vec2 out){
        if(!Float.isFinite(x) || !Float.isFinite(y)) return null;
        float margin = Math.max(4f, unit.hitSize / 2f);
        x = Mathf.clamp(x, margin, Math.max(margin, Vars.world.unitWidth() - margin));
        y = Mathf.clamp(y, margin, Math.max(margin, Vars.world.unitHeight() - margin));
        float distance = unit.dst(x, y);
        if(distance < 0.001f) return out.set(unit.x, unit.y);
        float fraction = Math.min(1f, maxDistance / distance);
        x = unit.x + (x - unit.x) * fraction;
        y = unit.y + (y - unit.y) * fraction;
        Vec2 safe = SpaceJumpLanding.outsideEnemyFlux(unit, out.set(x, y));
        return safe == null || !distantEnough(unit, safe) ? safe : SpaceJumpFormation.plan(unit, safe, maxDistance, Math.max(8f, minimumJumpDistance), out);
    }

    public void redirect(float x, float y){
        if(active){ targetX = x; targetY = y; }
    }

    public void begin(Unit unit, float x, float y, float ox, float oy, float rotation, int tier){
        if(!SpaceJump.enabled() || !SpaceJump.allowed(unit)) return;
        targetX = x; targetY = y; originX = ox; originY = oy; facing = rotation;
        level = tier; charge = 0f; reconstruction = 0f; active = true; automatic = false;
        engineTrails = null;
        updateTrails(unit);
    }

    private void complete(Unit unit){
        float rotation = facing;
        finish(unit, targetX, targetY, rotation);
        Player player = unit.getPlayer();
        if(player != null && player.con != null) Call.setPosition(player.con, unit.x, unit.y);
        SpaceJump.sync(unit, rotation);
    }

    public void finish(Unit unit, float x, float y, float rotation){
        if(!SpaceJump.enabled() || !SpaceJump.allowed(unit)) return;
        if(!active){ originX = unit.x; originY = unit.y; level = level(unit); }
        if(!Vars.headless){
            SpaceJumpAudio.fold(unit, originX, originY, x, y, level);
            SpaceJumpFx.fold(unit, originX, originY, x, y, rotation, level, engineTrails);
        }
        targetX = x; targetY = y; facing = rotation;
        active = false; engineTrails = null;
        reconstruction = reconstructionTime(level);
        finishing = true;
        unit.set(x, y); unit.rotation = rotation; unit.vel.setZero();
        unit.snapInterpolation(); unit.snapSync();
        cooldown = reload;
    }

    /** Vanilla controllers run after abilities; restore the locked pose once they have finished. */
    public void afterUpdate(Unit unit){
        if(!SpaceJump.enabled() || unit.dead) return;
        if(active || finishing || reconstructing()){
            unit.set(active ? originX : targetX, active ? originY : targetY);
            unit.rotation = facing;
            unit.vel.setZero();
            finishing = false;
        }
        if(digitalizing() && unit.trail != null) unit.trail.clear();
    }

    public void cancel(Unit unit){
        if(!active) return;
        active = false; charge = 0f; engineTrails = null;
        if(!Vars.net.client()) SpaceJump.syncCancel(unit);
    }

    private void updateTrails(Unit unit){
        if(Vars.headless || level >= 4){ engineTrails = null; return; }
        if(engineTrails == null){
            engineTrails = new Trail[unit.type.engines.size];
            for(int i = 0; i < engineTrails.length; i++) engineTrails[i] = new Trail(40 + level * 8);
        }
        for(int i = 0; i < engineTrails.length; i++){
            UnitEngine engine = unit.type.engines.get(i);
            engineTrails[i].update(originX + Angles.trnsx(facing - 90f, engine.x, engine.y),
                originY + Angles.trnsy(facing - 90f, engine.x, engine.y), 0.4f + chargeProgress());
        }
    }

    @Override
    public void draw(Unit unit){
        if(active && SpaceJump.enabled()) SpaceJumpFx.charge(unit, targetX, targetY, chargeProgress(), level, engineTrails);
    }

    public void drawWarning(Unit unit){
        if(active && SpaceJump.enabled()) SpaceJumpFx.warning(unit, targetX, targetY, chargeProgress(), level);
    }

    @Override
    public Ability copy(){
        SpaceJumpAbility copy = (SpaceJumpAbility)super.copy();
        copy.active = false; copy.automatic = false; copy.finishing = false;
        copy.cooldown = 0; copy.charge = 0; copy.reconstruction = 0; copy.engineTrails = null;
        return copy;
    }
}
