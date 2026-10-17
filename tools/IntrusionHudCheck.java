import arc.Core;
import arc.Events;
import arc.math.geom.Rect;
import arc.scene.ui.layout.Scl;
import arc.util.Log;
import arc.util.ScreenUtils;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.game.EventType.*;

/** Runs the real mobile input/HUD on desktop, checking landscape and portrait screenshots. */
public class IntrusionHudCheck {
    private static int stage, frames;
    private static boolean prepared;
    public static void main(String[] args){
        Vars.testMobile = true;
        Events.on(ClientLoadEvent.class, event -> {
            Core.graphics.setFullscreen(false);
            Core.graphics.setWindowSize(900, 400);
            Timer.schedule(() -> {
            try{
                Vars.mods.mainLoader().loadClass("cryon.Story.DesolateIntrusion").getMethod("preview").invoke(null);
            }catch(Exception error){ throw new RuntimeException(error); }
        }, 4f);
        });
        Events.on(WorldLoadEvent.class, event -> {
            if(prepared || !Vars.state.rules.tags.getBool("cryon-intrusion-map")) return;
            prepared = true;
            Timer.schedule(() -> {
                try{
                    Class<?> type = Vars.mods.mainLoader().loadClass("cryon.Script.VirusIntrusionScript");
                    Object script = type.getMethod("current").invoke(null);
                    java.lang.reflect.Field time = type.getDeclaredField("time");
                    time.setAccessible(true);
                    time.setFloat(script, 800f);
                    Vars.player.unit().unapply(mindustry.content.StatusEffects.disarmed);
                    Core.graphics.setWindowSize(900, 400);
                    stage = 1;
                }catch(Exception error){ throw new RuntimeException(error); }
            }, 2f);
        });
        Events.run(Trigger.uiDrawEnd, () -> {
            if(stage == 0 || ++frames < 8) return;
            if(stage == 1 && (Core.graphics.getWidth() != 900 || Core.graphics.getHeight() != 400)) return;
            if(stage == 2 && (Core.graphics.getWidth() != 600 || Core.graphics.getHeight() != 900)) return;
            try{
                Class<?> overlay = Vars.mods.mainLoader().loadClass("cryon.Story.IntrusionOverlay");
                Rect bounds = new Rect();
                overlay.getMethod("combatBounds", Rect.class, float.class, float.class, boolean.class, float.class)
                        .invoke(null, bounds, Core.scene.getWidth(), Core.scene.getHeight(), true, 0f);
                if(!Vars.mobile || bounds.x < 0 || bounds.y < 0 || bounds.x + bounds.width > Core.scene.getWidth()
                        || Core.scene.getHeight() - (bounds.y + bounds.height) < Scl.scl(
                                Core.scene.getWidth() > Core.scene.getHeight() && Core.scene.getHeight() < Scl.scl(480f) ? 64f : 152f)){
                    throw new AssertionError("Mobile panel overlaps top controls or screen edge: " + bounds);
                }
                ScreenUtils.saveScreenshot(Vars.dataDirectory.child(stage == 1 ? "hud-mobile-landscape.png" : "hud-mobile-portrait.png"));
                if(stage == 1){
                    Core.graphics.setWindowSize(600, 900);
                    stage = 2;
                    frames = 0;
                }else{
                    Log.info("HUD_CHECK_PASS: native mobile HUD, landscape/portrait bounds and screenshots");
                    Core.app.exit();
                    stage = 0;
                }
            }catch(Exception error){ throw new RuntimeException(error); }
        });
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
