import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.*;
import cryon.Content.*;
import cryon.Type.TerraTurret;
import mindustry.*;
import mindustry.content.*;
import mindustry.entities.Mover;
import mindustry.entities.bullet.BulletType;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.net.Net;

/** Exercises native paired shooting, liquid gating and point-impact lightning. */
public class TerraDefenseCheck{
    static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }
    static class Probe extends TerraTurret.TerraTurretBuild{
        int fired;
        Probe(TerraTurret turret){ turret.super(); }
        void fire(){ shoot(TerraDefense.terra.shootType); }
        @Override protected void bullet(BulletType type, float x, float y, float angle, Mover mover){
            check(type == TerraDefense.terra.beams[(fired / 2) % 4], "incorrect paired color order");
            fired++;
            super.bullet(type, x, y, angle, mover);
        }
    }
    public static void main(String[] args){
        Core.app = new MockApplication(); Core.files = new MockFiles();
        Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Core.settings = new Settings(); Core.settings.setDataDirectory(new Fi("/tmp/terra-defense-check"));
        Core.bundle = I18NBundle.createEmptyBundle();
        Vars.headless = true; Vars.loadLocales = false; Vars.net = new Net(null);
        Vars.platform = new mindustry.core.Platform(){};
        Vars.init(); Vars.content.createBaseContent(); CryonItems.load(); TerraDefense.load(); Vars.content.init();
        Vars.world.loadGenerator(80, 80, tiles -> {
            for(int x=0;x<80;x++) for(int y=0;y<80;y++)
                tiles.set(x,y,new mindustry.world.Tile(x,y,Blocks.sand,Blocks.air,Blocks.air));
        });
        Time.delta = 1f;
        TerraTurret turret = TerraDefense.terra;
        turret.buildType = () -> new Probe(turret);
        Vars.world.tile(20,20).setBlock(turret, Team.sharded);
        Probe build = (Probe)Vars.world.build(20,20);
        build.rotation = 0f; build.targetPos.set(300,164);
        build.updateConsumption(); check(build.efficiency == 0f, "fired without liquids");
        build.liquids.add(Liquids.water, 10f);
        build.updateConsumption(); check(build.efficiency == 0f, "water alone enabled firing");
        build.liquids.clear(); build.liquids.add(Liquids.slag, 10f);
        build.updateConsumption(); check(build.efficiency == 0f, "slag alone enabled firing");
        build.liquids.add(Liquids.water, 10f);
        build.updateConsumption(); check(build.efficiency > 0f, "both liquids did not enable firing");
        check(build.liquids.get(Liquids.water)<10f && build.liquids.get(Liquids.slag)<10f,
            "both liquids must be consumed");
        for(int i=0;i<5;i++) build.fire();
        check(build.fired == 10, "missing twin shots");
        check(Groups.bullet.size() > 0, "empty ground impacts did not produce lightning");
        Vars.world.tile(38,20).setBlock(Blocks.copperWall,Team.crux);
        Building enemy = Vars.world.build(38,20);
        float hp = enemy.health;
        turret.beams[0].create(build, Team.sharded, enemy.x-240f, enemy.y, 0f);
        check(enemy.health < hp, "point beam did not damage enemy building");
        for(int i=0;i<4;i++){
            BulletType beam = turret.beams[i];
            check(beam.lightningColor.equals(TerraTurret.spectrum[i]), "arc color mismatches beam");
            check(!beam.collidesAir && !beam.lightningType.collidesAir, "ground-only targeting failed");
        }
        System.out.println("PASS: both-liquid consumption/gating, twin shots, four-color cycle, empty-ground lightning, building damage and ground-only arcs");
    }
}
