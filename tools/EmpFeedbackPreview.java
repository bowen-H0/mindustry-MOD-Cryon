import arc.Core;
import arc.graphics.*;
import arc.graphics.g2d.*;
import cryon.Fx.EmpFx;
import cryon.Type.EmpArcBulletType;
import mindustry.entities.Effect;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** CPU check of the actual EMP charging, electric path and blast renderers. */
public final class EmpFeedbackPreview{
    public static void main(String[]args)throws Exception{
        Core.atlas=new TextureAtlas(){
            final AtlasRegion whiteRegion=new AtlasRegion();
            @Override public AtlasRegion white(){return whiteRegion;}
            @Override public AtlasRegion find(String name){return name.equals("circle")?CriticalEffectPreview.circle:whiteRegion;}
        };
        CriticalEffectPreview.circle.texture=new Texture(){@Override public boolean isDisposed(){return false;}};
        NanoFeedbackPreview.Capture capture=new NanoFeedbackPreview.Capture();Core.batch=capture;mindustry.Vars.headless=true;
        Effect[] effects={EmpFx.charge,new Effect(40f,e->EmpArcBulletType.drawBolt(e.x,e.y,e.rotation,e.id,(int)(e.time/3f))),EmpFx.burst};String[] names={"EMP CHARGE","REAL FLYING ARC PROJECTILE","EMP BLAST"};
        BufferedImage sheet=new BufferedImage(1200,600,BufferedImage.TYPE_INT_RGB);Graphics2D g=sheet.createGraphics();
        g.setColor(new java.awt.Color(17,25,35));g.fillRect(0,0,1200,600);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        for(int row=0;row<2;row++)for(int col=0;col<3;col++){
            Effect effect=effects[col];Effect.EffectContainer e=new Effect.EffectContainer();
            e.id=71;e.lifetime=effect.lifetime;e.time=effect.lifetime*(row==0?.2f:.6f);e.rotation=col==2?160f:0f;e.color=EmpFx.blue;e.data=Float.valueOf(160f);
            Graphics2D panel=(Graphics2D)g.create();float scale=col==0?5f:col==1?4f:.8f;
            panel.translate(col*400+200,row*300+160);panel.scale(scale,-scale);capture.graphics=panel;
            int before=capture.polygons;effect.render(e);panel.dispose();
            if(capture.polygons==before||!Draw.getColor().equals(Color.white)||Draw.getBlend()!=Blending.normal)throw new AssertionError("EMP render failure");
            g.setColor(new java.awt.Color(177,225,244));g.drawString(names[col]+"  "+(row==0?"EARLY":"LATE"),col*400+18,row*300+28);
        }
        g.dispose();File out=new File("build/reports/emp-feedback-preview.png");out.getParentFile().mkdirs();ImageIO.write(sheet,"png",out);
        System.out.println("EMP_FEEDBACK_PREVIEW_PASS: charge, actual flying projectile and blast, early/late, finite geometry and draw state reset");
    }
}
