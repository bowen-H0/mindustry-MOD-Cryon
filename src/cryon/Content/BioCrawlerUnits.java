package cryon.Content;

import arc.graphics.Color;
import cryon.Type.*;
import mindustry.content.*;
import mindustry.entities.bullet.*;
import mindustry.entities.effect.*;
import mindustry.entities.part.*;
import mindustry.entities.pattern.*;
import mindustry.gen.*;
import mindustry.graphics.Layer;
import mindustry.type.*;

/** Orange multi-turret assault fortresses, using native Erekir weapons and effects. */
public class BioCrawlerUnits{
    public static final Color orange = Color.valueOf("ff9b50"), ember = Color.valueOf("ef662f"), core = Color.valueOf("ffe2bd");
    public static UnitType benignitas, salus, propugno, ratio, veritas, decoy, airDecoy;

    public static void load(){
        // Keep these hidden content IDs readable by existing saves.
        decoy = new DecoyUnitType("bio-decoy", false);
        airDecoy = new DecoyUnitType("bio-decoy-air", true);
        benignitas = create("benignitas", 0);
        salus = create("salus", 1);
        propugno = create("propugno", 2);
        ratio = create("ratio", 3);
        veritas = create("veritas", 4);
    }

    private static UnitType create(String name, int tier){
        UnitType type = new UnitType(name);
        type.constructor = LegsUnit::create;
        type.speed = new float[]{1.15f, 0.85f, 0.9f, 0.8f, 0.7f}[tier];
        type.health = new float[]{600, 1200, 2000, 10000, 25000}[tier];
        type.armor = new float[]{4, 4, 8, 16, 24}[tier];
        type.hitSize = new float[]{26, 32, 32, 56, 72}[tier];
        type.legCount = tier == 4 ? 8 : 6;
        type.legLength = new float[]{22, 38, 38, 60, 80}[tier];
        type.legBaseOffset = new float[]{6, 10, 10, 14, 18}[tier];
        type.legMoveSpace = 1.8f;
        type.legExtension = tier < 3 ? -2 : tier == 3 ? -3 : -4;
        type.legStraightness = 0.5f;
        type.legGroupSize = 2;
        type.legSpeed = tier < 2 ? 0.6f : 0.85f;
        type.legContinuousMove = true;
        type.targetAir = false;
        type.faceTarget = true;
        type.shieldColor = orange;
        type.healColor = orange;
        type.outlineColor = Color.valueOf("38404b");
        type.mechLegColor = Color.valueOf("a65a48");
        type.immunities.add(StatusEffects.burning);
        float fieldRadius = new float[]{160, 192, 160, 176, 192}[tier];
        type.abilities.add(tier < 2 ? new FluxSuppressionAbility(fieldRadius) : new GlobalSuppressionAbility(fieldRadius));

        float reach = new float[]{80, 108, 132, 164, 192}[tier];
        Weapon main = mount(tier < 2 ? "benignitas-weapen" : "salus-meltdown-weapon", 0, tier < 2 ? 7 : -4, 60, false);
        main.shootY = tier < 2 ? 5 : 12;
        if(tier == 0){
            main.bullet = slug(4f, 18f, reach, 5f);
            main.reload = 34;
            main.shoot = new ShootSpread(3, 12f);
        }else if(tier == 1){
            // Afflict: an orb sheds small homing projectiles, then bursts on expiry.
            BasicBulletType orb = slug(4f, 65f, reach, 11f);
            orb.sprite = "large-orb";
            orb.intervalBullet = orb.fragBullet = rocket(22f, 68f, 8f);
            orb.bulletInterval = 10f;
            orb.intervalBullets = 1;
            orb.intervalAngle = 180;
            orb.intervalRandomSpread = 30;
            orb.fragBullets = 3;
            orb.fragOnHit = false;
            main.bullet = orb;
            main.reload = 75;
            main.shootSound = Sounds.shootAfflict;
        }else if(tier == 2){
            main.bullet = laser(230f, reach, 18f);
            main.shoot.firstShotDelay = 24;
            main.reload = 110;
            main.shootSound = Sounds.shootLancer;
        }else if(tier == 3){
            main.bullet = shell(280f, reach, 44f);
            main.reload = 145;
            main.shootSound = Sounds.shootArtillerySapBig;
            main.recoil = 7;
        }else{
            // Smite: alternating barrels feed a helix of charged, penetrating orbs.
            BasicBulletType orb = slug(8f, 240f, reach, 14f);
            orb.sprite = "large-orb";
            orb.pierce = orb.pierceBuilding = true;
            orb.pierceCap = 4;
            orb.intervalBullet = arc(20f, 5);
            orb.bulletInterval = 10;
            orb.intervalBullets = 1;
            orb.fragBullet = slug(6f, 45f, 60f, 6f);
            orb.fragBullets = 3;
            orb.fragOnHit = false;
            main.bullet = orb;
            ShootAlternate barrels = new ShootAlternate(5f);
            barrels.shots = barrels.barrels = 3;
            main.shoot = new ShootMulti(barrels, new ShootHelix(4f, 2.2f));
            main.reload = 125;
            main.shootSound = Sounds.shootSmite;
            main.recoil = 8;
        }
        if(tier >= 2) crown(main, tier);
        type.weapons.add(main);

        if(tier == 0){
            type.weapons.add(flameMount(8, -3));
        }else if(tier == 1){
            Weapon arcs = mount("salus-mount", 11, 4, 55, true);
            arcs.bullet = arc(22f, 11);
            arcs.shootSound = Sounds.shootPulsar;
            arcs.shoot.shots = 2;
            arcs.shoot.shotDelay = 4;
            type.weapons.add(arcs, rocketMount(8, -9, 58f, reach, 2));
        }else{
            type.weapons.add(kineticMount(tier, tier == 2 ? 12 : tier == 3 ? 20 : 29, 9, reach));
            type.weapons.add(rocketMount(tier == 2 ? 10 : tier == 3 ? 20 : 29, -12, 35f + tier * 13f, reach, 3));
            type.weapons.add(airMount(tier, tier == 2 ? 8 : tier == 3 ? 13 : 18, -20, reach));
            if(tier >= 3){
                Weapon heat = mount("salus-mount", tier == 3 ? 25 : 34, -2, 125, true);
                ContinuousFlameBulletType beam = new ContinuousFlameBulletType(30f + tier * 9f);
                tint(beam);
                beam.length = reach * 0.72f;
                beam.width = 4;
                beam.lifetime = 40;
                beam.colors = new Color[]{ember.cpy().a(0.25f), orange, core, Color.white};
                beam.flareColor = orange;
                beam.collidesAir = false;
                beam.hitEffect = Fx.hitFlameSmall;
                heat.bullet = beam;
                heat.continuous = true;
                heat.shootSound = Sounds.shootFlame;
                type.weapons.add(heat);
            }
            if(tier == 4){
                // Malign: homing projectiles unfold into a forward laser on impact.
                Weapon lances = mount("salus-weapon", 22, 24, 110, true);
                MissileBulletType pod = rocket(65, reach * 0.8f, 20);
                pod.fragBullet = laser(85f, 70f, 12f);
                pod.fragBullets = 1;
                pod.fragSpread = pod.fragRandomSpread = 0;
                lances.bullet = pod;
                lances.shoot.shots = 2;
                lances.shoot.shotDelay = 8;
                lances.shootSound = Sounds.shootMalign;
                Weapon mortars = mount("salus-weapon", 22, -30, 160, true);
                mortars.bullet = shell(140, reach * 0.85f, 30);
                mortars.shootSound = Sounds.shootArtillery;
                type.weapons.add(lances, mortars);
            }
        }
        return type;
    }

    private static Weapon mount(String sprite, float x, float y, float reload, boolean paired){
        Weapon weapon = new Weapon("cryon-" + sprite);
        weapon.x = x;
        weapon.y = y;
        weapon.shootY = 5;
        weapon.mirror = paired;
        weapon.alternate = true;
        weapon.rotate = true;
        weapon.rotateSpeed = 4;
        weapon.reload = reload;
        weapon.recoil = 2;
        weapon.shake = 1;
        weapon.shootCone = 12;
        weapon.shootSound = Sounds.shoot;
        weapon.heatColor = orange;
        weapon.cooldownTime = reload * 0.8f;
        weapon.shadow = paired ? 4 : 8;
        return weapon;
    }

    private static void tint(BulletType bullet){
        bullet.hitColor = bullet.lightColor = bullet.lightningColor = bullet.trailColor = orange;
        bullet.collidesAir = false;
        bullet.shootEffect = new MultiEffect(Fx.shootBigColor, wave(14, 10));
        bullet.smokeEffect = Fx.shootSmallSmoke;
        bullet.hitEffect = bullet.despawnEffect = burst(16);
    }

    private static WaveEffect wave(float radius, float life){
        WaveEffect effect = new WaveEffect();
        effect.colorFrom = effect.colorTo = orange;
        effect.sizeTo = radius;
        effect.lifetime = life;
        effect.strokeFrom = 2;
        effect.strokeTo = 0;
        return effect;
    }

    private static ExplosionEffect burst(float radius){
        ExplosionEffect effect = new ExplosionEffect();
        effect.waveColor = effect.sparkColor = orange;
        effect.smokeColor = ember.cpy().a(0.35f);
        effect.waveRad = radius;
        effect.waveStroke = 2;
        effect.lifetime = 22;
        effect.smokeSize = effect.smokeSizeBase = 0;
        effect.sparks = 6;
        effect.sparkRad = radius * 1.25f;
        effect.sparkLen = 4;
        effect.sparkStroke = 1.5f;
        return effect;
    }

    private static BasicBulletType slug(float speed, float damage, float range, float size){
        BasicBulletType bullet = new BasicBulletType(speed, damage);
        tint(bullet);
        bullet.lifetime = range / speed;
        bullet.width = size;
        bullet.height = size * 1.6f;
        bullet.backColor = orange;
        bullet.frontColor = core;
        bullet.trailWidth = size * 0.16f;
        bullet.trailLength = 7;
        bullet.trailEffect = Fx.disperseTrail;
        bullet.trailRotation = true;
        bullet.trailInterval = 6;
        return bullet;
    }

    private static LightningBulletType arc(float damage, int length){
        LightningBulletType bullet = new LightningBulletType();
        tint(bullet);
        bullet.damage = damage;
        bullet.lightningLength = length;
        bullet.lightningLengthRand = 3;
        bullet.shootEffect = Fx.sparkShoot;
        BulletType segment = new BulletType(0.0001f, 0f);
        tint(segment);
        segment.lifetime = Fx.lightning.lifetime;
        segment.hitEffect = Fx.hitBulletColor;
        segment.despawnEffect = Fx.none;
        segment.status = StatusEffects.shocked;
        segment.statusDuration = 20;
        segment.hittable = false;
        bullet.lightningType = segment;
        return bullet;
    }

    private static LaserBulletType laser(float damage, float range, float width){
        LaserBulletType bullet = new LaserBulletType(damage);
        tint(bullet);
        bullet.length = range;
        bullet.width = width;
        bullet.lifetime = 22;
        bullet.colors = new Color[]{ember.cpy().a(0.35f), orange, core, Color.white};
        bullet.sideLength = 18;
        bullet.sideWidth = 1;
        bullet.chargeEffect = new MultiEffect(wave(20, 24), Fx.colorSparkBig);
        bullet.shootEffect = Fx.shootBigColor;
        bullet.hitEffect = Fx.hitBulletColor;
        return bullet;
    }

    private static MissileBulletType rocket(float damage, float range, float splash){
        MissileBulletType bullet = new MissileBulletType(5f, damage);
        tint(bullet);
        bullet.lifetime = range / 5f;
        bullet.width = 8;
        bullet.height = 13;
        bullet.homingPower = 0.15f;
        bullet.homingRange = range;
        bullet.homingDelay = 4;
        bullet.weaveMag = 1.2f;
        bullet.weaveScale = 4;
        bullet.splashDamage = splash;
        bullet.splashDamageRadius = 20;
        bullet.frontColor = core;
        bullet.backColor = orange;
        bullet.trailWidth = 1.8f;
        bullet.trailLength = 9;
        bullet.despawnSound = Sounds.explosionDull;
        return bullet;
    }

    private static ArtilleryBulletType shell(float damage, float range, float radius){
        ArtilleryBulletType bullet = new ArtilleryBulletType(4f, damage, "shell");
        tint(bullet);
        bullet.lifetime = range / 4f;
        bullet.width = 17;
        bullet.height = 22;
        bullet.splashDamage = damage * 0.8f;
        bullet.splashDamageRadius = radius;
        bullet.scaledSplashDamage = true;
        bullet.backColor = orange;
        bullet.frontColor = core;
        bullet.trailWidth = 3;
        bullet.trailLength = 18;
        bullet.trailSinScl = 2.5f;
        bullet.trailSinMag = 0.5f;
        bullet.hitEffect = new MultiEffect(burst(radius), wave(radius * 1.2f, 28), Fx.colorSparkBig);
        bullet.shootEffect = new MultiEffect(Fx.shootTitan, wave(22, 12));
        bullet.smokeEffect = Fx.shootSmokeTitan;
        bullet.hitSound = Sounds.explosionTitan;
        bullet.despawnEffect = Fx.none;
        bullet.despawnShake = 4;
        bullet.status = StatusEffects.blasted;
        return bullet;
    }

    private static Weapon flameMount(float x, float y){
        Weapon weapon = mount("salus-mount", x, y, 16, true);
        weapon.shootSound = Sounds.shootFlame;
        weapon.inaccuracy = 7;
        BulletType flame = new BulletType(4.2f, 18f);
        tint(flame);
        flame.lifetime = 15;
        flame.hitSize = 7;
        flame.pierce = flame.pierceBuilding = true;
        flame.pierceCap = 3;
        flame.status = StatusEffects.burning;
        flame.statusDuration = 120;
        flame.shootEffect = Fx.shootSmallFlame;
        flame.hitEffect = Fx.hitFlameSmall;
        flame.despawnEffect = Fx.none;
        flame.keepVelocity = false;
        flame.hittable = false;
        weapon.bullet = flame;
        return weapon;
    }

    private static Weapon kineticMount(int tier, float x, float y, float range){
        Weapon weapon = mount("salus-weapon", x, y, 32 + tier * 3, true);
        BasicBulletType bullet = slug(7, 24 + tier * 15, range * 0.85f, 6);
        bullet.pierce = bullet.pierceBuilding = true;
        bullet.pierceCap = 2;
        weapon.bullet = bullet;
        weapon.shoot.shots = 3;
        weapon.shoot.shotDelay = 4;
        weapon.ejectEffect = Fx.casing2;
        return weapon;
    }

    private static Weapon rocketMount(float x, float y, float damage, float range, int shots){
        Weapon weapon = mount("sagitta-missile-mount", x, y, 85, true);
        weapon.bullet = rocket(damage, range * 0.9f, damage * 0.65f);
        weapon.shootSound = Sounds.shootMalign;
        weapon.shoot.shots = shots;
        weapon.shoot.shotDelay = 6;
        return weapon;
    }

    private static Weapon airMount(int tier, float x, float y, float range){
        Weapon weapon = mount("salus-mount", x, y, 24, true);
        FlakBulletType bullet = new FlakBulletType(8, 18 + tier * 7);
        tint(bullet);
        bullet.lifetime = range / 8f;
        bullet.width = 6;
        bullet.height = 9;
        bullet.frontColor = core;
        bullet.backColor = orange;
        bullet.collidesAir = true;
        bullet.collidesGround = bullet.collidesTiles = false;
        bullet.splashDamage = 22 + tier * 6;
        bullet.splashDamageRadius = 20;
        bullet.explodeRange = 18;
        bullet.trailWidth = 1;
        bullet.trailLength = 4;
        weapon.bullet = bullet;
        weapon.autoTarget = true;
        weapon.controllable = false;
        return weapon;
    }

    private static void crown(Weapon weapon, int tier){
        weapon.minWarmup = 0.8f;
        weapon.shootWarmupSpeed = 0.06f;
        ShapePart ring = new ShapePart();
        ring.progress = DrawPart.PartProgress.warmup;
        ring.color = orange;
        ring.circle = ring.hollow = true;
        ring.stroke = 0;
        ring.strokeTo = 1.5f;
        ring.radius = 7 + tier;
        ring.layer = Layer.effect;
        HaloPart halo = new HaloPart();
        halo.progress = DrawPart.PartProgress.warmup;
        halo.color = orange;
        halo.layer = Layer.effect;
        halo.tri = true;
        halo.shapes = tier + 2;
        halo.radius = 2;
        halo.haloRadius = 10 + tier;
        halo.triLength = 0;
        halo.triLengthTo = 5;
        halo.haloRotateSpeed = 1;
        weapon.parts.add(ring, halo);
    }
}
