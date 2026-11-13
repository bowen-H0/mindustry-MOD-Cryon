package cryon.Type;

import cryon.Features.MapAlliances;
import mindustry.entities.Units;
import mindustry.entities.units.AIController;
import mindustry.game.Team;
import mindustry.gen.Teamc;

/** Scripted Sharded attackers pursue the player's faction, including its landing base. */
public final class ShardedReceptorRaidAI extends AIController{
    private final arc.math.geom.Vec2 goal = new arc.math.geom.Vec2(), next = new arc.math.geom.Vec2();
    private final boolean[] noPath = {false};
    private boolean hostile(Team team){
        return team == cryon.Features.AllianceTeam.team && team != unit.team
            && !MapAlliances.allied(unit.team, team);
    }

    @Override public Teamc findTarget(float x, float y, float range, boolean air, boolean ground){
        return Units.closestTarget(unit.team, x, y, range,
            other -> hostile(other.team) && other.checkTarget(air, ground),
            building -> ground && hostile(building.team)
                && !(building instanceof JavaProcessor.JavaProcessorBuild));
    }

    @Override public Teamc findMainTarget(float x, float y, float range, boolean air, boolean ground){
        return findTarget(x, y, range, air, ground);
    }

    @Override public void updateMovement(){
        Teamc objective = findTarget(unit.x, unit.y, Math.max(mindustry.Vars.world.unitWidth(), mindustry.Vars.world.unitHeight()) * 2f,
            unit.type.targetAir, unit.type.targetGround);
        if(objective == null){ unit.vel.setZero(); return; }
        if(unit.within(objective, unit.range() * 0.85f)){
            moveTo(objective, unit.range() * 0.7f, 20f);
        }else if(mindustry.Vars.controlPath != null){
            goal.set(objective.x(), objective.y());
            if(mindustry.Vars.controlPath.getPathPosition(unit, goal, next, noPath)) moveTo(next, 0f);
        }else{
            moveTo(objective, unit.range() * 0.7f, 20f);
        }
        unit.lookAt(objective);
    }
}
