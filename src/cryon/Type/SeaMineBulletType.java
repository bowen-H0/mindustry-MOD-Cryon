package cryon.Type;

import arc.graphics.Color;
import mindustry.entities.Units;
import mindustry.gen.Bullet;
import mindustry.gen.WaterMovec;

/** A physical proximity fuse detects hostile hulls, including concealed submarines. */
public class SeaMineBulletType extends MineBulletType{
    public SeaMineBulletType(float damage, float radius){
        super(0.18f, damage, radius);
        collidesAir = false;
        collidesGround = true;
        underwater = true;
        armTime = 55f;
        triggerRadius = 28f;
        lifetime = 60f * 45f;
        drag = 0.08f;
        color = Color.valueOf("70ccd0");
        goldColor = Color.valueOf("d9f7e8");
        hitColor = color;
    }

    @Override public void update(Bullet bullet){
        // Avoid MineBulletType's targetability filter: mines have their own short-range fuse.
        if(bullet.time < armTime) return;
        boolean[] contact = {false};
        Units.nearbyEnemies(bullet.team, bullet.x, bullet.y, triggerRadius, unit -> {
            if(!unit.dead && unit instanceof WaterMovec && !unit.isFlying()
                    && unit.within(bullet, triggerRadius)) contact[0] = true;
        });
        if(contact[0]){ hit(bullet); bullet.remove(); }
    }
}
