package cryon.Features;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.util.Reflect;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Liquids;
import mindustry.editor.DrawOperation;
import mindustry.editor.MapEditor;
import mindustry.editor.MapEditorDialog;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.ResizeEvent;
import mindustry.gen.Icon;
import mindustry.graphics.MultiPacker;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.environment.ShallowLiquid;
import mindustry.world.meta.Env;
import mindustry.ui.dialogs.BaseDialog;

/** A single editor operation for seabed conversion, water coverage and environment rules. */
public final class UnderwaterEditor{
    private static final String buttonName = "cryon-underwater-editor";
    private static final String[] seabeds = {
        "pillow-basalt", "pressure-slate", "obsidian", "limestone", "black-silt", "white-clay",
        "frozen-silt", "silica-crust", "ice-crystal-floor", "titanium-bed", "nickel-bed", "metal-nodules"
    };

    private UnderwaterEditor(){}

    public static void load(){
        for(String name : seabeds) new FloodedSeabed("abyss-flooded-" + name, "abyss-" + name);
    }

    public static void install(){
        if(Vars.headless) return;
        Events.on(ClientLoadEvent.class, event -> {
            MapEditorDialog dialog = Vars.ui.editor;
            // The menu survives editor layout rebuilds, including after a playtest.
            dialog.shown(() -> addButton(dialog));
            Events.on(ResizeEvent.class, resize -> Core.app.post(() -> {
                if(dialog.isShown()) addButton(dialog);
            }));
        });
    }

    private static void addButton(MapEditorDialog dialog){
        BaseDialog menu = Reflect.get(MapEditorDialog.class, dialog, "menu");
        if(menu.find(buttonName) != null) return;
        menu.cont.row();
        menu.cont.button("@cryon.editor.underwater", Icon.waves, () -> {
            menu.hide();
            convert();
            dialog.resetSaved();
            Vars.ui.showInfoFade(Core.bundle.get("cryon.editor.underwater.done"));
        }).size(370f, 60f).padTop(3f).tooltip("@cryon.editor.underwater.description").get().name = buttonName;
        menu.cont.row();
    }

    /** Run only in the map editor; all floor edits and the environment flag share one undo step. */
    public static void convert(){
        if(Vars.editor == null || Vars.state == null || Vars.state.isGame() || Vars.editor.isLoading()
                || Vars.world.isGenerating() || Vars.editor.width() == 0 || Vars.editor.height() == 0) return;
        MapEditor editor = Vars.editor;
        // Resolve the complete palette first, so missing content cannot leave a half-converted map.
        Floor[] palette = new Floor[seabeds.length];
        for(int i = 0; i < seabeds.length; i++) palette[i] = floor("abyss-flooded-" + seabeds[i]);
        Floor blue = floor("abyss-blue-bed"), red = floor("abyss-red-bed");
        Floor silt = floor("abyss-silt-bed"), shallows = floor("abyss-shallows");
        int before = Vars.state.rules.env, after = before | Env.terrestrial | Env.underwater;
        editor.flushOp();
        // Mindustry exposes tile operations but no API for adding a rule-aware DrawOperation.
        Reflect.set(MapEditor.class, editor, "currentOp", new FloodOperation(before, after));
        for(Tile tile : editor.tiles()){
            Floor source = tile.floor();
            Floor target = target(source, palette, blue, red, silt, shallows);
            if(source != target){
                tile.setFloor(target);
                if(!Vars.headless) Vars.renderer.blocks.floor.recacheTile(tile);
            }
        }
        Vars.state.rules.env = after;
        editor.flushOp();
        // flushOp deliberately leaves empty operations pending; do not let a repeated click
        // attach the next brush stroke to this rule-aware operation.
        Reflect.set(MapEditor.class, editor, "currentOp", null);
    }

    private static Floor floor(String name){
        Block block = CryonContent.block(name);
        if(!(block instanceof Floor)) throw new IllegalStateException("Missing underwater floor: " + name);
        return block.asFloor();
    }

    private static Floor target(Floor source, Floor[] palette, Floor blue, Floor red, Floor silt, Floor shallows){
        if(source instanceof FloodedSeabed || source == blue || source == red || source == silt || source == shallows) return source;
        for(int i = 0; i < seabeds.length; i++){
            if(source == ((FloodedSeabed)palette[i]).floorBase) return palette[i];
        }
        if(source == Blocks.redmat) return red;
        if(source == Blocks.bluemat) return blue;
        if(source.isLiquid && source.liquidDrop == Liquids.water) return source.shallow ? shallows : blue;

        // Keep exposed titanium/nickel deposits as mineable seabed instead of erasing them.
        for(int i = 9; i <= 10; i++){
            if(source.itemDrop != null && source.itemDrop == palette[i].itemDrop) return palette[i];
        }
        String name = source.name;
        if(name.contains("crystal") || name.contains("dark-ice")) return palette[8];
        if(name.contains("ice") || name.contains("snow")) return palette[6];
        if(name.contains("obsidian")) return palette[2];
        if(name.contains("basalt") || name.contains("hotrock") || name.contains("magmarock")
                || source.liquidDrop == Liquids.slag) return palette[0];
        if(name.contains("salt") || name.contains("white")) return palette[5];
        if(name.contains("sand") || name.contains("mud") || name.contains("dirt")
                || source.liquidDrop == Liquids.oil) return palette[4];
        if(name.contains("dacite") || name.contains("limestone")) return palette[3];
        if(name.contains("shale") || name.contains("slate") || name.contains("stone")) return palette[1];
        Color color = source.mapColor;
        return color.r > color.b * 1.15f ? red : blue;
    }

    private static final class FloodOperation extends DrawOperation{
        private final int before, after;

        FloodOperation(int before, int after){
            this.before = before;
            this.after = after;
        }

        @Override
        public boolean isEmpty(){ return before == after && super.isEmpty(); }

        @Override
        public void undo(){
            super.undo();
            Vars.state.rules.env = before;
        }

        @Override
        public void redo(){
            super.redo();
            Vars.state.rules.env = after;
        }
    }

    /** Native shallow-liquid icons blend actual seabed textures with water; no duplicate sprites. */
    private static final class FloodedSeabed extends ShallowLiquid{
        private final String baseName;

        FloodedSeabed(String name, String baseName){
            super(name);
            this.baseName = baseName;
            supportsOverlay = true;
            speedMultiplier = 0.7f;
            statusDuration = 60f;
        }

        @Override
        public void load(){
            // Mods packs icons before content.init(), so init() cannot supply these bases.
            // HJSON floors already exist here, even though they were registered after us.
            set(Blocks.water, floor(baseName));
            super.load();
        }

        @Override
        public void createIcons(MultiPacker packer){
            // The later HJSON floor and vanilla water may not have loaded their regions yet.
            // Load their actual variants before the native blender reads any pixels.
            liquidBase.load();
            floorBase.load();
            super.createIcons(packer);
        }

        @Override
        public void init(){
            Floor base = floor(baseName);
            set(Blocks.water, base);
            itemDrop = base.itemDrop;
            playerUnmineable = base.playerUnmineable;
            attributes.add(base.attributes);
            wall = base.wall;
            decoration = base.decoration;
            mapColor.set(base.mapColor).lerp(Blocks.water.mapColor, liquidOpacity);
            localizedName = Core.bundle.format("cryon.editor.submerged-floor", base.localizedName);
            super.init();
        }
    }
}
