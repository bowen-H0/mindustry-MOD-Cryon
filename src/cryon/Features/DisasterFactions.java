package cryon.Features;

import arc.graphics.Color;
import arc.Events;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import mindustry.Vars;
import mindustry.game.Teams;
import mindustry.game.Team;
import mindustry.game.EventType.*;

/** Coreless environmental factions: hostile, but independent of campaign AI cleanup. */
public final class DisasterFactions{
    public static final Team nano = create(172, "nano", "4ce68c");

    private static Team create(int id, String name, String color){
        Team old = Team.get(id);
        if(!old.name.equals("team#" + id)) throw new IllegalStateException("Disaster faction slot already occupied: " + id);
        return new DisasterTeam(id, name, Color.valueOf(color));
    }
    public static boolean contains(Team team){ return team instanceof DisasterTeam; }
    private static boolean installed;
    public static void init(){
        if(installed) return;
        installed = true;
        NormaTeam.addEditorTeam(nano);
        Events.on(WorldLoadBeginEvent.class, e -> nano.data());
        Events.on(WorldLoadEvent.class, e -> nano.data());
        Events.on(ResetEvent.class, e -> nano.data());
    }

    private static Teams cachedTeams;
    private static DisasterData cachedData;
    private static Teams.TeamData data(Team team){
        if(cachedTeams == Vars.state.teams && cachedData != null) return cachedData;
        try{
            Field mapField = Teams.class.getDeclaredField("map"); mapField.setAccessible(true);
            Teams.TeamData[] map = (Teams.TeamData[])mapField.get(Vars.state.teams);
            Teams.TeamData old = Vars.state.teams.get(team);
            DisasterData replacement = new DisasterData(team);
            // Preserve native indexes if another subsystem accessed this slot before us.
            for(Field f : Teams.TeamData.class.getFields()){
                if(Modifier.isStatic(f.getModifiers()) || f.getName().equals("team")) continue;
                f.setAccessible(true); f.set(replacement, f.get(old));
            }
            map[team.id] = replacement;
            int index = Vars.state.teams.active.indexOf(old, true);
            if(index >= 0) Vars.state.teams.active.set(index, replacement);
            index = Vars.state.teams.present.indexOf(old, true);
            if(index >= 0) Vars.state.teams.present.set(index, replacement);
            cachedTeams = Vars.state.teams; cachedData = replacement;
            return replacement;
        }catch(ReflectiveOperationException error){ throw new IllegalStateException("Cannot install disaster faction rules", error); }
    }

    /** Excluded from native victory counting and capture/core-loss cleanup, even in editor maps. */
    public static class DisasterData extends Teams.TeamData{
        public DisasterData(Team team){ super(team); }
        @Override public boolean isAlive(){ return false; }
        @Override public void destroyToDerelict(){}
        @Override public void timeDestroy(float x, float y, float range){}
    }

    public static class DisasterTeam extends Team{
        protected DisasterTeam(int id, String name, Color color){ super(id, name, color); }
        @Override public Teams.TeamData data(){ return DisasterFactions.data(this); }
        // UnitComp kills isOnlyAI units on captured sectors. Disaster units must survive.
        @Override public boolean isOnlyAI(){ return false; }
        @Override public boolean isAI(){ return false; }
        @Override public boolean needsFlowField(){ return false; }
    }
}
