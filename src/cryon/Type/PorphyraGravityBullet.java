package cryon.Type;

import arc.graphics.Color;

/** Compact gravity well for the Porphyra flagship, with bounded flat damage. */
public class PorphyraGravityBullet extends BlackHoleBulletType{
    public PorphyraGravityBullet(){
        super(2.1f, 110f);
        startRadius = 5f;
        maxRadius = 36f;
        pullRadiusScale = 2.6f;
        pullForce = 0.16f;
        tickDamage = 28f;
        tickHealthPercent = 0f;
        damageInterval = 10f;
        explodeRadius = 90f;
        explodeDamage = 360f;
        explodeHealthPercent = 0f;
        color = Color.valueOf("b16be8");
        goldColor = Color.valueOf("eed3ff");
        lightColor = color;
        hitColor = color;
    }
}
