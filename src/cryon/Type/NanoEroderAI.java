package cryon.Type;

import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Time;
import cryon.Content.NanoEnvironment;
import cryon.Features.NanoColonies;
import mindustry.entities.units.AIController;
import mindustry.world.Tile;

/** Seek exposed ore veins, pulse nearby enemies, and seed infection along the route. */
public class NanoEroderAI extends AIController{
    private Tile destination;
    private float retarget, infectionTime;
    private final Vec2 destinationPosition = new Vec2(), nextPosition = new Vec2();
    private final boolean[] noPath = {false};
    @Override public void updateMovement(){
        if(mindustry.Vars.net.client()) return;
        infectionTime += Time.delta;
        if(infectionTime >= 30f){ infectionTime %= 30f; NanoEnvironment.spreadByUnit(unit); }
        if(target != null && !invalid(target)){
            moveTo(target, unit.type.range * 0.65f);
            unit.lookAt(target);
            return;
        }
        retarget -= Time.delta;
        if(destination == null || retarget <= 0f || unit.within(destination, 12f)){
            destination = NanoColonies.destination(unit);
            retarget = 120f + Mathf.randomSeed(unit.id, 60);
        }
        if(destination != null){
            destinationPosition.set(destination.worldx(), destination.worldy());
            if(mindustry.Vars.controlPath != null && mindustry.Vars.controlPath.getPathPosition(unit, destinationPosition, nextPosition, noPath)){
                moveTo(nextPosition, 0f); faceMovement();
            }else if(mindustry.Vars.controlPath == null){ moveTo(destination, 0f); faceMovement(); }
            if(noPath[0]){ destination = null; retarget = 0; }
        }
    }
}
