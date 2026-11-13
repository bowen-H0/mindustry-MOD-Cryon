package cryon.Type;

/** Orbit-to-orbit variant. Neither ordinary air, ground units nor buildings can receive damage. */
public class OrbitalBurnBeamBulletType extends LockOnBurnBulletType implements OrbitalProjectile{
    public OrbitalBurnBeamBulletType(){
        targetsOrbit = true;
        collidesAir = collidesGround = false;
        beamLayer = OrbitalUnitType.orbitalLayer + 0.1f;
    }
}
