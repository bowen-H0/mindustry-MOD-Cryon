import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Texture;
import arc.graphics.g2d.Batch;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.mock.MockGL20;
import cryon.Features.ReflectShieldRenderer;

/** Reproduce an additive GPU blend left behind while the empty batch says normal. */
public class ReflectShieldBlendCheck{
 static class GL extends MockGL20{
  int src,dst;
  public void glBlendFuncSeparate(int s,int d,int sa,int da){src=s;dst=d;}
 }
 static class EmptyBatch extends Batch{
  protected void draw(Texture t,float[] v,int o,int n){}
  protected void draw(TextureRegion r,float x,float y,float ox,float oy,float w,float h,float angle){}
  protected void flush(){}
 }
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  GL gl=new GL();Core.gl=gl;Core.batch=new EmptyBatch();
  java.lang.reflect.Method composite=ReflectShieldRenderer.class.getDeclaredMethod("composite",Runnable.class);
  composite.setAccessible(true);
  Draw.blend(Blending.normal);Blending.additive.apply();
  check(gl.dst==Blending.additive.dst,"test did not establish additive GPU state");
  composite.invoke(null,(Runnable)()->check(gl.src==Blending.normal.src && gl.dst==Blending.normal.dst,"shield inherited additive blend"));
  check(Draw.getBlend()==Blending.normal && gl.dst==Blending.normal.dst,"batch/GPU state mismatch after composite");
  Draw.blend(Blending.additive);Blending.additive.apply();
  try{
   composite.invoke(null,(Runnable)()->{check(gl.dst==Blending.normal.dst,"normal blending missing");throw new IllegalStateException("test");});
  }catch(java.lang.reflect.InvocationTargetException expected){
   check(expected.getCause() instanceof IllegalStateException,"unexpected failure");
  }
  check(Draw.getBlend()==Blending.additive && gl.dst==Blending.additive.dst,"original blend not restored on failure");
  System.out.println("PASS: shield replaces inherited additive GPU state and restores caller blending, including failures");
 }
}
