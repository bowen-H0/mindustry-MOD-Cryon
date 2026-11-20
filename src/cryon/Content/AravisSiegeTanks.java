package cryon.Content;

import arc.graphics.Color;
import arc.math.geom.Rect;
import arc.struct.ObjectMap;
import mindustry.content.Fx;
import mindustry.entities.bullet.*;
import mindustry.entities.pattern.ShootBarrel;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.meta.BlockFlag;

/** A five-tier siege line. Upgrade routes are reserved for future production buildings. */
public final class AravisSiegeTanks{
    public static UnitType abrade, rattle, usurp, deter, overbear;
    public static final ObjectMap<UnitType, UnitType> upgrades = new ObjectMap<>();
    public static UnitType[] tiers;
    private static final Color shellColor = Color.valueOf("d5aa72");

    public static void load(){
        abrade = tank("abrade", 360f, 3f, 1.05f, 12f, 48, 144f, 0.3f);
        gun(abrade, shell(5f, 32f, 144f, 1.65f), 55f);

        rattle = tank("rattle", 600f, 5f, 0.86f, 17f, 68, 184f, 0.6f);
        BasicBulletType burst = shell(5.4f, 48f, 184f, 1.65f);
        burst.splashDamage = 15f;
        burst.splashDamageRadius = 14f;
        Weapon burstGun = gun(rattle, burst, 80f);
        burstGun.shoot.shots = 3;
        burstGun.shoot.shotDelay = 5f;

        usurp = tank("usurp", 2200f, 10f, 0.68f, 23f, 92, 232f, 1.2f);
        BasicBulletType penetrator = shell(6.2f, 175f, 232f, 1.85f);
        penetrator.pierce = penetrator.pierceBuilding = true;
        penetrator.pierceCap = 3;
        penetrator.splashDamage = 45f;
        penetrator.splashDamageRadius = 21f;
        gun(usurp, penetrator, 85f);

        deter = tank("deter", 6800f, 15f, 0.53f, 31f, 124, 280f, 2.6f);
        ArtilleryBulletType heavy = artillery(4.5f, 260f, 110f, 30f, 280f, 1.9f);
        Weapon twins = gun(deter, heavy, 115f);
        ShootBarrel barrels = new ShootBarrel();
        barrels.barrels = new float[]{-4f, 0f, 0f, 4f, 0f, 0f};
        barrels.shots = 2;
        barrels.shotDelay = 8f;
        twins.shoot = barrels;

        overbear = tank("overbear", 15000f, 20f, 0.4f, 42f, 168, 336f, 5f);
        ArtilleryBulletType siege = artillery(4.8f, 650f, 320f, 44f, 336f, 2f);
        siege.width = 19f;
        siege.height = 27f;
        siege.hitEffect = siege.despawnEffect = Fx.massiveExplosion;
        Weapon siegeGun = gun(overbear, siege, 145f);
        siegeGun.recoil = 7f;
        siegeGun.shake = 4f;
        Weapon escorts = new Weapon(overbear.name + "-aux");
        escorts.x = 13f;
        escorts.y = -5f;
        escorts.mirror = true;
        escorts.rotate = true;
        escorts.rotateSpeed = 3f;
        escorts.reload = 34f;
        escorts.shootY = 5.5f;
        escorts.shootCone = 8f;
        escorts.bullet = shell(6f, 45f, 190f, 1.4f);
        escorts.shootSound = Sounds.shoot;
        overbear.weapons.add(escorts);

        tiers = new UnitType[]{abrade, rattle, usurp, deter, overbear};
        upgrades.clear();
        for(int i = 0; i < tiers.length - 1; i++) upgrades.put(tiers[i], tiers[i + 1]);
    }

    private static UnitType tank(String name, float health, float armor, float speed,
                                 float hitSize, int pixels, float range, float crushDamage){
        UnitType unit = new UnitType("aravis-" + name);
        unit.constructor = TankUnit::create;
        unit.health = health;
        unit.armor = armor;
        unit.speed = speed;
        unit.hitSize = hitSize;
        unit.range = range;
        unit.rotateSpeed = Math.max(1.1f, 3.5f - hitSize * 0.05f);
        unit.accel = 0.045f;
        unit.drag = 0.08f;
        unit.omniMovement = false;
        unit.rotateMoveFirst = true;
        unit.targetAir = false;
        unit.targetGround = true;
        unit.targetFlags = new BlockFlag[]{BlockFlag.turret, BlockFlag.reactor, BlockFlag.core, null};
        unit.outlineColor = Color.valueOf("282333");
        unit.treadRects = new Rect[]{new Rect(-pixels * 0.44f, -pixels * 0.34f,
            pixels * 0.18f, pixels * 0.68f)};
        unit.treadFrames = 4;
        unit.crushDamage = crushDamage;
        unit.crushFragile = true;
        return unit;
    }

    private static BasicBulletType shell(float speed, float damage, float range, float buildingBonus){
        BasicBulletType bullet = new BasicBulletType(speed, damage);
        bullet.lifetime = range / speed;
        bullet.rangeOverride = range;
        bullet.width = 7f;
        bullet.height = 13f;
        bullet.collidesAir = false;
        bullet.buildingDamageMultiplier = buildingBonus;
        bullet.keepVelocity = false;
        bullet.backColor = bullet.trailColor = shellColor;
        bullet.frontColor = Color.white;
        bullet.trailLength = 5;
        bullet.trailWidth = 1.5f;
        bullet.shootEffect = Fx.shootBig;
        bullet.hitEffect = bullet.despawnEffect = Fx.hitBulletBig;
        return bullet;
    }

    private static ArtilleryBulletType artillery(float speed, float damage, float splash,
                                                 float radius, float range, float buildingBonus){
        // Arcing artillery resolves its damage through the landing explosion.
        ArtilleryBulletType bullet = new ArtilleryBulletType(speed, 0f);
        bullet.lifetime = range / speed;
        bullet.rangeOverride = range;
        bullet.width = 12f;
        bullet.height = 18f;
        bullet.collidesAir = false;
        bullet.splashDamage = damage + splash;
        bullet.splashDamageRadius = radius;
        bullet.buildingDamageMultiplier = buildingBonus;
        bullet.keepVelocity = false;
        bullet.backColor = bullet.trailColor = shellColor;
        bullet.frontColor = Color.white;
        bullet.trailLength = 12;
        bullet.trailWidth = 2.5f;
        bullet.hitEffect = bullet.despawnEffect = Fx.blastExplosion;
        bullet.shootEffect = Fx.shootBig;
        return bullet;
    }

    private static Weapon gun(UnitType unit, BulletType bullet, float reload){
        Weapon gun = new Weapon(unit.name + "-weapon");
        // Weapon defaults to x=5 for mirrored side guns; these are central turrets.
        gun.x = 0f;
        gun.y = 0f;
        gun.mirror = false;
        gun.rotate = true;
        gun.rotateSpeed = 2.5f;
        gun.shootCone = 5f;
        gun.reload = reload;
        gun.shootY = unit.hitSize * 0.47f;
        gun.recoil = 2.5f;
        gun.bullet = bullet;
        gun.shootSound = Sounds.shootArtillery;
        unit.weapons.add(gun);
        return gun;
    }
}
