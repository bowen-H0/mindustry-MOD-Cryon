package cryon.Type;

import mindustry.type.UnitType;
import mindustry.gen.Unit;
import mindustry.entities.part.DrawPart;

/** Hull light masks belong between the body and its cell/weapon overlays. */
public class PorphyraHoverUnitType extends UnitType{
    public PorphyraHoverUnitType(String name){ super(name); }

    @Override public void drawBody(Unit unit){
        super.drawBody(unit);
        for(DrawPart part : parts){
            if(part instanceof PorphyraLightPart){
                DrawPart.params.set(0f, 0f, 0f, 0f, 0f, 0f, unit.x, unit.y, unit.rotation);
                ((PorphyraLightPart)part).drawHull(DrawPart.params);
            }
        }
    }
}
