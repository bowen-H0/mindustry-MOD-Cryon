package cryon.Type;

import arc.math.geom.Vec2;
import mindustry.gen.*;

/** Sustained all-layer beam, including cursor-controlled target switching when possessed. */
public class OmniBurnBeamBulletType extends LockOnBurnBulletType implements OrbitalProjectile{
    private final Vec2 aim = new Vec2();
    public OmniBurnBeamBulletType(){
        targetsOrbit = true;
        collidesAir = collidesGround = true;
        rangeOverride = 380f;
        damage = 8f;
        beamLayer = OrbitalUnitType.orbitalLayer + 0.1f;
    }
    @Override protected Teamc intendedTarget(Bullet b){
        if(b.owner instanceof Unit unit && (unit.isPlayer() || unit.controller() instanceof mindustry.ai.types.LogicAI)){
            Teamc selected = null;
            if(Float.isFinite(unit.aimX) && Float.isFinite(unit.aimY) && unit.aimX >= 0 && unit.aimY >= 0){
                aim.set(unit.aimX - b.x, unit.aimY - b.y).limit(rangeOverride).add(b.x, b.y);
                selected = targetAt(b, aim.x, aim.y);
            }
            if(selected == null && b.data instanceof BeamState state){ state.target = null; state.focus = 0f; }
            return selected;
        }
        return super.intendedTarget(b);
    }
}
