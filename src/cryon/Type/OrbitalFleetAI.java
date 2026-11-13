package cryon.Type;

import cryon.Features.MapAlliances;
import cryon.Features.OrbitalTargeting;
import mindustry.ai.types.CommandAI;
import mindustry.ai.types.FlyingAI;

/** Native movement/commands with optional, explicitly scoped orbital targeting. */
public class OrbitalFleetAI extends FlyingAI{
    public final boolean targetsOrbit;
    public OrbitalFleetAI(boolean targetsOrbit){ this.targetsOrbit = targetsOrbit; }
    @Override public void updateUnit(){
        var scope = MapAlliances.enter(unit);
        try{
            if(targetsOrbit) OrbitalTargeting.run(super::updateUnit);
            else super.updateUnit();
        }finally{ MapAlliances.exit(scope); }
    }
    public static class Commands extends CommandAI{
        public final boolean targetsOrbit;
        public Commands(boolean targetsOrbit){ this.targetsOrbit = targetsOrbit; }
        @Override public void updateUnit(){
            var scope = MapAlliances.enter(unit);
            try{
                if(targetsOrbit) OrbitalTargeting.run(super::updateUnit);
                else super.updateUnit();
            }finally{ MapAlliances.exit(scope); }
        }
    }
}
