import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.util.Time;
import cryon.Fx.SpaceJumpFx;
import mindustry.entities.Effect.EffectContainer;
import mindustry.gen.Unit;
import mindustry.gen.UnitEntity;
import mindustry.type.UnitType;
import mindustry.graphics.Trail;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** CPU capture of actual charge/warning/impact geometry; game lighting and audio are excluded. */
public class SpaceJumpEffectPreview{
 static class CpuTexture extends arc.graphics.Texture{ CpuTexture(){super();} public boolean isDisposed(){return false;} }
 public static void main(String[] args)throws Exception{
  AfterglowEffectPreview.circle.texture=new CpuTexture();
  Core.atlas=new TextureAtlas(){
   final AtlasRegion whiteRegion=new AtlasRegion();
   public AtlasRegion white(){return whiteRegion;}
   public AtlasRegion find(String name){return name.equals("circle")?AfterglowEffectPreview.circle:whiteRegion;}
  };
  AfterglowEffectPreview.Capture capture=new AfterglowEffectPreview.Capture(); Core.batch=capture;
  mindustry.Vars.headless=true; Time.time=83f;
  BufferedImage image=new BufferedImage(1600,1800,BufferedImage.TYPE_INT_RGB);
  Graphics2D g=image.createGraphics();g.setColor(new java.awt.Color(16,25,41));g.fillRect(0,0,1600,1800);
  g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
  mindustry.Vars.content=new mindustry.core.ContentLoader();
  Unit unit=UnitEntity.create(); unit.type=new UnitType("fold-preview"); unit.rotation=25f; unit.id=72;
  unit.type.engines.add(new UnitType.UnitEngine(-12,-18,3,-90), new UnitType.UnitEngine(12,-18,3,-90),new UnitType.UnitEngine(0,-24,5,-90));
  String[] names={"NEEDLE BLINK","PRISM FOLD","ORBITAL GYROSCOPE","DIGITAL CUBE","VOXEL CASCADE"};
  // Equal hull sizes deliberately demonstrate distinct topology, rather than magnification.
  unit.hitSize=34f;
  for(int row=0;row<5;row++)for(int col=0;col<4;col++){
   int level=row+1;
   Graphics2D panel=(Graphics2D)g.create();panel.translate(200+col*400,195+row*360);panel.scale(1.25,-1.25); capture.graphics=panel;
   if(col==0){
    SpaceJumpFx.charge(unit,0,0,0.8f,level,new Trail[]{new Trail(10),new Trail(10),new Trail(10)});
    Draw.color(Color.valueOf("30435b"));Fill.tri(-12,-22,12,-22,0,28);Draw.reset();
   }else if(col==1){
    SpaceJumpFx.warning(unit,0,0,0.8f,level);
   }else{
    mindustry.entities.Effect nativeEffect=(col==2?SpaceJumpFx.departureEffects:SpaceJumpFx.arrivalEffects)[row];
    EffectContainer effect=new EffectContainer();effect.id=72;effect.color=SpaceJumpFx.tint(level);effect.time=nativeEffect.lifetime*0.24f;effect.lifetime=nativeEffect.lifetime;effect.rotation=25f;
    effect.data=new SpaceJumpFx.FoldVisual(level>=4 ? SpaceJumpFx.digitalHalfSize(unit)*2f : unit.hitSize,level);
    nativeEffect.render(effect);
   }
   if(Draw.getBlend()!=arc.graphics.Blending.normal)throw new AssertionError("blend leak in class "+level);
   panel.dispose();g.setColor(new java.awt.Color(174,214,240));g.setFont(new java.awt.Font("SansSerif",java.awt.Font.PLAIN,14));
   g.drawString("CLASS "+level+" / "+names[row],18+col*400,25+row*360);
   g.drawString(new String[]{"CHARGE","DESTINATION WARNING","DEPARTURE","ARRIVAL"}[col],18+col*400,47+row*360);
  }
  g.dispose();File out=new File("build/reports/space-jump-effects-preview.png");out.getParentFile().mkdirs();ImageIO.write(image,"png",out);
  System.out.println("PASS: five distinct classes rendered at equal hull size: charge, warning, departure and arrival; no blend leaks");
 }
}
