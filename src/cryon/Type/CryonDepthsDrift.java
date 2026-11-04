package cryon.Type;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Rect;
import arc.util.Time;
import mindustry.gen.WeatherState;
import mindustry.type.Weather;

/** Small suspended specks and bubbles; the seabed remains readable through the water. */
public class CryonDepthsDrift extends Weather{
    private static final int maxCells = 512;
    private final Rect bounds = new Rect();
    private final Color tint = Color.valueOf("a4d5d0");

    public CryonDepthsDrift(){
        super("abyssal-drift");
        hidden = true;
    }

    @Override public void drawOver(WeatherState state){
        float alpha = state.opacity * state.intensity * opacityMultiplier
                * (mindustry.Vars.renderer == null ? 1f : mindustry.Vars.renderer.weatherAlpha);
        if(alpha <= 0.001f) return;
        Core.camera.bounds(bounds);
        float span = particleSpan(bounds.width, bounds.height), scale = span / 100f;
        int minX = Mathf.floor(bounds.x / span) - 1, maxX = Mathf.ceil((bounds.x + bounds.width) / span) + 1;
        int minY = Mathf.floor(bounds.y / span) - 1, maxY = Mathf.ceil((bounds.y + bounds.height) / span) + 1;
        Draw.color(tint, alpha * 0.18f);
        for(int x = minX; x <= maxX; x++) for(int y = minY; y <= maxY; y++){
            long seed = x * 73856093L ^ y * 19349663L;
            float px = x * span + Mathf.randomSeed(seed, span * 0.08f, span * 0.92f) + Mathf.sin(Time.time + seed % 127, 140f, 5f * scale);
            float py = y * span + Mathf.mod(Mathf.randomSeed(seed + 4, 0f, span) + Time.time * 0.035f, span);
            float radius = Mathf.randomSeed(seed + 7, 0.35f, 0.9f) * scale;
            if(Mathf.randomSeed(seed + 9) < 0.2f){
                Lines.stroke(0.25f);
                Lines.poly(px, py, 8, radius * 1.7f);
            }else Fill.poly(px, py, 8, radius);
        }
        Draw.reset();
    }

    static float particleSpan(float width, float height){
        float span = 100f;
        // Include padding and rounding at arbitrary camera positions in the budget.
        while((Math.ceil(width / span) + 4d) * (Math.ceil(height / span) + 4d) > maxCells) span *= 1.25f;
        return span;
    }
}
