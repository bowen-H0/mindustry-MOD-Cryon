package example;

import arc.func.Prov;
import arc.math.Mathf;
import arc.util.Log;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.gen.Building;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.blocks.production.Drill;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * 将"掉血 + 挖镁概率自爆"行为注入到 JSON 定义的 melting-drill 方块。
 *
 * 行为：
 *  - 每次产出矿物 → 扣除自身 HP（mineHealthCost）
 *  - 当前挖的是镁时，每次产出额外有 explosionChance 概率触发爆炸并自毁
 *
 * 用法（ExampleJavaMod.init() 末尾）：
 *   MeltingDrillInjector.inject("cryon-melting-drill");
 */
public class MeltingDrillInjector extends Drill {

    // ── 可调参数 ──────────────────────────────────────────────────
    /** 每次产出扣血量。 */
    public float mineHealthCost = 25f;
    /** 挖镁时每次产出触发爆炸的概率（0~1）。 */
    public float explosionChance = 0.05f;
    /** 爆炸范围（像素）。 */
    public float explosionRadius = 60f;
    /** 爆炸伤害。 */
    public float explosionDamage = 150f;
    /** 爆炸主效果。 */
    public Effect explosionEffect = Fx.blastExplosion;
    /** 附加火焰效果。 */
    public Effect fireEffect = Fx.fireballsmoke;
    // ─────────────────────────────────────────────────────────────

    /** 目标矿物（镁），init 后赋值。 */
    public Item magnesiumItem = null;

    public MeltingDrillInjector(String name) {
        super(name);
    }

    // =========================================================================
    //  Build
    // =========================================================================

    public class MeltingDrillBuild extends DrillBuild {

        @Override
        public void updateTile() {
            // dump 计时器
            if (timer(timerDump, dumpTime / timeScale())) {
                dump(dominantItem != null && items.has(dominantItem) ? dominantItem : null);
            }

            if (dominantItem == null) return;

            timeDrilled += warmup * delta();

            float delay = getDrillTime(dominantItem);

            if (items.total() < itemCapacity && dominantItems > 0 && efficiency > 0) {
                float speed = Mathf.lerp(1f, liquidBoostIntensity, optionalEfficiency) * efficiency;
                lastDrillSpeed = (speed * dominantItems * warmup) / delay;
                warmup = Mathf.approachDelta(warmup, speed, warmupSpeed);
                progress += delta() * dominantItems * speed * warmup;

                if (Mathf.chanceDelta(updateEffectChance * warmup)) {
                    updateEffect.at(x + Mathf.range(size * 2f),
                            y + Mathf.range(size * 2f));
                }
            } else {
                lastDrillSpeed = 0f;
                warmup = Mathf.approachDelta(warmup, 0f, warmupSpeed);
                return;
            }

            if (dominantItems > 0 && progress >= delay && items.total() < itemCapacity) {
                int amount = (int)(progress / delay);
                for (int i = 0; i < amount; i++) {
                    offload(dominantItem);
                }
                progress %= delay;

                if (wasVisible && Mathf.chanceDelta(drillEffectChance * warmup)) {
                    drillEffect.at(x + Mathf.range(drillEffectRnd),
                            y + Mathf.range(drillEffectRnd),
                            dominantItem.color);
                }

                // ★ 挖镁时：掉血 + 概率自爆 ★
                if (magnesiumItem != null && dominantItem == magnesiumItem) {
                    damage(mineHealthCost);
                    if (Mathf.chance(explosionChance)) {
                        explodeAndDie();
                    }
                }
            }
        }

        private void explodeAndDie() {
            // 范围伤害
            mindustry.entities.Units.nearbyEnemies(team, x, y, explosionRadius, unit -> {
                unit.damage(explosionDamage);
            });
            Vars.indexer.eachBlock(null, x, y, explosionRadius,
                    b -> b.team != team,
                    b -> b.damage(explosionDamage));

            // 视觉效果
            if (wasVisible) {
                explosionEffect.at(x, y);
                if (fireEffect != null) {
                    for (int i = 0; i < 4; i++) {
                        fireEffect.at(x + Mathf.range(size * 4f),
                                y + Mathf.range(size * 4f));
                    }
                }
            }

            Log.debug("[MeltingDrill] Exploded at (@, @)", tileX(), tileY());
            kill();
        }
    }

    // =========================================================================
    //  inject
    // =========================================================================

    /**
     * @param blockName       方块完整注册名（如 "cryon-melting-drill"）
     * @param mineHealthCost  每次产出扣血量
     * @param explosionChance 挖镁时爆炸概率（0~1）
     * @param explosionRadius 爆炸半径（像素）
     * @param explosionDamage 爆炸伤害
     */
    public static void inject(String blockName,
                              float mineHealthCost,
                              float explosionChance,
                              float explosionRadius,
                              float explosionDamage) {
        Block block = Vars.content.blocks().find(b -> b.name.equals(blockName));
        if (block == null) {
            Log.warn("[MeltingDrill] Block not found: @", blockName);
            return;
        }
        if (!(block instanceof Drill target)) {
            Log.warn("[MeltingDrill] Block is not a Drill: @", blockName);
            return;
        }

        Item magnesium = Vars.content.items().find(i -> i.name.contains("cryon-magnesium"));
        if (magnesium == null) {
            Log.warn("[MeltingDrill] Magnesium item not found, explosion will never trigger.");
        }

        String tempName = "__meltingdrill_inject__" + blockName;
        MeltingDrillInjector wrapper = new MeltingDrillInjector(tempName);
        copyAllFields(target, wrapper);
        removeFromContentRegistry(wrapper, tempName);

        wrapper.mineHealthCost  = mineHealthCost;
        wrapper.explosionChance = explosionChance;
        wrapper.explosionRadius = explosionRadius;
        wrapper.explosionDamage = explosionDamage;
        wrapper.magnesiumItem   = magnesium;

        try {
            Field buildTypeField = Block.class.getDeclaredField("buildType");
            buildTypeField.setAccessible(true);
            Prov<Building> newBuildType = (Prov<Building>) buildTypeField.get(wrapper);
            buildTypeField.set(target, newBuildType);

            Log.info("[MeltingDrill] Injected '@' | hpCost=@ explodeChance=@% radius=@ damage=@",
                    blockName, mineHealthCost, (int)(explosionChance * 100),
                    explosionRadius, explosionDamage);
        } catch (Exception e) {
            Log.err("[MeltingDrill] Inject failed for '@'", blockName);
            Log.err(e);
        }
    }

    /** 默认参数便捷重载：扣25血，5%概率爆炸，半径60，伤害150。 */
    public static void inject(String blockName) {
        inject(blockName, 25f, 0.05f, 60f, 150f);
    }

    // =========================================================================
    //  工具方法
    // =========================================================================

    private static void copyAllFields(Block src, Block dst) {
        Class<?> cls = src.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (Modifier.isFinal(f.getModifiers())) continue;
                String n = f.getName();
                if (n.equals("name") || n.equals("localizedName") ||
                        n.equals("id")   || n.equals("buildType")) continue;
                try {
                    f.setAccessible(true);
                    f.set(dst, f.get(src));
                } catch (Exception ignored) {}
            }
            cls = cls.getSuperclass();
        }
    }

    private static void removeFromContentRegistry(MeltingDrillInjector wrapper, String tempName) {
        try {
            Vars.content.blocks().remove(wrapper);
            Field nameMapField = mindustry.core.ContentLoader.class
                    .getDeclaredField("nameMap");
            nameMapField.setAccessible(true);
            @SuppressWarnings("unchecked")
            arc.struct.ObjectMap<String, ?> nameMap =
                    (arc.struct.ObjectMap<String, ?>) nameMapField.get(Vars.content);
            nameMap.remove(tempName);
        } catch (Exception e) {
            Log.warn("[MeltingDrill] Could not remove wrapper from registry: @", e.getMessage());
        }
    }
}