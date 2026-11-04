package cryon.Content;

import arc.Events;
import cryon.Type.PorphyraRinglightWeather;
import mindustry.Vars;
import mindustry.game.EventType.SaveLoadEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Rules;
import mindustry.type.Planet;
import mindustry.type.Weather;

public class PorphyraEnvironment{
    public static PorphyraRinglightWeather ringlight;

    public static void load(){
        ringlight = new PorphyraRinglightWeather("porphyra-ringlight");
        ringlight.hidden = true;
        ringlight.opacityMultiplier = 1f;
    }

    public static void init(){
        Events.on(WorldLoadEvent.class, event -> refresh());
        Events.on(SaveLoadEvent.class, event -> refresh());
    }

    public static void applyWeather(Rules rules){
        if(ringlight == null) return;
        Weather.WeatherEntry entry = rules.weather.find(e -> e.weather == ringlight);
        if(entry == null){
            entry = new Weather.WeatherEntry(ringlight);
            rules.weather.add(entry);
        }
        entry.always = true;
        entry.intensity = 1f;
        entry.cooldown = 0f;
    }

    private static void refresh(){
        if(Vars.state == null || (Vars.net != null && Vars.net.client())) return;
        Rules rules = Vars.state.rules;
        Planet planet = rules.sector == null ? rules.planet : rules.sector.planet;
        if(planet != null && planet.name.equals("cryon-porphyra")) applyWeather(rules);
    }
}
