package cryon.Type;

import arc.graphics.Color;
import cryon.Features.MapAlliances;
import cryon.Features.OrbitalTargeting;
import mindustry.content.Fx;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.gen.Bullet;

/** Plasma machine-gun bolt with explicit ground, conventional air and orbital collision support. */
public class OmniPlasmaBulletType extends BasicBulletType implements OrbitalProjectile, OrbitalTargetingProjectile{
    public OmniPlasmaBulletType(){
        super(10f, 16f);
        lifetime = 36f;
        rangeOverride = 360f;
        width = 7f;
        height = 12f;
        splashDamage = 6f;
        splashDamageRadius = 12f;
        collidesAir = collidesGround = true;
        absorbable = reflectable = false;
        trailLength = 5;
        trailWidth = 1.4f;
        backColor = trailColor = hitColor = Color.valueOf("c89bff");
        frontColor = Color.white;
        hitEffect = despawnEffect = Fx.hitLaser;
    }
    @Override public void createSplashDamage(Bullet b, float x, float y){
        var scope = MapAlliances.enterTeam(b.team);
        try{ OrbitalTargeting.run(() -> super.createSplashDamage(b, x, y)); }
        finally{ MapAlliances.exit(scope); }
    }
}
