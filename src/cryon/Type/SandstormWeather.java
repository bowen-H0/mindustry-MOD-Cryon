package cryon.Type;

import arc.Core;
import mindustry.Vars;
import mindustry.gen.WeatherState;
import mindustry.type.weather.ParticleWeather;

/** Bounds the native particle loop even at high resolutions and distant zoom. */
public class SandstormWeather extends ParticleWeather {
    public int maxParticles = 192;

    public SandstormWeather(String name){
        super(name);
    }

    @Override
    public void drawOver(WeatherState state){
        float originalDensity = density;
        boolean originalParticles = drawParticles;
        // Match Weather.drawParticles' padded simulation area, not camera bounds.
        float scale = Math.max(0.001f, Vars.renderer.minScale());
        float width = Core.graphics.getWidth() / scale + sizeMax * 3f;
        float height = Core.graphics.getHeight() / scale + sizeMax * 3f;
        if(maxParticles <= 0){
            drawParticles = false;
        }else{
            density = Math.max(density, width * height * Math.max(0f, state.intensity) / maxParticles);
        }
        try{
            super.drawOver(state);
        }finally{
            density = originalDensity;
            drawParticles = originalParticles;
        }
    }
}
