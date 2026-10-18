package cryon.Type;

import arc.graphics.Blending;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.Vec2;
import arc.util.Time;
import mindustry.entities.units.AIController;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.UnitType;

/** Disposable, synchronized health-bearing units. Damage uses ordinary engine collisions. */
public class DecoyUnitType extends UnitType{
    public DecoyUnitType(String name, boolean airborne){
        super(name);
        constructor = TimedKillUnit::create;
        controller = unit -> new BodyguardAI();
        flying = airborne;
        hovering = true;
        canDrown = false;
        health = 45;
        armor = 0;
        hitSize = 12;
        speed = 4.5f;
        accel = 0.25f;
        drag = 0.08f;
        lifetime = 150;
        useUnitCap = false;
        isEnemy = false;
        hidden = true;
        playerControllable = logicControllable = false;
        allowedInPayloads = false;
        createWreck = createScorch = false;
        physics = false;
        engineSize = 0;
        deathSound = Sounds.none;
        deathShake = 0;
        deathExplosionEffect = BioCrawlerFx.contact;
        targetPriority = 2;
    }

    @Override public Unit create(Team team){
        Unit unit = super.create(team);
        ((TimedKillc)unit).lifetime(lifetime);
        return unit;
    }

    @Override public void draw(Unit unit){
        float z = Draw.z();
        // 剩余寿命:最后 25% 开始闪烁,提示即将消失
        float life = 1f - ((TimedKillc)unit).time() / Math.max(lifetime, 1f);
        float blink = life < 0.25f ? 0.5f + 0.5f * Mathf.absin(Time.time, 3f, 1f) : 1f;
        float pulse = 1f + 0.1f * Mathf.absin(Time.time, 6f, 1f);

        Draw.z(unit.isFlying() ? Layer.flyingUnitLow : Layer.groundUnit);

        // 发光:叠加混合的两层光晕(不依赖地图是否开启光照)
        Draw.blend(Blending.additive);
        Draw.color(BioCrawlerFx.orange, 0.28f * blink);
        Fill.circle(unit.x, unit.y, 16f * pulse);
        Draw.color(BioCrawlerFx.red, 0.4f * blink);
        Fill.circle(unit.x, unit.y, 9f * pulse);
        Draw.blend();

        // 本体:红外层 → 橙内层 → 浅橙白芯
        Draw.color(BioCrawlerFx.red, blink);
        Drawf.tri(unit.x, unit.y, 11, 14, unit.rotation);
        Draw.color(BioCrawlerFx.orange, blink);
        Drawf.tri(unit.x, unit.y, 5, 8, unit.rotation);
        Draw.color(BioCrawlerFx.hot, blink);
        Drawf.tri(unit.x, unit.y, 2.5f, 5, unit.rotation);
        Lines.stroke(1);
        Draw.color(BioCrawlerFx.orange, blink);
        Lines.poly(unit.x, unit.y, 6, 5, unit.rotation);

        // 开启光照的地图里,额外给一圈真实光源
        Drawf.light(unit.x, unit.y, 42f * pulse, BioCrawlerFx.orange, 0.7f * blink);

        Draw.reset();
        Draw.z(z);
    }

    /** Steer the body to a predicted interception point; never modify an incoming bullet. */
    public static class BodyguardAI extends AIController{
        private final Vec2 destination = new Vec2();
        private Bullet incoming;
        private float best;

        @Override public void updateUnit(){
            incoming = null;
            best = Float.MAX_VALUE;
            Groups.bullet.intersect(unit.x - 140, unit.y - 140, 280, 280, bullet -> {
                if(bullet.team == unit.team || !bullet.type.collides || !unit.within(bullet, 140)
                        || (unit.isFlying() ? !bullet.type.collidesAir : !bullet.type.collidesGround)) return;
                float score = unit.dst2(bullet);
                if(score < best){ best = score; incoming = bullet; }
            });
            if(incoming == null){
                unit.moveAt(destination.trns(unit.rotation, unit.speed() * 0.25f));
                return;
            }
            float arrival = Math.min(12f, unit.dst(incoming) / Math.max(unit.speed() + incoming.vel.len(), 0.1f));
            destination.set(incoming.x + incoming.vel.x * arrival, incoming.y + incoming.vel.y * arrival);
            unit.lookAt(destination);
            unit.moveAt(destination.sub(unit.x, unit.y).limit(unit.speed()));
        }
    }
}