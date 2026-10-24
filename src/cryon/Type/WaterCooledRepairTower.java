package cryon.Type;

import mindustry.world.blocks.units.RepairTower;

public class WaterCooledRepairTower extends RepairTower {
    private final WaterCooling cooling;

    public WaterCooledRepairTower(String name){
        super(name);
        cooling = consume(new WaterCooling(0.1f, 1.5f));
        buildType = CooledBuild::new;
    }

    public class CooledBuild extends RepairTowerBuild {
        @Override
        public float efficiencyScale(){ return super.efficiencyScale() * cooling.speed(this); }
    }
}
