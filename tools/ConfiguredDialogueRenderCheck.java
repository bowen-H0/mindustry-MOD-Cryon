import arc.*;
import arc.files.Fi;
import arc.scene.Element;
import arc.scene.ui.*;
import arc.scene.ui.layout.Table;
import arc.util.*;
import java.lang.reflect.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.GameState.State;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Tile;

/** Real desktop verification of chapter routing, terminal text, portrait layout and configuration editing. */
public final class ConfiguredDialogueRenderCheck{
    static Fi data;
    static ClassLoader loader;
    static Building computer, processor;
    static BaseDialog dialog;
    static Class<?> radio;
    static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }
    static void guarded(Runnable action){ try{ action.run(); }catch(Throwable e){ Log.err(e); System.exit(1); } }
    static void screenshot(String name){ Core.scene.act(); Core.scene.draw(); ScreenUtils.saveScreenshot(data.child(name)); }
    static Object field(Object object, String name) throws Exception{
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    static BaseDialog shown(){
        var children = Core.scene.root.getChildren();
        for(int i = children.size - 1; i >= 0; i--){
            Element element = children.get(i);
            if(element instanceof BaseDialog d && d.isShown()) return d;
        }
        throw new AssertionError("No shown dialogue");
    }
    static void openContact() throws Exception{
        dialog = (BaseDialog)loader.loadClass("cryon.Story.QuantumCommunicationDialog").getConstructors()[0].newInstance(computer);
        dialog.show();
        Button pioneer = dialog.find("cryon-contact-pioneer");
        check(pioneer != null, "Missing Pioneer contact"); pioneer.fireClick();
        dialog = shown();
        check(dialog.getClass().getSimpleName().equals("ConfiguredCommunicationDialog"), "Wrong post-capture dialogue");
    }
    static void click(){
        Button next = dialog.find("cryon-configured-continue");
        check(next != null && !next.isDisabled(), "Configured continue missing/disabled"); next.fireClick();
    }
    static void finish(){
        int count = 0;
        try{
            String chapter = (String)field(dialog, "chapter");
            String key = "cryon-dialogue-read-" + chapter;
            while(!Core.settings.getBool(key) && count++ < 200) click();
            check(Core.settings.getBool(key), "Dialogue never completed");
            dialog.hide(null);
        }catch(Exception e){ throw new RuntimeException(e); }
    }
    static I18NBundle originalBundle;
    static void localizedPacket(boolean chinese) throws Exception{
        if(originalBundle == null) originalBundle = Core.bundle;
        if(chinese){
            var translated = I18NBundle.createBundle(new Fi("bundles/bundle"), java.util.Locale.SIMPLIFIED_CHINESE);
            var merged = new arc.struct.ObjectMap<String, String>();
            merged.putAll(originalBundle.getProperties()); merged.putAll(translated.getProperties());
            translated.setProperties(merged); Core.bundle = translated;
        }else Core.bundle = originalBundle;
        radio.getMethod("dismissAll").invoke(null);
        var receive = radio.getDeclaredMethod("receive", String.class); receive.setAccessible(true);
        receive.invoke(null, "{team:2,speaker:Crux,text:SERVER_FALLBACK,page:true,key:false,encrypted:true,hold:0,offset:0,duration:9,textKey:cryon.radio.story.cryon-sector-1.01.01,speakerKey:cryon.radio.speaker.crux}");
        Element overlay = Core.scene.find("cryon-radio-dialogue"); overlay.act(0f);
        Object current = field(overlay, "current");
        String expected = chinese ? "这里是 Crux。" : "This is Crux.";
        check(((arc.struct.Seq<?>)field(current, "sentences")).first().equals(expected), "Packet did not use recipient language");
        check((float)field(overlay, "lifetime") == 9f, "Localization changed network page timing");
        Field age = overlay.getClass().getDeclaredField("age"); age.setAccessible(true); age.setFloat(overlay, 3f);
    }
    public static void main(String[] args){
        data = new Fi(System.getProperty("mindustry.data.dir"));
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlaunch.jar"));
        Events.on(ClientLoadEvent.class, event -> Timer.schedule(() -> guarded(() -> {
            try{
                for(Element element : Core.scene.root.getChildren().copy()) if(element instanceof Dialog d){ d.clearActions(); d.hide(null); }
                Core.graphics.setFullscreen(false); Core.graphics.setWindowSize(1200, 850);
                Vars.logic.reset(); Vars.state.rules = new Rules();
                Vars.state.rules.sector = Vars.content.sector("cryon-cryon-sector-1").sector;
                Vars.world.loadGenerator(70, 70, tiles -> {
                    for(int x = 0; x < 70; x++) for(int y = 0; y < 70; y++) tiles.set(x, y, new Tile(x, y, Blocks.stone, Blocks.air, Blocks.air));
                    tiles.get(10, 10).setBlock(Blocks.coreShard, Team.sharded);
                    tiles.get(25, 25).setBlock(Vars.content.block("cryon-java-processor"), Team.sharded);
                    tiles.get(35, 35).setBlock(Vars.content.block("cryon-quantum-communication-computer"), Team.sharded);
                });
                Vars.player.team(Team.sharded); Vars.player.unit(UnitTypes.gamma.spawn(Team.sharded, 240, 220));
                Vars.logic.play(); Vars.state.set(State.paused);
                computer = Vars.world.build(35, 35); processor = Vars.world.build(25, 25);
                computer.block.unlock(); computer.power.status = 1f;
                computer.getClass().getMethod("constructorStatus", float.class).invoke(computer, 1f);
                Events.run(Trigger.update, () -> guarded(() -> {
                    Vars.state.set(State.paused); computer.power.status = 1f;
                    try{ computer.getClass().getMethod("constructorStatus", float.class).invoke(computer, 1f); }
                    catch(Exception e){ throw new RuntimeException(e); }
                }));
                loader = Vars.mods.locateMod("cryon").main.getClass().getClassLoader();
                radio = loader.loadClass("cryon.UI.RadioDialogue");
                Class<?> progress = loader.loadClass("cryon.Story.StoryProgress");
                Class<?> flag = loader.loadClass("cryon.Story.StoryProgress$Flag");
                progress.getMethod("set", flag, boolean.class).invoke(null, flag.getField("underground").get(null), true);
                Events.fire(new SectorCaptureEvent(Vars.content.sector("cryon-desolate-plain").sector, true));
                openContact(); click();
            }catch(Exception e){ throw new RuntimeException(e); }
            Runnable[] steps = {
                () -> { screenshot("terminal-desolate.png"); finish(); check(Core.settings.getBool("cryon-story-aravis-mission"), "Desolate chapter not completed");
                    Events.fire(new SectorCaptureEvent(Vars.content.sector("cryon-terminus").sector, true));
                    try{ openContact(); click(); }catch(Exception e){ throw new RuntimeException(e); } },
                () -> { screenshot("terminal-aravis.png"); click(); click(); click(); click(); click();
                    Core.graphics.setWindowSize(650, 900); },
                () -> { screenshot("terminal-aravis-portrait.png"); finish(); check(Core.settings.getBool("cryon-story-act-two"), "Aravis chapter not completed");
                    try{
                        processor.getClass().getMethod("setScript", String.class).invoke(processor, "radio-porphyra");
                        Object script = processor.getClass().getField("script").get(processor);
                        Table table = new Table(); script.getClass().getMethod("buildUI", Table.class).invoke(script, table);
                        for(Element e : table.getChildren()) if(e instanceof TextButton b && b.getText().toString().equals(Core.bundle.get("cryon.radio.edit"))) b.fireClick();
                        dialog = shown();
                    }catch(Exception e){ throw new RuntimeException(e); } },
                () -> { screenshot("radio-config-portrait.png");
                    TextArea editor = dialog.find(e -> e instanceof TextArea);
                    check(editor != null, "Configuration editor missing");
                    editor.setText("{title:测试,steps:[{team:pioneer,text:我们将继续维持双方的通讯隔离。,mode:encrypted,hold:12}]}");
                    TextButton save = dialog.find(e -> e instanceof TextButton && ((TextButton)e).getText().toString().equals(Core.bundle.get("cryon.radio.save")));
                    check(save != null, "Configuration save missing"); save.fireClick();
                    try{
                        Object script = processor.getClass().getField("script").get(processor);
                        check(script.getClass().getMethod("source").invoke(script).toString().contains("通讯隔离"), "Editor did not save");
                        Vars.state.set(State.playing); script.getClass().getMethod("update").invoke(script);
                        Vars.state.set(State.paused);
                    }catch(Exception e){ throw new RuntimeException(e); } },
                () -> { try{
                        Element overlay = Core.scene.find("cryon-radio-dialogue");
                        check(overlay != null && overlay.visible, "Configured radio missing");
                        Field age = overlay.getClass().getDeclaredField("age"); age.setAccessible(true); age.setFloat(overlay, 7f);
                        overlay.act(0f);
                        check(field(overlay, "current") != null && (float)field(overlay, "lifetime") > 16f,
                            "Configured hold expired early");
                        screenshot("radio-configured-encrypted.png");
                        localizedPacket(false);
                    }catch(Exception e){ throw new RuntimeException(e); } },
                () -> { try{
                        ScreenUtils.saveScreenshot(data.child("radio-localized-en.png")); localizedPacket(true);
                    }catch(Exception e){ throw new RuntimeException(e); } },
                () -> {
                        ScreenUtils.saveScreenshot(data.child("radio-localized-zh.png")); Core.bundle = originalBundle;
                        Log.info("CONFIGURED_RENDER_PASS: recipient language packets, EN/ZH screenshots, real chapter routing/completion, Chinese text, portrait, editor save, encrypted HUD and extended hold");
                        Core.app.exit(); }
            };
            int[] index = {0};
            Timer.schedule(() -> guarded(steps[index[0]++]), 0.8f, 0.8f, steps.length - 1);
        }), 3f));
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
