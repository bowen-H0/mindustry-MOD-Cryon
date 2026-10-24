package cryon.Fx;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import mindustry.entities.Effect;
import mindustry.graphics.Drawf;
import static arc.math.Angles.randLenVectors;

/** Scorched Earth's molten impact: hot core, dust crown and cooling embers. */
public class AfterglowImpact extends Effect {
    public boolean small;
    private static final Color hot = Color.valueOf("fff1ce");
    private static final Color gold = Color.valueOf("ffb461");
    private static final Color ember = Color.valueOf("c4513a");
    private static final Color ash = Color.valueOf("51443f");

    public AfterglowImpact(){ super(); }

    @Override
    public void init(){
        lifetime = small ? 30f : 58f;
        clip = small ? 64f : 144f;
        super.init();
    }

    @Override
    public void render(EffectContainer e){
        float radius = small ? 19f : 43f;
        // Soft dust settles behind the hot blast rather than covering its center.
        Draw.color(ash);
        Draw.alpha(0.35f * e.fout());
        randLenVectors(e.id + 31, small ? 3 : 6, radius * 0.8f * e.fin(), (x, y) ->
            Fill.poly(e.x + x, e.y + y, 24, (small ? 3f : 7f) * Mathf.sin(e.fin() * Mathf.PI), 0f));
        Draw.color(gold, ember, e.fin());
        Draw.alpha(e.fout());
        Lines.stroke((small ? 1.4f : 2.6f) * e.fout());
        Lines.circle(e.x, e.y, 2f + radius * (1f - Mathf.pow(e.fout(), 3f)));
        // A short incandescent center and angular rays mark the initial detonation.
        e.scaled(small ? 9f : 14f, flash -> {
            Draw.color(hot, gold, flash.fin());
            Draw.alpha(flash.fout());
            Fill.poly(flash.x, flash.y, 24, (small ? 4f : 9f) * flash.fout(), 0f);
            for(int sign : Mathf.signs){
                Drawf.tri(flash.x, flash.y, (small ? 2f : 4f) * flash.fout(),
                        (small ? 12f : 28f) * flash.fout(), 45f + sign * 90f);
            }
        });
        Draw.color(gold, ember, e.fin());
        Draw.alpha(e.fout());
        Lines.stroke((small ? 1f : 1.6f) * e.fout());
        randLenVectors(e.id, small ? 5 : 11, radius * 1.25f * e.fin(), (x, y) -> {
            float angle = Mathf.angle(x, y);
            Lines.lineAngle(e.x + x, e.y + y, angle, (small ? 3f : 6f) * e.fout());
            Fill.poly(e.x + x, e.y + y, 12, (small ? 0.7f : 1.2f) * e.fout(), 0f);
        });
        Drawf.light(e.x, e.y, (small ? 28f : 68f) * e.fout(), gold, 0.5f * e.fout());
        Draw.reset();
    }
}
