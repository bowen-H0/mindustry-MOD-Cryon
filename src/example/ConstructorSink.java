package example;

import arc.graphics.*;
import mindustry.gen.*;
import mindustry.graphics.Pal;
import mindustry.ui.*;
import mindustry.world.meta.*;
//It should be called ConstructorVoid, but let's forget about it.
public class ConstructorSink extends ConstructorBlock {

    public float consumption = 10f;

    public ConstructorSink(String name) {
        super(name);
        outputsConstructor  = false;
        consumesConstructor = true;
        update      = true;
        solid       = true;
    }

    @Override
    public void setBars() {
        super.setBars();
        addBar("constructor-in", (ConstructorBuild b) ->
                new Bar(
                        () -> "Input: " + consumption + "/t",
                        () -> Pal.accent,
                        () -> b.enabled ? 1f : 0f
                )
        );
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.powerUse, consumption * 60f, StatUnit.powerSecond);
    }

    // ==================================================================

    public class ConstructorSinkBuild extends ConstructorBuild {

        @Override
        public float constructorConsumed() {
            return enabled ? consumption : 0f;
        }


        @Override
        public BlockStatus status() {
            if (!enabled) return BlockStatus.noInput;
            if (constructorGraph == null || constructorGraph.buildings.size <= 1) return BlockStatus.noInput;
            if (constructorGraph.getSatisfaction() < 1f) return BlockStatus.noInput;
            return BlockStatus.active;
        }
    }
}