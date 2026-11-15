package cryon.Features;

import arc.Core;
import arc.Events;
import arc.scene.Element;
import arc.scene.style.Drawable;
import arc.scene.ui.ImageButton;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Reflect;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.editor.MapEditorDialog;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.Team;
import mindustry.gen.Tex;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

/** Compact editor controls without changing the game's global faction list. */
public final class EditorUi{
    private static final String actionsName = "cryon-editor-menu-actions";
    private static final String paletteName = "cryon-editor-team-palette";
    private static final String recentKey = "cryon-editor-recent-teams";
    private static final int recentLimit = 5;
    private static Team lastTeam;
    private static boolean installed;

    private EditorUi(){}

    public static void install(){
        if(Vars.headless || installed) return;
        installed = true;
        Events.on(ClientLoadEvent.class, event -> {
            Vars.ui.editor.shown(EditorUi::updatePalette);
            // The native editor rebuilds its controls after resizing and playtesting.
            Events.run(Trigger.update, () -> {
                if(Vars.ui.editor.isShown()) updatePalette();
            });
        });
    }

    public static Cell<TextButton> addMenuButton(MapEditorDialog editor, String name, String text, Drawable icon, Runnable action){
        BaseDialog menu = Reflect.get(MapEditorDialog.class, editor, "menu");
        if(menu.find(name) != null) return null;
        Table actions = menu.find(actionsName);
        if(actions == null){
            TextButton info = menu.cont.find(element -> element instanceof TextButton
                && ((TextButton)element).getText().toString().equals(Core.bundle.get("editor.mapinfo")));
            if(info == null || !(info.parent instanceof Table)) return null;
            Table grid = (Table)info.parent;
            Seq<SavedCell> cells = snapshot(grid);
            actions = new Table();
            actions.name = actionsName;
            actions.defaults().size(180f, 60f).padBottom(5f).padLeft(5f).padRight(5f);
            grid.clearChildren();
            for(SavedCell cell : cells){
                cell.addTo(grid);
                if(cell.element == info){
                    if(!cell.endRow) grid.row();
                    grid.add(actions).colspan(2).size(380f, 65f).pad(0f);
                    grid.row();
                }
            }
        }
        return actions.button(text, icon, action).name(name);
    }

    private static Team nativeTeam(Element element){
        if(!(element instanceof ImageButton) || element.name != null) return null;
        ImageButton button = (ImageButton)element;
        for(Team team : Team.baseTeams){
            if(button.getStyle().imageUpColor == team.color) return team;
        }
        return null;
    }

    private static void updatePalette(){
        MapEditorDialog editor = Vars.ui.editor;
        Table palette = editor.find(paletteName);
        if(palette == null){
            Element first = editor.find(element -> nativeTeam(element) != null);
            if(first == null || !(first.parent instanceof Table)) return;
            Table tools = (Table)first.parent;
            Seq<SavedCell> cells = snapshot(tools);
            tools.clearChildren();
            for(SavedCell cell : cells){
                if(nativeTeam(cell.element) == null) cell.addTo(tools);
            }
            palette = new Table();
            palette.name = paletteName;
            float size = Vars.mobile ? 50f : 58f;
            tools.add(palette).colspan(3).size(size * 3f, size * 2f).pad(0f);
            remember(Vars.editor.drawTeam);
            rebuildPalette(palette);
        }else if(lastTeam != Vars.editor.drawTeam){
            // Includes the native eyedropper and other ways of changing drawTeam.
            remember(Vars.editor.drawTeam);
            rebuildPalette(palette);
        }
        lastTeam = Vars.editor.drawTeam;
    }

    private static Seq<Team> storedRecent(){
        Seq<Team> teams = new Seq<>();
        for(String value : Core.settings.getString(recentKey, "").split(",")){
            int id = Strings.parseInt(value, -1);
            if(id >= 0 && id < Team.all.length && teams.size < recentLimit) teams.addUnique(Team.get(id));
        }
        return teams;
    }

    private static void remember(Team team){
        Seq<Team> teams = storedRecent();
        teams.remove(team, true);
        teams.insert(0, team);
        teams.truncate(recentLimit);
        StringBuilder ids = new StringBuilder();
        for(Team recent : teams){
            if(ids.length() > 0) ids.append(',');
            ids.append(recent.id);
        }
        Core.settings.put(recentKey, ids.toString());
    }

    private static void choose(Team team){
        Vars.editor.drawTeam = team;
        remember(team);
        lastTeam = null;
    }

    private static void rebuildPalette(Table palette){
        palette.clearChildren();
        float size = Vars.mobile ? 50f : 58f;
        palette.defaults().size(size);
        Seq<Team> teams = storedRecent();
        for(Team team : Team.baseTeams) if(teams.size < recentLimit) teams.addUnique(team);
        for(int i = 0; i < teams.size; i++){
            Team team = teams.get(i);
            ImageButton button = new ImageButton(Tex.whiteui, Styles.clearNoneTogglei);
            button.margin(4f);
            button.getImageCell().grow();
            button.getStyle().imageUpColor = team.color;
            button.clicked(() -> choose(team));
            button.update(() -> button.setChecked(Vars.editor.drawTeam == team));
            palette.add(button).name("cryon-editor-recent-team-" + team.id)
                .tooltip(team.localized() + " (#" + team.id + ")");
            if(i % 3 == 2) palette.row();
        }
        TextButton all = palette.button("@cryon.editor.all-teams", Styles.flatt, EditorUi::showTeams)
            .name("cryon-editor-all-teams").tooltip("@cryon.editor.all-teams").get();
        all.getLabel().setFontScale(0.65f);
        all.getLabelCell().minWidth(0f);
    }

    private static void showTeams(){
        BaseDialog dialog = new BaseDialog("@cryon.editor.all-teams");
        dialog.name = "cryon-editor-team-dialog";
        float width = Math.min(720f, Core.graphics.getWidth() / Scl.scl() - 60f);
        int columns = Math.max(1, (int)(width / 220f));
        Table list = new Table();
        dialog.cont.field("", text -> populateTeams(list, dialog, text, width, columns))
            .growX().height(48f).with(field -> field.setMessageText("@cryon.editor.search-teams"))
            .name("cryon-editor-team-search");
        dialog.cont.row();
        dialog.cont.pane(list).width(width)
            .height(Math.min(420f, Math.max(120f, Core.graphics.getHeight() / Scl.scl() - 230f)));
        populateTeams(list, dialog, "", width, columns);
        dialog.addCloseButton();
        dialog.show();
    }

    private static void populateTeams(Table list, BaseDialog dialog, String text, float width, int columns){
        list.clearChildren();
        String query = text.trim().toLowerCase(java.util.Locale.ROOT);
        int count = 0;
        for(Team team : Team.all){
            String label = team.localized() + " (#" + team.id + ")";
            if(!query.isEmpty() && !(label + " " + team.name).toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
            TextButton button = list.button(label, Styles.flatt, () -> {
                choose(team);
                dialog.hide();
            }).size(width / columns, 52f).name("cryon-editor-team-" + team.id).get();
            button.getLabel().setColor(team.color);
            button.update(() -> button.setChecked(Vars.editor.drawTeam == team));
            if(++count % columns == 0) list.row();
        }
        if(count == 0) list.add("@none").pad(16f);
    }

    private static Seq<SavedCell> snapshot(Table table){
        Seq<SavedCell> result = new Seq<>();
        for(Cell<?> cell : table.getCells()) result.add(new SavedCell(cell));
        return result;
    }

    private static final class SavedCell{
        final Element element;
        final Cell<?> constraints;
        final boolean endRow;
        SavedCell(Cell<?> cell){
            element = cell.get();
            constraints = new Cell<>().set(cell);
            endRow = cell.isEndRow();
        }
        void addTo(Table table){
            table.add(element).set(constraints);
            if(endRow) table.row();
        }
    }
}
