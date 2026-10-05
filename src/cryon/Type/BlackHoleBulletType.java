package cryon.Type;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;

/** 黑洞弹:缓慢飞行并持续变大,期间持续伤害+牵引+吞子弹,寿命结束时湮灭爆炸。 */
public class BlackHoleBulletType extends BulletType{
    public float startRadius = 8f;
    public float maxRadius = 130f;
    public float pullRadiusScale = 2.2f;   // 牵引范围 = 当前半径 * 此值
    public float pullForce = 0.5f;
    public float tickDamage = 500f;        // 持续伤害(每次,满体积时;随体积增长)
    public float tickMinScale = 0.3f;      // 刚发射时的伤害比例
    public float tickHealthPercent = 0.004f; // 每次额外造成目标最大生命值的比例
    public float damageInterval = 6f;
    public float explodeRadius = 340f;
    public float explodeDamage = 40000f;
    public float explodeHealthPercent = 0.12f; // 爆炸额外造成目标最大生命值的比例
    public Color color = Color.valueOf("ffa31a");
    public Color goldColor = Color.valueOf("ffd75e");

    public static final Effect annihilateFx = new Effect(75f, 1200f, e -> {
        float rad = e.rotation;
        Draw.blend(Blending.additive);
        Fill.light(e.x, e.y, 60, rad * 0.7f * e.fout(), Tmp.c1.set(Color.white).a(e.fout()), Tmp.c2.set(e.color).a(0f));
        Draw.color(Color.white, e.color, e.fin());
        Lines.stroke(26f * e.fout());
        Lines.circle(e.x, e.y, rad * e.finpow());
        Lines.stroke(8f * e.fout());
        Lines.circle(e.x, e.y, rad * 0.6f * e.finpow());
        Angles.randLenVectors(e.id, 48, rad * 0.2f + rad * 0.8f * e.finpow(), (x, y) -> {
            Fill.circle(e.x + x, e.y + y, 2f + 7f * e.fout());
        });
        Draw.blend();
        Draw.reset();
    });

    public BlackHoleBulletType(float speed, float lifetime){
        super(speed, 0f);
        this.lifetime = lifetime;
        collides = false;
        collidesTiles = false;
        absorbable = false;
        hittable = false;
        reflectable = false;
        keepVelocity = false;
        despawnHit = false;
        hitEffect = Fx.none;
        despawnEffect = Fx.none;
        shootEffect = Fx.none;
        smokeEffect = Fx.none;
        lightColor = color;
    }

    @Override
    public void init(){
        super.init();
        drawSize = Math.max(drawSize, maxRadius * 5f);
    }

    protected float radius(Bullet b){
        return Mathf.lerp(startRadius, maxRadius, Interp.smooth.apply(b.fin()));
    }

    /** 真实伤害:无距离衰减、无视护甲;单位额外按最大生命值百分比结算。 */
    protected void pierceDamage(Bullet b, float radius, float flat, float pct){
        float mult = b.damageMultiplier();
        if(collidesAir || collidesGround){
            Units.nearbyEnemies(b.team, Tmp.r2.setCentered(b.x, b.y, radius * 2f), u -> {
                if(!u.checkTarget(collidesAir, collidesGround) || !u.within(b, radius + u.hitSize / 2f)) return;
                u.damagePierce((flat + u.maxHealth * pct) * mult);
            });
        }
        if(collidesGround){
            Units.nearbyBuildings(b.x, b.y, radius, bd -> {
                if(bd.team != b.team && bd.team != Team.derelict && bd.block.targetable){
                    bd.damage(b.team, flat * mult);
                }
            });
        }
    }

    @Override
    public void update(Bullet b){
        super.update(b);
        float r = radius(b);
        float pr = r * pullRadiusScale;

        // 牵引敌方单位
        Units.nearbyEnemies(b.team, Tmp.r1.setCentered(b.x, b.y, pr * 2f), u -> {
            if(!u.checkTarget(collidesAir, collidesGround) || !u.within(b, pr)) return;
            float f = pullForce * (1f - u.dst(b) / pr) * Time.delta;
            u.vel.add(Tmp.v1.set(b.x - u.x, b.y - u.y).setLength(f));
        });

        // 吞掉敌方子弹
        Groups.bullet.intersect(b.x - r, b.y - r, r * 2f, r * 2f, o -> {
            if(o != b && o.team != b.team && o.type.absorbable && o.within(b, r)){
                o.absorb();
            }
        });

        // 持续伤害
        if(b.timer(1, damageInterval)){
            float scale = Mathf.lerp(tickMinScale, 1f, b.fin());
            pierceDamage(b, r, tickDamage * scale, tickHealthPercent);
        }
    }

    @Override
    public void despawned(Bullet b){
        super.despawned(b);
        pierceDamage(b, explodeRadius, explodeDamage, explodeHealthPercent);
        annihilateFx.at(b.x, b.y, explodeRadius, color);
        Sounds.explosion.at(b.x, b.y, 0.6f, 3f);
        Effect.shake(30f, 60f, b.x, b.y);
    }

    @Override
    public void draw(Bullet b){
        float r = radius(b);
        float z = Draw.z();
        Draw.z(Layer.bullet + 5f);

        Draw.blend(Blending.additive);
        // 中空外晕:用柔和的圆环代替实心径向光晕,中心保持透明
        Draw.color(color);
        Draw.alpha(0.12f);
        Lines.stroke(r * 0.55f);
        Lines.circle(b.x, b.y, r * 1.05f);
        Draw.alpha(0.22f);
        Lines.stroke(r * 0.28f);
        Lines.circle(b.x, b.y, r * 1.02f);
        for(int i = 0; i < 3; i++){
            Draw.color(i == 0 ? goldColor : color);
            Draw.alpha(0.9f - i * 0.2f);
            Lines.stroke(Math.max(1.5f, r * (0.1f - i * 0.025f)));
            Lines.arc(b.x, b.y, r * (1.2f + 0.14f * i), 0.3f + 0.1f * i, Time.time * (5f - i * 1.3f) + i * 120f);
            Lines.arc(b.x, b.y, r * (1.2f + 0.14f * i), 0.3f + 0.1f * i, Time.time * (5f - i * 1.3f) + i * 120f + 180f);
        }
        Draw.blend();

        Draw.blend(Blending.additive);
        Draw.color(goldColor);
        Lines.stroke(Math.max(1.5f, r * 0.05f));
        Lines.circle(b.x, b.y, r * 1.02f);
        Draw.blend();

        Drawf.light(b.x, b.y, r * 4f, color, 0.8f);
        Draw.reset();
        Draw.z(z);
    }
}