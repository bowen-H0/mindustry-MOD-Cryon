import arc.*;
import arc.files.Fi;
import arc.scene.ui.TextButton;
import arc.util.*;
import mindustry.*;
import mindustry.game.EventType.*;
import mindustry.graphics.g3d.PlanetParams;
import mindustry.type.*;
import mindustry.ui.dialogs.BaseDialog;

/** Exercises the actual unit-launch library map used by the bundled desktop mod. */
public class CryonLayersRenderCheck{
    static Fi dir;
    static boolean ocean;
    static BaseDialog map;
    static void check(boolean value, String message){ if(!value) throw new AssertionError(message); }
    static void guarded(Runnable task){ try{ task.run(); }catch(Throwable e){ Log.err(e); System.exit(1); } }
    static Object field(String name){
        try{ return map.getClass().getField(name).get(map); }catch(Exception e){ throw new RuntimeException(e); }
    }
    static void mode(String name){
        try{
            var f = map.getClass().getField("mode");
            for(Object constant : f.getType().getEnumConstants()) if(constant.toString().equals(name)) f.set(map, constant);
        }catch(Exception e){ throw new RuntimeException(e); }
    }
    static void call(String name, Class<?>[] types, Object... values){
        try{ map.getClass().getMethod(name, types).invoke(map, values); }catch(Exception e){ throw new RuntimeException(e); }
    }
    static PlanetParams params(){ return (PlanetParams)field("state"); }
    static void screenshot(String name){
        Core.scene.act(); Core.scene.draw();
        ScreenUtils.saveScreenshot(dir.child(name));
    }
    static void click(String name){ ((TextButton)map.find("cryon-layer-" + name)).fireClick(); }
    static void checkUnifiedMap(BaseDialog dialog, Planet surface, Planet depths){
        int[] entries = {0};
        dialog.forEach(element -> {
            if(element instanceof TextButton){
                String text = ((TextButton)element).getText().toString();
                check(!text.equals(depths.localizedName), "Underground still has a separate planet-list entry");
                if(text.equals(surface.localizedName)) entries[0]++;
            }
        });
        check(entries[0] == 1, "Expected exactly one Cryon entry, found " + entries[0]);
        check(depths.position == surface.position, "Layers do not share one position");
        check(surface.getWorldPosition(new arc.math.geom.Vec3()).epsilonEquals(
                depths.getWorldPosition(new arc.math.geom.Vec3()), 0.0001f), "Layers occupy different orbital locations");
        check(surface.visible != depths.visible, "Both layer bodies rendered together");
        check(Math.abs(surface.getRotation() - depths.getRotation()) < 0.0001f, "Layers rotate as separate planets");
        if(dialog == map){
            try{
                Planet picked = (Planet)dialog.getClass().getMethod("getHoverPlanet", float.class, float.class)
                        .invoke(dialog, Core.graphics.getWidth() / 2f, Core.graphics.getHeight() / 2f);
                check(picked == params().planet, "Hidden layer intercepts globe clicks");
            }catch(ReflectiveOperationException error){ throw new RuntimeException(error); }
        }
    }
    public static void main(String[] args){
        dir = new Fi(System.getProperty("mindustry.data.dir"));
        Fi repo = new Fi(System.getProperty("cryon.repo", "."));
        repo.child("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(dir.child("mods/cryon.jar"));
        repo.child("单位发射台library.jar").copyTo(dir.child("mods/unitlanuch.jar"));
        Events.on(ClientLoadEvent.class, e -> Timer.schedule(() -> guarded(() -> {
            Core.settings.put("campaignselect", true);
            for(var element : Core.scene.root.getChildren().copy()) if(element instanceof arc.scene.ui.Dialog d){ d.clearActions(); d.hide(null); }
            Vars.ui.planet.show();
            Timer.schedule(() -> guarded(() -> {
                try{
                    var mod = Vars.mods.locateMod("cryon");
                    var layers = mod.main.getClass().getClassLoader().loadClass("cryon.Features.CryonCampaignLayers");
                    map = (BaseDialog)layers.getMethod("activeDialog").invoke(null);
                }catch(Exception error){ throw new RuntimeException(error); }
                check(map != null && map.isShown() && map != Vars.ui.planet, "Library replacement map was not used");
                var surface = Vars.content.planet("cryon-cryon");
                var depths = Vars.content.planet("cryon-cryon-depths");
                // This fixture exercises the ocean view after the Pioneer story agreement.
                Core.settings.put("cryon-story-underground", true);
                depths.accessible = true;
                check(surface.techTree != null && surface.techTree == depths.techTree, "Research not shared on actual client");
                check(Vars.content.item("cryon-aluminum").isOnPlanet(depths), "Local items hidden underground");
                check(mindustry.content.UnitTypes.risso.isOnPlanet(mindustry.content.Planets.serpulo), "Native fleet disappeared from Serpulo");
                check(Vars.content.block("cryon-abyssal-shipyard") == null, "Removed shipyard is still registered");
                call("viewPlanet", new Class[]{Planet.class, boolean.class}, surface, false);
                call("lookAt", new Class[]{Sector.class}, surface.getStartSector());
                Timer.schedule(() -> guarded(() -> {
                    check(map.isShown() && params().planet == surface, "Surface map is not visible");
                    checkUnifiedMap(map, surface, depths);
                    screenshot("surface.png");
                    check(map.find("cryon-campaign-layers").visible, "Layer switch is invisible on the real map");
                    click("underground");
                    check(params().planet == depths && ((Sector)field("selected")).planet == depths, "Underground click failed");
                    check(!surface.visible && depths.visible, "Surface obscures underground");
                    Timer.schedule(() -> guarded(() -> {
                        screenshot("underground.png");
                        check(map.isShown() && params().planet == depths, "Underground view lost");
                        checkUnifiedMap(map, surface, depths);
                        var entry = (TextButton)map.find("cryon-planet-entry");
                        entry.fireClick();
                        check(params().planet == depths, "Unified Cryon entry reset the underground layer");
                        Vars.ui.research.show();
                        check(Vars.ui.research.lastNode == surface.techTree, "Library star-map research opened the wrong tree");
                        Vars.ui.research.hide(null);
                        click("surface");
                        check(params().planet == surface && ((Sector)field("selected")).planet == surface, "Surface return failed");
                        mode("select"); click("underground");
                        check(params().planet == surface, "Layer change bypasses unit transport selector restrictions");
                        mode("look"); click("underground"); map.hide(null);
                        check(surface.visible && !depths.visible, "Planet visibility leaked after closing");
                        map.show(); call("viewPlanet", new Class[]{Planet.class, boolean.class}, depths, false);
                        check(map.getChildren().count(child -> "cryon-campaign-layers".equals(child.name)) == 1, "Duplicated layer overlays");
                        checkUnifiedMap(map, surface, depths);
                        map.hide(null);
                        try{
                            var lock = map.getClass().getField("lockPlanetReplace");
                            lock.setBoolean(null, true);
                            var nativeMap = Vars.ui.planet;
                            nativeMap.show(); nativeMap.viewPlanet(surface, false);
                            Core.scene.act();
                            checkUnifiedMap(nativeMap, surface, depths);
                            check(nativeMap.find("cryon-campaign-layers").visible, "Native layer switch is hidden");
                            ((TextButton)nativeMap.find("cryon-layer-underground")).fireClick();
                            check(nativeMap.state.planet == depths, "Native map failed to switch underground");
                            checkUnifiedMap(nativeMap, surface, depths);
                            Vars.ui.research.show(); Vars.ui.research.hide(null);
                            Events.fire(Trigger.uiDrawBegin);
                            checkUnifiedMap(nativeMap, surface, depths);
                            check(nativeMap.find("cryon-layer-surface") != null, "Research rebuild lost layer controls");
                            ((TextButton)nativeMap.find("cryon-layer-surface")).fireClick();
                            check(nativeMap.state.planet == surface, "Native map failed to return to surface");
                            nativeMap.hide(null); lock.setBoolean(null, false);
                        }catch(Exception error){ throw new RuntimeException(error); }
                        Vars.control.playSector(depths.getStartSector());
                        Timer.schedule(() -> guarded(() -> {
                            check(Vars.state.rules.sector == depths.getStartSector(), "Campaign launch entered wrong layer");
                            check(mindustry.game.Team.sharded.core() != null, "No player core");
                            Vars.state.rules.canGameOver = false; Vars.state.rules.waveTimer = false;
                            Vars.state.rules.waves = false; Vars.state.rules.attackMode = false;
                            Events.fire(new SectorCaptureEvent(depths.getStartSector(), true));
                            check(Vars.content.sector("cryon-depths-strait").locked(), "Capture unlocked an unreleased ocean mission");
                            check(Vars.content.sector("cryon-depths-archipelago").locked(), "Capture unlocked the entire campaign");
                            map.show();
                            Core.scene.act();
                            checkUnifiedMap(map, surface, depths);
                            screenshot("after-base.png");
                            call("showSelect", new Class[]{Sector.class, arc.func.Cons.class}, depths.getStartSector(), (arc.func.Cons<Sector>)sector -> {});
                            Core.scene.act();
                            checkUnifiedMap(map, surface, depths);
                            map.hide(null);
                            ocean = true;
                            Timer.schedule(() -> guarded(() -> {
                                screenshot("ocean-map.png");
                                Log.info("CRYON_LAYERS_RENDER_PASS: one Cryon list entry in both maps and transport selectors, one shared orbital position, layer switching and independent campaign progress");
                                Core.app.exit();
                            }), 2f);
                        }), 4f);
                    }), 2f);
                }), 2f);
            }), 1f);
        }), 2f));
        Events.run(Trigger.update, () -> {
            if(ocean){ Core.camera.position.set(65 * 8f, 47 * 8f); Vars.renderer.setScale(1.6f); }
        });
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
