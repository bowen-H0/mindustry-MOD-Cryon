package cryon.Type;

import cryon.Features.OrbitalTargeting;
import mindustry.entities.bullet.MissileBulletType;
import mindustry.gen.Bullet;

/** Guided anti-orbit missile with native air targeting and splash damage. */
public class OrbitalMissileBulletType extends MissileBulletType implements OrbitalTargetingProjectile{
    @Override public void update(Bullet bullet){
        OrbitalTargeting.run(() -> super.update(bullet));
    }

    @Override public void hit(Bullet bullet, float x, float y){
        OrbitalTargeting.run(() -> super.hit(bullet, x, y));
    }
}
