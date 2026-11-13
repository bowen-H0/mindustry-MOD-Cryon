package cryon.Type;

import mindustry.ai.UnitCommand;
import mindustry.gen.Unit;
import mindustry.gen.Player;
import mindustry.Vars;

/** An autonomous executor, excluded from possession, logic control and RTS commands. */
public class SkywatchUnitType extends OrbitalUnitType{
    public SkywatchUnitType(String name){
        super(name);
        playerControllable = false;
        logicControllable = false;
        allowChangeCommands = false;
        aiController = SkywatchAI::new;
        controller = unit -> new SkywatchAI();
    }
    @Override public boolean allowCommand(Unit unit, UnitCommand command){ return false; }
    @Override public void update(Unit unit){
        super.update(unit);
        if(Vars.net != null && Vars.net.client()) return;
        // Also recover old saves or forced control transfers that bypass normal input validation.
        if(unit.controller() instanceof Player){
            Player player = (Player)unit.controller();
            if(player.unit() == unit) player.clearUnit();
        }
        if(!(unit.controller() instanceof SkywatchAI)) unit.controller(new SkywatchAI());
    }
}
