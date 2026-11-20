import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.*;
import cryon.Content.*;
import mindustry.game.Rules;
import mindustry.*;
import mindustry.content.*;
import mindustry.entities.bullet.PointLaserBulletType;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.net.Net;
import mindustry.world.blocks.defense.turrets.ContinuousTurret;

/** Oil/power gating and native Lustre-style point-only beam damage. */
public class AravisResourceRulesCheck{
    static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }
    static class Probe extends ContinuousTurret.ContinuousTurretBuild{
        Probe(ContinuousTurret turret){ turret.super(); }
        @Override public boolean isShooting(){ return true; }
        @Override public boolean shouldConsume(){ return true; }
    }
    public static void main(String[] args){
        Core.app = new MockApplication(); Core.files = new MockFiles();
        Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Core.settings = new Settings(); Core.settings.setDataDirectory(new Fi("/tmp/emberglow-defense-check"));
        Core.bundle = I18NBundle.createEmptyBundle();
        Vars.headless = true; Vars.loadLocales = false; Vars.net = new Net(null);
        Vars.platform = new mindustry.core.Platform(){};
        Vars.init(); Vars.content.createBaseContent(); CryonItems.load(); EmberglowDefense.load(); Vars.content.init();
        Vars.world.loadGenerator(80,80, tiles -> {
            for(int x=0;x<80;x++) for(int y=0;y<80;y++)
                tiles.set(x,y,new mindustry.world.Tile(x,y,Blocks.sand,Blocks.air,Blocks.air));
        });
        Time.delta = 1f;
        Rules rules = Vars.state.rules;
        rules.defaultTeam = Team.sharded;
        rules.teams.get(Team.sharded).cheat = true;
        rules.teams.get(Team.derelict).cheat = true;
        for(Team team:Team.all) if(team != Team.sharded && team != Team.derelict)
            rules.teams.get(team).cheat = true;
        ContinuousTurret turret = EmberglowDefense.emberglow;
        turret.buildType = () -> new Probe(turret);
        Vars.world.tile(20,20).setBlock(turret,Team.malis);
        Probe enemy = (Probe)Vars.world.build(20,20);
        enemy.updateConsumption();
        check(enemy.efficiency == 1f,"test enemy cheat did not bypass missing oil/power");
        AravisEnvironment.applyResourceRules(rules);
        for(Team team:Team.all) if(team != Team.sharded && team != Team.derelict)
            check(!rules.teams.get(team).cheat,"enemy cheat remained enabled: " + team.id);
        check(rules.teams.get(Team.sharded).cheat,"changed player setting");
        check(rules.teams.get(Team.derelict).cheat,"changed neutral setting");
        enemy.updateConsumption();
        check(enemy.efficiency == 0f,"enemy fires without oil/power after disabling cheat");
        enemy.power.status = 1f;
        enemy.updateConsumption();
        check(enemy.efficiency == 0f,"enemy fires without oil");
        enemy.liquids.add(Liquids.oil,10f);
        enemy.updateConsumption();
        check(enemy.efficiency == 1f && enemy.liquids.get(Liquids.oil)<10f,
            "enemy with supplies does not consume oil normally");
        enemy.power.status = 0f;
        enemy.updateConsumption();
        check(enemy.efficiency == 0f,"enemy fires without power");
        AravisEnvironment.applyResourceRules(rules);
        check(!rules.teams.get(Team.malis).cheat,"non-idempotent rule");
        System.out.println("PASS: all enemy cheats disabled, player/neutral settings preserved, enemy needs oil and power and consumes supplies");
    }
}
