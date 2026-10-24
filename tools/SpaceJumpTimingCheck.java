import cryon.Type.*;
import mindustry.Vars;
import mindustry.gen.*;
import mindustry.game.Team;
import mindustry.type.UnitType;

public class SpaceJumpTimingCheck{
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  SpaceJumpCheck.main(args);Groups.unit.clear();Groups.build.clear();
  Unit unit=UnitEntity.create();unit.type=new UnitType("timing-probe");
  unit.type.flying=true;unit.type.speed=1;unit.type.rotateSpeed=6;
  unit.team=Team.sharded;unit.maxHealth=600;unit.health=600;unit.elevation=1;unit.speedMultiplier=1;unit.dead=false;unit.hitSize=12;
  unit.set(100,100);unit.rotation=0;
  SpaceJumpAbility jump=new SpaceJumpAbility();
  jump.minimumJumpDistance=0; // isolate the speed/time thresholds below

  check(jump.worthwhile(unit,800,100),"slow unit rejected a large saving");
  unit.type.speed=6;check(!jump.worthwhile(unit,800,100),"fast unit jumped despite tiny saving");
  unit.type.speed=1;
  check(!jump.worthwhile(unit,300,100) && jump.worthwhile(unit,305,100),"two-second saving threshold incorrect");
  unit.type.speed=3;
  check(!jump.worthwhile(unit,700,100) && jump.worthwhile(unit,715,100),"trigger distance did not scale with speed");
  unit.type.speed=1;unit.rotation=180;unit.type.rotateSpeed=.5f;
  check(!jump.worthwhile(unit,800,100),"slow alignment time ignored");
  unit.type.rotateSpeed=6;check(jump.worthwhile(unit,800,100),"normal alignment rejected");
  unit.rotation=0;unit.maxHealth=16000;check(!jump.worthwhile(unit,512,100) && jump.worthwhile(unit,514,100),"50% time ratio or capital charging cost ignored");
  unit.maxHealth=100;check(jump.worthwhile(unit,320,100),"short small-ship charge not reflected");
  unit.maxHealth=600;unit.speedMultiplier=2;check(!jump.worthwhile(unit,305,100),"status speed multiplier ignored");
  unit.speedMultiplier=1;jump.maxDistance=120;check(!jump.worthwhile(unit,2000,100),"range-capped hop compared against full route");
  jump.maxDistance=960;
  FluxBarrier barrier=new FluxBarrier("timing-flux-check");barrier.radius=100;
  FluxBarrier.ForceBuild shield=barrier.new ForceBuild();shield.block=barrier;shield.team=Team.crux;shield.x=350;shield.y=100;shield.broken=false;shield.radscl=1;
  Groups.build.add(shield);
  check(!jump.worthwhile(unit,350,100),"flux-shortened hop still triggered for tiny saving");
  Groups.build.clear();
  jump.minimumJumpDistance=320;
  check(!jump.jump(unit,419,100),"near manual jump accepted");
  unit.type.speed=6;
  check(!jump.worthwhile(unit,500,100) && jump.jump(unit,500,100),"far manual choice should not require automatic time savings");
  System.out.println("PASS: speed-dependent threshold, two-second and 50% savings, alignment, hull charge cost, status speed, range/flux limited hop and manual override");
 }
}
