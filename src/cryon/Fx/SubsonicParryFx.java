package cryon.Fx;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import mindustry.entities.Effect;
import mindustry.graphics.*;

/** A sharp, pixel-like parry flash followed by split fragments and neon separation. */
public final class SubsonicParryFx{
    public static final Color cyan = Color.valueOf("70faff"), pink = Color.valueOf("ff5aae"), gold = Color.valueOf("ffd47c");

    public static class Trace{
        public final float incoming, size;
        public Trace(float incoming, float size){ this.incoming = incoming; this.size = size; }
    }

    public static final Effect cut = new Effect(22, 140, e -> {
        if(!(e.data instanceof Trace)) return;
        Trace trace = (Trace)e.data;
        float age = e.time, fade = e.fout();
        // First three frames: a solid white knife-shaped cross, with offset colour edges.
        if(age < 5){
            float flash = Mathf.clamp(1 - age / 5);
            float length = 18 + trace.size;
            float dx = Angles.trnsx(e.rotation + 90, 1.1f), dy = Angles.trnsy(e.rotation + 90, 1.1f);
            Draw.color(pink, flash);
            Drawf.tri(e.x - dx, e.y - dy, 4 * flash, length + 5, e.rotation);
            Drawf.tri(e.x - dx, e.y - dy, 4 * flash, length + 5, e.rotation + 180);
            Draw.color(cyan, flash);
            Drawf.tri(e.x + dx, e.y + dy, 4 * flash, length + 5, e.rotation);
            Drawf.tri(e.x + dx, e.y + dy, 4 * flash, length + 5, e.rotation + 180);
            Draw.color(Color.white, flash);
            for(int side : new int[]{0, 180}){
                Drawf.tri(e.x, e.y, 4.5f * flash, length, e.rotation + side);
                Drawf.tri(e.x, e.y, 2.5f * flash, length * 0.55f, e.rotation + side + 90);
            }
            Fill.square(e.x, e.y, 2.4f * flash, e.rotation + 45);
        }
        // Hard-edged cyan/magenta echoes peel away instead of a soft explosion puff.
        if(age < 12){
            float alpha = (1 - age / 12) * 0.7f;
            for(int side : new int[]{-1, 1}){
                Draw.color(side < 0 ? pink : cyan, alpha);
                Lines.stroke(1.4f * alpha);
                float split = side * age * 0.32f;
                float x = e.x + Angles.trnsx(e.rotation + 90, split);
                float y = e.y + Angles.trnsy(e.rotation + 90, split);
                Lines.lineAngleCenter(x, y, e.rotation, 18 + age * 1.7f);
            }
        }
        // Two purely visual halves preserve the incoming round's direction.
        for(int side : new int[]{-1, 1}){
            float angle = trace.incoming + side * 52;
            float distance = 3 + age * 1.3f;
            float x = e.x + Angles.trnsx(angle, distance);
            float y = e.y + Angles.trnsy(angle, distance);
            Draw.color(gold, Color.white, fade);
            Draw.alpha(fade);
            Fill.rect(Mathf.round(x * 2) / 2f, Mathf.round(y * 2) / 2f,
                (3 + trace.size * 0.25f) * fade, 1.2f * fade, trace.incoming + side * age * 8);
            Lines.stroke(0.8f * fade);
            Lines.lineAngle(x, y, angle + 180, 5 * fade);
        }
        Angles.randLenVectors(e.id, 12, 3 + e.finpow() * 32, trace.incoming + 180, 105, (x, y) -> {
            Draw.color(gold, Color.white, fade);
            Draw.alpha(fade);
            float px = Mathf.round((e.x + x) * 2) / 2f, py = Mathf.round((e.y + y) * 2) / 2f;
            Fill.square(px, py, 0.65f * fade);
            Lines.stroke(0.7f * fade);
            Lines.lineAngle(px, py, Mathf.angle(x, y), 1 + 4 * fade);
        });
        Drawf.light(e.x, e.y, 42 * fade, gold, 0.65f * fade);
        Draw.reset();
    }).layer(Layer.effect + 0.01f);

    private SubsonicParryFx(){}
}
