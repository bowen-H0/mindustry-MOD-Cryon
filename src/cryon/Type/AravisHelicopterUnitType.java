package cryon.Type;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.util.Time;
import mindustry.gen.Unit;
import mindustry.type.UnitType;

/** A visible rotating main rotor, independent of the helicopter's weapon direction. */
public class AravisHelicopterUnitType extends UnitType{
    private TextureRegion rotor;
    public AravisHelicopterUnitType(String name){super(name);}
    @Override public void load(){
        super.load();
        rotor = Core.atlas.find(name + "-rotor");
    }
    @Override public void drawWeapons(Unit unit){
        super.drawWeapons(unit);
        applyColor(unit);
        Draw.rect(rotor, unit.x, unit.y, Time.time * 19f + unit.id % 360);
        Draw.reset();
    }
}
