import arc.Core;
import arc.graphics.Texture;
import arc.graphics.g2d.Batch;
import arc.graphics.g2d.TextureRegion;
import cryon.Type.SpaceJumpAbility;
import mindustry.gen.Unit;
import mindustry.gen.UnitEntity;
import mindustry.type.UnitType;

public class SpaceJumpNativePointCheck{
 static class EmptyBatch extends Batch{
  protected void draw(Texture t,float[] v,int o,int n){}
  protected void draw(TextureRegion r,float x,float y,float ox,float oy,float w,float h,float a){}
  protected void flush(){}
 }
 static class NativeType extends UnitType{
  boolean called,fail;
  NativeType(){super("native-point-probe");flying=true;region=new TextureRegion();shadowRegion=new TextureRegion();drawSoftShadow=true;lightRadius=90;}
  public void draw(Unit unit){
   called=true;
   check(unit.abilities.length==0,"abilities were duplicated in native capture");
   check(engines.size==1,"native engine excluded from point capture");
   check(shadowRegion.width==0 && !drawSoftShadow,"shadows duplicated in point layer");
   unit.shadowAlpha=.9f;
   if(fail)throw new IllegalStateException("capture failure");
  }
 }
 static void check(boolean value,String text){if(!value)throw new AssertionError(text);}
 public static void main(String[] args)throws Exception{
  SpaceJumpCheck.main(args);Core.batch=new EmptyBatch();
  Unit unit=UnitEntity.create();NativeType type=new NativeType();unit.type=type;unit.elevation=1;unit.dead=false;unit.maxHealth=8000;
  unit.set(100,100);unit.shadowAlpha=.4f;unit.shieldAlpha=.5f;
  SpaceJumpAbility jump=new SpaceJumpAbility();unit.abilities=new mindustry.entities.abilities.Ability[]{jump};
  type.engines.add(new UnitType.UnitEngine(0,-20,5,-90));
  jump.begin(unit,800,100,100,100,0,4);
  Object abilities=unit.abilities,engines=type.engines,shadow=type.shadowRegion;
  java.lang.reflect.Method draw=Class.forName("cryon.Features.SpaceJumpPointRenderer").getDeclaredMethod("drawNativeHull",Unit.class,float.class);draw.setAccessible(true);
  draw.invoke(null,unit,115f);check(type.called,"actual UnitType.draw was bypassed");
  check(unit.abilities==abilities && type.engines==engines && type.shadowRegion==shadow,"native capture leaked shared render state");
  check(unit.shadowAlpha==.4f && unit.shieldAlpha==.5f && type.drawSoftShadow && type.lightRadius==90,"unit state not restored");
  type.fail=true;
  try{draw.invoke(null,unit,115f);throw new AssertionError("expected failure");}catch(java.lang.reflect.InvocationTargetException expected){}
  check(unit.abilities==abilities && type.engines==engines && type.shadowRegion==shadow,"failed capture leaked state");
  System.out.println("PASS: actual native unit draw captured; native engines included, no duplicate abilities/shadows; shared and per-unit state restored on success/failure");
 }
}
