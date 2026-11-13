package cryon.Features;

import arc.Core;
import arc.Events;
import arc.audio.Music;
import arc.files.Fi;
import arc.struct.Seq;
import arc.util.Log;
import cryon.CryonJavaMod;
import cryon.Story.IntrusionMap;
import mindustry.audio.SoundControl;
import mindustry.game.EventType.*;
import mindustry.gen.Musics;
import mindustry.type.Planet;

import static mindustry.Vars.*;

/** Native menu playback and planet-scoped campaign playlists. */
public final class CampaignMusic{
    private static boolean installed;
    private static Music[] tracks;
    private static MenuPlaylist menu;
    private static SoundControl owner;
    private static Seq<Music> ambient, dark, boss;

    private CampaignMusic(){}

    public static void install(){
        if(installed || headless) return;
        installed = true;
        Events.on(ClientLoadEvent.class, event -> {
            load();
            // Run after all ClientLoad listeners, when native music registration is complete.
            Core.app.post(CampaignMusic::startMenuPlayback);
        });
        Events.run(Trigger.update, () -> {
            if(tracks == null) return;
            updateCampaign();
            menu.update();
        });
        Events.on(ResetEvent.class, event -> restore());
        Core.app.addListener(new arc.ApplicationListener(){
            @Override public void dispose(){
                restore();
                if(menu != null) menu.stop();
                if(tracks != null) for(Music track : tracks) track.dispose();
            }
        });
    }

    private static void startMenuPlayback(){
        if(menu == null || state == null || !state.isMenu()
                || control == null || control.sound == null) return;
        // Hand off any startup track immediately, using native selection, fade and volume rules.
        control.sound.stop();
        control.sound.update();
    }

    private static void load(){
        Music first = null, second = null;
        try{
            var root = mods.getMod(CryonJavaMod.class).root;
            first = loadTrack(root, "cryon-src-000.ogg");
            second = loadTrack(root, "cryon-ultimatum.ogg");
            tracks = new Music[]{first, second};
            menu = new MenuPlaylist();
            Musics.menu = menu;
            for(Planet planet : content.planets()){
                if(isModPlanet(planet)) planet.launchMusic = menu;
            }
        }catch(Exception error){
            if(first != null) first.dispose();
            if(second != null) second.dispose();
            Log.err("[Cryon] Could not load campaign BGM", error);
        }
    }

    private static Music loadTrack(Fi root, String name) throws Exception{
        // ZipFi.child resolves one entry at a time; a slash returns a missing plain Fi.
        Fi file = root.child("music").child(name);
        if(!file.exists()) throw new IllegalStateException("Missing bundled BGM: " + name);
        return new Music(file);
    }

    private static boolean isModPlanet(Planet planet){
        return planet != null && planet.minfo.mod != null
                && planet.minfo.mod.main instanceof CryonJavaMod;
    }

    private static void updateCampaign(){
        boolean active = state != null && state.isGame() && !state.isEditor()
                && isModPlanet(state.rules.sector == null ? state.rules.planet : state.rules.sector.planet)
                && !IntrusionMap.active();
        if(!active || control == null || control.sound == null){ restore(); return; }
        if(owner == control.sound) return;
        restore();
        owner = control.sound;
        owner.stop();
        ambient = owner.ambientMusic;
        dark = owner.darkMusic;
        boss = owner.bossMusic;
        owner.ambientMusic = Seq.with(tracks);
        owner.darkMusic = Seq.with(tracks);
        owner.bossMusic = Seq.with(tracks);
        owner.playRandom();
    }

    private static void restore(){
        if(owner == null) return;
        owner.stop();
        owner.ambientMusic = ambient;
        owner.darkMusic = dark;
        owner.bossMusic = boss;
        owner = null;
    }

    /** SoundControl loops menu assets; this adapter loops the two-song sequence instead. */
    private static final class MenuPlaylist extends Music{
        private int index;
        private boolean playing, paused, looping;
        private float volume = 1f;

        private Music track(){ return tracks[index]; }

        void update(){
            if(playing && !paused && !track().isPlaying()){
                index = (index + 1) % tracks.length;
                playTrack();
            }
        }

        private void playTrack(){
            track().setLooping(false);
            track().setVolume(volume);
            track().play();
        }

        @Override public void play(){ playing = true; paused = false; playTrack(); }
        @Override public void stop(){
            // Tracks are shared with the native in-game playlists. Stop only our active voice.
            if(playing) track().stop();
            playing = paused = false;
        }
        @Override public void pause(boolean value){ paused = value; if(playing) track().pause(value); }
        @Override public boolean isPlaying(){ return playing; }
        @Override public boolean isLooping(){ return looping; }
        @Override public void setLooping(boolean value){ looping = value; }
        @Override public float getVolume(){ return volume; }
        @Override public void setVolume(float value){ volume = value; if(playing) track().setVolume(value); }
    }
}
