package cryon.Type;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.*;
import mindustry.game.Team;
import mindustry.graphics.Layer;

/** Shared thermal response and local discharge for building and unit flux barriers. */
public final class FluxBarrierEffects{
    private FluxBarrierEffects(){}

    // Bounded heat per point of damage avoids the old cubic single-hit overload.
    public static float heat(float damage){
        damage = Math.max(0f, damage);
        return damage * (0.25f + 0.25f * (1f - 200f / (damage + 200f)));
    }

    public static float coolingDelay(float damage){
        return Math.min(Math.max(damage, 0f) * 0.5f, 120f);
    }

    private static final Effect thermalArc = new Effect(16f, e -> {
        Draw.color(e.color, Color.white, e.fout());
        Lines.stroke(1.4f * e.fout());
        float angle = e.rotation;
        float length = 7f + Mathf.randomSeed(e.id, 11f);
        float dx = Angles.trnsx(angle, length), dy = Angles.trnsy(angle, length);
        float bend = Mathf.randomSeed(e.id + 1, -5f, 5f);
        float mx = e.x + dx * 0.5f + Angles.trnsx(angle + 90f, bend);
        float my = e.y + dy * 0.5f + Angles.trnsy(angle + 90f, bend);
        Lines.line(e.x, e.y, mx, my);
        Lines.line(mx, my, e.x + dx, e.y + dy);
        Draw.reset();
    }).layer(Layer.shields + 11f);

    public static void particles(float x, float y, float radius, int sides, float rotation, float heat, Color color){
        if(Vars.headless || radius <= 1f || heat <= 0f) return;
        heat = Mathf.clamp(heat);
        // Sample within the actual polygon; capped attempts keep large fields inexpensive.
        for(int i = 0; i < 3; i++){
            if(!Mathf.chanceDelta(heat * heat * 0.18f)) continue;
            Tmp.v1.rnd(radius * Mathf.sqrt(Mathf.random()));
            float px = x + Tmp.v1.x, py = y + Tmp.v1.y;
            if(!Intersector.isInRegularPolygon(sides, x, y, radius, rotation, px, py)) continue;
            Fx.circleColorSpark.at(px, py, color);
            if(Mathf.chance(heat)) thermalArc.at(px, py, Mathf.random(360f), color);
        }
    }

    public static void explode(Team team, float x, float y, Color color){
        Fx.dynamicSpikes.at(x, y, 24f, color);
        // Four-tile burst; visual thermal arcs above never deal damage.
        if(Vars.net.client()) return;
        Damage.damage(team, x, y, 32f, 100f, true, true);
        for(int i = 0; i < 4; i++){
            Lightning.create(team, color, 20f, x, y, i * 90f + Mathf.range(15f), 6);
        }
    }
}
