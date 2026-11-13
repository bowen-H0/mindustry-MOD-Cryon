package cryon.Type;

import cryon.Features.MapAlliances;
import cryon.Features.OrbitalTargeting;
import mindustry.ai.types.CommandAI;
import mindustry.ai.types.FlyingAI;
import mindustry.entities.Units;
import mindustry.gen.*;

/** Orbit-only autonomous targeting, with the same filter for the native player command controller. */
public class OrbitalCombatAI extends FlyingAI{
    private static Teamc find(Unit owner, float x, float y, float range){
        return Units.closestTarget(owner.team, x, y, range,
            candidate -> candidate.type instanceof OrbitalUnitType && !MapAlliances.allied(owner.team, candidate.team),
            building -> false);
    }

    @Override public void updateUnit(){
        var scope = MapAlliances.enter(unit);
        try{ OrbitalTargeting.run(super::updateUnit); }
        finally{ MapAlliances.exit(scope); }
    }
    @Override public Teamc findTarget(float x, float y, float range, boolean air, boolean ground){
        return find(unit, x, y, range);
    }
    @Override public void updateMovement(){
        if(target instanceof Unit && ((Unit)target).type instanceof OrbitalUnitType){
            moveTo(target, unit.range() * 0.78f, 30f);
            unit.lookAt(target);
        }
    }

    public static class Commands extends CommandAI{
        @Override public void updateUnit(){
            var scope = MapAlliances.enter(unit);
            try{ OrbitalTargeting.run(super::updateUnit); }
            finally{ MapAlliances.exit(scope); }
        }
        @Override public Teamc findTarget(float x, float y, float range, boolean air, boolean ground){
            if(attackTarget instanceof Unit && ((Unit)attackTarget).type instanceof OrbitalUnitType
                && !MapAlliances.allied(unit.team, attackTarget.team()) && nearAttackTarget(x, y, range)) return attackTarget;
            return find(unit, x, y, range);
        }
        @Override public void commandTarget(Teamc target, boolean stopAtTarget){
            if(target instanceof Unit && ((Unit)target).type instanceof OrbitalUnitType) super.commandTarget(target, stopAtTarget);
            else if(target != null) commandPosition(new arc.math.geom.Vec2(target.getX(), target.getY()));
        }
    }
}
