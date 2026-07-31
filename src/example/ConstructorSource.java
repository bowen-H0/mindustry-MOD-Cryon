package example;

import arc.graphics.*;
import mindustry.gen.*;
import mindustry.graphics.Pal;
import mindustry.ui.*;
import mindustry.world.meta.*;

public class ConstructorSource extends ConstructorBlock {

    public float production = 10f;
    public Color activeColor = Pal.accent;

    public ConstructorSource(String name) {
        super(name);
        outputsConstructor  = true;
        consumesConstructor = false;
        update      = true;
        solid       = true;
    }

    @Override
    public void setBars() {
        super.setBars();
        addBar("constructor-out", (ConstructorBuild b) ->
                new Bar(
                        () -> "Output: " + production*60 + "unit",
                        () -> activeColor,
                        () -> b.enabled ? 1f : 0f
                )
        );
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.basePowerGeneration, production * 60f, StatUnit.powerSecond);
    }

    // ==================================================================

    public class ConstructorSourceBuild extends ConstructorBuild {

        @Override
        public float constructorProduced() {
            return enabled ? production : 0f;
        }


        @Override
        public BlockStatus status() {
            if (!enabled) return BlockStatus.noOutput;
            if (constructorGraph == null || constructorGraph.buildings.size <= 1) return BlockStatus.noOutput;
            return BlockStatus.active;
        }
    }
}