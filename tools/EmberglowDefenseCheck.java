import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.*;
import cryon.Content.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.entities.bullet.PointLaserBulletType;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.net.Net;
import mindustry.world.blocks.defense.turrets.ContinuousTurret;

/** Oil/power gating and native Lustre-style point-only beam damage. */
public class EmberglowDefenseCheck{
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
        ContinuousTurret turret = EmberglowDefense.emberglow;
        turret.buildType = () -> new Probe(turret);
        Vars.world.tile(20,20).setBlock(turret,Team.sharded);
        Probe build = (Probe)Vars.world.build(20,20);
        build.power.status = 1f;
        build.updateConsumption(); check(build.efficiency == 0f, "beam enabled without oil");
        build.liquids.add(Liquids.water,10f);
        build.updateConsumption(); check(build.efficiency == 0f, "water replaced oil");
        build.liquids.add(Liquids.oil,10f);
        build.updateConsumption(); check(build.efficiency == 1f, "oil and power did not enable beam: efficiency=" + build.efficiency + ", power=" + build.power.status + ", oil=" + build.liquids.get(Liquids.oil));
        check(build.liquids.get(Liquids.oil)<10f, "oil not consumed while firing");
        build.power.status = 0f;
        build.updateConsumption(); check(build.efficiency == 0f, "beam enabled without power");
        Vars.world.tile(45,20).setBlock(Blocks.copperWall,Team.crux);
        Vars.world.tile(32,20).setBlock(Blocks.copperWall,Team.crux);
        Building endpoint = Vars.world.build(45,20), path = Vars.world.build(32,20);
        float hp = endpoint.health, pathHp = path.health;
        PointLaserBulletType type = (PointLaserBulletType)turret.shootType;
        Bullet beam = type.create(build,Team.sharded,build.x,build.y,0f);
        beam.aimX = endpoint.x; beam.aimY = endpoint.y;
        Time.time += 6f;
        type.update(beam);
        check(endpoint.health < hp, "beam endpoint did not damage target");
        check(path.health == pathHp, "point laser damaged intervening building");
        check(type.status == StatusEffects.burning, "burning status missing");
        check(turret.aimChangeSpeed == ((ContinuousTurret)Blocks.lustre).aimChangeSpeed,
            "Lustre tracking behavior changed");
        check(turret.rotateSpeed == ((ContinuousTurret)Blocks.lustre).rotateSpeed, "Lustre turn speed changed");
        System.out.println("PASS: mandatory oil/power, oil consumption, water rejection, point-only damage and Lustre tracking speeds");
    }
}
