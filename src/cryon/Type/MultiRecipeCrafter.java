package cryon.Type;

import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.ImageButton;
import arc.scene.ui.layout.Table;
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
 * - 配方通过点击方块弹出的配置 UI 手动选择（不再靠喂料自动判定）。
 * - 未选择配方前（currentRecipe == -1），不接受任何物品输入。
 * - 配方一旦选定就一直锁定，直到玩家在配置 UI 里手动切换到另一个配方。
 */
public class MultiRecipeCrafter extends GenericCrafter {

    /** 所有可用配方 */
    public Seq<CraftRecipe> recipes = new Seq<>(4);

    protected ConsumeItemDynamic consItemDyn;
    protected ConsumeLiquidsDynamic consLiquidDyn;

    public MultiRecipeCrafter(String name) {
        super(name);
        configurable = true;   // 改动：需要弹出配置 UI，必须允许配置
        saveConfig = true;     // 存档要记住玩家选的配方
    }

    @Override
    public void init() {
        config(Integer.class, (MultiRecipeCrafterBuild build, Integer value) -> {
            if (value == null) return;
            if (value != -1 && (value < 0 || value >= recipes.size)) return;
            if (build.currentRecipe != value) {
                build.currentRecipe = value;
                build.progress = 0f;
            }
        });

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

    /** 配方的代表图标：优先用第一个产出物品，没有产出就用第一个输入物品 */
    protected Item representativeItem(CraftRecipe r) {
        if (r.outputItems != null && r.outputItems.length > 0) return r.outputItems[0].item;
        if (r.itemReq != null && r.itemReq.length > 0) return r.itemReq[0].item;
        return null;
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
        /** -1 表示尚未选择配方，此时不接受任何物品输入 */
        public int currentRecipe = -1;

        public CraftRecipe recipe() {
            return currentRecipe == -1 ? null : recipes.get(currentRecipe);
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            // 改动：未选配方前，一律拒绝任何物品
            if (currentRecipe == -1) return false;

            CraftRecipe r = recipe();
            if (r.itemReq == null) return false;

            boolean usedByRecipe = Structs.contains(r.itemReq, s -> s.item == item);
            if (!usedByRecipe) return false; // 只接受当前锁定配方需要的物品

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

            // 改动：去掉"清空存货就自动解锁配方"的逻辑
            // 配方现在完全由玩家在配置 UI 里手动决定，不再被库存状态自动重置
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

        // 改动：新增配置面板，点击方块后弹出，展示每个配方的代表图标供玩家选择
        @Override
        public void buildConfiguration(Table table) {
            if (recipes.isEmpty()) return;

            table.table(Styles.black6, t -> {
                t.defaults().size(42f).pad(2f);

                int cols = Math.max(1, (int) Math.sqrt(recipes.size) + 1);
                int i = 0;
                for (CraftRecipe r : recipes) {
                    int index = i;
                    Item icon = representativeItem(r);

                    ImageButton b = t.button(
                            icon == null ? Icon.cancel : new TextureRegionDrawable(icon.uiIcon),
                            Styles.clearNoneTogglei,
                            32f,
                            () -> configure(index)
                    ).get();
                    b.update(() -> b.setChecked(currentRecipe == index));

                    i++;
                    if (i % cols == 0) t.row();
                }
            });
        }

        @Override
        public Object senseObject(LAccess sensor) {
            if (sensor == LAccess.config) return currentRecipe == -1 ? null : recipe().outputItems != null ? recipe().outputItems[0].item : null;
            return super.senseObject(sensor);
        }


        @Override
        public Object config() {
            return currentRecipe;
        }
        @Override
        public byte version() {
            return 1;
        }
        @Override
        public void write(Writes write) {
            super.write(write);
            write.s(currentRecipe);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            if (revision >= 1) {
                currentRecipe = read.s();
            } else {
                currentRecipe = -1;
            }
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