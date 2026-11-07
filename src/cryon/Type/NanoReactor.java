package cryon.Type;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.io.*;
import cryon.Content.CryonItems;
import cryon.Content.NanoEnvironment;
import cryon.Fx.NanoFeedbackFx;
import mindustry.content.*;
import mindustry.entities.Damage;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.ui.Bar;
import mindustry.world.Block;
import mindustry.world.meta.*;
import static mindustry.Vars.*;

/** A retained seed doubles the entire live inventory, rather than a fixed recipe output. */
public class NanoReactor extends Block{
    public float cycleTime = 240f, blockageTime = 180f;
    public int siliconCost = 1, titaniumCost = 1;
    public static final Color nanoColor = Color.valueOf("4ce68c");

    public NanoReactor(String name){
        super(name);
        requirements(Category.crafting, ItemStack.with(Items.silicon, 600, Items.titanium, 500,
            Items.phaseFabric, 150, Items.surgeAlloy, 200));
        size = 4; health = 2400;
        update = solid = hasItems = acceptsItems = hasPower = true;
        outputsPower = false; itemCapacity = 1024; canOverdrive = false;
        consumePowerCond(100f, (NanoReactorBuild b) -> b.canReplicate());
        buildType = NanoReactorBuild::new;
    }

    @Override public boolean outputsItems(){ return true; }
    @Override public void setStats(){
        super.setStats();
        stats.add(Stat.productionTime, cycleTime / 60f, StatUnit.seconds);
        stats.add(Stat.input, StatValues.items(cycleTime, ItemStack.with(Items.silicon, siliconCost, Items.titanium, titaniumCost)));
        stats.add(Stat.input, Core.bundle.get("stat.cryon-nano-food"));
        stats.add(Stat.output, Core.bundle.get("stat.cryon-nano-replication"));
    }
    @Override public void setBars(){
        super.setBars();
        addBar("nano-stock", (NanoReactorBuild b) -> new Bar(
            () -> Core.bundle.format("bar.cryon-nano-reserve", b.items.get(CryonItems.nanoMaterial)),
            () -> nanoColor, () -> b.items.get(CryonItems.nanoMaterial) / (float)itemCapacity));
        addBar("nano-danger", (NanoReactorBuild b) -> new Bar(
            () -> Core.bundle.get("bar.cryon-nano-danger"), () -> Color.scarlet,
            () -> b.blocked / blockageTime));
    }

    public class NanoReactorBuild extends Building{
        public float progress, blocked;
        private float dumpClock;
        private boolean exploded;

        public boolean canReplicate(){
            int stock = items.get(CryonItems.nanoMaterial);
            return enabled && stock > 0
                && items.has(Items.silicon, stock * siliconCost) && items.has(Items.titanium, stock * titaniumCost);
        }
        @Override public int getMaximumAccepted(Item item){ return itemCapacity; }
        @Override public boolean acceptItem(Building source, Item item){
            return (item == CryonItems.nanoMaterial || item == Items.silicon || item == Items.titanium)
                && items.get(item) < getMaximumAccepted(item);
        }
        @Override public boolean canDump(Building to, Item item){
            return item == CryonItems.nanoMaterial && items.get(item) > 1;
        }
        @Override public int removeStack(Item item, int amount){
            return super.removeStack(item, item == CryonItems.nanoMaterial ? Math.min(amount, Math.max(0, items.get(item) - 1)) : amount);
        }
        @Override public void updateTile(){
            if(net.client()) return;
            int stock = items.get(CryonItems.nanoMaterial);
            dumpClock += Time.delta;
            boolean exported = false;
            if(dumpClock >= 5f){
                dumpClock %= 5f;
                for(int i = 0; i < 16 && items.get(CryonItems.nanoMaterial) > 1; i++){
                    if(!dump(CryonItems.nanoMaterial)) break;
                    exported = true;
                }
            }
            stock = items.get(CryonItems.nanoMaterial);
            if(exported || stock < itemCapacity * 3 / 4) blocked = 0f;
            else blocked += Time.delta; // Existing high-density stock stays dangerous without power.
            if(stock > itemCapacity || blocked >= blockageTime){ kill(); return; }
            if(!canReplicate() || power.status < 0.98f) return;
            progress += Time.delta;
            while(progress >= cycleTime && canReplicate()){
                progress -= cycleTime;
                stock = items.get(CryonItems.nanoMaterial);
                items.remove(Items.silicon, stock * siliconCost); items.remove(Items.titanium, stock * titaniumCost);
                if(stock > itemCapacity / 2){ kill(); return; }
                items.add(CryonItems.nanoMaterial, stock);
                NanoFeedbackFx.event(NanoFeedbackFx.grow, x, y, size, 2);
            }
        }
        @Override public void onDestroyed(){
            if(!exploded && !net.client()){
                exploded = true; // Set before area damage: adjacent reactors can chain-react.
                int radius = 6 + Math.min(10, items.get(CryonItems.nanoMaterial) / 100);
                NanoEnvironment.seed(x, y, radius);
                NanoFeedbackFx.event(NanoFeedbackFx.explosion, x, y, radius * tilesize, 3);
                Damage.damage(x, y, radius * tilesize, 2000f + items.get(CryonItems.nanoMaterial) * 8f);
            }
            super.onDestroyed();
        }
        @Override public void draw(){
            super.draw();
            Draw.color(nanoColor, Color.scarlet, Mathf.clamp(blocked / blockageTime));
            Draw.alpha(0.4f + Mathf.absin(Time.time, 8f, 0.4f));
            Fill.circle(x, y, 2f + 4f * Mathf.clamp(items.get(CryonItems.nanoMaterial) / (float)itemCapacity));
            Draw.reset();
            if(blocked > 0f){
                NanoFeedbackFx.ambient(NanoFeedbackFx.warning, x, y, 0f, 12f);
            }else if(canReplicate() && power.status >= 0.98f){
                NanoFeedbackFx.ambient(NanoFeedbackFx.charge, x, y, 0f, 22f);
            }
        }
        @Override public byte version(){ return 1; }
        @Override public void write(Writes w){ super.write(w); w.f(progress); w.f(blocked); }
        @Override public void read(Reads r, byte revision){ super.read(r, revision); progress = r.f(); blocked = r.f(); }
    }
}
