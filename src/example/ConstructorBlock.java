package example;

import arc.graphics.Color;
import arc.struct.*;
import arc.util.Log;
import mindustry.*;
import mindustry.gen.*;
import mindustry.graphics.Pal;
import mindustry.ui.*;
import mindustry.world.*;

public class ConstructorBlock extends Block {

    public boolean outputsConstructor = false;
    public boolean consumesConstructor = false;

    public ConstructorBlock(String name) {
        super(name);
        update = true;
        solid = true;
    }

    @Override
    public void setBars() {
        super.setBars();
        addBar("构造", (ConstructorBuild b) ->
                new Bar(
                        () -> {
                            ConstructorGraph g = b.constructorGraph;
                            if (g == null) return "构造: --";
                            float balance = g.getBalance();
                            String sign = balance >= 0 ? "+" : "";
                            return "构造: " + sign + (int) (balance * 60);
                        },
                        () -> Pal.accent,
                        () -> b.constructorGraph != null ? b.constructorGraph.getSatisfaction() : 0f
                )
        );
    }

    // ---------------------------------------------------------------

    public class ConstructorBuild extends Building {

        public ConstructorGraph constructorGraph;
        public IntSeq constructorLinkPositions = new IntSeq();
        public float constructorSatisfaction = 1f;

        public float constructorProduced() { return 0f; }
        public float constructorConsumed() { return 0f; }

        public Seq<Building> constructorLinks() {
            Seq<Building> out = new Seq<>();
            for (int i = 0; i < constructorLinkPositions.size; i++) {
                var b = Vars.world.build(constructorLinkPositions.get(i));
                if (b != null) out.add(b);
            }
            return out;
        }


        public void applyConstructorSatisfaction(float sat) {
            this.constructorSatisfaction = sat;
        }

        public void ensureGraph() {
            if (constructorGraph == null) {
                constructorGraph = new ConstructorGraph();
                constructorGraph.addBuilding(this);
            }
        }

        @Override
        public void updateTile() {
            ensureGraph();
            if (enabled && efficiency > 0f) {
                consume();
            }

        }

        @Override
        public void onRemoved() {
            super.onRemoved();

            if (constructorGraph != null) {
                constructorGraph.removeBuilding(this);
                constructorGraph = null;
            }
        }
    }
}