import arc.Core;
import arc.graphics.*;
import arc.graphics.g2d.*;
import cryon.Fx.NanoFeedbackFx;
import mindustry.entities.Effect;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

/** Render the actual native particle geometry at three ages, without a desktop/OpenGL session. */
public final class NanoFeedbackPreview{
    static class Capture extends CriticalEffectPreview.Capture{
        int polygons;
        @Override void polygon(float[] xy,float packed){
            for(float v:xy)if(!Float.isFinite(v))throw new AssertionError("nonfinite particle vertex");
            polygons++;super.polygon(xy,packed);
        }
    }
    public static void main(String[]args)throws Exception{
        Core.atlas=new TextureAtlas(){
            final AtlasRegion whiteRegion=new AtlasRegion();
            @Override public AtlasRegion white(){return whiteRegion;}
            @Override public AtlasRegion find(String name){return name.equals("circle")?CriticalEffectPreview.circle:whiteRegion;}
        };
        CriticalEffectPreview.circle.texture=new Texture(){@Override public boolean isDisposed(){return false;}};
        Capture capture=new Capture();Core.batch=capture;mindustry.Vars.headless=true;
        Effect[] effects={NanoFeedbackFx.assimilate,NanoFeedbackFx.oreBloom,NanoFeedbackFx.grow,NanoFeedbackFx.corrosion,
            NanoFeedbackFx.repair,NanoFeedbackFx.containment,NanoFeedbackFx.rejected,NanoFeedbackFx.charge,
            NanoFeedbackFx.warning,NanoFeedbackFx.pulseShot,NanoFeedbackFx.laserHit,NanoFeedbackFx.explosion};
        String[] names={"ASSIMILATION","ORE CONVERSION / MINING","COLONY GROWTH","CORROSION",
            "WALL REGENERATION","CONTAINMENT FIELD","SUPPRESSED INFECTION","REPLICATION / FACTORY CHARGE",
            "BLOCKAGE WARNING","GREEN PULSE MUZZLE","LASER IMPACT","NANO EXPLOSION"};
        BufferedImage sheet=new BufferedImage(1200,1200,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=sheet.createGraphics();g.setColor(new java.awt.Color(18,27,29));g.fillRect(0,0,1200,1200);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        for(int i=0;i<effects.length;i++){
            int row=i/3,col=i%3;
            for(float age:new float[]{0.12f,0.42f,0.8f}){
                Effect effect=effects[i];Effect.EffectContainer e=new Effect.EffectContainer();
                e.id=37+i;e.color=NanoFeedbackFx.green;e.time=effect.lifetime*age;e.lifetime=effect.lifetime;
                e.rotation=effect==NanoFeedbackFx.containment?160:effect==NanoFeedbackFx.explosion?110:effect==NanoFeedbackFx.grow?3:8;
                Graphics2D panel=age==0.42f?(Graphics2D)g.create():new BufferedImage(1200,1200,BufferedImage.TYPE_INT_ARGB).createGraphics();
                float scale=effect==NanoFeedbackFx.containment?0.75f:effect==NanoFeedbackFx.explosion?1.1f:4f;
                panel.translate(col*400+200,row*300+165);panel.scale(scale,-scale);capture.graphics=panel;
                int before=capture.polygons;effect.render(e);
                if(capture.polygons<=before)throw new AssertionError("empty effect: "+names[i]);
                if(Draw.getBlend()!=Blending.normal)throw new AssertionError("blend leak");
                if(!Draw.getColor().equals(Color.white))throw new AssertionError("color/alpha leak");
                panel.dispose();
            }
            g.setColor(new java.awt.Color(175,226,200));g.drawString(names[i],col*400+18,row*300+26);
        }
        g.dispose();File target=new File("build/reports/nano-feedback-preview.png");target.getParentFile().mkdirs();ImageIO.write(sheet,"png",target);
        System.out.println("NANO_FEEDBACK_PREVIEW_PASS: 12 native effects at 3 ages, finite geometry, color/alpha/blend reset");
    }
}
