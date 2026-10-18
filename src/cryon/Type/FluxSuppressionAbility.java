package cryon.Type;

import arc.graphics.g2d.*;
import arc.math.*;
import mindustry.entities.abilities.EnergyFieldAbility;
import mindustry.entities.Units;
import mindustry.entities.abilities.Ability;
import mindustry.game.Team;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import mindustry.graphics.Layer;

/** Evaluated by shields directly, so update order, death and leaving range are immediate. */
public class FluxSuppressionAbility extends Ability{
    public float radius;
    private static float searchRadius = 160f;
    public FluxSuppressionAbility(float radius){
        this.radius = radius;
        searchRadius = Math.max(searchRadius, radius);
    }
    @Override public void init(UnitType type){
        super.init(type);
        type.clipSize = Math.max(type.clipSize, radius * 2f + 16f);
    }
    @Override public void addStats(arc.scene.ui.layout.Table table){
        table.add(arc.Core.bundle.format("ability.fluxsuppression.description", radius / 8f)).wrap().width(300).row();
    }
    @Override public String localized(){ return arc.Core.bundle.get("ability.fluxsuppression"); }

    public static boolean suppressed(Team team, float x, float y){
        boolean[] result = {false};
        Units.nearbyEnemies(team, x, y, searchRadius, source -> {
            if(result[0] || source.dead || !source.isAdded()) return;
            for(Ability ability : source.abilities){
                if(ability instanceof FluxSuppressionAbility && source.within(x, y, ((FluxSuppressionAbility)ability).radius)){
                    result[0] = true;
                    break;
                }
            }
        });
        return result[0];
    }
    // Use the native energy-field renderer only; no extra damage or healing ability.
    private static final FieldVisual visual = new FieldVisual();
    private static class FieldVisual extends EnergyFieldAbility{
        FieldVisual(){
            super(0f, 60f, 1f);
            color = cryon.Content.BioCrawlerUnits.orange;
            effectRadius = 5f;
            sectors = 4;
            sectorRad = 0.1f;
            rotateSpeed = 0.7f;
            curStroke = 0.65f;
        }
    }
    @Override public void draw(Unit unit){
        float z = Draw.z();
        visual.range = radius;
        visual.effectRadius = this instanceof GlobalSuppressionAbility ? 7f : 5f;
        visual.draw(unit);
        Draw.reset();
        Draw.z(z);
    }
}
