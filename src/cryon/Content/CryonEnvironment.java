package cryon.Content;

import arc.Events;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.ctype.ContentType;
import mindustry.game.EventType.SaveLoadEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Rules;
import mindustry.gen.Groups;
import mindustry.gen.WeatherState;
import mindustry.type.Planet;
import mindustry.type.Weather;

/** Canonical campaign weather, also applied when loading older sector saves. */
public class CryonEnvironment {
    public static void init(){
        Events.on(WorldLoadEvent.class, event -> refreshLoadedWorld());
        Events.on(SaveLoadEvent.class, event -> refreshLoadedWorld());
    }

    private static boolean isCryon(Rules rules){
        Planet planet = rules.sector == null ? rules.planet : rules.sector.planet;
        return planet != null && planet.name.equals("cryon-cryon");
    }

    public static void applyWeather(Rules rules){
        if(!isCryon(rules)) return;
        Weather ice = Vars.content.getByName(ContentType.weather, "cryon-ice-crystal-fog");
        Weather aurora = Vars.content.getByName(ContentType.weather, "cryon-polar-aurora");
        Weather fallout = Vars.content.getByName(ContentType.weather, "cryon-deuterium-fallout");
        Weather radiation = Vars.content.getByName(ContentType.weather, "cryon-radiation-storm");
        if(ice == null || aurora == null || fallout == null || radiation == null) return;

        Weather.WeatherEntry iceEntry = rules.weather.find(entry -> entry.weather == ice);
        Weather.WeatherEntry auroraEntry = rules.weather.find(entry -> entry.weather == aurora);
        Weather.WeatherEntry falloutEntry = rules.weather.find(entry -> entry.weather == fallout);
        Weather.WeatherEntry radiationEntry = rules.weather.find(entry -> entry.weather == radiation);
        if(iceEntry == null) iceEntry = new Weather.WeatherEntry(ice);
        iceEntry.always = true;
        iceEntry.intensity = 1f;
        iceEntry.cooldown = 0f;
        if(auroraEntry == null){
            float second = Time.toSeconds;
            auroraEntry = new Weather.WeatherEntry(aurora,
                    120f * second, 240f * second, 90f * second, 180f * second);
        }
        if(falloutEntry == null){
            float second = Time.toSeconds;
            falloutEntry = new Weather.WeatherEntry(fallout,
                    240f * second, 420f * second, 60f * second, 120f * second);
        }
        if(radiationEntry == null){
            float second = Time.toSeconds;
            radiationEntry = new Weather.WeatherEntry(radiation,
                    480f * second, 720f * second, 30f * second, 60f * second);
        }

        // Preserve existing timers while removing vanilla weather and duplicate entries.
        rules.weather.clear();
        rules.weather.add(iceEntry, auroraEntry);
        rules.weather.add(falloutEntry, radiationEntry);
    }

    public static void refreshLoadedWorld(){
        if(Vars.state == null || !isCryon(Vars.state.rules)
                || (Vars.net != null && Vars.net.client())) return;
        applyWeather(Vars.state.rules);

        // A save may contain an already active snowstorm as well as its schedule.
        Seq<WeatherState> obsolete = new Seq<>();
        Groups.weather.each(state -> {
            String name = state.weather.name;
            if(!name.equals("cryon-ice-crystal-fog") && !name.equals("cryon-polar-aurora")
                    && !name.equals("cryon-deuterium-fallout") && !name.equals("cryon-radiation-storm")){
                obsolete.add(state);
            }
        });
        obsolete.each(WeatherState::remove);
    }
}
