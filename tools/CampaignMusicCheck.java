import arc.audio.Music;
import java.lang.reflect.*;

/** Checks the menu adapter without an audio device or player save data. */
public class CampaignMusicCheck{
    private static class Track extends Music{
        boolean playing, paused, looping;
        float volume;
        int starts;
        @Override public void play(){ playing = true; paused = false; starts++; }
        @Override public void stop(){ playing = false; }
        @Override public void pause(boolean value){ paused = value; }
        @Override public boolean isPlaying(){ return playing && !paused; }
        @Override public void setLooping(boolean value){ looping = value; }
        @Override public void setVolume(float value){ volume = value; }
    }

    public static void main(String[] args) throws Exception{
        Class<?> controller = Class.forName("cryon.Features.CampaignMusic");
        Track first = new Track(), second = new Track();
        Field tracks = controller.getDeclaredField("tracks");
        tracks.setAccessible(true);
        tracks.set(null, new Music[]{first, second});
        Class<?> type = Class.forName("cryon.Features.CampaignMusic$MenuPlaylist");
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        Music menu = (Music)constructor.newInstance();
        Method update = type.getDeclaredMethod("update");
        update.setAccessible(true);

        menu.setVolume(0.3f);
        menu.setLooping(true);
        menu.play();
        check(first.playing && !first.looping && first.volume == 0.3f, "first track / native volume");
        first.playing = false; // Simulate reaching EOF.
        check(menu.isPlaying(), "native controller must keep the playlist alive at EOF");
        update.invoke(menu);
        check(second.playing && second.volume == 0.3f, "advance to second track");
        menu.pause(true);
        update.invoke(menu);
        check(first.starts == 1 && second.starts == 1, "pause must not advance playlist");
        menu.pause(false);
        menu.setVolume(0f);
        check(second.volume == 0f, "mute applies immediately");
        menu.setVolume(0.6f);
        second.playing = false;
        update.invoke(menu);
        check(first.starts == 2 && first.volume == 0.6f, "wrap to first track with current volume");
        menu.stop();
        check(!menu.isPlaying() && !first.playing, "stop on leaving menu");
        second.play(); // Native campaign playlists share these tracks.
        menu.stop();
        update.invoke(menu);
        check(second.playing && first.starts == 2, "inactive menu must leave campaign playback alone");
        System.out.println("CAMPAIGN_MUSIC_CHECK_PASS: sequence, wrap, pause, volume, mute, stop, shared tracks");
    }

    private static void check(boolean condition, String message){
        if(!condition) throw new AssertionError(message);
    }
}
