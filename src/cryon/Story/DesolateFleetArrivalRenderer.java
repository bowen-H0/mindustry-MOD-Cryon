package cryon.Story;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Tmp;
import cryon.Script.DesolatePlainScript;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.type.UnitType.UnitEngine;

/** Solid white-hot engine cores and long red exhaust, behind the actual native hulls. */
public final class DesolateFleetArrivalRenderer{
    private static final Color exhaust = Color.valueOf("e52b35"), hot = Color.valueOf("ff9c82");

    public static void draw(){
        DesolatePlainScript script = DesolatePlainScript.current();
        if(script == null || script.phase != DesolatePlainScript.intro || !mindustry.Vars.state.isGame()) return;
        float fade = Mathf.clamp((script.elapsed - DesolatePlainScript.fleetArrival) / 12f)
            * Mathf.clamp((DesolatePlainScript.introDuration - script.elapsed) / 60f);
        float thrust = (0.35f + 0.65f * (1f - script.entryProgress())) * fade;
        if(thrust <= 0f) return;
        float previous = Draw.z();
        Blending blend = Draw.getBlend();
        try{
            Draw.z(Layer.flyingUnitLow - 0.01f);
            Draw.blend(Blending.additive);
            for(int slot = 0; slot < 8; slot++){
                Unit unit = script.fleetUnit(slot);
                if(unit == null) continue;
                if(unit.type.engines.isEmpty()){
                    jet(unit, 0f, -unit.type.engineOffset, Math.max(5f, unit.type.engineSize), thrust);
                }else{
                    for(UnitEngine engine : unit.type.engines) jet(unit, engine.x, engine.y, engine.radius, thrust);
                }
            }
        }finally{
            Draw.blend(blend);
            Draw.reset();
            Draw.z(previous);
        }
    }

    private static void jet(Unit unit, float localX, float localY, float radius, float thrust){
        float rotation = unit.rotation - 90f;
        float x = unit.x + Angles.trnsx(rotation, localX, localY);
        float y = unit.y + Angles.trnsy(rotation, localX, localY);
        float width = Math.max(Math.max(8f, radius * 1.2f), unit.hitSize * 0.14f);
        float length = (180f + unit.hitSize * 2.5f) * thrust;
        Draw.color(exhaust, 0.18f * thrust);
        Drawf.tri(x, y, width * 5f, length * 1.2f, unit.rotation + 180f);
        Draw.color(exhaust, 0.8f * thrust);
        Drawf.tri(x, y, width * 2.1f, length, unit.rotation + 180f);
        Draw.color(hot, 0.95f * thrust);
        Drawf.tri(x, y, width * 1.25f, length * 0.72f, unit.rotation + 180f);
        Draw.color(Color.white, thrust);
        Drawf.tri(x, y, width * 0.65f, length * 0.46f, unit.rotation + 180f);
        Fill.circle(x, y, width * 0.8f * thrust);
        Fill.light(x, y, 32, width * 5f,
            Tmp.c1.set(hot).a(0.5f * thrust), Tmp.c2.set(exhaust).a(0f));
        Fill.light(x, y, 24, width * 2f,
            Tmp.c1.set(Color.white).a(0.8f * thrust), Tmp.c2.set(hot).a(0f));
    }
}
