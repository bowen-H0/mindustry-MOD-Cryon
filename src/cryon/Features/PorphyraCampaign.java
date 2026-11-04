package cryon.Features;

import arc.Core;
import arc.Events;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType.SectorCaptureEvent;
import mindustry.type.Planet;
import mindustry.game.Rules;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.io.JsonIO;

/** Records the seizure of ring authority and shuts down planetary defense waves. */
public final class PorphyraCampaign{
    private static final String factionVersion = "cryon-porphyra-alliance";
    private static final String networkDisabled = "cryon-porphyra-network-disabled";

    private PorphyraCampaign(){}

    public static void install(){
        Planet planet = Vars.content.planet("cryon-porphyra");
        if(planet == null) return;
        if(Core.settings.getBool(networkDisabled, false)) disableNetwork(planet);
        Events.on(mindustry.game.EventType.WorldLoadEvent.class, event -> migrateFactions());
        Events.on(SectorCaptureEvent.class, event -> {
            if(event.sector == null || event.sector.planet != planet || event.sector.id != 9) return;
            Core.settings.put(networkDisabled, true);
            Core.settings.forceSave();
            disableNetwork(planet);
            Log.info("[Cryon] Porphyra ring authority seized; planetary defense network disabled");
            if(event.initialCapture && !Vars.headless && Vars.ui != null){
                Core.app.post(() -> Vars.ui.showInfo(Core.bundle.get("cryon.porphyra.victory")));
            }
        });
    }

    public static void setFactions(Rules rules){
        rules.defaultTeam = AllianceTeam.team;
        rules.waveTeam = NormaTeam.team;
    }

    /** Upgrade only Porphyra campaign worlds; other planets and custom maps keep their teams. */
    public static void migrateFactions(){
        Rules rules = Vars.state.rules;
        if(rules.sector == null || !rules.sector.planet.name.equals("cryon-porphyra")) return;
        if(!rules.tags.getBool(factionVersion)){
            if(rules.defaultTeam == Team.sharded || !Team.sharded.cores().isEmpty()){
                JsonIO.copy(rules.teams.get(Team.sharded), rules.teams.get(AllianceTeam.team));
            }
            if(rules.waveTeam == Team.green || rules.waveTeam == Team.malis){
                JsonIO.copy(rules.teams.get(rules.waveTeam), rules.teams.get(NormaTeam.team));
            }
            for(var tile : Vars.world.tiles){
                if(tile.isCenter() && tile.build != null){
                    Team target = migratedTeam(tile.team());
                    if(target != tile.team()) tile.build.changeTeam(target);
                }
            }
            for(var unit : Groups.unit.copy()) unit.team(migratedTeam(unit.team));
            for(var player : Groups.player.copy()) player.team(migratedTeam(player.team()));
            if(Vars.player != null) Vars.player.team(migratedTeam(Vars.player.team()));
            for(var spawn : rules.spawns){
                if(spawn.team != null) spawn.team = migratedTeam(spawn.team);
            }
            rules.tags.put(factionVersion, "true");
            Vars.state.teams.updateTeamStats();
        }
        setFactions(rules);
    }

    private static Team migratedTeam(Team team){
        if(team == Team.sharded) return AllianceTeam.team;
        if(team == Team.green || team == Team.malis) return NormaTeam.team;
        return team;
    }

    private static void disableNetwork(Planet planet){
        planet.allowWaves = false;
        planet.allowSectorInvasion = false;
    }
}
