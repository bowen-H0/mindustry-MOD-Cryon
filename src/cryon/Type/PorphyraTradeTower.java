package cryon.Type;

import arc.Core;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.io.Reads;
import arc.util.io.Writes;
import cryon.Content.CryonItems;
import mindustry.content.Items;
import mindustry.gen.Building;
import mindustry.type.Category;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.ui.Bar;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;

/** Physical protocol tokens buy a selected, fixed batch of imported materials. */
public class PorphyraTradeTower extends Block{
    public final Item[] imports = {Items.silicon, Items.graphite, Items.titanium};
    public final int[] prices = {8, 4, 6};
    public final int batchSize = 10;
    public float tradeTime = 180f;
    public int protocolCapacity = 120;

    public PorphyraTradeTower(String name){
        super(name);
        size = 3;
        health = 800;
        solid = update = hasItems = hasPower = configurable = saveConfig = true;
        itemCapacity = 120;
        buildType = TradeBuild::new;
        consumePower(0.8f);
        requirements(Category.crafting, BuildVisibility.shown,
                ItemStack.with(CryonItems.rhenium, 140, CryonItems.gammaCrystal, 70, CryonItems.technetium, 50));
        config(Integer.class, (TradeBuild build, Integer value) -> build.select(value));
        configClear((TradeBuild build) -> build.select(0));
    }

    @Override public boolean outputsItems(){ return true; }

    @Override public void setBars(){
        super.setBars();
        removeBar("items");
        addBar("protocol", (TradeBuild build) -> new Bar(
                () -> Core.bundle.format("bar.cryon-trade-points", build.items.get(CryonItems.protocolPoint), protocolCapacity),
                () -> CryonItems.protocolPoint.color,
                () -> build.items.get(CryonItems.protocolPoint) / (float)protocolCapacity));
        addBar("imports", (TradeBuild build) -> new Bar(
                () -> Core.bundle.format("bar.cryon-trade-storage", build.storedImports(), itemCapacity),
                () -> imports[build.selected].color,
                () -> build.storedImports() / (float)itemCapacity));
        addBar("trade", (TradeBuild build) -> new Bar(
                () -> Core.bundle.get("bar.cryon-trade-progress"),
                () -> CryonItems.protocolPoint.color, () -> build.progress / tradeTime));
    }

    public class TradeBuild extends Building{
        public int selected;
        public float progress;

        public void select(int value){
            if(value < 0 || value >= imports.length || value == selected) return;
            selected = value;
            progress = 0f;
        }

        @Override public Object config(){ return selected; }

        public int storedImports(){
            int count = 0;
            for(Item item : imports) count += items.get(item);
            return count;
        }

        @Override public int getMaximumAccepted(Item item){
            return item == CryonItems.protocolPoint ? protocolCapacity : 0;
        }

        @Override public boolean acceptItem(Building source, Item item){
            return item == CryonItems.protocolPoint && items.get(item) < protocolCapacity;
        }

        @Override public boolean shouldConsume(){
            // Currency and purchased goods use separate storage budgets. A purchase
            // can yield more items than it spends, so a shared total deadlocks at full input.
            return enabled && items.get(CryonItems.protocolPoint) >= prices[selected]
                    && storedImports() + batchSize <= itemCapacity;
        }

        @Override public void updateTile(){
            for(Item item : imports) dump(item);
            if(!shouldConsume() || efficiency <= 0f) return;
            progress += edelta();
            if(progress >= tradeTime){
                items.remove(CryonItems.protocolPoint, prices[selected]);
                items.add(imports[selected], batchSize);
                progress %= tradeTime;
            }
        }

        @Override public void buildConfiguration(Table table){
            for(int i = 0; i < imports.length; i++){
                final int index = i;
                String label = imports[i].localizedName + " ×" + batchSize
                        + "\n" + prices[i] + " " + CryonItems.protocolPoint.localizedName;
                table.button(label, () -> configure(index)).size(210f, 54f)
                        .update(button -> button.setDisabled(selected == index));
                table.row();
            }
        }

        @Override public byte version(){ return 1; }
        @Override public void write(Writes write){
            super.write(write);
            write.i(selected);
            write.f(progress);
        }
        @Override public void read(Reads read, byte revision){
            super.read(read, revision);
            if(revision >= 1){
                selected = Mathf.clamp(read.i(), 0, imports.length - 1);
                float saved = read.f();
                progress = Float.isFinite(saved) ? Mathf.clamp(saved, 0f, tradeTime) : 0f;
            }
        }
    }
}
