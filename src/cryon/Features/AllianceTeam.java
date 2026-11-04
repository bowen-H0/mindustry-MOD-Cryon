package cryon.Features;

import arc.graphics.Color;
import mindustry.game.Team;

/** Independent Alliance faction. Its ID is fixed for maps, saves and multiplayer. */
public final class AllianceTeam{
    public static final Team team = Team.get(170);
    private static boolean installed;

    private AllianceTeam(){}

    public static void loadIcon(){
        TeamEmblems.loadIcon(team);
    }

    public static void init(){
        if(installed) return;
        if(!team.name.equals("team#" + team.id) && !team.name.equals("alliance")){
            throw new IllegalStateException("Alliance team ID " + team.id + " is already used by " + team.name);
        }
        team.name = "alliance";
        team.color.set(Color.valueOf("3271e3"));
        team.setPalette(team.color, team.color.cpy().mul(0.75f).a(1f), team.color.cpy().mul(0.5f).a(1f));
        NormaTeam.addEditorTeam(team);
        installed = true;
    }
}
