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

/** Packaged T2 upgrade, longer melee/parry reach, scaled IK and allied overclock. */
public class TransonicCheck{
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void index(){
        Vars.state.teams.updateTeamStats();Groups.unit.tree().clear();
        for(Unit u:Groups.unit)Groups.unit.tree().insert(u);
        for(Team team:new Team[]{Team.sharded,Team.crux,Team.green}){
            team.data().tree().clear();for(Unit u:Groups.unit)if(u.team==team)team.data().tree().insert(u);
        }
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

        UnitType t1=Vars.content.unit("cryon-subsonic"),t2=Vars.content.unit("cryon-transonic");
        check(t2!=null&&t2.techNode!=null&&t2.techNode.parent==t1.techNode,"missing T2 research");
        check(t2.health>t1.health&&t2.armor>t1.armor&&t2.hitSize>t1.hitSize&&t2.speed>=t1.speed,"T2 not larger/stronger");
        check(t2.weapons.first().bullet.damage>t1.weapons.first().bullet.damage&&t2.range>t1.range,"T2 sword not stronger/longer");
        Vars.logic=new Logic();Vars.logic.reset();
        Vars.state.rules.unitCap=100;Vars.state.rules.canGameOver=false;
        Vars.world.loadGenerator(100,100,tiles->{for(int x=0;x<100;x++)for(int y=0;y<100;y++)tiles.set(x,y,new Tile(x,y,Blocks.stone,Blocks.air,Blocks.air));});
        Vars.state.set(mindustry.core.GameState.State.playing);Time.setDeltaProvider(()->1f);Time.delta=1;
        var factory=(mindustry.world.blocks.units.Reconstructor)Vars.content.block("cryon-t2factory");
        check(factory.upgrades.contains(pair->pair[0]==t1&&pair[1]==t2),"missing native upgrade recipe");
        Vars.world.tile(12,12).setBlock(factory,Team.sharded);
        var build=(mindustry.world.blocks.units.Reconstructor.ReconstructorBuild)Vars.world.build(12,12);
        build.payload=new mindustry.world.blocks.payloads.UnitPayload(t1.create(Team.sharded));build.power.status=1;
        var items=(mindustry.world.consumers.ConsumeItems)factory.findConsumer(c->c instanceof mindustry.world.consumers.ConsumeItems);
        for(var cost:items.items)build.items.add(cost.item,cost.amount);
        for(int i=0;i<factory.constructTime+1;i++){build.updateConsumption();build.updateTile();}
        check(build.payload!=null&&build.payload.unit.type==t2,"native T1 to T2 reconstruction failed");
        for(var cost:items.items)check(build.items.get(cost.item)==0,"T2 upgrade did not consume materials");
        Unit owner=t2.spawn(Team.sharded,300,300);owner.rotation=0;
        Unit victim=UnitTypes.dagger.spawn(Team.crux,340,300);victim.health=victim.maxHealth=2000;index();
        Weapon weapon=t2.weapons.first();weapon.bullet.create(owner,owner.team,300,300,0);
        check(victim.health<=1520,"scaled blade failed to deal high damage beyond T1 reach");
        Groups.bullet.clear();
        var round=new mindustry.entities.bullet.BasicBulletType(3,20);round.hitSize=2;
        Bullet incoming=round.create(null,Team.crux,340,300,180);
        var mount=owner.mounts[0];mount.reload=100;mount.shoot=false;
        mount.getClass().getField("age").setFloat(mount,7);mount.getClass().getField("hold").setFloat(mount,0);
        mount.getClass().getField("combo").setInt(mount,0);mount.getClass().getField("swingSide").setInt(mount,1);
        Groups.bullet.tree().clear();Groups.bullet.tree().insert(incoming);weapon.update(owner,mount);
        check(incoming.absorbed&&!incoming.isAdded(),"T2 parry did not match longer blade reach");
        Method elbow=weapon.getClass().getMethod("elbow",Vec2.class,Vec2.class,int.class,float.class);
        for(int side:new int[]{-1,1})for(int angle=-135;angle<=135;angle++){
            Vec2 shoulder=new Vec2(0,side*7.5f),wrist=new Vec2().trns(angle,side==1?13.5f:7.5f);
            Vec2 joint=(Vec2)elbow.invoke(null,shoulder,wrist,side,10.5f);
            check(Math.abs(joint.dst(shoulder)-10.5f)<0.002&&Math.abs(joint.dst(wrist)-10.5f)<0.002,"scaled IK lost bone length");
        }
        Vars.state.rules.tags.put("cryon-allied-teams",String.valueOf(Team.green.id));
        Unit friend=UnitTypes.dagger.spawn(Team.sharded,350,360);
        Unit ally=UnitTypes.flare.spawn(Team.green,270,350);
        Unit enemy=UnitTypes.dagger.spawn(Team.crux,300,360);
        Unit far=UnitTypes.dagger.spawn(Team.sharded,500,360);index();
        var aura=owner.abilities[0];
        for(int i=0;i<59;i++)aura.update(owner);
        check(!friend.hasEffect(StatusEffects.overclock),"aura pulsed too soon");
        aura.update(owner);
        check(friend.hasEffect(StatusEffects.overclock)&&ally.hasEffect(StatusEffects.overclock)&&owner.hasEffect(StatusEffects.overclock),"aura missed friendly, allied air or self");
        check(!enemy.hasEffect(StatusEffects.overclock)&&!far.hasEffect(StatusEffects.overclock),"aura buffed enemy/outside range");
        check(friend.getDuration(StatusEffects.overclock)==120,"wrong buff duration");
        friend.update();
        check(friend.speedMultiplier>1&&friend.reloadMultiplier>1,"native overclock did not improve movement/reload");
        for(int i=0;i<60;i++)aura.update(owner);
        check(friend.getDuration(StatusEffects.overclock)==120,"refresh stacked buff duration");
        Unit second=t2.spawn(Team.sharded,300,350);index();friend.unapply(StatusEffects.overclock);friend.update();
        second.abilities[0].update(second);
        check(!friend.hasEffect(StatusEffects.overclock),"aura timer shared between units");
        for(int i=0;i<59;i++)second.abilities[0].update(second);
        check(friend.hasEffect(StatusEffects.overclock),"second aura never pulsed");
        second.remove();owner.remove();
        for(int i=0;i<122;i++)friend.update();
        check(!friend.hasEffect(StatusEffects.overclock),"buff persisted after sources removed");
        System.out.println("TRANSONIC_PASS: native upgrade/consumption, research, larger/stronger, longer damage/parry reach, scaled IK, friendly/allied/air/self overclock, enemy/range exclusions, independent pulse timers, refresh/expiry");
    }
}
