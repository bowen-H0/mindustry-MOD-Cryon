package example;

import arc.func.Prov;
import arc.util.Log;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.heat.HeatConsumer;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class HeatBoostTurret extends ItemTurret {

    public float maxHeatBoost = 1.0f;

    /**
     * 满增幅所需热量值，与原版 heatRequirement 完全独立。
     * heatRequirement 保持 -1，让原版 canConsume() 不拦截炮塔。
     * heatCapacity 只用于计算加速比例，以及作为 HeatConsumer 的 heatRequirement()。
     */
    public float heatCapacity = 10f;

    public HeatBoostTurret(String name) {
        super(name);
    }

    /** 只添加热量相关属性，不调用 super，供 inject() 单独追加到 target.stats */
    public void addHeatStats() {
        String orange = mindustry.graphics.Pal.lightOrange.toString();
        stats.add(mindustry.world.meta.Stat.booster,
                "[#" + orange + "]+" + (int)(maxHeatBoost * 100f) + "% " +
                        arc.Core.bundle.get("stat.shootspeed", "射速") +
                        " @ " + (int)heatCapacity + " " +
                        mindustry.world.meta.StatUnit.heatUnits.localized() + "[]"
        );
    }

    /** 只添加热量状态条，不调用 super，供 inject() 单独追加到 target.bars */
    public void addHeatBar() {
        addBar("heat-boost", (HeatBoostTurretBuild b) ->
                new mindustry.ui.Bar(
                        () -> arc.Core.bundle.format("bar.heatpercent",
                                (int)b.boostHeat,
                                (int)(Math.min(b.boostHeat / heatCapacity, 1f) * 100f)),
                        () -> mindustry.graphics.Pal.lightOrange,
                        () -> b.boostHeat / heatCapacity
                )
        );
    }

    // =========================================================================
    // Build
    // =========================================================================

    /**
     * 实现 HeatConsumer 接口，让热量网络识别并向 sideHeat[] 写入热量。
     * 原版 TurretBuild 虽然有 sideHeat[] 字段，但只有 heatRequirement>0 时
     * 才注册为 HeatConsumer，我们绕过这个条件，直接实现接口。
     */
    public class HeatBoostTurretBuild extends ItemTurretBuild implements HeatConsumer {

        // 用自己的 sideHeat，不依赖父类的（父类的在 heatRequirement=-1 时不会被写入）
        public float[] boostSideHeat = new float[4];
        // 存储接收到的热量
        public float boostHeat = 0f;

        // ---- HeatConsumer 接口 ----

        @Override
        public float[] sideHeat() {
            return boostSideHeat;
        }

        @Override
        public float heatRequirement() {
            // 返回 heatCapacity，热量网络用这个判断满载
            return heatCapacity;
        }

        // ---- 更新 ----

        @Override
        public void updateTile() {
            // 手动从 boostSideHeat 计算热量，存入 boostHeat
            boostHeat = calculateHeat(boostSideHeat);
            super.updateTile();
            // super 里 heatRequirement=-1，不会碰 heatReq/sideHeat，安全
        }

        @Override
        protected float baseReloadSpeed() {
            float base = super.baseReloadSpeed();
            if (maxHeatBoost <= 0f || boostHeat <= 0f || heatCapacity <= 0f) return base;
            float heatFrac = Math.min(boostHeat / heatCapacity, 1f);
            return base * (1f + heatFrac * maxHeatBoost);
        }

        /** 屏蔽原版热量→efficiency映射，热量是纯可选加成。 */
        @Override
        public void updateEfficiencyMultiplier() {
            // 不调用 super
        }

        // ---- 序列化 ----

        @Override
        public void write(arc.util.io.Writes write) {
            super.write(write);
            write.f(boostHeat);
            for (float v : boostSideHeat) write.f(v);
        }

        @Override
        public void read(arc.util.io.Reads read, byte revision) {
            super.read(read, revision);
            boostHeat = read.f();
            for (int i = 0; i < 4; i++) boostSideHeat[i] = read.f();
        }
    }

    // =========================================================================
    // 注入
    // =========================================================================

    public static void inject(String blockName, float heatCapacity, float maxHeatBoost) {
        Block block = Vars.content.blocks().find(b -> b.name.equals(blockName));
        if (block == null) {
            Log.warn("[HeatBoost] Block not found: @", blockName);
            return;
        }
        if (!(block instanceof ItemTurret target)) {
            Log.warn("[HeatBoost] Block is not an ItemTurret: @", blockName);
            return;
        }

        String tempName = "__heatboost__" + blockName;
        HeatBoostTurret wrapper = new HeatBoostTurret(tempName);

        copyAllFields(target, wrapper);
        removeFromContentRegistry(wrapper, tempName);

        // heatRequirement 保持从 target 拷来的 -1，绝对不能改
        wrapper.heatCapacity = heatCapacity;
        wrapper.maxHeatBoost = maxHeatBoost;

        try {
            Field f = Block.class.getDeclaredField("buildType");
            f.setAccessible(true);
            Prov<Building> wrapperBuildType = (Prov<Building>) f.get(wrapper);
            f.set(target, wrapperBuildType);

            // 共享 stats 对象，addHeatStats 写进去就是写给 target
            wrapper.stats = target.stats;
            // 注：bars 已由 copyAllFields 拷贝，wrapper 与 target 指向同一对象，无需额外处理
            wrapper.addHeatStats();
            wrapper.addHeatBar();

            Log.info("[HeatBoost] Injected '@' | heatCapacity=@ maxBoost=@x",
                    blockName, heatCapacity, maxHeatBoost);
        } catch (Exception e) {
            Log.err("[HeatBoost] Inject failed for '@'", blockName);
            Log.err(e);
        }
    }

    // =========================================================================
    // 工具方法
    // =========================================================================

    private static void copyAllFields(Block src, Block dst) {
        Class<?> cls = src.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (Modifier.isFinal(f.getModifiers())) continue;
                String n = f.getName();
                if (n.equals("name") || n.equals("localizedName") ||
                        n.equals("id")   || n.equals("buildType"))  continue;
                try {
                    f.setAccessible(true);
                    f.set(dst, f.get(src));
                } catch (Exception ignored) {}
            }
            cls = cls.getSuperclass();
        }
    }

    private static void removeFromContentRegistry(HeatBoostTurret wrapper, String tempName) {
        try {
            Vars.content.blocks().remove(wrapper);
            Field nameMapField = mindustry.core.ContentLoader.class
                    .getDeclaredField("nameMap");
            nameMapField.setAccessible(true);
            @SuppressWarnings("unchecked")
            arc.struct.ObjectMap<String, ?> nameMap =
                    (arc.struct.ObjectMap<String, ?>) nameMapField.get(Vars.content);
            nameMap.remove(tempName);
            Log.debug("[HeatBoost] Removed temp wrapper '@' from registry", tempName);
        } catch (Exception e) {
            Log.warn("[HeatBoost] Could not remove wrapper from registry: @", e.getMessage());
        }
    }
}