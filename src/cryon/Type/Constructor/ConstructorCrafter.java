package cryon.Type.Constructor;

import arc.Core;
import arc.math.Mathf;
import arc.util.Time;
import cryon.Constructor.ConstructorConsumer;
import mindustry.graphics.Pal;
import mindustry.ui.Bar;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.meta.*;

/** A native crafter whose throughput is limited by a live constructor-network supply. */
public class ConstructorCrafter extends GenericCrafter{
    public float constructorConsumption = 40f;
    public ConstructorCrafter(String name){
        super(name);
        buildType = ConstructorCrafterBuild::new;
    }
    @Override public void setStats(){
        super.setStats();
        stats.add(Stat.input, StatValues.string(Core.bundle.format("stat.constructor.input", (int)(constructorConsumption * 60f))));
    }
    @Override public void setBars(){
        super.setBars();
        addBar("constructor", (ConstructorCrafterBuild b) -> new Bar(
                () -> Core.bundle.get("cryon.superphase.constructor"), () -> Pal.accent, b::supply));
    }
    public class ConstructorCrafterBuild extends GenericCrafterBuild implements ConstructorConsumer{
        private float satisfaction, lastSupply = -Float.MAX_VALUE;
        public float supply(){
            return Time.time - lastSupply <= Math.max(2f, Time.delta * 2f) ? satisfaction : 0f;
        }
        @Override public float constructorUse(){ return constructorConsumption * potentialEfficiency; }
        @Override public void constructorStatus(float status){
            satisfaction = Mathf.clamp(status);
            lastSupply = Time.time;
        }
        @Override public boolean constructorValid(){
            return isValid() && enabled && super.shouldConsume() && potentialEfficiency > 0.0001f;
        }
        @Override public boolean shouldConsume(){ return supply() > 0.0001f && super.shouldConsume(); }
        @Override public void updateEfficiencyMultiplier(){
            super.updateEfficiencyMultiplier();
            efficiency *= supply();
            optionalEfficiency *= supply();
        }
    }
}
