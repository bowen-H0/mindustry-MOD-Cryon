package cryon.Type;

import arc.graphics.Color;
import mindustry.content.Fx;
import mindustry.gen.Sounds;

/** Slow ballistic warhead: inherits launcher momentum and coasts with slight drag, without homing. */
public class HeavyTorpedoBulletType extends TorpedoBulletType{
    public HeavyTorpedoBulletType(float damage, float splash, float radius){
        super(0.8f, damage);
        sprite = "missile-large";
        width = 10f; height = 23f;
        lifetime = 540f;
        drag = 0.001f;
        keepVelocity = true;
        homingPower = 0f;
        collidesAir = false;
        underwater = true;
        splashDamage = splash;
        splashDamageRadius = radius;
        scaledSplashDamage = true;
        buildingDamageMultiplier = 0.85f;
        backColor = trailColor = hitColor = Color.valueOf("76ced1");
        frontColor = Color.valueOf("e2faf1");
        trailLength = 18; trailWidth = 2.8f;
        hitEffect = new mindustry.entities.Effect(55f, 700f, e -> {
            arc.graphics.g2d.Draw.color(Color.white, e.color, e.fin());
            arc.graphics.g2d.Lines.stroke(5f * e.fout());
            arc.graphics.g2d.Lines.circle(e.x, e.y, radius * 1.15f * e.finpow());
            arc.graphics.g2d.Lines.stroke(2.5f * e.fout());
            arc.graphics.g2d.Lines.circle(e.x, e.y, radius * 0.65f * e.finpow());
            arc.graphics.g2d.Fill.light(e.x, e.y, 40, radius * 0.8f * e.fout(),
                    arc.util.Tmp.c1.set(Color.white).a(e.fout() * 0.8f), arc.util.Tmp.c2.set(e.color).a(0f));
            arc.math.Angles.randLenVectors(e.id, 18, radius * e.fin(), (x, y) -> {
                arc.graphics.g2d.Lines.stroke(e.fout());
                arc.graphics.g2d.Lines.circle(e.x + x, e.y + y, 2f + 4f * e.fin());
            });
            arc.graphics.g2d.Draw.reset();
        });
        despawnEffect = Fx.none;
        hitSound = Sounds.explosion;
        shootEffect = Fx.shootBig;
        smokeEffect = Fx.none;
        despawnHit = true;
    }
}
