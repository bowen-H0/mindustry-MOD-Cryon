package cryon.Type;

import arc.math.Mathf;
import mindustry.content.Liquids;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.consumers.ConsumeLiquid;
import mindustry.world.meta.*;

/** Optional cooling: dry buildings retain their original speed and power requirements. */
public class WaterCooling extends ConsumeLiquid {
    public final float speedMultiplier;

    public WaterCooling(float amount, float speedMultiplier){
        super(Liquids.water, amount);
        this.speedMultiplier = speedMultiplier;
        optional = booster = true;
    }

    @Override
    public void apply(Block block){
        super.apply(block);
        block.liquidCapacity = Math.max(block.liquidCapacity, 30f);
    }

    private float supplied(Building build){
        return Mathf.clamp(build.liquids.get(liquid) / Math.max(amount * build.delta(), 0.000001f));
    }

    public float speed(Building build){
        return Mathf.lerp(1f, speedMultiplier, supplied(build));
    }

    @Override
    public float efficiency(Building build){
        return supplied(build);
    }

    @Override
    public void update(Building build){
        // Cooling demand follows unboosted production, avoiding feedback through edelta().
        float operating = Mathf.clamp(build.efficiency / speed(build));
        build.liquids.remove(liquid, Math.min(build.liquids.get(liquid), amount * build.delta() * operating));
    }

    @Override
    public void display(Stats stats){
        super.display(stats);
        stats.add(Stat.speedIncrease, (speedMultiplier - 1f) * 100f, StatUnit.percent);
    }
}
