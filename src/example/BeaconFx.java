package example;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import mindustry.entities.Effect;
import mindustry.graphics.Layer;

/** 蓝色轨道光束打击特效集合 */
public class BeaconFx {

    private static final Color CORE = Color.valueOf("eaffff");
    private static final Color GLOW = Color.valueOf("4fb8ff");
    private static final Color DARK = Color.valueOf("1c5fb0");

    /** 从斜上方快速落下的蓝色光束,倾斜角度制造轨道打击的3D坠落感 */
    public static final Effect beam = new Effect(26f, 420f, e -> {
        Draw.z(Layer.flyingUnit + 2f);

        float fin = e.fin();
        float fout = e.fout();

        // 倾斜角度与长度随时间收缩,模拟光束从高空斜向砸落到地面的过程
        float tiltAngle = 62f;
        float maxLen = 340f;
        float len = Mathf.lerp(maxLen, 40f, fin);

        float ox = Mathf.cosDeg(tiltAngle) * len;
        float oy = Mathf.sinDeg(tiltAngle) * len;

        float sx = e.x + ox, sy = e.y + oy;

        // 外层辉光
        Draw.color(GLOW);
        Draw.alpha(0.35f * fout);
        Lines.stroke(14f * fout + 3f);
        Lines.line(sx, sy, e.x, e.y);

        // 主体光束
        Draw.color(GLOW, CORE, 0.4f);
        Draw.alpha(0.8f * fout);
        Lines.stroke(6f * fout + 1.5f);
        Lines.line(sx, sy, e.x, e.y);

        // 明亮核心
        Draw.color(CORE);
        Draw.alpha(fout);
        Lines.stroke(2f);
        Lines.line(sx, sy, e.x, e.y);

        Draw.reset();
    });

    /** 光束落地瞬间的蓝色冲击波 */
    public static final Effect impact = new Effect(34f, 140f, e -> {
        Draw.z(Layer.flyingUnit + 2f);

        float fin = e.fin();
        float fout = e.fout();

        // 中心亮闪
        Draw.color(CORE);
        Draw.alpha(fout);
        Fill.circle(e.x, e.y, 10f * fout);

        // 主扩散环
        Draw.color(GLOW);
        Draw.alpha(0.8f * fout);
        Lines.stroke(3f * fout + 1f);
        Lines.circle(e.x, e.y, Mathf.lerp(4f, 70f, fin));

        // 外圈余晖
        Draw.color(DARK);
        Draw.alpha(0.4f * fout);
        Lines.stroke(2f);
        Lines.circle(e.x, e.y, Mathf.lerp(10f, 90f, fin));

        Draw.reset();
    });
}