package cryon.Story;

import arc.Core;
import arc.Events;
import arc.audio.Music;
import arc.math.Mathf;
import cryon.Script.VirusIntrusionScript;
import mindustry.audio.SoundControl;
import mindustry.game.EventType.*;
import mindustry.gen.Musics;

import static mindustry.Vars.*;

/** Encounter-only use of native music assets; leaves campaign playlists unchanged. */
public final class IntrusionMusic {
    private static boolean installed, owned, paused, oldLooping;
    private static float oldChance, oldWaveChance, oldVolume, fade;
    private static Music current;
    private static SoundControl owner;

    private IntrusionMusic() {}

    public static void install(){
        if(installed || headless) return;
        installed = true;
        Events.run(Trigger.update, IntrusionMusic::update);
        Events.on(ResetEvent.class, event -> release());
        Events.on(WorldLoadEvent.class, event -> release());
        Core.app.addListener(new arc.ApplicationListener(){
            @Override public void dispose(){ release(); }
        });
    }

    private static void update(){
        if(headless || state == null || !state.isGame() || state.isEditor() || !IntrusionMap.active()
                || control == null || control.sound == null){
            release();
            return;
        }
        if(!owned || owner != control.sound){
            release();
            owner = control.sound;
            oldChance = owner.musicChance;
            oldWaveChance = owner.musicWaveChance;
            owned = true;
        }
        // The encounter owns music selection, including its deliberate silent intervals.
        owner.musicChance = owner.musicWaveChance = 0f;
        owner.stop();
        VirusIntrusionScript script = VirusIntrusionScript.current();
        Music wanted = script == null || script.finished() || script.transforming() || script.disarmed()
                ? null : script.enraged() ? Musics.boss2 : Musics.boss1;
        if(wanted != current){
            stopTrack();
            current = wanted;
            if(current != null){
                oldLooping = current.isLooping();
                oldVolume = current.getVolume();
                current.setLooping(true);
                current.setVolume(0f);
                current.play();
            }
        }
        if(current == null) return;
        if(state.isPaused()){
            if(!paused){ current.pause(true); paused = true; }
            return;
        }
        if(paused){ current.pause(false); paused = false; }
        else if(!current.isPlaying()) current.play();
        fade = Math.min(1f, fade + Math.min(Core.graphics.getDeltaTime(), 0.1f) / 0.8f);
        current.setVolume(Mathf.clamp(Core.settings.getInt("musicvol", 100) / 100f) * fade);
    }

    private static void stopTrack(){
        if(current != null){
            current.stop();
            current.setLooping(oldLooping);
            current.setVolume(oldVolume);
            current = null;
        }
        paused = false;
        fade = 0f;
    }

    private static void release(){
        stopTrack();
        if(owned && owner != null){
            owner.musicChance = oldChance;
            owner.musicWaveChance = oldWaveChance;
        }
        owner = null;
        owned = false;
    }
}
