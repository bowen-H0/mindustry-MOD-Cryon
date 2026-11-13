package cryon.Content;

import arc.graphics.Color;
import cryon.Type.OrbitalUnit;
import cryon.Type.OrbitalUnitType;
import cryon.Type.OrbitalBombBulletType;
import cryon.Type.OrbitalBurnBeamBulletType;
import cryon.Type.OrbitalCombatAI;
import cryon.Type.OrbitalItemTurret;
import cryon.Type.OrbitalHoverUnitType;
import cryon.Type.OrbitalGroundHoverAI;
import cryon.Type.DownwardBurnBeamBulletType;
import cryon.Type.OrbitalSalvoMissile;
import cryon.Type.OmniBurnBeamBulletType;
import cryon.Type.OmniPlasmaBulletType;
import cryon.Type.OrbitalFleetAI;
import cryon.Type.UniversalUnitAssembler;
import cryon.Type.SkywatchBeamBulletType;
import cryon.Type.SkywatchUnitType;
import arc.struct.Seq;
import mindustry.world.blocks.units.UnitFactory;
import mindustry.content.Fx;
import mindustry.entities.bullet.BombBulletType;
import mindustry.gen.EntityMapping;
import mindustry.gen.Sounds;
import mindustry.type.Weapon;
import mindustry.type.ItemStack;
import mindustry.type.PayloadStack;
import mindustry.type.LiquidStack;
import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.world.meta.BuildVisibility;
import cryon.Features.CryonContent;

public final class OrbitalUnits{
    public static OrbitalUnitType startrail;
    public static OrbitalUnitType skywatch;
    public static OrbitalUnitType transit;
    public static OrbitalUnitType halo;
    public static OrbitalUnitType aphelion, firmament;
    public static UnitFactory deploymentPad;
    private OrbitalUnits(){}

    public static void load(){
        OrbitalUnit.entityId = EntityMapping.register("cryon-orbital-unit", OrbitalUnit::new);
        startrail = new OrbitalUnitType("startrail");
        startrail.health = 150f;
        startrail.armor = 1f;
        startrail.hitSize = 10f;
        startrail.speed = 1.4f;
        startrail.accel = 0.04f;
        startrail.drag = 0.035f;
        startrail.rotateSpeed = 3f;
        startrail.faceTarget = false;
        startrail.circleTarget = true;
        startrail.autoDropBombs = true;
        startrail.circleTargetRadius = 24f;
        startrail.targetAir = false;
        startrail.targetGround = true;
        // Like Quad, acquisition range exceeds the small directly-below bombing footprint.
        startrail.range = 96f;
        startrail.itemCapacity = 15;
        startrail.engineOffset = 7f;
        startrail.engineSize = 1.6f;
        startrail.outlineColor = Color.valueOf("44413c");
        startrail.trailLength = 14;
        startrail.trailScl = 0.8f;
        startrail.drawCell = false;
        startrail.clipSize = 420f;

        Weapon bombs = new Weapon();
        bombs.mirror = false;
        bombs.x = 0f;
        bombs.y = 0f;
        bombs.reload = 75f;
        bombs.shootCone = 180f;
        bombs.ignoreRotation = true;
        bombs.minShootVelocity = 0.01f;
        bombs.shootSound = Sounds.none;
        BombBulletType bomb = new OrbitalBombBulletType(32f, 40f);
        bomb.width = bomb.height = 12f;
        bomb.lifetime = 70f;
        bomb.maxRange = 30f;
        bomb.collidesAir = false;
        bomb.collidesGround = true;
        bomb.backColor = bomb.trailColor = Color.valueOf("9bdcf7");
        bomb.frontColor = Color.white;
        bomb.hitEffect = bomb.despawnEffect = Fx.flakExplosion;
        bomb.shootEffect = Fx.none;
        bomb.trailLength = 6;
        bomb.trailWidth = 0.8f;
        bombs.bullet = bomb;
        startrail.weapons.add(bombs);

        skywatch = new SkywatchUnitType("skywatch");
        skywatch.health = 520f;
        skywatch.armor = 3f;
        skywatch.hitSize = 16f;
        skywatch.speed = 1.1f;
        skywatch.accel = 0.035f;
        skywatch.drag = 0.045f;
        skywatch.rotateSpeed = 2.5f;
        skywatch.range = 360f;
        skywatch.aimDst = 0f;
        skywatch.targetAir = false;
        skywatch.targetGround = true;
        skywatch.faceTarget = true;
        skywatch.omniMovement = true;
        skywatch.itemCapacity = 30;
        skywatch.engineOffset = 9f;
        skywatch.engineSize = 2f;
        skywatch.drawCell = false;
        skywatch.outlineColor = Color.valueOf("44413c");
        skywatch.clipSize = 780f;

        Weapon beam = new Weapon();
        beam.mirror = false;
        beam.x = beam.y = 0f;
        beam.reload = 180f;
        beam.shootCone = 360f;
        beam.ignoreRotation = true;
        beam.predictTarget = false;
        beam.autoTarget = false;
        beam.shootSound = Sounds.shootLancer;
        SkywatchBeamBulletType column = new SkywatchBeamBulletType();
        column.shootEffect = Fx.circleColorSpark;
        column.collidesAir = false;
        column.collidesGround = true;
        beam.bullet = column;
        skywatch.weapons.add(beam);

        transit = new OrbitalUnitType("transit"){
            @Override public void setStats(){
                super.setStats();
                stats.add(OrbitalItemTurret.targetsOrbit, true);
            }
        };
        transit.health = 380f;
        transit.armor = 3f;
        transit.hitSize = 14f;
        transit.speed = 1.5f;
        transit.accel = 0.045f;
        transit.drag = 0.04f;
        transit.rotateSpeed = 3f;
        transit.range = 200f;
        transit.targetAir = transit.targetGround = false;
        transit.singleTarget = true;
        transit.faceTarget = true;
        transit.omniMovement = true;
        transit.itemCapacity = 30;
        transit.engineOffset = 7f;
        transit.engineSize = 2f;
        transit.outlineColor = Color.valueOf("44413c");
        transit.drawCell = false;
        transit.clipSize = 500f;
        transit.aiController = OrbitalCombatAI::new;
        transit.controller = unit -> !unit.type.playerControllable || unit.team.isAI() && !unit.team.rules().rtsAi
            ? new OrbitalCombatAI() : new OrbitalCombatAI.Commands();
        Weapon burn = new Weapon();
        burn.mirror = false;
        burn.x = burn.y = 0f;
        burn.shootY = 5f;
        burn.rotate = true;
        burn.rotateSpeed = 8f;
        burn.shootCone = 25f;
        burn.predictTarget = false;
        burn.continuous = burn.alwaysContinuous = true;
        burn.reload = 30f;
        burn.shootSound = Sounds.none;
        OrbitalBurnBeamBulletType tether = new OrbitalBurnBeamBulletType();
        tether.damage = 5f;
        tether.damageInterval = 6f;
        tether.rangeOverride = transit.range;
        tether.drawSize = 500f;
        burn.bullet = tether;
        transit.weapons.add(burn);

        halo = new OrbitalHoverUnitType("halo");
        halo.health = 1100f;
        halo.armor = 6f;
        halo.hitSize = 22f;
        halo.speed = 1.15f;
        halo.accel = 0.035f;
        halo.drag = 0.065f;
        halo.rotateSpeed = 2f;
        halo.range = 240f;
        halo.aimDst = 0f;
        halo.targetAir = false;
        halo.targetGround = true;
        halo.singleTarget = true;
        halo.faceTarget = false;
        halo.omniMovement = true;
        halo.itemCapacity = 60;
        halo.engineOffset = 0f;
        halo.engineSize = 2.2f;
        halo.outlineColor = Color.valueOf("44413c");
        halo.drawCell = false;
        halo.clipSize = 650f;
        halo.aiController = OrbitalGroundHoverAI::new;
        halo.controller = unit -> !unit.type.playerControllable || unit.team.isAI() && !unit.team.rules().rtsAi
            ? new OrbitalGroundHoverAI() : new OrbitalGroundHoverAI.Commands();
        Weapon downward = new Weapon();
        downward.mirror = false;
        downward.x = downward.y = downward.shootY = 0f;
        downward.ignoreRotation = true;
        downward.shootCone = 360f;
        downward.predictTarget = false;
        downward.continuous = downward.alwaysContinuous = true;
        downward.reload = 45f;
        downward.shootSound = Sounds.none;
        DownwardBurnBeamBulletType groundBeam = new DownwardBurnBeamBulletType();
        groundBeam.damage = 12f;
        groundBeam.damageInterval = 6f;
        groundBeam.rangeOverride = halo.range;
        groundBeam.drawSize = 650f;
        downward.bullet = groundBeam;
        halo.weapons.add(downward);

        aphelion = fleet("aphelion", 3600f, 9f, 30f, 0.9f, 400f, false);
        aphelion.engineOffset = 18f;
        aphelion.engineSize = 3f;
        aphelion.itemCapacity = 100;
        Weapon salvo = new Weapon();
        salvo.mirror = false;
        salvo.x = 0f; salvo.y = -2f;
        salvo.rotate = true;
        salvo.rotateSpeed = 4f;
        salvo.shootCone = 30f;
        salvo.reload = 210f;
        salvo.shoot.shots = 8;
        salvo.shoot.shotDelay = 7f;
        salvo.shootSound = Sounds.shootMissile;
        salvo.bullet = new OrbitalSalvoMissile(aphelion.range);
        aphelion.weapons.add(salvo);

        firmament = fleet("firmament", 15000f, 18f, 48f, 0.55f, 400f, true);
        firmament.engineOffset = 27f;
        firmament.engineSize = 4.5f;
        firmament.itemCapacity = 180;
        firmament.clipSize = 1100f;
        for(float[] position : new float[][]{{0f, 13f}, {-10f, -7f}, {10f, -7f}}){
            Weapon lance = new Weapon();
            lance.mirror = false;
            lance.x = position[0]; lance.y = position[1];
            lance.rotate = true; lance.rotateSpeed = 5f;
            lance.shootY = 2f;
            lance.shootCone = 360f; lance.ignoreRotation = true;
            lance.predictTarget = false;
            lance.continuous = lance.alwaysContinuous = true;
            lance.reload = 45f;
            lance.shootSound = Sounds.none;
            lance.bullet = new OmniBurnBeamBulletType();
            firmament.weapons.add(lance);
        }
        OmniPlasmaBulletType plasma = new OmniPlasmaBulletType();
        for(int side : new int[]{-1, 1}) for(int row = 0; row < 4; row++){
            Weapon gun = new Weapon();
            gun.mirror = false;
            gun.x = side * 17f; gun.y = -17f + row * 10f;
            gun.rotate = true; gun.rotateSpeed = 7f;
            gun.shootY = 3f;
            gun.shootCone = 25f;
            gun.reload = 12f;
            gun.inaccuracy = 2f;
            gun.shootSound = Sounds.shootLaser;
            gun.bullet = plasma;
            firmament.weapons.add(gun);
        }
        deploymentPad = new UnitFactory("orbital-deployment-pad"){
            @Override public void init(){
                requirements(Category.units, ItemStack.with(CryonContent.item("aluminum"), 200,
                        Items.silicon, 140, Items.graphite, 120, Items.titanium, 80));
                plans = Seq.with(new UnitPlan(skywatch, 3600f, ItemStack.with(
                        CryonContent.item("aluminum"), 100, Items.silicon, 80, Items.graphite, 60, Items.titanium, 40)));
                super.init();
            }
        };
        deploymentPad.size = 4;
        deploymentPad.health = 1200;
        deploymentPad.hasPower = deploymentPad.hasItems = true;
        deploymentPad.buildVisibility = BuildVisibility.shown;
        deploymentPad.consumePower(6f);
    }

    public static void configureProduction(){
        assemble(3, halo, transit, "composite-wall-large", 4, 2400f, "hydrogen", 15f);
        assemble(4, aphelion, halo, "charged-surge-wall-large", 6, 4500f, "nitrogen", 22f);
        assemble(5, firmament, aphelion, "cryo-alloy-wall-large", 10, 7200f, "cryofluid", 32f);
    }

    private static void assemble(int tier, OrbitalUnitType output, OrbitalUnitType input, String wall, int walls,
                                 float time, String liquid, float rate){
        UniversalUnitAssembler assembler = (UniversalUnitAssembler)CryonContent.block("t" + tier + "universal-assembler");
        if(assembler.plans.contains(plan -> plan.unit == output)) return;
        UniversalUnitAssembler.AssemblerUnitPlan plan = new UniversalUnitAssembler.AssemblerUnitPlan(output, time,
            Seq.with(new PayloadStack(input, 2), new PayloadStack(CryonContent.block(wall), walls)));
        plan.liquidReq = new LiquidStack[]{new LiquidStack(CryonContent.liquid(liquid), rate / 60f)};
        assembler.plans.add(plan);
    }

    private static OrbitalUnitType fleet(String name, float health, float armor, float size, float speed, float range, boolean orbit){
        OrbitalUnitType type = new OrbitalUnitType(name){
            @Override public void setStats(){ super.setStats(); if(orbit) stats.add(OrbitalItemTurret.targetsOrbit, true); }
        };
        type.health = health; type.armor = armor; type.hitSize = size; type.speed = speed;
        type.accel = 0.025f; type.drag = 0.05f; type.rotateSpeed = orbit ? 1.2f : 1.8f;
        type.range = range; type.targetAir = type.targetGround = true;
        type.singleTarget = false; type.faceTarget = true; type.omniMovement = true;
        type.drawCell = false; type.outlineColor = Color.valueOf("44413c"); type.clipSize = 850f;
        type.aiController = () -> new OrbitalFleetAI(orbit);
        type.controller = unit -> !unit.type.playerControllable || unit.team.isAI() && !unit.team.rules().rtsAi
            ? new OrbitalFleetAI(orbit) : new OrbitalFleetAI.Commands(orbit);
        return type;
    }
}
