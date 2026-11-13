package cryon.Type;

import arc.Core;
import cryon.Features.OrbitalTargeting;
import mindustry.game.Team;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import mindustry.world.meta.Stat;

public class OrbitalUnitType extends UnitType{
    public static final float orbitalLayer = Layer.shields + 24f;
    public static final Stat orbitalLayerStat = new Stat("cryon-orbital-layer");
    public OrbitalUnitType(String name){
        super(name);
        constructor = OrbitalUnit::new;
        flying = true;
        lowAltitude = false;
        flyingLayer = orbitalLayer;
        shadowElevationScl = 12f;
        physics = false; // OrbitalPhysics supplies collisions in a separate native physics world.
        canBoost = false;
    }
    @Override public boolean targetable(Unit unit, Team observer){
        return OrbitalTargeting.enabled() && super.targetable(unit, observer);
    }
    @Override public void setStats(){
        super.setStats();
        stats.add(orbitalLayerStat, Core.bundle.get("cryon.orbital-layer"));
    }
}
