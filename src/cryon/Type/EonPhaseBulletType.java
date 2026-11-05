package cryon.Type;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import cryon.Content.EonUnit;
import cryon.Fx.EonCombatFx;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.gen.*;

/** Carrier creates a stationary, timed collapse entity; no delayed tasks survive world resets. */
public class EonPhaseBulletType extends BasicBulletType{
    public static final Color phase = Color.valueOf("bda6ff");
    public static final Effect blast = new Effect(65f, 1100f, e -> {
        Draw.color(Color.white, phase, e.fin());
        Lines.stroke(12f * e.fout());
        Lines.circle(e.x, e.y, e.rotation * e.finpow());
        Lines.stroke(4f * e.fout());
        Lines.circle(e.x, e.y, e.rotation * 0.65f * e.finpow());
        Angles.randLenVectors(e.id, 30, e.rotation * e.fin(), (x, y) ->
            Fill.square(e.x + x, e.y + y, 6f * e.fout(), 45f));
        Draw.reset();
    });

    public static void burst(Bullet b, float radius, float amount, boolean mark){
        areaDamage(b, radius, amount, mark);
        blast.at(b.x, b.y, radius, phase);
        Effect.shake(14f, 25f, b.x, b.y);
        Sounds.explosion.at(b.x, b.y, 0.65f, 1.8f);
    }

    public static void areaDamage(Bullet b, float radius, float amount, boolean mark){
        if(Vars.net.client()) return;
        float scaled = amount * b.damageMultiplier();
        Units.nearbyEnemies(b.team, b.x, b.y, radius, u -> {
            if(!u.within(b, radius + u.hitSize / 2f) || !u.targetable(b.team)) return;
            // Direct hits alone consume critical marks; blasts taper toward their perimeter.
            float edgeDistance = Math.max(0f, u.dst(b) - u.hitSize / 2f);
            float falloff = Mathf.lerp(1f, 0.25f, Mathf.clamp(edgeDistance / radius));
            u.damage(scaled * falloff);
            EonUnit.phaseDamage.trackDamage(u);
            if(mark) u.apply(EonUnit.phaseDamage, 240f);
        });
        Units.nearbyBuildings(b.x, b.y, radius, build -> {
            if(!cryon.Features.MapAlliances.allied(build.team, b.team) && build.block.targetable)
                build.damage(b.team, scaled * Mathf.lerp(1f, 0.25f, Mathf.clamp(build.dst(b) / radius)));
        });
    }

    public static class GravityWellState{
        final arc.struct.Seq<Unit> captured = new arc.struct.Seq<>();
    }

    public static void pull(Bullet b, float radius, float strength, boolean imprison){
        if(Vars.net.client()) return;
        GravityWellState well;
        if(imprison){
            if(!(b.data instanceof GravityWellState)) b.data = new GravityWellState();
            well = (GravityWellState)b.data;
        }else{
            well = null;
        }
        float prisonRadius = radius * 0.38f;
        Units.nearbyEnemies(b.team, b.x, b.y, radius, u -> {
            if(!u.within(b, radius) || !u.targetable(b.team)) return;
            u.vel.add(Tmp.v1.set(b.x - u.x, b.y - u.y)
                .limit(strength * Time.delta * (0.25f + 1f - u.dst(b) / radius)));
            if(well != null && u.within(b, prisonRadius)) well.captured.addUnique(u);
        });
        if(well != null){
            well.captured.removeAll(u -> !u.isValid() || cryon.Features.MapAlliances.allied(u.team, b.team));
            for(Unit unit : well.captured){
                // A spatial leash, not a status: movement/aim/reload/weapon logic remain active.
                float dx = unit.x - b.x, dy = unit.y - b.y;
                float distance = Mathf.sqrt(dx * dx + dy * dy);
                if(distance < 0.001f) continue;
                float nx = dx / distance, ny = dy / distance;
                if(distance > prisonRadius) unit.set(b.x + nx * prisonRadius, b.y + ny * prisonRadius);
                float outward = unit.vel.x * nx + unit.vel.y * ny;
                if(outward > 0f) unit.vel.sub(nx * outward, ny * outward);
            }
        }
    }

    private final BasicBulletType collapse = new BasicBulletType(0f, 0f){
        {
            lifetime = 150f;
            collides = collidesTiles = false;
            absorbable = hittable = reflectable = false;
            keepVelocity = false;
            despawnHit = false;
            hitEffect = despawnEffect = shootEffect = smokeEffect = Fx.none;
            drawSize = 1000f;
        }
        @Override public void update(Bullet b){
            super.update(b);
            pull(b, 220f, 0.8f, true);
            if(b.timer(1, 20f)) areaDamage(b, 130f, 220f, false);
        }
        @Override public void draw(Bullet b){
            float radius = Mathf.lerp(230f, 14f, b.fin());
            Draw.blend(arc.graphics.Blending.additive);
            Fill.light(b.x, b.y, 48, radius * 1.3f, Tmp.c1.set(phase).a(0.18f + b.fin() * 0.3f), Tmp.c2.set(phase).a(0f));
            for(int i = 0; i < 14; i++){
                float angle = i * 137.5f + b.time * (1.2f + i * 0.1f);
                float distance = Mathf.mod(i * 21f - b.time * 2f, 230f);
                Draw.color(phase);
                Lines.stroke(1.4f);
                Lines.lineAngle(b.x + Angles.trnsx(angle, distance), b.y + Angles.trnsy(angle, distance), angle + 180f, 12f + b.fin() * 22f);
            }
            Draw.blend();
            Draw.color(phase);
            Lines.stroke(3f);
            for(int i = 0; i < 3; i++) Lines.arc(b.x, b.y, radius + i * 12f, 0.22f, b.time * 3f + i * 120f);
            Draw.color(Color.black);
            Fill.circle(b.x, b.y, 14f + b.fin() * 28f);
            Draw.color(Color.white, phase, b.fin());
            Lines.stroke(3f + b.fin() * 3f);
            Lines.circle(b.x, b.y, 14f + b.fin() * 28f);
            Draw.reset();
        }
        @Override public void despawned(Bullet b){
            areaDamage(b, 210f, 16000f, true);
            EonCombatFx.finalBlast.at(b.x, b.y, 360f);
            Effect.shake(32f, 65f, b.x, b.y);
            cryon.Features.EonAudio.detonation.at(b.x, b.y, 1f, 1.5f);
        }
    };

    public EonPhaseBulletType(){
        super(14f, 0f);
        lifetime = 80f;
        width = 22f; height = 60f;
        backColor = trailColor = hitColor = phase;
        frontColor = Color.white;
        trailLength = 50; trailWidth = 7f;
        chargeEffect = EonCombatFx.charge;
        shootEffect = EonCombatFx.muzzle;
        smokeEffect = Fx.shootBigSmoke;
        lightRadius = 140f;
        lightOpacity = 0.85f;
        splashDamage = 0f;
        absorbable = hittable = reflectable = false;
        despawnHit = true;
        hitEffect = despawnEffect = Fx.none;
        hitSound = Sounds.none;
    }

    @Override public void init(){ super.init(); collapse.init(); }
    @Override public void hit(Bullet b, float x, float y, boolean createFrags){
        if(b.data == Boolean.TRUE) return;
        b.data = Boolean.TRUE;
        areaDamage(b, 140f, 7000f, true);
        EonCombatFx.initial.at(x, y, 260f);
        Effect.shake(22f, 40f, x, y);
        cryon.Features.EonAudio.impact.at(x, y, 1f, 1.2f);
        collapse.create(b.owner, b.team, x, y, 0f);
    }
}
