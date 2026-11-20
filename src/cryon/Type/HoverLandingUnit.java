package cryon.Type;

import mindustry.entities.EntityCollisions;
import mindustry.gen.Building;
import mindustry.gen.PayloadUnit;
import mindustry.gen.Unit;
import mindustry.world.Tile;
import mindustry.world.blocks.payloads.Payload;
import mindustry.world.blocks.payloads.UnitPayload;

/** Ground-colliding hover transport using native payload networking and serialization. */
public class HoverLandingUnit extends PayloadUnit{
    public static int entityId;
    @Override public int classId(){return entityId;}
    @Override public EntityCollisions.SolidPred solidity(){return EntityCollisions::solid;}
    @Override public boolean canPickup(Unit unit){return unit != this && unit.isGrounded() && super.canPickup(unit);}
    @Override public boolean canPickup(Building building){return false;}
    @Override public boolean canPickupPayload(Payload payload){
        return payload instanceof UnitPayload && canPickup(((UnitPayload)payload).unit) && super.canPickupPayload(payload);
    }

    private boolean safeShore(Payload payload){
        if(!(payload instanceof UnitPayload)) return false;
        Unit passenger = ((UnitPayload)payload).unit;
        Tile tile = tileOn();
        return tile != null && (!tile.floor().isDeep() || !passenger.type.canDrown || passenger.type.hovering || passenger.isFlying());
    }
    @Override public boolean canDropPayload(){
        return hasPayload() && safeShore(payloads().peek()) && super.canDropPayload();
    }
    @Override public boolean tryDropPayload(Payload payload){
        return safeShore(payload) && super.tryDropPayload(payload);
    }
}
