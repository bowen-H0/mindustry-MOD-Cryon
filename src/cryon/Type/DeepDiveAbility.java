package cryon.Type;

import arc.Core;
import cryon.Content.SubmarineContent;
import mindustry.Vars;
import mindustry.ai.types.CommandAI;
import mindustry.entities.abilities.Ability;
import mindustry.gen.Unit;

/** RTS stance is saved by the normal command controller; status preserves the state when piloted. */
public class DeepDiveAbility extends Ability{
    private float detection;
    @Override public void init(mindustry.type.UnitType type){ detection = ((SubmarineUnitType)type).detectionRange; }
    @Override public void addStats(arc.scene.ui.layout.Table table){
        table.add(Core.bundle.format("ability.cryon-deep-dive.description", (int)(detection / 8f))).wrap().width(320f).row();
    }
    @Override public void update(Unit unit){
        if(Vars.net != null && Vars.net.client()) return;
        if(unit.controller() instanceof CommandAI){
            if(((CommandAI)unit.controller()).hasStance(SubmarineContent.diveStance)) unit.apply(SubmarineContent.diveStatus);
            else unit.unapply(SubmarineContent.diveStatus);
        }else if(!unit.isPlayer()){
            // Enemy wave submarines can use the same concealment mechanic.
            unit.apply(SubmarineContent.diveStatus);
        }
    }

    @Override public String localized(){ return Core.bundle.get("ability.cryon-deep-dive"); }
}
