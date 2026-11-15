import arc.*;
import arc.files.Fi;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.ui.*;
import arc.scene.ui.layout.Table;
import arc.util.*;
import java.lang.reflect.Method;
import mindustry.*;
import mindustry.editor.MapEditorDialog;
import mindustry.game.EventType.*;
import mindustry.game.Team;
import mindustry.ui.dialogs.BaseDialog;

/** Real desktop editor layout, native controls, full faction picker and recent-team persistence. */
public class EditorUiRenderCheck{
    static Fi data;
    static Class<?> ui;
    static void check(boolean value, String message){ if(!value) throw new AssertionError(message); }
    static void guarded(Runnable action){ try{ action.run(); }catch(Throwable error){ Log.err(error); System.exit(1); } }
    static void update() throws Exception{
        Method method = ui.getDeclaredMethod("updatePalette"); method.setAccessible(true); method.invoke(null);
        Core.scene.act(); Vars.ui.editor.validate();
    }
    static void capture(String name){
        for(Element element : Core.scene.root.getChildren()){
            if(element instanceof Dialog dialog && dialog.isShown()){dialog.clearActions();dialog.color.a=1f;}
        }
        Core.scene.act(); Core.scene.draw(); ScreenUtils.saveScreenshot(data.child(name));
    }
    static int count(Element element, String prefix){
        int result = element.name != null && element.name.startsWith(prefix) ? 1 : 0;
        if(element instanceof Group group) for(Element child : group.getChildren()) result += count(child, prefix);
        return result;
    }
    static BaseDialog picker(){
        Button all = Vars.ui.editor.find("cryon-editor-all-teams");
        check(all != null, "Missing all-teams button"); all.fireClick();
        BaseDialog dialog = null;
        var children = Core.scene.root.getChildren();
        for(int i=children.size-1;i>=0;i--){
            Element element = children.get(i);
            if("cryon-editor-team-dialog".equals(element.name)){dialog=(BaseDialog)element;break;}
        }
        check(dialog != null && dialog.isShown(), "Team picker did not open");
        return dialog;
    }
    static void visibleFloor(){
        CheckBox floor = Vars.ui.editor.find(element -> element instanceof CheckBox box
            && box.getText().toString().equals(Core.bundle.get("editor.showfloor")));
        if(floor == null){
            Log.info("Expected floor label: @", Core.bundle.get("editor.showfloor"));
            dump(Vars.ui.editor);
        }
        check(floor != null, "Native show-floor control removed");
        Vec2 point = floor.localToStageCoordinates(new Vec2());
        check(point.y >= 0 && point.y + floor.getHeight() <= Core.scene.getHeight(), "Show-floor control clipped: " + point);
        boolean before = Vars.editor.showFloor; floor.fireClick();
        check(Vars.editor.showFloor != before, "Show-floor toggle broken"); floor.fireClick();
        check(count(Vars.ui.editor, "cryon-editor-recent-team-") == 5, "Quick palette must have exactly five teams");
    }
    static void dump(Element element){
        if(element instanceof TextButton button) Log.info("UI text: @, type: @", button.getText(), button.getClass().getName());
        if(element instanceof Group group) for(Element child : group.getChildren()) dump(child);
    }
    static void menu() throws Exception{
        BaseDialog menu = Reflect.get(MapEditorDialog.class, Vars.ui.editor, "menu"); menu.show();
        Core.scene.act(); menu.validate();
        Table actions = menu.find("cryon-editor-menu-actions");
        check(actions != null && actions.getChildren().size == 2, "Missing or duplicate feature buttons");
        TextButton info = menu.find(element -> element instanceof TextButton button
            && button.getText().toString().equals(Core.bundle.get("editor.mapinfo")));
        check(info != null && actions.parent == info.parent, "Features are outside the native function grid");
        Vec2 a = actions.localToStageCoordinates(new Vec2()), b = info.localToStageCoordinates(new Vec2());
        check(a.y < b.y, "Features must be below map information");
        check(menu.find("cryon-underwater-editor") != null && menu.find("cryon-map-allies") != null,
            "Missing underwater/alliance feature");
        capture("editor-menu.png"); menu.hide(null);
    }
    static void test() throws Exception{
        check(!Vars.mods.locateMod("cryon").hasContentErrors(), "Packaged content errors");
        ui = Vars.mods.locateMod("cryon").main.getClass().getClassLoader().loadClass("cryon.Features.EditorUi");
        Vars.ui.editor.show(); Vars.ui.editor.clearActions();
        Vars.editor.beginEdit(40, 40); update(); capture("editor-initial.png"); visibleFloor();
        capture("editor-compact.png"); menu();
        BaseDialog dialog = picker();
        check(count(dialog, "cryon-editor-team-") - 2 == Team.all.length, "Picker omits teams");
        capture("editor-teams.png");
        TextField search = dialog.find("cryon-editor-team-search");
        search.setText("norma"); search.fire(new arc.scene.event.ChangeListener.ChangeEvent());
        check(dialog.find("cryon-editor-team-169") != null && dialog.find("cryon-editor-team-2") == null,
            "Team name search failed");
        Button norma = dialog.find("cryon-editor-team-169"); norma.fireClick(); dialog.hide(null); update();
        check(Vars.editor.drawTeam == Team.get(169), "Selecting Norma does not change drawTeam");
        check(Core.settings.getString("cryon-editor-recent-teams").startsWith("169"), "MRU does not start with selected team");
        for(int id : new int[]{171, 170, 2, 5, 1, 169}){
            dialog = picker(); Button choice = dialog.find("cryon-editor-team-" + id); choice.fireClick(); dialog.hide(null); update();
        }
        check(Core.settings.getString("cryon-editor-recent-teams").equals("169,1,5,2,170"), "MRU order/eviction wrong");
        Table palette = Vars.ui.editor.find("cryon-editor-team-palette");
        check(palette.getChildren().first().name.equals("cryon-editor-recent-team-169"), "MRU first slot incorrect");
        Vars.editor.drawTeam = Team.get(171); update();
        check(Core.settings.getString("cryon-editor-recent-teams").startsWith("171,169"), "Eyedropper/manual team change not recorded");
        Vars.ui.editor.build(); update(); visibleFloor(); menu();
        Core.settings.forceSave();
        Settings reloaded = new Settings(); reloaded.setDataDirectory(data); reloaded.load();
        check(reloaded.getString("cryon-editor-recent-teams").equals(Core.settings.getString("cryon-editor-recent-teams")),
            "MRU did not persist to disk");
        Core.graphics.setWindowSize(900, 600);
        Timer.schedule(() -> guarded(() -> {
            try{
                update(); visibleFloor(); capture("editor-compact-small.png");
                BaseDialog small = picker(); capture("editor-teams-small.png"); small.hide(null);
                Vars.ui.editor.hide(); Vars.ui.editor.show(); update(); visibleFloor(); menu();
                Log.info("EDITOR_UI_PASS: native menu grid, five recent teams, all 256 teams, search, selection, MRU ordering/eviction/persistence, rebuild/reopen and floor controls at 1100x760/900x600");
                Core.app.exit();
            }catch(Exception error){ throw new RuntimeException(error); }
        }), 1f);
    }
    public static void main(String[] args){
        data = Fi.get(System.getProperty("mindustry.data.dir"));
        Fi repo = Fi.get(System.getProperty("cryon.repo", "."));
        repo.child("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        repo.child("单位发射台library.jar").copyTo(data.child("mods/unitlaunch.jar"));
        Events.on(ClientLoadEvent.class, event -> Timer.schedule(() -> guarded(() -> {
            for(Element element : Core.scene.root.getChildren().copy())
                if(element instanceof Dialog dialog){ dialog.clearActions(); dialog.hide(null); }
            Core.graphics.setFullscreen(false); Core.graphics.setWindowSize(1100, 760);
            arc.backend.sdl.jni.SDL.SDL_RestoreWindow(((arc.backend.sdl.SdlApplication)Core.app).getWindow());
            Core.graphics.setWindowSize(1100, 760);
            Timer.schedule(() -> guarded(() -> {try{ test(); }catch(Exception error){ throw new RuntimeException(error); }}), 1f);
        }), 3f));
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
