package cryon.Type;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.util.io.*;
import mindustry.Vars;
import mindustry.entities.Units;
import mindustry.gen.Building;
import mindustry.graphics.*;
import mindustry.world.Block;
import mindustry.world.meta.*;

/** One reciprocal link per post. The beam only interacts with enemy units. */
public class PlasmaFence extends Block{
    public float linkRange = 160f, beamWidth = 6f, damagePerSecond = 12000f;
    public PlasmaFence(String name){
        super(name);
        update = solid = hasPower = configurable = saveConfig = true;
        size = 2;
        health = 1400;
        consumePower(500f);
        buildType = PlasmaFenceBuild::new;
        config(Point2.class, (PlasmaFenceBuild b, Point2 p) -> b.connect(Vars.world.build(b.tileX() + p.x, b.tileY() + p.y)));
        configClear((PlasmaFenceBuild b) -> b.disconnect());
    }
    @Override public void load(){
        super.load();
        region = Core.atlas.find(name, "surge-wall-large");
    }
    @Override public void setStats(){
        super.setStats();
        stats.add(Stat.range, linkRange / Vars.tilesize, StatUnit.blocks);
        stats.add(Stat.damage, damagePerSecond, StatUnit.perSecond);
    }
    public class PlasmaFenceBuild extends Building{
        public int link = -1;
        public PlasmaFenceBuild partner(){
            Building b = Vars.world.build(link);
            return b instanceof PlasmaFenceBuild && b.block == block && b.team == team && within(b, linkRange)
                    ? (PlasmaFenceBuild)b : null;
        }
        public void disconnect(){
            PlasmaFenceBuild other = partner();
            if(other != null && other.link == pos()) other.link = -1;
            link = -1;
        }
        public void connect(Building b){
            if(b == this){ disconnect(); return; }
            if(!(b instanceof PlasmaFenceBuild) || b.block != block || b.team != team || !within(b, linkRange)) return;
            PlasmaFenceBuild other = (PlasmaFenceBuild)b;
            disconnect();
            other.disconnect();
            link = other.pos();
            other.link = pos();
        }
        @Override public boolean onConfigureBuildTapped(Building other){
            if(other == this || other instanceof PlasmaFenceBuild && other.block == block && other.team == team && within(other, linkRange)){
                configure(new Point2(other.tileX() - tileX(), other.tileY() - tileY()));
                return false;
            }
            return true;
        }
        @Override public Object config(){
            PlasmaFenceBuild b = partner();
            return b == null ? new Point2() : new Point2(b.tileX() - tileX(), b.tileY() - tileY());
        }
        public boolean active(PlasmaFenceBuild other){
            return other != null && other.link == pos() && enabled && other.enabled && efficiency >= 0.99f && other.efficiency >= 0.99f;
        }
        @Override public void updateTile(){
            PlasmaFenceBuild other = partner();
            if(!active(other) || pos() >= other.pos() || Vars.net.client()) return;
            float dx = other.x - x, dy = other.y - y;
            float length2 = dx * dx + dy * dy;
            Units.nearbyEnemies(team, (x + other.x) / 2f, (y + other.y) / 2f, linkRange / 2f + 64f, unit -> {
                if(cryon.Features.MapAlliances.allied(team, unit.team)) return;
                float t = Mathf.clamp(((unit.x - x) * dx + (unit.y - y) * dy) / length2);
                if(unit.within(x + t * dx, y + t * dy, beamWidth + unit.hitSize / 2f))
                    unit.damage(damagePerSecond * delta() / 60f);
            });
        }
        @Override public void draw(){
            super.draw();
            Draw.z(Layer.effect);
            Draw.color(Pal.lancerLaser);
            Fill.circle(x, y, 3f);
            PlasmaFenceBuild other = partner();
            if(active(other) && pos() < other.pos()){
                Lines.stroke(5f + Mathf.absin(3f, 1f));
                Lines.line(x, y, other.x, other.y);
                Draw.color(Color.white);
                Lines.stroke(1.5f);
                Lines.line(x, y, other.x, other.y);
                Drawf.light(x, y, other.x, other.y, 20f, Pal.lancerLaser, 0.7f);
            }
            Draw.reset();
        }
        @Override public void drawConfigure(){
            Drawf.dashCircle(x, y, linkRange, Pal.accent);
            PlasmaFenceBuild b = partner();
            if(b != null) Drawf.square(b.x, b.y, b.block.size * Vars.tilesize / 2f + 2f, Pal.accent);
        }
        @Override public void onRemoved(){ disconnect(); super.onRemoved(); }
        @Override public void write(Writes write){ super.write(write); write.i(link); }
        @Override public void read(Reads read, byte revision){ super.read(read, revision); link = read.i(); }
    }
}
