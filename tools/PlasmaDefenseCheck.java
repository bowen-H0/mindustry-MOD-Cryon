import arc.*;
import arc.files.*;
import arc.mock.*;
import arc.util.*;
import mindustry.*;
import mindustry.net.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.game.*;
import cryon.Content.*;
import cryon.Type.*;

public class PlasmaDefenseCheck{
    static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }
    public static void main(String[] args){
        Core.app = new MockApplication(); Core.files = new MockFiles();
        Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Core.settings = new Settings(); Core.settings.setDataDirectory(new Fi("/tmp/plasma-defense-check"));
        Core.bundle = I18NBundle.createEmptyBundle();
        Vars.headless = true; Vars.loadLocales = false; Vars.net = new Net(null);
        Vars.platform = new mindustry.core.Platform(){};
        Vars.init(); Vars.content.createBaseContent();
        CryonItems.aluminum = Items.copper;
        PlasmaDefense.load();
        FluxBarrier shieldBlock = new FluxBarrier("flux-test");
        Vars.content.init();
        Vars.world.loadGenerator(80, 80, tiles -> {
            for(int x=0;x<80;x++) for(int y=0;y<80;y++)
                tiles.set(x,y,new mindustry.world.Tile(x,y,Blocks.sand,Blocks.air,Blocks.air));
        });
        Vars.state.rules.unitCap = 100;
        Time.delta = 1f;
        Vars.world.tile(20,20).setBlock(PlasmaDefense.plasmaFence, Team.sharded);
        Vars.world.tile(35,20).setBlock(PlasmaDefense.plasmaFence, Team.sharded);
        Vars.world.tile(60,20).setBlock(PlasmaDefense.plasmaFence, Team.sharded);
        PlasmaFence.PlasmaFenceBuild a = (PlasmaFence.PlasmaFenceBuild)Vars.world.build(20,20);
        PlasmaFence.PlasmaFenceBuild b = (PlasmaFence.PlasmaFenceBuild)Vars.world.build(35,20);
        a.connect(b); check(a.partner()==b && b.partner()==a,"link not reciprocal");
        a.connect(Vars.world.build(60,20)); check(a.partner()==b,"out-of-range connection accepted");
        a.efficiency = b.efficiency = 1f;
        Unit enemy=UnitTypes.dagger.create(Team.crux); enemy.set((a.x+b.x)/2,a.y); enemy.add(); enemy.health=10000; enemy.maxHealth=10000;
        Unit ally=UnitTypes.dagger.create(Team.sharded); ally.set(enemy.x,enemy.y); ally.add();
        Unit far=UnitTypes.dagger.create(Team.crux); far.set(enemy.x,enemy.y+50); far.add();
        Vars.state.teams.updateTeamStats();
        enemy.team.data().tree().clear(); enemy.team.data().tree().insert(enemy); enemy.team.data().tree().insert(far);
        ally.team.data().tree().clear(); ally.team.data().tree().insert(ally);
        float hp=enemy.health, allyHp=ally.health, farHp=far.health;
        Bullet passing = new mindustry.entities.bullet.BasicBulletType(3f, 10f).create(null, Team.crux, enemy.x, enemy.y, 0f);
        a.updateTile(); b.updateTile();
        check(passing.isAdded() && !passing.absorbed, "fence blocked projectile");
        check(Math.abs(hp-enemy.health-200f)<0.01f,"beam missing or double damage");
        check(ally.health==allyHp && far.health==farHp,"beam hit friendly or distant unit");
        b.efficiency=0; hp=enemy.health; a.updateTile(); b.updateTile();
        check(hp==enemy.health,"unpowered fence dealt damage");
        Bullet probe=Bullet.create(); probe.team=Team.sharded; probe.type=PlasmaDefense.interfere.shootType; probe.damage=5;
        probe.set(enemy.x,enemy.y);
        probe.type.hit(probe,probe.x,probe.y);
        check(enemy.hasEffect(PlasmaDefense.paralysis),"EMP did not paralyze nearby enemy");
        check(!ally.hasEffect(PlasmaDefense.paralysis),"EMP paralyzed ally");
        check(!far.hasEffect(PlasmaDefense.paralysis),"EMP exceeded radius");
        check(FluxBarrierEffects.heat(100)<40 && FluxBarrierEffects.heat(500)<250,"high damage heat reduction missing");
        check(FluxBarrierEffects.coolingDelay(10000)==120,"cooldown unbounded");
        Vars.world.tile(27,20).setBlock(shieldBlock, Team.sharded);
        FluxBarrier.ForceBuild shield = (FluxBarrier.ForceBuild)Vars.world.build(27,20);
        hp = enemy.health;
        shield.kill();
        check(enemy.health < hp, "cold/broken building destruction did not discharge");
        check(ally.health == allyHp, "shield discharge hit ally");
        a.disconnect(); check(a.partner()==null && b.partner()==null,"disconnect left stale link");
        System.out.println("PASS: reciprocal/range links, single beam damage, friendly/distant immunity, power cutoff, EMP radius/status, thermal resistance, cooldown cap, projectile passage and cold shield destruction");
    }
}
