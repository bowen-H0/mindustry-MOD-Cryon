package cryon.Fx;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.graphics.*;

/** 核打击落点标记：发光 + 动画，克制不抢眼，不做全屏覆盖。 */
public class NukeMarkerFx{
    private static final Color col = Color.valueOf("ff4433");

    /** 在世界绘制阶段调用，替换原来的红框。 */
    public static void draw(float tx, float ty, float radius, float remaining, float total, int seed){
        float t = Time.time + seed * 17f;
        float progress = Mathf.clamp(1f - remaining / Math.max(total, 1f)); // 0 -> 1
        float urgent = Mathf.clamp(1f - remaining / 300f);                  // 最后5秒逐渐加急
        float pulse = 0.75f + 0.25f * Mathf.absin(t, 40f / (1f + urgent * 2.5f), 1f);

        float z = Draw.z(), stroke = Lines.getStroke();
        try{
            Draw.z(Layer.light + 1f);
            Draw.blend(Blending.additive);

            // 地面极淡的底色，只标出范围
            Draw.color(col, 0.05f * pulse);
            Fill.circle(tx, ty, radius);

            // 外圈：四段慢速旋转的弧
            Draw.color(col, 0.55f * pulse);
            Lines.stroke(1.2f);
            for(int i = 0; i < 4; i++){
                Lines.arc(tx, ty, radius, 0.16f, t * 0.6f + i * 90f);
            }

            // 收缩环：从边缘缩向中心，落地时正好收拢，用来读剩余时间
            float shrink = radius * (1f - progress);
            Draw.color(col, (0.25f + 0.25f * progress) * pulse);
            Lines.stroke(0.8f + progress);
            Lines.circle(tx, ty, Math.max(shrink, 2f));

            // 四角括号，随呼吸轻微收放
            float b = radius * (1f + 0.02f * Mathf.sin(t / 8f));
            float l = Math.min(radius * 0.25f, 14f);
            Draw.color(col, 0.8f * pulse);
            Lines.stroke(1.5f);
            for(int sx = -1; sx <= 1; sx += 2){
                for(int sy = -1; sy <= 1; sy += 2){
                    float cx = tx + sx * b, cy = ty + sy * b;
                    Lines.line(cx, cy, cx - sx * l, cy);
                    Lines.line(cx, cy, cx, cy - sy * l);
                }
            }

            // 中心十字
            Draw.color(col, 0.7f * pulse);
            Lines.stroke(1f);
            Lines.line(tx - 6f, ty, tx + 6f, ty);
            Lines.line(tx, ty - 6f, tx, ty + 6f);
        }finally{
            Draw.blend();
            Draw.reset();
            Lines.stroke(stroke);
            Draw.z(z);
        }
    }

    /** 黑暗地图里照亮落点周围，在 drawLight 阶段调用（可选）。 */
    public static void drawLight(float tx, float ty, float radius, float remaining){
        float urgent = Mathf.clamp(1f - remaining / 300f);
        float pulse = 0.7f + 0.3f * Mathf.absin(Time.time, 40f / (1f + urgent * 2.5f), 1f);
        Drawf.light(tx, ty, radius * 1.3f, col, 0.18f * pulse);
    }
}