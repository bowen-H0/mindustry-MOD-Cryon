package cryon.Type;

import mindustry.world.blocks.production.Pump;

/** Electric pump with a fixed rated throughput, including on rich deep-water floors. */
public class PoweredPump extends Pump{
    public PoweredPump(String name){
        super(name);
        buildType = PoweredPumpBuild::new;
    }

    public class PoweredPumpBuild extends PumpBuild{
        @Override
        public void onProximityUpdate(){
            super.onProximityUpdate();
            amount = Math.min(amount, size * size);
        }
    }
}
