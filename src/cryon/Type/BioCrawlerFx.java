package cryon.Type;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import mindustry.entities.Effect;
import mindustry.graphics.Drawf;

import static arc.graphics.g2d.Draw.color;
import static arc.graphics.g2d.Lines.stroke;

/** 极简但带发光:每个特效一两个基本形状 + 一道泛光 */
public class BioCrawlerFx{
    public static final Color red = Color.valueOf("c63b32"), orange = Color.valueOf("ed7941"),
            hot = Color.valueOf("ffd9b0");

    /** 炮口:垂直于射向的两片定长三角,宽度收缩(同 lancerLaserShoot 思路) */
    public static final Effect pulseShoot = new Effect(18f, e -> {
        color(orange);
        for(int i : Mathf.signs){
            Drawf.tri(e.x, e.y, 5f * e.fout(), 26f, e.rotation + 90f * i);
        }
    });

    /** 命中:一道粗环 */
    public static final Effect contact = new Effect(14f, e -> {
        color(orange, red, e.fin());
        stroke(e.fout() * 3.5f);
        Lines.circle(e.x, e.y, e.finpow() * 24f);
    });

    /** 释放:中心白闪 + 一道粗环 + 泛光 */
    public static final Effect release = new Effect(22f, 160f, e -> {
        e.scaled(7f, s -> {
            color(hot);
            Fill.circle(e.x, e.y, 10f * s.fout());
        });

        color(orange, red, e.fin());
        stroke(e.fout() * 5f);
        Lines.circle(e.x, e.y, e.finpow() * 40f);

        Drawf.light(e.x, e.y, 120f * e.fout(), orange, 0.8f * e.fout());
    });

    /** 诱饵生成:从诱饵位置朝单位方向拖出的光束(rotation = 诱饵飞出方向)+ 小环 + 泛光 */
    public static final Effect decoySpawn = new Effect(16f, 80f, e -> {
        color(orange, red, e.fin());
        // 尖端指回单位,根部在诱饵,像被"射"出去
        Drawf.tri(e.x, e.y, 7f * e.fout(), 22f, e.rotation + 180f);
        stroke(e.fout() * 2.5f);
        Lines.circle(e.x, e.y, e.finpow() * 13f);

        Drawf.light(e.x, e.y, 45f * e.fout(), orange, 0.7f * e.fout());
    });
}