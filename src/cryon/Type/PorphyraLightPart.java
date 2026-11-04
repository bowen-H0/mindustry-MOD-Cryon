package cryon.Type;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Interp;
import arc.util.Time;
import mindustry.entities.part.DrawPart;

/** Independent hull light mask with a smooth time-based color cycle. */
public class PorphyraLightPart extends DrawPart{
    public String suffix = "-light";
    public float cycleDuration = 1200f;
    public float opacity = 0.8f;
    private TextureRegion region;
    private final Color tint = new Color();
    private static final Color[] palette = {
        Color.valueOf("da54ff"), Color.valueOf("758cff"),
        Color.valueOf("72dcff"), Color.valueOf("d4a0ff")
    };

    @Override public void load(String name){
        region = Core.atlas.find(name + suffix);
    }

    @Override public void draw(PartParams params){}

    public void drawHull(PartParams params){
        if(region == null || !region.found()) return;
        float phase = (Time.time / Math.max(1f, cycleDuration) % 1f) * palette.length;
        int index = (int)phase;
        tint.set(palette[index]).lerp(palette[(index + 1) % palette.length],
                Interp.smooth.apply(phase - index));
        float layer = Draw.z();
        try{
            // Called immediately after the body, before cell and weapons.
            Draw.z(layer);
            Draw.blend(Blending.additive);
            Draw.color(tint, opacity);
            Draw.rect(region, params.x, params.y, params.rotation - 90f);
        }finally{
            Draw.blend();
            Draw.reset();
            Draw.z(layer);
        }
    }
}
