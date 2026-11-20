import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.*;
import arc.util.io.*;
import java.io.*;
import java.nio.file.Files;
import mindustry.*;
import mindustry.ai.UnitCommand;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.abilities.MoveEffectAbility;
import mindustry.game.Team;
import mindustry.game.Objectives;
import mindustry.game.Saves;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.*;
import mindustry.net.Net;
import mindustry.type.*;
import mindustry.world.Tile;
import mindustry.world.blocks.payloads.UnitPayload;
import mindustry.world.blocks.units.UnitFactory;

/** Packaged manufacture, combat layers, native hover movement, transport commands and serialization. */
public class AravisRtsCheck{
    static UnitType unit(String name){return Vars.content.unit("cryon-aravis-"+name);}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void tick(Building build,int count){for(int i=0;i<count;i++){Time.time++;Time.delta=1f;build.updateConsumption();build.updateTile();}}
    static void index(){
        Vars.state.teams.updateTeamStats();
        for(Team team:new Team[]{Team.sharded,Team.crux}){
            team.data().tree().clear();
            for(Unit unit:Groups.unit)if(unit.team==team)team.data().tree().insert(unit);
        }
    }
    public static void main(String[] args){try{run();System.exit(0);}catch(Throwable error){error.printStackTrace();System.exit(1);}}
    static void run() throws Exception{
        Core.app=new MockApplication();Core.files=new MockFiles();Core.graphics=new MockGraphics();Core.audio=new MockAudio();
        Core.settings=new Settings();Core.settings.setDataDirectory(new Fi(Files.createTempDirectory("cryon-aravis-rts-").toFile()));
        Core.bundle=I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(Core.settings.getDataDirectory().child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(Core.settings.getDataDirectory().child("mods/unitlaunch.jar"));
        Vars.headless=true;Vars.loadLocales=false;Vars.net=new Net(null);Vars.platform=new mindustry.core.Platform(){};
        mindustry.core.Version.init();Vars.init();Vars.content.createBaseContent();Vars.content.createModContent();Vars.content.init();
        var mod=Vars.mods.locateMod("cryon");check(mod!=null&&!mod.hasContentErrors(),"Packaged content errors");
        ClassLoader loader=mod.main.getClass().getClassLoader();
        loader.loadClass("cryon.TechTree.AravisTechTree").getMethod("load").invoke(null);Timer.instance().clear();
        ridgeUnlocks();
        loader.loadClass("cryon.Features.MapAlliances").getMethod("install").invoke(null);
        Vars.logic=new Logic();Vars.logic.reset();Vars.state.rules.unitCap=100;Vars.state.rules.canGameOver=false;
        Vars.world.loadGenerator(100,100,tiles->{
            for(int x=0;x<100;x++)for(int y=0;y<100;y++)tiles.set(x,y,new Tile(x,y,
                x>=45&&x<=55&&y>=45&&y<=55?Blocks.deepwater:Blocks.stone,Blocks.air,Blocks.air));
        });
        Vars.state.set(mindustry.core.GameState.State.playing);Time.delta=1f;
        String[] army={"light-tank","flak-car","field-artillery","hover-tank","shorekeeper"};
        String[] air={"needlewing","violet-falcon","ash-kite"};
        factory("army",army,15,20);factory("air",air,15,40);
        check(unit("needlewing").health<unit("ash-kite").health&&unit("ash-kite").health<unit("violet-falcon").health,
            "Aircraft durability roles lost");
        check(unit("field-artillery").health<Vars.content.unit("cryon-jaspis").health
            &&unit("field-artillery").range>unit("light-tank").range*1.7f,"Artillery is not fragile and long ranged");
        check(unit("hover-tank").getTotalRequirements()[0].amount>0,"Hover tank has no production recipe");
        for(String name:new String[]{"shorekeeper","hover-tank"}){
            Unit hover=unit(name).create(Team.sharded);
            check(!hover.isFlying()&&hover.type.hovering&&!hover.type.canDrown,"Hover unit became an aircraft or can drown");
            check(hover.canPass(50,50),"Hover unit cannot traverse deep water");
            Vars.world.tile(50,50).setBlock(Blocks.copperWall,Team.crux);
            check(!hover.canPass(50,50),"Hover unit crosses solid walls");Vars.world.tile(50,50).remove();
        }
        check(unit("light-tank").canDrown&&!unit("light-tank").hovering
            &&unit("light-tank").flowfieldPathType!=unit("hover-tank").flowfieldPathType,"Ordinary tank lost ground navigation/drowning");
        noAircraftJump(loader);ranges();combat();transport();
        System.out.println("ARAVIS_RTS_PASS: 8 researched units, 3/5 headquarters recipes, resource/power gating and consumption, air/ground combat isolation, ground hover water/wall limits, native RTS loading/unloading, payload capacity and serialization");
    }
    static void noAircraftJump(ClassLoader loader) throws Exception{
        Class<?> jump=loader.loadClass("cryon.Features.SpaceJump"),ability=loader.loadClass("cryon.Type.SpaceJumpAbility");
        if(Vars.netServer==null)Vars.netServer=new mindustry.core.NetServer();
        if(Vars.netClient==null)Vars.netClient=new mindustry.core.NetClient();
        jump.getMethod("install").invoke(null);
        UnitCommand command=(UnitCommand)jump.getField("jumpCommand").get(null);
        var previous=Vars.state.rules.sector;Vars.state.rules.sector=Vars.content.sector("cryon-ridge").sector;
        try{
            for(String name:new String[]{"violet-falcon","ash-kite","needlewing"}){
                UnitType type=unit(name);Unit plane=type.create(Team.sharded);plane.set(200,200);
                check((boolean)jump.getMethod("excluded",UnitType.class).invoke(null,type),"Aravis aircraft still eligible for jump: "+name);
                check(!type.commands.contains(command)&&!type.abilities.contains(ability::isInstance),"Jump ability/RTS command installed: "+name);
                check(!type.engines.contains(engine->engine.getClass().getSimpleName().equals("DigitalEngine")),"Digital jump engine installed: "+name);
                Object legacy=ability.getConstructor().newInstance();
                check(!(boolean)ability.getMethod("ready",Unit.class).invoke(legacy,plane)
                    &&!(boolean)ability.getMethod("jump",Unit.class,float.class,float.class).invoke(legacy,plane,600f,200f),
                    "Legacy/manual jump bypasses aircraft exclusion: "+name);
            }
        }finally{Vars.state.rules.sector=previous;}
        UnitType existing=Vars.content.unit("cryon-halo");
        check(existing!=null&&existing.commands.contains(command)&&existing.abilities.contains(ability::isInstance),
            "Existing Cryon jump-capable aircraft lost jump");
        System.out.println("ARAVIS_NO_JUMP_PASS: three aircraft excluded from ability, RTS command, digital engines and legacy manual execution; existing Cryon jump preserved");
    }
    static void ranges(){
        String[] names={"violet-falcon","ash-kite","needlewing","field-artillery","hover-tank","light-tank","flak-car"};
        float[] expected={120f,112f,96f,210f,120f,96f,136f};
        for(int i=0;i<names.length;i++){
            UnitType type=unit(names[i]);BulletType bullet=type.weapons.first().bullet;
            check(Math.abs(type.range-expected[i])<0.001f&&Math.abs(bullet.range-expected[i])<0.001f,
                "Target/projectile range mismatch: "+names[i]);
            check(Math.abs(bullet.speed*bullet.lifetime-expected[i])<0.001f&&!bullet.keepVelocity,
                "Physical projectile travel exceeds configured range: "+names[i]);
        }
        var chisel=(mindustry.world.blocks.defense.turrets.Turret)Vars.content.block("cryon-chisel");
        check(unit("field-artillery").range==chisel.range,"Fargrit must match Chisel turret range");
        for(String name:new String[]{"shorekeeper","hover-tank"}){
            UnitType type=unit(name);
            check(type.getClass().getSimpleName().equals("AravisHoverUnitType")
                &&type.abilities.count(a->a instanceof MoveEffectAbility)==2,"Missing hover render/wake effects: "+name);
        }
        System.out.println("ARAVIS_RANGE_PASS: shortened target and physical projectile ranges; Fargrit equals Chisel at 210; two hover jets per hover unit");
    }
    static void ridgeUnlocks(){
        if(Core.assets==null)Core.assets=new arc.assets.AssetManager();
        Sector ridge=Vars.content.sector("cryon-ridge").sector;
        for(String name:new String[]{"army-headquarters","air-headquarters","light-tank","flak-car",
                "field-artillery","hover-tank","shorekeeper","needlewing","violet-falcon","ash-kite"}){
            UnlockableContent content=name.endsWith("headquarters")?Vars.content.block("cryon-aravis-"+name):unit(name);
            var gate=content.techNode.objectives.find(o->o instanceof Objectives.SectorComplete);
            check(gate instanceof Objectives.SectorComplete&&((Objectives.SectorComplete)gate).preset.sector==ridge,
                "New RTS content must require Ridge: "+name);
            check(!gate.complete(),"Ridge gate already satisfied in fresh profile: "+name);
            ridge.save=new Saves().new SaveSlot(Core.settings.getDataDirectory().child("ridge.msav"));
            ridge.info.hasCore=true;ridge.info.waves=false;ridge.info.attack=true;
            check(!gate.complete(),"Unfinished Ridge battle unlocks RTS content: "+name);
            ridge.info.attack=false;
            check(gate.complete(),"Capturing Ridge fails to open RTS research: "+name);
            for(var node=content.techNode;node!=null;node=node.parent){
                for(var objective:node.objectives)if(objective instanceof Objectives.SectorComplete capture){
                    check(capture.preset.sector==ridge,"Later sector delays RTS unlock: "+name);
                }
            }
            ridge.save=null;ridge.info.hasCore=false;
        }
        System.out.println("ARAVIS_RIDGE_UNLOCK_PASS: both headquarters and all eight units require Ridge capture, with no later campaign gates");
    }
    static void factory(String branch,String[] names,int x,int y){
        UnitFactory factory=(UnitFactory)Vars.content.block("cryon-aravis-"+branch+"-headquarters");
        check(factory.plans.size==names.length&&factory.techNode!=null,"Missing headquarters plans/research");factory.unlock();
        for(int i=0;i<names.length;i++){
            UnitFactory.UnitPlan plan=factory.plans.get(i);UnitType unit=unit(names[i]);
            check(plan.unit==unit&&unit.techNode!=null&&unit.techNode.parent.content==factory,"Wrong factory/tech route: "+names[i]);unit.unlock();
            Vars.world.tile(x+i*13,y).setBlock(factory,Team.sharded);
            UnitFactory.UnitFactoryBuild build=(UnitFactory.UnitFactoryBuild)Vars.world.build(x+i*13,y);
            build.currentPlan=i;build.power.status=1f;tick(build,8);
            check(build.progress==0f&&build.payload==null,"Production bypasses materials");
            for(ItemStack cost:plan.requirements)build.items.add(cost.item,cost.amount);
            build.power.status=0f;tick(build,8);check(build.progress==0f,"Production bypasses power");
            build.power.status=1f;build.progress=plan.time-1f;tick(build,1);
            check(build.payload!=null&&build.payload.unit.type==unit,"Native manufacture failed: "+names[i]);
            for(ItemStack cost:plan.requirements)check(build.items.get(cost.item)==0,"Recipe did not consume materials: "+names[i]);
            check(factory.capacities[plan.requirements[0].item.id]>=plan.requirements[0].amount,"Factory input capacity too low");
            build.tile.remove();
        }
        if(branch.equals("army")){
            int cheap=0,hover=0;
            for(ItemStack item:factory.plans.get(0).requirements)cheap+=item.amount;
            for(ItemStack item:factory.plans.get(3).requirements)hover+=item.amount;
            check(hover>cheap*3,"Hover tank should be much more expensive than cannon fodder");
        }
    }
    static void combat(){
        Unit air=UnitTypes.flare.spawn(Team.crux,600,200),ground=unit("light-tank").spawn(Team.crux,600,200);
        for(String name:new String[]{"violet-falcon","ash-kite","needlewing","flak-car","field-artillery","hover-tank","light-tank"}){
            UnitType type=unit(name);BulletType bullet=type.weapons.first().bullet;
            check(type.weapons.first().x==0f,"Weapon mount is not centered: "+name);
            air.health=air.maxHealth=ground.health=ground.maxHealth=10000f;index();
            Bullet shot=bullet.create(null,Team.sharded,600,200,0f);
            boolean antiAir=name.equals("violet-falcon")||name.equals("flak-car");
            check(type.targetAir==antiAir&&type.targetGround!=antiAir,"Wrong targeting role: "+name);
            if(!name.equals("field-artillery")){
                check(shot.collides(air)==antiAir&&shot.collides(ground)!=antiAir,"Wrong collision layers: "+name);
                shot.collision(antiAir?air:ground,600,200);
            }else bullet.hit(shot,600,200);
            check((antiAir?air:ground).health<10000f,"Weapon deals no damage: "+name);
            check((antiAir?ground:air).health==10000f,"Weapon damages excluded combat layer: "+name);
            shot.remove();
        }
        air.remove();ground.remove();
    }
    static void transport() throws Exception{
        Unit boat=unit("shorekeeper").spawn(Team.sharded,280,280);Payloadc cargo=(Payloadc)boat;
        check(boat.type.commands.contains(UnitCommand.loadUnitsCommand)&&boat.type.commands.contains(UnitCommand.unloadPayloadCommand)
            &&boat.type.commands.contains(UnitCommand.loopPayloadCommand),"Missing ground transport RTS commands");
        Unit passenger=unit("light-tank").spawn(Team.sharded,280,280);
        Unit enemy=unit("light-tank").create(Team.crux),heavy=UnitTypes.eclipse.create(Team.sharded);
        check(cargo.canPickup(passenger)&&!cargo.canPickup(enemy)&&!cargo.canPickup(heavy)
            &&!cargo.canPickup(unit("shorekeeper").create(Team.sharded)),"Transport ownership/capacity/nesting restrictions broken");
        check(!cargo.canPickupPayload(new UnitPayload(enemy))
            &&!cargo.canPickupPayload(new UnitPayload(unit("shorekeeper").create(Team.sharded))),"Payload transfer bypasses transport restrictions");
        index();boat.command().command(UnitCommand.loadUnitsCommand);boat.controller().updateUnit();
        check(cargo.payloads().size==1&&!passenger.isAdded(),"Native RTS load command failed on hover transport");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();boat.write(Writes.get(new DataOutputStream(bytes)));
        Unit restored=(Unit)EntityMapping.map(boat.classId()).get();
        restored.read(Reads.get(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        Payloadc restoredCargo=(Payloadc)restored;
        check(restored.type==boat.type&&restoredCargo.payloads().size==1
            &&((UnitPayload)restoredCargo.payloads().first()).unit.type==passenger.type,"Transport cargo lost on save/read");
        boat.set(400,400);check(!cargo.dropLastPayload()&&cargo.hasPayload(),"Land unit unloaded into deep water");
        boat.set(280,280);index();boat.command().command(UnitCommand.unloadPayloadCommand);boat.controller().updateUnit();
        check(!cargo.hasPayload()&&passenger.isAdded(),"Native RTS unload command failed on shore");
        Unit hematites=Vars.content.unit("cryon-hematites").create(Team.sharded);
        check(cargo.canPickup(hematites),"Landing craft cannot carry advertised heavy mech");
        cargo.pickup(hematites);check(!cargo.canPickup(passenger),"Full landing craft accepts excess cargo");
        boat.remove();passenger.remove();
    }
}
