package example;

import arc.graphics.Color;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import mindustry.entities.Effect;
import mindustry.entities.effect.*;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;

import static arc.graphics.g2d.Draw.color;
import static arc.graphics.g2d.Lines.stroke;
import static arc.math.Angles.randLenVectors;

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
}