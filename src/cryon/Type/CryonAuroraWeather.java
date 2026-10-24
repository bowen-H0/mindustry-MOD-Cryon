package cryon.Type;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Mathf;
import arc.math.Interp;
import arc.scene.ui.layout.Scl;
import arc.util.Time;
import mindustry.Vars;
import mindustry.gen.WeatherState;
import mindustry.type.Weather;

/** Bright, translucent curtains built from a fixed-size mesh, without texture assets. */
public class CryonAuroraWeather extends Weather {
    public Color lowerColor = Color.valueOf("58ffc1");
    public Color upperColor = Color.valueOf("a48bff");
    public Color coreColor = Color.valueOf("a4f4ff");
    public float brightness = 0.65f;
    /** Camera scales before UI scaling: lower values mean a more distant view. */
    public float fullyVisibleZoom = 2f, hiddenZoom = 3f;

    public CryonAuroraWeather(String name){
        super(name);
    }

    @Override
    public void drawOver(WeatherState state){
        float zoom = Vars.renderer.getDisplayScale() / Scl.scl(1f);
        float zoomVisibility = Interp.smooth.apply(Mathf.clamp(
                (hiddenZoom - zoom) / Math.max(0.001f, hiddenZoom - fullyVisibleZoom)));
        float opacity = Mathf.clamp(state.opacity * Vars.renderer.weatherAlpha * opacityMultiplier)
                * zoomVisibility;
        if(opacity <= 0.001f) return;
        float width = Core.camera.width, height = Core.camera.height;
        float left = Core.camera.position.x - width / 2f;
        float bottom = Core.camera.position.y - height / 2f;
        float time = Time.time;
        float pulse = 0.8f + Mathf.sin(time, 170f, 0.2f);
        Blending previousBlend = Draw.getBlend();
        Draw.blend(Blending.additive);
        try{
            for(int curtain = 0; curtain < 3; curtain++){
                float phase = curtain * 2.3f;
                float strength = opacity * brightness * pulse * (1f - curtain * 0.18f);
                float base = bottom + height * (0.3f + curtain * 0.23f);
                float extent = height * (0.2f + curtain * 0.025f);
                // Constant mesh size, independent of map size and camera zoom.
                for(int segment = 0; segment < 64; segment++){
                    float x1 = left + width * segment / 64f;
                    float x2 = left + width * (segment + 1) / 64f;
                    float wave1 = wave(x1, time, phase);
                    float wave2 = wave(x2, time, phase);
                    float y1 = base + height * 0.08f * wave1;
                    float y2 = base + height * 0.08f * wave2;
                    float fold1 = 0.55f + 0.45f * Mathf.sin(x1 / 30f + phase + time / 90f);
                    float fold2 = 0.55f + 0.45f * Mathf.sin(x2 / 30f + phase + time / 90f);
                    float bright1 = strength * (0.5f + fold1 * 0.5f);
                    float bright2 = strength * (0.5f + fold2 * 0.5f);
                    float core1 = packed(coreColor, bright1), core2 = packed(coreColor, bright2);
                    float lower1 = packed(lowerColor, bright1 * 0.5f);
                    float lower2 = packed(lowerColor, bright2 * 0.5f);
                    float upper1 = packed(upperColor, bright1 * 0.35f);
                    float upper2 = packed(upperColor, bright2 * 0.35f);
                    float clear = Color.clearFloatBits;
                    strip(x1, x2, y1 - extent * 0.3f, y2 - extent * 0.3f,
                            y1 - extent * 0.08f, y2 - extent * 0.08f, clear, clear, lower1, lower2);
                    strip(x1, x2, y1 - extent * 0.08f, y2 - extent * 0.08f,
                            y1, y2, lower1, lower2, core1, core2);
                    strip(x1, x2, y1, y2,
                            y1 + extent * 0.4f, y2 + extent * 0.4f, core1, core2, upper1, upper2);
                    strip(x1, x2, y1 + extent * 0.4f, y2 + extent * 0.4f,
                            y1 + extent, y2 + extent, upper1, upper2, clear, clear);
                }
            }
        }finally{
            Draw.blend(previousBlend);
            Draw.reset();
        }
    }

    private static float wave(float x, float time, float phase){
        return Mathf.sin(x / 230f + time / 210f + phase)
                + 0.35f * Mathf.sin(x / 85f - time / 150f + phase);
    }

    private static float packed(Color color, float alpha){
        return Color.toFloatBits(color.r, color.g, color.b, Mathf.clamp(alpha));
    }

    private static void strip(float x1, float x2, float low1, float low2,
                              float high1, float high2, float lc1, float lc2, float hc1, float hc2){
        Fill.quad(x1, low1, lc1, x2, low2, lc2, x2, high2, hc2, x1, high1, hc1);
    }
}
