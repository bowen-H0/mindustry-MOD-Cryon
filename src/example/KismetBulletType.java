package example;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.geom.Position;
import arc.math.geom.Vec2;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.bullet.ArtilleryBulletType;
import mindustry.game.EventType;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import arc.Events;

/**
 * 命运绑定子弹 - 纯辅助标记工具
 * 子弹不飞行,发射瞬间直接在落点判定命中(同 BeaconBulletType 原理)。
 * 命中敌人后加入一个"绑定组"(最多10个成员),组内任意成员受到伤害时,
 * 会按比例分享给组内其他所有存活成员。
 */
public class KismetBulletType extends ArtilleryBulletType {

    /** 寻找同伴/同组目标的搜索半径 */
    public float linkRange = 120f;
    /** 链接颜色 */
    public Color linkColor = Color.valueOf("ff0000");
    /** 伤害共享比例 */
    public float shareFraction = 0.8f;
    /** 单个绑定组最多容纳的敌人数量 */
    public static final int MAX_GROUP_SIZE = 10;

    /** 一个绑定组:内部成员互相共享伤害 */
    public static class LinkGroup {
        public Seq<Unit> members = new Seq<>();
    }

    /** 还未配对、独自等待加入/组成绑定组的敌人 */
    public static final Seq<Unit> markedUnits = new Seq<>();
    /** 所有当前存在的绑定组 */
    public static final Seq<LinkGroup> groups = new Seq<>();
    /** 敌人 -> 所在绑定组,便于快速查询 */
    public static final ObjectMap<Unit, LinkGroup> unitGroup = new ObjectMap<>();
    /** 防止伤害转发死循环 */
    private static final Seq<Unit> forwarding = new Seq<>();

    public static float shareFractionStatic = 0.8f;

    private static final Color RED = Color.valueOf("ff0000");
    private static final Color RED_ALPHA = Color.valueOf("ff000080");
    private static final Color MARK_COLOR = Color.valueOf("ff0000aa");

    /** 红色激光打击特效:从 a 点瞬间射向 b 点,代表一次绑定/共伤判定 */
    public static final Effect kismetLaser = new Effect(20f, 300f, e -> {
        if(!(e.data instanceof Position p)) return;
        float tx = p.getX(), ty = p.getY();

        Draw.color(Color.white, RED, e.fin());
        Draw.alpha(e.fout());
        Lines.stroke(3f * e.fout());
        Lines.line(e.x, e.y, tx, ty);

        Drawf.light(e.x, e.y, tx, ty, 12f * e.fout() + 4f, RED, 0.6f * e.fout());

        Draw.reset();
    }).followParent(false).rotWithParent(false);

    public KismetBulletType(float speed, float damage) {
        super(speed, 0f);
        this.damage = 0f;
        this.splashDamage = 0f;
        this.collides = false;
        this.absorbable = false;
        this.hittable = false;
        this.despawnEffect = Fx.none;
        this.hitEffect = Fx.none;
        if (lifetime <= 0f) lifetime = 20f;
    }

    static {
        Events.on(EventType.UnitDamageEvent.class, e -> {
            Unit victim = e.unit;
            if (victim == null || forwarding.contains(victim)) return;

            LinkGroup group = unitGroup.get(victim);
            if (group == null) return;

            float baseDamage = e.bullet != null ? e.bullet.damage : 0f;
            if (baseDamage <= 0.01f) return;

            float shared = baseDamage * shareFractionStatic;
            if (shared <= 0.01f) return;

            forwarding.add(victim);
            for (Unit member : group.members) {
                if (member == victim || forwarding.contains(member)) continue;
                if (!member.isValid() || member.dead) continue;

                forwarding.add(member);
                member.damage(shared);
                forwarding.remove(member);

                kismetLaser.at(victim.x, victim.y, 0f, RED, member);
            }
            forwarding.remove(victim);
        });
    }

    @Override
    public void init(Bullet b) {
        super.init(b);
        shareFractionStatic = shareFraction;
    }

    @Override
    public void draw(Bullet b) {
        // 不绘制弹体,子弹瞬间到达落点
    }

    @Override
    public void update(Bullet b) {
        super.update(b);

        if (b.time < 1f) {
            resolve(b, b.aimX, b.aimY);
            b.remove();
        }
    }

    /** 子弹销毁瞬间在落点执行绑定逻辑 */
    protected void resolve(Bullet b, float x, float y) {
        kismetLaser.at(b.x, b.y, 0f, linkColor, new Vec2(x, y));

        Unit hitUnit = findNearestEnemy(b, x, y, 8f);
        if (hitUnit == null || !hitUnit.isValid() || hitUnit.dead) return;

        // 已经在某个组里,不重复处理
        if (unitGroup.containsKey(hitUnit)) return;

        // 优先尝试加入附近一个未满的现有组
        LinkGroup nearGroup = findNearbyGroup(hitUnit);
        if (nearGroup != null) {
            Unit anchor = nearGroup.members.peek();
            nearGroup.members.add(hitUnit);
            unitGroup.put(hitUnit, nearGroup);

            kismetLaser.at(anchor.x, anchor.y, 0f, linkColor, hitUnit);
            Fx.sparkShoot.at(hitUnit.x, hitUnit.y, 0, RED);
            return;
        }

        // 没有现成的组,尝试和一个正在等待配对的独立敌人组队
        Unit partner = findMarkedEnemy(hitUnit);
        if (partner != null) {
            markedUnits.remove(partner, true);

            LinkGroup group = new LinkGroup();
            group.members.add(partner, hitUnit);
            groups.add(group);
            unitGroup.put(partner, group);
            unitGroup.put(hitUnit, group);

            kismetLaser.at(partner.x, partner.y, 0f, linkColor, hitUnit);
            Fx.sparkShoot.at(partner.x, partner.y, 0, RED);
            Fx.sparkShoot.at(hitUnit.x, hitUnit.y, 0, RED);
        } else {
            // 没有可配对的目标,单独标记等待
            markedUnits.add(hitUnit);
            Fx.sparkShoot.at(hitUnit.x, hitUnit.y, 0, RED);
        }
    }

    /** 在链接范围内寻找一个未满的现有绑定组(以组内任意成员为参照距离) */
    private LinkGroup findNearbyGroup(Unit target) {
        LinkGroup best = null;
        float bestDst = Float.MAX_VALUE;

        for (LinkGroup group : groups) {
            if (group.members.size >= MAX_GROUP_SIZE) continue;

            for (Unit member : group.members) {
                if (!member.isValid() || member.dead) continue;
                float dst = member.dst(target);
                if (dst < linkRange && dst < bestDst) {
                    bestDst = dst;
                    best = group;
                }
            }
        }
        return best;
    }

    /** 查找范围内正在等待配对的独立敌人 */
    private Unit findMarkedEnemy(Unit exclude) {
        Unit result = null;
        float bestDst = Float.MAX_VALUE;

        for (Unit u : markedUnits) {
            if (u == exclude || !u.isValid() || u.dead) continue;

            float dst = u.dst(exclude);
            if (dst < linkRange && dst < bestDst) {
                bestDst = dst;
                result = u;
            }
        }
        return result;
    }

    /** 查找落点附近最近的敌人 */
    private Unit findNearestEnemy(Bullet b, float x, float y, float range) {
        Unit[] result = {null};
        float[] bestDst = {Float.MAX_VALUE};

        Groups.unit.intersect(x - range, y - range, range * 2, range * 2, u -> {
            if (u.team == b.team || u.dead || !u.isValid()) return;

            float dst = u.dst(x, y);
            if (dst < range && dst < bestDst[0]) {
                bestDst[0] = dst;
                result[0] = u;
            }
        });

        return result[0];
    }

    /** 清理死亡/无效的标记与绑定组成员(建议在更新循环中定期调用) */
    public static void cleanup() {
        markedUnits.removeAll(u -> !u.isValid() || u.dead);

        for (int i = groups.size - 1; i >= 0; i--) {
            LinkGroup group = groups.get(i);
            group.members.removeAll(u -> {
                boolean invalid = !u.isValid() || u.dead;
                if (invalid) unitGroup.remove(u);
                return invalid;
            });

            if (group.members.size <= 1) {
                for (Unit u : group.members) unitGroup.remove(u);
                groups.remove(i);
            }
        }
    }

    /** 绘制所有绑定组连线与等待中的标记圈 */
    public static void drawLinks() {
        for (LinkGroup group : groups) {
            Seq<Unit> members = group.members;
            for (int i = 0; i < members.size - 1; i++) {
                Unit a = members.get(i);
                Unit b = members.get(i + 1);
                if (!a.isValid() || !b.isValid() || a.dead || b.dead) continue;

                Draw.z(Layer.shields);
                Lines.stroke(4f, RED);
                Lines.line(a.x, a.y, b.x, b.y);

                Draw.z(Layer.shields);
                Lines.stroke(2f, RED_ALPHA);
                Lines.line(a.x, a.y, b.x, b.y);
            }

            for (Unit u : members) {
                if (u.isValid() && !u.dead) drawMarkCircle(u);
            }
        }

        for (Unit u : markedUnits) {
            if (u.isValid() && !u.dead) drawMarkCircle(u);
        }
    }

    private static void drawMarkCircle(Unit u) {
        Draw.z(Layer.shields);
        Lines.stroke(2.5f, RED);
        Lines.circle(u.x, u.y, 14f);

        Draw.z(Layer.shields);
        float pulse = 1f + 0.15f * (float) Math.sin(Time.time / 25f);
        Lines.stroke(1.5f, RED_ALPHA);
        Lines.circle(u.x, u.y, 14f * pulse);

        Draw.z(Layer.shields);
        Lines.stroke(1f, MARK_COLOR);
        Lines.circle(u.x, u.y, 10f);
    }

    public static String getStatus() {
        return "等待中: " + markedUnits.size + ", 绑定组数: " + groups.size;
    }
}