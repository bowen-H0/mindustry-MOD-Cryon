package cryon.Type;

import arc.graphics.g2d.Draw;
import mindustry.gen.Unit;
import mindustry.graphics.Pal;

/** Keeps the projected saucer shadow at ground level beneath the elevated hull. */
public class OrbitalHoverUnitType extends OrbitalUnitType{
    public OrbitalHoverUnitType(String name){ super(name); }
    @Override public void drawBody(Unit unit){
        applyColor(unit);
        Draw.rect(region, unit.x, unit.y);
        Draw.reset();
    }
    @Override public void drawOutline(Unit unit){
        Draw.reset();
        if(arc.Core.atlas.isFound(outlineRegion)){
            applyColor(unit);
            applyOutlineColor(unit);
            Draw.rect(outlineRegion, unit.x, unit.y);
            Draw.reset();
        }
    }
    @Override public void drawShadow(Unit unit){
        if(weapons.isEmpty() || !(weapons.first().bullet instanceof DownwardBurnBeamBulletType beam)){
            super.drawShadow(unit);
            return;
        }
        float groundY = unit.y - beam.hoverHeight;
        if(!mindustry.Vars.world.floorWorld(unit.x, groundY).canShadow) return;
        Draw.color(Pal.shadow, Pal.shadow.a * 0.7f);
        Draw.rect(shadowRegion, unit.x, groundY);
        Draw.color();
    }
}
