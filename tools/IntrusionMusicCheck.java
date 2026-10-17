import arc.Core;
import arc.Events;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType.*;
import mindustry.gen.Musics;

/** Native audio smoke test, layered on the isolated encounter rendering check. */
public class IntrusionMusicCheck {
    private static boolean first, second, silent, volume;
    private static boolean firstLoop, secondLoop;
    private static float chance, waveChance;
    public static void main(String[] args){
        Events.on(ClientLoadEvent.class, event -> {
            Core.settings.put("musicvol", 30);
            chance = Vars.control.sound.musicChance;
            waveChance = Vars.control.sound.musicWaveChance;
            firstLoop = Musics.boss1.isLooping();
            secondLoop = Musics.boss2.isLooping();
        });
        Events.run(Trigger.uiDrawEnd, () -> {
            if(!Vars.state.isGame() || !Vars.state.rules.tags.getBool("cryon-intrusion-map")) return;
            first |= Musics.boss1.isPlaying() && Musics.boss1.isLooping();
            second |= Musics.boss2.isPlaying() && Musics.boss2.isLooping();
            silent |= Vars.state.rules.tags.containsKey("cryon-intrusion-transform-time")
                    && !Musics.boss1.isPlaying() && !Musics.boss2.isPlaying();
            volume |= (Musics.boss1.isPlaying() && Math.abs(Musics.boss1.getVolume() - 0.3f) < 0.001f)
                    || (Musics.boss2.isPlaying() && Math.abs(Musics.boss2.getVolume() - 0.3f) < 0.001f);
        });
        IntrusionRenderCheck.beforeExit = () -> {
            if(!first || !second || !silent || !volume) throw new AssertionError(
                    "BGM state: first=" + first + " second=" + second + " dialogueSilent=" + silent + " volume=" + volume);
            Vars.logic.reset();
            if(Musics.boss1.isPlaying() || Musics.boss2.isPlaying()
                    || Musics.boss1.isLooping() != firstLoop || Musics.boss2.isLooping() != secondLoop
                    || Vars.control.sound.musicChance != chance || Vars.control.sound.musicWaveChance != waveChance){
                throw new AssertionError("BGM state not restored on map reset");
            }
            Log.info("BGM_CHECK_PASS: both stage tracks loop, dialogue is silent, volume follows settings, exit restores music state");
        };
        IntrusionRenderCheck.main(args);
    }
}
