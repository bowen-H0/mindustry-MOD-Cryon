package cryon.Type;

import arc.input.KeyCode;
import cryon.Features.MapAlliances;
import mindustry.Vars;
import mindustry.gen.Unit;
import mindustry.input.MobileInput;

/** Keep native touch controls while applying map diplomacy to their separate targeting path. */
public class AllianceMobileInput extends MobileInput{
    public static void install(){
        if(!Vars.headless && Vars.control != null && Vars.control.input != null
                && Vars.control.input.getClass() == MobileInput.class){
            Vars.control.setInput(new AllianceMobileInput());
        }
    }

    private void clearAlliedTarget(){
        if(target != null && Vars.player != null && !MapAlliances.mayTarget(target, Vars.player.team())){
            target = null;
            if(!manualShooting) Vars.player.shooting = false;
        }
    }

    @Override protected void updateMovement(Unit unit){
        clearAlliedTarget();
        MapAlliances.Scope scope = MapAlliances.enterTeam(unit.team);
        try{
            super.updateMovement(unit);
        }finally{
            MapAlliances.exit(scope);
            clearAlliedTarget();
        }
    }

    @Override public boolean tap(float x, float y, int count, KeyCode button){
        MapAlliances.Scope scope = Vars.player == null ? null : MapAlliances.enterTeam(Vars.player.team());
        try{
            return super.tap(x, y, count, button);
        }finally{
            MapAlliances.exit(scope);
            // Native taps also select buildings directly, without an enemy-index search.
            clearAlliedTarget();
        }
    }
}
