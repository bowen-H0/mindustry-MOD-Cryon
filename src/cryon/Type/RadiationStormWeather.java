package cryon.Type;

import arc.graphics.Color;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.gen.WeatherState;
import mindustry.gen.Sounds;
import mindustry.type.weather.ParticleWeather;

/** Toxic green wind with a slowly pulsing violet ionization haze. */
public class RadiationStormWeather extends ParticleWeather {
    public Color ionColor = Color.valueOf("9780dc");
    private final Color animatedNoise = new Color();

    public RadiationStormWeather(String name){
        super(name);
        sound = Sounds.wind;
        soundVol = 0.45f;
    }

    @Override
    public void drawOver(WeatherState state){
        Color originalNoise = noiseColor;
        float originalOpacity = opacityMultiplier;
        float pulse = 0.5f + Mathf.sin(Time.time, 100f, 0.5f);
        noiseColor = animatedNoise.set(originalNoise).lerp(ionColor, pulse * 0.6f);
        opacityMultiplier *= 0.8f + pulse * 0.2f;
        try{
            super.drawOver(state);
        }finally{
            noiseColor = originalNoise;
            opacityMultiplier = originalOpacity;
        }
    }
}
