package cryon.Type;

import arc.Core;
import arc.graphics.Pixmap;
import arc.graphics.g2d.*;
import arc.util.Time;
import cryon.Features.EonBarrierRenderer;
import cryon.Features.EonHullField;
import mindustry.entities.abilities.Ability;
import mindustry.gen.Unit;
import mindustry.graphics.MultiPacker;

/** Hull-shaped, filled shader barrier, expanded eight world units with a soft luminous fringe. */
public class HighDimensionBarrierAbility extends Ability{
    // Ability.copy() is shallow: all instances share the packed, immutable hull mask.
    private static class Hull{
        String name;
        TextureRegion region;
    }
    private final Hull hull = new Hull();

    /** Source pixels are only accessed while the icon packer is alive. */
    public void prepare(MultiPacker packer, String name, PixmapRegion source){
        if(hull.name != null) return;
        Pixmap field = EonHullField.create(source);
        try{
            packer.add(MultiPacker.PageType.main, name + "-barrier-field", field);
            hull.name = name + "-barrier-field";
        }finally{
            field.dispose();
        }
    }

    @Override public String localized(){ return Core.bundle.get("ability.highdimensionbarrier"); }

    @Override public void update(Unit unit){
        data = Math.max(unit.hitTime, Math.max(0f, data - Time.delta / 40f));
    }

    @Override public void draw(Unit unit){
        float intensity = Math.max(unit.hitTime, data);
        if(intensity <= 0.001f || hull.name == null) return;
        if(hull.region == null) hull.region = Core.atlas.find(hull.name);
        float impact = unit instanceof EonEntity ? ((EonEntity)unit).barrierAngle - unit.rotation + 90f : 0f;
        EonBarrierRenderer.draw(hull.region, unit.x, unit.y, unit.rotation - 90f,
            unit.type.region.scl(), impact, intensity);
    }
}
