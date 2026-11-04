package cryon.Features;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Time;
import cryon.Content.PlanetEnvironmentStatuses;
import cryon.Type.CrystalStatueBlock;
import mindustry.Vars;
import mindustry.content.Planets;
import mindustry.game.EventType.*;
import mindustry.game.Rules;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.type.Planet;
import mindustry.type.UnitType;
import mindustry.type.unit.ErekirUnitType;
import mindustry.world.meta.Env;
import mindustry.world.meta.Stat;

/** Planet traits can also be overridden per-map through Rules.tags. */
public final class PlanetTraits{
    public enum Trait{ heat, cold, recrystallization, deepsea }
    private static final ObjectMap<Planet, ObjectSet<Trait>> traits = new ObjectMap<>();
    private static final ObjectSet<String> porphyra = ObjectSet.with(
            "thread", "process", "kernel", "receptor", "synapse", "ganglion", "cortex", "connectome",
            "monomer", "dimer", "oligomer", "polymer", "macromolecule",
            "particle", "momentum", "inertia", "gravity", "singularity");
    private static final ObjectSet<String> aravis = ObjectSet.with(
            "jaspis", "obsidianum", "nitrum", "hematites", "vindex", "imperator");
    private static final Stat heatAdaptation = new Stat("cryon-heat-adaptation");
    private static final Stat coldAdaptation = new Stat("cryon-cold-adaptation");
    private static final Stat crystalAdaptation = new Stat("cryon-crystal-adaptation");
    private static final Stat deepSeaAdaptation = new Stat("cryon-deepsea-adaptation");
    private static final Color heatTint = Color.valueOf("f36d4d");
    private static final Color coldTint = Color.valueOf("9fcff5");
    private static final Color crystalTint = Color.valueOf("b080dd");
    private static float exposureTimer;
    private static final Seq<Unit> pendingStatues = new Seq<>();

    private PlanetTraits(){}

    public static void set(Planet planet, Trait... values){
        if(planet != null) traits.put(planet, ObjectSet.with(values));
    }

    public static boolean active(Rules rules, Trait trait){
        String key = "cryon-environment-" + trait.name();
        if(rules.tags.containsKey(key)) return Boolean.parseBoolean(rules.tags.get(key));
        Planet planet = rules.sector == null ? rules.planet : rules.sector.planet;
        if(planet == null) return false;
        ObjectSet<Trait> values = traits.get(planet);
        return values != null && values.contains(trait);
    }

    public static void configure(){
        set(Vars.content.planet("cryon-cryon"), Trait.cold);
        set(Vars.content.planet("cryon-cryon-depths"), Trait.cold, Trait.deepsea);
        set(Vars.content.planet("cryon-aravis"), Trait.heat);
        Planet finalPlanet = Vars.content.planet("cryon-porphyra");
        set(finalPlanet, Trait.recrystallization);
        if(finalPlanet != null) finalPlanet.defaultEnv = Env.terrestrial | Env.scorching;
        for(UnitType type : Vars.content.units()){
            String name = type.name.startsWith("cryon-") ? type.name.substring(6) : type.name;
            boolean local = type.minfo.mod != null && "cryon".equals(type.minfo.mod.name);
            boolean isPorphyra = local && porphyra.contains(name);
            boolean isCryon = local && !isPorphyra && !aravis.contains(name);
            boolean isErekir = type instanceof ErekirUnitType
                    || (type.isVanilla() && type.shownPlanets != null && type.shownPlanets.contains(Planets.erekir));
            if(isPorphyra){
                type.immunities.add(PlanetEnvironmentStatuses.crystallized);
                type.envDisabled &= ~Env.scorching;
                type.envEnabled |= Env.scorching;
            }
            if(type.supportsEnv(Env.terrestrial | Env.scorching) || !isCryon)
                type.immunities.add(PlanetEnvironmentStatuses.heatStress);
            else type.immunities.remove(PlanetEnvironmentStatuses.heatStress);
            if(isPorphyra || isCryon || !isErekir) type.immunities.add(PlanetEnvironmentStatuses.coldStress);
            else type.immunities.remove(PlanetEnvironmentStatuses.coldStress);
            if(local || isErekir){
                type.checkStats();
                type.stats.remove(heatAdaptation);
                type.stats.remove(coldAdaptation);
                type.stats.remove(crystalAdaptation);
                type.stats.remove(deepSeaAdaptation);
                type.stats.add(heatAdaptation, type.immunities.contains(PlanetEnvironmentStatuses.heatStress));
                type.stats.add(coldAdaptation, type.immunities.contains(PlanetEnvironmentStatuses.coldStress));
                type.stats.add(crystalAdaptation, type.immunities.contains(PlanetEnvironmentStatuses.crystallized));
                type.stats.add(deepSeaAdaptation, DeepSeaRules.allowed(type));
            }
        }
        describe("cryon-cryon", "cryon.environment.planet-cold");
        describe("cryon-cryon-depths", "cryon.environment.planet-deepsea");
        describe("cryon-aravis", "cryon.environment.planet-heat");
        describe("cryon-porphyra", "cryon.environment.planet-final");
    }

    private static void describe(String planetName, String key){
        Planet planet = Vars.content.planet(planetName);
        if(planet == null) return;
        String text = Core.bundle.get(key, key);
        if(planet.description == null || planet.description.isEmpty()) planet.description = text;
        else if(!planet.description.contains(text)) planet.description += "\n\n" + text;
    }

    public static void apply(Unit unit, Rules rules){
        if(unit.dead) return;
        if(active(rules, Trait.heat) && !unit.type.supportsEnv(Env.terrestrial | Env.scorching))
            unit.apply(PlanetEnvironmentStatuses.heatStress, 60f);
        if(active(rules, Trait.cold)) unit.apply(PlanetEnvironmentStatuses.coldStress, 60f);
        if(active(rules, Trait.deepsea) && !DeepSeaRules.allowed(unit.type)) unit.apply(cryon.Content.SubmarineContent.pressureStatus, 60f);
        if(active(rules, Trait.recrystallization)) unit.apply(PlanetEnvironmentStatuses.crystallized);
    }

    public static void refresh(){
        if(Vars.state == null) return;
        Rules rules = Vars.state.rules;
        Planet planet = rules.sector == null ? rules.planet : rules.sector.planet;
        if(planet != null && planet.name.equals("cryon-porphyra")) rules.env |= Env.scorching;
        if(Vars.net == null || !Vars.net.client()) Groups.unit.each(unit -> apply(unit, rules));
    }

    public static void install(){
        ColdEnvironmentAudio.install();
        Events.on(WorldLoadEvent.class, event -> refresh());
        Events.on(SaveLoadEvent.class, event -> refresh());
        Events.on(UnitCreateEvent.class, event -> {
            if(Vars.state != null && (Vars.net == null || !Vars.net.client())) apply(event.unit, Vars.state.rules);
        });
        Events.on(ResetEvent.class, event -> {
            exposureTimer = 0f;
            pendingStatues.clear();
            PlanetEnvironmentStatuses.crystallized.clear();
        });
        Events.on(UnitDestroyEvent.class, event -> PlanetEnvironmentStatuses.crystallized.forget(event.unit));
        Events.run(Trigger.afterGameUpdate, () -> {
            if(Vars.state == null || !Vars.state.isGame() || Vars.state.isPaused()) return;
            exposureTimer += Time.delta;
            boolean expose = exposureTimer >= 30f && (Vars.net == null || !Vars.net.client());
            if(expose) exposureTimer = 0f;
            pendingStatues.clear();
            // Ocean/cold maps normally have no crystal conversions. Do not scan every unit
            // on the 29 intervening ticks when neither exposure nor conversion is needed.
            if(!expose && !PlanetEnvironmentStatuses.crystallized.hasFrozenUnits()) return;
            Groups.unit.each(unit -> {
                if(expose) apply(unit, Vars.state.rules);
                if(unit.hasEffect(PlanetEnvironmentStatuses.crystallized)){
                    PlanetEnvironmentStatuses.crystallized.freeze(unit);
                    pendingStatues.add(unit);
                }
            });
            if(Vars.net == null || !Vars.net.client()) pendingStatues.each(CrystalStatueBlock::convert);
            pendingStatues.clear();
        });
        Events.run(Trigger.draw, () -> {
            if(Vars.headless || Vars.state == null || !Vars.state.isGame()) return;
            boolean heat = active(Vars.state.rules, Trait.heat) || (Vars.state.rules.env & Env.scorching) != 0;
            boolean cold = active(Vars.state.rules, Trait.cold);
            boolean crystal = active(Vars.state.rules, Trait.recrystallization);
            if(!heat && !cold && !crystal) return;
            Draw.draw(Layer.weather - 1f, () -> {
                try{
                    if(heat) tint(heatTint, 0.055f);
                    if(cold) tint(coldTint, 0.035f);
                    if(crystal) tint(crystalTint, 0.07f);
                }finally{ Draw.reset(); }
            });
        });
    }

    private static void tint(Color color, float opacity){
        Draw.color(color, opacity);
        Fill.crect(Core.camera.position.x - Core.camera.width / 2f,
                Core.camera.position.y - Core.camera.height / 2f, Core.camera.width, Core.camera.height);
    }
}
