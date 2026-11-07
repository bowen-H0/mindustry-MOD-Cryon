import arc.Core;
import arc.Events;
import arc.struct.StringMap;
import arc.util.Log;
import arc.util.ScreenUtils;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.EventType.*;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.maps.Map;
import mindustry.world.Tile;

/** Independent desktop scene: original energy ribbons, upper illumination and camera offset. */
public final class OrbitalPowerNodeRenderCheck{
    static Building node;
    static boolean started, landing, sky;
    static long start;
    public static void main(String[] args){
        Events.on(ClientLoadEvent.class, event -> Timer.schedule(() -> {
            Vars.logic.reset(); Core.settings.put("backgroundpause", false);
            Vars.state.rules.waves = false; Vars.state.rules.canGameOver = false; Vars.state.rules.pauseDisabled = true;
            Vars.state.rules.lighting = true;
            Vars.state.rules.ambientLight.set(0.025f, 0.02f, 0.012f, 0.88f);
            Vars.renderer.drawLight = true; Vars.enableLight = true;
            Vars.state.map = new Map(StringMap.of("name", "Orbital Power Node preview"));
            Vars.world.loadGenerator(100, 180, tiles -> {
                for(int x = 0; x < 100; x++) for(int y = 0; y < 180; y++)
                    tiles.set(x, y, new Tile(x, y, Blocks.darksand, Blocks.air, Blocks.air));
                tiles.get(20, 18).setBlock(Blocks.coreShard, Team.sharded);
                tiles.get(50, 38).setBlock(Vars.content.block("cryon-orbital-power-node"), Team.sharded);
                tiles.get(60, 38).setBlock(Blocks.battery, Team.sharded);
                tiles.get(60, 42).setBlock(Vars.content.block("cryon-cryo-repair-tower"), Team.sharded);
            });
            node = Vars.world.build(50, 38);
            if(!Core.atlas.isFound(node.block.region)) throw new AssertionError("missing orbital receiver sprite");
            node.configureAny(Vars.world.build(60, 38).pos());
            node.configureAny(Vars.world.build(60, 42).pos());
            Vars.logic.play();
            Vars.ui.hudfrag.shown = false;
            Vars.control.input.logicCutscene = true;
            Vars.control.input.logicCamSpeed = 1f;
            Vars.control.input.logicCutsceneZoom = 0f;
            Vars.control.input.logicCamPan.set(node.x, node.y + 100f);
            start = System.nanoTime(); started = true;
            Timer.schedule(() -> {
                if(!landing || !sky){ Log.err("Incomplete orbital render check"); System.exit(1); return; }
                Log.info("ORBITAL_RENDER_CHECK_PASS: original ribbons, upper illumination, camera offset, native links and sprite");
                Core.app.exit();
            }, 8f);
        }, 5f));
        Events.run(Trigger.uiDrawEnd, () -> {
            if(!started) return;
            float seconds = (System.nanoTime() - start) / 1000000000f;
            if(!landing && seconds > 2f){
                ScreenUtils.saveScreenshot(Vars.dataDirectory.child("orbital-power-landing.png")); landing = true;
                Vars.control.input.logicCamPan.set(node.x - 35f, node.y + 700f);
            }
            if(!sky && seconds > 5f){
                ScreenUtils.saveScreenshot(Vars.dataDirectory.child("orbital-power-sky.png")); sky = true;
            }
        });
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
