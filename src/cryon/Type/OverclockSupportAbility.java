package cryon.Type;

import arc.Core;
import arc.util.Time;
import cryon.Features.MapAlliances;
import mindustry.content.*;
import mindustry.entities.Units;
import mindustry.entities.abilities.StatusFieldAbility;
import mindustry.gen.Unit;

/** Pulsed native overclock, including allied map teams, without multiplying stacks. */
public class OverclockSupportAbility extends StatusFieldAbility{
    public OverclockSupportAbility(){
        super(StatusEffects.overclock, 120, 60, 96);
        applyEffect = activeEffect = Fx.none;
    }

    @Override public String localized(){ return Core.bundle.get("ability.transonic-overclock"); }

    @Override public void update(Unit unit){
        if(unit.dead || !unit.isAdded()) return;
        timer += Time.delta;
        if(timer < reload) return;
        timer %= reload;
        Units.nearby(unit.x - range, unit.y - range, range * 2, range * 2, target -> {
            if(!target.dead && target.within(unit, range) && MapAlliances.allied(unit.team, target.team)){
                target.apply(effect, duration);
            }
        });
    }
}
