package cryon.Fx;

import arc.graphics.Color;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import mindustry.entities.Effect;
import mindustry.graphics.Drawf;

import static arc.graphics.g2d.Draw.color;
import static arc.graphics.g2d.Lines.stroke;
import static arc.math.Angles.randLenVectors;
import arc.graphics.g2d.Draw;
import arc.math.Interp;

/** Cryon 轨道炮特效集合，硬朗高冲击力风格，支持双色自定义。 */
public class CryonFx {

    /** 弹道拖尾（三角形拖影） */
    public static Effect railgunTrail(Color front, Color back){
        return new Effect(30f, e -> {
            for(int i = 0; i < 2; i++){
                color(i == 0 ? back : front);
                float m = i == 0 ? 1f : 0.5f;
                float rot = e.rotation + 180f;
                float w = 15f * e.fout() * m;
                Drawf.tri(e.x, e.y, w, (30f + Mathf.randomSeedRange(e.id, 15f)) * m, rot);
                Drawf.tri(e.x, e.y, w, 10f * m, rot + 180f);
            }
            Drawf.light(e.x, e.y, 60f, back, 0.6f * e.fout());
        });
    }

    /** 开火特效（炮口爆发，比原版instShoot更多刺、更强冲击环） */
    public static Effect railgunShoot(Color front, Color back){
        return new Effect(28f, e -> {
            e.scaled(12f, b -> {
                color(Color.white, back, b.fin());
                stroke(b.fout() * 3.5f + 0.3f);
                Lines.circle(b.x, b.y, b.fin() * 60f);
            });

            color(back);
            for(int i : Mathf.signs){
                Drawf.tri(e.x, e.y, 16f * e.fout(), 100f, e.rotation + 90f * i);
                Drawf.tri(e.x, e.y, 16f * e.fout(), 60f, e.rotation + 20f * i);
            }
            color(front);
            for(int i : Mathf.signs){
                Drawf.tri(e.x, e.y, 8f * e.fout(), 70f, e.rotation + 45f * i);
            }

            Drawf.light(e.x, e.y, 220f, back, 1f * e.fout());
        });
    }

    /** 命中特效（比原版instHit更多刺、更多层冲击波、更多碎片） */
    public static Effect railgunHit(Color front, Color back){
        return new Effect(24f, 240f, e -> {
            for(int i = 0; i < 2; i++){
                color(i == 0 ? back : front);
                float m = i == 0 ? 1f : 0.55f;
                for(int j = 0; j < 7; j++){
                    float rot = e.rotation + Mathf.randomSeedRange(e.id + j, 55f);
                    float w = 26f * e.fout() * m;
                    Drawf.tri(e.x, e.y, w, (95f + Mathf.randomSeedRange(e.id + j, 45f)) * m, rot);
                    Drawf.tri(e.x, e.y, w, 22f * m, rot + 180f);
                }
            }

            e.scaled(12f, c -> {
                color(front);
                stroke(c.fout() * 2.5f + 0.3f);
                Lines.circle(e.x, e.y, c.fin() * 36f);
            });
            e.scaled(20f, c -> {
                color(back);
                stroke(c.fout() * 1.6f + 0.2f);
                Lines.circle(e.x, e.y, c.fin() * 55f);
            });

            e.scaled(14f, c -> {
                color(back);
                randLenVectors(e.id, 30, 6f + e.fin() * 95f, e.rotation, 70f, (x, y) -> {
                    Fill.square(e.x + x, e.y + y, c.fout() * 3.5f, 45f);
                });
            });
            e.scaled(16f, c -> {
                color(front);
                randLenVectors(e.id + 99, 14, 4f + e.fin() * 60f, (x, y) -> {
                    Fill.square(e.x + x, e.y + y, c.fout() * 2.2f, 45f);
                });
            });

            Drawf.light(e.x, e.y, 140f * e.fout(), back, 1f * e.fout());
        });
    }

    /** 穿透特效（缩小版命中，力度稍弱但依旧有刺） */
    public static Effect railgunPierce(Color front, Color back){
        return new Effect(18f, e -> {
            color(back);
            for(int j = 0; j < 4; j++){
                float rot = e.rotation + Mathf.randomSeedRange(e.id + j, 40f);
                Drawf.tri(e.x, e.y, 10f * e.fout(), 40f, rot);
                Drawf.tri(e.x, e.y, 10f * e.fout(), 10f, rot + 180f);
            }
            color(front);
            stroke(1.6f * e.fout());
            Lines.circle(e.x, e.y, 2f + 14f * e.fin());

            Drawf.light(e.x, e.y, 40f * e.fout(), back, 0.5f * e.fout());
        });
    }

    /** 消失特效（弹道末端的收束闪光） */
    public static Effect railgunDespawn(Color front, Color back){
        return new Effect(20f, e -> {
            color(back);
            for(int i : Mathf.signs){
                Drawf.tri(e.x, e.y, 8f * e.fout(), 30f, e.rotation + 90f * i);
            }
            color(front);
            stroke(1.5f * e.fout());
            Lines.circle(e.x, e.y, 6f * e.fin());

            Drawf.light(e.x, e.y, 30f * e.fout(), back, 0.4f * e.fout());
        });
    }
    /** 反弹盾专用爆炸：先内爆收束，再向外爆发。e.rotation 传入护盾半径 */
    public static final Effect reflectShieldExplosion = new Effect(50f, 700f, e -> {
        float R = Math.max(e.rotation, 20f);
        Color front = Color.valueOf("d8f1ff");
        Color back = Color.valueOf("4aa3ff");

        float split = 0.3f;

        if(e.fin() < split){
            float in = Interp.pow2In.apply(e.fin() / split);

            color(back, front, in);
            stroke(2f + in * 3f);
            Lines.circle(e.x, e.y, R * (1f - in * 0.85f));

            color(front);
            stroke(1.5f);
            for(int i = 0; i < 20; i++){
                float ang = 360f / 20f * i + Mathf.randomSeedRange(e.id + i, 8f);
                float r0 = R * (1.3f - in * 1.1f);
                float r1 = r0 + R * 0.25f * (1f - in);
                Lines.line(
                        e.x + Mathf.cosDeg(ang) * r0, e.y + Mathf.sinDeg(ang) * r0,
                        e.x + Mathf.cosDeg(ang) * r1, e.y + Mathf.sinDeg(ang) * r1);
            }

            color(front);
            Draw.alpha(0.25f + in * 0.5f);
            Fill.circle(e.x, e.y, R * 0.12f * (1f + in));
            Draw.alpha(1f);
        }else{
            float o = (e.fin() - split) / (1f - split);
            float oe = Interp.pow3Out.apply(o);
            float fade = 1f - o;

            color(Color.white, front, o);
            Draw.alpha(fade);
            Fill.circle(e.x, e.y, R * 0.45f * fade);
            Draw.alpha(1f);

            color(back, front, fade);
            stroke(fade * 6f + 0.3f);
            Lines.circle(e.x, e.y, R * (0.2f + oe * 1.9f));

            float o2 = Mathf.clamp((o - 0.15f) / 0.85f);
            if(o2 > 0f){
                float oe2 = Interp.pow3Out.apply(o2);
                color(front);
                stroke((1f - o2) * 3f + 0.2f);
                Lines.circle(e.x, e.y, R * (0.2f + oe2 * 1.3f));
            }

            for(int i = 0; i < 2; i++){
                color(i == 0 ? back : front);
                float m = i == 0 ? 1f : 0.55f;
                for(int j = 0; j < 12; j++){
                    float rot = 360f / 12f * j + Mathf.randomSeedRange(e.id + j, 12f);
                    float len = R * (0.7f + Mathf.randomSeed(e.id + j * 3, 0f, 0.7f)) * m;
                    float w = 12f * fade * m;
                    Drawf.tri(e.x, e.y, w, len * oe, rot);
                    Drawf.tri(e.x, e.y, w, 8f * m * fade, rot + 180f);
                }
            }

            color(front);
            for(int i = 0; i < 26; i++){
                float ang = Mathf.randomSeed(e.id + i * 5, 0f, 360f);
                float dist = R * (0.5f + Mathf.randomSeed(e.id + i * 7, 0f, 1.4f)) * oe;
                float px = e.x + Mathf.cosDeg(ang) * dist;
                float py = e.y + Mathf.sinDeg(ang) * dist;
                Fill.square(px, py, (2f + Mathf.randomSeed(e.id + i * 9, 0f, 3f)) * fade, ang + o * 180f);
            }

            color(back);
            stroke(fade * 1.6f);
            for(int i = 0; i < 18; i++){
                float ang = Mathf.randomSeed(e.id + i * 13, 0f, 360f);
                float r0 = R * (0.8f + oe * 0.9f);
                Lines.lineAngle(e.x + Mathf.cosDeg(ang) * r0, e.y + Mathf.sinDeg(ang) * r0, ang, (10f + Mathf.randomSeed(e.id + i * 17, 0f, 18f)) * fade);
            }
        }

        Draw.reset();
        Drawf.light(e.x, e.y, R * 3f * (1f - Math.abs(e.fin() - split) * 0.8f), back, 0.9f * e.fout());
    });
    // 伽马瓦解射线的 3 秒蓄力特效(红色、向炮口汇聚)
    public static final Effect gammaCharge = new Effect(190f, 700f, e -> {
        Color c = Color.valueOf("ff2a2a");
        float fin = e.fin(), fout = e.fout();

        // 中心能量球,越蓄越大、越白
        Draw.color(c, Color.white, fin * fin);
        Fill.circle(e.x, e.y, 6f + 40f * fin * fin);
        Draw.color(Color.white);
        Fill.circle(e.x, e.y, 3f + 18f * fin * fin * fin);

        // 两圈向内收缩的光环
        Draw.color(c);
        stroke(1.5f + 5f * fin);
        Lines.circle(e.x, e.y, 140f * fout);
        Lines.circle(e.x, e.y, 70f * fout * fout);

        // 四周向中心汇聚的光刺
        randLenVectors(e.id, 24, 200f * fout, (x, y) -> {
            Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y) + 180f, 6f + 20f * fin);
        });

        Drawf.light(e.x, e.y, 100f + 260f * fin, c, 0.9f * fin);
        Draw.reset();
    }).followParent(true).rotWithParent(true);
}