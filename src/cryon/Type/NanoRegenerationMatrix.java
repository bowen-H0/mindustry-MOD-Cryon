package cryon.Type;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.io.*;
import cryon.Content.CryonItems;
import cryon.Features.MapAlliances;
import cryon.Features.NanoMistRenderer;
import cryon.Fx.NanoRepairFx;
import mindustry.content.Items;
import mindustry.entities.Units;
import mindustry.entities.Effect;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.Bar;
import mindustry.world.Block;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

/** Paid nanomaterial batches fuel a continuous, authoritative allied repair field. */
public class NanoRegenerationMatrix extends Block{
    public static final Color mistColor = Color.valueOf("58e887");
    public float range = 480f, batchDuration = 30f;
    public int batchSize = 1;
    public float flatHealing = 1200f, buildingHealing = 0.30f, unitHealing = 0.20f;
    // Register the effect on both headless and graphical peers during content loading.
    public Effect repairEffect = NanoRepairFx.particles;

    public NanoRegenerationMatrix(String name){
        super(name);
        requirements(Category.effect, BuildVisibility.shown, ItemStack.with(
            Items.silicon, 1800, Items.phaseFabric, 600, Items.surgeAlloy, 900,
            CryonItems.nanoMaterial, 1600, CryonItems.neutronite, 800, CryonItems.farstarAlloy, 1200));
        size = 7; health = 36000; armor = 20f;
        suppressable = true;
        update = solid = hasPower = hasItems = acceptsItems = true;
        itemCapacity = 600; canOverdrive = false;
        clipSize = range * 2f + 100f;
        consumePowerCond(300f, (NanoMatrixBuild b) -> b.enabled && !b.isHealSuppressed() && b.hasFuel());
        buildType = NanoMatrixBuild::new;
    }

    @Override public void setStats(){
        super.setStats();
        stats.add(Stat.range, range / tilesize, StatUnit.blocks);
        stats.add(Stat.input, StatValues.items(batchDuration, ItemStack.with(CryonItems.nanoMaterial, batchSize)));
        stats.add(Stat.repairSpeed, Core.bundle.get("stat.cryon-nano-matrix-healing"));
    }

    @Override public void setBars(){
        super.setBars();
        addBar("nano-reserve", (NanoMatrixBuild b) -> new Bar(
            () -> Core.bundle.format("bar.cryon-nano-reserve", b.items.get(CryonItems.nanoMaterial)),
            () -> mistColor, () -> b.items.get(CryonItems.nanoMaterial) / (float)itemCapacity));
        addBar("nano-running", (NanoMatrixBuild b) -> new Bar(
            () -> Core.bundle.get(b.operating() ? "bar.cryon-nano-active" : "bar.cryon-nano-offline"),
            () -> mistColor, () -> b.fuelTime / batchDuration));
    }

    public class NanoMatrixBuild extends Building{
        public float fuelTime, warmup;
        private float refresh;
        private float particleTime;
        private int particleCursor;
        private final Seq<Building> buildings = new Seq<>();
        private final Seq<Unit> units = new Seq<>();

        public boolean hasFuel(){ return fuelTime > 0f || items.has(CryonItems.nanoMaterial, batchSize); }
        public boolean operating(){
            return enabled && !dead && !isHealSuppressed() && hasFuel() && power != null && power.status >= 0.98f;
        }
        @Override public boolean acceptItem(Building source, Item item){
            return item == CryonItems.nanoMaterial && items.get(item) < itemCapacity;
        }
        @Override public void updateTile(){
            boolean running = operating();
            warmup = Mathf.approachDelta(warmup, running ? 1f : 0f, running ? 0.035f : 0.06f);
            if(!running) return;
            refreshTargets();
            // Each graphical peer creates its own cosmetic effects from synchronized targets.
            // A headless host must not be responsible for creating client-only particles.
            if(!headless){
                particleTime += Time.delta;
                if(particleTime >= 12f){ particleTime %= 12f; emitRepairParticles(); }
            }
            if(net.client()) return;
            // Charge before healing. Account for large deltas without spending a fractional
            // batch or providing a frame of free healing when the last batch runs out.
            float remaining = Time.delta, worked = 0f;
            while(remaining > 0.0001f){
                if(fuelTime <= 0f){
                    if(!items.has(CryonItems.nanoMaterial, batchSize)) break;
                    items.remove(CryonItems.nanoMaterial, batchSize);
                    fuelTime = batchDuration;
                }
                float step = Math.min(remaining, fuelTime);
                worked += step; remaining -= step; fuelTime = Math.max(0f, fuelTime - step);
            }
            if(worked <= 0f) return;
            float seconds = worked / 60f;
            for(Building b : buildings){
                if(!canHeal(b)) continue;
                b.heal((flatHealing + b.maxHealth * buildingHealing) * seconds);
                b.recentlyHealed();
            }
            for(Unit u : units){
                if(!canHeal(u)) continue;
                u.heal((flatHealing + u.maxHealth * unitHealing) * seconds);
            }
        }
        private void refreshTargets(){
            refresh -= Time.delta;
            if(refresh <= 0f){
                refresh = 15f; buildings.clear(); units.clear();
                Units.nearbyBuildings(x, y, range, b -> {
                    if(MapAlliances.allied(team, b.team) && b.within(this, range)) buildings.add(b);
                });
                Units.nearby(x - range, y - range, range * 2f, range * 2f, u -> {
                    if(MapAlliances.allied(team, u.team) && u.within(this, range)) units.add(u);
                });
            }
        }
        private boolean canHeal(Building b){
            return b.isValid() && !b.dead && b.damaged() && b.within(this, range) &&
                MapAlliances.allied(team, b.team) && !b.isHealSuppressed();
        }
        private boolean canHeal(Unit u){
            return u.isValid() && !u.dead && u.damaged() && u.within(this, range) && MapAlliances.allied(team, u.team);
        }
        private void emitRepairParticles(){
            int count = buildings.size + units.size;
            if(count == 0) return;
            int emitted = 0, visited = 0;
            // Rotate the budget so large armies all receive visible repairs over time.
            while(visited++ < count && emitted < 16){
                int index = particleCursor++ % count;
                if(index < buildings.size){
                    Building b = buildings.get(index);
                    if(!canHeal(b)) continue;
                    repairEffect.at(b.x, b.y, b.block.size * tilesize, mistColor, b);
                }else{
                    Unit u = units.get(index - buildings.size);
                    if(!canHeal(u)) continue;
                    repairEffect.at(u.x, u.y, u.hitSize * 1.3f, mistColor, u);
                }
                emitted++;
            }
        }
        @Override public void drawSelect(){
            super.drawSelect(); Drawf.dashCircle(x, y, range, mistColor);
        }
        @Override public void draw(){
            super.draw();
            if(headless || warmup <= 0.001f) return;
            NanoMistRenderer.draw(x, y, range, warmup, id);
            float z = Draw.z(); Draw.z(Layer.blockOver);
            Draw.color(mistColor, warmup * (0.5f + Mathf.absin(Time.time, 12f, 0.25f)));
            Fill.circle(x, y, 5f);
            Lines.stroke(1.2f); Lines.square(x, y, 13f, 45f);
            Drawf.light(x, y, range * 1.25f, mistColor, warmup * 0.14f);
            Draw.reset(); Draw.z(z);
        }
        @Override public byte version(){ return 2; }
        @Override public void write(Writes w){ super.write(w); w.f(fuelTime); w.f(warmup); }
        @Override public void read(Reads r, byte revision){
            super.read(r, revision); fuelTime = r.f(); warmup = r.f(); refresh = 0f;
            // A version-1 prepaid second contained 30 materials. Preserve that paid reserve
            // at the new half-second-per-item rate instead of throwing the material away.
            if(revision < 2) fuelTime *= 15f;
        }
    }
}
