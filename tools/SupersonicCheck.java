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

/** Packaged T3 assembly plus real native-collision dash, cooldown, targets and balance. */
public class SupersonicCheck{
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
    static Unit source(){Unit unit=type.spawn(Team.sharded,200,300);unit.rotation=0;return unit;}
    static Unit target(){return UnitTypes.dagger.spawn(Team.crux,330,300);}
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


        type=Vars.content.unit("cryon-supersonic");UnitType previous=Vars.content.unit("cryon-transonic"),tank=Vars.content.unit("cryon-peak");
        check(type!=null&&type.techNode!=null&&type.techNode.parent==previous.techNode,"missing T3 research");
        check(type.health>previous.health&&type.hitSize>previous.hitSize&&type.weapons.first().bullet.damage>previous.weapons.first().bullet.damage,"T3 not larger/stronger");
        check(type.health<tank.health&&type.armor<tank.armor,"T3 breaks tank durability hierarchy");
        Vars.logic=new Logic();world();assembly();world();
        Unit source=source(),enemy=target();index();var dash=source.abilities[0];
        for(int i=0;i<3;i++)dash.update(source);
        check(source.x==200&&active(dash),"dash lacks a short startup hold");
        for(int i=0;i<12;i++)dash.update(source);
        check(Math.abs(source.x-248)<0.1&&Math.abs(source.y-300)<0.1,"dash not capped to six tiles: "+source.x+","+source.y);
        check(!active(dash)&&cooldown(dash)>160,"wrong duration or cooldown");
        check((Boolean)dash.getClass().getMethod("settling").invoke(dash),"dash lacks an ending brake");
        for(int i=0;i<4;i++){source.vel.set(1,0);dash.update(source);check(source.vel.isZero(),"ending brake failed to stop movement");}
        check((Integer)dash.getClass().getMethod("trailCount").invoke(dash)>=5,"missing dash history");
        Unit fresh=type.create(Team.sharded);
        check(fresh.abilities[0]!=dash&&(Integer)dash.getClass().getMethod("trailCount").invoke(fresh.abilities[0])==0,"dash histories shared between units");
        float endX=source.x;
        for(int i=0;i<100;i++)dash.update(source);
        check(source.x==endX&&!active(dash),"dash retriggered before cooldown");
        check((Float)dash.getClass().getMethod("chroma",Unit.class).invoke(null,source)==0,"chromatic effect did not fade");
        for(int i=0;i<90;i++)dash.update(source);
        check(source.x>endX,"automatic dash never recharged");
        check(source.dst(enemy)>=type.range*0.65f,"dash overshot target");
        world();source=source();enemy=target();index();dash=source.abilities[0];dash.update(source);
        for(int y=25;y<50;y++)Vars.world.tile(29,y).setBlock(Blocks.copperWall,Team.crux);
        Time.delta=12;dash.update(source);Time.delta=1;
        check(source.x<232&&!active(dash),"large-delta dash tunnelled through wall: "+source.x);
        world();source=source();index();source.vel.set(0,1.7f);dash=source.abilities[0];
        for(int i=0;i<15;i++)dash.update(source);
        check(Math.abs(source.x-200)<0.1&&Math.abs(source.y-348)<0.1,"ordinary walking did not trigger a burst in movement direction");
        // Real engine ticks: an AI keeps requesting movement even through the pauses.
        world();source=source();index();
        source.controller(new mindustry.entities.units.AIController(){
            @Override public void updateUnit(){unit.vel.set(1.7f,0);}
        });
        dash=source.abilities[0];float startX=-1,stopX=-1;boolean startupPaused=false,landingPaused=false;
        for(int i=0;i<30;i++){
            float before=source.x;boolean charging=(Boolean)dash.getClass().getMethod("windingUp").invoke(dash);
            boolean braking=(Boolean)dash.getClass().getMethod("settling").invoke(dash);
            source.update();
            if(startX<0&&active(dash))startX=source.x;
            if(charging){check(Math.abs(source.x-before)<0.01,"walking controller erased startup hold");startupPaused=true;}
            if(braking){check(Math.abs(source.x-before)<0.01,"walking controller erased landing brake");landingPaused=true;}
            if(startX>=0&&!active(dash)&&stopX<0)stopX=source.x;
        }
        check(startupPaused&&landingPaused&&Math.abs(stopX-startX-48)<0.1,"native walking burst has wrong pauses or distance");
        world();source=source();
        UnitTypes.dagger.spawn(Team.sharded,330,300);UnitTypes.flare.spawn(Team.crux,330,300);
        Vars.state.rules.tags.put("cryon-allied-teams",String.valueOf(Team.green.id));UnitTypes.dagger.spawn(Team.green,325,300);index();
        dash=source.abilities[0];dash.update(source);
        check(source.x==200&&!active(dash),"dash targeted friend/allied team/aircraft");
        world();source=source();target();index();source.apply(StatusEffects.unmoving,100);dash=source.abilities[0];dash.update(source);
        check(!active(dash)&&source.x==200,"rooted unit dashed");
        world();source=source();enemy=target();index();source.disarmed=true;dash=source.abilities[0];dash.update(source);
        check(!active(dash)&&source.x==200,"disarmed unit dashed");
        source.disarmed=false;dash.update(source);float x=source.x;enemy.dead=true;dash.update(source);
        check(!active(dash)&&source.x==x,"dash did not cancel after target died");
        world();source=source();target();index();dash=source.abilities[0];dash.update(source);float hp=source.health;source.damage(50);
        check(source.health<hp,"dash unexpectedly grants invulnerability");
        world();source=source();source.set(300,300);Unit armoured=tank.spawn(Team.crux,335,300);index();
        float hpTank=armoured.health;source.type.weapons.first().bullet.create(source,source.team,300,300,0);
        check(armoured.health>0&&hpTank-armoured.health<400,"T3 ignores tracked-armour balance");
        System.out.println("SUPERSONIC_PASS: native T3 assembly/consumption/research, scale and tank hierarchy, automatic combat/walking six-tile dash, startup/ending holds, cooldown/recharge, independent trails/fade, walls/large delta, friend/air exclusion, root/disarm/death cancellation, no invulnerability, armour damage reduction");
    }
    static void assembly()throws Exception{
        var block=Vars.content.block("cryon-t3universal-assembler");block.getClass().getMethod("initCapacities").invoke(block);
        arc.struct.Seq<?> plans=(arc.struct.Seq<?>)field(block,"plans");int chosen=-1;
        for(int i=0;i<plans.size;i++)if(field(plans.get(i),"unit")==type)chosen=i;
        check(chosen>=0,"missing T3 assembly plan");Object plan=plans.get(chosen);
        Vars.world.tile(20,20).setBlock(block,Team.sharded,0);Building build=Vars.world.build(20,20);
        build.getClass().getField("currentPlan").setInt(build,chosen);
        UnitType input=Vars.content.unit("cryon-transonic");
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
