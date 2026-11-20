package cryon.Content;

import arc.graphics.Color;
import arc.math.geom.Rect;
import cryon.Type.AravisHelicopterUnitType;
import cryon.Type.WaterCooledUnitFactory;
import cryon.Type.HoverLandingUnit;
import cryon.Type.AravisHoverUnitType;
import mindustry.ai.UnitCommand;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.entities.bullet.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.blocks.units.UnitFactory;

/** Aravis combined-arms branches: three aircraft and five ground/hover units. */
public final class AravisRtsUnits{
    public static UnitType violetFalcon, ashKite, needlewing, shorekeeper, fieldArtillery, hoverTank, lightTank, flakCar;
    public static UnitFactory airHeadquarters, armyHeadquarters;
    private static final Color accent = Color.valueOf("a27ce5");
    private AravisRtsUnits(){}

    public static void load(){
        violetFalcon = air("aravis-violet-falcon", false, 420f, 3.4f, 14f);
        violetFalcon.targetAir = true;
        violetFalcon.targetGround = false;
        MissileBulletType intercept = new MissileBulletType(5.2f, 19f);
        intercept.lifetime = 120f / intercept.speed;
        intercept.homingPower = 0.1f;
        intercept.homingRange = 120f;
        intercept.collidesGround = false;
        intercept.splashDamage = 8f;
        intercept.splashDamageRadius = 12f;
        missileColors(intercept);
        Weapon falconGun = weapon(violetFalcon, intercept, 27f, false);
        falconGun.shoot.shots = 2;
        falconGun.shoot.shotDelay = 3f;
        falconGun.shootSound = Sounds.shootMissile;

        ashKite = air("aravis-ash-kite", true, 340f, 1.65f, 15f);
        ashKite.drag = 0.09f;
        ashKite.rotateSpeed = 4.5f;
        ashKite.engineSize = 0f;
        ashKite.lowAltitude = true;
        MissileBulletType groundMissile = new MissileBulletType(3.8f, 22f);
        groundMissile.lifetime = 112f / groundMissile.speed;
        groundMissile.homingPower = 0.055f;
        groundMissile.homingRange = 100f;
        groundMissile.collidesAir = false;
        groundMissile.splashDamage = 10f;
        groundMissile.splashDamageRadius = 15f;
        missileColors(groundMissile);
        Weapon helicopterGun = weapon(ashKite, groundMissile, 55f, true);
        helicopterGun.shoot.shots = 2;
        helicopterGun.shoot.shotDelay = 6f;
        helicopterGun.shootSound = Sounds.shootMissile;

        needlewing = air("aravis-needlewing", false, 160f, 2.7f, 11f);
        needlewing.circleTarget = true;
        BasicBulletType weakGun = shell(5.5f, 7f, 96f);
        weakGun.collidesAir = false;
        Weapon attackGun = weapon(needlewing, weakGun, 20f, false);
        attackGun.shoot.shots = 2;
        attackGun.shoot.shotDelay = 2f;

        shorekeeper = hover("aravis-shorekeeper", 1000f, 3f, 1.25f, 25f);
        HoverLandingUnit.entityId = EntityMapping.register("cryon-hover-landing-unit", HoverLandingUnit::new);
        shorekeeper.constructor = HoverLandingUnit::new;
        shorekeeper.payloadCapacity = 24f * 24f;
        shorekeeper.pickupUnits = true;
        shorekeeper.allowedInPayloads = false;
        shorekeeper.commands.addAll(UnitCommand.moveCommand, UnitCommand.loadUnitsCommand,
            UnitCommand.unloadPayloadCommand, UnitCommand.loopPayloadCommand);
        shorekeeper.defaultCommand = UnitCommand.moveCommand;
        shorekeeper.targetAir = shorekeeper.targetGround = false;

        fieldArtillery = tank("aravis-field-artillery", 260f, 0f, 0.72f, 14f, 64);
        ArtilleryBulletType artillery = new ArtilleryBulletType(3.5f, 30f);
        artillery.lifetime = 210f / artillery.speed;
        artillery.collidesAir = false;
        artillery.splashDamage = 65f;
        artillery.splashDamageRadius = 26f;
        artillery.width = 9f;
        artillery.height = 12f;
        artillery.backColor = accent;
        artillery.frontColor = Color.white;
        artillery.hitEffect = Fx.blastExplosion;
        artillery.despawnEffect = Fx.blastExplosion;
        Weapon howitzer = weapon(fieldArtillery, artillery, 105f, true);
        howitzer.recoil = 3f;
        howitzer.shootSound = Sounds.shootArtillery;

        hoverTank = hover("aravis-hover-tank", 720f, 4f, 1.45f, 15f);
        BasicBulletType hoverShell = shell(5.5f, 38f, 120f);
        hoverShell.collidesAir = false;
        weapon(hoverTank, hoverShell, 34f, true);

        lightTank = tank("aravis-light-tank", 200f, 1f, 1.2f, 11f, 48);
        BasicBulletType lightShell = shell(5f, 18f, 96f);
        lightShell.collidesAir = false;
        weapon(lightTank, lightShell, 32f, true);

        flakCar = tank("aravis-flak-car", 450f, 2f, 1.05f, 13f, 56);
        flakCar.targetAir = true;
        flakCar.targetGround = false;
        FlakBulletType flak = new FlakBulletType(5.5f, 10f);
        flak.lifetime = 136f / flak.speed;
        flak.collidesGround = false;
        flak.splashDamage = 18f;
        flak.splashDamageRadius = 20f;
        flak.backColor = accent;
        flak.frontColor = Color.white;
        flak.hitEffect = Fx.flakExplosion;
        weapon(flakCar, flak, 16f, true);

        armyHeadquarters = headquarters("aravis-army-headquarters", 6f,
            ItemStack.with(CryonItems.ferrum, 200, CryonItems.steel, 120, Items.silicon, 100, CryonItems.nickel, 40));
        plan(armyHeadquarters, lightTank, 1200f, 25, 15, 10, 0, 0, 0);
        plan(armyHeadquarters, flakCar, 2100f, 55, 0, 40, 15, 30, 0);
        plan(armyHeadquarters, fieldArtillery, 2700f, 65, 0, 45, 0, 50, 25);
        plan(armyHeadquarters, hoverTank, 3000f, 90, 0, 65, 40, 0, 65);
        plan(armyHeadquarters, shorekeeper, 3600f, 100, 70, 70, 40, 0, 0);

        airHeadquarters = headquarters("aravis-air-headquarters", 8f,
            ItemStack.with(CryonItems.ferrum, 240, CryonItems.hardSteel, 140, Items.silicon, 160, CryonItems.nickel, 60));
        plan(airHeadquarters, needlewing, 1500f, 25, 15, 20, 0, 0, 0);
        plan(airHeadquarters, violetFalcon, 2400f, 45, 0, 45, 15, 30, 0);
        plan(airHeadquarters, ashKite, 2700f, 60, 45, 35, 0, 0, 0);
    }

    private static UnitType air(String name, boolean helicopter, float health, float speed, float hitSize){
        UnitType unit = helicopter ? new AravisHelicopterUnitType(name) : new UnitType(name);
        unit.constructor = UnitEntity::create;
        unit.flying = true;
        unit.health = health;
        unit.speed = speed;
        unit.hitSize = hitSize;
        unit.accel = 0.06f;
        unit.drag = 0.04f;
        unit.rotateSpeed = 5f;
        unit.engineSize = 1.4f;
        unit.engineOffset = hitSize * 0.55f;
        unit.targetAir = false;
        unit.targetGround = true;
        unit.outlineColor = Color.valueOf("282333");
        return unit;
    }

    private static UnitType hover(String name, float health, float armor, float speed, float hitSize){
        AravisHoverUnitType unit = new AravisHoverUnitType(name);
        unit.constructor = ElevationMoveUnit::create;
        unit.health = health;
        unit.armor = armor;
        unit.speed = speed;
        unit.hitSize = hitSize;
        unit.hovering = true;
        unit.canDrown = false;
        unit.canBoost = false;
        unit.targetAir = false;
        unit.accel = 0.055f;
        unit.drag = 0.065f;
        unit.engineSize = 0f;
        unit.outlineColor = Color.valueOf("282333");
        unit.addHoverJets();
        return unit;
    }

    private static UnitType tank(String name, float health, float armor, float speed, float hitSize, int pixels){
        UnitType unit = new UnitType(name);
        unit.constructor = TankUnit::create;
        unit.health = health;
        unit.armor = armor;
        unit.speed = speed;
        unit.hitSize = hitSize;
        unit.rotateSpeed = 3.5f;
        unit.accel = 0.06f;
        unit.drag = 0.08f;
        unit.omniMovement = false;
        unit.rotateMoveFirst = true;
        unit.targetAir = false;
        unit.outlineColor = Color.valueOf("282333");
        unit.treadRects = new Rect[]{new Rect(-pixels * 0.44f, -pixels * 0.34f, pixels * 0.18f, pixels * 0.68f)};
        unit.treadFrames = 4;
        unit.crushDamage = 0f;
        return unit;
    }

    private static BasicBulletType shell(float speed, float damage, float range){
        BasicBulletType bullet = new BasicBulletType(speed, damage);
        bullet.lifetime = range / speed;
        bullet.width = 5f;
        bullet.height = 10f;
        bullet.backColor = accent;
        bullet.frontColor = Color.white;
        bullet.hitEffect = Fx.hitBulletSmall;
        bullet.despawnEffect = Fx.hitBulletSmall;
        return bullet;
    }

    private static void missileColors(MissileBulletType bullet){
        bullet.backColor = accent;
        bullet.frontColor = Color.white;
        bullet.trailColor = accent;
        bullet.trailLength = 6;
        bullet.trailWidth = 1.2f;
    }

    private static Weapon weapon(UnitType unit, BulletType bullet, float reload, boolean rotate){
        Weapon weapon = new Weapon(unit.name + "-weapon");
        weapon.mirror = false;
        weapon.rotate = rotate;
        weapon.rotateSpeed = 5f;
        weapon.x = 0f;
        weapon.y = 0f;
        weapon.shootY = unit.hitSize * 0.45f;
        weapon.reload = reload;
        weapon.shootCone = 8f;
        weapon.bullet = bullet;
        unit.range = bullet.speed * bullet.lifetime;
        bullet.rangeOverride = unit.range;
        bullet.keepVelocity = false;
        weapon.shootSound = Sounds.shoot;
        bullet.shootEffect = Fx.shootSmall;
        unit.weapons.add(weapon);
        return weapon;
    }

    private static UnitFactory headquarters(String name, float power, ItemStack[] requirements){
        UnitFactory factory = new WaterCooledUnitFactory(name);
        factory.size = 4;
        factory.health = 1600;
        factory.itemCapacity = 200;
        factory.consumePower(power);
        factory.requirements(Category.units, requirements);
        return factory;
    }

    private static void plan(UnitFactory factory, UnitType unit, float time, int iron, int steel, int silicon,
                             int nickel, int hardSteel, int armorAlloy){
        arc.struct.Seq<ItemStack> cost = new arc.struct.Seq<>();
        Item[] items = {CryonItems.ferrum, CryonItems.steel, Items.silicon, CryonItems.nickel, CryonItems.hardSteel, CryonItems.armorAlloy};
        int[] amounts = {iron, steel, silicon, nickel, hardSteel, armorAlloy};
        for(int i = 0; i < items.length; i++) if(amounts[i] > 0) cost.add(new ItemStack(items[i], amounts[i]));
        factory.plans.add(new UnitFactory.UnitPlan(unit, time, cost.toArray(ItemStack.class)));
    }
}
