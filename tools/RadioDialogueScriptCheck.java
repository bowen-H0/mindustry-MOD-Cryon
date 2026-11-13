import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.*;
import java.nio.file.Files;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.io.SaveIO;
import mindustry.net.Net;
import mindustry.world.Tile;

/** Actual packaged director in a generated fixture, independent of map format changes. */
public final class RadioDialogueScriptCheck{
    static Class<?> directorType;
    static Object director;
    static Building processor;
    static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }
    static boolean cue(String name){ return Vars.state.rules.tags.getBool("cryon-desolate-radio-" + name); }
    static void tick() throws Exception{ Time.delta = 1f; directorType.getMethod("update").invoke(director); }
    static void locate() throws Exception{
        processor = Groups.build.find(b -> {
            try{ return directorType.isInstance(b.getClass().getField("script").get(b)); }
            catch(Exception ignored){ return false; }
        });
        check(processor != null, "director not found");
        director = processor.getClass().getField("script").get(processor);
    }
    public static void main(String[] args){
        try{ run(); System.exit(0); }
        catch(Throwable error){ error.printStackTrace(); System.exit(1); }
    }
    static void run() throws Exception{
        Core.app = new MockApplication(); Core.files = new MockFiles(); Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Core.settings = new Settings(); Fi data = new Fi(Files.createTempDirectory("cryon-radio-script-").toFile());
        Core.settings.setDataDirectory(data); Core.bundle = I18NBundle.createBundle(new Fi("bundles/bundle"));
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlaunch.jar"));
        Vars.headless = true; Vars.loadLocales = false; Vars.net = new Net(null);
        Vars.platform = new mindustry.core.Platform(){};
        mindustry.core.Version.init(); Vars.init(); Core.settings.setJson(mindustry.io.JsonIO.json);
        Vars.content.createBaseContent(); Vars.content.createModContent(); Vars.content.init();
        var mod = Vars.mods.locateMod("cryon"); check(mod != null && !mod.hasContentErrors(), "packaged content errors");
        directorType = mod.main.getClass().getClassLoader().loadClass("cryon.Script.DesolatePlainScript");
        Class<?> radio = mod.main.getClass().getClassLoader().loadClass("cryon.UI.RadioDialogue");
        var split = radio.getDeclaredMethod("splitSentences", String.class); split.setAccessible(true);
        var sentences = (arc.struct.Seq<?>)split.invoke(null, "目标身份已确认。所有作战单位，解除火力限制。消灭入侵者。不计代价。");
        check(sentences.size == 4 && sentences.get(1).equals("所有作战单位，解除火力限制。"), "Chinese sentence grouping");
        sentences = (arc.struct.Seq<?>)split.invoke(null, "Power 3.5 confirmed.\nReady?! “Fire!”\r\nGo");
        check(sentences.size == 4 && sentences.get(0).equals("Power 3.5 confirmed.")
            && sentences.get(1).equals("Ready?!") && sentences.get(2).equals("“Fire!”")
            && sentences.get(3).equals("Go"), "English/decimal/quotes/newline sentence grouping");
        check(((arc.struct.Seq<?>)split.invoke(null, " \n\r\n ")).isEmpty(), "Empty dialogue produced a page");
        directorType.getMethod("install").invoke(null);
        Vars.logic = new Logic(); Vars.logic.reset(); Vars.state.rules.winWave = 100;
        Vars.world.loadGenerator(100, 100, tiles -> {
            for(int x = 0; x < 100; x++) for(int y = 0; y < 100; y++) tiles.set(x, y, new Tile(x, y, Blocks.stone, Blocks.air, Blocks.air));
            tiles.get(10, 10).setBlock(Blocks.coreShard, Team.sharded);
            tiles.get(90, 90).setBlock(Vars.content.block("cryon-java-processor"), Team.crux);
        });
        Vars.state.set(mindustry.core.GameState.State.playing);
        Vars.world.build(90, 90).getClass().getMethod("setScript", String.class).invoke(Vars.world.build(90, 90), "desolate_plain");
        locate(); tick();
        check(cue("opening") && !cue("cleansing") && !cue("fleet"), "opening dialogue timing");
        Vars.state.wave = 20; tick();
        check(!cue("cleansing"), "cleansing dialogue too early");
        Unit enemy = UnitTypes.flare.create(Team.crux); enemy.set(600, 600); enemy.add();
        Vars.state.wave = 21; tick();
        check(!cue("cleansing"), "cleansing dialogue before wave 20 clears");
        enemy.remove(); tick();
        check(cue("cleansing") && !cue("fleet"), "cleansing dialogue not dispatched with protocol activation");
        check(directorType.getField("cleansingStarted").getBoolean(director), "protocol not active");
        Vars.state.wave = 99; tick();
        check(!cue("fleet"), "fleet dialogue fired before wave 99 starts");
        enemy = UnitTypes.flare.create(Team.crux); enemy.set(600, 600); enemy.add();
        Vars.state.wave = 100; tick();
        check(cue("fleet") && directorType.getField("phase").getInt(director) == 0,
            "fleet dialogue must precede cinematic, immediately when wave 99 is dispatched");
        Fi save = data.child("radio-triggers.msav"); SaveIO.write(save); SaveIO.load(save); locate();
        check(cue("opening") && cue("cleansing") && cue("fleet"), "saved dialogue flags lost");
        for(int i = 0; i < 10; i++) tick();
        check(cue("opening") && cue("cleansing") && cue("fleet"), "dialogue flags changed on repeated update");
        System.out.println("RADIO_SCRIPT_PASS: opening, cleansing trigger, wave 99 start before cinematic, persistent one-time cues, packaged content");
    }
}
