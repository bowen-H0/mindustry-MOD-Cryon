import arc.graphics.g2d.TextureRegion;
import cryon.Fx.SpaceJumpFx;
import cryon.Type.SpaceJumpAbility;
import mindustry.Vars;
import mindustry.gen.*;
import mindustry.type.UnitType;
import mindustry.graphics.Trail;

public class SpaceJumpDigitalCheck{
 static class Engine extends UnitType.UnitEngine{
  int draws;
  Engine(){super(0,-25,5,-90);}
  public void draw(Unit unit){draws++;}
 }
 static void check(boolean value,String text){if(!value)throw new AssertionError(text);}
 public static void main(String[] args)throws Exception{
  SpaceJumpCheck.main(args);Groups.unit.clear();Groups.build.clear();
  Unit unit=UnitEntity.create();unit.type=new UnitType("digital-probe");unit.type.flying=true;unit.type.speed=1;unit.type.rotateSpeed=6;
  unit.elevation=1;unit.dead=false;unit.team=mindustry.game.Team.sharded;unit.maxHealth=8000;unit.health=8000;unit.hitSize=40;unit.speedMultiplier=1;
  unit.type.region=new TextureRegion();unit.type.region.width=80;unit.type.region.height=100;
  unit.type.fullIcon=new TextureRegion();unit.type.fullIcon.width=120;unit.type.fullIcon.height=160;
  Engine original=new Engine();
  Class<?> wrapper=Class.forName("cryon.Features.SpaceJump$DigitalEngine");
  java.lang.reflect.Constructor<?> constructor=wrapper.getDeclaredConstructor(UnitType.UnitEngine.class);constructor.setAccessible(true);
  UnitType.UnitEngine engine=(UnitType.UnitEngine)constructor.newInstance(original);unit.type.engines.add(engine);
  SpaceJumpAbility jump=new SpaceJumpAbility();unit.abilities=new mindustry.entities.abilities.Ability[]{jump};
  check(SpaceJumpFx.digitalHalfSize(unit)>=86,"cube does not fit actual hull icon");
  engine.draw(unit);check(original.draws==1,"ordinary engine drawing suppressed");
  unit.set(100,100);jump.begin(unit,800,100,100,100,0,4);engine.draw(unit);
  check(jump.digitalizing() && original.draws==2,"native engine missing during digital charge");
  jump.finish(unit,800,100,0);check(jump.reconstructing(),"arrival immediately revealed ship without reconstruction");
  engine.draw(unit);check(original.draws==3,"native engine missing during reconstruction");
  check(!jump.jump(unit,1000,100),"second jump accepted during reconstruction");
  unit.trail=new Trail(10);unit.trail.update(100,100);unit.trail.update(120,100);
  unit.set(999,999);unit.rotation=99;jump.afterUpdate(unit);
  check(unit.x==800 && unit.y==100 && unit.rotation==0,"unit escaped reconstruction cube");
  check(unit.trail.size()==0,"native trail remained during digitalization");
  for(int i=0;i<53;i++)jump.update(unit);check(jump.reconstructing(),"reconstruction ended before block collapse");
  jump.update(unit);check(!jump.reconstructing(),"reconstruction did not finish with block collapse");
  engine.draw(unit);check(original.draws==4,"normal flight engines did not resume");
  unit.maxHealth=16000;jump.begin(unit,1200,100,800,100,0,5);jump.finish(unit,1200,100,0);
  for(int i=0;i<71;i++)jump.update(unit);check(jump.reconstructing(),"grade 5 used wrong reconstruction duration");
  jump.update(unit);check(!jump.reconstructing(),"grade 5 reconstruction stuck");
  SpaceJumpAbility small=new SpaceJumpAbility();unit.maxHealth=100;small.begin(unit,1400,100,1200,100,0,1);small.finish(unit,1400,100,0);
  check(!small.reconstructing() && !small.digitalizing(),"accepted lower tiers gained digital delay");
  System.out.println("PASS: sprite-sized digital cube, native engines retained and native trail suppressed during 4/5 digitalization, locked reconstruction, distinct 54/72 tick lifetimes, lower tiers unchanged");
 }
}
