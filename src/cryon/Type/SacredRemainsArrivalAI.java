package cryon.Type;

import arc.math.geom.Vec2;
import mindustry.ai.types.GroundAI;

/** Native movement advances real feet through the portal; no teleport or sprite-only translation. */
public final class SacredRemainsArrivalAI extends GroundAI{
    private final Vec2 direction = new Vec2();
    private final float endX, endY;
    public SacredRemainsArrivalAI(float x,float y,float angle){
        direction.trns(angle,1f);endX=x+direction.x*125f;endY=y+direction.y*125f;
    }
    @Override public void updateMovement(){
        unit.lookAt(direction.angle());
        if(!unit.within(endX,endY,5f))unit.moveAt(new Vec2(direction).scl(unit.speed()),0.3f);
        else unit.vel.scl(0.7f);
        unit.controlWeapons(false,false);
    }
}
