package cryon.Features;

import arc.*;
import arc.struct.*;
import arc.util.*;
import arc.util.pooling.*;
import java.lang.reflect.Field;
import mindustry.Vars;
import mindustry.ai.BlockIndexer;
import mindustry.ai.BaseBuilderAI;
import mindustry.ai.RtsAI;
import mindustry.ai.types.CommandAI;
import mindustry.entities.units.AIController;
import mindustry.editor.MapEditorDialog;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.Turret.TurretBuild;

/** Map-scoped diplomacy. Ownership, command permission, inventories and native AI stay separate. */
public final class MapAlliances{
    public static final String tag = "cryon-allied-teams";
    private static final String buttonName = "cryon-map-allies";
    private static final boolean[] members = new boolean[256];
    private static final Seq<Scope> free = new Seq<>();
    private static final Field indexTeams = field(BlockIndexer.class, "activeTeams");
    private static final Field[] aiTargets = {field(AIController.class, "target"), field(AIController.class, "bomberTarget")};
    private static final Field aiFallback = field(AIController.class, "fallback"), commandController = field(CommandAI.class, "commandController");
    private static Rules cachedRules;
    private static String cachedTag;
    private static boolean installed, hasAllies, adjustedCores;
    private static Scope current;
    private static Teams checkedTeams;
    private static final Team[][] checkedEnemies = new Team[256][];

    private MapAlliances(){}
    public interface Adapted{}

    public static void install(){
        if(installed) return;
        installed = true;
        AllianceAdapters.install();
        Pool<Bullet> bullets = new Pool<Bullet>(){
            @Override protected Bullet newObject(){ return new AllianceBullet(); }
        };
        Pools.set(Bullet.class, bullets);
        Pools.set((Class)AllianceBullet.class, bullets);
        Events.on(WorldLoadEvent.class, event -> refresh());
        Events.on(SaveLoadEvent.class, event -> refresh());
        Events.on(ResetEvent.class, event -> {
            cachedRules = null;
            checkedTeams = null;
            java.util.Arrays.fill(checkedEnemies, null);
            adjustedCores = false;
        });
        Events.run(Trigger.beforeGameUpdate, MapAlliances::refresh);
        Events.run(Trigger.afterGameUpdate, MapAlliances::checkVictory);
        if(!Vars.headless) Events.on(ClientLoadEvent.class, event -> {
            Vars.ui.editor.shown(MapAlliances::addEditorButton);
            Events.on(ResizeEvent.class, resize -> Core.app.post(() -> {
                if(Vars.ui.editor.isShown()) addEditorButton();
            }));
        });
    }

    public static boolean enabled(){
        if(Vars.state == null || Vars.state.rules == null) return false;
        read(Vars.state.rules);
        return hasAllies;
    }

    private static void read(Rules rules){
        String value = rules.tags.get(tag, "");
        if(rules == cachedRules && value.equals(cachedTag)) return;
        cachedRules = rules; cachedTag = value; hasAllies = false;
        java.util.Arrays.fill(members, false);
        for(String part : value.split(",")){
            int id = Strings.parseInt(part.trim(), -1);
            if(id > 0 && id < members.length){ members[id] = true; hasAllies = true; }
        }
    }

    public static boolean selected(Rules rules, Team team){
        read(rules);
        return team != Team.derelict && team != rules.defaultTeam && members[team.id];
    }

    public static void setAlly(Rules rules, Team team, boolean ally){
        if(team == Team.derelict || team == rules.defaultTeam) return;
        read(rules); members[team.id] = ally;
        StringBuilder value = new StringBuilder();
        for(int id = 1; id < members.length; id++) if(members[id]){
            if(value.length() > 0) value.append(',');
            value.append(id);
        }
        if(value.length() == 0) rules.tags.remove(tag); else rules.tags.put(tag, value.toString());
        if(ally){ rules.teams.get(team).rtsAi = true; rules.teams.get(team).buildAi = true; }
        cachedRules = null;
        if(Vars.state != null && rules == Vars.state.rules) refresh();
    }

    public static boolean allied(Team first, Team second){
        if(first == null || second == null) return false;
        if(first == second) return true;
        if(first == Team.derelict || second == Team.derelict || !enabled()) return false;
        Team player = Vars.state.rules.defaultTeam;
        return (first == player || members[first.id]) && (second == player || members[second.id]);
    }

    private static boolean friendlyDifferent(Team first, Team second){ return first != second && allied(first, second); }
    public static boolean mayTarget(Teamc target, Team observer){ return !friendlyDifferent(target.team(), observer); }
    public static boolean mayDamageFrom(Teamc target, Team source){ return !friendlyDifferent(target.team(), source); }
    public static boolean mayDamage(Teamc target){ return current == null || mayDamageFrom(target, current.actor); }
    public static boolean mayCollide(Teamc actor, Object other){
        return !(other instanceof Teamc) || (!(actor instanceof Bullet) && !(other instanceof Bullet))
            || !friendlyDifferent(actor.team(), ((Teamc)other).team());
    }

    public static void refresh(){
        if(Vars.state == null || Vars.state.teams == null) return;
        boolean enabled = enabled();
        if(!enabled && !adjustedCores) return;
        if(checkedTeams != Vars.state.teams){
            checkedTeams = Vars.state.teams;
            java.util.Arrays.fill(checkedEnemies, null);
        }
        Scope root = current;
        while(root != null && root.previous != null) root = root.previous;
        Seq<Teams.TeamData> active = root == null ? Vars.state.teams.active : root.active;
        for(var data : active){
            if(enabled){
                if(data.team.rules().rtsAi && !(data.rtsAi instanceof AllianceRtsAI)) data.rtsAi = new AllianceRtsAI(data);
                if(data.team.rules().buildAi && !(data.buildAi instanceof AllianceBuilderAI)) data.buildAi = new AllianceBuilderAI(data);
            }
            int expected = 0;
            boolean changed = false;
            for(var other : active) if(data.team != other.team && !friendlyDifferent(data.team, other.team)){
                if(expected >= data.coreEnemies.length || data.coreEnemies[expected] != other.team) changed = true;
                expected++;
            }
            if(!changed && expected == data.coreEnemies.length){
                checkedEnemies[data.team.id] = data.coreEnemies;
                continue;
            }
            Team[] enemies = new Team[expected];
            int index = 0;
            for(var other : active) if(data.team != other.team && !friendlyDifferent(data.team, other.team)) enemies[index++] = other.team;
            data.coreEnemies = enemies;
            checkedEnemies[data.team.id] = enemies;
        }
        adjustedCores = enabled;
    }

    /** Native searches enumerate these lists. A short, nestable scope omits only foreign allies. */
    public static Scope enter(Teamc entity){
        Scope scope = enterTeam(entity.team());
        if(scope != null) clearTarget(entity);
        return scope;
    }

    public static Scope enterTeam(Team team){
        if(!enabled()) return null;
        // Non-allied factions already treat every other faction as hostile. A root update
        // needs no alternate indexes; nested attacks must still restore their own view.
        if(current == null && team != Vars.state.rules.defaultTeam && !members[team.id]) return null;
        Teams.TeamData data = Vars.state.teams.get(team);
        // Native core changes replace coreEnemies arrays. Refresh only when this cache is
        // invalidated, instead of rebuilding every faction's relationships for every entity.
        if(checkedTeams != Vars.state.teams || checkedEnemies[team.id] != data.coreEnemies){
            refresh();
            checkedEnemies[team.id] = data.coreEnemies;
        }
        Scope scope = free.isEmpty() ? new Scope() : free.pop();
        scope.previous = current; scope.actor = team;
        scope.teams = Vars.state.teams; scope.indexer = Vars.indexer;
        scope.present = scope.teams.present; scope.active = scope.teams.active;
        try{ scope.indexed = (Seq<Team>)indexTeams.get(scope.indexer); }
        catch(IllegalAccessException error){ throw new RuntimeException(error); }
        Scope root = current;
        while(root != null && root.previous != null) root = root.previous;
        Seq<Teams.TeamData> sourcePresent = root == null ? scope.present : root.present;
        Seq<Teams.TeamData> sourceActive = root == null ? scope.active : root.active;
        Seq<Team> sourceIndexed = root == null ? scope.indexed : root.indexed;
        scope.fullPresent = sourcePresent; scope.fullActive = sourceActive; scope.fullIndexed = sourceIndexed;
        scope.filteredPresent.clear(); scope.filteredActive.clear(); scope.filteredIndexed.clear();
        for(var present : sourcePresent) if(!friendlyDifferent(scope.actor, present.team)) scope.filteredPresent.add(present);
        // BlockIndexer.getEnemy falls back to every team when present is empty.
        if(scope.filteredPresent.isEmpty()) scope.filteredPresent.add(scope.teams.get(scope.actor));
        for(var active : sourceActive) if(!friendlyDifferent(scope.actor, active.team)) scope.filteredActive.add(active);
        for(Team candidate : sourceIndexed) if(!friendlyDifferent(scope.actor, candidate)) scope.filteredIndexed.add(candidate);
        current = scope;
        scope.teams.present = scope.filteredPresent; scope.teams.active = scope.filteredActive;
        try{ indexTeams.set(scope.indexer, scope.filteredIndexed); }
        catch(IllegalAccessException error){ exit(scope); throw new RuntimeException(error); }
        return scope;
    }

    public static void exit(Scope scope){
        if(scope == null) return;
        // Preserve teams activated by construction or spawning during an update.
        // Nested death/explosion callbacks can use a different team. Previously hidden
        // teams must stay hidden when returning to the outer attacker's search scope.
        for(var data : scope.filteredPresent) if(!scope.fullPresent.contains(data)){
            scope.fullPresent.add(data);
            if(scope.previous != null && !friendlyDifferent(scope.previous.actor, data.team) && !scope.present.contains(data)) scope.present.add(data);
        }
        for(var data : scope.filteredActive) if(!scope.fullActive.contains(data)){
            scope.fullActive.add(data);
            if(scope.previous != null && !friendlyDifferent(scope.previous.actor, data.team) && !scope.active.contains(data)) scope.active.add(data);
        }
        for(Team team : scope.filteredIndexed) if(!scope.fullIndexed.contains(team)){
            scope.fullIndexed.add(team);
            if(scope.previous != null && !friendlyDifferent(scope.previous.actor, team) && !scope.indexed.contains(team)) scope.indexed.add(team);
        }
        scope.teams.present = scope.present; scope.teams.active = scope.active;
        try{ indexTeams.set(scope.indexer, scope.indexed); }
        catch(IllegalAccessException error){ throw new RuntimeException(error); }
        current = scope.previous;
        scope.previous = null; scope.teams = null; scope.indexer = null;
        scope.present = null; scope.active = null; scope.indexed = null;
        scope.fullPresent = null; scope.fullActive = null; scope.fullIndexed = null;
        scope.filteredPresent.clear(); scope.filteredActive.clear(); scope.filteredIndexed.clear();
        free.add(scope);
    }

    private static void clearTarget(Teamc entity){
        if(entity instanceof Unit){
            Unit unit = (Unit)entity;
            for(var mount : unit.mounts) if(mount.target != null && friendlyDifferent(unit.team, mount.target.team())){
                mount.target = null; mount.shoot = false; mount.retarget = 0;
            }
            if(unit.controller() instanceof AIController) clearAI((AIController)unit.controller(), unit.team, 0);
        }
        if(entity instanceof TurretBuild){
            TurretBuild turret = (TurretBuild)entity;
            if(turret.target instanceof Teamc && friendlyDifferent(turret.team, ((Teamc)turret.target).team())) turret.target = null;
        }
    }

    private static void clearAI(AIController ai, Team team, int depth){
        if(depth > 3 || ai == null) return;
        try{
            for(Field field : aiTargets){
                Teamc target = (Teamc)field.get(ai);
                if(target != null && friendlyDifferent(team, target.team())) field.set(ai, null);
            }
            if(ai instanceof CommandAI){
                CommandAI command = (CommandAI)ai;
                if(command.attackTarget != null && friendlyDifferent(team, command.attackTarget.team())){
                    command.attackTarget = null; command.targetPos = null;
                }
                command.commandQueue.removeAll(position -> position instanceof Teamc && friendlyDifferent(team, ((Teamc)position).team()));
                clearAI((AIController)commandController.get(command), team, depth + 1);
            }
            clearAI((AIController)aiFallback.get(ai), team, depth + 1);
        }catch(IllegalAccessException error){ throw new RuntimeException(error); }
    }

    private static Field field(Class<?> type, String name){
        try{ Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
        catch(ReflectiveOperationException error){ throw new RuntimeException(error); }
    }

    private static void checkVictory(){
        refresh();
        if(enabled() && allied(Vars.state.rules.defaultTeam, Vars.state.rules.waveTeam)) Vars.state.enemies = 0;
        if(!enabled() || Vars.state.isEditor() || !Vars.state.isGame() || Vars.state.isCampaign()
                || Vars.net.client() || !Vars.state.rules.attackMode || Vars.state.gameOver
                || Vars.state.rules.defaultTeam.core() == null) return;
        Team player = Vars.state.rules.defaultTeam;
        for(var data : Vars.state.teams.getActive()){
            if(data.team != Team.derelict && data.isAlive() && !allied(player, data.team)) return;
        }
        Vars.state.gameOver = true;
        Events.fire(new GameOverEvent(player));
    }

    private static void addEditorButton(){
        var editor = Vars.ui.editor;
        BaseDialog menu = Reflect.get(MapEditorDialog.class, editor, "menu");
        if(menu.find(buttonName) != null) return;
        menu.cont.row();
        menu.cont.button("@cryon.allies.title", Icon.players, () -> {
            menu.hide();
            showEditor();
        }).size(370f, 60f).padTop(3f).get().name = buttonName;
        menu.cont.row();
    }

    private static void showEditor(){
        Rules rules = Vars.state.rules;
        BaseDialog dialog = new BaseDialog("@cryon.allies.title");
        dialog.cont.add(Core.bundle.get("cryon.allies.description")).width(660f).wrap().pad(12f).row();
        Seq<Team> teams = new Seq<>();
        for(Team team : Team.baseTeams) if(team != Team.derelict && team != rules.defaultTeam) teams.addUnique(team);
        for(var tile : Vars.editor.tiles()) if(tile.team() != Team.derelict && tile.team() != rules.defaultTeam) teams.addUnique(tile.team());
        for(Team team : Team.all) if(selected(rules, team)) teams.addUnique(team);
        teams.sort(team -> team.id);
        dialog.cont.pane(list -> {
            for(Team team : teams){
                list.table(row -> {
                    row.check(team.localized() + (team == rules.waveTeam ? " " + Core.bundle.get("cryon.allies.wave-team") : ""), selected(rules, team), value -> {
                        setAlly(rules, team, value); Vars.ui.editor.resetSaved();
                    }).width(250f).left();
                    row.check("@cryon.allies.rts", rules.teams.get(team).rtsAi, value -> {
                        rules.teams.get(team).rtsAi = value; Vars.ui.editor.resetSaved();
                    }).update(box -> box.setChecked(rules.teams.get(team).rtsAi)).disabled(box -> !selected(rules, team)).padRight(16f);
                    row.check("@cryon.allies.build", rules.teams.get(team).buildAi, value -> {
                        rules.teams.get(team).buildAi = value; Vars.ui.editor.resetSaved();
                    }).update(box -> box.setChecked(rules.teams.get(team).buildAi)).disabled(box -> !selected(rules, team));
                }).growX().pad(8f).row();
            }
        }).maxHeight(520f).growX();
        dialog.addCloseButton(); dialog.show();
    }

    public static final class Scope{
        Team actor;
        Scope previous;
        Teams teams;
        BlockIndexer indexer;
        Seq<Teams.TeamData> present, active;
        Seq<Teams.TeamData> fullPresent, fullActive;
        Seq<Team> indexed;
        Seq<Team> fullIndexed;
        final Seq<Teams.TeamData> filteredPresent = new Seq<>(Teams.TeamData.class), filteredActive = new Seq<>(Teams.TeamData.class);
        final Seq<Team> filteredIndexed = new Seq<>(Team.class);
    }

    private static class AllianceRtsAI extends RtsAI{
        final Team team;
        AllianceRtsAI(Teams.TeamData data){ super(data); team = data.team; }
        @Override public void update(){ Scope scope = enterTeam(team); try{ super.update(); }finally{ exit(scope); } }
    }

    private static class AllianceBuilderAI extends BaseBuilderAI{
        final Team team;
        AllianceBuilderAI(Teams.TeamData data){ super(data); team = data.team; }
        @Override public void update(){ Scope scope = enterTeam(team); try{ super.update(); }finally{ exit(scope); } }
    }

    /** Keep native bullet pooling and callbacks, with the attacker's diplomacy active for damage. */
    public static class AllianceBullet extends Bullet{
        @Override public boolean collides(Hitboxc other){ return mayCollide(this, other) && super.collides(other); }
        @Override public void add(){ Scope scope = enter(this); try{ super.add(); }finally{ exit(scope); } }
        @Override public void update(){ Scope scope = enter(this); try{ super.update(); }finally{ exit(scope); } }
        @Override public void collision(Hitboxc other, float x, float y){
            if(!mayCollide(this, other)) return;
            Scope scope = enter(this); try{ super.collision(other, x, y); }finally{ exit(scope); }
        }
        @Override public void remove(){ Scope scope = enter(this); try{ super.remove(); }finally{ exit(scope); } }
    }
}
