package cryon.Content;

import arc.graphics.Color;
import arc.util.Time;
import mindustry.content.Weathers;
import mindustry.game.Rules;
import mindustry.gen.Sounds;
import mindustry.type.Weather;
import mindustry.type.weather.ParticleWeather;
import mindustry.world.meta.Attribute;

public class AravisEnvironment {
    public static ParticleWeather sandstorm;

    public static void load(){
        sandstorm = new ParticleWeather("aravis-sandstorm"){{
            color = Color.valueOf("d5b486");
            noiseColor = Color.valueOf("b79365");
            particleRegion = "particle";
            drawNoise = true;
            useWindVector = true;
            sizeMin = 40f;
            sizeMax = 100f;
            minAlpha = 0f;
            maxAlpha = 0.18f;
            density = 1800f;
            baseSpeed = 5.4f;
            opacityMultiplier = 0.28f;
            force = 0.08f;
            attrs.set(Attribute.light, -0.12f);
            attrs.set(Attribute.water, -0.1f);
            sound = Sounds.wind;
            soundVol = 0.6f;
            duration = 90f * Time.toSeconds;
        }};
    }

    public static void applyWeather(Rules rules){
        boolean dunes = rules.sector != null && rules.sector.preset != null
                && rules.sector.preset.name.equals("cryon-dunes");
        // Replace the automatically selected vanilla sandstorm; keep other map weather.
        rules.weather.removeAll(entry -> entry.weather == Weathers.sandstorm);
        if(rules.weather.contains(entry -> entry.weather == sandstorm)) return;

        float minute = 60f * Time.toSeconds;
        Weather.WeatherEntry entry = new Weather.WeatherEntry(sandstorm,
                (dunes ? 3f : 5f) * minute, (dunes ? 5f : 8f) * minute,
                60f * Time.toSeconds, 100f * Time.toSeconds);
        rules.weather.add(entry);
    }
}
