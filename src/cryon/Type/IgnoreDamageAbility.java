package cryon.Type;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.abilities.Ability;
import mindustry.gen.*;
import mindustry.graphics.*;

/** 每 interval tick(默认 1 秒)掷一次骰子,刷到就给 duration tick(默认 1 秒)无敌,与是否被攻击无关。 */
public class IgnoreDamageAbility extends Ability{
    public float chance = 0.25f;                       // 每次判定的触发概率
    public float interval = 60f;                       // 判定间隔(tick),60 = 1 秒
    public float duration = 60f;                       // 触发后无敌持续时间(tick)
    public Color color = Color.valueOf("ffd75e");

    public static final Effect triggerFx = new Effect(24f, e -> {
        Draw.blend(Blending.additive);
        Draw.color(Color.white, e.color, e.fin());
        Lines.stroke(3f * e.fout());
        Lines.circle(e.x, e.y, e.rotation * (0.6f + 0.8f * e.finpow()));
        Fill.light(e.x, e.y, 24, e.rotation * 1.1f * e.fout(), Tmp.c1.set(e.color).a(0.5f), Tmp.c2.set(e.color).a(0f));
        Draw.blend();
        Draw.reset();
    });

    protected float timer, activeTime;

    public IgnoreDamageAbility(){}

    public IgnoreDamageAbility(float chance){
        this.chance = chance;
    }

    @Override
    public void update(Unit unit){
        // 每个间隔掷一次骰子,刷到就开启无敌并播放触发特效
        timer += Time.delta;
        if(timer >= interval){
            timer = 0f;
            if(Mathf.chance(chance)){
                activeTime = duration;
                triggerFx.at(unit.x, unit.y, unit.hitSize, color);
            }
        }

        // 无敌期间持续刷新状态(时长必须 >= 2,1 tick 会在倍率生效前被移除)
        if(activeTime > 0f){
            unit.apply(StatusEffects.invincible, 2f);
            activeTime -= Time.delta;
        }
    }

    @Override
    public void draw(Unit unit){
        // 无敌期间显示一圈脉动光环,方便确认状态
        if(activeTime <= 0f) return;
        float z = Draw.z();
        Draw.z(Layer.effect);
        Draw.blend(Blending.additive);
        Draw.color(color);
        Draw.alpha(0.5f + Mathf.absin(Time.time, 6f, 0.2f));
        Lines.stroke(1.8f);
        Lines.circle(unit.x, unit.y, unit.hitSize * 0.75f);
        Draw.blend();
        Draw.reset();
        Draw.z(z);
    }
}