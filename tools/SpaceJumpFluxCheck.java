import arc.math.geom.Vec2;
import cryon.Type.*;
import mindustry.Vars;
import mindustry.entities.abilities.Ability;
import mindustry.game.Team;
import mindustry.gen.*;

public class SpaceJumpFluxCheck{
 static class Field extends FluxBarrierAbility{ Field(float radius){this.radius=radius;radscl=1;} }
 static class SafeField extends SafeFluxBarrierAbility{
  SafeField(float radius){this.radius=radius;radscl=1;}
  void broken(boolean value){broken=value;}
 }
 static void check(boolean value,String text){if(!value)throw new AssertionError(text);}
 static Unit source(float x,float y,Ability ability)throws Exception{
  Unit unit=mindustry.content.UnitTypes.flare.create(Team.crux);unit.set(x,y);unit.dead=false;
  unit.abilities=new Ability[]{ability};
  java.lang.reflect.Field added=UnitEntity.class.getDeclaredField("added");added.setAccessible(true);added.set(unit,true);
  Groups.unit.add(unit);return unit;
 }
 public static void main(String[] args)throws Exception{
  SpaceJumpCheck.main(args);Groups.unit.clear();Groups.build.clear();
  Unit unit=mindustry.content.UnitTypes.flare.create(Team.sharded);unit.set(100,100);unit.elevation=1;unit.dead=false;
  SpaceJumpAbility jump=new SpaceJumpAbility();Vec2 landing=new Vec2();
  FluxBarrier block=new FluxBarrier("jump-flux-check");block.radius=100;
  FluxBarrier.ForceBuild build=block.new ForceBuild();build.block=block;build.team=Team.crux;build.x=800;build.y=100;build.radscl=1;build.broken=false;
  Groups.build.add(build);
  jump.landing(unit,800,100,landing);check(landing.x<700-unit.hitSize/2 && landing.y==100,"enemy building allowed landing inside");
  float expected=landing.x;
  check(jump.jump(unit,800,100),"blocked landing did not start a shortened jump");
  int ticks=0;while(jump.active() && ticks++<1000)jump.update(unit);
  check(Math.abs(unit.x-expected)<0.01,"preview/actual barrier landing mismatch");
  unit.set(100,100);build.team=Team.sharded;new SpaceJumpAbility().landing(unit,800,100,landing);check(landing.x==800,"friendly shield blocked fold");
  build.team=Team.crux;build.broken=true;new SpaceJumpAbility().landing(unit,800,100,landing);check(landing.x==800,"broken shield blocked fold");
  build.broken=false;
  Unit suppressor=mindustry.content.UnitTypes.flare.create(Team.sharded);suppressor.set(800,100);suppressor.dead=false;suppressor.abilities=new Ability[]{new FluxSuppressionAbility(200)};
  java.lang.reflect.Field added=UnitEntity.class.getDeclaredField("added");added.setAccessible(true);added.set(suppressor,true);Groups.unit.add(suppressor);
  Vars.state.teams.updateTeamStats();suppressor.team.data().tree().clear();suppressor.team.data().tree().insert(suppressor);
  new SpaceJumpAbility().landing(unit,800,100,landing);check(landing.x>700 && build.realRadius()==0,"suppressed shield still blocks its full field");
  suppressor.team.data().tree().clear();Groups.build.clear();
  Unit shield=source(800,100,new Field(100));
  new SpaceJumpAbility().landing(unit,800,100,landing);check(landing.x<700-unit.hitSize/2,"unit field allowed landing inside");
  Unit neighbour=source(650,100,new Field(100));
  new SpaceJumpAbility().landing(unit,800,100,landing);check(landing.x<550-unit.hitSize/2,"overlapping shield union not respected");
  Groups.unit.clear();SafeField safe=new SafeField(100);shield=source(800,100,safe);
  new SpaceJumpAbility().landing(unit,800,100,landing);check(landing.x<700-unit.hitSize/2,"safe field allowed landing inside");
  safe.broken(true);new SpaceJumpAbility().landing(unit,800,100,landing);check(landing.x>700,"broken safe field still blocks its full field");
  safe.broken(false);shield.set(1200,500);
  SpaceJumpAbility dynamic=new SpaceJumpAbility();check(dynamic.jump(unit,800,100),"dynamic charge did not start");
  shield.set(800,100);ticks=0;while(dynamic.active() && ticks++<1000)dynamic.update(unit);
  check(unit.x<700-unit.hitSize/2,"field arriving during charge failed to redirect landing");
  unit.set(800,100);check(new SpaceJumpAbility().landing(unit,820,100,landing)==null,"unsafe enclosed landing accepted");
  check(new SpaceJumpAbility().landing(unit,1100,100,landing)!=null && landing.x==1100,"leaving enemy field disallowed");
  System.out.println("PASS: enemy building/unit/safe flux fields, friendly/broken/suppressed exceptions, overlapping fields, preview parity, dynamic charge redirect, enclosed landing rejection and exit");
 }
}
