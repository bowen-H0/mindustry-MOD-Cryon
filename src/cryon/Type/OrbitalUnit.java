package cryon.Type;

import cryon.Features.OrbitalTargeting;
import mindustry.gen.UnitEntity;

/** Serializes with the native flying entity, but exposes a separate combat layer. */
public class OrbitalUnit extends UnitEntity{
    public static int entityId;
    @Override public int classId(){ return entityId; }
    @Override public boolean checkTarget(boolean air, boolean ground){
        return air && OrbitalTargeting.enabled();
    }
}
