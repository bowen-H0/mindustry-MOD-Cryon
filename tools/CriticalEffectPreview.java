import arc.Core;
import arc.graphics.Color;
import arc.graphics.Texture;
import arc.graphics.g2d.*;
import cryon.Fx.AfterglowImpact;
import mindustry.entities.Effect.EffectContainer;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

/** CPU preview of the actual effect geometry; lighting is left to the game renderer. */
public class CriticalEffectPreview {
    static final TextureAtlas.AtlasRegion circle=new TextureAtlas.AtlasRegion();
    static class Capture extends Batch {
        Graphics2D graphics;
        void polygon(float[] xy,float packed){
            Color c=new Color().abgr8888(packed);
            graphics.setColor(new java.awt.Color(c.r,c.g,c.b,Math.min(1,c.a)));
            Path2D path=new Path2D.Float();path.moveTo(xy[0],xy[1]);
            for(int i=2;i<xy.length;i+=2)path.lineTo(xy[i],xy[i+1]);
            path.closePath();graphics.fill(path);
        }
        protected void draw(Texture texture,float[] vertices,int offset,int count){
            for(int i=offset;i<offset+count;i+=24){
                float[] points=new float[8];
                for(int j=0;j<4;j++){points[j*2]=vertices[i+j*6];points[j*2+1]=vertices[i+j*6+1];}
                polygon(points,vertices[i+2]);
            }
        }
        protected void draw(TextureRegion region,float x,float y,float ox,float oy,float width,float height,float rotation){
            if(region==circle){
                Color c=new Color().abgr8888(colorPacked);
                graphics.setColor(new java.awt.Color(c.r,c.g,c.b,Math.min(1,c.a)));
                graphics.fill(new java.awt.geom.Ellipse2D.Float(x,y,width,height));return;
            }
            float[] points={-ox,-oy,-ox,height-oy,width-ox,height-oy,width-ox,-oy};
            double angle=Math.toRadians(rotation);
            for(int i=0;i<8;i+=2){float px=points[i],py=points[i+1];points[i]=(float)(px*Math.cos(angle)-py*Math.sin(angle))+x+ox;points[i+1]=(float)(px*Math.sin(angle)+py*Math.cos(angle))+y+oy;}
            polygon(points,colorPacked);
        }
        protected void flush(){}
    }
    public static void main(String[] args)throws Exception{
        Core.atlas=new TextureAtlas(){
            final AtlasRegion whiteRegion=new AtlasRegion();
            public AtlasRegion white(){return whiteRegion;}
            public AtlasRegion find(String name){return name.equals("circle") ? circle : whiteRegion;}
        };
        circle.texture=new arc.graphics.Texture(){public boolean isDisposed(){return false;}};
        Capture capture=new Capture();Core.batch=capture;mindustry.Vars.headless=true;
        BufferedImage image=new BufferedImage(1280,960,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();g.setColor(new java.awt.Color(18,23,34));g.fillRect(0,0,1280,960);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        mindustry.entities.Effect[] effects={cryon.Fx.CriticalFx.shoot,cryon.Fx.CriticalFx.impact,cryon.Fx.CriticalFx.satelliteHit};
        for(int mobile=0;mobile<2;mobile++)for(int row=0;row<3;row++)for(int col=0;col<4;col++){
            mindustry.Vars.mobile=mobile==1;
            mindustry.entities.Effect fx=effects[row];
            EffectContainer e=new EffectContainer();e.id=27;e.color=cryon.Fx.CriticalFx.pink;e.time=fx.lifetime*new float[]{0.08f,0.25f,0.5f,0.8f}[col];e.lifetime=fx.lifetime;e.rotation=0f;
            Graphics2D panel=(Graphics2D)g.create();panel.translate(160+col*320,180+row*300);panel.scale(1.1,-1.1);capture.graphics=panel;
            fx.render(e);
            if(Draw.getBlend()!=arc.graphics.Blending.normal)throw new AssertionError("blend leak");
            panel.dispose();
            if(mobile==0){g.setColor(new java.awt.Color(240,198,224));g.drawString(new String[]{"MUZZLE SHOCK","MAIN IMPACT","SATELLITE HIT"}[row]+"  t="+(int)e.time,25+col*320,35+row*300);}
        }
        g.dispose();File out=new File("build/reports/critical-effects-preview.png");out.getParentFile().mkdirs();ImageIO.write(image,"png",out);
        System.out.println("PASS: all three effects at four stages, desktop/mobile; no blend leaks");
    }
}
