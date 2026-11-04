package cryon.Type;

import arc.graphics.g2d.Draw;
import cryon.Content.SubmarineContent;
import cryon.Features.MapAlliances;
import mindustry.entities.Units;
import mindustry.game.Team;
import mindustry.gen.Unit;
import mindustry.type.UnitType;

public class SubmarineUnitType extends UnitType{
    public float detectionRange = 112f;

    public SubmarineUnitType(String name){
        super(name);
        constructor = SubmarineUnit::new;
    }

    /** A team discovers a diving hull only when one of its units or buildings is close enough. */
    public boolean detected(Unit unit, Team observer){
        if(MapAlliances.allied(observer, unit.team) || !SubmarineContent.diving(unit)) return true;
        float x = unit.x - detectionRange, y = unit.y - detectionRange, size = detectionRange * 2f;
        if(observer == null){
            return Units.any(x, y, size, size, other -> other != unit && !other.dead && other.isAdded() && other.within(unit, detectionRange));
        }
        var data = observer.data();
        // Search only the observer's indexes and stop at the first valid detector.
        // The old all-buildings search visited nearby buildings from every faction.
        if(data.tree().intersect(x, y, size, size,
                other -> other != unit && !other.dead && other.isAdded() && other.within(unit, detectionRange))) return true;
        return data.buildingTree != null && data.buildingTree.intersect(x, y, size, size,
                build -> build.team == observer && build.isValid() && build.within(unit, detectionRange));
    }

    @Override public boolean targetable(Unit unit, Team observer){
        return super.targetable(unit, observer) && detected(unit, observer);
    }

    @Override public void drawBody(Unit unit){
        applyColor(unit);
        if(SubmarineContent.diving(unit)) Draw.alpha(0.58f);
        Draw.rect(region, unit.x, unit.y, unit.rotation - 90f);
        Draw.reset();
    }
}
