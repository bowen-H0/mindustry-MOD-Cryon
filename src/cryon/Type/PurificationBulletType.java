package cryon.Type;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import cryon.Features.MapAlliances;
import cryon.Fx.PurificationFx;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.game.Team;
import mindustry.gen.Bullet;
import mindustry.gen.Sounds;
import mindustry.graphics.Layer;

/** An entity-owned orbital bombardment, with no callbacks surviving a world reset. */
public class PurificationBulletType extends BulletType{
    public float strikeDelay = 120f;
    public float strikeDuration = 480f;
    public float strikeInterval = 12f;
    public int strikesPerBatch = 4;
    public float strikeRadius = 320f;
    public float impactDelay = 36f;
    public float impactRadius = 80f;
    public float impactDamage = 650f;
    public float openingDamage = 2000f;
    public float finalDamage = 4500f;
    public final OrbitalImpact impact = new OrbitalImpact();

    public static class State{
        public int batches;
        public boolean started, finished;
    }

    public PurificationBulletType(){
        super(0f, 0f);
        collides = collidesTiles = false;
        hittable = absorbable = reflectable = false;
        keepVelocity = false;
        setDefaults = false;
        hitEffect = despawnEffect = shootEffect = smokeEffect = Fx.none;
    }

    @Override public void init(){
        super.init();
        lifetime = strikeDelay + strikeDuration + impactDelay + 1f;
        drawSize = strikeRadius * 2f + 100f;
        splashDamage = openingDamage;
        splashDamageRadius = strikeRadius;
        impact.lifetime = impactDelay;
        impact.radius = impactRadius;
        impact.drawSize = 6000f;
        impact.damage = impactDamage;
        impact.init();
    }

    @Override public void init(Bullet b){
        super.init(b);
        Vec2 target = new Vec2(b.aimX, b.aimY);
        if(b.aimX < 0f || b.aimY < 0f){
            target.set(b.x + Angles.trnsx(b.rotation(), rangeOverride),
                b.y + Angles.trnsy(b.rotation(), rangeOverride));
        }
        target.sub(b.x, b.y).limit(rangeOverride).add(b.x, b.y);
        b.set(target);
        b.data = new State();
        Sounds.chargeCorvus.at(b.x, b.y, 0.7f, 0.9f);
    }

    @Override public void update(Bullet b){
        if(!(b.data instanceof State state) || b.time < strikeDelay) return;
        if(!state.started){
            state.started = true;
            blast(b, strikeRadius, openingDamage);
        }
        int totalBatches = Mathf.ceil(strikeDuration / strikeInterval);
        while(state.batches < totalBatches && b.time >= strikeDelay + state.batches * strikeInterval){
            for(int i = 0; i < strikesPerBatch; i++){
                long seed = (long)b.id * 104729L + (long)state.batches * strikesPerBatch + i;
                float angle = Mathf.randomSeed(seed, 0f, 360f);
                float distance = Mathf.sqrt(Mathf.randomSeed(seed + 49999L)) * Math.max(0f, strikeRadius - impactRadius);
                impact.create(b.owner, b.team, b.x + Angles.trnsx(angle, distance),
                    b.y + Angles.trnsy(angle, distance), 0f);
            }
            state.batches++;
        }
        if(!state.finished && b.time >= strikeDelay + strikeDuration + impactDelay){
            state.finished = true;
            blast(b, strikeRadius, finalDamage);
        }
    }

    private static void blast(Bullet b, float radius, float amount){
        damageArea(b, radius, amount);
        PurificationFx.impact.at(b.x, b.y, radius, PurificationFx.blue);
        PurificationFx.beam.at(b.x, b.y, radius, PurificationFx.blue);
        Effect.shake(18f, 60f, b.x, b.y);
        Sounds.explosionReactor2.at(b.x, b.y, 0.7f, 1.5f);
    }

    private static void damageArea(Bullet b, float radius, float amount){
        if(Vars.net.client()) return;
        float damage = amount * b.damageMultiplier();
        Units.nearbyEnemies(b.team, b.x, b.y, radius, unit -> {
            if(!MapAlliances.allied(b.team, unit.team) && unit.team != Team.derelict
                && unit.within(b, radius) && unit.targetable(b.team)) unit.damage(damage);
        });
        Vars.indexer.eachBlock(null, b.x, b.y, radius,
            building -> building.team != Team.derelict && !MapAlliances.allied(b.team, building.team)
                && building.block.targetable && !building.block.privileged,
            building -> building.damage(b.team, damage));
    }

    @Override public void draw(Bullet b){
        if(!(b.data instanceof State state) || state.finished) return;
        float progress = Mathf.clamp(b.time / strikeDelay);
        Draw.z(Layer.flyingUnit + 1f);
        Draw.color(PurificationFx.blue);
        Draw.alpha(0.45f);
        Lines.stroke(1.6f);
        Lines.circle(b.x, b.y, strikeRadius);
        for(int i = 0; i < 8; i++){
            float angle = i * 45f;
            float x = b.x + Angles.trnsx(angle, strikeRadius);
            float y = b.y + Angles.trnsy(angle, strikeRadius);
            Lines.lineAngle(x, y, angle + 180f, 14f);
        }
        if(!state.started){
            Draw.alpha(0.8f);
            Lines.stroke(2.5f);
            Lines.circle(b.x, b.y, strikeRadius * (1f - progress));
        }
        Draw.reset();
    }

    @Override public void despawned(Bullet b){}

    public static class OrbitalImpact extends BulletType{
        public float radius;

        public OrbitalImpact(){
            super(0f, 880f);
            collides = collidesTiles = false;
            hittable = absorbable = reflectable = false;
            keepVelocity = false;
            setDefaults = false;
            hitEffect = despawnEffect = shootEffect = smokeEffect = Fx.none;
        }

        @Override public void draw(Bullet b){
            Draw.z(Layer.flyingUnit + 1f);
            Draw.color(PurificationFx.blue);
            Draw.alpha(0.45f + b.fin() * 0.35f);
            Lines.stroke(1.5f);
            Lines.circle(b.x, b.y, radius * (1f - b.fin()) + 4f);
            Lines.line(b.x - 8f, b.y, b.x + 8f, b.y);
            Lines.line(b.x, b.y - 8f, b.x, b.y + 8f);
            Draw.reset();
            float descent = Mathf.clamp((b.time - (b.lifetime - 22f)) / 22f);
            if(descent > 0f) PurificationFx.drawDescent(b.x, b.y, radius, descent);
        }

        @Override public void despawned(Bullet b){
            if(b.absorbed || b.hit || b.time < b.lifetime) return;
            b.hit = true;
            damageArea(b, radius, damage);
            PurificationFx.beam.at(b.x, b.y, radius, PurificationFx.blue);
            PurificationFx.impact.at(b.x, b.y, radius, PurificationFx.blue);
            Effect.shake(3f, 12f, b.x, b.y);
            Sounds.explosionArtilleryShock.at(b.x, b.y, 0.8f, 0.45f);
        }
    }
}
