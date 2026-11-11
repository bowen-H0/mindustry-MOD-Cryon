package cryon.Fx;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Interp;
import arc.math.Mathf;
import mindustry.entities.Effect;
import mindustry.graphics.Drawf;

import static cryon.Fx.NukeFx.*;

/** Local nuclear impact for the Rainburst's sustained barrage. */
public class RainburstImpact extends Effect{
    public float radius;

    @Override
    public void init(){
        lifetime = 44f;
        clip = radius * 2f;
        super.init();
    }

    @Override
    public void render(EffectContainer e){
        e.scaled(19f, wave -> {
            float reach = radius * Interp.pow3Out.apply(wave.fin());
            Draw.color(glow, hot, wave.fin());
            Draw.alpha(0.85f * wave.fout());
            Lines.stroke(4f * wave.fout());
            Lines.circle(e.x, e.y, reach);
            Draw.color(danger);
            Draw.alpha(0.55f * wave.fout());
            Lines.stroke(2.5f * wave.fout());
            Lines.circle(e.x, e.y, reach * 0.84f);
        });

        e.scaled(12f, flash -> {
            float fireball = radius * (0.15f + 0.28f * Interp.pow3Out.apply(flash.fin()));
            Draw.color(danger);
            Draw.alpha(0.55f * flash.fout());
            Fill.circle(e.x, e.y, fireball);
            Draw.color(hot);
            Draw.alpha(0.85f * flash.fout());
            Fill.circle(e.x, e.y, fireball * 0.76f);
            Draw.color(Color.white, glow, flash.fin());
            Draw.alpha(flash.fout());
            Fill.circle(e.x, e.y, fireball * 0.48f);
            Drawf.light(e.x, e.y, radius * 1.35f, hot, 0.75f * flash.fout());
        });

        // Let some impacts form a full plume so overlapping shots leave room to see the front line.
        if(Mathf.randomSeed(e.id, 0, 3) == 0){
            float rise = Interp.pow2Out.apply(Mathf.clamp((e.time - 4f) / 22f));
            float opacity = rise * e.fout();
            float height = radius * 0.65f * rise;
            float capRadius = radius * (0.12f + 0.18f * rise);
            float stem = radius * 0.085f;
            Draw.color(hot, ash, e.fin());
            Draw.alpha(0.82f * opacity);
            Fill.quad(e.x - stem, e.y, e.x + stem, e.y,
                e.x + stem * 0.7f, e.y + height, e.x - stem * 0.7f, e.y + height);

            for(int i = 0; i < 7; i++){
                float angle = i * (360f / 7f) + e.time * 1.1f;
                float x = e.x + Mathf.cosDeg(angle) * capRadius * 0.72f;
                float y = e.y + height + Mathf.sinDeg(angle) * capRadius * 0.28f;
                Draw.color(i % 2 == 0 ? hot : danger, smoke, Mathf.clamp(e.fin() * 1.35f));
                Draw.alpha(0.85f * opacity);
                Fill.circle(x, y, capRadius * 0.57f);
            }
            Draw.color(glow, smoke, Mathf.clamp(e.fin() * 1.4f));
            Draw.alpha(0.75f * opacity);
            Fill.circle(e.x, e.y + height, capRadius * 0.56f);
        }
        Draw.reset();
    }
}
