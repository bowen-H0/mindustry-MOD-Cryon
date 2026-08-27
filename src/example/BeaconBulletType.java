package example;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.ArtilleryBulletType;
import mindustry.game.Team;
import mindustry.gen.Bullet;
import mindustry.graphics.Layer;

/**
 * BeaconBulletType —— 轨道打击标记弹。
 * 基于 ArtilleryBulletType:弹道会精确落在开火瞬间选定的目标点上,
 * 弹体本身完全不绘制,玩家看到落点警告标记,延迟后蓝色光束落下造成范围伤害。
 */
public class BeaconBulletType extends ArtilleryBulletType {

    public float strikeDelay = 90f;
    public float strikeRadius = 80f;
    public float strikeDamage = 220f;
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

        public float progress() {
            return Mathf.clamp((Time.time - startTime) / delay);
        }
    }

    public static final Seq<Marker> markers = new Seq<>();

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
    }

    @Override
    public void update(Bullet b) {
        super.update(b);

        if (b.time < 1f) {
            mark(b.team, b.aimX, b.aimY);
            b.remove();
        }
    }

    @Override
    public void hit(Bullet b, float x, float y) {
        mark(b.team, b.aimX, b.aimY);
    }

    protected void mark(Team team, float x, float y) {
        Marker m = new Marker(x, y, strikeRadius, strikeDelay, markColor, team);
        markers.add(m);
        Fx.circleColorSpark.at(x, y, markColor);

        Time.run(strikeDelay, () -> {
            markers.remove(m);
            strike(team, x, y);
        });
    }

    protected void strike(Team team, float x, float y) {
        BeaconFx.beam.at(x, y, 0f, beamColor);
        BeaconFx.impact.at(x, y, 0f, beamColor);

        Units.nearbyEnemies(team, x, y, strikeRadius, unit -> {
            if (unit.isGrounded()) { // 只伤害地面单位
                unit.damage(strikeDamage);
            }
        });

        Vars.indexer.eachBlock(null, x, y, strikeRadius,
                building -> building.team != team,
                building -> building.damage(strikeDamage)
        );

        Fx.hitBulletColor.at(x, y, 0f, beamColor);

        Effect.shake(4f, 4f, x, y);
    }

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