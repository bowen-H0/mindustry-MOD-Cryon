package cryon.Content;

import cryon.Features.CryonContent;
import cryon.Type.EnergyBladeWeapon;
import cryon.Fx.SubsonicParryFx;
import arc.math.Angles;
import arc.graphics.g2d.Draw;
import arc.math.geom.Vec2;
import mindustry.gen.Unit;
import mindustry.gen.MechUnit;
import mindustry.type.*;
import mindustry.world.blocks.units.UnitFactory;
import mindustry.world.blocks.units.Reconstructor;
import cryon.Type.OverclockSupportAbility;
import cryon.Type.AfterimageDashAbility;
import cryon.Type.ExtremeOverclockAbility;
import cryon.Type.MechFluxSuppressionAbility;
import cryon.Type.SuperluminalBladeWeapon;
import cryon.Features.MechAudio;
import cryon.Type.UniversalUnitAssembler;
import arc.struct.Seq;

/** Fast T1 close-combat mech, with independently posed two-bone arms. */
public final class ShadowMech{
    public static UnitType subsonic, transonic, supersonic, relativistic, superluminal;
    public static StatusEffect extremeOverclock;

    public static void load(){
        MechAudio.load();
        extremeOverclock = new StatusEffect("extreme-overclock"){{
            color = ExtremeOverclockAbility.cyan;
            speedMultiplier = ExtremeOverclockAbility.timeScale;
            reloadMultiplier = ExtremeOverclockAbility.timeScale;
            effectChance = 0;
        }};
        subsonic = create("subsonic", 1f, 100, 0, 1.5f, 180, 50);
        transonic = create("transonic", 1.5f, 300, 3, 1.65f, 480, 48);
        transonic.abilities.add(new OverclockSupportAbility());
        supersonic = create("supersonic", 2.1f, 900, 7, 1.7f, 1000, 50);
        supersonic.abilities.add(new AfterimageDashAbility());
        supersonic.abilities.add(new MechFluxSuppressionAbility(160f));
        relativistic = create("relativistic", 2.8f, 4000, 12, 1.75f, 2400, 56);
        relativistic.aiController = ExtremeOverclockAbility.PerceptionAI::new;
        relativistic.controller = unit -> !relativistic.playerControllable
            || (unit.team.isAI() && !unit.team.rules().rtsAi)
            ? relativistic.aiController.get() : new ExtremeOverclockAbility.PerceptionCommandAI();
        relativistic.abilities.add(new ExtremeOverclockAbility());
        relativistic.abilities.add(new MechFluxSuppressionAbility(256f));
        superluminal = create("superluminal", 3.6f, 12000, 16, 1.8f, 600, 8);
        superluminal.weapons.clear();
        SuperluminalBladeWeapon blade = new SuperluminalBladeWeapon("superluminal", 3.6f, 600f);
        superluminal.weapons.add(blade);
        superluminal.range = blade.reach;
        superluminal.clipSize = blade.reach * 2f + 256f;
    }

    private static UnitType create(String name, float scale, float health, float armor, float speed, float damage, float reload){
        UnitType type = new UnitType(name){
            private final Vec2 torsoOrigin=new Vec2();
            private void drawTorso(Unit unit, boolean cell){
                EnergyBladeWeapon.BladeMount blade = (EnergyBladeWeapon.BladeMount)unit.mounts[0];
                Vec2 origin = EnergyBladeWeapon.poseOrigin(unit, blade, blade.age,torsoOrigin);
                float chroma = AfterimageDashAbility.chroma(unit);
                if(!cell && chroma > 0){
                    for(int side=-1;side<=1;side+=2){
                        Draw.color(side < 0 ? SubsonicParryFx.pink : SubsonicParryFx.cyan, 0.8f * chroma);
                        Draw.rect(region, origin.x + Angles.trnsx(unit.rotation + 90, side * 3 * chroma),
                            origin.y + Angles.trnsy(unit.rotation + 90, side * 3 * chroma),
                            unit.rotation + EnergyBladeWeapon.torsoTwist(unit) - 90);
                    }
                }
                if(!cell && blade.parryFlash > 0){
                    float flash = blade.parryFlash / 10f;
                    for(int side=-1;side<=1;side+=2){
                        Draw.color(side < 0 ? SubsonicParryFx.pink : SubsonicParryFx.cyan, 0.4f * flash);
                        Draw.rect(region, origin.x + Angles.trnsx(unit.rotation + 90, side * 2.2f * flash),
                            origin.y + Angles.trnsy(unit.rotation + 90, side * 2.2f * flash),
                            unit.rotation + EnergyBladeWeapon.torsoTwist(unit) - 90);
                    }
                }
                applyColor(unit);
                if(cell) Draw.color(cellColor(unit));
                Draw.rect(cell ? cellRegion : region, origin.x, origin.y,
                    unit.rotation + EnergyBladeWeapon.torsoTwist(unit) - 90);
                Draw.reset();
            }
            @Override public void drawBody(Unit unit){ drawTorso(unit, false); }
            @Override public void drawCell(Unit unit){ drawTorso(unit, true); }
        };
        type.constructor = MechUnit::create;
        type.health = health;
        type.armor = armor;
        type.speed = speed;
        type.accel = 0.16f;
        type.drag = 0.12f;
        type.hitSize = 10 * scale;
        type.rotateSpeed = 8;
        type.mechSideSway = 0.3f;
        type.mechStepParticles = true;
        type.stepSound = scale > 1 ? MechAudio.stepHeavy : MechAudio.stepLight;
        type.stepSoundVolume = scale >= 2 ? 0.38f : scale > 1 ? 0.3f : 0.22f;
        type.stepSoundPitch = scale >= 2 ? 0.87f : scale > 1 ? 0.97f : 1.08f;
        type.stepSoundPitchRange = 0.07f;
        type.deathSound = MechAudio.collapse;
        type.deathSoundVolume = scale >= 2 ? 0.7f : 0.5f;
        type.targetAir = false;
        type.targetGround = true;
        type.faceTarget = true;
        type.range = 29 * scale;
        type.weapons.add(new EnergyBladeWeapon(name, scale, damage, reload));
        return type;
    }

    public static void configureProduction(){
        UniversalUnitAssembler t5 = (UniversalUnitAssembler)CryonContent.block("t5universal-assembler");
        if(!t5.plans.contains(plan -> plan.unit == superluminal)){
            UniversalUnitAssembler.AssemblerUnitPlan plan = new UniversalUnitAssembler.AssemblerUnitPlan(
                superluminal, 7200, Seq.with(new PayloadStack(relativistic, 2),
                    new PayloadStack(CryonContent.block("cryo-alloy-wall-large"), 8)));
            plan.liquidReq = new LiquidStack[]{new LiquidStack(CryonContent.liquid("cryofluid"), 30f / 60f)};
            t5.plans.add(plan);
        }
        UniversalUnitAssembler t4 = (UniversalUnitAssembler)CryonContent.block("t4universal-assembler");
        if(!t4.plans.contains(plan -> plan.unit == relativistic)){
            UniversalUnitAssembler.AssemblerUnitPlan plan = new UniversalUnitAssembler.AssemblerUnitPlan(
                relativistic, 4500, Seq.with(new PayloadStack(supersonic, 2),
                    new PayloadStack(CryonContent.block("charged-surge-wall-large"), 6)));
            plan.liquidReq = new LiquidStack[]{new LiquidStack(CryonContent.liquid("nitrogen"), 22f / 60f)};
            t4.plans.add(plan);
        }
        UniversalUnitAssembler t3 = (UniversalUnitAssembler)CryonContent.block("t3universal-assembler");
        if(!t3.plans.contains(plan -> plan.unit == supersonic)){
            UniversalUnitAssembler.AssemblerUnitPlan plan = new UniversalUnitAssembler.AssemblerUnitPlan(
                supersonic, 2400, Seq.with(new PayloadStack(transonic, 2),
                    new PayloadStack(CryonContent.block("composite-wall-large"), 4)));
            plan.liquidReq = new LiquidStack[]{new LiquidStack(CryonContent.liquid("hydrogen"), 15f / 60f)};
            t3.plans.add(plan);
        }
        Reconstructor reconstructor = (Reconstructor)CryonContent.block("t2factory");
        if(!reconstructor.upgrades.contains(pair -> pair[0] == subsonic && pair[1] == transonic)){
            reconstructor.addUpgrade(subsonic, transonic);
        }
        UnitFactory factory = (UnitFactory)CryonContent.block("unit-projector");
        if(!factory.plans.contains(p -> p.unit == subsonic)){
            factory.plans.add(new UnitFactory.UnitPlan(subsonic, 1500,
                ItemStack.with(CryonItems.magnesium, 25, mindustry.content.Items.silicon, 20,
                    mindustry.content.Items.graphite, 10)));
            factory.initCapacities();
        }
    }
}
