package cryon.Features;

import arc.Core;
import arc.Events;
import arc.audio.AudioBus;
import arc.audio.Filters.BiquadFilter;
import arc.audio.Filters.FreeverbFilter;
import mindustry.Vars;
import mindustry.game.EventType.ResetEvent;
import mindustry.game.EventType.Trigger;

/** Gentle muffling and a spacious tail for cold worlds, on battlefield sounds only. */
public final class ColdEnvironmentAudio{
    // Slot zero belongs to Mindustry's pause filter.
    private static final int lowpassSlot = 1, reverbSlot = 2;
    private static AudioBus bus;
    private static BiquadFilter lowpass;
    private static FreeverbFilter reverb;
    private static boolean installed;
    private static float blend;

    private ColdEnvironmentAudio(){}

    public static void install(){
        if(installed || Vars.headless) return;
        installed = true;
        Events.run(Trigger.update, ColdEnvironmentAudio::update);
        Events.on(ResetEvent.class, event -> release());
    }

    private static void update(){
        if(Vars.state == null || !Vars.state.isGame() || Vars.state.isEditor()
                || !PlanetTraits.active(Vars.state.rules, PlanetTraits.Trait.cold)
                || Core.audio == null || !Core.audio.initialized()){
            release();
            return;
        }
        if(bus != Core.audio.soundBus){
            release();
            if(lowpass == null){
                lowpass = new BiquadFilter();
                lowpass.set(0, 2800f, 0.7f);
                reverb = new FreeverbFilter();
                reverb.set(0f, 0.8f, 0.65f, 1f);
            }
            bus = Core.audio.soundBus;
            bus.setFilter(lowpassSlot, lowpass);
            bus.setFilter(reverbSlot, reverb);
            bus.setFilterParam(lowpassSlot, 0, 0f);
            bus.setFilterParam(reverbSlot, 0, 0f);
        }
        blend = Math.min(1f, blend + Math.min(Core.graphics.getDeltaTime(), 0.1f) / 1.5f);
        bus.setFilterParam(lowpassSlot, 0, 0.65f * blend);
        bus.setFilterParam(reverbSlot, 0, 0.16f * blend);
    }

    private static void release(){
        if(bus != null){
            bus.setFilter(lowpassSlot, null);
            bus.setFilter(reverbSlot, null);
            bus = null;
        }
        blend = 0f;
    }
}
