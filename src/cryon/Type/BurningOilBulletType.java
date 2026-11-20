package cryon.Type;

import arc.graphics.Color;
import arc.math.Mathf;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.gen.Bullet;
import mindustry.world.Tile;

/** Deposits real oil puddles and ignites those same tiles on impact or expiration. */
public class BurningOilBulletType extends BasicBulletType{
    public BurningOilBulletType(){
        super(7f, 16f);
        lifetime = 28f;
        width = 9f;
        height = 16f;
        hitSize = 7f;
        ammoMultiplier = 1f;
        collidesAir = false;
        hittable = false;
        reflectable = false;
        keepVelocity = false;
        splashDamage = 14f;
        splashDamageRadius = 28f;
        buildingDamageMultiplier = 0.65f;
        status = StatusEffects.burning;
        statusDuration = 360f;
        backColor = trailColor = hitColor = Color.valueOf("ff873c");
        frontColor = Color.valueOf("ffe6a0");
        trailLength = 7;
        trailWidth = 2.4f;
        trailEffect = Fx.burning;
        trailChance = 0.3f;
        shootEffect = Fx.shootPyraFlame;
        smokeEffect = Fx.none;
        hitEffect = Fx.hitFlameSmall;
        despawnEffect = Fx.none;
        despawnHit = true;
        puddleLiquid = Liquids.oil;
        puddles = 5;
        puddleAmount = 12f;
        puddleRange = 24f;
    }

    @Override public void createPuddles(Bullet b, float x, float y){
        if(Vars.net.client()) return;
        for(int i = 0; i < puddles; i++){
            // Always coat the impact tile as well as the surrounding ground.
            Tile tile = Vars.world.tileWorld(x + (i == 0 ? 0f : Mathf.range(puddleRange)),
                y + (i == 0 ? 0f : Mathf.range(puddleRange)));
            if(tile == null) continue;
            Puddles.deposit(tile, puddleLiquid, puddleAmount);
            // Native fire obeys oxygen/fire rules and burns fuel left on the ground.
            Fires.create(tile);
        }
    }
}
