package cryon.Type;

import mindustry.world.blocks.units.Reconstructor;

public class WaterCooledReconstructor extends Reconstructor {
    private final WaterCooling cooling;

    public WaterCooledReconstructor(String name){
        super(name);
        cooling = consume(new WaterCooling(0.15f, 1.5f));
        buildType = CooledBuild::new;
    }

    public class CooledBuild extends ReconstructorBuild {
        @Override
        public float efficiencyScale(){ return super.efficiencyScale() * cooling.speed(this); }
    }
}
