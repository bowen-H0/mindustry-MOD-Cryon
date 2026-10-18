package cryon.Content;

import arc.Events;
import arc.graphics.Color;
import mindustry.Vars;
import mindustry.game.EventType.SaveLoadEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.type.Planet;
import arc.util.Time;
import mindustry.content.Weathers;
import mindustry.game.Rules;
import mindustry.gen.Sounds;
import mindustry.type.Weather;
import mindustry.type.weather.ParticleWeather;
import mindustry.world.meta.Attribute;
import cryon.Type.SandstormWeather;

public class AravisEnvironment {
    public static ParticleWeather sandstorm;

    public static void load(){
        sandstorm = new SandstormWeather("aravis-sandstorm"){{
            color = Color.valueOf("d5b486");
            noiseColor = Color.valueOf("b79365");
            particleRegion = "particle";
            drawNoise = true;
            useWindVector = true;
            sizeMin = 40f;
            sizeMax = 100f;
            minAlpha = 0f;
            maxAlpha = 0.18f;
            // Native density is area per particle: larger values mean fewer particles.
            density = 7200f;
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

    public static void init(){
        Events.on(WorldLoadEvent.class, event -> refreshLoadedWorld());
        Events.on(SaveLoadEvent.class, event -> refreshLoadedWorld());
    }

    private static boolean isAravis(Rules rules){
        Planet planet = rules.sector == null ? rules.planet : rules.sector.planet;
        return planet != null && planet.name.equals("cryon-aravis");
    }

    public static void applyWeather(Rules rules){
        if(sandstorm == null) return;
        Weather.WeatherEntry entry = rules.weather.find(weather -> weather.weather == sandstorm);
        // Replace the old intermittent schedule and remove duplicate/vanilla storms.
        rules.weather.removeAll(weather -> weather.weather == Weathers.sandstorm
                || weather.weather == sandstorm);
        if(entry == null) entry = new Weather.WeatherEntry(sandstorm);
        entry.always = true;
        entry.intensity = 1f;
        entry.cooldown = 0f;
        rules.weather.add(entry);
    }

    public static void refreshLoadedWorld(){
        if(Vars.state == null || !isAravis(Vars.state.rules)
                || (Vars.net != null && Vars.net.client())) return;
        applyWeather(Vars.state.rules);
        // Older saves may also contain an already active vanilla sandstorm.
        Weathers.sandstorm.remove();
    }
}
