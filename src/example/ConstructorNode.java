package example;

import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.input.*;
import mindustry.ui.Bar;
import mindustry.world.*;
import mindustry.world.meta.*;

import java.util.*;

import static mindustry.Vars.*;
// ConstructorNode
// Based on laser node
public class ConstructorNode extends ConstructorBlock {

    private static final int maxRange = 30;

    public int range = 5;

    public TextureRegion laser;
    public TextureRegion laserEnd;

    public float pulseScl = 7f, pulseMag = 0.05f;
    public float laserWidth = 0.4f;

    public ConstructorNode(String name){
        super(name);
        outputsConstructor = false;
        consumesConstructor = false;
        drawDisabled = false;
        envEnabled |= Env.space;
        allowDiagonal = false;
        underBullets = true;
        priority = TargetPriority.transport;
    }

    @Override
    public void load(){
        super.load();
        laser    = arc.Core.atlas.find("cryon-constructor-beam");
        laserEnd = arc.Core.atlas.find("cryon-constructor-beam-end");
    }


    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.powerRange, range, StatUnit.blocks);
    }

    @Override
    public void init(){
        super.init();
        updateClipRadius((range + 1) * tilesize);
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        for(int i = 0; i < 4; i++){
            int maxLen = range + size / 2;
            Building dest = null;
            var dir = Geometry.d4[i];
            int dx = dir.x, dy = dir.y;
            int offset = size / 2;

            for(int j = 1 + offset; j <= range + offset; j++){
                var other = world.build(x + j * dx, y + j * dy);
                if(other != null && other.isInsulated()) break;
                if(other != null && other.team == Vars.player.team()
                        && (other instanceof ConstructorBuild || other instanceof ConstructorConsumer)){
                    maxLen = j;
                    dest = other;
                    break;
                }
            }

            Drawf.dashLine(
                    Pal.placing,
                    x * tilesize + dx * (tilesize * size / 2f + 2),
                    y * tilesize + dy * (tilesize * size / 2f + 2),
                    x * tilesize + dx * maxLen * tilesize,
                    y * tilesize + dy * maxLen * tilesize
            );

            if(dest != null){
                Drawf.square(dest.x, dest.y, dest.block.size * tilesize / 2f + 2.5f, 0f);
            }
        }
    }

    @Override
    public void changePlacementPath(Seq<Point2> points, int rotation, boolean diagonal){
        if(!diagonal){
            Placement.calculateNodes(
                    points, this, rotation,
                    (point, other) -> Math.max(Math.abs(point.x - other.x), Math.abs(point.y - other.y)) <= range + size - 1
            );
        }
    }

    public static void getNodeLinks(Tile tile, Block block, Team team, Cons<Building> others){
        var tree = team.data().buildingTree;
        if(tree == null) return;

        float cx = tile.worldx() + block.offset,
                cy = tile.worldy() + block.offset,
                s  = block.size * tilesize / 2f,
                r  = maxRange * tilesize;

        Seq<Building> tempBuilds = new Seq<>();
        arc.math.geom.Rect rect = new arc.math.geom.Rect();

        for(int i = 0; i < 4; i++){
            switch(i){
                case 0 -> rect.set(cx - s, cy - s, r,      s * 2f);
                case 1 -> rect.set(cx - s, cy - s, s * 2f, r);
                case 2 -> rect.set(cx + s, cy - s, -r,     s * 2f).normalize();
                case 3 -> rect.set(cx - s, cy + s, s * 2f, -r).normalize();
            }

            tempBuilds.clear();
            tree.intersect(rect, tempBuilds);
            int fi = i;
            Building closest = tempBuilds.min(
                    b -> b instanceof ConstructorNodeBuild node
                            && node.couldConnect((fi + 2) % 4, block, tile.x, tile.y),
                    b -> b.dst2(cx, cy)
            );
            tempBuilds.clear();
            if(closest != null) others.get(closest);
        }
    }


    public class ConstructorNodeBuild extends ConstructorBuild {

        public Building[] links = new Building[4];
        public Tile[]     dests = new Tile[4];
        public int lastChange = -1;
        private boolean firstUpdate = true;

        public boolean couldConnect(int direction, Block target, int targetX, int targetY){
            int off  = -(target.size - 1) / 2;
            int minX = targetX + off, minY = targetY + off;
            int maxX = minX + target.size - 1, maxY = minY + target.size - 1;
            var dir  = Geometry.d4[direction];
            int ro   = size / 2;

            for(int j = 1 + ro; j <= range + ro; j++){
                var other = world.tile(tile.x + j * dir.x, tile.y + j * dir.y);
                if(other == null) return false;
                if(other.build != null && other.build.isInsulated()) return false;
                if(other.build != null && other.build.team() == team
                        && (other.build instanceof ConstructorBuild || other.build instanceof ConstructorConsumer)
                        && other.build != this){
                    return false;
                }
                if(other.x >= minX && other.y >= minY && other.x <= maxX && other.y <= maxY){
                    return true;
                }
            }
            return false;
        }

        @Override
        public void updateTile(){
            ensureGraph();

            if(firstUpdate || lastChange != world.tileChanges){
                firstUpdate = false;
                lastChange = world.tileChanges;
                updateDirections();
            }

            if(constructorGraph != null){
                boolean master = true;
                for(var b : constructorGraph.buildings){
                    if(b instanceof ConstructorNodeBuild other && other.id < this.id){
                        master = false;
                        break;
                    }
                }
                if(master) constructorGraph.update();
            }
        }
        @Override
        public void onRemoved() {
            for (int i = 0; i < 4; i++) {
                if (links[i] == null || !links[i].isAdded()) continue;

                if (links[i] instanceof ConstructorBuild neighbour) {
                    neighbour.constructorLinkPositions.removeValue(pos());
                    constructorLinkPositions.removeValue(neighbour.pos());

                    ConstructorGraph oldGraph = constructorGraph;
                    if (oldGraph != null) {
                        for (var b : oldGraph.buildings) {
                            if (b instanceof ConstructorBuild cb) {
                                cb.constructorGraph = null;
                            }
                        }
                        oldGraph.buildings.clear();
                    }

                    if (neighbour.constructorGraph == null) {
                        ConstructorGraph newGraph = new ConstructorGraph();
                        newGraph.reflow(neighbour);
                    }
                } else if (links[i] instanceof ConstructorConsumer cc) {
                    cc.constructorStatus(0f);
                    constructorLinkPositions.removeValue(links[i].pos());
                }
            }

            if (constructorGraph != null) {
                constructorGraph.removeBuilding(this);
            }
            constructorGraph = null;

            super.onRemoved();
        }

        @Override
        public void draw(){
            super.draw();

            if(Mathf.zero(Renderer.laserOpacity) || team == Team.derelict) return;

            Draw.z(Layer.power);

            float balance = constructorGraph != null ? constructorGraph.getBalance() : 0f;

            Draw.color();

            if(balance < 0){
                Draw.alpha(Renderer.laserOpacity * 0.3f);
            }else{
                Draw.alpha(Renderer.laserOpacity);
            }

            float w = laserWidth + Mathf.absin(pulseScl, pulseMag);

            for(int i = 0; i < 4; i++){
                if(dests[i] != null && links[i] != null && links[i].wasVisible && (
                        !(links[i].block instanceof ConstructorNode node) ||
                                (links[i].tileX() != tileX() && links[i].tileY() != tileY()) ||
                                (links[i].id > id && range >= node.range) ||
                                range > node.range
                )){
                    int dst = Math.max(
                            Math.abs(dests[i].x - tile.x),
                            Math.abs(dests[i].y - tile.y)
                    );
                    if(dst > 1 + size / 2){
                        var point = Geometry.d4[i];
                        float poff = tilesize / 2f;

                        Drawf.laser(
                                laser, laserEnd,
                                x + poff * size * point.x,
                                y + poff * size * point.y,
                                dests[i].worldx() - poff * point.x,
                                dests[i].worldy() - poff * point.y,
                                w
                        );
                    }
                }
            }

            Draw.reset();
        }

        @Override
        public void pickedUp(){
            Arrays.fill(links, null);
            Arrays.fill(dests, null);
        }

        public void updateDirections(){
            for(int i = 0; i < 4; i++){
                var prev   = links[i];
                var dir    = Geometry.d4[i];
                links[i]   = null;
                dests[i]   = null;
                int offset = size / 2;

                for(int j = 1 + offset; j <= range + offset; j++){
                    var other = world.build(tile.x + j * dir.x, tile.y + j * dir.y);

                    if(other != null && other.isInsulated()) break;

                    if(other != null && other.team == team && other != this
                            && (other instanceof ConstructorBuild || other instanceof ConstructorConsumer)){
                        links[i] = other;
                        dests[i] = world.tile(tile.x + j * dir.x, tile.y + j * dir.y);
                        break;
                    }
                }

                var next = links[i];
                if(next != prev){
                    if(prev != null && prev.isAdded()){
                        constructorLinkPositions.removeValue(prev.pos());
                        if(prev instanceof ConstructorBuild prevCb){
                            prevCb.constructorLinkPositions.removeValue(pos());

                            ConstructorGraph oldGraph = constructorGraph;
                            if(oldGraph != null){
                                for(var b : oldGraph.buildings){
                                    if(b instanceof ConstructorBlock.ConstructorBuild cb){
                                        cb.constructorGraph = null;
                                    }
                                }
                                oldGraph.buildings.clear();
                            }

                            ConstructorGraph newGraph = new ConstructorGraph();
                            newGraph.reflow(this);

                            if(prevCb.constructorGraph == null){
                                ConstructorGraph prevGraph = new ConstructorGraph();
                                prevGraph.reflow(prevCb);
                            }
                        } else if(prev instanceof ConstructorConsumer cc){
                            cc.constructorStatus(0f);
                        }
                    }

                    if(next != null){
                        constructorLinkPositions.addUnique(next.pos());

                        if(next instanceof ConstructorBuild nextCb){
                            nextCb.constructorLinkPositions.addUnique(pos());

                            ensureGraph();
                            nextCb.ensureGraph();
                            constructorGraph.addGraph(nextCb.constructorGraph);
                        } else {
                            ensureGraph();
                        }
                    }
                }

            }
        }
    }
}