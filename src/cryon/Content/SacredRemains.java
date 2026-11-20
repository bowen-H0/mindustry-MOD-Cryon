package cryon.Content;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.ai.types.GroundAI;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.abilities.Ability;
import mindustry.entities.bullet.LiquidBulletType;
import mindustry.entities.pattern.ShootSpread;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.meta.Env;

/** Malis' forbidden tripod, using native three-leg movement and persistent liquid hazards. */
public final class SacredRemains{
    public static final Color ichorColor = Color.valueOf("e0b568"), flesh = Color.valueOf("ac647d"), violet = Color.valueOf("b28bf0");
    public static Liquid ichor;
    public static StatusEffect ichorCorrosion;
    public static UnitType sacredRemains;
    public static final float riftDuration = 600f;

    public static final Effect drip = new Effect(30f, e -> {
        Draw.color(ichorColor, e.fout());
        Fill.circle(e.x, e.y + e.fout() * 16f, 1.5f * e.fout());
        Lines.stroke(e.fout());
        Lines.circle(e.x, e.y, 2f + e.fin() * 5f);
    }).layer(Layer.debris);

    /** Visual lightning only: the entire emergence area is telegraphed before the boss is dangerous. */
    public static final Effect rift = new Effect(riftDuration, 420f, e -> {
        float elapsed = e.time + e.rotation, progress = Mathf.clamp(elapsed / riftDuration);
        float open = Math.min(Mathf.clamp(progress * 5f), Mathf.clamp((1f - progress) * 7f));
        Draw.color(Color.valueOf("1d142d"), open * 0.96f);
        Fill.polyBegin();
        for(int i = 0; i < 48; i++) Fill.polyPoint(e.x + Mathf.cosDeg(i * 7.5f) * 28f * open, e.y + 22f + Mathf.sinDeg(i * 7.5f) * 106f * open);
        Fill.polyEnd();
        Draw.color(violet, open * 0.8f);
        Lines.stroke(2f * open);
        Lines.ellipse(e.x, e.y + 22f, 1f, 27f * open, 105f * open, 0f);
        Lines.ellipse(e.x, e.y, 54f * open, 1f, 0.35f, 0f);
        long frame = (long)(elapsed / 4f);
        for(int i = 0; i < 15; i++){
            long seed = e.id * 193L + frame * 89L + i * 17L;
            float angle = Mathf.randomSeed(seed, 0f, 360f), length = Mathf.randomSeed(seed + 1, 45f, 142f) * open;
            float px = e.x, py = e.y + 22f;
            Draw.color(violet, Color.white, Mathf.randomSeed(seed + 2, 0.2f, 0.9f));
            Draw.alpha(open * 0.85f);
            Lines.stroke(Mathf.randomSeed(seed + 3, 0.7f, 1.8f));
            for(int j = 1; j <= 7; j++){
                float distance = length * j / 7f;
                float sideways = j == 7 ? 0f : Mathf.randomSeed(seed + j * 13L, -13f, 13f);
                float nx = e.x + Mathf.cosDeg(angle) * distance - Mathf.sinDeg(angle) * sideways;
                float ny = e.y + 22f + Mathf.sinDeg(angle) * distance + Mathf.cosDeg(angle) * sideways;
                Lines.line(px, py, nx, ny);px = nx;py = ny;
            }
        }
        Drawf.light(e.x, e.y, 160f * open, violet, 0.65f);
        Draw.reset();
    }).layer(Layer.effect + 0.5f);

    private SacredRemains(){}
    public static void load(){
        ichorCorrosion = new StatusEffect("ichor-corrosion"){{
            color = ichorColor;
            damage = 4.5f;
            speedMultiplier = 0.82f;
            effect = Fx.sapped;
            effectChance = 0.07f;
        }};
        ichor = new Liquid("ichor", ichorColor){{
            effect = ichorCorrosion;
            viscosity = 0.7f;
            temperature = 0.55f;
            heatCapacity = 0.1f;
            boilPoint = 2f;
            coolant = false;
            lightColor = new Color(ichorColor).a(0.18f);
            canStayOn.add(Liquids.water);canStayOn.add(Liquids.oil);canStayOn.add(Liquids.cryofluid);
        }};
        sacredRemains = new cryon.Type.SacredRemainsUnitType("sacred-remains"){{
            constructor = LegsUnit::create;
            controller = unit -> new GroundAI();
            health = 36000f;
            armor = 12f;
            hitSize = 60f;
            speed = 0.48f;
            accel = 0.06f;
            drag = 0.12f;
            rotateSpeed = 0.8f;
            legCount = 3;
            legGroupSize = 1;
            legLength = 92f;
            legBaseOffset = 18f;
            legMoveSpace = 0.7f;
            legSpeed = 0.36f;
            legForwardScl = 0.65f;
            legExtension = -6f;
            legMinLength = 0.4f;
            legMaxLength = 1.25f;
            legContinuousMove = false;
            legStraightness = 0f;
            legSplashDamage = 90f;
            legSplashRange = 22f;
            stepShake = 1f;
            allowLegStep = true;
            canDrown = false;
            useUnitCap = false;
            playerControllable = logicControllable = false;
            allowedInPayloads = false;
            itemCapacity = 0;
            targetAir = false;
            faceTarget = true;
            drawCell = false;
            outlineColor = Color.valueOf("302533");
            mechLegColor = Color.white;
            envEnabled = Env.any;
            range = 200f;
            immunities.add(ichorCorrosion);immunities.add(StatusEffects.wet);
            immunities.add(StatusEffects.burning);immunities.add(StatusEffects.melting);
            abilities.add(new IchorTrail());
            Weapon mouth = new Weapon("cryon-sacred-remains-mouth");
            mouth.x = -11f;mouth.y = 13f;mouth.mirror = false;mouth.rotate = true;
            mouth.rotateSpeed = 1.4f;mouth.shootY = 7f;mouth.reload = 75f;
            mouth.inaccuracy = 10f;mouth.shoot = new ShootSpread(9, 7f);
            mouth.bullet = stream(200f, 85f, 3.4f);mouth.shootSound = Sounds.loopSpray;
            weapons.add(mouth);
            // Three independently aimed vents spray the surrounding ground rather than firing artillery.
            for(int i = 0; i < 3; i++){
                Weapon vent = new Weapon("");
                vent.x = i == 0 ? 17f : i == 1 ? -16f : 2f;
                vent.y = i == 0 ? 3f : i == 1 ? -8f : -19f;
                vent.mirror = false;vent.rotate = true;vent.rotateSpeed = 1.7f;
                vent.controllable = false;vent.autoTarget = true;vent.predictTarget = false;
                vent.reload = 52f + i * 9f;vent.inaccuracy = 23f;
                vent.shoot = new ShootSpread(5, 11f);
                vent.bullet = stream(160f, 55f, 2.6f);vent.shootSound = Sounds.loopSpray;
                vent.shootSoundVolume = 0.45f;
                weapons.add(vent);
            }
        }};
    }
    private static LiquidBulletType stream(float range, float damage, float size){
        LiquidBulletType bullet = new LiquidBulletType(ichor);
        bullet.speed = 4f;bullet.lifetime = range / bullet.speed;
        bullet.drag = 0f;bullet.keepVelocity = false;bullet.rangeOverride = range;
        bullet.damage = damage;bullet.orbSize = size;bullet.puddleSize = 48f;
        bullet.collidesAir = false;bullet.statusDuration = 120f;
        bullet.despawnHit = true;
        return bullet;
    }
    public static final class IchorTrail extends Ability{
        private float timer;
        @Override public void update(Unit unit){
            if(Vars.net.client() || !unit.moving() || unit.dead) return;
            timer += arc.util.Time.delta;
            if(timer < 18f) return;
            timer %= 18f;
            Tmp.v1.trns(unit.rotation - 90f, Mathf.randomSeed(unit.id + (long)arc.util.Time.time, -15f, 15f), -10f).add(unit);
            Puddles.deposit(Vars.world.tileWorld(Tmp.v1.x, Tmp.v1.y), ichor, 45f);
            Call.effect(drip, Tmp.v1.x, Tmp.v1.y, 0f, ichorColor);
        }
    }
}
