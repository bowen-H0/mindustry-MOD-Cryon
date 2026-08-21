package example;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.game.EventType;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import arc.Events;

/**
 * 命运绑定子弹 - 纯辅助标记工具
 * 命中第一个敌人创建标记，命中第二个敌人建立永久链接
 * 队友攻击链接中的任一敌人，伤害会按比例分享给另一个
 */
public class KismetBulletType extends BasicBulletType {

    /** 链接范围（寻找第二个敌人的搜索半径） */
    public float linkRange = 120f;
    /** 链接颜色（红色） */
    public Color linkColor = Color.valueOf("ff0000");
    /** 伤害共享比例（攻击一方时，另一方受到的比例） */
    public float shareFraction = 0.8f;

    // 全局记录：已标记的敌人（等待配对的第一个目标）
    public static final ObjectSet<Unit> markedUnits = new ObjectSet<>();
    // 全局记录：unit -> 绑定的另一个 unit
    public static final ObjectMap<Unit, Unit> links = new ObjectMap<>();
    // 防止伤害转发死循环
    private static final ObjectSet<Unit> forwarding = new ObjectSet<>();

    // 静态共享配置
    public static float shareFractionStatic = 0.8f;

    // 红色颜色常量
    private static final Color RED = Color.valueOf("ff0000");
    // 用于连线的半透明红色
    private static final Color RED_ALPHA = Color.valueOf("ff000080");
    // 标记圈的颜色（带一点透明度）
    private static final Color MARK_COLOR = Color.valueOf("ff0000aa");

    public KismetBulletType(float speed, float damage) {
        super(speed, damage);

        this.collides = true;
        this.collidesTiles = false;
        this.hitSize = 4f;

        // ===== 完全无视护甲 =====
        this.pierceArmor = true;      // 无视护甲
        this.armorMultiplier = 0f;     // 护甲倍率0

        // 确保子弹必定命中
        this.absorbable = false;
        this.hittable = true;
        this.pierce = true;
        this.pierceCap = 999;
    }

    static {
        // 全局挂钩：拦截单位受伤，转发共享伤害
        Events.on(EventType.UnitDamageEvent.class, e -> {
            Unit victim = e.unit;
            if (victim == null || forwarding.contains(victim)) return;

            // 检查是否有链接
            Unit partner = links.get(victim);
            if (partner == null || !partner.isValid() || partner.dead) {
                // 链接目标已失效，清理
                links.remove(victim);
                // 同时检查反向链接
                for (ObjectMap.Entry<Unit, Unit> entry : links.entries()) {
                    if (entry.value == victim) {
                        links.remove(entry.key);
                        break;
                    }
                }
                return;
            }

            // 获取伤害来源的子弹伤害值
            float baseDamage = e.bullet != null ? e.bullet.damage : 0f;

            // 如果受害者和伙伴都活着，共享伤害
            if (partner.isValid() && !partner.dead) {
                float shared = baseDamage * shareFractionStatic;

                if (shared > 0.01f) {
                    forwarding.add(partner);
                    partner.damage(shared);
                    forwarding.remove(partner);

                    // 在伙伴位置播放红色粒子效果
                    Fx.hitLancer.at(partner.x, partner.y);
                    Fx.sparkShoot.at(partner.x, partner.y, 0, RED);
                }
            }

            // 如果受害者死亡，清除所有链接
            if (victim.dead || !victim.isValid()) {
                Unit partner2 = links.get(victim);
                if (partner2 != null) {
                    links.remove(victim);
                    links.remove(partner2);
                    // 在伙伴身上播放消失特效
                    Fx.hitLancer.at(partner2.x, partner2.y);
                }
            }
        });
    }

    @Override
    public void init(Bullet b) {
        super.init(b);
        // 同步实例配置到静态字段
        shareFractionStatic = shareFraction;
    }

    @Override
    public void hit(Bullet b, float x, float y) {
        super.hit(b, x, y);

        // 检测命中的敌人
        Unit hitUnit = findNearestEnemy(b, x, y, 8f);
        if (hitUnit == null || !hitUnit.isValid() || hitUnit.dead) return;

        // 检查是否已被标记
        if (markedUnits.contains(hitUnit)) {
            return;
        }

        // 检查这个敌人是否已经在链接中（作为key或value）
        if (links.containsKey(hitUnit)) {
            return;
        }
        if (links.containsValue(hitUnit, true)) {
            return;
        }

        // 查找是否有等待配对的标记
        Unit firstMarked = findMarkedEnemy(hitUnit);

        if (firstMarked == null) {
            // 没有等待配对的标记，将当前敌人标记
            markedUnits.add(hitUnit);
            // 在敌人身上播放红色标记粒子
            Fx.sparkShoot.at(hitUnit.x, hitUnit.y, 0, RED);
            Fx.hitLancer.at(hitUnit.x, hitUnit.y);
        } else {
            // 找到等待配对的标记，建立链接
            // 从标记集合中移除（但标记圈会继续显示）
            markedUnits.remove(firstMarked);

            // 建立双向链接
            links.put(hitUnit, firstMarked);
            links.put(firstMarked, hitUnit);

            // 在两者之间显示红色连线（通过特效，初次建立时的特效）
            Fx.chainLightning.at(firstMarked.x, firstMarked.y, 0, linkColor, hitUnit);
            // 额外红色粒子爆发在两者身上
            Fx.sparkShoot.at(firstMarked.x, firstMarked.y, 0, RED);
            Fx.sparkShoot.at(hitUnit.x, hitUnit.y, 0, RED);
            Fx.hitLancer.at(firstMarked.x, firstMarked.y);
            Fx.hitLancer.at(hitUnit.x, hitUnit.y);
        }
    }

    /** 查找被标记且最接近指定单位的敌人 */
    private Unit findMarkedEnemy(Unit exclude) {
        Unit[] result = {null};
        float[] bestDst = {Float.MAX_VALUE};

        // 使用迭代器遍历ObjectSet
        for (Unit u : markedUnits) {
            if (u == exclude) continue;
            if (!u.isValid() || u.dead) {
                // 清理无效标记
                markedUnits.remove(u);
                continue;
            }

            float dst = u.dst(exclude);
            if (dst < linkRange && dst < bestDst[0]) {
                bestDst[0] = dst;
                result[0] = u;
            }
        }

        return result[0];
    }

    /** 查找最近的敌人（用于子弹命中检测） */
    private Unit findNearestEnemy(Bullet b, float x, float y, float range) {
        Unit[] result = {null};
        float[] bestDst = {Float.MAX_VALUE};

        Groups.unit.intersect(x - range, y - range, range * 2, range * 2, u -> {
            if (u.team == b.team) return;
            if (u.dead || !u.isValid()) return;

            float dst = u.dst(x, y);
            if (dst < range && dst < bestDst[0]) {
                bestDst[0] = dst;
                result[0] = u;
            }
        });

        return result[0];
    }

    /** 清理已死亡或无效的标记和链接（在更新循环中调用） */
    public static void cleanup() {
        // 清理无效标记
        Seq<Unit> toRemoveMark = new Seq<>();
        for (Unit u : markedUnits) {
            if (!u.isValid() || u.dead) {
                toRemoveMark.add(u);
            }
        }
        for (Unit u : toRemoveMark) {
            markedUnits.remove(u);
        }

        // 清理无效链接
        Seq<Unit> toRemoveLink = new Seq<>();
        for (ObjectMap.Entry<Unit, Unit> entry : links.entries()) {
            Unit a = entry.key;
            Unit b = entry.value;
            if (!a.isValid() || a.dead || !b.isValid() || b.dead) {
                toRemoveLink.add(a);
            }
        }
        for (Unit u : toRemoveLink) {
            Unit partner = links.get(u);
            links.remove(u);
            if (partner != null) {
                links.remove(partner);
            }
        }
    }

    /**
     * 绘制所有当前链接和标记（在渲染阶段调用）
     * 链接建立后，标记圈会保留
     * 渲染在 Layer.shields 层
     */
    public static void drawLinks() {
        ObjectSet<Unit> drawn = new ObjectSet<>();
        ObjectSet<Unit> linkedUnits = new ObjectSet<>();

        // 先收集所有在链接中的单位
        for (ObjectMap.Entry<Unit, Unit> entry : links.entries()) {
            Unit a = entry.key;
            Unit b = entry.value;
            if (a != null && a.isValid() && !a.dead) {
                linkedUnits.add(a);
            }
            if (b != null && b.isValid() && !b.dead) {
                linkedUnits.add(b);
            }
        }

        // 绘制所有链接线 - 在护盾层
        for (ObjectMap.Entry<Unit, Unit> entry : links.entries()) {
            Unit a = entry.key;
            Unit b = entry.value;
            if (a == null || b == null || drawn.contains(a) || drawn.contains(b)) continue;
            if (!a.isValid() || !b.isValid()) continue;

            drawn.add(a);
            drawn.add(b);

            // 绘制主要连线 - 红色粗线
            Draw.z(Layer.shields);
            Lines.stroke(4f, RED);
            Lines.line(a.x, a.y, b.x, b.y);

            // 绘制发光效果 - 半透明细线
            Draw.z(Layer.shields);
            Lines.stroke(2f, RED_ALPHA);
            Lines.line(a.x, a.y, b.x, b.y);

            // 在敌人身上持续显示红色粒子效果
            if (Time.time % 3 == 0) {
                Fx.hitLancer.at(a.x, a.y);
                Fx.hitLancer.at(b.x, b.y);
            }
        }

        // 绘制标记圈 - 在护盾层
        // 1. 绘制等待配对的标记（在 markedUnits 中）
        for (Unit u : markedUnits) {
            if (!u.isValid() || u.dead) continue;
            drawMarkCircle(u);
        }

        // 2. 绘制已链接单位的标记圈（在 links 中）
        for (Unit u : linkedUnits) {
            if (!u.isValid() || u.dead) continue;
            // 避免重复绘制（如果已经在 markedUnits 中）
            if (markedUnits.contains(u)) continue;
            drawMarkCircle(u);
        }
    }

    /** 绘制单个标记圈 - 在护盾层 */
    private static void drawMarkCircle(Unit u) {
        if (u == null || !u.isValid() || u.dead) return;

        // 主标记圈 - 红色实线
        Draw.z(Layer.shields);
        Lines.stroke(2.5f, RED);
        Lines.circle(u.x, u.y, 14f);

        // 外圈发光效果 - 半透明
        Draw.z(Layer.shields);
        float pulse = 1f + 0.15f * (float)Math.sin(Time.time / 25f);
        Lines.stroke(1.5f, RED_ALPHA);
        Lines.circle(u.x, u.y, 14f * pulse);

        // 内圈 - 更细的亮线
        Draw.z(Layer.shields);
        Lines.stroke(1f, MARK_COLOR);
        Lines.circle(u.x, u.y, 10f);

        // 标记上的粒子效果（间隔生成）
        if (Time.time % 4 == 0) {
            Fx.sparkShoot.at(u.x, u.y, 0, RED);
        }
    }

    /** 获取链接状态（用于调试或UI显示） */
    public static String getStatus() {
        int linkedCount = 0;
        for (ObjectMap.Entry<Unit, Unit> entry : links.entries()) {
            if (entry.key != null && entry.key.isValid()) {
                linkedCount++;
            }
        }
        return "标记中: " + markedUnits.size + ", 已链接单位: " + linkedCount;
    }
}