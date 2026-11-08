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

/** Packaged T1 research/production, delayed real melee hits and IK geometry. */
public class SubsonicCheck{
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
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
        UnitType type=Vars.content.unit("cryon-subsonic"),comet=Vars.content.unit("cryon-comet");
        check(type.health>comet.health&&type.speed>comet.speed,"not tougher/faster than missile mech");
        check(type.techNode!=null&&type.techNode.parent.content.name.equals("cryon-unit-projector"),"missing T1 research");
        check(type.create(Team.sharded) instanceof Mechc,"not a walking mech");
        Vars.logic=new Logic();Vars.logic.reset();
        Vars.world.loadGenerator(80,80,tiles->{for(int x=0;x<80;x++)for(int y=0;y<80;y++)tiles.set(x,y,new Tile(x,y,Blocks.stone,Blocks.air,Blocks.air));});
        Vars.state.set(mindustry.core.GameState.State.playing);Vars.state.rules.canGameOver=false;Vars.state.rules.unitCap=100;
        UnitFactory factory=(UnitFactory)Vars.content.block("cryon-unit-projector");
        Vars.world.tile(10,10).setBlock(factory,Team.sharded);
        UnitFactory.UnitFactoryBuild build=(UnitFactory.UnitFactoryBuild)Vars.world.build(10,10);
        build.currentPlan=factory.plans.indexOf(p->p.unit==type);
        check(build.currentPlan>=0,"missing factory plan");
        var plan=factory.plans.get(build.currentPlan);
        for(ItemStack stack:plan.requirements)build.items.add(stack.item,stack.amount);
        build.progress=plan.time;build.updateTile();
        check(build.payload!=null&&build.payload.unit.type==type&&build.items.total()==0,"native production failed");
        Unit owner=type.spawn(Team.sharded,300,296);owner.rotation=0;
        Unit enemy=UnitTypes.dagger.spawn(Team.crux,322,296);
        Unit ally=UnitTypes.dagger.spawn(Team.sharded,316,296);
        Unit far=UnitTypes.dagger.spawn(Team.crux,365,296);
        Unit air=UnitTypes.flare.spawn(Team.crux,318,296);
        Vars.world.tile(40,37).setBlock(Blocks.copperWall,Team.crux);
        Building wall=Vars.world.build(40,37);float wallHealth=wall.health;
        Vars.state.teams.updateTeamStats();
        for(Team team:new Team[]{Team.sharded,Team.crux}){team.data().tree().clear();for(Unit u:Groups.unit)if(u.team==team)team.data().tree().insert(u);}
        Weapon weapon=type.weapons.first();var mount=owner.mounts[0];
        Method shoot=weapon.getClass().getDeclaredMethod("shoot",Unit.class,mindustry.entities.units.WeaponMount.class,float.class,float.class,float.class);shoot.setAccessible(true);
        float hp=enemy.health,allyHp=ally.health,farHp=far.health,airHp=air.health;
        Time.setDeltaProvider(()->1f);Time.delta=1;
        shoot.invoke(weapon,owner,mount,300f,296f,0f);
        for(int i=0;i<6;i++){Time.update();weapon.update(owner,mount);}
        check(enemy.health==hp,"hit before wind-up finished");
        for(int i=0;i<5;i++){Time.update();weapon.update(owner,mount);}
        System.out.println("HITS enemy="+enemy.health+"/"+hp+" wall="+wall.health+"/"+wallHealth+" bullets="+Groups.bullet.size()+" age="+mount.getClass().getField("age").getFloat(mount));
        check(enemy.health<hp&&wall.health<wallHealth,"melee did not damage nearby unit/building");
        check(enemy.dead&&wallHealth-wall.health>=150,"close-combat strike lacks high burst damage");
        check(ally.health==allyHp&&far.health==farHp&&air.health==airHp,"hit friend, distant unit or aircraft");
        float age=mount.getClass().getField("age").getFloat(mount);
        check(age>=8&&age<12,"no impact hold");
        int hand=mount.getClass().getField("swingSide").getInt(mount);
        shoot.invoke(weapon,owner,mount,300f,296f,0f);
        check(mount.getClass().getField("swingSide").getInt(mount)==-hand,"swing directions do not alternate");
        Method elbow=weapon.getClass().getMethod("elbow",Vec2.class,Vec2.class,int.class);
        for(int side:new int[]{-1,1})for(int a=-110;a<=110;a++){
            Vec2 shoulder=new Vec2(0,side*5),wrist=new Vec2().trns(a,side==1?9:5);
            Vec2 joint=(Vec2)elbow.invoke(null,shoulder,wrist,side);
            check(Math.abs(joint.dst(shoulder)-7)<0.001&&Math.abs(joint.dst(wrist)-7)<0.001,"IK bone length changed side="+side+" angle="+a+" upper="+joint.dst(shoulder)+" lower="+joint.dst(wrist)+" wrist="+wrist+" joint="+joint);
        }
        parryChecks(owner, weapon, mount);
        tankBalanceChecks();
        System.out.println("SUBSONIC_PASS: content, T1 production/research, toughness/speed, delayed melee unit/building damage, friendly/air/range exclusion, impact hold, two-handed swing directions, IK");
    }
    static void indexBullets(){
        Groups.bullet.tree().clear();
        for(Bullet b:Groups.bullet)Groups.bullet.tree().insert(b);
    }
    static Bullet round(Unit owner,Team team,float x,float y,boolean hittable){
        var type=new mindustry.entities.bullet.BasicBulletType(3,20);
        type.hittable=hittable;type.lifetime=120;type.hitSize=2;
        return type.create(owner,team,x,y,180);
    }
    static void pose(mindustry.entities.units.WeaponMount mount,float age)throws Exception{
        mount.getClass().getField("age").setFloat(mount,age);
        mount.getClass().getField("hold").setFloat(mount,0);
        mount.getClass().getField("combo").setInt(mount,0);
        mount.getClass().getField("swingSide").setInt(mount,1);
        mount.getClass().getField("parryHold").setBoolean(mount,false);
        mount.shoot=false;mount.reload=100;
    }
    static void parryChecks(Unit owner,Weapon weapon,mindustry.entities.units.WeaponMount mount)throws Exception{
        Groups.bullet.clear();Vars.state.rules.tags.put("cryon-allied-teams",String.valueOf(Team.green.id));
        Bullet target=round(owner,Team.crux,320,296,true);
        Bullet friendly=round(owner,Team.sharded,320,296,true);
        Bullet allied=round(owner,Team.green,320,296,true);
        Bullet rear=round(owner,Team.crux,280,296,true);
        Bullet distant=round(owner,Team.crux,355,296,true);
        Bullet immune=round(owner,Team.crux,320,296,false);
        pose(mount,30);indexBullets();weapon.update(owner,mount);
        check(target.isAdded(),"idle blade removed bullet");
        pose(mount,0);weapon.update(owner,mount);
        check(target.isAdded(),"wind-up removed bullet");
        pose(mount,5);
        for(int i=0;i<3;i++){indexBullets();weapon.update(owner,mount);}
        check(!target.isAdded()&&target.absorbed,"swept edge failed to cut enemy bullet");
        check(friendly.isAdded()&&allied.isAdded()&&rear.isAdded()&&distant.isAdded()&&immune.isAdded(),"parry hit friendly/outside/immune bullet");
        check(mount.getClass().getField("hold").getFloat(mount)>0,"parry lacks impact hold");
        check(mount.getClass().getField("parryFlash").getFloat(mount)>0,"parry flash not triggered");
        Groups.bullet.clear();Bullet guard=round(owner,Team.crux,320,296,true);
        pose(mount,30);mount.reload=0;indexBullets();weapon.update(owner,mount);
        check(mount.getClass().getField("defensive").getBoolean(mount)&&mount.reload>0,"no automatic defensive swing");
        for(int i=0;i<2;i++){indexBullets();weapon.update(owner,mount);}
        check(!guard.isAdded(),"automatic guard missed incoming bullet");
        Groups.bullet.clear();Bullet blocked=round(owner,Team.crux,320,296,true);
        pose(mount,7);owner.disarmed=true;indexBullets();weapon.update(owner,mount);owner.disarmed=false;
        check(blocked.isAdded(),"disarmed mech still parried");
        Groups.bullet.clear();Bullet recovering=round(owner,Team.crux,320,296,true);
        pose(mount,20);indexBullets();weapon.update(owner,mount);
        check(recovering.isAdded(),"recovery acted as permanent shield");
        Groups.bullet.clear();Bullet fast=round(owner,Team.crux,345,296,true);fast.vel.set(-35,0);
        pose(mount,7);indexBullets();weapon.update(owner,mount);
        check(!fast.isAdded(),"fast bullet tunnelled through blade");
        float contactX=mount.getClass().getField("parryX").getFloat(mount);
        float contactY=mount.getClass().getField("parryY").getFloat(mount);
        check(owner.dst(contactX,contactY)<=32,"fast-bullet flash appeared away from blade contact");
        Method poseAngle=weapon.getClass().getMethod("poseAngle",mount.getClass(),float.class);
        var angles=new java.util.HashSet<Float>();
        for(int i=0;i<4;i++){mount.getClass().getField("combo").setInt(mount,i);angles.add((Float)poseAngle.invoke(null,mount,6f));}
        check(angles.size()==4,"missing distinct combo poses");
        System.out.println("SUBSONIC_PARRY_PASS: swept blade, idle/windup/recovery, friendly/allied/immune/range exclusions, automatic guard, disarmed, fast bullets, hit hold, four combo poses");
    }

    static void tankBalanceChecks()throws Exception{
        String[] mechs={"subsonic","transonic"},tanks={"buffer","guardian"};
        for(int tier=0;tier<2;tier++){
            UnitType mechType=Vars.content.unit("cryon-"+mechs[tier]),tankType=Vars.content.unit("cryon-"+tanks[tier]);
            check(mechType.health<tankType.health&&mechType.armor<tankType.armor,"mech is tougher than same-tier tank");
            Unit mech=mechType.spawn(Team.sharded,200,450);
            Unit tank=tankType.spawn(Team.crux,222,450);
            Unit light=UnitTypes.dagger.spawn(Team.crux,226,450);light.health=light.maxHealth=5000;
            Vars.state.teams.updateTeamStats();
            for(Team team:new Team[]{Team.sharded,Team.crux}){
                team.data().tree().clear();for(Unit u:Groups.unit)if(u.team==team)team.data().tree().insert(u);
            }
            Weapon weapon=mechType.weapons.first();float lightHp=light.health;
            weapon.bullet.create(mech,mech.team,200,450,0);
            check(!tank.dead&&tank.health>0,"tank was killed by one slash");
            check(Math.abs(lightHp-light.health-weapon.bullet.damage)<0.01,"armour reduction leaked into light-target damage");
            int strikes=1;
            while(!tank.dead&&strikes<10){weapon.bullet.create(mech,mech.team,200,450,0);strikes++;}
            check(strikes==(tier==0?3:4),"wrong number of cuts needed against same-tier tank: "+strikes);
            Groups.bullet.clear();var rounds=new java.util.ArrayList<Bullet>();
            for(int i=0;i<6;i++)rounds.add(round(mech,Team.crux,220,450,true));
            var mount=mech.mounts[0];pose(mount,7);
            mount.getClass().getField("parried").setInt(mount,0);
            indexBullets();weapon.update(mech,mount);
            int cut=0;for(Bullet b:rounds)if(b.absorbed)cut++;
            check(cut==(tier==0?2:3),"parry capacity did not limit dense fire: "+cut);
            System.out.println("MELEE_TANK_BALANCE_PASS: "+mechs[tier]+" needs "+strikes+" cuts for "+tanks[tier]+", full damage to light targets, parry cap="+cut);
            mech.remove();tank.remove();light.remove();Groups.bullet.clear();
        }
    }

}
