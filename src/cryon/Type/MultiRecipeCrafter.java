package cryon.Type;

import arc.graphics.g2d.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.logic.LAccess;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.consumers.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

/**
 * MultiRecipeCrafter
 *
 * 支持多配方的通用工厂：
 * - 面板（database/stats）里会列出全部配方的输入输出，方便玩家查阅。
 * - 不提供悬浮点选 UI，配方完全靠"喂什么原料"自动判定、自动切换。
 * - 只要工厂当前存货为空（items.empty()），喂入任意一种被配方使用的物品，
 *   就会自动切换/锁定到对应配方；存货没清空前不会切换到别的配方，
 *   避免不同配方的原料互相污染。
 */
public class MultiRecipeCrafter extends GenericCrafter {

    /** 所有可用配方 */
    public Seq<CraftRecipe> recipes = new Seq<>(4);

    protected ConsumeItemDynamic consItemDyn;
    protected ConsumeLiquidsDynamic consLiquidDyn;

    public MultiRecipeCrafter(String name) {
        super(name);
        configurable = false; //不需要玩家手动选配方,不会弹悬浮UI
    }

    @Override
    public void init() {
        //动态消耗：当前配方需要什么，就消耗什么
        consume(consItemDyn = new ConsumeItemDynamic((MultiRecipeCrafterBuild build) ->
                build.currentRecipe == -1 || build.recipe().itemReq == null
                        ? ItemStack.empty
                        : build.recipe().itemReq));

        consume(consLiquidDyn = new ConsumeLiquidsDynamic((MultiRecipeCrafterBuild build) ->
                build.currentRecipe == -1 || build.recipe().liquidReq == null
                        ? LiquidStack.empty
                        : build.recipe().liquidReq));

        super.init();
    }

    /** 面板里展示全部配方的输入输出，替代基类只显示单一 outputItems 的逻辑 */
    @Override
    public void setStats() {
        super.setStats();

        stats.add(Stat.output, table -> {
            table.row();
            for (CraftRecipe r : recipes) {
                table.table(Styles.grayPanel, t -> {
                    t.table(in -> {
                        int len = 0;
                        if (r.itemReq != null) {
                            for (ItemStack stack : r.itemReq) {
                                if (len++ % 6 == 0) in.row();
                                in.add(StatValues.stack(stack)).pad(5);
                            }
                        }
                        if (r.liquidReq != null) {
                            for (LiquidStack stack : r.liquidReq) {
                                in.row();
                                in.add(StatValues.displayLiquid(stack.liquid, stack.amount * 60f, true));
                            }
                        }
                    }).left();

                    t.add(" -> ").pad(10f);

                    t.table(out -> {
                        int len = 0;
                        if (r.outputItems != null) {
                            for (ItemStack stack : r.outputItems) {
                                if (len++ % 6 == 0) out.row();
                                out.add(StatValues.stack(stack)).pad(5);
                            }
                        }
                        if (r.outputLiquids != null) {
                            for (LiquidStack stack : r.outputLiquids) {
                                out.row();
                                out.add(StatValues.displayLiquid(stack.liquid, stack.amount * 60f, true));
                            }
                        }
                    }).right();
                }).growX().pad(5);
                table.row();
            }
        });
    }

    public class MultiRecipeCrafterBuild extends GenericCrafterBuild {
        /** -1 表示尚未锁定配方,等待原料输入来判定 */
        public int currentRecipe = -1;

        public CraftRecipe recipe() {
            return currentRecipe == -1 ? null : recipes.get(currentRecipe);
        }

        /** 根据物品反查它属于哪个配方的输入 */
        protected int findRecipeFor(Item item) {
            return recipes.indexOf(r -> r.itemReq != null && Structs.contains(r.itemReq, s -> s.item == item));
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            int match = findRecipeFor(item);
            if (match == -1) return false; //没有任何配方用得到这个物品

            if (currentRecipe == -1) {
                //空闲状态,直接锁定这个配方
                if (!items.empty()) return false; //理论上不该出现:没锁定配方但有残留物品
                currentRecipe = match;
            } else if (currentRecipe != match) {
                //喂的是别的配方的原料
                if (items.empty()) {
                    //当前配方已经清空,允许切换
                    currentRecipe = match;
                    progress = 0f;
                } else {
                    return false; //拒绝混料
                }
            }

            return items.get(item) < getMaximumAccepted(item);
        }

        @Override
        public boolean shouldConsume() {
            if (currentRecipe == -1) return false;
            CraftRecipe r = recipe();

            if (r.outputItems != null) {
                for (ItemStack output : r.outputItems) {
                    if (items.get(output.item) + output.amount > itemCapacity) return false;
                }
            }

            if (r.outputLiquids != null && !ignoreLiquidFullness) {
                boolean allFull = true;
                for (LiquidStack output : r.outputLiquids) {
                    if (liquids.get(output.liquid) >= liquidCapacity - 0.001f) {
                        if (!dumpExtraLiquid) return false;
                    } else {
                        allFull = false;
                    }
                }
                if (allFull) return false;
            }

            return enabled;
        }

        @Override
        public void updateTile() {
            if (efficiency > 0) {
                progress += getProgressIncrease(currentRecipe == -1 ? craftTime : recipe().craftTime);
                warmup = Mathf.approachDelta(warmup, warmupTarget(), warmupSpeed);

                CraftRecipe r = recipe();
                if (r != null && r.outputLiquids != null) {
                    float inc = getProgressIncrease(1f);
                    for (LiquidStack output : r.outputLiquids) {
                        handleLiquid(this, output.liquid, Math.min(output.amount * inc, liquidCapacity - liquids.get(output.liquid)));
                    }
                }

                if (wasVisible && Mathf.chanceDelta(updateEffectChance)) {
                    updateEffect.at(x + Mathf.range(size * updateEffectSpread), y + Mathf.range(size * updateEffectSpread));
                }
            } else {
                warmup = Mathf.approachDelta(warmup, 0f, warmupSpeed);
            }

            totalProgress += warmup * Time.delta;

            if (progress >= 1f) {
                craft();
            }

            dumpOutputs();

            //工厂彻底清空后,解除配方锁定,等待下一次原料自动判定
            if (currentRecipe != -1 && items.empty() && (liquids == null || liquids.currentAmount() <= 0.0001f)) {
                currentRecipe = -1;
            }
        }

        @Override
        public float getProgressIncrease(float baseTime) {
            CraftRecipe r = recipe();
            if (ignoreLiquidFullness || r == null || r.outputLiquids == null) {
                return edelta() / baseTime;
            }

            float scaling = 1f, max = 1f;
            for (LiquidStack s : r.outputLiquids) {
                float value = (liquidCapacity - liquids.get(s.liquid)) / (s.amount * edelta());
                scaling = Math.min(scaling, value);
                max = Math.max(max, value);
            }

            return (edelta() / baseTime) * (dumpExtraLiquid ? Math.min(max, 1f) : scaling);
        }

        public void craft() {
            consume();

            CraftRecipe r = recipe();
            if (r != null && r.outputItems != null) {
                for (ItemStack output : r.outputItems) {
                    for (int i = 0; i < output.amount; i++) offload(output.item);
                }
            }

            if (wasVisible) craftEffect.at(x, y);
            progress %= 1f;
        }

        public void dumpOutputs() {
            CraftRecipe r = recipe();
            if (r == null) return;

            if (r.outputItems != null && timer(timerDump, dumpTime / timeScale)) {
                for (ItemStack output : r.outputItems) {
                    dump(output.item);
                }
            }

            if (r.outputLiquids != null) {
                for (LiquidStack output : r.outputLiquids) {
                    dumpLiquid(output.liquid, 2f, -1);
                }
            }
        }

        @Override
        public Object senseObject(LAccess sensor) {
            if (sensor == LAccess.config) return currentRecipe == -1 ? null : recipe().outputItems != null ? recipe().outputItems[0].item : null;
            return super.senseObject(sensor);
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.s(currentRecipe);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            currentRecipe = read.s();
        }
    }

    /** 单条配方：输入物品/液体，输出物品/液体，独立的制作耗时 */
    public static class CraftRecipe {
        public ItemStack[] itemReq;
        public LiquidStack[] liquidReq;
        public ItemStack[] outputItems;
        public LiquidStack[] outputLiquids;
        public float craftTime = 60f;

        public CraftRecipe() {}

        public CraftRecipe(ItemStack[] itemReq, ItemStack[] outputItems, float craftTime) {
            this.itemReq = itemReq;
            this.outputItems = outputItems;
            this.craftTime = craftTime;
        }
    }
}