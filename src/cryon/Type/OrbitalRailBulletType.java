package cryon.Type;

import cryon.Features.OrbitalTargeting;
import mindustry.entities.bullet.RailBulletType;
import mindustry.gen.Bullet;

/** Native rail penetration and damage, including orbital targets along the beam. */
public class OrbitalRailBulletType extends RailBulletType{
    @Override public void init(Bullet bullet){ OrbitalTargeting.run(() -> super.init(bullet)); }
}
