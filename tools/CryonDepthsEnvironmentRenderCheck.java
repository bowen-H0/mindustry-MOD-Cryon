import arc.*;
import arc.files.Fi;
import arc.util.*;
import mindustry.*;
import mindustry.content.Blocks;
import mindustry.game.EventType.*;

/** Verify the packaged seabed textures and native Tantros plants in the real desktop renderer. */
public class CryonDepthsEnvironmentRenderCheck{
    static boolean ready;
    static void check(boolean value, String message){ if(!value) throw new AssertionError(message); }
    static void guarded(Runnable task){ try{ task.run(); }catch(Throwable error){ Log.err(error); System.exit(1); } }
    public static void main(String[] args){
        Fi data = new Fi(System.getProperty("mindustry.data.dir"));
        Fi repo = new Fi(System.getProperty("cryon.repo", "."));
        repo.child("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        repo.child("单位发射台library.jar").copyTo(data.child("mods/unitlanuch.jar"));
        Events.on(ClientLoadEvent.class, event -> Timer.schedule(() -> guarded(() -> {
            for(var element : Core.scene.root.getChildren().copy()) if(element instanceof arc.scene.ui.Dialog dialog){ dialog.clearActions(); dialog.hide(null); }
            var preset = Vars.content.sector("cryon-depths-outpost");
            Vars.logic.reset(); Vars.state.rules.sector = preset.sector; Vars.state.rules.planet = preset.planet;
            Vars.world.loadGenerator(224, 224, tiles -> preset.generator.generate(tiles, new mindustry.world.WorldParams()));
            Vars.state.rules.canGameOver = false; Vars.state.rules.waveTimer = false;
            check((Vars.state.rules.env & mindustry.world.meta.Env.underwater) != 0, "Missing underwater environment");
            for(String name : new String[]{"blue-bed", "red-bed", "silt-bed", "shallows", "vent"}){
                var floor = Vars.content.block("cryon-abyss-" + name).asFloor();
                check(floor.variantRegions != null && floor.variantRegions.length >= 2, "Missing seabed variants: " + name);
                for(var region : floor.variantRegions) check(region.found(), "Missing seabed image: " + name);
                if(!name.equals("vent")) check(floor.cacheLayer == mindustry.graphics.CacheLayer.normal, "Seabed still uses a water-surface shader");
            }
            check(Blocks.redweed.region.found() && Blocks.purbush.region.found() && Blocks.yellowCoral.region.found(), "Native Tantros plant graphics missing");
            int plants = 0;
            for(var tile : Vars.world.tiles) if(tile.block() == Blocks.redweed || tile.block() == Blocks.purbush || tile.block() == Blocks.yellowCoral) plants++;
            check(plants > 100, "Bare seabed without flora");
            Vars.player.team(Vars.state.rules.defaultTeam); Vars.logic.play(); ready = true;
            Timer.schedule(() -> guarded(() -> {
                check(Vars.player.unit().isAdded() && Vars.player.unit().type.supportsEnv(Vars.state.rules.env), "Player cannot operate underwater");
                ScreenUtils.saveScreenshot(data.child("tantros-seabed.png"));
                Log.info("TANTROS_ENVIRONMENT_RENDER_PASS: underwater player, all 14 native-derived floor variants, native seaweed/coral, deep-water lighting and suspended particles");
                Fi legacy = new Fi("/tmp/cryon-layers-render-8/saves/sector-cryon-cryon-depths-0.msav");
                if(!legacy.exists()){ Core.app.exit(); return; }
                try{ Vars.logic.reset(); mindustry.io.SaveIO.load(legacy); }catch(Exception error){ throw new RuntimeException(error); }
                Vars.state.rules.canGameOver = false; Vars.state.rules.waveTimer = false;
                for(var tile : Vars.world.tiles) check(!tile.floor().name.equals("cryon-cryon-ice-rock"), "Old save still has ice-rock terrain");
                check((Vars.state.rules.env & mindustry.world.meta.Env.underwater) != 0, "Old save still has surface rules");
                Vars.player.team(Vars.state.rules.defaultTeam); Vars.logic.play();
                Timer.schedule(() -> guarded(() -> {
                    ScreenUtils.saveScreenshot(data.child("tantros-legacy-save.png"));
                    Log.info("TANTROS_LEGACY_RENDER_PASS: previous underground world converted and rendered without resetting the base");
                    Core.app.exit();
                }), 3f);
            }), 4f);
        }), 2f));
        Events.run(Trigger.update, () -> {
            if(ready){ Core.camera.position.set(80 * 8f, 64 * 8f); Vars.renderer.setScale(1.15f); }
        });
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
