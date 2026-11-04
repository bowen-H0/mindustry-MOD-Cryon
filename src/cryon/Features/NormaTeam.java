package cryon.Features;

import arc.graphics.Color;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.Team;

/** Independent faction with a stable save/network ID; vanilla teams stay intact. */
public final class NormaTeam{
    // Keep this ID fixed: maps, saves and multiplayer packets serialize it.
    // Slot 8 is used by Vanilla Expansion's Lacuna faction.
    public static final Team team = Team.get(169);
    private static boolean installed;

    private NormaTeam(){}

    public static void loadIcon(){
        TeamEmblems.loadIcon(team);
    }

    public static void init(){
        if(installed) return;
        if(!team.name.equals("team#" + team.id) && !team.name.equals("norma")){
            throw new IllegalStateException("Norma team ID " + team.id + " is already used by " + team.name);
        }
        team.name = "norma";
        team.color.set(Color.valueOf("da54ff"));
        team.setPalette(team.color);
        addEditorTeam(team);
        arc.Events.on(mindustry.game.EventType.WorldLoadEvent.class, event -> migrateLegacyCampaign());
        installed = true;
    }

    static void addEditorTeam(Team team){
        for(Team candidate : Team.baseTeams) if(candidate == team) return;
        Team[] teams = java.util.Arrays.copyOf(Team.baseTeams, Team.baseTeams.length + 1);
        teams[teams.length - 1] = team;
        try{
            java.lang.reflect.Field field = Team.class.getDeclaredField("baseTeams");
            field.setAccessible(true);
            if(Vars.android){
                field.set(null, teams);
            }else{
                // Desktop Java does not permit Field.set on a static final array.
                Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                java.lang.reflect.Field singleton = unsafeClass.getDeclaredField("theUnsafe");
                singleton.setAccessible(true);
                Object unsafe = singleton.get(null);
                Object base = unsafeClass.getMethod("staticFieldBase", java.lang.reflect.Field.class).invoke(unsafe, field);
                long offset = ((Number)unsafeClass.getMethod("staticFieldOffset", java.lang.reflect.Field.class).invoke(unsafe, field)).longValue();
                unsafeClass.getMethod("putObjectVolatile", Object.class, long.class, Object.class).invoke(unsafe, base, offset, teams);
            }
        }catch(ReflectiveOperationException error){
            throw new IllegalStateException("Could not add " + team.name + " to the editor team list", error);
        }
    }

    /** Only old Porphyra campaign saves used green as Norma; other maps keep green. */
    public static void migrateLegacyCampaign(){
        if(Vars.state == null || Vars.state.rules.sector == null
                || !Vars.state.rules.sector.planet.name.equals("cryon-porphyra")
                || Vars.state.rules.waveTeam != Team.green
                || Vars.state.rules.defaultTeam == Team.green) return;
        mindustry.game.Rules rules = Vars.state.rules;
        mindustry.io.JsonIO.copy(rules.teams.get(Team.green), rules.teams.get(team));
        rules.waveTeam = team;
        for(mindustry.game.SpawnGroup group : rules.spawns){
            if(group.team == Team.green) group.team = team;
        }
        // Iterate tiles rather than the live building group: changeTeam updates indexes.
        for(mindustry.world.Tile tile : Vars.world.tiles){
            if(tile.isCenter() && tile.build != null && tile.team() == Team.green){
                tile.build.changeTeam(team);
            }
        }
        for(mindustry.gen.Unit unit : mindustry.gen.Groups.unit.copy()){
            if(unit.team == Team.green) unit.team(team);
        }
        Vars.state.teams.updateTeamStats();
        Log.info("[Cryon] Migrated legacy Porphyra Norma faction to team @", team.id);
    }
}
