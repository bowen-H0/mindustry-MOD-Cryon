package cryon.Type;

import mindustry.Vars;
import mindustry.ai.types.FlyingAI;
import mindustry.type.UnitType;

/** Lets turret-equipped aircraft keep their hull oriented along their movement. */
public class PorphyraFlyingAI extends FlyingAI{
    @Override public void updateMovement(){
        float heading = unit.rotation;
        super.updateMovement();
        if(!unit.type.faceTarget){
            unit.rotation = heading;
            faceMovement();
        }
    }

    public static void install(){
        for(String name : new String[]{"particle", "momentum", "inertia", "gravity", "singularity"}){
            UnitType type = Vars.content.unit("cryon-" + name);
            if(type != null) type.aiController = PorphyraFlyingAI::new;
        }
    }
}
