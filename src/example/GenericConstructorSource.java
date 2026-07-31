package example;

import arc.graphics.Color;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.graphics.Pal;
import mindustry.ui.Bar;
import mindustry.world.meta.*;

public class GenericConstructorSource extends ConstructorBlock {
    public float production = 10f;
    public float craftTime = 60f;
    public Color activeColor = Pal.accent;

    public GenericConstructorSource(String name) {
        super(name);
        outputsConstructor = true;
        consumesConstructor = false;
        update = true;
        solid = true;

        hasItems = true;
        hasLiquids = true;
        hasPower = true;
        consumesPower = true;
        outputsPower = false;
    }

    @Override
    public void setBars() {
        super.setBars();
        addBar("constructor-out", (ConstructorBuild b) ->
                new Bar(
                        () -> "Output: " + (int)(production * 60f / craftTime * b.efficiency) + "/s",
                        () -> activeColor,
                        () -> b.enabled ? b.efficiency : 0f
                )
        );
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(Stat.output, production * 60f / craftTime, StatUnit.powerSecond);
    }

    // ==================================================================

    public class GenericConstructorSourceBuild extends ConstructorBuild {
        public float progress;
        public float warmup;

        @Override
        public void updateTile() {
            updateConsumption();

            ensureGraph();

            warmup = Mathf.lerpDelta(warmup, enabled ? efficiency : 0f, 0.1f);

            if (enabled && efficiency > 0.001f) {
                progress += warmup * Time.delta / craftTime;
            }

            if (progress >= 1f) {
                consume();
                progress -= 1f;
            }
        }

        @Override
        public float constructorProduced() {
            return enabled ? (production / craftTime) * efficiency : 0f;
        }

        @Override
        public BlockStatus status() {
            if (!enabled) return BlockStatus.noOutput;
            if (efficiency <= 0.001f) return BlockStatus.noInput;
            if (constructorGraph == null || constructorGraph.buildings.size <= 1) return BlockStatus.noOutput;
            return BlockStatus.active;
        }
    }
}