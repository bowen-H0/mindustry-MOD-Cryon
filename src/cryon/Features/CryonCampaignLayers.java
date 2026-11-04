package cryon.Features;

import arc.Core;
import arc.Events;
import arc.util.Log;
import arc.scene.event.Touchable;
import arc.scene.ui.TextButton;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectSet;
import cryon.Content.CryonItems;
import cryon.Content.SubmarineContent;
import cryon.Generator.CryonDepthsGenerator;
import cryon.Type.CryonLayerPlanet;
import mindustry.Vars;
import mindustry.content.TechTree.TechNode;
import mindustry.content.UnitTypes;
import mindustry.ctype.UnlockableContent;
import mindustry.game.EventType.*;
import mindustry.game.Objectives.SectorComplete;
import mindustry.graphics.g3d.HexMesh;
import mindustry.graphics.g3d.PlanetParams;
import mindustry.type.*;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import java.lang.reflect.*;

/** Two views of Cryon, sharing research but retaining native, separate sector saves. */
public final class CryonCampaignLayers{
    public static final String[] stages = {"depths-outpost", "depths-strait", "depths-archipelago",
            "depths-corridor", "depths-vents", "depths-nexus"};
    public static final int shownStages = 1;
    private static Planet surface, depths;
    private static boolean installed;
    private static LayerView nativeView, libraryView;

    private CryonCampaignLayers(){}

    public static void load(){
        // Register the layer before HJSON planets so a visible layer wins equal-distance picking.
        new CryonLayerPlanet();
    }

    public static void configure(){
        surface = Vars.content.planet("cryon-cryon");
        depths = Vars.content.planet("cryon-cryon-depths");
        if(surface == null || depths == null) return;
        depths.generator = new CryonDepthsGenerator();
        depths.defaultCore = surface.defaultCore;
        for(Sector sector : depths.sectors) depths.generator.generateSector(sector);
        // Keep future maps and save namespaces, but expose only the current release's missions.
        for(int i = 0; i < stages.length; i++){
            SectorPreset preset = Vars.content.sector("cryon-" + stages[i]);
            if(preset == null) continue;
            preset.sector.preset = i < shownStages ? preset : null;
            if(i >= shownStages) preset.sector.generateEnemyBase = false;
        }
        depths.defaultEnv = cryon.Content.CryonDepthsEnvironment.environment;
        depths.updateLighting = false;
        syncOrbit();
        depths.rotateTime = surface.rotateTime;
        depths.allowWaves = true;
        depths.allowSectorInvasion = false;
        depths.ruleSetter = rules -> {
            cryon.Content.CryonDepthsEnvironment.applyRules(rules);
            rules.placeRangeCheck = false;
            rules.coreDestroyClear = true;
        };
        if(!Vars.headless){
            depths.meshLoader = () -> new HexMesh(depths, 6);
            depths.reloadMesh();
        }
    }

    public static void shareResearch(){
        if(surface == null || depths == null || surface.techTree == null) return;
        depths.techTree = surface.techTree;
        // Copy ownership after all three trees are built, including native shared items.
        for(var list : Vars.content.getContentMap()) for(var content : list){
            if(content instanceof UnlockableContent unlocked && unlocked.shownPlanets != null
                    && unlocked.shownPlanets.contains(surface)){
                unlocked.shownPlanets.add(depths);
                unlocked.databaseTabs.add(depths);
            }
        }
        for(var entry : CryonItems.itemPlanets){
            if(entry.value.contains(surface.name)) entry.value.add(depths.name);
        }
        TechNode parent = surface.techTree;
        for(int i = 0; i < stages.length; i++){
            SectorPreset preset = Vars.content.sector("cryon-" + stages[i]);
            if(preset == null) throw new IllegalStateException("Missing Cryon ocean stage: " + stages[i]);
            if(preset.techNode != null) preset.techNode.remove();
            if(i >= shownStages){
                preset.techNode = null;
                preset.shownPlanets = new ObjectSet<>();
                preset.databaseTabs.remove(depths);
                continue;
            }
            TechNode node = new TechNode(parent, preset, ItemStack.empty);
            node.planet = surface;
            if(i > 0) node.objectives.add(new SectorComplete(Vars.content.sector("cryon-" + stages[i - 1])));
            preset.shownPlanets = ObjectSet.with(depths);
            preset.databaseTabs.add(depths);
            parent = node;
        }
        TechNode production = CryonContent.block("unit-projector").techNode;
        UnitType[] ships = {SubmarineContent.minimus, SubmarineContent.danae, SubmarineContent.pammelas,
                SubmarineContent.schmidti, SubmarineContent.sloani};
        String[] upgrades = {"unit-projector", "t2factory", "t3universal-assembler", "t4universal-assembler", "t5universal-assembler"};
        TechNode previous = production;
        for(int i = 0; i < ships.length; i++){
            TechNode node = new TechNode(previous, ships[i], ItemStack.with(CryonItems.aluminum, 100 * (i + 1),
                    mindustry.content.Items.graphite, 60 * (i + 1), mindustry.content.Items.silicon, 80 * (i + 1)));
            node.planet = surface;
            node.objectives.add(new mindustry.game.Objectives.Research(CryonContent.block(upgrades[i])));
            if(ships[i].shownPlanets == null) ships[i].shownPlanets = new ObjectSet<>();
            ships[i].shownPlanets.add(surface);
            ships[i].shownPlanets.add(depths);
            ships[i].databaseTabs.add(surface);
            ships[i].databaseTabs.add(depths);
            previous = node;
        }
        TechNode mines = new TechNode(SubmarineContent.danae.techNode, SubmarineContent.seaMine,
                ItemStack.with(CryonItems.aluminum, 100, mindustry.content.Items.graphite, 80, mindustry.content.Items.titanium, 40));
        mines.planet = surface;
        SubmarineContent.seaMine.shownPlanets = ObjectSet.with(surface, depths);
        SubmarineContent.seaMine.databaseTabs.add(depths);
        for(var block : Vars.content.blocks()) if(block.name.startsWith("cryon-abyss-")) block.databaseTabs.add(depths);
        cryon.Story.StoryProgress.gateResearch();
        unlockRoute();
    }

    /** Also repairs progression when opening an existing ocean save after an update. */
    public static void unlockRoute(){
        if(!cryon.Story.StoryProgress.get(cryon.Story.StoryProgress.Flag.underground)) return;
        for(int i = 0; i < shownStages; i++){
            SectorPreset current = Vars.content.sector("cryon-" + stages[i]);
            if(current == null) return;
            if(i == 0 || Vars.content.sector("cryon-" + stages[i - 1]).sector.isCaptured()) current.unlock();
        }
    }

    public static void install(){
        if(installed || surface == null || depths == null) return;
        installed = true;
        Events.on(SectorCaptureEvent.class, event -> {
            if(event.sector != null && event.sector.planet == depths) unlockRoute();
        });
        if(Vars.headless) return;
        Events.run(Trigger.update, CryonCampaignLayers::syncOrbit);
        Events.on(ClientLoadEvent.class, event -> {
            nativeView = new LayerView(Vars.ui.planet);
            attach(nativeView);
            // The bundled unit-launch library owns a separate BaseDialog, not a PlanetDialog subclass.
            var library = Vars.mods.locateMod("unitlanuch");
            if(library != null && library.main != null){
                try{
                    Class<?> vars = library.main.getClass().getClassLoader().loadClass("ul.CVars");
                    Object ui = vars.getField("cui").get(null);
                    libraryView = new LayerView((BaseDialog)ui.getClass().getField("cplanet").get(ui));
                    attach(libraryView);
                }catch(ReflectiveOperationException error){
                    Log.err("[Cryon] Cannot attach campaign layers to unit-launch star map", error);
                }
            }
            Vars.ui.research.shown(() -> {
                LayerView view = activeView();
                if(view != null && view.dialog.isShown() && isCryon(view.params.planet)) Vars.ui.research.switchTree(surface.techTree);
            });
            Events.run(Trigger.uiDrawBegin, () -> {
                ensureLayerUi(nativeView);
                ensureLayerUi(libraryView);
            });
        });
    }

    private static void attach(LayerView view){
        view.dialog.shown(() -> buildLayerUi(view));
        view.dialog.hidden(() -> { surface.visible = true; depths.visible = false; });
    }

    private static void ensureLayerUi(LayerView view){
        if(view == null || !view.dialog.isShown()) return;
        hideLockedSystemEntries(view);
        // Research and the library may rebuild the map without showing the dialog again.
        if(view.dialog.find("cryon-campaign-layers") == null) buildLayerUi(view);
        syncVisibility(view);
    }

    private static void buildLayerUi(LayerView view){
        BaseDialog dialog = view.dialog;
        hideLockedSystemEntries(view);
        mergePlanetEntries(view);
        Table overlay = new Table();
        overlay.name = "cryon-campaign-layers";
        overlay.setFillParent(true);
        overlay.touchable = Touchable.childrenOnly;
        overlay.top().marginTop(Vars.mobile ? 64f : 20f);
        overlay.visible(() -> view.looking() && isCryon(view.params.planet));
        overlay.table(Styles.black8, bar -> {
            bar.defaults().height(46f).width(Core.bundle.get("cryon.layer.underground").length() > 6 ? 180f : 130f).pad(4f);
            bar.button("@cryon.layer.surface", Styles.togglet, () -> switchLayer(view, false))
                    .checked(button -> view.params.planet == surface).get().name = "cryon-layer-surface";
            bar.button("@cryon.layer.underground", Styles.togglet, () -> switchLayer(view, true))
                    .disabled(button -> !cryon.Story.StoryProgress.get(cryon.Story.StoryProgress.Flag.underground))
                    .checked(button -> view.params.planet == depths).get().name = "cryon-layer-underground";
        });
        overlay.update(() -> syncVisibility(view));
        dialog.addChild(overlay);
        // Dialog.shown runs before the dialog is attached to the scene.
        showLayer(view);
    }

    /** One physical body: the auxiliary sector namespace shares Cryon's actual position vector. */
    private static void syncOrbit(){
        depths.orbitRadius = surface.orbitRadius;
        depths.orbitTime = surface.orbitTime;
        depths.orbitOffset = surface.orbitOffset;
        depths.drawOrbit = surface.drawOrbit;
        depths.position = surface.position;
    }

    private static void mergePlanetEntries(LayerView view){
        TextButton surfaceEntry = view.dialog.find(element -> element instanceof TextButton
                && ((TextButton)element).getText().toString().equals(surface.localizedName));
        TextButton depthsEntry = view.dialog.find(element -> element instanceof TextButton
                && ((TextButton)element).getText().toString().equals(depths.localizedName));
        view.planetEntry = surfaceEntry != null ? surfaceEntry : depthsEntry;
        if(surfaceEntry != null && depthsEntry != null){
            // Remove the cell as well as the actor's visual space, including after bases exist below.
            Table parent = (Table)depthsEntry.parent;
            parent.getCell(depthsEntry).height(0f).pad(0f).setElement(null);
        }
        if(view.planetEntry == null) return;
        view.planetEntry.setText(surface.localizedName);
        view.planetEntry.name = "cryon-planet-entry";
        view.planetEntry.clicked(() -> {
            if(view.looking()) switchLayer(view, Core.settings.getBool("cryon-underground-view", false));
        });
        if(view.looking() && isCryon(view.params.planet)){
            Core.settings.put("cryon-underground-view", view.params.planet == depths);
        }
    }

    private static LayerView activeView(){
        return libraryView != null && libraryView.dialog.isShown() ? libraryView : nativeView;
    }

    /** Exposes the map currently used by the client, including the optional library replacement. */
    public static BaseDialog activeDialog(){
        LayerView view = activeView();
        return view == null ? null : view.dialog;
    }

    private static boolean isCryon(Planet planet){ return planet == surface || planet == depths; }

    private static void syncVisibility(LayerView view){
        if(!view.dialog.isShown()) return;
        showLayer(view);
    }

    private static void showLayer(LayerView view){
        Planet planet = view.params.planet;
        boolean lockedSystem = planet != null && planet.solarSystem != null
                && planet.solarSystem.name.equals("cryon-kyanos")
                && !cryon.Story.StoryProgress.get(cryon.Story.StoryProgress.Flag.actTwo);
        if(lockedSystem || (planet == depths && !cryon.Story.StoryProgress.get(cryon.Story.StoryProgress.Flag.underground))){
            view.invoke(view.viewPlanet, surface, false);
            view.set(view.selected, null);
            view.set(view.hovered, null);
        }
        boolean underground = view.params.planet == depths;
        surface.visible = !underground;
        depths.visible = underground;
        if(view.planetEntry != null) view.planetEntry.setChecked(isCryon(view.params.planet));
    }

    private static void hideLockedSystemEntries(LayerView view){
        if(cryon.Story.StoryProgress.get(cryon.Story.StoryProgress.Flag.actTwo)) return;
        Planet planet = Vars.content.planet("cryon-porphyra");
        if(planet == null) return;
        // The library lists planets with old bases regardless of accessible/visible.
        TextButton entry;
        while((entry = view.dialog.find(element -> element instanceof TextButton
                && ((TextButton)element).getText().toString().equals(planet.localizedName))) != null){
            if(entry.parent instanceof Table){
                Table parent = (Table)entry.parent;
                parent.getCell(entry).height(0f).pad(0f).setElement(null);
            }else entry.remove();
        }
        Planet star = Vars.content.planet("cryon-kyanos");
        if(star == null) return;
        Label heading;
        while((heading = view.dialog.find(element -> element instanceof Label
                && ((Label)element).getText().toString().equals(star.localizedName))) != null){
            if(heading.parent instanceof Table && ((Table)heading.parent).getCell(heading) != null){
                ((Table)heading.parent).getCell(heading).height(0f).pad(0f).setElement(null);
            }else heading.remove();
        }
    }

    public static void switchLayer(boolean underground){
        LayerView view = activeView();
        if(view != null) switchLayer(view, underground);
    }

    private static void switchLayer(LayerView view, boolean underground){
        if(!view.looking() || !isCryon(view.params.planet)) return;
        if(underground && !cryon.Story.StoryProgress.get(cryon.Story.StoryProgress.Flag.underground)) return;
        Planet target = underground ? depths : surface;
        if(view.params.planet == target) return;
        Core.settings.put("cryon-underground-view", underground);
        Sector selected = (Sector)view.get(view.selected);
        if(selected != null) Core.settings.put("cryon-layer-sector-" + view.params.planet.name, selected.id);
        view.set(view.hovered, null);
        view.params.otherCamPos = null;
        view.invoke(view.viewPlanet, target, false);
        int id = Core.settings.getInt("cryon-layer-sector-" + target.name, target.startSector);
        Sector sector = target.sectors.get(id >= 0 && id < target.sectors.size ? id : target.startSector);
        if(sector.preset != null && sector.preset.locked() && !sector.hasBase()) sector = target.getStartSector();
        view.invoke(view.lookAt, sector);
        view.invoke(view.selectSector, sector);
        syncVisibility(view);
    }

    /** Cache the common public API once; neither library classes nor their source are bundled. */
    private static final class LayerView{
        final BaseDialog dialog;
        final PlanetParams params;
        final Field mode, selected, hovered;
        final Method viewPlanet, lookAt, selectSector;
        TextButton planetEntry;

        LayerView(BaseDialog dialog){
            this.dialog = dialog;
            try{
                Class<?> type = dialog.getClass();
                params = (PlanetParams)type.getField("state").get(dialog);
                mode = type.getField("mode"); selected = type.getField("selected"); hovered = type.getField("hovered");
                viewPlanet = type.getMethod("viewPlanet", Planet.class, boolean.class);
                lookAt = type.getMethod("lookAt", Sector.class);
                selectSector = type.getMethod("selectSector", Sector.class);
            }catch(ReflectiveOperationException e){ throw new IllegalStateException("Unsupported star-map interface", e); }
        }
        boolean looking(){ return "look".equals(String.valueOf(get(mode))); }
        Object get(Field field){
            try{ return field.get(dialog); }catch(ReflectiveOperationException e){ throw new IllegalStateException(e); }
        }
        void set(Field field, Object value){
            try{ field.set(dialog, value); }catch(ReflectiveOperationException e){ throw new IllegalStateException(e); }
        }
        void invoke(Method method, Object... args){
            try{ method.invoke(dialog, args); }catch(ReflectiveOperationException e){ throw new IllegalStateException(e); }
        }
    }
}
