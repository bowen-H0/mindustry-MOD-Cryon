package cryon.Type;

import arc.math.geom.Vec2;
import cryon.Content.OrbitalUnits;
import cryon.Features.OrbitalSupport;
import mindustry.Vars;
import mindustry.entities.units.AIController;
import mindustry.gen.Building;

/** Reserve a beacon request, approach at stand-off range, otherwise guard a local support hub. */
public class SkywatchAI extends AIController{
    private BeaconBulletType.Marker request;
    private final Vec2 position = new Vec2();

    @Override public void updateUnit(){
        if(Vars.net != null && Vars.net.client()) return;
        unit.controlWeapons(false, false);
        target = null;
        if(!OrbitalSupport.pending(request, unit) || request.claimedBy != unit.id) request = OrbitalSupport.claim(unit);
        if(request != null){
            engage(request.x, request.y);
            return;
        }
        Building home = home();
        if(home != null) moveTo(home, 80f, 30f);
        else unit.movePref(position.setZero());
    }

    private void engage(float x, float y){
        position.set(x, y);
        moveTo(position, unit.range() * 0.72f, 30f);
        unit.lookAt(x, y);
        unit.aim(x, y);
        unit.controlWeapons(true, unit.within(x, y, unit.range()));
    }

    private Building home(){
        Building best = nearest(Vars.content.block("cryon-beacon"), null);
        best = nearest(OrbitalUnits.deploymentPad, best);
        return best == null ? unit.closestCore() : best;
    }

    private Building nearest(mindustry.world.Block block, Building best){
        if(block == null) return best;
        float distance = best == null ? Float.MAX_VALUE : unit.dst2(best);
        for(Building building : unit.team.data().getBuildings(block)){
            if(!building.isValid()) continue;
            float dst = unit.dst2(building);
            if(dst < distance){ best = building; distance = dst; }
        }
        return best;
    }
}
