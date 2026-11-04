package cryon.Type;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.util.Time;
import mindustry.Vars;
import mindustry.gen.WeatherState;
import mindustry.type.Weather;

/** Slow bands of light projected by Porphyra's rings onto the terrain. */
public class PorphyraRinglightWeather extends Weather{
    private final Color light = Color.valueOf("c5a8ef");

    public PorphyraRinglightWeather(String name){ super(name); }

    @Override public void drawOver(WeatherState state){
        if(Vars.renderer == null) return;
        float alpha = Math.min(1f, state.opacity * Vars.renderer.weatherAlpha * opacityMultiplier) * 0.085f;
        if(alpha <= 0.001f) return;
        float left = Core.camera.position.x - Core.camera.width / 2f;
        float right = left + Core.camera.width;
        float bottom = Core.camera.position.y - Core.camera.height / 2f;
        float top = bottom + Core.camera.height;
        float period = 270f, slope = 0.27f, shift = (Time.time * 0.42f) % period;
        // Use world coordinates so the bands stay on the terrain while the camera moves.
        int first = (int)Math.floor((bottom - right * slope - shift - 90f) / period);
        int last = (int)Math.ceil((top - left * slope - shift + 90f) / period);
        Draw.color(light, alpha);
        for(int band = first; band <= last; band++){
            float y = band * period + shift;
            float yLeft = y + left * slope, yRight = y + right * slope;
            Fill.quad(left, yLeft - 34f, right, yRight - 34f,
                    right, yRight + 34f, left, yLeft + 34f);
        }
        Draw.reset();
    }
}
