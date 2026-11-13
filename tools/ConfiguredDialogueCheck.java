import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.struct.Seq;
import arc.util.*;
import java.nio.file.Files;
import java.lang.reflect.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.io.SaveIO;
import mindustry.net.Net;
import mindustry.world.Tile;

/** Packaged data, real processor configuration/copy/save, pause/wait/hold and rejection checks. */
public final class ConfiguredDialogueCheck{
    static Class<?> sequenceType, scriptType;
    static Building processor;
    static Object script;
    static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }
    static Object call(Object object, String method) throws Exception{ return object.getClass().getMethod(method).invoke(object); }
    static Object field(Object object, String name) throws Exception{ return object.getClass().getField(name).get(object); }
    static void tick(int frames) throws Exception{
        Time.delta = 1f;
        for(int i = 0; i < frames; i++) scriptType.getMethod("update").invoke(script);
    }
    static void locate() throws Exception{
        processor = Vars.world.build(25, 25);
        script = field(processor, "script");
        check(scriptType.isInstance(script), "Saved processor lost its script");
    }
    static void config(String source) throws Exception{
        processor.configured(null, "radio-dialogue-config:" + source);
        script = field(processor, "script");
    }
    static Object parse(String text) throws Exception{ return sequenceType.getMethod("parse", String.class).invoke(null, text); }
    static void localization() throws Exception{
        var previous = Core.bundle;
        try{
            var english = I18NBundle.createBundle(new Fi("bundles/bundle"), java.util.Locale.ENGLISH);
            var chinese = I18NBundle.createBundle(new Fi("bundles/bundle"), java.util.Locale.SIMPLIFIED_CHINESE);
            for(String id : new String[]{"underice-outpost", "desolate-captured", "aravis-captured", "porphyra-landing",
                "cryon-sector-1", "neutron-flux-zone", "exclusion-zone", "baryon-bastion", "twilight-icefield"}){
                String source = (String)sequenceType.getMethod("source", String.class).invoke(null, id);
                Core.bundle = english;
                Object en = parse(source);
                Seq<?> enSteps = (Seq<?>)field(en, "steps");
                check(!call(en, "localizedTitle").equals(field(en, "title")), "Title did not localize: " + id);
                Core.bundle = chinese;
                Object zh = parse(source);
                Seq<?> zhSteps = (Seq<?>)field(zh, "steps");
                check(enSteps.size == zhSteps.size, "Language changed event/page positions: " + id);
                check(call(zh, "localizedTitle").equals(field(zh, "title")), "Chinese title changed: " + id);
                for(int i = 0; i < enSteps.size; i++){
                    Object page = enSteps.get(i);
                    if((boolean)call(page, "waiting")) continue;
                    String key = (String)field(page, "textKey");
                    check(!key.isEmpty() && english.has(key) && chinese.has(key), "Missing translation: " + key);
                    Core.bundle = english;
                    String text = (String)call(page, "localizedText");
                    check(text.equals(english.get(key)) && !text.equals(field(page, "text")), "English text not used: " + key);
                    float time = (float)call(page, "duration");
                    Core.bundle = chinese;
                    check(call(page, "localizedText").equals(field(page, "text")), "Chinese original changed: " + key);
                    check((float)call(page, "duration") == time, "Language changed shared timing: " + key);
                }
            }
            System.out.println("RADIO_LOCALIZATION_PASS: all nine presets, English/Chinese originals, fixed page/event positions and timings");
        }finally{ Core.bundle = previous; }
    }
    static void readScript(String source, int cursor, float remaining) throws Exception{
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        arc.util.io.Writes write = new arc.util.io.Writes(new java.io.DataOutputStream(bytes));
        write.str(source); write.i(cursor); write.f(remaining);
        scriptType.getMethod("read", arc.util.io.Reads.class).invoke(script,
            new arc.util.io.Reads(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()))));
    }
    public static void main(String[] args){
        try{ run(); System.exit(0); }
        catch(Throwable error){ error.printStackTrace(); System.exit(1); }
    }
    static void run() throws Exception{
        Core.app = new MockApplication(); Core.files = new MockFiles(); Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Fi data = new Fi(Files.createTempDirectory("cryon-configured-dialogue-").toFile());
        Core.settings = new Settings(); Core.settings.setDataDirectory(data);
        Core.bundle = I18NBundle.createBundle(new Fi("bundles/bundle"));
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlaunch.jar"));
        Vars.headless = true; Vars.loadLocales = false; Vars.net = new Net(null);
        Vars.platform = new mindustry.core.Platform(){};
        mindustry.core.Version.init(); Vars.init(); Core.settings.setJson(mindustry.io.JsonIO.json);
        Vars.content.createBaseContent(); Vars.content.createModContent(); Vars.content.init();
        var mod = Vars.mods.locateMod("cryon"); check(mod != null && !mod.hasContentErrors(), "Packaged content errors");
        var loader = mod.main.getClass().getClassLoader();
        sequenceType = loader.loadClass("cryon.Story.DialogueSequence");
        scriptType = loader.loadClass("cryon.Script.RadioDialogueScript");
        localization();
        for(String id : new String[]{"underice-outpost", "desolate-captured", "aravis-captured", "porphyra-landing"}){
            String source = (String)sequenceType.getMethod("source", String.class).invoke(null, id);
            Object sequence = parse(source);
            Seq<?> steps = (Seq<?>)field(sequence, "steps");
            check(steps.size > 5, "Missing chapter content: " + id);
            if(id.equals("porphyra-landing")){
                boolean silence = false, encrypted = false, restored = false;
                for(int i = 0; i < steps.size; i++){
                    Object step = steps.get(i);
                    if(field(step, "speaker").equals("联盟") && field(step, "text").equals("……")){
                        check((float)field(step, "hold") == 12f && i + 1 < steps.size
                            && field(steps.get(i + 1), "text").equals("回答我！"), "Long silence misplaced");
                        silence = true;
                    }
                    if(field(step, "mode").toString().equals("encrypted")) encrypted = true;
                    if(encrypted && field(step, "speaker").equals("Malis")){
                        check(field(step, "mode").toString().equals("normal"), "Public transmission remained encrypted");
                        restored = true;
                    }
                }
                check(silence && encrypted && restored, "Missing silence or encrypted/public transition");
            }
        }
        for(String bad : new String[]{"{steps: []}", "{steps:[{wait:-1}]}", "{steps:[{wait:NaN}]}",
            "{steps:[{team:unknown,text:test}]}", "{steps:[{team:crux,text:test,mode:secret}]}",
            "{steps:[{wait:1,text:test}]}", "{steps:[{wait:3601}]}"}){
            boolean rejected = false;
            try{ parse(bad); }catch(InvocationTargetException expected){ rejected = true; }
            check(rejected, "Accepted invalid configuration: " + bad);
        }
        Vars.logic = new Logic(); Vars.logic.reset();
        Vars.world.loadGenerator(50, 50, tiles -> {
            for(int x = 0; x < 50; x++) for(int y = 0; y < 50; y++) tiles.set(x, y, new Tile(x, y, Blocks.stone, Blocks.air, Blocks.air));
            tiles.get(10, 10).setBlock(Blocks.coreShard, Team.sharded);
            tiles.get(25, 25).setBlock(Vars.content.block("cryon-java-processor"), Team.sharded);
        });
        Vars.state.set(mindustry.core.GameState.State.playing);
        processor = Vars.world.build(25, 25);
        processor.getClass().getMethod("setScript", String.class).invoke(processor, "radio-porphyra");
        locate();
        String fresh = (String)call(script, "source");
        var root = new arc.util.serialization.JsonReader().parse(fresh);
        var entries = root.get("steps");
        for(int i = 0; i < entries.size; i++) if(entries.get(i).has("event")){ entries.remove(i); break; }
        root.remove("titleKey");
        for(var entry : entries){ entry.remove("textKey"); entry.remove("speakerKey"); entry.remove("duration"); }
        String legacy = root.toJson(arc.util.serialization.JsonWriter.OutputType.json);
        Seq<?> freshPages = (Seq<?>)field(parse(fresh), "steps");
        int event = -1;
        for(int i = 0; i < freshPages.size; i++) if(field(freshPages.get(i), "event") != null){ event = i; break; }
        check(event > 0, "Missing preset raid event");
        readScript(legacy, event - 1, 2.5f);
        check(((String)call(script, "source")).contains("sharded-receptor-raid")
            && (int)call(script, "cursor") == event - 1 && (float)call(script, "remaining") == 2.5f,
            "Legacy upgrade lost pre-event dialogue position");
        readScript(legacy, event + 2, 2.5f);
        check((int)call(script, "cursor") == event + 3 && (float)call(script, "remaining") == 2.5f,
            "Legacy upgrade lost post-event dialogue position");
        readScript(legacy, freshPages.size - 1, -1f);
        check((boolean)call(script, "finished"), "Legacy completed dialogue restarted");
        String edited = legacy.replace("波菲拉", "自定义剧情");
        readScript(edited, 0, -1f);
        check(call(script, "source").equals(edited), "Legacy upgrade replaced an edited dialogue");
        String custom = "{title:测试,startWave:3,steps:[{wait:3},{team:alliance,text:……,hold:12,mode:encrypted},{wait:2}]}";
        config(custom);
        check(call(script, "source").equals(custom), "Custom source not installed");
        Vars.state.wave = 2; tick(30);
        check((float)call(script, "remaining") == -1f, "startWave did not gate playback");
        Vars.state.wave = 3; Vars.state.rules.editor = true; tick(60);
        check((float)call(script, "remaining") == -1f, "Editor consumed dialogue");
        Vars.state.rules.editor = false; tick(60);
        check((int)call(script, "cursor") == 0 && Math.abs((float)call(script, "remaining") - 2f) < 0.01f, "Wait duration incorrect");
        Vars.state.set(mindustry.core.GameState.State.paused); tick(60);
        check(Math.abs((float)call(script, "remaining") - 2f) < 0.01f, "Pause consumed wait");
        Vars.state.set(mindustry.core.GameState.State.playing);
        Fi save = data.child("dialogue.msav"); SaveIO.write(save); SaveIO.load(save); locate();
        check(call(script, "source").equals(custom) && (int)call(script, "cursor") == 0
            && Math.abs((float)call(script, "remaining") - 2f) < 0.01f, "Save lost embedded source or wait cursor");
        Vars.state.set(mindustry.core.GameState.State.playing); tick(122);
        check((int)call(script, "cursor") == 1, "Wait failed to advance");
        tick(600);
        check((int)call(script, "cursor") == 1 && (float)call(script, "remaining") > 5f, "Extra silence hold was skipped");
        float before = (float)call(script, "remaining");
        SaveIO.write(save); SaveIO.load(save); locate();
        check((int)call(script, "cursor") == 1 && Math.abs((float)call(script, "remaining") - before) < 0.01f,
            "Save lost partial speech/hold position");
        Vars.state.set(mindustry.core.GameState.State.playing);
        Object copied = processor.config();
        Vars.world.tile(30, 25).setBlock(Vars.content.block("cryon-java-processor"), Team.sharded);
        Building copy = Vars.world.build(30, 25); copy.configured(null, copied);
        Object copiedScript = field(copy, "script");
        check(call(copiedScript, "source").equals(custom) && (int)call(copiedScript, "cursor") == 0,
            "Blueprint configuration lost source or copied consumed progress");
        String oldSource = (String)call(script, "source"); config("{steps:[]}");
        check(call(script, "source").equals(oldSource), "Invalid configuration replaced working dialogue");
        tick(1500); check((boolean)call(script, "finished"), "Dialogue did not finish");
        int done = (int)call(script, "cursor"); tick(300);
        check((int)call(script, "cursor") == done, "Finished dialogue replayed automatically");
        System.out.println("CONFIGURED_DIALOGUE_PASS: packaged chapters, 12s silence, encryption, validation, editor/wave/pause, native saves, copy and one-shot playback");
    }
}
