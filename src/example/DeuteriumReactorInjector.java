package example;

import arc.func.Prov;
import arc.graphics.g2d.Draw;
import arc.struct.ObjectMap;
import arc.util.Log;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.graphics.Pal;
import mindustry.type.Liquid;
import mindustry.type.LiquidStack;
import mindustry.ui.Bar;
import mindustry.world.Block;
import mindustry.world.blocks.power.ImpactReactor;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import mindustry.world.meta.StatValues;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class DeuteriumReactorInjector extends ImpactReactor {

    private static ObjectMap<String, OutputConfig> outputConfigs = new ObjectMap<>();

    public static class OutputConfig {
        public Liquid liquid;
        public float amount;

        public OutputConfig(Liquid liquid, float amount) {
            this.liquid = liquid;
            this.amount = amount;
        }
    }

    public DeuteriumReactorInjector(String name) {
        super(name);
    }

    public class DeuteriumReactorBuild extends ImpactReactorBuild {

        @Override
        public void updateTile() {
            super.updateTile();

            OutputConfig config = outputConfigs.get(block.name);
            if(config == null || config.liquid == null) return;

            if(efficiency > 0) {
                // 直接输出液体，像GenericCrafter一样
                float inc = getProgressIncrease(1f);
                handleLiquid(this, config.liquid, Math.min(config.amount * inc,
                        liquidCapacity - liquids.get(config.liquid)));
            }

            // 倾倒多余液体
            dumpLiquid(config.liquid);
        }
    }

    public static void inject(String blockName, String outputLiquidName, float amount) {
        Block block = Vars.content.block(blockName);
        if(block == null) {
            Log.warn("[DeuteriumReactor] Block not found: @", blockName);
            return;
        }

        if(!(block instanceof ImpactReactor)) {
            Log.warn("[DeuteriumReactor] Block is not ImpactReactor: @", blockName);
            return;
        }

        Liquid liquid = Vars.content.liquid(outputLiquidName);
        if(liquid == null) {
            Log.warn("[DeuteriumReactor] Liquid not found: @", outputLiquidName);
            return;
        }

        outputConfigs.put(blockName, new OutputConfig(liquid, amount));

        String tempName = "__deuterium_inject__" + blockName;
        DeuteriumReactorInjector injector = new DeuteriumReactorInjector(tempName);
        copyAllFields((ImpactReactor)block, injector);
        removeFromRegistry(tempName);

        injector.outputsLiquid = true;

        try {
            Field buildTypeField = Block.class.getDeclaredField("buildType");
            buildTypeField.setAccessible(true);
            Prov<Building> newBuildType = (Prov<Building>) buildTypeField.get(injector);
            buildTypeField.set(block, newBuildType);

            // 用官方方法添加液体输出条
            block.addLiquidBar(liquid);

            // 添加面板信息 - 显示液体名称和输出量
            block.stats.add(Stat.output, StatValues.liquids(1f, new LiquidStack[]{
                    new LiquidStack(liquid, amount)
            }));

            Log.info("[DeuteriumReactor] Injected '@' | output=@ amount=@/tick",
                    blockName, outputLiquidName, amount);
        } catch(Exception e) {
            Log.err("[DeuteriumReactor] Inject failed: @", e.getMessage());
            e.printStackTrace();
        }
    }

    private static void copyAllFields(ImpactReactor src, DeuteriumReactorInjector dst) {
        Class<?> cls = src.getClass();
        while(cls != null && cls != Object.class) {
            for(Field f : cls.getDeclaredFields()) {
                if(Modifier.isFinal(f.getModifiers())) continue;
                String name = f.getName();
                if(name.equals("name") || name.equals("localizedName") ||
                        name.equals("id") || name.equals("buildType")) continue;
                try {
                    f.setAccessible(true);
                    f.set(dst, f.get(src));
                } catch(Exception ignored) {}
            }
            cls = cls.getSuperclass();
        }
    }

    private static void removeFromRegistry(String tempName) {
        try {
            Block block = Vars.content.block(tempName);
            if(block != null) {
                Vars.content.blocks().remove(block);
            }
        } catch(Exception e) {
            Log.warn("[DeuteriumReactor] Could not remove wrapper: @", e.getMessage());
        }
    }
}