package cryon.Content;

import arc.graphics.Color;
import cryon.Features.DisasterFactions;
import cryon.Type.*;
import cryon.Fx.NanoFeedbackFx;
import mindustry.content.*;
import mindustry.entities.bullet.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.blocks.production.Drill;
import mindustry.world.meta.*;

public final class NanoColonyContent{
    private static final Color builderBeamColor=Color.valueOf("83ffee");
    public static Drill drill;
    public static NanoMachineFactory factory;
    public static Wall wall;
    public static UnitType eroder;
    public static UnitType armorBreaker,annihilator,weaver;
    public static NanoWarehouse warehouse;
    public static mindustry.world.blocks.distribution.ItemBridge bridge;
    public static RogueUnloader unloader;
    public static NanoMachineFactory largeFactory,combatFactory,ultimateFactory;
    public static NanoCleaver cleaver;

    public static void load(){
        drill = new NanoColonyDrill("rogue-nano-drill");
        drill.requirements(Category.production, BuildVisibility.editorOnly, ItemStack.empty);
        drill.size = 1; drill.health = 900; drill.tier = 4; drill.drillTime = 90f;
        drill.hardnessDrillMultiplier = 0f; drill.itemCapacity = 40; drill.hasLiquids = false;
        drill.rotate = true; drill.drawMineItem = false;
        drill.drillEffect = NanoFeedbackFx.oreBloom;
        drill.updateEffect = NanoFeedbackFx.charge;
        drill.drillEffectChance = 0.03f;
        factory = new NanoMachineFactory("nano-machine-factory");
        wall = new RogueNanoWall("rogue-nano-wall");

        eroder = new UnitType("nano-eroder"){
            @Override public void draw(Unit unit){
                super.draw(unit);
                if(!unit.dead && unit.vel.len2() > 0.04f) NanoFeedbackFx.ambient(NanoFeedbackFx.assimilate, unit.x, unit.y, 0f, 18f);
            }
        };
        legs(eroder,6,14f,3.5f,0.3f);
        eroder.controller = u -> new NanoEroderAI();
        eroder.health = 520; eroder.armor = 4; eroder.speed = 1.4f;
        eroder.hitSize = 12; eroder.rotateSpeed = 7; eroder.range = 104;
        eroder.useUnitCap = false; eroder.playerControllable = eroder.logicControllable = false;
        eroder.createWreck = false; eroder.itemCapacity = 0;
        eroder.targetAir = true; eroder.targetGround = true;
        Weapon weapon = new Weapon("cryon-nano-pulse"){
            { x = 0; y = 1; mirror = false; reload = 40f; shootY = 5; }
        };
        LaserBulletType laser = new LaserBulletType(42f);
        laser.length = 104; laser.width = 8; laser.lifetime = 14;
        laser.colors = new Color[]{Color.valueOf("26764b88"), Color.valueOf("4ce68c"), Color.valueOf("d5ffe5")};
        laser.shootEffect = NanoFeedbackFx.pulseShot;
        laser.hitEffect = NanoFeedbackFx.laserHit;
        weapon.bullet = laser; weapon.shootSound = Sounds.shootLancer;
        eroder.weapons.add(weapon);
        loadIndustry();
    }

    private static void loadIndustry(){
        weaver=new UnitType("nano-weaver"){
            @Override public void draw(Unit unit){
                super.draw(unit);
                mindustry.entities.units.BuildPlan plan=unit.buildPlan();
                if(plan!=null&&plan.tile()!=null&&plan.tile().build instanceof mindustry.world.blocks.ConstructBlock.ConstructBuild&&unit.within(plan,buildRange)&&!NanoEnvironment.suppressed(plan.tile())){
                    // Build plans sync natively, so remote clients also see the work beam.
                    arc.graphics.g2d.Draw.z(mindustry.graphics.Layer.buildBeam);
                    arc.graphics.g2d.Draw.color(plan.breaking?mindustry.graphics.Pal.remove:builderBeamColor);
                    arc.graphics.g2d.Lines.stroke(0.7f);
                    mindustry.graphics.Drawf.buildBeam(unit.x,unit.y,plan.drawx(),plan.drawy(),plan.tile().block().size*4f);
                    arc.graphics.g2d.Draw.reset();
                    NanoFeedbackFx.ambient(NanoFeedbackFx.repair,plan.drawx(),plan.drawy(),0f,20f);
                }
            }
        };legs(weaver,6,16f,4f,0.4f);weaver.drawBuildBeam=false;
        weaver.aiController=NanoBuilderAI::new;weaver.controller=u->new NanoBuilderAI();
        weaver.health=900;weaver.armor=8;weaver.speed=2.2f;weaver.hitSize=12;
        weaver.buildSpeed=1.5f;weaver.buildRange=64f;weaver.itemCapacity=120;
        weaver.useUnitCap=false;weaver.playerControllable=weaver.logicControllable=false;
        weaver.createWreck=false;

        armorBreaker=combatUnit("nano-armor-breaker",16000,28,0.85f,32);
        legs(armorBreaker,6,38f,10f,0.45f);
        armorBreaker.range=224;
        Weapon cutter=new Weapon("cryon-nano-specialist-weapon");cutter.x=9;cutter.y=0;cutter.reload=32;cutter.shootY=7;
        LaserBulletType lance=new LaserBulletType(320f);lance.length=224;lance.width=14;lance.pierceArmor=true;lance.colors=beamColors();
        lance.shootEffect=NanoFeedbackFx.pulseShot;lance.hitEffect=NanoFeedbackFx.laserHit;cutter.bullet=lance;armorBreaker.weapons.add(cutter);
        annihilator=combatUnit("nano-annihilator",120000,70,0.45f,68);annihilator.range=360;
        legs(annihilator,8,76f,18f,0.35f);
        Weapon main=new Weapon("cryon-nano-annihilator-weapon");main.x=0;main.y=10;main.mirror=false;main.reload=180;main.continuous=true;main.shootY=18;
        ContinuousLaserBulletType beam=new ContinuousLaserBulletType(600f);beam.length=360;beam.width=16;beam.lifetime=100;beam.colors=beamColors();beam.incendChance=0;
        main.bullet=beam;annihilator.weapons.add(main);
        Weapon flanks=new Weapon("cryon-nano-specialist-weapon");flanks.x=23;flanks.y=-9;flanks.reload=45;flanks.shootY=7;
        LaserBulletType heavy=new LaserBulletType(600);heavy.length=320;heavy.width=17;heavy.colors=beamColors();flanks.bullet=heavy;annihilator.weapons.add(flanks);
        warehouse=new NanoWarehouse("nano-warehouse");unloader=new RogueUnloader("rogue-nano-unloader");
        bridge=new mindustry.world.blocks.distribution.ItemBridge("nano-bridge"){
            @Override public boolean canPlaceOn(Tile tile,mindustry.game.Team team,int rotation){return team==DisasterFactions.nano&&super.canPlaceOn(tile,team,rotation);}
        };
        bridge.requirements(Category.distribution,BuildVisibility.editorOnly,ItemStack.empty);
        bridge.range=12;bridge.transportTime=1f;bridge.hasPower=false;bridge.itemCapacity=120;bridge.health=1200;
        largeFactory=plant("nano-large-factory",5,5000,30,600,weaver,24);
        combatFactory=plant("nano-combat-factory",5,11000,450,1800,armorBreaker,24);
        ultimateFactory=plant("nano-ultimate-factory",7,24000,4000,5400,annihilator,4);
        cleaver=new NanoCleaver("nano-cleaver");
        cryon.Features.RetiredNanoLogistics.load();
    }
    private static Color[] beamColors(){return new Color[]{Color.valueOf("23734b66"),Color.valueOf("4ce68c"),Color.valueOf("ddffe8")};}
    private static void legs(UnitType type,int count,float length,float base,float speed){
        type.constructor=LegsUnit::create;type.legCount=count;type.legLength=length;type.legBaseOffset=base;
        type.legSpeed=speed;type.legGroupSize=2;type.legMoveSpace=1.1f;
        type.legExtension=-length*0.06f;type.legStraightness=0.35f;type.legContinuousMove=true;
        type.mechLegColor=Color.valueOf("7f9d80");
    }
    private static UnitType combatUnit(String name,float health,float armor,float speed,float hit){
        UnitType type=new UnitType(name);type.constructor=UnitEntity::create;type.controller=u->new NanoAssaultAI();
        type.health=health;type.armor=armor;type.speed=speed;type.hitSize=hit;type.rotateSpeed=3;
        type.useUnitCap=false;type.playerControllable=type.logicControllable=false;type.createWreck=false;
        type.targetAir=type.targetGround=true;return type;
    }
    private static NanoMachineFactory plant(String name,int size,int health,int cost,float time,UnitType unit,int limit){
        NanoMachineFactory block=new NanoMachineFactory(name);block.size=size;block.health=health;block.cost=cost;block.craftTime=time;
        block.itemCapacity=Math.max(cost*2,240);block.outputUnit=unit;block.unitLimit=limit;return block;
    }
}
