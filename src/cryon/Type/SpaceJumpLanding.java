package cryon.Type;

import arc.math.geom.Vec2;
import arc.struct.Seq;
import mindustry.entities.abilities.Ability;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Unit;

/** Resolve the closest safe landing toward the source, outside the union of hostile flux fields. */
public class SpaceJumpLanding{
    public static Vec2 outsideEnemyFlux(Unit unit, Vec2 destination){
        float dx = destination.x - unit.x, dy = destination.y - unit.y;
        float length2 = dx * dx + dy * dy;
        if(length2 < 0.0001f) return destination;
        Seq<Vec2> intervals = new Seq<>();
        float clearance = unit.hitSize / 2f + 8f;
        for(Building building : Groups.build){
            if(building.team == unit.team || building.dead || !(building instanceof FluxBarrier.ForceBuild)) continue;
            FluxBarrier.ForceBuild shield = (FluxBarrier.ForceBuild)building;
            float radius = shield.broken ? 0f : shield.realRadius();
            if(radius > 0.001f) interval(unit, dx, dy, length2, shield.x, shield.y, radius + clearance, intervals);
        }
        for(Unit source : Groups.unit){
            if(source.team == unit.team || source.dead || !source.isAdded()
                || FluxSuppressionAbility.suppressed(source.team, source.x, source.y)) continue;
            for(Ability ability : source.abilities){
                float radius = 0f;
                if(ability instanceof FluxBarrierAbility){
                    FluxBarrierAbility shield = (FluxBarrierAbility)ability;
                    radius = shield.radius * shield.radscl;
                }else if(ability instanceof SafeFluxBarrierAbility){
                    SafeFluxBarrierAbility shield = (SafeFluxBarrierAbility)ability;
                    if(!shield.broken) radius = shield.radius * shield.radscl;
                }
                if(radius > 0.001f) interval(unit, dx, dy, length2, source.x, source.y, radius + clearance, intervals);
            }
        }
        // Merge intersecting intervals; pushing out of one shield must not put us in its neighbour.
        intervals.sort((a, b) -> Float.compare(a.x, b.x));
        float start = 0f, end = -1f;
        for(Vec2 interval : intervals){
            if(interval.x > end){
                start = interval.x;
                end = interval.y;
            }else end = Math.max(end, interval.y);
            if(end >= 1f && start <= 1f){
                if(start <= 0f) return null; // no safe point on this segment
                float fraction = Math.max(0f, start - 0.5f / (float)Math.sqrt(length2));
                return destination.set(unit.x + dx * fraction, unit.y + dy * fraction);
            }
        }
        return destination;
    }

    private static void interval(Unit unit, float dx, float dy, float length2, float x, float y,
                                 float radius, Seq<Vec2> intervals){
        float ox = unit.x - x, oy = unit.y - y;
        float b = ox * dx + oy * dy;
        float discriminant = b * b - length2 * (ox * ox + oy * oy - radius * radius);
        if(discriminant < 0f) return;
        float root = (float)Math.sqrt(discriminant);
        float enter = (-b - root) / length2, exit = (-b + root) / length2;
        if(exit >= 0f && enter <= 1f) intervals.add(new Vec2(enter, exit));
    }
}
