import arc.*;
import arc.files.*;
import arc.mock.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.net.*;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import cryon.Content.*;
import cryon.Type.*;

/** Native laser collision and magnesium ammunition checks, without desktop state. */
public class FaultDefenseCheck{
    static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }
    public static void main(String[] args){
        Core.app=new MockApplication(); Core.files=new MockFiles(); Core.graphics=new MockGraphics(); Core.audio=new MockAudio();
        Core.settings=new Settings(); Core.settings.setDataDirectory(new Fi("/tmp/cryon-fault-check")); Core.bundle=I18NBundle.createEmptyBundle();
        Vars.headless=true; Vars.loadLocales=false; Vars.net=new Net(null); Vars.platform=new mindustry.core.Platform(){};
        Vars.init(); Vars.content.createBaseContent(); CryonItems.load(); FaultDefense.load(); Vars.content.init();
        Vars.world.loadGenerator(80,80, tiles -> {for(int x=0;x<80;x++)for(int y=0;y<80;y++)
            tiles.set(x,y,new mindustry.world.Tile(x,y,Blocks.sand,Blocks.air,Blocks.air));});
        Time.delta=1f;
        Vars.state.rules.unitCap=100;
        FaultTurret turret=FaultDefense.fault;
        Vars.world.tile(20,20).setBlock(turret,Team.sharded);
        ItemTurret.ItemTurretBuild build=(ItemTurret.ItemTurretBuild)Vars.world.build(20,20);
        check(build.acceptItem(null,CryonItems.magnesium),"magnesium rejected");
        check(!build.acceptItem(null,Items.copper),"wrong ammo accepted");
        build.handleStack(CryonItems.magnesium,2,null);
        check(build.totalAmmo==2,"magnesium ammo conversion incorrect");
        build.useAmmo(); check(build.totalAmmo==0,"shot must consume 2 magnesium");
        FaultLaserBulletType beam=(FaultLaserBulletType)turret.ammoTypes.get(CryonItems.magnesium);
        mindustry.world.blocks.defense.turrets.Turret lancer=(mindustry.world.blocks.defense.turrets.Turret)Blocks.lancer;
        check(turret.shootSound==lancer.shootSound && turret.chargeSound==lancer.chargeSound,"Lancer audio not reused");
        check(turret.inaccuracy==12 && turret.shoot.firstShotDelay==24,"accuracy/charge incorrect");
        check(!turret.targetAir && !beam.collidesAir,"ground-only targeting changed");
        check(beam.damage==320 && beam.pierceCap==3,"damage or penetration incorrect");
        Unit[] targets=new Unit[4];
        for(int i=0;i<4;i++){
            Unit u=UnitTypes.dagger.create(Team.crux);u.set(200+i*28,164);u.add();u.health=u.maxHealth=10000;
            targets[i]=u;
        }
        Unit flying=UnitTypes.flare.create(Team.crux);flying.set(215,164);flying.elevation=1;flying.add();
        Unit friendly=UnitTypes.dagger.create(Team.sharded);friendly.set(215,164);friendly.add();
        Vars.state.teams.updateTeamStats();
        Team.crux.data().tree().clear();for(Unit u:targets)Team.crux.data().tree().insert(u);Team.crux.data().tree().insert(flying);
        Team.sharded.data().tree().clear();Team.sharded.data().tree().insert(friendly);
        float airHp=flying.health, friendlyHp=friendly.health;
        Bullet b=beam.create(build,Team.sharded,180,164,0f);
        for(int i=0;i<3;i++)check(Math.abs(targets[i].health-9680)<0.01,"laser missed piercing target "+i+": "+targets[i].health);
        check(targets[3].health==10000,"laser exceeded 3-target penetration cap");
        check(flying.health==airHp && friendly.health==friendlyHp,"laser hit aircraft or friend");
        check(b.fdata>0 && b.fdata<=beam.length,"native collision length unavailable for rendering");
        System.out.println("PASS: magnesium-only ammo, 2 items per shot, charge/spread, 320 damage, 3-target native laser penetration, ground/friendly filtering and visual length");
    }
}
