package cryon.Type;

import arc.Core;
import arc.Events;
import arc.scene.ui.layout.Table;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.Time;
import cryon.Content.BioCrawlerUnits;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.Vars;
import mindustry.entities.Units;
import mindustry.entities.abilities.Ability;
import mindustry.game.EventType.*;
import mindustry.game.Team;
import mindustry.gen.*;
import java.util.IdentityHashMap;
import java.util.Map;

/** Freezes hostile building updates without persisting a changed enabled state. */
public class GlobalSuppressionAbility extends FluxSuppressionAbility{
    private static boolean installed;
    private static final Color shadow = Color.valueOf("1c0804"), warning = Color.valueOf("e34920");
    private static final class State{
        final boolean enabled, updating;
        State(Building build){ enabled = build.enabled; updating = build.isAdded(); }
    }
    private static final IdentityHashMap<Building, State> before = new IdentityHashMap<>();

    public GlobalSuppressionAbility(float radius){
        super(radius);
        install();
    }

    @Override public String localized(){ return Core.bundle.get("ability.globalsuppression"); }
    @Override public void addStats(Table table){
        table.add(Core.bundle.format("ability.globalsuppression.description", radius / 8f)).wrap().width(300).row();
    }

    private static void install(){
        if(installed) return;
        installed = true;
        Events.run(Trigger.beforeGameUpdate, GlobalSuppressionAbility::freeze);
        Events.run(Trigger.afterGameUpdate, GlobalSuppressionAbility::restore);
        Events.on(ResetEvent.class, event -> clear());
        Events.on(WorldLoadEvent.class, event -> clear());
        Events.on(SaveWriteEvent.class, event -> restore());
    }

    private static void freeze(){
        restore();
        if(Vars.state == null || Vars.state.isEditor() || !Vars.state.isGame()
                || (Vars.net != null && Vars.net.client())) return;
        // Only main-thread game-update hooks touch entities or update groups.
        Groups.unit.each(source -> {
            if(source.dead || !source.isAdded()) return;
            for(Ability ability : source.abilities){
                if(!(ability instanceof GlobalSuppressionAbility)) continue;
                float range = ((GlobalSuppressionAbility)ability).radius;
                Units.nearbyBuildings(source.x, source.y, range, build -> {
                    if(build.dead || build.team == source.team || build.team == Team.derelict) return;
                    if(!before.containsKey(build)) before.put(build, new State(build));
                    build.enabled = false;
                    // Temporarily detach from the update group, like native sleeping.
                    // This also stops blocks that ignore enabled; tiles/proximity stay intact.
                    build.remove();
                });
            }
        });
    }

    @Override public void draw(Unit unit){
        float z = Draw.z();
        float time = Time.time + unit.id * 0.37f;
        float breath = Mathf.absin(time, 30f, 1f);
        Color orange = BioCrawlerUnits.orange;

        // A dark pressure footprint sits below units, leaving weapons and terrain readable.
        Draw.z(Layer.blockOver + 0.1f);
        Draw.color(shadow, 0.17f);
        Fill.circle(unit.x, unit.y, radius);
        Draw.color(warning, 0.035f);
        Fill.circle(unit.x, unit.y, radius * 0.94f);

        Draw.z(Layer.effect - 0.1f);
        Draw.color(warning, 0.3f);
        Lines.stroke(7f);
        Lines.circle(unit.x, unit.y, radius);
        Draw.color(orange, 0.75f + breath * 0.15f);
        Lines.stroke(1.6f);
        Lines.circle(unit.x, unit.y, radius - 2f);

        // Counter-rotating siege segments and inward teeth make this distinct from a shield.
        for(int i = 0; i < 12; i++){
            float angle = i * 30f + time * 0.12f;
            Draw.color(i % 3 == 0 ? orange : warning, 0.85f);
            Lines.stroke(i % 3 == 0 ? 3.4f : 2f);
            Lines.arc(unit.x, unit.y, radius - 7f, 0.052f, angle);
            float x = unit.x + Angles.trnsx(angle, radius - 12f);
            float y = unit.y + Angles.trnsy(angle, radius - 12f);
            Drawf.tri(x, y, i % 3 == 0 ? 7f : 4f, i % 3 == 0 ? 17f : 10f, angle + 180f);
        }
        // Three slow waves converge on the carrier instead of expanding outwards.
        for(int i = 0; i < 3; i++){
            float phase = Mathf.mod(time / 150f + i / 3f, 1f);
            float alpha = Mathf.sin(phase * Mathf.PI) * 0.24f;
            Draw.color(orange, alpha);
            Lines.stroke(1.2f + (1f - phase) * 1.5f);
            Lines.poly(unit.x, unit.y, 12, radius * (1f - phase * 0.88f), -time * 0.08f);
        }

        float inner = unit.hitSize * 0.5f + 10f;
        Draw.color(orange, 0.85f);
        Lines.stroke(2f);
        for(int i = 0; i < 4; i++){
            Lines.arc(unit.x, unit.y, inner, 0.15f, i * 90f - time * 0.55f);
        }
        Draw.color(BioCrawlerUnits.core, 0.6f + breath * 0.25f);
        Lines.stroke(1f);
        Lines.poly(unit.x, unit.y, 4, inner * 0.8f, time * 0.3f);

        Draw.reset();
        Draw.z(z);
    }

    private static void clear(){
        restore();
    }

    private static void restore(){
        for(Map.Entry<Building, State> entry : before.entrySet()){
            Building build = entry.getKey();
            State previous = entry.getValue();
            build.enabled = previous.enabled;
            if(previous.updating && build.isValid()) build.add();
        }
        before.clear();
    }
}
