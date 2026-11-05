import arc.Core;
import arc.files.Fi;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.util.ArcNativesLoader;
import cryon.Features.EonHullField;

/** Checks true-distance padding and scale without depending on a live GL context. */
public class EonBarrierRenderCheck{
    static void check(boolean ok,String message){ if(!ok)throw new AssertionError(message); }
    static float distance(Pixmap field,int x,int y){
        return (((field.get(x,y) >>> 24)/255f)*2f-1f)*EonHullField.distanceRange;
    }
    public static void main(String[] args){
        ArcNativesLoader.load();
        Pixmap square=new Pixmap(128,128);
        for(int y=32;y<96;y++)for(int x=32;x<96;x++)square.set(x,y,0xffffffff);
        Pixmap field=EonHullField.create(new PixmapRegion(square));
        int p=EonHullField.padding;
        // 32 pixels = 8 world units at Mindustry's 0.25 world-units-per-pixel scale.
        float outside=distance(field,p+127,p+64)*0.25f;
        check(Math.abs(outside+8f)<0.2f,"Expected eight-unit expansion boundary, got "+outside);
        float diagonal=distance(field,p+111,p+111);
        check(Math.abs(diagonal-(0.5f-(float)Math.sqrt(16*16+16*16)))<0.4f,"Distance field must be Euclidean");
        check(distance(field,p+64,p+64)>0f,"Filled hull interior missing");
        check(field.getA(0,0)==255,"Atlas must retain the glow padding");
        field.dispose(); square.dispose();
        Pixmap hull=new Pixmap(new Fi("sprites/units/eon.png"));
        long start=System.nanoTime();
        Pixmap actual=EonHullField.create(new PixmapRegion(hull));
        check(actual.width==hull.width+128 && actual.height==hull.height+128,"Incorrect glow margin");
        int positive=0,negative=0;
        for(int y=0;y<actual.height;y+=8)for(int x=0;x<actual.width;x+=8){
            if(distance(actual,x,y)>0)positive++;else negative++;
        }
        check(positive>100 && negative>100,"Actual hull must have interior and exterior distances");
        System.out.println("PASS: Euclidean hull field; 8-world-unit boundary; opaque glow padding; actual Eon field built in "+((System.nanoTime()-start)/1000000)+" ms.");
        actual.dispose(); hull.dispose();
    }
}
