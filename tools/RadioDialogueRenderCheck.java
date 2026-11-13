import arc.*;
import arc.files.Fi;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.event.Touchable;
import arc.scene.ui.Dialog;
import arc.util.*;
import java.lang.reflect.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.world.Tile;

/** Isolated native desktop HUD: actual font typing, minimap positioning, queue and responsive layout. */
public final class RadioDialogueRenderCheck{
    static Class<?> type;
    static Element overlay;
    static Fi data;
    static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }
    static void guarded(Runnable action){ try{ action.run(); }catch(Throwable error){ Log.err(error); System.exit(1); } }
    static Object field(Object object, String name) throws Exception{
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }
    static void show(Team team, String speaker, String text) throws Exception{
        type.getMethod("show", Team.class, String.class, String.class).invoke(null, team, speaker, text);
    }
    static void bounds(boolean map){
        float pad = arc.scene.ui.layout.Scl.scl(12f);
        check(overlay.visible && overlay.getWidth() > 150f, "Radio panel not visible");
        check(overlay.x >= 0f && overlay.y >= 0f && overlay.x + overlay.getWidth() <= Core.scene.getWidth()
            && overlay.y + overlay.getHeight() <= Core.scene.getHeight(), "Radio panel outside screen");
        if(map){
            Element minimap = Core.scene.find("minimap"), position = Core.scene.find("position");
            float bottom = Math.min(minimap.localToStageCoordinates(new Vec2()).y,
                position.localToStageCoordinates(new Vec2()).y);
            check(overlay.y + overlay.getHeight() <= bottom - pad + 1f, "Radio panel overlaps minimap/coordinates");
        }else{
            check(Math.abs(overlay.y + overlay.getHeight() - (Core.scene.getHeight() - pad)) < 1f,
                "Radio panel failed to move to upper right without minimap");
        }
        check(overlay.touchable == Touchable.disabled && overlay.hit(10f, 10f, true) == null, "Radio intercepts input");
    }
    public static void main(String[] args){
        data = Fi.get(System.getProperty("mindustry.data.dir"));
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlaunch.jar"));
        Events.on(ClientLoadEvent.class, event -> Timer.schedule(() -> guarded(() -> {
            try{
                for(Element e : Core.scene.root.getChildren().copy()) if(e instanceof Dialog dialog){ dialog.clearActions(); dialog.hide(null); }
                Core.graphics.setFullscreen(false); Core.graphics.setWindowSize(1100, 760);
                Vars.logic.reset();
                Vars.world.loadGenerator(80, 70, tiles -> {
                    for(int x = 0; x < 80; x++) for(int y = 0; y < 70; y++) tiles.set(x, y, new Tile(x, y, Blocks.stone, Blocks.air, Blocks.air));
                    tiles.get(30, 30).setBlock(Blocks.coreShard, Team.sharded);
                });
                Vars.state.rules.waves = false; Vars.state.rules.canGameOver = false;
                Vars.logic.play(); Vars.player.team(Team.sharded);
                Vars.player.unit(UnitTypes.gamma.spawn(Team.sharded, 250, 240));
                Vars.renderer.setScale(3f);
                Core.settings.put("minimap", true); Core.settings.put("position", true);
                Core.settings.put("mouseposition", false);
                type = Vars.mods.mainLoader().loadClass("cryon.UI.RadioDialogue");
                type.getMethod("dismissAll").invoke(null);
                show(Team.crux, "Crux", "目标身份已确认。所有作战单位，解除火力限制。消灭入侵者。不计代价。");
                Class<?> mode = Vars.mods.mainLoader().loadClass("cryon.UI.RadioDialogue$Mode");
                type.getMethod("show", Team.class, String.class, String.class, mode).invoke(null,
                    Team.sharded, "Sharded", "Signal received.\nHold position and await further orders.",
                    mode.getField("encrypted").get(null));
                overlay = Core.scene.find("cryon-radio-dialogue");
                check(overlay != null, "Radio overlay not installed");
            }catch(Exception e){ throw new RuntimeException(e); }
            Timer.schedule(() -> guarded(() -> {
                bounds(true);
                try{
                    float age = (float)field(overlay, "age");
                    int glyphs = (int)field(overlay, "glyphs");
                    check(age > 0.25f && (age - 0.25f) * 24f < glyphs, "Typewriter did not advance gradually");
                }catch(Exception e){ throw new RuntimeException(e); }
                ScreenUtils.saveScreenshot(data.child("radio-typing-minimap.png"));
            }), 0.4f);
            Timer.schedule(() -> guarded(() -> {
                bounds(true); ScreenUtils.saveScreenshot(data.child("radio-full-minimap.png"));
                Core.settings.put("minimap", false);
            }), 2.8f);
            Timer.schedule(() -> guarded(() -> {
                bounds(false); ScreenUtils.saveScreenshot(data.child("radio-no-minimap.png"));
                Core.graphics.setWindowSize(600, 900);
                Core.settings.put("minimap", true);
            }), 3.6f);
            Timer.schedule(() -> guarded(() -> {
                bounds(true); ScreenUtils.saveScreenshot(data.child("radio-portrait.png"));
            }), 4.5f);
            Timer.schedule(() -> guarded(() -> {
                try{
                    Object current = field(overlay, "current");
                    check(current != null && field(current, "team") == Team.crux
                        && (int)field(current, "sentence") == 1, "Radio did not advance one sentence at a time");
                    arc.struct.Seq<?> sentences = (arc.struct.Seq<?>)field(current, "sentences");
                    check(sentences.size == 4 && sentences.get(0).equals("目标身份已确认。")
                        && sentences.get(1).equals("所有作战单位，解除火力限制。"), "Chinese dialogue was not split at sentence stops");
                    ScreenUtils.saveScreenshot(data.child("radio-second-sentence.png"));
                    for(int i = 0; i < 3; i++){
                        Field ageField = overlay.getClass().getDeclaredField("age"); ageField.setAccessible(true);
                        ageField.setFloat(overlay, 100f); overlay.act(0.01f);
                    }
                    overlay.act(0.01f);
                    current = field(overlay, "current");
                    check(current != null && field(current, "team") == Team.sharded, "Queued dialogue failed to advance/change faction");
                    check(field(current, "mode").toString().equals("encrypted"), "Queued dialogue lost encrypted mode");
                    check((float)field(overlay, "statusHeight") > 0f, "Encrypted channel status missing");
                    Field ageField = overlay.getClass().getDeclaredField("age"); ageField.setAccessible(true);
                    ageField.setFloat(overlay, 1f);
                    Vars.state.set(mindustry.core.GameState.State.paused);
                    float age = (float)field(overlay, "age");
                    overlay.act(1f);
                    check((float)field(overlay, "age") == age, "Pause consumed dialogue time");
                    Timer.schedule(() -> guarded(() -> {
                        try{
                            bounds(true);
                            ScreenUtils.saveScreenshot(data.child("radio-encrypted.png"));
                            Events.fire(new ResetEvent());
                            check(!overlay.visible && field(overlay, "current") == null
                                && ((arc.struct.Seq<?>)field(overlay, "queue")).isEmpty(), "Reset retained stale dialogue");
                            Log.info("RADIO_HUD_PASS: typing, sentences, minimap, portrait, encrypted mode, queue, pause and reset");
                            Core.app.exit();
                        }catch(Exception e){ throw new RuntimeException(e); }
                    }), 0.3f);
                }catch(Exception e){ throw new RuntimeException(e); }
            }), 8f);
        }), 3f));
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
