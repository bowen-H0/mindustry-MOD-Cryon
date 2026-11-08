package cryon.Type;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;

/** Mech-only blue field arcs; shared suppression rules and other carriers stay unchanged. */
public class MechFluxSuppressionAbility extends FluxSuppressionAbility{
    private static final Color blue = Color.valueOf("589dff");

    public MechFluxSuppressionAbility(float radius){ super(radius); }

    @Override public void draw(Unit unit){
        float z = Draw.z();
        Draw.z(Layer.effect);
        float stroke = 0.7f + Mathf.absin(20f, 0.7f);
        float innerRadius = 5f * (1f + Mathf.absin(20f, 0.1f)) + 3f;
        Lines.stroke(stroke, blue);
        for(int i = 0; i < 4; i++){
            Lines.arc(unit.x, unit.y, innerRadius, 0.1f, unit.rotation + i * 90f - Time.time * 0.7f);
        }
        Lines.stroke(stroke * 0.65f);
        for(int i = 0; i < 4; i++){
            Lines.arc(unit.x, unit.y, radius, 0.1f, unit.rotation + i * 90f + Time.time * 0.7f);
        }
        Drawf.light(unit.x, unit.y, radius * 1.5f, blue, 0.65f * 0.8f);
        Draw.reset();
        Draw.z(z);
    }
}
