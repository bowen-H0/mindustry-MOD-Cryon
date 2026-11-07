import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.util.Log;
import arc.util.ScreenUtils;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.ctype.ContentType;
import mindustry.game.EventType.*;
import mindustry.gen.Groups;
import mindustry.io.SaveIO;
import mindustry.type.SectorPreset;

/** Run DesktopLauncher with an isolated mindustry.data.dir; captures approach and settling. */
public final class DesolateFleetRenderCheck{
    static Class<?> directorType;
    static Object director;
    static boolean started, front, flanks, rear, released;
    public static void main(String[] args){
        Events.on(ClientLoadEvent.class, event -> Timer.schedule(() -> {
            try{
                Vars.logic.reset();
                Core.settings.put("backgroundpause", false);
                SaveIO.load(new Fi("maps/cryon-desolate-plain.msav"));
                SectorPreset preset = Vars.content.getByName(ContentType.sector, "cryon-desolate-plain");
                Vars.state.rules.sector = preset.sector;
                preset.sector.info.wasCaptured = false;
                Vars.state.wave = 100;
                Vars.state.rules.pauseDisabled = true;
                for(var tile : Vars.world.tiles){
                    if(tile.block() == mindustry.content.Blocks.air && tile.overlay() != mindustry.content.Blocks.spawn && !tile.floor().isDeep()){
                        tile.setBlock(Vars.content.block("cryon-java-processor"), Vars.state.rules.waveTeam);
                        tile.build.getClass().getMethod("setScript", String.class).invoke(tile.build, "desolate_plain");
                        break;
                    }
                }
                for(var unit : Groups.unit.copy()) if(unit.team == Vars.state.rules.waveTeam) unit.remove();
                Vars.logic.play();
                directorType = Vars.mods.mainLoader().loadClass("cryon.Script.DesolatePlainScript");
                started = true;
                Timer.schedule(() -> {
                    if(!front || !flanks || !rear || !released){
                        Log.err("Incomplete fleet render check: front=@ flanks=@ rear=@ released=@", front, flanks, rear, released);
                        System.exit(1);
                        return;
                    }
                    Log.info("DESOLATE_RENDER_CHECK_PASS: fleet approach, advance, settling, camera/HUD restoration");
                    Core.app.exit();
                }, 10f);
            }catch(Exception error){ throw new RuntimeException(error); }
        }, 5f));
        Events.run(Trigger.uiDrawEnd, () -> {
            if(!started) return;
            try{
                director = directorType.getMethod("current").invoke(null);
                if(director == null) return;
                int phase = directorType.getField("phase").getInt(director);
                float elapsed = directorType.getField("elapsed").getFloat(director);
                if(phase == 1){
                    if(!Vars.control.input.logicCutscene || Vars.ui.hudfrag.shown)
                        throw new AssertionError("Cinematic camera/HUD not active");
                    if(!front && elapsed >= 90f){ ScreenUtils.saveScreenshot(Vars.dataDirectory.child("fleet-front.png")); front = true; }
                    if(!flanks && elapsed >= 165f){ ScreenUtils.saveScreenshot(Vars.dataDirectory.child("fleet-flanks.png")); flanks = true; }
                    if(!rear && elapsed >= 255f){ ScreenUtils.saveScreenshot(Vars.dataDirectory.child("fleet-rear.png")); rear = true; }
                }else if(phase == 2){
                    if(Vars.control.input.logicCutscene || !Vars.ui.hudfrag.shown)
                        throw new AssertionError("Cinematic camera/HUD not released");
                    released = true;
                }
            }catch(Exception error){ throw new RuntimeException(error); }
        });
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
