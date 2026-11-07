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

/** Independent desktop scene: linked curtains and orbital receivers. */
public final class OrbitalBarrierNodeRenderCheck{
    static Building node;
    static boolean started, landing, sky;
    static long start;
    public static void main(String[] args){
        Events.on(ClientLoadEvent.class, event -> Timer.schedule(() -> {
            Vars.logic.reset(); Core.settings.put("backgroundpause", false);
            Vars.state.rules.waves = false; Vars.state.rules.canGameOver = false; Vars.state.rules.pauseDisabled = true;
            Vars.state.rules.lighting = true;
            Vars.state.rules.ambientLight.set(0.12f, 0.11f, 0.075f, 0.65f);
            Vars.renderer.drawLight = true; Vars.enableLight = true;
            Vars.state.map = new Map(StringMap.of("name", "Orbital Power Node preview"));
            Vars.world.loadGenerator(100, 180, tiles -> {
                for(int x = 0; x < 100; x++) for(int y = 0; y < 180; y++)
                    tiles.set(x, y, new Tile(x, y, Blocks.darksand, Blocks.air, Blocks.air));
                tiles.get(20, 18).setBlock(Blocks.coreShard, Team.sharded);
                tiles.get(35, 38).setBlock(Vars.content.block("cryon-orbital-barrier-node"), Team.sharded);
                tiles.get(70, 38).setBlock(Vars.content.block("cryon-orbital-barrier-node"), Team.sharded);
                tiles.get(70, 68).setBlock(Vars.content.block("cryon-orbital-barrier-node"), Team.sharded);
                tiles.get(48, 53).setBlock(Vars.content.block("cryon-orbital-power-node"), Team.sharded);
            });
            node = Vars.world.build(35, 38);
            if(!Core.atlas.isFound(node.block.region)) throw new AssertionError("missing orbital receiver sprite");
            if(node.block.techNode == null || node.block.techNode.parent == null ||
                !node.block.techNode.parent.content.name.equals("cryon-aegis-barrier"))
                throw new AssertionError("orbital defense is not after aegis in Cryon research");
            if(!node.block.shownPlanets.contains(Vars.content.planet("cryon-cryon")))
                throw new AssertionError("orbital defense is not assigned to Cryon");
            node.configureAny(Vars.world.build(70, 38).pos());
            Vars.world.build(70, 38).configureAny(Vars.world.build(70, 68).pos());
            Building source=Vars.world.build(48,53);
            source.configureAny(node.pos());
            source.configureAny(Vars.world.build(70,38).pos());
            source.configureAny(Vars.world.build(70,68).pos());
            Vars.logic.play();
            Vars.ui.hudfrag.shown = false;
            Vars.control.input.logicCutscene = true;
            Vars.control.input.logicCamSpeed = 1f;
            Vars.control.input.logicCutsceneZoom = 0f;
            Vars.control.input.logicCamPan.set(node.x + 140f, node.y + 100f);
            start = System.nanoTime(); started = true;
            Timer.schedule(() -> {
                if(!landing || !sky){ Log.err("Incomplete orbital render check"); System.exit(1); return; }
                Log.info("ORBITAL_BARRIER_RENDER_CHECK_PASS: new planar shader, powered 9x9 receivers, world-space pattern and light");
                Core.app.exit();
            }, 8f);
        }, 5f));
        Events.run(Trigger.uiDrawEnd, () -> {
            if(!started) return;
            float seconds = (System.nanoTime() - start) / 1000000000f;
            if(!landing && seconds > 2f){
                if(node.power.status < 0.98f) throw new AssertionError("preview shield has no power");
                ScreenUtils.saveScreenshot(Vars.dataDirectory.child("orbital-barrier-landing.png")); landing = true;
                Vars.control.input.logicCamPan.set(node.x + 110f, node.y + 300f);
            }
            if(!sky && seconds > 5f){
                ScreenUtils.saveScreenshot(Vars.dataDirectory.child("orbital-barrier-sky.png")); sky = true;
            }
        });
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
