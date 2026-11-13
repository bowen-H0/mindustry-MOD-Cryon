import arc.Core;
import arc.Events;
import arc.audio.Music;
import arc.files.Fi;
import arc.util.Log;
import arc.util.Timer;
import java.lang.reflect.Field;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.gen.Musics;

/** Real desktop startup with an isolated profile; requires no clicks or campaign save. */
public class CampaignMusicStartupCheck{
    private static void checkPlayback(){
        try{
            if(!Vars.state.isMenu()) throw new AssertionError("Startup left main menu");
            Class<?> type = Vars.mods.mainLoader().loadClass("cryon.Features.CampaignMusic");
            Field tracksField = type.getDeclaredField("tracks");
            tracksField.setAccessible(true);
            Music[] tracks = (Music[])tracksField.get(null);
            if(tracks == null || !Musics.menu.getClass().getName().contains("MenuPlaylist")){
                throw new AssertionError("Custom menu playlist not installed");
            }
            Music playing = tracks[0].isPlaying() ? tracks[0] : tracks[1].isPlaying() ? tracks[1] : null;
            if(playing == null || playing.getVolume() < 0.1f || playing.getPosition() < 0.1f){
                throw new AssertionError("No audible menu track after startup; volume="
                        + (playing == null ? "none" : playing.getVolume())
                        + " playlistPlaying=" + Musics.menu.isPlaying());
            }
            float initialVolume = playing.getVolume(), initialPosition = playing.getPosition();
            Field current = mindustry.audio.SoundControl.class.getDeclaredField("current");
            current.setAccessible(true);
            if(current.get(Vars.control.sound) != Musics.menu){
                throw new AssertionError("Native controller does not own the menu playlist");
            }
            // Reproduce a stale startup selection: bootstrap must replace it in one update.
            Vars.control.sound.stop();
            Musics.editor.setVolume(0f);
            Musics.editor.play();
            current.set(Vars.control.sound, Musics.editor);
            Field fade = mindustry.audio.SoundControl.class.getDeclaredField("fade");
            fade.setAccessible(true);
            fade.setFloat(Vars.control.sound, 1f);
            java.lang.reflect.Method start = type.getDeclaredMethod("startMenuPlayback");
            start.setAccessible(true);
            start.invoke(null);
            if(current.get(Vars.control.sound) != Musics.menu || !Musics.menu.isPlaying()
                    || Musics.editor.isPlaying()){
                throw new AssertionError("Startup handoff did not select custom music immediately");
            }
            Core.settings.put("musicvol", 0);
            start.invoke(null);
            if(tracks[0].isPlaying() || tracks[1].isPlaying()){
                throw new AssertionError("Startup bootstrap ignores mute");
            }
            Core.settings.put("musicvol", 40);
            start.invoke(null);
            if(current.get(Vars.control.sound) != Musics.menu || !Musics.menu.isPlaying()){
                throw new AssertionError("Menu music does not start after unmuting");
            }
            Log.info("BGM_STARTUP_PASS: menu plays without interaction, volume=@ position=@",
                    initialVolume, initialPosition);
            Log.info("BGM_STARTUP_HANDOFF_PASS: immediate native handoff, mute and unmute");
            Core.app.exit();
        }catch(Throwable error){
            Log.err(error);
            System.exit(1);
        }
    }

    public static void main(String[] args){
        Fi dir = Fi.get(System.getProperty("mindustry.data.dir"));
        dir.child("mods").mkdirs();
        Fi.get("build/libs/mindustry-MOD-JavaDesktop.jar")
                .copyTo(dir.child("mods").child("cryon.jar"));
        // Avoid the bundled library's first-run import/restart during this audio check.
        Fi.get("单位发射台library.jar").copyTo(dir.child("mods").child("unitlaunch.jar"));
        Events.on(ClientLoadEvent.class, event -> {
            Core.settings.put("musicvol", 40);
            // Wait for normal desktop updates; do not click a menu or manually play music.
            Timer.schedule(CampaignMusicStartupCheck::checkPlayback, 4f);
        });
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
