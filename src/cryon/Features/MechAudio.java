package cryon.Features;

import arc.Core;
import arc.Events;
import arc.ApplicationListener;
import arc.audio.AudioBus;
import arc.audio.Sound;
import arc.math.Mathf;
import mindustry.Vars;
import mindustry.gen.*;
import mindustry.game.EventType.*;

/** Short spatial action layers; no persistent voices, with native distance/interval limits. */
public final class MechAudio{
    private static AudioBus dryImpactBus;
    private static boolean busHooks;
    public enum Cue{ready, light, reverse, heavy, hit, armour, parry, dashCharge, dashBurst, dashBrake, slaughter}
    public static Sound ready = Sounds.none, light = Sounds.none, reverse = Sounds.none, heavy = Sounds.none,
        hit = Sounds.none, armour = Sounds.none, parry = Sounds.none, dashCharge = Sounds.none,
        dashBurst = Sounds.none, dashBrake = Sounds.none, stepLight = Sounds.none, stepHeavy = Sounds.none,
        collapse = Sounds.none, slaughter = Sounds.none;

    private static Sound clip(String name, long interval){
        Sound sound = Vars.tree.loadSound("cryon-mech-" + name);
        sound.setMinInterval(interval);
        return sound;
    }

    public static void load(){
        if(Vars.headless) return;
        ready = clip("blade-ready", 100);
        light = clip("slash-light", 70); reverse = clip("slash-reverse", 70); heavy = clip("slash-heavy", 80);
        hit = clip("blade-hit", 85); armour = clip("blade-armour", 100); parry = clip("blade-parry", 100);
        dashCharge = clip("dash-charge", 80); dashBurst = clip("dash-burst", 90); dashBrake = clip("dash-brake", 90);
        stepLight = clip("step-light", 70); stepHeavy = clip("step-heavy", 85); collapse = clip("collapse", 120);
        slaughter = clip("slaughter-impact", 90);
        if(Core.audio != null && Core.audio.initialized()){
            if(dryImpactBus == null) dryImpactBus = new AudioBus();
            // ColdEnvironmentAudio adds reverb to soundBus; impacts must remain dry.
            armour.setBus(dryImpactBus);
            slaughter.setBus(dryImpactBus);
        }
        if(!busHooks){
            busHooks = true;
            Events.run(Trigger.update, MechAudio::updateDryBus);
            Events.on(ResetEvent.class, event -> { if(dryImpactBus != null) dryImpactBus.stop(); });
            Core.app.addListener(new ApplicationListener(){
                @Override public void dispose(){ if(dryImpactBus != null){dryImpactBus.dispose();dryImpactBus = null;} }
            });
        }
    }

    private static void updateDryBus(){
        if(dryImpactBus == null || Core.audio == null) return;
        if(Vars.state == null || !Vars.state.isGame()){ dryImpactBus.stop(); return; }
        dryImpactBus.play();
        Core.audio.setPaused(dryImpactBus.id, Vars.state.isPaused());
    }

    public static void play(Cue cue, Unit unit, float x, float y, float scale){
        if(Vars.headless || Core.audio == null || Vars.state == null || !Vars.state.isGame() || Vars.state.isPaused()
            || unit.dead || (Vars.player != null && unit.inFogTo(Vars.player.team()))) return;
        Sound sound;
        float volume;
        switch(cue){
            case ready: sound = ready; volume = 0.22f; break;
            case light: sound = light; volume = 0.44f; break;
            case reverse: sound = reverse; volume = 0.46f; break;
            case heavy: sound = heavy; volume = 0.54f; break;
            case hit: sound = hit; volume = 0.48f; break;
            case armour: sound = armour; volume = 0.94f; break;
            case slaughter: sound = slaughter; volume = 0.98f; break;
            case parry: sound = parry; volume = 0.58f; break;
            case dashCharge: sound = dashCharge; volume = 0.36f; break;
            case dashBurst: sound = dashBurst; volume = 0.58f; break;
            default: sound = dashBrake; volume = 0.42f;
        }
        volume *= Math.min(1.25f, 0.85f + scale * 0.15f);
        volume = Math.min(volume, 0.98f);
        if(sound == null || sound.calcVolume(x, y) * volume < 0.01f) return;
        if((cue == Cue.armour || cue == Cue.slaughter) && dryImpactBus != null) updateDryBus();
        float pitch = cue == Cue.slaughter ? Mathf.random(0.94f, 1.02f) : cue == Cue.armour ? Mathf.random(1.02f, 1.06f)
            : (scale >= 2 ? 0.86f : scale > 1 ? 0.96f : 1.08f) * Mathf.random(0.96f, 1.04f);
        sound.at(x, y, pitch, volume);
    }

    private MechAudio(){}
}
