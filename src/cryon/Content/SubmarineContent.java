package cryon.Content;

import arc.Core;
import arc.graphics.Color;
import arc.struct.Seq;
import cryon.Features.CryonContent;
import cryon.Type.*;
import mindustry.Vars;
import mindustry.ai.UnitStance;
import mindustry.ai.types.GroundAI;
import mindustry.content.*;
import mindustry.entities.abilities.RepairFieldAbility;
import mindustry.entities.bullet.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.type.weapons.*;
import mindustry.world.blocks.units.Reconstructor;
import mindustry.world.meta.*;

public final class SubmarineContent{
    public static SubmarineUnitType minimus, danae, pammelas, schmidti, sloani;
    public static StatusEffect diveStatus, pressureStatus;
    public static UnitStance diveStance;
    public static SeaMineBlock seaMine;
    public static final Color teal = Color.valueOf("76ced1");

    private SubmarineContent(){}

    public static boolean diving(Unit unit){ return diveStatus != null && unit.hasEffect(diveStatus); }
    public static boolean submarine(UnitType type){ return type instanceof SubmarineUnitType; }

    public static void load(){
        SubmarineUnit.entityId = EntityMapping.register("cryon-submarine", SubmarineUnit::new);
        diveStance = new UnitStance("deep-dive", "download", null);
        diveStatus = new StatusEffect("deep-diving"){{
            color = teal;
            permanent = true;
            speedMultiplier = 0.55f;
            effect = Fx.none;
        }};
        pressureStatus = new StatusEffect("depth-pressure"){{
            color = Color.valueOf("526b94");
            disarm = true;
            speedMultiplier = 0.05f;
            buildSpeedMultiplier = 0f;
            effect = Fx.none;
        }};
        minimus = hull("minimus", 0, 420f, 3f, 13f, 0.95f);
        danae = hull("danae", 1, 1100f, 5f, 18f, 0.88f);
        pammelas = hull("pammelas", 2, 3400f, 9f, 27f, 0.76f);
        schmidti = hull("schmidti", 3, 9200f, 14f, 39f, 0.66f);
        sloani = hull("sloani", 4, 23500f, 22f, 58f, 0.56f);
        seaMine = new SeaMineBlock("abyssal-mine");
        new PoweredPump("powered-pump");
        new WaterCurrentGenerator("underice-current-generator");
    }

    private static SubmarineUnitType hull(String name, int tier, float health, float armor, float size, float speed){
        SubmarineUnitType type = new SubmarineUnitType(name);
        type.health = health; type.armor = armor; type.hitSize = size;
        type.speed = speed; type.accel = 0.12f; type.drag = 0.08f; type.rotateSpeed = 3.6f - tier * 0.4f;
        type.flying = false; type.faceTarget = false;
        type.range = 150f + tier * 44f;
        type.detectionRange = 104f + tier * 10f;
        type.aiController = GroundAI::new;
        // v160 removed unit-ammo classes; use the optional v157 power-ammo field when present.
        try{
            Object ammo = Class.forName("mindustry.type.ammo.PowerAmmoType").getConstructor(float.class).newInstance(1200f + tier * 1000f);
            UnitType.class.getField("ammoType").set(type, ammo);
        }catch(ReflectiveOperationException ignored){}
        type.trailLength = 10; type.trailScl = 1f;
        type.waveTrailX = size * 0.25f; type.waveTrailY = -size * 0.3f;
        type.itemCapacity = 20 + tier * 20;
        type.envEnabled |= Env.underwater;
        type.immunities.add(pressureStatus);
        type.immunities.add(StatusEffects.freezing);
        type.stances.addAll(UnitStance.stop, UnitStance.holdFire, UnitStance.pursueTarget,
                UnitStance.patrol, UnitStance.holdPosition, diveStance);
        type.abilities.add(new DeepDiveAbility());

        RepairBeamWeapon beam = new RepairBeamWeapon("cryon-submarine-repair-beam");
        beam.mirror = false; beam.x = 0f; beam.y = -size * 0.18f;
        beam.repairSpeed = 0.65f + tier * 0.7f;
        beam.beamWidth = 0.8f + tier * 0.2f;
        beam.targetUnits = beam.targetBuildings = true;
        beam.bullet = new BulletType(); beam.bullet.maxRange = 130f + tier * 35f;
        type.weapons.add(beam);

        Weapon laser = new Weapon("cryon-submarine-laser");
        laser.x = size * 0.3f; laser.y = -size * 0.08f;
        laser.rotate = true; laser.rotateSpeed = 5f;
        laser.reload = Math.max(11f, 26f - tier * 3f);
        laser.shootSound = Sounds.shootLaser;
        LaserBoltBulletType bolt = new LaserBoltBulletType(5.2f, 12f + tier * 9f);
        bolt.lifetime = 35f + tier * 7f;
        bolt.healPercent = 4f + tier;
        bolt.collidesTeam = true;
        bolt.backColor = bolt.hitColor = teal;
        bolt.frontColor = Color.white;
        laser.bullet = bolt;
        type.weapons.add(laser);

        Weapon torpedo = new Weapon();
        torpedo.mirror = false; torpedo.x = 0f; torpedo.y = size * 0.18f;
        torpedo.rotate = true; torpedo.rotateSpeed = 2f;
        torpedo.reload = tier < 2 ? 100f - tier * 10f : 180f + (tier - 2) * 30f;
        torpedo.shootSound = Sounds.shootRetusa;
        if(tier >= 2){
            float[] direct = {620f, 1050f, 1850f}, splash = {500f, 900f, 1600f}, radius = {56f, 80f, 104f};
            torpedo.bullet = new HeavyTorpedoBulletType(direct[tier - 2], splash[tier - 2], radius[tier - 2]);
        }else{
            TorpedoBulletType light = new TorpedoBulletType(0.9f, 40f + tier * 24f);
            light.sprite = "missile"; light.width = 5f; light.height = 10f;
            light.lifetime = 200f; light.underwater = true; light.collidesAir = false;
            light.homingPower = 0.018f; light.homingRange = 120f; light.homingDelay = 20f;
            light.splashDamage = 35f + tier * 35f; light.splashDamageRadius = 22f + tier * 6f;
            light.backColor = light.trailColor = teal; light.frontColor = Color.white;
            light.trailLength = 10; light.trailWidth = 1.4f;
            light.hitEffect = Fx.blastExplosion;
            torpedo.bullet = light;
        }
        type.weapons.add(torpedo);
        if(tier >= 1){
            PointDefenseWeapon defense = new PointDefenseWeapon("cryon-submarine-point-defense");
            defense.mirror = false; defense.y = -size * 0.26f;
            defense.reload = 12f - tier;
            defense.bullet = new BulletType(); defense.bullet.maxRange = 90f + tier * 15f;
            defense.bullet.damage = 10f + tier * 4f;
            defense.bullet.shootEffect = Fx.sparkShoot;
            defense.bullet.hitEffect = Fx.pointHit;
            type.weapons.add(defense);
            SeaMineLayerAbility mines = new SeaMineLayerAbility(new SeaMineBulletType(150f + tier * 110f, 30f + tier * 5f));
            mines.reload = 360f - tier * 30f; mines.maxMines = 3 + tier;
            type.abilities.add(mines);
        }
        if(tier >= 3) type.abilities.add(new RepairFieldAbility(80f + (tier - 3) * 80f, 180f, 140f + tier * 20f));
        return type;
    }

    /** Called after the existing Cryon assemblers have received their regular plans. */
    public static void configureProduction(){
        Reconstructor t2 = (Reconstructor)CryonContent.block("t2factory");
        if(!t2.upgrades.contains(path -> path[0] == minimus && path[1] == danae)) t2.addUpgrade(minimus, danae);
        addAssembly("t3universal-assembler", danae, pammelas, "composite-wall-large", 4, "hydrogen", 15f / 60f, 2400f,
                ItemStack.with(CryonItems.nickel, 40, Items.silicon, 80, CryonItems.farstarAlloy, 20));
        addAssembly("t4universal-assembler", pammelas, schmidti, "charged-surge-wall-large", 5, "nitrogen", 20f / 60f, 4200f,
                ItemStack.with(CryonItems.nickel, 100, Items.surgeAlloy, 70, Items.graphite, 100));
        addAssembly("t5universal-assembler", schmidti, sloani, "cryo-alloy-wall-large", 8, "cryofluid", 30f / 60f, 6600f,
                ItemStack.with(CryonItems.cryoAlloy, 100, CryonItems.nanoMaterial, 60, Items.phaseFabric, 100));
        seaMine.requirements(Category.effect, ItemStack.with(CryonItems.aluminum, 25, Items.graphite, 20, Items.titanium, 10));
    }

    private static void addAssembly(String name, UnitType previous, UnitType output, String wall, int walls,
                                    String liquid, float rate, float time, ItemStack[] items){
        UniversalUnitAssembler assembler = (UniversalUnitAssembler)CryonContent.block(name);
        if(assembler.plans.contains(plan -> plan.unit == output)) return;
        UniversalUnitAssembler.AssemblerUnitPlan plan = new UniversalUnitAssembler.AssemblerUnitPlan(output, time,
                Seq.with(new PayloadStack(previous, 2), new PayloadStack(CryonContent.block(wall), walls)));
        plan.itemReq = items;
        plan.liquidReq = LiquidStack.with(CryonContent.liquid(liquid), rate);
        assembler.plans.add(plan);
        assembler.initCapacities();
    }
}
