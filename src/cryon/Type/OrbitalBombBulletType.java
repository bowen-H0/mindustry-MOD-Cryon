package cryon.Type;

import mindustry.entities.bullet.BombBulletType;
import mindustry.gen.Bullet;
import arc.graphics.g2d.Draw;
import arc.math.Mathf;
import mindustry.graphics.Layer;

/** Quad-style stationary downward bomb, descending from above all shield layers. */
public class OrbitalBombBulletType extends BombBulletType implements OrbitalProjectile{
    public OrbitalBombBulletType(float damage, float radius){
        super(damage, radius);
        sprite = "large-bomb";
        speed = 0f;
        drag = 0f;
        keepVelocity = false;
        collides = collidesTiles = false;
        absorbable = reflectable = hittable = false;
        spin = 2f;
        shrinkX = shrinkY = 0.7f;
    }
    @Override public void draw(Bullet bullet){
        float z = Draw.z();
        Draw.z(Mathf.lerp(OrbitalUnitType.orbitalLayer, Layer.bullet, bullet.fin()));
        super.draw(bullet);
        Draw.z(z);
    }
}
