package example;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.ArtilleryBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.game.Team;
import mindustry.gen.Bullet;
import mindustry.graphics.Layer;

/**
 * BeaconBulletType —— 轨道打击标记弹。
 * 基于 ArtilleryBulletType:弹道会精确落在开火瞬间选定的目标点上,
 * 不依赖真实碰撞检测,因此天然满足"直接决定落点"的需求。
 * 弹体本身完全不绘制(draw 被清空),玩家看不到任何飞行中的子弹,
 * 只会看到落点出现警告标记,延迟后蓝色光束落下造成范围伤害。
 */
public class BeaconBulletType extends ArtilleryBulletType {

    /** 标记到光束落下之间的延迟(tick, 60tick = 1秒) */
    public float strikeDelay = 90f;
    /** 光束打击的伤害半径 */
    public float strikeRadius = 80f;
    /** 光束打击造成的伤害 */
    public float strikeDamage = 220f;
    /** 警告标记 / 光束整体色调 */
    public Color markColor = Color.valueOf("57c2ff");
    public Color beamColor = Color.valueOf("bfe9ff");

    public static class Marker {
        public float x, y, radius, delay, startTime;
        public Color color;
        public Team team;

        Marker(float x, float y, float radius, float delay, Color color, Team team) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.delay = delay;
            this.color = color;
            this.team = team;
            this.startTime = Time.time;
        }

        /** 0 -> 1, 距离光束落下还剩多少进度 */
        public float progress() {
            return Mathf.clamp((Time.time - startTime) / delay);
        }
    }

    /** 全局待触发的标记列表,供绘制钩子读取 */
    public static final Seq<Marker> markers = new Seq<>();

    /** speed 决定"决定落点"前的极短过渡时间(弹体不可见,几乎无感知);damage 参数不使用 */
    public BeaconBulletType(float speed, float unusedDamage) {
        super(speed, 0f);
        damage = 0f;
        splashDamage = 0f;
        collides = false;
        hittable = true;
        absorbable = false;
        pierce = false;
        despawnEffect = Fx.none;
        hitEffect = Fx.none;
        if (lifetime <= 0f) lifetime = 20f;
    }

    @Override
    public void draw(Bullet b) {
        // 完全不绘制弹体本身 —— 不出现"看得见的子弹"
    }

    @Override
    public void update(Bullet b) {
        super.update(b);

        // 子弹一生成就立即标记（第一次 update 就触发）
        if (b.time < 1f) {  // 只在第一帧执行
            Log.info("[Beacon] Instant mark at aim=(@, @)", b.aimX, b.aimY);
            mark(b.team, b.aimX, b.aimY);
            b.remove();  // 立即销毁子弹
        }
    }


    @Override
    public void hit(Bullet b, float x, float y) {
        Log.info("[Beacon] hit() called! aim=(@, @), hit=(@, @)", b.aimX, b.aimY, x, y);
        // 标记目标点
        mark(b.team, b.aimX, b.aimY);  // 使用 aim 坐标而不是 hit 坐标
    }

    protected void mark(Team team, float x, float y) {
        Log.info("[Beacon] mark() called at (@, @)", x, y);
        Marker m = new Marker(x, y, strikeRadius, strikeDelay, markColor, team);
        markers.add(m);
        Fx.circleColorSpark.at(x, y, markColor);

        Time.run(strikeDelay, () -> {
            markers.remove(m);
            strike(team, x, y);
        });
    }


    protected void strike(Team team, float x, float y) {
        Log.info("[Beacon] Strike at (@, @), damage=@, radius=@", x, y, strikeDamage, strikeRadius);

        // 1. 你的自定义特效
        BeaconFx.beam.at(x, y, 0f, beamColor);
        BeaconFx.impact.at(x, y, 0f, beamColor);

        // 2. 对单位造成伤害（参考 MeltingDrillInjector）
        Units.nearbyEnemies(team, x, y, strikeRadius, unit -> {
            unit.damage(strikeDamage);
            Log.info("[Beacon] Damaged unit: @, health now: @", unit, unit.health);
        });

        // 3. 对方块造成伤害（参考 MeltingDrillInjector）
        Vars.indexer.eachBlock(null, x, y, strikeRadius,
                building -> building.team != team,
                building -> {
                    building.damage(strikeDamage);
                    Log.info("[Beacon] Damaged building: @, health now: @", building, building.health);
                }
        );

        // 4. 使用内置爆炸特效（参考 MeltingDrillInjector 的 explosionEffect）
        Fx.blastExplosion.at(x, y);  // 大爆炸
        Fx.massiveExplosion.at(x, y, 0f, beamColor);

        // 5. 附加火焰效果（参考 MeltingDrillInjector 的 fireEffect）
        for (int i = 0; i < 8; i++) {
            Fx.fireballsmoke.at(
                    x + Mathf.range(strikeRadius * 0.8f),
                    y + Mathf.range(strikeRadius * 0.8f)
            );
        }

        // 6. 震动效果
        Effect.shake(6f, 6f, x, y);

        Log.info("[Beacon] Strike complete!");
    }

    /** 在绘制钩子里调用,渲染所有待落下的警告标记与倒计时圈 */
    public static void drawMarkers() {
        for (Marker m : markers) {
            float progress = m.progress();

            Draw.z(Layer.flyingUnit + 1f);
            Draw.color(m.color);

            Lines.stroke(2f);
            Draw.alpha(0.35f + 0.45f * Mathf.absin(Time.time, 4f - progress * 2.5f, 1f));
            Lines.circle(m.x, m.y, m.radius * (1.15f - progress * 0.15f));

            float cross = m.radius * (1f - progress) + 4f;
            Draw.alpha(0.6f);
            Lines.line(m.x - cross, m.y, m.x + cross, m.y);
            Lines.line(m.x, m.y - cross, m.x, m.y + cross);

            Draw.reset();
        }
    }
}