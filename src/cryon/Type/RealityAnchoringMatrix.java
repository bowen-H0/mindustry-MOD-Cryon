package cryon.Type;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.io.*;
import cryon.Content.CryonItems;
import cryon.Features.MapAlliances;
import cryon.Features.RealityAnchorRenderer;
import mindustry.content.Items;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.Bar;
import mindustry.world.Block;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

/** A paid square reality field. It anchors enemy endpoints without obstructing ordinary movement. */
public class RealityAnchoringMatrix extends Block{
    public static final Color fieldColor = Color.valueOf("a1b8ff");
    public float halfExtent = 640f, phaseInterval = 12f;
    private static final Seq<AnchorBuild> anchors = new Seq<>();
    private static boolean installed;

    public RealityAnchoringMatrix(String name){
        super(name);
        requirements(Category.effect, BuildVisibility.shown, ItemStack.with(Items.silicon, 2000,
            Items.phaseFabric, 1600, Items.surgeAlloy, 1400, CryonItems.nanoMaterial, 1800,
            CryonItems.neutronite, 1600, CryonItems.farstarAlloy, 1200));
        size = 9; health = 72000; armor = 30f;
        solid = update = hasPower = hasItems = acceptsItems = true;
        itemCapacity = 300; canOverdrive = false; clipSize = halfExtent * 2f + 100f;
        consumePowerCond(480f, (AnchorBuild b) -> b.enabled && b.hasFuel());
        buildType = AnchorBuild::new;
        if(!installed){ installed = true; Events.on(mindustry.game.EventType.ResetEvent.class, e -> anchors.clear()); }
    }
    @Override public void setStats(){
        super.setStats();
        stats.add(Stat.range, halfExtent * 2f / tilesize, StatUnit.blocks);
        stats.add(Stat.input, StatValues.items(phaseInterval, ItemStack.with(Items.phaseFabric, 1)));
    }
    @Override public void setBars(){
        super.setBars();
        addBar("reality-anchor", (AnchorBuild b) -> new Bar(
            () -> Core.bundle.get(b.active() ? "bar.cryon-anchor-active" : "bar.cryon-anchor-offline"),
            () -> fieldColor, () -> b.fuelTime / phaseInterval));
    }
    public static boolean contains(Team hostile, float x, float y, float margin){
        for(AnchorBuild b : anchors){
            if(!b.active() || MapAlliances.allied(hostile, b.team)) continue;
            float extent = ((RealityAnchoringMatrix)b.block).halfExtent + margin;
            if(Math.abs(x - b.x) <= extent && Math.abs(y - b.y) <= extent) return true;
        }
        return false;
    }
    /** Slab intersection includes fast projectiles crossing the entire square in one frame. */
    public static boolean crosses(Team hostile, float ax, float ay, float bx, float by){
        for(AnchorBuild b : anchors){
            if(!b.active() || MapAlliances.allied(hostile, b.team)) continue;
            float extent = ((RealityAnchoringMatrix)b.block).halfExtent;
            if(intersects(ax, ay, bx, by, b.x, b.y, extent)) return true;
        }
        return false;
    }
    public static boolean intersects(float ax, float ay, float bx, float by, float x, float y, float extent){
        float dx = bx - ax, dy = by - ay, low = 0f, high = 1f;
        if(Math.abs(dx) < 0.00001f){ if(Math.abs(ax - x) > extent) return false; }
        else{
            float a = (x - extent - ax) / dx, b = (x + extent - ax) / dx;
            low = Math.max(low, Math.min(a, b)); high = Math.min(high, Math.max(a, b));
        }
        if(Math.abs(dy) < 0.00001f){ if(Math.abs(ay - y) > extent) return false; }
        else{
            float a = (y - extent - ay) / dy, b = (y + extent - ay) / dy;
            low = Math.max(low, Math.min(a, b)); high = Math.min(high, Math.max(a, b));
        }
        return low <= high;
    }
    public static boolean teleportAllowed(Unit unit, float x, float y){
        if(unit.type instanceof OrbitalUnitType) return true;
        float margin = unit.hitSize / 2f;
        return !contains(unit.team, unit.x, unit.y, margin) && !contains(unit.team, x, y, margin);
    }
    /** Large native set() discontinuities cover scripted jumps without intercepting normal steps. */
    public static boolean allowRelocation(Unit unit, float x, float y, boolean explicit){
        if(net == null || net.client() || !unit.isAdded() || unit.dead || anchors.isEmpty()) return true;
        if(!explicit && unit.dst(x, y) <= Math.max(48f, unit.speed() * Math.max(Time.delta, 1f) * 6f)) return true;
        return teleportAllowed(unit, x, y);
    }
    public class AnchorBuild extends Building{
        public float fuelTime, warmup;
        public boolean hasFuel(){ return fuelTime > 0f || items.has(Items.phaseFabric); }
        public boolean active(){ return isValid() && !dead && enabled && hasFuel() && power != null && power.status >= 0.98f; }
        @Override public void add(){ super.add(); if(!anchors.contains(this, true)) anchors.add(this); }
        @Override public void onRemoved(){ anchors.remove(this, true); super.onRemoved(); }
        @Override public boolean acceptItem(Building source, Item item){ return item == Items.phaseFabric && items.get(item) < itemCapacity; }
        @Override public void updateTile(){
            boolean running = active();
            warmup = Mathf.approachDelta(warmup, running ? 1f : 0f, 0.04f);
            if(net.client() || !running) return;
            float remaining = Time.delta;
            while(remaining > 0.0001f){
                if(fuelTime <= 0f){
                    if(!items.has(Items.phaseFabric)) break;
                    items.remove(Items.phaseFabric, 1); fuelTime = phaseInterval;
                }
                float step = Math.min(remaining, fuelTime); remaining -= step; fuelTime = Math.max(0f, fuelTime - step);
            }
        }
        @Override public void drawSelect(){ super.drawSelect(); Drawf.dashSquare(fieldColor, x, y, halfExtent * 2f); }
        @Override public void draw(){
            super.draw();
            if(headless || warmup < 0.001f) return;
            RealityAnchorRenderer.draw(x, y, halfExtent, warmup);
            Drawf.light(x, y, size * tilesize * 2f, fieldColor, warmup * 0.2f);
        }
        @Override public byte version(){ return 1; }
        @Override public void write(Writes w){ super.write(w); w.f(fuelTime); w.f(warmup); }
        @Override public void read(Reads r, byte rev){ super.read(r, rev); fuelTime = r.f(); warmup = r.f(); }
    }
}
