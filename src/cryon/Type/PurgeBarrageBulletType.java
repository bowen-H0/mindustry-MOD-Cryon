package cryon.Type;

import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.Time;
import mindustry.content.Blocks;
import mindustry.entities.Mover;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.gen.Bullet;
import mindustry.world.blocks.defense.turrets.PowerTurret;

/** Four rotating hooked petals, followed by interleaved counter-rotating fragment rings. */
public class PurgeBarrageBulletType extends BasicBulletType{
    public PurgeBarrageBulletType(){
        super(5f, 180f);
        BasicBulletType original = (BasicBulletType)((PowerTurret)Blocks.afflict).shootType;
        sprite = "large-orb";
        lifetime = 80f;
        width = height = 16f;
        shrinkX = shrinkY = 0f;
        pierceCap = 2;
        buildingDamageMultiplier = 0.5f;
        ammoMultiplier = 1f;
        hitColor = backColor = trailColor = original.hitColor;
        frontColor = original.frontColor;
        trailLength = 12;
        trailWidth = 2.2f;
        shootEffect = original.shootEffect;
        smokeEffect = original.smokeEffect;
        hitEffect = original.hitEffect;
        despawnEffect = original.despawnEffect;
        despawnSound = original.despawnSound;
        shootSound = original.shootSound;
        // Independent copy: the native Afflict projectile remains untouched.
        intervalBullet = original.intervalBullet.copy();
        intervalBullet.homingDelay = 16f;
        fragBullet = intervalBullet;
        bulletInterval = 6f;
        intervalBullets = 4;
        intervalRandomSpread = 0f;
        fragBullets = 20;
        fragOnHit = false;
        fragRandomSpread = 0f;
    }

    private Mover curl(float direction){
        return child -> {
            // Let the curved silhouette form before native homing takes over.
            if(child.time < child.type.homingDelay){
                child.vel.rotate(direction * 5f * Time.delta);
            }
        };
    }

    @Override public void updateBulletInterval(Bullet b){
        if(b.time < intervalDelay || !b.timer.get(2, bulletInterval)) return;
        int wave = (int)(b.time / bulletInterval);
        float direction = (wave & 1) == 0 ? 1f : -1f;
        float phase = b.rotation() + wave * 37f + direction * 22f;
        for(int i = 0; i < intervalBullets; i++){
            float angle = phase + i * 360f / intervalBullets;
            intervalBullet.create(b.owner, b.shooter, b.team, b.x, b.y, angle, -1f,
                (i & 1) == 0 ? 0.85f : 1.15f, 1f, null, curl(direction), b.aimX, b.aimY);
        }
    }

    @Override public void createFrags(Bullet b, float x, float y){
        if((b.absorbed && !fragOnAbsorb) || (pierceFragCap >= 0 && b.frags >= pierceFragCap)) return;
        for(int i = 0; i < fragBullets; i++){
            float direction = (i & 1) == 0 ? 1f : -1f;
            float angle = b.rotation() + i * 360f / fragBullets;
            fragBullet.create(b.owner, b.shooter, b.team, x, y, angle, -1f,
                direction > 0 ? 0.65f : 1.15f, 1f, null, curl(direction), b.aimX, b.aimY);
        }
        b.frags++;
    }

    @Override public void draw(Bullet b){
        super.draw(b);
        Draw.color(backColor);
        Lines.stroke(1.3f * b.fout());
        float phase = b.rotation() + b.time * 9f;
        for(int i = 0; i < 4; i++){
            Lines.arc(b.x, b.y, 11f, 0.12f, phase + i * 90f);
        }
        Draw.reset();
    }
}
