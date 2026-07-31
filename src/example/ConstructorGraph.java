package example;

import arc.struct.*;
import arc.util.Log;
import mindustry.gen.*;
//Based on power system modifications.
public class ConstructorGraph {

    private static final Queue<Building> queue = new Queue<>();

    public final Seq<Building> buildings = new Seq<>(false);

    private float lastProduced;
    private float lastConsumed;
    private float satisfaction = 1f;

    private void logTopology(String action) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(action).append("]Current graph topology：\n");
        for (var b : buildings) {
            if (b instanceof ConstructorBlock.ConstructorBuild cb) {
                sb.append("  ").append(cb.block.name).append(" @ ").append(cb.pos());
                sb.append("  -> [");
                var links = cb.constructorLinks();
                for (int i = 0; i < links.size; i++) {
                    if (i > 0) sb.append(", ");
                    var l = links.get(i);
                    sb.append(l.block.name).append("@").append(l.pos());
                }
                sb.append("]\n");
            }
        }

        Log.info(sb.toString());
    }

    // ---------------------------------------------------------------

    public void addBuilding(Building b) {
        if (b instanceof ConstructorBlock.ConstructorBuild cb && !buildings.contains(b)) {
            buildings.add(b);
            cb.constructorGraph = this;
        }
    }

    public void removeBuilding(Building b) {
        buildings.remove(b);
        if (b instanceof ConstructorBlock.ConstructorBuild cb && cb.constructorGraph == this) {
            cb.constructorGraph = null;
        }
        //I hate this system!
    }

    public void addGraph(ConstructorGraph other) {
        if (other == this) return;
        for (var b : other.buildings) {
            if (b instanceof ConstructorBlock.ConstructorBuild cb) {
                cb.constructorGraph = this;
            }
            if (!buildings.contains(b)) buildings.add(b);
        }
        other.buildings.clear();
    }

    public void reflow(Building root) {
        queue.clear();
        queue.addLast(root);
        addBuilding(root);

        while (queue.size > 0) {
            Building cur = queue.removeFirst();
            if (cur instanceof ConstructorBlock.ConstructorBuild cb) {
                for (var next : cb.constructorLinks()) {
                    if (next instanceof ConstructorBlock.ConstructorBuild ncb
                            && ncb.constructorGraph == null) {
                        addBuilding(ncb);
                        queue.addLast(ncb);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------

    public void update() {
        float produced = 0f, consumed = 0f;

        for (var b : buildings) {
            if (b instanceof ConstructorBlock.ConstructorBuild cb) {
                produced += cb.constructorProduced();
                consumed += cb.constructorConsumed();
            }
        }
        for (var b : buildings) {
            if (b instanceof ConstructorBlock.ConstructorBuild cb) {
                for (var link : cb.constructorLinks()) {
                    if (link instanceof ConstructorConsumer cc
                            && !(link instanceof ConstructorBlock.ConstructorBuild)
                            && cc.constructorValid()) {
                        consumed += cc.constructorUse();
                    }
                }
            }
        }

        lastProduced = produced;
        lastConsumed = consumed;
        satisfaction = consumed <= 0.0001f ? 1f : Math.min(1f, produced / consumed);

        for (var b : buildings) {
            if (b instanceof ConstructorBlock.ConstructorBuild cb) {
                cb.applyConstructorSatisfaction(satisfaction);
            }
        }

        for (var b : buildings) {
            if (b instanceof ConstructorBlock.ConstructorBuild cb) {
                for (var link : cb.constructorLinks()) {
                    if (link instanceof ConstructorConsumer cc
                            && !(link instanceof ConstructorBlock.ConstructorBuild)) {
                        cc.constructorStatus(satisfaction);
                    }
                }
            }
        }
    }

    public float getSatisfaction()  { return satisfaction; }
    public float getLastProduced()  { return lastProduced; }
    public float getLastConsumed()  { return lastConsumed; }
    public float getBalance()       { return lastProduced - lastConsumed; }
}