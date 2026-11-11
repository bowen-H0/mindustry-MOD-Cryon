import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.*;
import java.lang.reflect.*;
import java.nio.file.Files;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.entities.bullet.BulletType;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.net.Net;
import mindustry.type.*;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.blocks.units.*;

/** Packaged production/research and deterministic timing/damage of all five missile carrier tiers. */
public class MissileTruckCheck{
    static ClassLoader loader;static Class<?> content;static UnitType[] line;static Unit owner;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static int integer(Object value,String name)throws Exception{return value.getClass().getField(name).getInt(value);}
    static boolean flag(Object value,String name)throws Exception{return value.getClass().getField(name).getBoolean(value);}
    static Object field(Object value,String name)throws Exception{return value.getClass().getField(name).get(value);}
    public static void main(String[]args)throws Exception{try{run();System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
    static void run()throws Exception{
        Core.app=new MockApplication();Core.files=new MockFiles();Core.graphics=new MockGraphics();Core.audio=new MockAudio();
        Core.settings=new Settings();Core.settings.setDataDirectory(new Fi(Files.createTempDirectory("cryon-missile-trucks-").toFile()));
        Core.bundle=I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(Core.settings.getDataDirectory().child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(Core.settings.getDataDirectory().child("mods/unitlanuch.jar"));
        Vars.headless=true;Vars.loadLocales=false;Vars.net=new Net(null);Vars.platform=new mindustry.core.Platform(){};
        mindustry.core.Version.init();Vars.init();Vars.content.createBaseContent();Vars.content.createModContent();Vars.content.init();
        var mod=Vars.mods.locateMod("cryon");check(mod!=null&&!mod.hasContentErrors(),"packaged content errors");
        loader=mod.main.getClass().getClassLoader();content=loader.loadClass("cryon.Content.MissileTruckUnits");
        line=(UnitType[])content.getField("line").get(null);
        Class<?> assembler=loader.loadClass("cryon.Type.UniversalUnitAssembler");Class<?> main=mod.main.getClass();
        Object[] factories={main.getField("t3universalAssembler").get(null),main.getField("t4universalAssembler").get(null),main.getField("t5universalAssembler").get(null)};
        content.getMethod("configureProduction",assembler,assembler,assembler).invoke(null,factories);
        for(Object factory:factories)assembler.getMethod("initCapacities").invoke(factory);
        loader.loadClass("cryon.TechTree.CryonTechTree").getMethod("load").invoke(null);Timer.instance().clear();
        for(int i=0;i<5;i++){
            Unit unit=line[i].create(Team.sharded);
            check(unit instanceof Tankc&&unit.type==line[i],"not a real tracked carrier at tier "+i);
            check(line[i].techNode!=null,"missing research tier "+i);
            if(i>0)check(line[i].techNode.parent==line[i-1].techNode,"broken research sequence");
            check(line[i].crushDamage>0==(i>=3),"wrong crush tiers");
            check(line[i].weapons.size==1,"unexpected duplicate launcher");
        }
        UnitFactory first=(UnitFactory)Vars.content.block("cryon-unit-projector");
        check(first.plans.contains(p->p.unit==line[0]),"T1 missing factory plan");
        check(((Reconstructor)Vars.content.block("cryon-t2factory")).upgrades.contains(pair->pair[0]==line[0]&&pair[1]==line[1]),"T2 missing upgrade");
        for(int i=0;i<3;i++){
            arc.struct.Seq<?> plans=(arc.struct.Seq<?>)assembler.getField("plans").get(factories[i]);boolean found=false;
            for(Object plan:plans)if(field(plan,"unit")==line[i+2]){
                found=true;arc.struct.Seq<?> req=(arc.struct.Seq<?>)field(plan,"requirements");
                check(((PayloadStack)req.first()).item==line[i+1],"wrong assembly input");
            }
            check(found,"missing assembler tier "+i);
        }
        Vars.logic=new Logic();Vars.logic.reset();world();
        // Real native factory completion must consume the new plan's items and create its payload.
        Vars.world.tile(15,15).setBlock(first,Team.sharded);
        UnitFactory.UnitFactoryBuild producer=(UnitFactory.UnitFactoryBuild)Vars.world.build(15,15);
        producer.currentPlan=first.plans.indexOf(p->p.unit==line[0]);
        UnitFactory.UnitPlan production=first.plans.get(producer.currentPlan);
        for(ItemStack stack:production.requirements)producer.items.add(stack.item,stack.amount);
        producer.progress=production.time;producer.updateTile();
        check(producer.payload!=null&&producer.payload.unit.type==line[0]&&producer.items.total()==0,"T1 factory failed real production/consumption");
        producer.tile.remove();
        owner=line[4].spawn(Team.sharded,180,300);
        // A high-health enemy and an ally occupy the same impact zone.
        Unit enemy=UnitTypes.reign.spawn(Team.crux,500,300),ally=UnitTypes.reign.spawn(Team.green,500,300);
        enemy.health=enemy.maxHealth=1000000;ally.health=ally.maxHealth=1000000;
        Vars.state.rules.tags.put("cryon-allied-teams",String.valueOf(Team.green.id));
        loader.loadClass("cryon.Features.MapAlliances").getMethod("install").invoke(null);index();
        Vars.world.tile(38,37).setBlock(Blocks.thoriumWall,Team.crux);
        Building impact=Vars.world.build(38,37);
        Bullet t1=fire(0,180,300,400,300);
        t1.type.hitTile(t1,impact,300,300,impact.health,true);
        t1.type.hitTile(t1,impact,310,300,impact.health,true);
        check(flag(t1.data,"recalled"),"T1 did not recall");
        BulletType echo=(BulletType)field(t1.type,"echo");check(Groups.bullet.count(b->b.type==echo)==1,"T1 duplicated recall on piercing hits");
        Bullet replay=Groups.bullet.find(b->b.type==echo);
        BulletType fragment=(BulletType)field(t1.type,"fragment");
        int steps=integer(replay.data,"steps");
        for(int i=0;i<steps;i++){
            replay.time=i*4;replay.type.update(replay);
            check(integer(replay.data,"pulses")==i+1,"T1 recall pulse order");
            check(Groups.bullet.count(b->b.type==fragment)==(i+1)*6,"T1 pulse must scatter six native fragments");
        }
        check(t1.type instanceof mindustry.entities.bullet.MissileBulletType,"T1 stopped using a native missile");
        check(((arc.math.geom.Vec2)field(replay.data,"end")).x==300&&((arc.math.geom.Vec2)field(replay.data,"origin")).x==180,"T1 recall endpoints reversed");
        check(Groups.bullet.contains(b->b.type==fragment&&b.x<190)&&Groups.bullet.contains(b->b.type==fragment&&b.x>290),"T1 fragment explosions do not span impact to muzzle");
        replay.time=51;replay.type.update(replay);check(integer(replay.data,"pulses")==steps,"T1 repeated finished recall");
        clearShots();
        Bullet unitHit=fire(0,180,300,400,300);unitHit.set(300,300);
        unitHit.type.hitEntity(unitHit,enemy,enemy.health);
        check(flag(unitHit.data,"recalled")&&Groups.bullet.count(b->b.type==echo)==1,"T1 native unit impact failed to trigger recall");
        clearShots();
        Bullet missed=fire(0,180,300,400,300);missed.type.despawned(missed);
        check(!flag(missed.data,"recalled")&&Groups.bullet.count(b->b.type==echo)==0,"T1 triggered recall without an enemy impact");
        clearShots();
        impact.tile.remove();
        arc.struct.Seq<Bullet> volley=new arc.struct.Seq<>();
        line[1].weapons.first().shoot.shoot(0,(x,y,angle,delay,mover)->{
            check(delay==0,"T2 missiles are staggered instead of simultaneous");
            volley.add(fire(1,180,300,500,300));
        });
        check(volley.size==3,"T2 must fire three missiles per salvo");
        check(volley.get(0).dst(volley.get(1))>1&&volley.get(1).dst(volley.get(2))>1
                &&volley.get(0).dst(volley.get(2))>1,"T2 salvo missiles overlap at spawn");
        clearShots();
        Bullet t2=fire(1,180,300,500,300);Object flank=t2.data;
        check(t2.type instanceof mindustry.entities.bullet.MissileBulletType,"T2 must use native missiles");
        check(t2.dst((arc.math.geom.Vec2)field(flank,"rear"))<0.01f&&!flag(flank,"cruising"),"T2 must start behind its vehicle");
        enemy.set(500,370);
        for(int tick=0;tick<200&&!flag(flank,"cruising");tick++){
            t2.type.update(t2);
            if(!flag(flank,"cruising"))check(((arc.math.geom.Vec2)field(flank,"target")).dst(500,300)<0.01f,"T2 acquired enemy before reaching its flank");
            check(!t2.vel.isZero(),"T2 stopped instead of flying around vehicle");
            t2.set(t2.x+t2.vel.x,t2.y+t2.vel.y);t2.time++;
        }
        check(flag(flank,"cruising")&&integer(flank,"waypoint")==3,"T2 did not complete rear/flank/exit route");
        check(((arc.math.geom.Vec2)field(flank,"target")).dst(enemy)<0.01f,"T2 did not lock enemy after flank exit");
        enemy.set(500,300);index();clearShots();
        Bullet t3=fire(2,180,300,500,300);Object salvo=t3.data;
        for(int i=0;i<12;i++){t3.time=i*5;t3.type.update(t3);}
        check(integer(salvo,"launched")==12,"T3 must launch exactly twelve missiles");
        BulletType nodeMissile=(BulletType)field(t3.type,"missile");int impacts=0;
        for(Bullet missile:Groups.bullet.copy())if(missile.type==nodeMissile){
            if(impacts++<11){missile.set((arc.math.geom.Vec2)field(missile.data,"target"));missile.type.update(missile);}
        }
        t3.type.update(t3);check(integer(salvo,"landed")==11&&!flag(salvo,"detonated"),"T3 network burst before the twelfth impact");
        for(Bullet missile:Groups.bullet.copy())if(missile.type==nodeMissile){missile.set((arc.math.geom.Vec2)field(missile.data,"target"));missile.type.update(missile);}
        float beforeNetwork=enemy.health;
        t3.type.update(t3);check(integer(salvo,"landed")==12&&!flag(salvo,"detonated"),"T3 trap must briefly charge after all impacts");
        check(enemy.health==beforeNetwork,"T3 charge already dealt laser damage");
        t3.time+=18;t3.type.update(t3);
        check(flag(salvo,"detonated")&&enemy.health<beforeNetwork-100,"T3 heavy laser spikes did not spring the trap");
        BulletType nodeLaser=(BulletType)field(t3.type,"laser");check(Groups.bullet.count(b->b.type==nodeLaser)==66,"T3 did not connect all twelve nodes");clearShots();
        // Adaptive volleys cover separate clusters instead of drawing a fixed ring.
        Unit clusterA=UnitTypes.reign.spawn(Team.crux,410,240),clusterB=UnitTypes.reign.spawn(Team.crux,440,385);
        clusterA.health=clusterA.maxHealth=clusterB.health=clusterB.maxHealth=100000;index();
        Bullet adaptive=fire(2,180,300,470,300);
        for(int i=0;i<12;i++){adaptive.time=i*5;adaptive.type.update(adaptive);}
        boolean coversA=false,coversB=false;
        for(Bullet missile:Groups.bullet.copy())if(missile.type==nodeMissile){
            arc.math.geom.Vec2 target=(arc.math.geom.Vec2)field(missile.data,"target");
            coversA|=target.within(clusterA,65);coversB|=target.within(clusterB,65);
        }
        check(coversA&&coversB,"T3 adaptive salvo ignored one enemy cluster");
        clearShots();clusterA.remove();clusterB.remove();index();
        // Run the native swept tile raycast, not just the custom hit callback.
        Vars.world.tile(35,37).setBlock(Blocks.thoriumWall,Team.crux);Building blocker=Vars.world.build(35,37);
        float blockerHp=blocker.health,protectedHp=enemy.health;
        Bullet blockedSalvo=fire(2,180,300,500,300);blockedSalvo.type.update(blockedSalvo);
        Bullet blocked=Groups.bullet.find(b->b.type==nodeMissile);
        check(nodeMissile instanceof mindustry.entities.bullet.MissileBulletType&&nodeMissile.collides&&nodeMissile.collidesTiles
                &&!nodeMissile.collideTerrain&&!nodeMissile.collideFloor&&!nodeMissile.pierceBuilding,"T3 missile does not use normal blocking collision");
        blocked.set(520,300);blocked.tileRaycast(22,37,65,37);
        check(!blocked.isAdded()&&blocker.health<blockerHp&&enemy.health==protectedHp,"T3 missile passed through a wall");
        arc.math.geom.Vec2[] actual=(arc.math.geom.Vec2[])field(blockedSalvo.data,"nodes");
        check(actual[0]!=null&&actual[0].dst(blocker)<1,"T3 node stayed at planned target instead of wall impact");
        blockedSalvo.time=5;blockedSalvo.type.update(blockedSalvo);
        Bullet early=Groups.bullet.find(b->b.type==nodeMissile);
        Unit interceptor=UnitTypes.reign.spawn(Team.crux,330,300);interceptor.health=interceptor.maxHealth=100000;index();
        early.set(330,300);early.collision(interceptor,330,300);
        check(actual[1]!=null&&actual[1].dst(interceptor)<1&&integer(blockedSalvo.data,"landed")==2,"T3 did not create a node on an early unit collision");
        blockedSalvo.time=10;blockedSalvo.type.update(blockedSalvo);
        Bullet absorbed=Groups.bullet.find(b->b.type==nodeMissile);absorbed.absorb();
        check(integer(blockedSalvo.data,"resolved")==3&&actual[2]==null,"Absorbed missile created a damage node or stalled the salvo");
        clearShots();blocker.tile.remove();interceptor.remove();index();
        float enemyBefore=enemy.health,allyBefore=ally.health;
        Vars.world.tile(65,15).setBlock(Blocks.coreShard,Team.crux);
        Vars.world.tile(36,66).setBlock(Blocks.duo,Team.crux);
        Building importantCore=Vars.world.build(65,15),importantTurret=Vars.world.build(36,66);
        importantCore.health=importantTurret.health=1000000;index();
        Bullet t4=fire(3,180,300,500,300);Object sky=t4.data;
        for(int i=0;i<8;i++){t4.time=i*3;t4.type.update(t4);}
        BulletType flare=(BulletType)field(t4.type,"flare"),bomb=(BulletType)field(t4.type,"bomb");
        check(integer(sky,"launched")==8&&Groups.bullet.count(b->b.type==flare)==8,"T4 did not distribute its signal flares");
        check(!flare.collides&&!flare.collidesTiles&&!flare.absorbable&&!flare.hittable,"T4 signals must have no collision/interception");
        boolean aimsCore=false,aimsTurret=false,aimsUnit=false;
        for(Bullet signal:Groups.bullet.copy())if(signal.type==flare){
            arc.math.geom.Vec2 point=(arc.math.geom.Vec2)field(signal.data,"target");
            aimsCore|=point.within(importantCore,65);aimsTurret|=point.within(importantTurret,65);aimsUnit|=point.within(enemy,65);
            check(!signal.collides(enemy),"T4 signal collided with an enemy instead of passing through");
            signal.type.despawned(signal);
            check(Groups.bullet.count(b->b.type==bomb)==0,"T4 called bombs before signals reached their destinations");
        }
        check(aimsCore&&aimsTurret&&aimsUnit,"T4 did not support multiple important targets in one volley");
        for(Bullet signal:Groups.bullet.copy())if(signal.type==flare){
            signal.set((arc.math.geom.Vec2)field(signal.data,"target"));signal.type.update(signal);
            check(flag(signal.data,"arrived"),"T4 signal did not arrive");
            signal.time=signal.lifetime-2;signal.type.despawned(signal);
            check(!flag(signal.data,"released"),"T4 signal called bombers before fading");
            signal.time=signal.lifetime;signal.type.despawned(signal);signal.type.despawned(signal);
        }
        check(integer(sky,"faded")==8&&integer(sky,"bombsReleased")==32&&Groups.bullet.count(b->b.type==bomb)==32,"T4 saturation bombardment must release four bombs per faded signal exactly once");
        check(enemy.health==enemyBefore&&!flag(sky,"detonated"),"T4 signals dealt instant damage");
        float fallTime=bomb.getClass().getField("fallTime").getFloat(bomb);
        for(Bullet falling:Groups.bullet.copy())if(falling.type==bomb){
            float delay=falling.data.getClass().getField("delay").getFloat(falling.data);
            falling.time=delay+fallTime-1;falling.type.update(falling);
            check(!flag(falling.data,"detonated"),"T4 bomb exploded before landing");
            falling.time=delay+fallTime;falling.type.update(falling);falling.type.update(falling);
        }
        for(int i=0;i<4;i++){Time.time++;Time.update();}
        check(flag(sky,"detonated")&&integer(sky,"impacts")==32&&enemy.health<enemyBefore&&ally.health==allyBefore,"T4 heavy bombs failed to damage enemies or harmed allies");
        check(importantCore.health<1000000&&importantTurret.health<1000000,"T4 failed to bomb separate strategic buildings");
        clearShots();importantCore.tile.remove();importantTurret.tile.remove();index();
        Bullet t5=fire(4,180,300,500,300);BulletType hypersonic=t5.type;
        float railgunRange=((Turret)Vars.content.block("cryon-railgun")).range;
        check(hypersonic instanceof mindustry.entities.bullet.MissileBulletType&&hypersonic.speed>=40,"T5 must be a very fast native missile");
        check(line[4].range==railgunRange&&hypersonic.rangeOverride==railgunRange,"T5 range must match the railgun");
        check(!hypersonic.hittable&&!hypersonic.absorbable&&!hypersonic.reflectable,"T5 is interceptable");
        Class<?> barrier=loader.loadClass("cryon.Type.OrbitalBarrierNode");
        check(!(boolean)barrier.getMethod("intercept",Bullet.class).invoke(null,t5),"T5 intercepted by orbital barrier");
        check(t5.vel.len()>=40&&t5.x==180,"T5 teleported instead of launching");
        enemyBefore=enemy.health;allyBefore=ally.health;
        t5.lastX=468;t5.lastY=300;t5.set(516,300);t5.type.update(t5);
        check(flag(t5.data,"detonated")&&!t5.isAdded()&&t5.dst(500,300)<0.1f,"T5 high-speed step skipped its target");
        check(enemy.health<enemyBefore-1000&&ally.health==allyBefore,"T5 heavy warhead damage or ally safety failed");
        enemyBefore=enemy.health;t5.type.update(t5);t5.type.despawned(t5);
        check(enemy.health==enemyBefore,"T5 detonated twice");
        check(Groups.bullet.size()==0,"T5 spawned obsolete orbs or laser networks");
        Vars.world.tile(35,37).setBlock(Blocks.thoriumWall,Team.crux);blocker=Vars.world.build(35,37);blockerHp=blocker.health;
        Bullet blockedT5=fire(4,180,300,500,300);blockedT5.set(520,300);blockedT5.tileRaycast(22,37,65,37);
        check(!blockedT5.isAdded()&&flag(blockedT5.data,"detonated")&&blocker.health<blockerHp,"T5 failed native wall impact");
        blocker.tile.remove();clearShots();
        Bullet longShot=fire(4,180,300,2500,300);
        arc.math.geom.Vec2 destination=(arc.math.geom.Vec2)field(longShot.data,"target");
        check(Math.abs(destination.dst(180,300)-railgunRange)<0.1f,"T5 maximum range is wrong");
        longShot.time=longShot.lifetime;longShot.type.despawned(longShot);
        check(flag(longShot.data,"detonated"),"T5 heavy warhead did not explode at maximum range");
        terrainCollisionChecks(nodeMissile);
        System.out.println("MISSILE_TRUCK_CHECK_PASS: five tracked units, production/research, reverse fragment explosions, triple flank launch, adaptive spike trap, multi-target 32-bomb saturation, uninterceptable hypersonic missile, swept target/wall impact, heavy warhead exactly once, environment pass-through and ally safety");
    }
    static void terrainCollisionChecks(BulletType nodeMissile)throws Exception{
        clearShots();
        Vars.world.tile(30,75).setBlock(Blocks.stoneWall);
        Vars.world.tile(32,75).setBlock(Blocks.copperWall,Team.sharded);
        Vars.world.tile(34,75).setBlock(Blocks.copperWall,Team.green);
        Building friendly=Vars.world.build(32,75),allied=Vars.world.build(34,75);
        float friendlyHp=friendly.health,alliedHp=allied.health;
        BulletType t1=line[0].weapons.first().bullet;
        BulletType[] types={t1,(BulletType)field(t1,"fragment"),line[1].weapons.first().bullet,nodeMissile,line[4].weapons.first().bullet};
        for(int i=0;i<types.length;i++){
            Bullet b;
            if(types[i]==nodeMissile){
                Bullet salvo=fire(2,180,600,500,600);salvo.type.update(salvo);
                b=Groups.bullet.find(candidate->candidate.type==nodeMissile);
                Method predict=salvo.type.getClass().getDeclaredMethod("expectedImpact",Bullet.class,arc.math.geom.Vec2.class);
                predict.setAccessible(true);
                var predicted=(arc.math.geom.Vec2)predict.invoke(salvo.type,salvo,new arc.math.geom.Vec2(500,600));
                check(predicted.within(500,600,0.1f),"T3 target planner still stops at terrain/friendly walls");
            }else b=types[i].create(owner,Team.sharded,180,600,0,-1,1,1,null,null,500,600);
            check(!b.type.collideTerrain&&!b.type.collideFloor,"unexpected terrain collision type "+i);
            b.lastX=180;b.lastY=600;b.set(500,600);b.tileRaycast(22,75,62,75);
            check(b.isAdded(),"environment or friendly wall blocked projectile type "+i);
            check(friendly.health==friendlyHp&&allied.health==alliedHp,"projectile hit friendly/allied wall type "+i);
            Vars.world.tile(36,75).setBlock(Blocks.thoriumWall,Team.crux);
            Building enemy=Vars.world.build(36,75);float hp=enemy.health;
            b.lastX=180;b.lastY=600;b.set(500,600);b.tileRaycast(22,75,62,75);
            check(enemy.health<hp,"enemy building no longer intercepts projectile type "+i);
            if(types[i]==nodeMissile||i==4)check(!b.isAdded(),"non-piercing missile crossed enemy building");
            check(friendly.health==friendlyHp&&allied.health==alliedHp,"enemy impact damaged friendly/allied walls");
            enemy.tile.remove();clearShots();
            check(Vars.world.tile(30,75).block()==Blocks.stoneWall,"projectile modified natural wall");
        }
        Vars.world.tile(36,75).setBlock(Blocks.copperWall,Team.derelict);
        Building abandoned=Vars.world.build(36,75);
        Unit friendlyUnit=UnitTypes.reign.spawn(Team.sharded,310,600);
        Unit alliedUnit=UnitTypes.reign.spawn(Team.green,335,600);
        Bullet t5=fire(4,180,600,500,600);
        check(!t5.collides(friendlyUnit)&&!t5.collides(alliedUnit),"Friendly unit blocks T5");
        check(!t5.type.testCollision(t5,friendly)&&!t5.type.testCollision(t5,allied)
                &&!t5.type.testCollision(t5,abandoned),"Friendly or abandoned building blocks T5");
        t5.lastX=180;t5.lastY=600;t5.set(516,600);t5.type.update(t5);
        check(flag(t5.data,"detonated")&&t5.dst(500,600)<0.1f,"T5 did not reach its aim point past neutral and friendly obstacles");
        check(friendly.health==friendlyHp&&allied.health==alliedHp,"T5 damaged friendly or allied walls");
        Vars.world.tile(30,75).setAir();friendly.tile.remove();allied.tile.remove();abandoned.tile.remove();
        friendlyUnit.remove();alliedUnit.remove();
        System.out.println("MISSILE_TERRAIN_PASS: T1 missile/fragments, T2, T3 and T5 pass natural/friendly/allied walls; enemy structures still collide; T3 planning ignores terrain");
    }
    static Bullet fire(int tier,float x,float y,float ax,float ay){return line[tier].weapons.first().bullet.create(owner,Team.sharded,x,y,0,-1,1,1,null,null,ax,ay);}
    static void clearShots(){for(Bullet b:Groups.bullet.copy())b.remove();}
    static void world(){
        Vars.world.loadGenerator(140,100,tiles->{for(int x=0;x<140;x++)for(int y=0;y<100;y++)tiles.set(x,y,new Tile(x,y,Blocks.stone,Blocks.air,Blocks.air));});
        Vars.state.set(mindustry.core.GameState.State.playing);Vars.state.rules.canGameOver=false;Vars.state.rules.unitCap=100;Time.delta=1;
    }
    static void index(){Vars.state.teams.updateTeamStats();for(Team team:new Team[]{Team.sharded,Team.crux,Team.green}){team.data().tree().clear();for(Unit u:Groups.unit)if(u.team==team)team.data().tree().insert(u);}}
}
