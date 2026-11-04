package cryon.Features;

import arc.graphics.Color;
import mindustry.game.Team;

/** Independent Pioneer faction. Keep its ID fixed for maps, saves and multiplayer. */
public final class PioneerTeam{
    public static final Team team = Team.get(171);
    private static boolean installed;

    private PioneerTeam(){}

    public static void loadIcon(){
        TeamEmblems.loadIcon(team);
    }

    public static void init(){
        if(installed) return;
        if(!team.name.equals("team#" + team.id) && !team.name.equals("pioneer")){
            throw new IllegalStateException("Pioneer team ID " + team.id + " is already used by " + team.name);
        }
        team.name = "pioneer";
        team.color.set(Color.valueOf("f28c28"));
        team.setPalette(team.color, team.color.cpy().mul(0.75f).a(1f), team.color.cpy().mul(0.5f).a(1f));
        NormaTeam.addEditorTeam(team);
        installed = true;
    }
}
