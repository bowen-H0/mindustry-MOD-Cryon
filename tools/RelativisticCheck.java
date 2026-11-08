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

/** T4 native assembly, self-only burst and concrete enhanced perception/parry behavior. */
public class RelativisticCheck{
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



        type=Vars.content.unit("cryon-relativistic");UnitType prior=Vars.content.unit("cryon-supersonic"),tank=Vars.content.unit("cryon-blaze");
        StatusEffect buff=(StatusEffect)loader.loadClass("cryon.Content.ShadowMech").getField("extremeOverclock").get(null);
        check(type!=null&&type.techNode.parent==prior.techNode,"missing T4 research");
        check(type.health>prior.health&&type.hitSize>prior.hitSize&&type.weapons.first().bullet.damage>prior.weapons.first().bullet.damage,"T4 not larger/stronger");
        check(type.health<tank.health&&type.armor<tank.armor,"T4 exceeds frontline tank durability");
        Vars.logic=new Logic();world();assembly();world();
        Unit owner=type.spawn(Team.sharded,200,300);owner.rotation=0;index();var ability=owner.abilities[0];ability.update(owner);
        check(!owner.hasEffect(buff),"idle overclock activated without a threat");
        Unit enemy=UnitTypes.dagger.spawn(Team.crux,380,300);enemy.health=enemy.maxHealth=2000000;
        enemy.apply(StatusEffects.unmoving,100000);enemy.apply(StatusEffects.disarmed,100000);
        Unit friend=UnitTypes.dagger.spawn(Team.sharded,230,340);index();
        var ai=(mindustry.entities.units.AIController)owner.controller();
        check(owner.isCommandable() && ai instanceof mindustry.ai.types.CommandAI,"T4 cannot receive box-selection commands");
        var command=(mindustry.ai.types.CommandAI)ai;
        command.commandPosition(new Vec2(200,500));
        check(command.hasCommand() && command.targetPos.epsilonEquals(200,500,0.01f),"move command not accepted");
        command.commandQueue(new Vec2(200,600));
        check(command.commandQueue.size==1,"queued move command not accepted");
        check(ai.findTarget(owner.x,owner.y,owner.type.range,false,true)==null,"base perception already reaches distant enemy");
        for(int i=0;i<16;i++)ability.update(owner);
        check(owner.hasEffect(buff)&&!friend.hasEffect(buff),"overclock not self-only");
        owner.update();
        check(owner.controller()==command && command.targetPos.epsilonEquals(200,500,0.01f)
            && command.commandQueue.size==1,"overclock replaced or discarded player commands");
        check(owner.vel.y>0 && Math.abs(owner.vel.x)<0.01f,"enhanced AI ignored move command to chase enemy");
        command.clearCommands();
        command.commandTarget(enemy);
        check(command.attackTarget==enemy,"explicit attack command not accepted");
        check(Math.abs(owner.speedMultiplier-3f)<0.001f&&Math.abs(owner.reloadMultiplier-3f)<0.001f,"native speed/reload buff did not apply");
        check(ai.findTarget(owner.x,owner.y,owner.type.range,false,true)==enemy,"enhanced perception missed distant target");
        float normalReach=type.range,hp=enemy.health;
        owner.type.weapons.first().bullet.create(owner,owner.team,owner.x,owner.y,0);
        check(enemy.health==hp&&type.range==normalReach,"perception extended sword damage range");
        int samples=0;for(int i=0;i<120;i++){Time.time++;if(ai.retarget())samples++;}
        check(samples>=15,"enhanced AI did not retarget faster");
        owner.rotation=180;owner.lookAt(enemy);
        check(Math.abs(arc.math.Angles.angleDist(owner.rotation,180)-type.rotateSpeed*3f)<0.01f,"native turning not exactly x3");
        Unit fresh=type.spawn(Team.sharded,200,450);fresh.rotation=0;index();
        check(!fresh.hasEffect(buff)&&fresh.abilities[0]!=ability&&type.speed==1.75f,"buff mutated shared unit type or other instances");
        check(parry(owner,7)==7,"enhanced parry capacity not increased");
        check(parry(fresh,7)==5,"base T4 parry capacity changed unexpectedly");
        // Far, fast projectiles must trigger defence earlier only under the buff.
        Groups.bullet.clear();owner.set(200,300);owner.rotation=0;var weapon=owner.type.weapons.first();var mount=owner.mounts[0];
        pose(mount,30);mount.reload=0;
        var round=new mindustry.entities.bullet.BasicBulletType(48,20);round.hitSize=2;
        Bullet shot=round.create(null,Team.crux,380,300,180);indexBullets();weapon.update(owner,mount);
        check(mount.getClass().getField("defensive").getBoolean(mount),"enhanced perception failed to anticipate fast round");
        shot.set(332,300);indexBullets();weapon.update(owner,mount);check(shot.absorbed,"enhanced parry failed fast-round interception");
        Groups.bullet.clear();
        float duration=owner.getDuration(buff);check(duration>0&&duration<=180,"wrong burst duration");
        for(int i=0;i<185;i++)owner.update();
        check(!owner.hasEffect(buff)&&owner.speedMultiplier<2&&owner.reloadMultiplier<1.5f,"burst failed to expire during cooldown");
        check((Float)ability.getClass().getMethod("cooldown").invoke(ability)>0,"burst cooldown disappeared");
        world();owner=type.spawn(Team.sharded,200,300);Unit plate=tank.spawn(Team.crux,245,300);index();hp=plate.health;
        owner.type.weapons.first().bullet.create(owner,owner.team,200,300,0);
        check(plate.health>0&&hp-plate.health<1000,"T4 ignored tracked armour reduction");
        selfTime(buff);
        System.out.println("RELATIVISTIC_PASS: native T4 assembly/research/consumption, box-selection movement/queue/attack commands during overclock, size and tank hierarchy, self-only native speed/reload burst, distant acquisition, rapid retarget/turning, actual parry capacity/fast-round response, no extended sword range or shared mutation, expiration/cooldown and armour balance");
    }
    static void selfTime(StatusEffect buff)throws Exception{
        world();
        Unit owner=type.spawn(Team.sharded,400,400);owner.apply(buff,180);owner.update();
        Unit enemy=UnitTypes.dagger.spawn(Team.crux,450,400);enemy.health=enemy.maxHealth=1000000;
        enemy.controller(new mindustry.ai.types.CommandAI());
        Unit friend=UnitTypes.dagger.spawn(Team.sharded,450,430);
        Vars.world.tile(60,60).setBlock(Blocks.copperWall,Team.crux,0);
        Building building=Vars.world.build(60,60);
        var round=new mindustry.entities.bullet.BasicBulletType(10,20);round.lifetime=60;
        Bullet shot=round.create(null,Team.crux,500,400,0);shot.update();shot.time=10;
        index();indexBullets();float x=shot.x;
        Events.fire(EventType.Trigger.beforeGameUpdate);
        enemy.update();friend.update();shot.update();
        Events.fire(EventType.Trigger.afterGameUpdate);
        check(enemy.speedMultiplier==1 && enemy.reloadMultiplier==1 && friend.speedMultiplier==1,
            "self acceleration changed surrounding units");
        check(building.timeScale()==1 && Math.abs(shot.x-x-10)<0.01f && shot.time==11 && shot.vel.len()==10 && Time.delta==1,
            "self acceleration changed external building/projectile/global time");
        Groups.bullet.clear();
        var weapon=type.weapons.first();var mount=owner.mounts[0];
        pose(mount,0);weapon.update(owner,mount);
        check(mount.getClass().getField("age").getFloat(mount)==3,"blade animation not exactly x3");
        var shoot=weapon.getClass().getDeclaredMethod("shoot",Unit.class,mindustry.entities.units.WeaponMount.class,float.class,float.class,float.class);
        shoot.setAccessible(true);owner.rotation=0;owner.aim(enemy);shoot.invoke(weapon,owner,mount,owner.x,owner.y,0f);
        float hp=enemy.health;
        for(int i=0;i<2;i++){weapon.update(owner,mount);Time.update();}
        check(enemy.health==hp,"accelerated melee hit before its windup");
        weapon.update(owner,mount);Time.update();
        check(enemy.health<hp,"accelerated melee delay did not match x3 blade animation");
        check(weapon.shoot.firstShotDelay==8 && type.speed==1.75f,"acceleration mutated shared weapon/type");
        System.out.println("SELF_TIME_PASS: own speed/reload/animation x3, matching accelerated melee windup, surrounding units/buildings/projectile travel/lifetime and global time unchanged");
    }
    static void indexBullets(){Groups.bullet.tree().clear();for(Bullet b:Groups.bullet)Groups.bullet.tree().insert(b);}
    static void pose(mindustry.entities.units.WeaponMount mount,float age)throws Exception{
        mount.getClass().getField("age").setFloat(mount,age);mount.getClass().getField("hold").setFloat(mount,0);
        mount.getClass().getField("combo").setInt(mount,0);mount.getClass().getField("swingSide").setInt(mount,1);
        mount.getClass().getField("parried").setInt(mount,0);mount.getClass().getField("parryHold").setBoolean(mount,false);
        mount.shoot=false;mount.reload=100;
    }
    static int parry(Unit unit,int count)throws Exception{
        Groups.bullet.clear();unit.rotation=0;var weapon=unit.type.weapons.first();var mount=unit.mounts[0];pose(mount,7);
        var rounds=new java.util.ArrayList<Bullet>();var kind=new mindustry.entities.bullet.BasicBulletType(8,20);kind.hitSize=2;
        for(int i=0;i<count;i++)rounds.add(kind.create(null,Team.crux,unit.x+60,unit.y,180));
        indexBullets();weapon.update(unit,mount);int cut=0;for(Bullet b:rounds)if(b.absorbed)cut++;return cut;
    }
    static void assembly()throws Exception{
        var block=Vars.content.block("cryon-t4universal-assembler");block.getClass().getMethod("initCapacities").invoke(block);
        arc.struct.Seq<?> plans=(arc.struct.Seq<?>)field(block,"plans");int chosen=-1;
        for(int i=0;i<plans.size;i++)if(field(plans.get(i),"unit")==type)chosen=i;
        check(chosen>=0,"missing T3 assembly plan");Object plan=plans.get(chosen);
        Vars.world.tile(20,20).setBlock(block,Team.sharded,0);Building build=Vars.world.build(20,20);
        build.getClass().getField("currentPlan").setInt(build,chosen);
        UnitType input=Vars.content.unit("cryon-supersonic");
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
