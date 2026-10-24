import arc.math.geom.Vec2;
import cryon.Type.*;
import mindustry.Vars;
import mindustry.gen.*;
import mindustry.type.UnitType;
import mindustry.game.Team;

public class SpaceJumpFormationCheck{
 static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
 static Unit ship(int i)throws Exception{
  UnitType type=new UnitType("formation-probe-"+i);type.flying=true;type.speed=1;type.rotateSpeed=6;
  Unit unit=UnitEntity.create();unit.type=type;unit.team=Team.sharded;unit.dead=false;unit.elevation=1;unit.speedMultiplier=1;
  unit.maxHealth=i%3==0?16000:i%3==1?8000:100;unit.health=unit.maxHealth;unit.hitSize=i%3==0?56:i%3==1?36:12;
  unit.set(100+(i%6)*18,170+(i/6)*20);
  unit.abilities=new mindustry.entities.abilities.Ability[]{new SpaceJumpAbility()};
  java.lang.reflect.Field added=UnitEntity.class.getDeclaredField("added");added.setAccessible(true);added.set(unit,true);Groups.unit.add(unit);
  return unit;
 }
 static SpaceJumpAbility jump(Unit u){return (SpaceJumpAbility)u.abilities[0];}
 static void separated(Unit a,Unit b){
  float clearance=SpaceJumpFormation.radius(a)+SpaceJumpFormation.radius(b)+3.9f;
  check(arc.math.Mathf.dst(jump(a).landingX(),jump(a).landingY(),jump(b).landingX(),jump(b).landingY())>=clearance,"reserved landing overlap");
 }
 public static void main(String[] args)throws Exception{
  SpaceJumpCheck.main(args);Groups.unit.clear();Groups.build.clear();
  Unit[] fleet=new Unit[60];
  for(int i=0;i<fleet.length;i++)fleet[i]=ship(i);
  long start=System.nanoTime();
  for(Unit unit:fleet)check(jump(unit).jump(unit,900,400),"fleet could not allocate a landing slot");
  for(int i=0;i<fleet.length;i++){
   Unit a=fleet[i];SpaceJumpAbility j=jump(a);float r=SpaceJumpFormation.radius(a);
   check(j.landingX()>=r && j.landingY()>=r && j.landingX()<=Vars.world.unitWidth()-r && j.landingY()<=Vars.world.unitHeight()-r,"formation outside map");
   check(a.dst(j.landingX(),j.landingY())<=j.maxDistance+.01,"formation exceeded hop range");
   for(int k=0;k<i;k++)separated(a,fleet[k]);
  }
  float oldX=jump(fleet[0]).landingX(),oldY=jump(fleet[0]).landingY();
  for(Unit unit:fleet)jump(unit).update(unit);
  for(int i=0;i<fleet.length;i++)for(int k=0;k<i;k++)separated(fleet[i],fleet[k]);
  check(Math.abs(jump(fleet[0]).landingX()-oldX)<.01 && Math.abs(jump(fleet[0]).landingY()-oldY)<.01,"stable slot drifted each tick");
  jump(fleet[0]).cancel(fleet[0]);
  Vec2 reclaimed=jump(fleet[0]).landing(fleet[0],oldX,oldY,new Vec2());
  check(reclaimed!=null && reclaimed.dst(oldX,oldY)<.01,"cancelled reservation not released");
  // An arrived/reconstructing ship retains its slot; moving ships are also obstacles.
  Unit arriving=fleet[1];float ax=jump(arriving).landingX(),ay=jump(arriving).landingY();jump(arriving).finish(arriving,ax,ay,0);
  Vec2 blocked=jump(fleet[0]).landing(fleet[0],ax,ay,new Vec2());
  check(blocked!=null && blocked.dst(ax,ay)>=SpaceJumpFormation.radius(arriving)+SpaceJumpFormation.radius(fleet[0]),"reconstruction slot was reused");
  fleet[0].hitSize=arriving.hitSize;fleet[0].maxHealth=arriving.maxHealth;
  arriving.dead=true;Vec2 freed=jump(fleet[0]).landing(fleet[0],ax,ay,new Vec2());
  check(freed!=null && freed.dst(ax,ay)<.01,"dead ship left a stale slot");
  jump(fleet[0]).maxDistance=10f;
  Vec2 tooClose=jump(fleet[0]).landing(fleet[0],fleet[0].x+9,fleet[0].y,new Vec2());
  check(tooClose==null || !jump(fleet[0]).distantEnough(fleet[0],tooClose),"crowded short hop incorrectly became a valid far jump");
  System.out.println("PASS: 60 mixed-size simultaneous jumps, pairwise clearance, stable slots, map/range bounds, cancellation/death cleanup and reconstruction reservation; planning ms="+(System.nanoTime()-start)/1000000);
 }
}
