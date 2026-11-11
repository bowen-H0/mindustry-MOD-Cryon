package cryon.Content;

import arc.graphics.Color;
import arc.math.geom.Rect;
import arc.struct.Seq;
import cryon.Features.CryonContent;
import cryon.Type.MissileTruckBullets;
import cryon.Type.UniversalUnitAssembler;
import mindustry.content.Fx;
import mindustry.gen.TankUnit;
import mindustry.gen.Sounds;
import mindustry.type.*;
import mindustry.world.blocks.units.Reconstructor;
import mindustry.world.blocks.units.UnitFactory;

/** Cryon's five-tier golden missile carrier line. */
public final class MissileTruckUnits{
    public static final String[] names = {"jinwu", "xihe", "fusang", "yaoling", "zhulong"};
    public static final UnitType[] line = new UnitType[5];
    public static void load(){
        // Keep missile carriers more fragile than Cryon's same-tier frontline tanks.
        float[] health = {90, 315, 1500, 7500, 28500};
        float[] armor = {0, 4, 8, 12, 20};
        float[] size = {13, 19, 28, 40, 56};
        float[] range = {160, 240, 330, 440, 600};
        float[] reload = {70, 110, 250, 480, 720};
        int[] pixels = {56, 80, 112, 160, 224};
        for(int tier = 0; tier < 5; tier++){
            UnitType type = new UnitType(names[tier]);
            line[tier] = type;
            type.constructor = TankUnit::create;
            type.health = health[tier]; type.hitSize = size[tier];
            type.armor = armor[tier];
            type.speed = new float[]{1.35f, 1.1f, 0.85f, 0.65f, 0.5f}[tier];
            type.rotateSpeed = 2.8f - tier * 0.35f;
            type.accel = 0.06f; type.drag = 0.09f;
            type.omniMovement = false; type.rotateMoveFirst = true;
            type.targetAir = tier > 0; type.targetGround = true;
            type.faceTarget = true; type.range = range[tier];
            type.outlineColor = Color.valueOf("252d39");
            type.treadRects = new Rect[]{new Rect(-pixels[tier] * 0.46f, -pixels[tier] * 0.4f,
                    pixels[tier] * 0.19f, pixels[tier] * 0.8f)};
            type.treadFrames = 4;
            type.crushFragile = tier >= 3;
            type.crushDamage = tier >= 3 ? (tier == 3 ? 3f : 6f) : 0f;
            Weapon launcher = new Weapon("cryon-" + names[tier] + "-launcher");
            launcher.mirror = false; launcher.rotate = true; launcher.rotateSpeed = 2.4f;
            launcher.x = 0; launcher.y = -size[tier] * 0.07f;
            launcher.shootY = tier == 1 ? -size[tier] * 0.75f : size[tier] * 0.33f;
            launcher.reload = reload[tier]; launcher.recoil = 1.5f + tier;
            launcher.shootCone = 12; launcher.inaccuracy = 0;
            launcher.shootSound = Sounds.shootMissile;
            if(tier == 1){
                launcher.shoot.shots = 3;
                launcher.shoot.shotDelay = 0;
            }
            launcher.shootStatusDuration = tier >= 2 ? 90 : 0;
            launcher.bullet = MissileTruckBullets.create(tier, range[tier]);
            launcher.bullet.shootEffect = Fx.shootSmall;
            type.weapons.add(launcher);
        }
    }

    /** Called after HJSON factories exist; use native factory/reconstructor/assembler plans. */
    public static void configureProduction(UniversalUnitAssembler t3, UniversalUnitAssembler t4, UniversalUnitAssembler t5){
        UnitFactory factory = (UnitFactory)CryonContent.block("unit-projector");
        if(!factory.plans.contains(p -> p.unit == line[0])) factory.plans.add(new UnitFactory.UnitPlan(line[0], 2100,
                ItemStack.with(CryonItems.magnesium, 35, mindustry.content.Items.silicon, 25, mindustry.content.Items.graphite, 10)));
        factory.initCapacities();
        Reconstructor reconstructor = (Reconstructor)CryonContent.block("t2factory");
        if(!reconstructor.upgrades.contains(pair -> pair[0] == line[0])) reconstructor.addUpgrade(line[0], line[1]);
        UniversalUnitAssembler[] assemblers = {t3, t4, t5};
        String[] walls = {"composite-wall-large", "charged-surge-wall-large", "cryo-alloy-wall-large"};
        String[] fluids = {"hydrogen", "nitrogen", "cryofluid"};
        for(int i = 0; i < 3; i++){
            final UnitType output = line[i + 2];
            if(assemblers[i].plans.contains(p -> p.unit == output)) continue;
            UniversalUnitAssembler.AssemblerUnitPlan plan = new UniversalUnitAssembler.AssemblerUnitPlan(output,
                    new float[]{2400, 4500, 7200}[i], Seq.with(new PayloadStack(line[i + 1], 2),
                    new PayloadStack(CryonContent.block(walls[i]), new int[]{4, 6, 10}[i])));
            plan.liquidReq = new LiquidStack[]{new LiquidStack(CryonContent.liquid(fluids[i]), new float[]{15, 22, 32}[i] / 60f)};
            assemblers[i].plans.add(plan);
        }
    }
}
