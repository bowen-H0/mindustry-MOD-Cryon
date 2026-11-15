package cryon.Type;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import cryon.Features.MapAlliances;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.*;
import mindustry.graphics.Drawf;

/** Abyss: a lightning mass that unfolds into a damaging field, then a rolling detonation. */
public class AbyssLightningBulletType extends BulletType{
    public static final Color abyssColor = Color.valueOf("00d8d8");
    public final boolean divine;
    public final Color color;
    public final float dischargeInterval, dischargeDamage;
    public final int dischargeCount;
    private static final Effect thunderburst = new Effect(48f, 650f, e -> {
        Draw.color(Color.white, e.color, e.fin());
        Lines.stroke(7f * e.fout());
        Lines.circle(e.x, e.y, e.rotation * e.finpow());
        Lines.stroke(2.5f * e.fout());
        Lines.circle(e.x, e.y, e.rotation * 0.65f * e.finpow());
        Angles.randLenVectors(e.id, 20, e.rotation * e.fin(), (x, y) ->
            Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 18f * e.fout()));
        Draw.reset();
    });
    public final BulletType discharge = new LightningPoint();
    public final LightningField field;

    public AbyssLightningBulletType(){ this(false); }

    public AbyssLightningBulletType(boolean divine){
        super(divine ? 8f : 5f, divine ? 1600f : 360f);
        this.divine = divine;
        color = divine ? ThemisLawFx.gold : abyssColor;
        field = new LightningField(divine);
        dischargeInterval = divine ? 3f : 18f;
        dischargeDamage = divine ? 120f : 35f;
        dischargeCount = divine ? 2 : 1;
        lifetime = divine ? 70f : 65f;
        hitSize = divine ? 28f : 16f;
        keepVelocity = false;
        despawnHit = true;
        hitColor = lightColor = trailColor = color;
        hitEffect = Fx.hitLancer;
        despawnEffect = Fx.none;
        hitSound = Sounds.shootArc;
        trailLength = divine ? 16 : 7;
        trailWidth = divine ? 7f : 3f;
        drawSize = 160f;
        status = StatusEffects.shocked;
        statusDuration = 60f;
        splashDamage = divine ? 1200f : 180f;
        splashDamageRadius = divine ? 100f : 40f;
        shootEffect = Fx.shootTitan;
        smokeEffect = Fx.shootSmokeSmite;
        chargeEffect = Fx.hitLancer;
        hitShake = divine ? 12f : 3f;
    }

    @Override public void update(Bullet b){
        super.update(b);
        // Themis retains the full storm; Abyss emits only occasional short discharges.
        if(!Vars.net.client() && b.timer(2, dischargeInterval)){
            for(int i = 0; i < dischargeCount; i++)
                Lightning.create(discharge, b.team, color, dischargeDamage * b.damageMultiplier(), b.x, b.y,
                    b.rotation() + (i == 0 ? 65f : -65f), divine ? 22 : 8);
        }
    }

    @Override public void hit(Bullet b, float x, float y, boolean fragments){
        super.hit(b, x, y, fragments);
        // A collision and end-of-life callback must never produce two fields.
        if(b.fdata != 0f || Vars.net.client()) return;
        b.fdata = 1f;
        field.createNet(b.team, x, y, b.rotation(), b.damageMultiplier(), 1f, 1f);
    }

    @Override public boolean testCollision(Bullet b, Building build){
        return !MapAlliances.allied(b.team, build.team) && super.testCollision(b, build);
    }

    @Override public void hitEntity(Bullet b, Hitboxc entity, float health){
        if(entity instanceof Teamc && MapAlliances.allied(b.team, ((Teamc)entity).team())) return;
        super.hitEntity(b, entity, health);
    }

    @Override public void createSplashDamage(Bullet b, float x, float y){
        damageArea(b, x, y, splashDamageRadius, splashDamage * b.damageMultiplier());
    }

    private static void damageArea(Bullet b, float x, float y, float range, float amount){
        if(Vars.net.client()) return;
        Units.nearbyEnemies(b.team, x, y, range, unit -> {
            if(MapAlliances.allied(b.team, unit.team) || !unit.targetable(b.team)
                || !unit.within(x, y, range + unit.hitSize / 2f)) return;
            unit.damage(amount);
            unit.apply(StatusEffects.shocked, 90f);
        });
        Units.nearbyBuildings(x, y, range, build -> {
            if(!MapAlliances.allied(b.team, build.team) && build.block.targetable)
                build.damage(b.team, amount);
        });
    }

    @Override public void draw(Bullet b){
        if(b.trail != null) b.trail.draw(color, trailWidth);
        Draw.color(color, 0.18f);
        Fill.circle(b.x, b.y, divine ? 38f : 20f);
        if(divine) ThemisLawFx.halo(b.x, b.y, 48f, 0.8f);
        for(int i = 0; i < (divine ? 16 : 6); i++){
            float angle = i * 137.5f + b.time * 3f;
            float radius = Mathf.randomSeed(b.id * 71L + i, 5f, divine ? 32f : 16f);
            bolt(b.x + Angles.trnsx(angle, radius), b.y + Angles.trnsy(angle, radius),
                angle + b.time, divine ? 26f : 14f, b.id + i, (int)(b.time / 3f), 1f);
        }
        Draw.reset();
        Drawf.light(b.x, b.y, divine ? 120f : 60f, color, divine ? 0.8f : 0.45f);
    }

    private void bolt(float x, float y, float angle, float length, int seed, int phase, float alpha){
        for(int pass = 0; pass < 2; pass++){
            Draw.color(pass == 0 ? color : Color.white, alpha);
            Lines.stroke(pass == 0 ? 2.5f : 0.8f);
            float px = x, py = y;
            for(int i = 1; i <= 5; i++){
                float side = i == 5 ? 0f : Mathf.randomSeed(seed * 31L + phase * 17L + i, -length * 0.3f, length * 0.3f);
                float nx = x + Angles.trnsx(angle, length * i / 5f, side);
                float ny = y + Angles.trnsy(angle, length * i / 5f, side);
                Lines.line(px, py, nx, ny);
                px = nx; py = ny;
            }
        }
    }

    public class LightningField extends BulletType{
        public final float radius, chargeTime = 300f, chainInterval;
        public final float pulseInterval, damagePerSecond, blastDamage, blastRadius;
        public final float finalDamage;
        public final int pulseBranches, blastBranches;
        public final BulletType discharge = new LightningPoint();
        public final int nodeCount;

        public LightningField(boolean divine){
            super(0f, 1f);
            radius = divine ? 264f : 144f;
            chainInterval = divine ? 2f : 5f;
            pulseInterval = divine ? 12f : 36f;
            damagePerSecond = divine ? 1000f : 150f;
            blastDamage = divine ? 2100f : 300f;
            blastRadius = divine ? 100f : 45f;
            finalDamage = divine ? 4500f : 500f;
            nodeCount = divine ? 25 : 9;
            pulseBranches = divine ? 4 : 1;
            blastBranches = divine ? 3 : 0;
            lifetime = chargeTime + nodeCount * chainInterval + 1f;
            collides = collidesTiles = collidesAir = collidesGround = false;
            hittable = absorbable = reflectable = keepVelocity = false;
            hitEffect = despawnEffect = shootEffect = smokeEffect = Fx.none;
            drawSize = divine ? 850f : 380f;
        }

        public float nodeRadius(int index){
            if(!divine) return index == 0 ? 0f : index <= 4 ? 55f : 110f;
            return index == 0 ? 0f : index <= 6 ? 72f : index <= 14 ? 144f : 216f;
        }

        private float nodeAngle(Bullet b, int index){
            int start = !divine ? (index <= 4 ? 1 : 5) : index <= 6 ? 1 : index <= 14 ? 7 : 15;
            int count = !divine ? 4 : index <= 6 ? 6 : index <= 14 ? 8 : 10;
            return b.rotation() + (index - start) * 360f / count + start * 13f;
        }

        @Override public void update(Bullet b){
            // One area pass avoids multiplying damage where visual branches overlap.
            if(b.time < chargeTime && b.timer(1, pulseInterval)){
                areaDamage(b, b.x, b.y, radius * Mathf.clamp(b.time / 20f), damagePerSecond * pulseInterval / 60f);
                if(!Vars.net.client()){
                    for(int i = 0; i < pulseBranches; i++){
                        int node = 1 + ((int)(b.time / pulseInterval) * pulseBranches + i) % (nodeCount - 1);
                        float angle = nodeAngle(b, node), distance = nodeRadius(node) * Mathf.clamp(b.time / 20f);
                        Lightning.create(discharge, b.team, color, (divine ? 130f : 30f) * b.damage,
                            b.x + Angles.trnsx(angle, distance), b.y + Angles.trnsy(angle, distance), angle + 180f, divine ? 20 : 6);
                    }
                }
            }
            while(b.fdata < nodeCount && b.time >= chargeTime + b.fdata * chainInterval){
                int index = (int)b.fdata++;
                float angle = nodeAngle(b, index), distance = nodeRadius(index);
                float x = b.x + Angles.trnsx(angle, distance), y = b.y + Angles.trnsy(angle, distance);
                if(index == 0){
                    areaDamage(b, b.x, b.y, radius, finalDamage);
                    thunderburst.at(b.x, b.y, radius, color);
                    Effect.shake(divine ? 22f : 4f, divine ? 40f : 12f, b.x, b.y);
                    if(divine) ThemisLawFx.verdict.at(b.x, b.y, radius, color);
                }
                areaDamage(b, x, y, blastRadius, blastDamage);
                thunderburst.at(x, y, blastRadius, color);
                Fx.chainLightning.at(b.x, b.y, 0f, color, new arc.math.geom.Vec2(x, y));
                if(!Vars.net.client()) for(int i = 0; i < blastBranches; i++)
                    Lightning.create(discharge, b.team, color, 225f * b.damage, x, y, angle + i * 120f, 18);
                if(index % 6 == 0) Sounds.explosion.at(x, y, 1.2f, 0.6f);
            }
        }

        private void areaDamage(Bullet b, float x, float y, float range, float amount){
            damageArea(b, x, y, range, amount * b.damage);
        }

        @Override public void draw(Bullet b){
            float spread = Mathf.clamp(b.time / 20f);
            int exploded = b.time < chargeTime ? 0 : Math.min(nodeCount, 1 + (int)((b.time - chargeTime) / chainInterval));
            float alpha = b.time < chargeTime ? 0.65f + 0.2f * Mathf.clamp(b.time / chargeTime) : 0.8f;
            if(divine) ThemisLawFx.halo(b.x, b.y, radius * spread, 0.5f);
            for(int i = exploded; i < nodeCount; i++){
                float angle = nodeAngle(b, i), distance = nodeRadius(i) * spread;
                float x = b.x + Angles.trnsx(angle, distance), y = b.y + Angles.trnsy(angle, distance);
                for(int j = 0; j < (divine ? 3 : 1); j++)
                    bolt(x, y, angle + j * 120f + b.time * 0.7f, (divine ? 38f : 20f) + j * 7f, b.id + i * 7 + j, (int)(b.time / 4f), alpha);
                if(divine && i > 0) bolt(x, y, angle + 180f, 64f * spread, b.id + i, (int)(b.time / 5f), alpha * 0.8f);
                Drawf.light(x, y, 45f, color, 0.3f);
            }
            Draw.reset();
        }
    }

    /** Native lightning damage nodes retain the mod's allied-team protection. */
    public static class LightningPoint extends BulletType{
        public LightningPoint(){
            super(0.0001f, 0f);
            lifetime = 10f;
            hittable = reflectable = absorbable = keepVelocity = false;
            hitEffect = Fx.hitLancer;
            despawnEffect = Fx.none;
            status = StatusEffects.shocked;
            statusDuration = 90f;
        }
        @Override public boolean testCollision(Bullet b, Building build){
            return !MapAlliances.allied(b.team, build.team) && super.testCollision(b, build);
        }
        @Override public void hitEntity(Bullet b, Hitboxc entity, float health){
            if(entity instanceof Teamc && MapAlliances.allied(b.team, ((Teamc)entity).team())) return;
            super.hitEntity(b, entity, health);
        }
        @Override public void draw(Bullet b){}
    }
}
