import arc.Core;
import arc.Events;
import arc.util.Log;
import arc.util.ScreenUtils;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Groups;

/** Desktop GL regression check. Run with an isolated mindustry.data.dir containing the built mod. */
public class IntrusionRenderCheck{
    public static Runnable beforeExit = () -> {};
    private static int frames, maxUnits, maxBullets;
    private static boolean started, captured, upgraded, warningCaptured, ringCaptured, chainCaptured, dialogueCaptured;

    public static void main(String[] args){
        Events.on(ClientLoadEvent.class, event -> Timer.schedule(() -> {
            try{
                Vars.mods.mainLoader().loadClass("cryon.Story.DesolateIntrusion")
                        .getMethod("preview").invoke(null);
                started = true;
                Timer.schedule(() -> {
                    try{
                        Class<?> scriptType = Vars.mods.mainLoader().loadClass("cryon.Script.VirusIntrusionScript");
                        Object script = scriptType.getMethod("current").invoke(null);
                        mindustry.gen.Unit boss = (mindustry.gen.Unit)scriptType.getMethod("virusNode").invoke(script);
                        boss.health = boss.maxHealth * 0.5f;
                    }catch(Exception error){ throw new RuntimeException(error); }
                }, 15f);
                Timer.schedule(() -> Core.graphics.setWindowSize(1000, 700), 18f);
                Timer.schedule(() -> {
                    if(frames < 60 || maxUnits < 2 || maxBullets == 0 || !captured || !upgraded || !warningCaptured || !ringCaptured || !chainCaptured || !dialogueCaptured){
                        throw new IllegalStateException("Incomplete render check: frames=" + frames
                                + " units=" + maxUnits + " bullets=" + maxBullets + " screenshot=" + captured + " upgraded=" + upgraded + " warning=" + warningCaptured);
                    }
                    Log.info("RENDER_CHECK_PASS frames=@ units=@ bullets=@", frames, maxUnits, maxBullets);
                    beforeExit.run();
                    Core.app.exit();
                }, 48f);
            }catch(Exception error){
                throw new RuntimeException(error);
            }
        }, 4f));
        Events.run(Trigger.drawOver, () -> {
            if(!started || !Vars.state.isGame()) return;
            frames++;
            if(Vars.player.unit() != null && Vars.player.unit().type.name.equals("cryon-transliminal")) upgraded = true;
            maxUnits = Math.max(maxUnits, Groups.unit.size());
            maxBullets = Math.max(maxBullets, Groups.bullet.size());
        });
        Events.run(Trigger.postDraw, () -> {
            Groups.bullet.each(bullet -> {
                if(bullet.team != Vars.state.rules.waveTeam) return;
                if(bullet.type.getClass() == mindustry.entities.bullet.LaserBulletType.class && !ringCaptured){
                    ScreenUtils.saveScreenshot(Vars.dataDirectory.child("ring-render.png"));
                    ringCaptured = true;
                }
            });
            Groups.draw.each(drawable -> {
                if(drawable instanceof mindustry.gen.EffectState){
                    mindustry.gen.EffectState effect = (mindustry.gen.EffectState)drawable;
                    if(effect.effect == mindustry.content.Fx.lightning && effect.color.equals(arc.graphics.Color.valueOf("be78ed")) && !chainCaptured){
                        ScreenUtils.saveScreenshot(Vars.dataDirectory.child("chain-render.png"));
                        chainCaptured = true;
                    }
                }
            });
            if(!warningCaptured && started && Vars.state.rules.tags.containsKey("cryon-intrusion-warning-angle")){
                ScreenUtils.saveScreenshot(Vars.dataDirectory.child("warning-render.png"));
                warningCaptured = true;
            }
            if(!captured && upgraded && started && Vars.state.isGame() && Groups.bullet.size() > 0){
                ScreenUtils.saveScreenshot(Vars.dataDirectory.child("combat-render.png"));
                captured = true;
            }
        });
        Events.run(Trigger.uiDrawEnd, () -> {
            String elapsed = Vars.state.rules.tags.get("cryon-intrusion-transform-time");
            if(!dialogueCaptured && elapsed != null && Float.parseFloat(elapsed) > 150f){
                ScreenUtils.saveScreenshot(Vars.dataDirectory.child("transform-dialogue.png"));
                dialogueCaptured = true;
            }
        });
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
