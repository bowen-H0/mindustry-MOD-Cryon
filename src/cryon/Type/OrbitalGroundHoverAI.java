package cryon.Type;

import arc.math.geom.Vec2;
import cryon.Features.MapAlliances;
import mindustry.ai.types.CommandAI;
import mindustry.ai.types.FlyingAI;
import mindustry.entities.Units;
import mindustry.gen.*;

/** Flies to a ground target's projected overhead station instead of strafing beside it. */
public class OrbitalGroundHoverAI extends FlyingAI{
    private final Vec2 station = new Vec2();
    private boolean reacquire = true;

    public static boolean inTransit(Unit unit){
        for(var ability : unit.abilities) if(ability instanceof SpaceJumpAbility jump && jump.reservesLanding()) return true;
        return false;
    }

    public static void clearBeam(Unit unit){
        unit.controlWeapons(false, false);
        for(var mount : unit.mounts){
            if(!(mount.weapon.bullet instanceof DownwardBurnBeamBulletType)) continue;
            if(mount.bullet != null){ mount.bullet.remove(); mount.bullet = null; }
            mount.target = null;
            mount.shoot = mount.rotate = false;
        }
    }

    /** Arrival invalidates source-location combat state without discarding subsequent movement orders. */
    public static void arrived(Unit unit){
        clearBeam(unit);
        if(unit.controller() instanceof OrbitalGroundHoverAI ai){
            ai.target = ai.bomberTarget = null;
            ai.reacquire = true;
        }else if(unit.controller() instanceof Commands ai){
            ai.onArrival();
        }
    }

    private static Teamc groundTarget(Unit owner, float x, float y, float range){
        return Units.closestTarget(owner.team, x, y, range,
            candidate -> !candidate.isFlying() && !(candidate.type instanceof OrbitalUnitType)
                && !MapAlliances.allied(owner.team, candidate.team),
            building -> !MapAlliances.allied(owner.team, building.team));
    }

    private static boolean keepTarget(Unit owner, Teamc candidate, float x, float y, float range){
        if(candidate == null || MapAlliances.allied(owner.team, candidate.team())
            || Units.invalidateTarget(candidate, owner.team, x, y, range)) return false;
        return candidate instanceof Building || candidate instanceof Unit unit
            && !unit.isFlying() && !(unit.type instanceof OrbitalUnitType);
    }

    private static void aimNow(Unit owner){
        for(var mount : owner.mounts) if(mount.target != null){
            mount.aimX = mount.target.getX();
            mount.aimY = mount.target.getY();
            owner.aimX = mount.aimX;
            owner.aimY = mount.aimY;
        }
    }

    @Override public void updateUnit(){
        if(mindustry.Vars.net.client() || unit.dead) return;
        if(inTransit(unit)){ unit.controlWeapons(false, false); return; }
        var scope = MapAlliances.enter(unit);
        try{ super.updateUnit(); }finally{ MapAlliances.exit(scope); }
    }
    @Override public boolean retarget(){
        if(reacquire){ reacquire = false; return true; }
        return super.retarget();
    }
    @Override public Teamc findTarget(float x, float y, float range, boolean air, boolean ground){
        return keepTarget(unit, target, x, y, range) ? target : groundTarget(unit, x, y, range);
    }
    @Override public void updateWeapons(){
        super.updateWeapons();
        aimNow(unit);
    }
    @Override public void updateMovement(){
        if(target != null && unit.type.weapons.first().bullet instanceof DownwardBurnBeamBulletType beam){
            beam.hoverPoint(unit, target, station);
            moveTo(station, 4f, 24f, false, null);
        }
    }

    public static class Commands extends CommandAI{
        private final Vec2 station = new Vec2();
        private boolean reacquire = true;
        private void onArrival(){
            target = bomberTarget = null;
            reacquire = true;
            if(currentCommand() == cryon.Features.SpaceJump.jumpCommand){
                command(mindustry.ai.UnitCommand.moveCommand);
                if(attackTarget == null && targetPos != null
                    && unit.within(targetPos, SpaceJumpFormation.radius(unit) + 8f)){
                    targetPos = null;
                    lastTargetPos = null;
                }
            }
        }
        @Override public boolean retarget(){
            if(reacquire){ reacquire = false; return true; }
            return super.retarget();
        }
        @Override public Teamc findTarget(float x, float y, float range, boolean air, boolean ground){
            if(keepTarget(unit, attackTarget, x, y, range)) return attackTarget;
            return keepTarget(unit, target, x, y, range) ? target : groundTarget(unit, x, y, range);
        }
        @Override public void updateWeapons(){
            super.updateWeapons();
            aimNow(unit);
        }
        @Override public void updateUnit(){
            if(mindustry.Vars.net.client() || unit.dead) return;
            if(inTransit(unit)){ unit.controlWeapons(false, false); return; }
            var scope = MapAlliances.enter(unit);
            try{
                super.updateUnit();
                // Explicit move orders take priority. Idle/attack orders allow automatic overhead support.
                if(commandController == null && (targetPos == null || attackTarget != null) && target != null
                    && unit.isShooting && unit.type.weapons.first().bullet instanceof DownwardBurnBeamBulletType beam){
                    beam.hoverPoint(unit, target, station);
                    moveTo(station, 4f, 24f, false, null);
                }
            }finally{ MapAlliances.exit(scope); }
        }
    }
}
