package cryon.Type;

import mindustry.world.blocks.units.UnitFactory;

public class WaterCooledUnitFactory extends UnitFactory {
    private final WaterCooling cooling;

    public WaterCooledUnitFactory(String name){
        super(name);
        cooling = consume(new WaterCooling(0.1f, 1.5f));
        buildType = CooledBuild::new;
    }

    public class CooledBuild extends UnitFactoryBuild {
        @Override
        public float efficiencyScale(){ return super.efficiencyScale() * cooling.speed(this); }
    }
}
