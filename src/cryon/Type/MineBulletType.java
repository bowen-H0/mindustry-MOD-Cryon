package cryon.Type;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.gen.*;
import mindustry.graphics.*;

/** 地雷弹:发射后减速停下,显示为发光环;敌方单位进入触发半径时爆炸,时间到则无声消失。 */
public class MineBulletType extends BulletType{
    public float armTime = 40f;
    public float triggerRadius = 48f;
    public Color color = Color.valueOf("ffa31a");
    public Color goldColor = Color.valueOf("ffd75e");

    public MineBulletType(float speed, float damage, float blastRadius){
        super(speed, 0f);
        splashDamage = damage;
        splashDamageRadius = blastRadius;
        collides = false;
        collidesTiles = false;
        absorbable = false;
        hittable = false;
        reflectable = false;
        keepVelocity = false;
        despawnHit = false;
        drag = 0.03f;
        lifetime = 480f;
        hitEffect = new Effect(34f, 500f, e -> {
            Draw.blend(Blending.additive);
            Draw.color(Color.white, e.color, e.fin());
            Lines.stroke(8f * e.fout());
            Lines.circle(e.x, e.y, 10f + blastRadius * e.finpow());
            Fill.light(e.x, e.y, 36, blastRadius * 0.7f * e.fout(), Tmp.c1.set(Color.white).a(e.fout()), Tmp.c2.set(e.color).a(0f));
            Draw.blend();
            Draw.reset();
        }).layer(Layer.bullet + 6f);
        hitColor = color;
        hitSound = Sounds.explosion;
        despawnEffect = Fx.none;
        shootEffect = Fx.none;
        smokeEffect = Fx.none;
        lightColor = color;
    }

    @Override
    public void update(Bullet b){
        super.update(b);
        if(b.time < armTime) return;
        Unit hit = Units.closestEnemy(b.team, b.x, b.y, triggerRadius,
                u -> u.checkTarget(collidesAir, collidesGround) && u.targetable(b.team));
        if(hit != null){
            hit(b);
            b.remove();
        }
    }

    @Override
    public void draw(Bullet b){
        float z = Draw.z();
        Draw.z(Layer.bullet + 2f);
        float arm = b.time < armTime ? 0.35f : 1f;
        float fade = Mathf.clamp(b.time / 10f) * Mathf.clamp(b.fout() * 6f) * arm;
        float pulse = 1f + Mathf.absin(Time.time, 14f, 0.05f);
        float rr = triggerRadius * 0.55f * pulse;

        Draw.blend(Blending.additive);
        Draw.color(color);
        Draw.alpha(0.3f * fade);
        Lines.stroke(6f);
        Lines.circle(b.x, b.y, rr);
        Draw.alpha(fade);
        Lines.stroke(2f);
        Lines.circle(b.x, b.y, rr);

        Draw.color(goldColor);
        Draw.alpha(fade);
        Lines.stroke(2f);
        Lines.arc(b.x, b.y, rr * 0.7f, 0.25f, Time.time * 3f);
        Lines.arc(b.x, b.y, rr * 0.7f, 0.25f, Time.time * 3f + 180f);

        Fill.light(b.x, b.y, 24, rr * 0.5f, Tmp.c1.set(Color.white).a(0.8f * fade), Tmp.c2.set(color).a(0f));
        Draw.blend();
        Drawf.light(b.x, b.y, triggerRadius * 1.5f, color, 0.5f * fade);
        Draw.reset();
        Draw.z(z);
    }
}