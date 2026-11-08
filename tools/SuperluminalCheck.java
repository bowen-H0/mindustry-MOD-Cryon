import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.*;
import arc.math.geom.Vec2;
import java.nio.file.Files;
import java.lang.reflect.Method;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.net.Net;
import mindustry.type.*;
import mindustry.world.Tile;
import mindustry.world.blocks.units.UnitFactory;

/** T5 production, weapon form, swept area damage and escalating target marks. */
public class SuperluminalCheck{
    static UnitType type;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static Object field(Object value,String name)throws Exception{return value.getClass().getField(name).get(value);}
    static boolean active(Object dash)throws Exception{return (Boolean)dash.getClass().getMethod("active").invoke(dash);}
    static float cooldown(Object dash)throws Exception{return (Float)dash.getClass().getMethod("cooldown").invoke(dash);}
    static void index(){
        Vars.state.teams.updateTeamStats();Groups.unit.tree().clear();
        for(Unit u:Groups.unit)Groups.unit.tree().insert(u);
        for(Team team:new Team[]{Team.sharded,Team.crux,Team.green}){
            team.data().tree().clear();for(Unit u:Groups.unit)if(u.team==team)team.data().tree().insert(u);
        }
    }
    static void world(){
        Vars.logic.reset();Vars.state.rules=new Rules();Vars.state.rules.unitCap=100;Vars.state.rules.canGameOver=false;
        Vars.world.loadGenerator(140,110,tiles->{for(int x=0;x<140;x++)for(int y=0;y<110;y++)tiles.set(x,y,new Tile(x,y,Blocks.stone,Blocks.air,Blocks.air));});
        Vars.state.set(mindustry.core.GameState.State.playing);Time.setDeltaProvider(()->1f);Time.delta=1;
    }
    public static void main(String[]args)throws Exception{try{run();System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
    static void run()throws Exception{
        Core.app=new MockApplication();Core.files=new MockFiles();Core.graphics=new MockGraphics();Core.audio=new MockAudio();
        Core.settings=new Settings();Core.settings.setDataDirectory(new Fi(Files.createTempDirectory("cryon-missile-trucks-").toFile()));
        Core.bundle=I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(Core.settings.getDataDirectory().child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(Core.settings.getDataDirectory().child("mods/unitlanuch.jar"));
        Vars.headless=true;Vars.loadLocales=false;Vars.net=new Net(null);Vars.platform=new mindustry.core.Platform(){};
        mindustry.core.Version.init();Vars.init();Vars.content.createBaseContent();Vars.content.createModContent();Vars.content.init();
        var mod=Vars.mods.locateMod("cryon");check(mod!=null&&!mod.hasContentErrors(),"packaged content errors");

        ClassLoader loader=mod.main.getClass().getClassLoader();
        loader.loadClass("cryon.Content.ShadowMech").getMethod("configureProduction").invoke(null);
        loader.loadClass("cryon.TechTree.CryonTechTree").getMethod("load").invoke(null);
        Timer.instance().clear();



        type=Vars.content.unit("cryon-superluminal");UnitType prior=Vars.content.unit("cryon-relativistic");
        check(type!=null && type.techNode!=null && type.techNode.parent==prior.techNode,"missing T5 research");
        check(type.health>prior.health && type.hitSize>prior.hitSize,"T5 is not larger/tougher");
        check(type.weapons.first().bullet.damage==600f,"T5 base damage is not 600");
        check(type.abilities.isEmpty(),"T5 has separate abilities");
        Vars.logic=new Logic();world();assembly();world();
        Unit owner=type.spawn(Team.sharded,400,400);owner.rotation=0;index();
        check(owner.isCommandable(),"T5 cannot be box selected");
        var command=(mindustry.ai.types.CommandAI)owner.controller();command.commandPosition(new Vec2(400,700));
        var weapon=type.weapons.first();var mount=owner.mounts[0];
        weapon.update(owner,mount);
        check(!(Boolean)field(mount,"slaughter")&&(Float)field(mount,"deployment")==0f,"idle slaughter form deployed");
        Unit distractor=UnitTypes.dagger.spawn(Team.crux,500,350);distractor.health=distractor.maxHealth=1000000;
        Unit front=UnitTypes.dagger.spawn(Team.crux,650,400),side=UnitTypes.dagger.spawn(Team.crux,520,608);
        Unit rear=UnitTypes.dagger.spawn(Team.crux,200,515),friend=UnitTypes.dagger.spawn(Team.sharded,530,400);
        Unit air=UnitTypes.flare.spawn(Team.crux,550,400),far=UnitTypes.dagger.spawn(Team.crux,850,400);
        for(Unit u:new Unit[]{front,side,rear,friend,air,far})u.health=u.maxHealth=1000000;
        
        Vars.world.tile(75,95).setBlock(Blocks.copperWall,Team.sharded,0);Building friendly=Vars.world.build(75,95);friendly.health=1000000;
        index();
        for(int i=0;i<18;i++)weapon.update(owner,mount);
        check((Boolean)field(mount,"slaughter")&&(Float)field(mount,"deployment")==1f,"nearby enemy did not fully deploy form");
        check(command.hasCommand()&&command.targetPos.epsilonEquals(400,700,0.01f),"weapon form discarded move command");
        var length=weapon.getClass().getDeclaredMethod("bladeLength",Unit.class,loader.loadClass("cryon.Type.EnergyBladeWeapon$BladeMount"));length.setAccessible(true);
        check(Math.abs((Float)length.invoke(weapon,owner,mount)-18f*3.6f*5f)<0.01f,"energy flame not fivefold blade length");
        var strength=weapon.getClass().getMethod("markStrength",Healthc.class);
        float base=weapon.bullet.damage;
        cut(owner);check(Math.abs(1000000-front.health-base)<1,"first sweep damage wrong or duplicated");
        check(side.health==1000000&&distractor.health==1000000,"side target stole aimed strike");
        check(rear.health==1000000&&friend.health==1000000&&air.health==1000000&&far.health==1000000&&friendly.health==1000000,"sweep hit rear/friendly/air/outside targets");
        check((Integer)strength.invoke(weapon,front)==1,"first hit did not mark");
        float hp=front.health;cut(owner);check(Math.abs(hp-front.health-base*2f)<1,"second hit not x2 critical");
        hp=front.health;cut(owner);check(Math.abs(hp-front.health-base*3f)<1,"critical damage did not escalate");
        for(int i=0;i<8;i++)cut(owner);
        check((Integer)strength.invoke(weapon,front)==8,"mark strength cap wrong");
        hp=front.health;cut(owner);check(Math.abs(hp-front.health-base*5f)<1,"maximum mark critical not x5");
        Time.time+=481f;check((Integer)strength.invoke(weapon,front)==0,"mark failed to expire");
        hp=front.health;cut(owner);check(Math.abs(hp-front.health-base)<1,"expired mark retained critical multiplier");
        owner.disarmed=true;hp=front.health;cut(owner);check(front.health==hp,"disarmed mech still deals sweep damage");
        owner.disarmed=false;index();mount.getClass().getField("scan").setFloat(mount,0f);weapon.update(owner,mount);
        var shoot=weapon.getClass().getDeclaredMethod("shoot",Unit.class,mindustry.entities.units.WeaponMount.class,float.class,float.class,float.class);shoot.setAccessible(true);
        shoot.invoke(weapon,owner,mount,owner.x,owner.y,0f);
        check(mount.reload==8f&&Groups.bullet.isEmpty(),"slaughter attack interval is not 8 ticks or spawns duplicate beam damage");
        Events.fire(new EventType.ResetEvent());
        check((Integer)strength.invoke(weapon,front)==0,"target marks leaked across resets");
        world();owner=type.spawn(Team.sharded,400,400);owner.rotation=0;
        Unit tank=Vars.content.unit("cryon-eternal").spawn(Team.crux,650,400);tank.health=tank.maxHealth=1000000;index();
        for(int i=0;i<18;i++)weapon.update(owner,owner.mounts[0]);
        cut(owner);check(1000000-tank.health<base*0.31f,"T5 sweep ignored tracked armour reduction");
        world();owner=type.spawn(Team.sharded,400,400);owner.rotation=0;
        Unit distant=UnitTypes.dagger.spawn(Team.crux,740,400);distant.health=distant.maxHealth=1000000;index();
        float reach=weapon.getClass().getField("reach").getFloat(weapon);
        check(Math.abs(weapon.bullet.range-reach)<0.01f,"native weapon range disagrees with deployed blade: "+weapon.bullet.range+" vs "+reach);
        Unit offAxis=UnitTypes.dagger.spawn(Team.crux,500,350);offAxis.health=offAxis.maxHealth=1000000;index();
        ((mindustry.ai.types.CommandAI)owner.controller()).commandTarget(distant);
        for(int i=0;i<100;i++){Time.time++;owner.update();Time.update();index();}
        check(distant.health<1000000 && owner.mounts[0].totalShots>=5,"native command AI did not repeatedly attack at extended range");
        check(offAxis.health==1000000,"native ordered target lost hits to off-axis enemy");
        blocking();
        var history=owner.mounts[0];
        owner.vel.set(1f,0f);weapon.update(owner,history);
        check((Integer)field(history,"trailCount")>0,"moving/attacking mech did not record afterimages");
        Unit fresh=type.spawn(Team.sharded,200,200);
        check(field(history,"trailLife")!=field(fresh.mounts[0],"trailLife"),"afterimage history shared between mechs");
        owner.vel.setZero();owner.disarmed=true;history.getClass().getField("age").setFloat(history,30f);
        for(int i=0;i<18;i++)weapon.update(owner,history);
        for(float life:(float[])field(history,"trailLife"))check(life==0f,"afterimages did not fade after stopping");
        System.out.println("SUPERLUMINAL_PASS: 600 base/3000 max critical damage, larger T5/no abilities, native assembly/materials/research, box commands, proximity deployment/fivefold blade, aimed single-target contact/no off-axis hit stealing, unit/building occlusion and environment passthrough, friend/air/rear/range exclusions, once-per-swing damage, x2/x3/x5 criticals/expiry/reset, disarm and extended-range native AI attacks");
    }
    static void cut(Unit owner)throws Exception{
        var mount=owner.mounts[0];
        mount.getClass().getField("age").setFloat(mount,5f);mount.getClass().getField("hold").setFloat(mount,0f);
        mount.getClass().getField("combo").setInt(mount,0);mount.getClass().getField("swingSide").setInt(mount,1);
        mount.getClass().getField("defensive").setBoolean(mount,false);mount.getClass().getField("hitSoundPlayed").setBoolean(mount,false);
        ((arc.struct.ObjectSet<?>)field(mount,"struck")).clear();
        mount.getClass().getField("blocked").setBoolean(mount,false);
        mount.getClass().getField("resolved").setBoolean(mount,false);
        mount.getClass().getField("attackAngle").setFloat(mount,0f);
        mount.getClass().getField("blockedDistance").setFloat(mount,Float.POSITIVE_INFINITY);
        mount.shoot=false;mount.reload=1000;
        for(int i=0;i<4;i++){Time.time++;owner.type.weapons.first().update(owner,mount);}
    }
    static void blocking()throws Exception{
        var weapon=type.weapons.first();
        for(int kind=0;kind<4;kind++){
            world();Unit owner=type.spawn(Team.sharded,400,400);owner.rotation=0;
            Unit behind=UnitTypes.dagger.spawn(Team.crux,740,400);behind.health=behind.maxHealth=1000000;
            Unit blocker=null;Building wall=null;
            if(kind==0){blocker=UnitTypes.dagger.spawn(Team.crux,540,400);blocker.health=blocker.maxHealth=1000000;}
            if(kind==1||kind==2){
                Vars.world.tile(65,50).setBlock(Blocks.copperWall,kind==1?Team.crux:Team.sharded,0);
                wall=Vars.world.build(65,50);wall.health=1000000;
            }
            if(kind==3)for(int y=45;y<=55;y++)Vars.world.tile(65,y).setBlock(Blocks.stoneWall);
            index();for(int i=0;i<18;i++)weapon.update(owner,owner.mounts[0]);
            cut(owner);
            if(kind==3){
                check(behind.health==999400,"environment wall blocked blade");
                continue;
            }
            check(behind.health==1000000,"sword pierced blocker kind "+kind);
            check((Boolean)field(owner.mounts[0],"blocked")&&(Float)field(owner.mounts[0],"blockedDistance")<200f,"blade not visually clipped at collision");
            if(kind==0){
                check(blocker.health==999400,"first blocking unit not hit exactly once");
                blocker.health=1;cut(owner);
                check(blocker.dead&&behind.health==1000000,"killing first target pierced into a second in same swing");
                blocker.remove();
            }
            if(kind==1){check(wall.health==999400,"enemy wall not hit exactly once");wall.tile.setBlock(Blocks.air);}
            if(kind==2){check(wall.health==1000000,"friendly wall took damage");wall.tile.setBlock(Blocks.air);}
            if(kind==3)for(int y=45;y<=55;y++)Vars.world.tile(65,y).setBlock(Blocks.air);
            index();cut(owner);check(behind.health==999400,"next swing did not resume after blocker removed, kind "+kind);
        }
        world();Unit owner=type.spawn(Team.sharded,400,400);owner.rotation=0;
        Unit large=UnitTypes.dagger.spawn(Team.crux,650,440);large.hitSize=100f;large.health=large.maxHealth=1000000;
        index();for(int i=0;i<18;i++)weapon.update(owner,owner.mounts[0]);cut(owner);
        check(large.health==999400,"narrow collision query excluded a large target overlapping the blade corridor");
        System.out.println("BLOCKING_PASS: one contact per swing; nearest unit and enemy/friendly buildings block; environment walls pass through; killing blocker never pierces; clipped blade and recovery next swing");
    }
    static void assembly()throws Exception{
        var block=Vars.content.block("cryon-t5universal-assembler");block.getClass().getMethod("initCapacities").invoke(block);
        arc.struct.Seq<?> plans=(arc.struct.Seq<?>)field(block,"plans");int chosen=-1;
        for(int i=0;i<plans.size;i++)if(field(plans.get(i),"unit")==type)chosen=i;
        check(chosen>=0,"missing T3 assembly plan");Object plan=plans.get(chosen);
        Vars.world.tile(20,20).setBlock(block,Team.sharded,0);Building build=Vars.world.build(20,20);
        build.getClass().getField("currentPlan").setInt(build,chosen);
        UnitType input=Vars.content.unit("cryon-relativistic");
        check(build.acceptPayload(build,new mindustry.world.blocks.payloads.UnitPayload(input.create(Team.sharded))),"T2 input payload rejected");
        arc.struct.Seq<?> requirements=(arc.struct.Seq<?>)field(plan,"requirements");
        PayloadSeq inventory=(PayloadSeq)field(build,"blocks");
        for(Object req:requirements){PayloadStack stack=(PayloadStack)req;inventory.add(stack.item,stack.amount);}
        for(LiquidStack liquid:(LiquidStack[])field(plan,"liquidReq")){
            check(build.acceptLiquid(build,liquid.liquid),"assembly fluid rejected");build.liquids.add(liquid.liquid,200);
        }
        build.efficiency=1;if(build.power!=null)build.power.status=1;
        build.getClass().getField("constructorSatisfaction").setFloat(build,1);
        Vec2 spawn=(Vec2)build.getClass().getMethod("getUnitSpawn").invoke(build);
        int count=block.getClass().getField("dronesCreated").getInt(block);
        UnitType droneType=(UnitType)field(block,"droneType");float area=block.getClass().getField("areaSize").getInt(block);
        @SuppressWarnings("unchecked") arc.struct.Seq<Unit> drones=(arc.struct.Seq<Unit>)field(build,"units");
        for(int i=0;i<count;i++){
            Unit drone=droneType.create(Team.sharded);drone.set(new Vec2().trns(i*90+45,area/2f*arc.math.Mathf.sqrt2*Vars.tilesize).add(spawn));
            drone.rotation=i*90+225;drone.add();drones.add(drone);
        }
        float time=(Float)field(plan,"time");build.getClass().getField("progress").setFloat(build,1f-0.1f/time);
        Vars.state.teams.updateTeamStats();int before=Groups.unit.count(u->u.type==type);Time.delta=1;build.updateTile();
        check(Groups.unit.count(u->u.type==type)==before+1,"native T3 assembly did not complete");
        check(inventory.total()==0,"assembly did not consume input units and walls");
    }
}
