import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.*;
import cryon.Content.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.net.Net;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.turrets.LiquidTurret;
import mindustry.world.meta.Env;

/** Oil ammunition and actual persistent, burning oil deposits. */
public class IncinerationDefenseCheck{
    static void check(boolean ok,String message){ if(!ok) throw new AssertionError(message); }
    static class Probe extends LiquidTurret.LiquidTurretBuild{
        Probe(LiquidTurret turret){ turret.super(); }
        void fire(){ shoot(peekAmmo()); }
    }
    public static void main(String[] args){
        Core.app=new MockApplication(); Core.files=new MockFiles();
        Core.graphics=new MockGraphics(); Core.audio=new MockAudio();
        Core.settings=new Settings(); Core.settings.setDataDirectory(new Fi("/tmp/incineration-defense-check"));
        Core.bundle=I18NBundle.createEmptyBundle();
        Vars.headless=true; Vars.loadLocales=false; Vars.net=new Net(null);
        Vars.platform=new mindustry.core.Platform(){};
        Vars.init(); Vars.content.createBaseContent(); CryonItems.load();
        IncinerationDefense.load(); Vars.content.init();
        Vars.world.loadGenerator(80,80,tiles -> {
            tiles.fill(); for(Tile tile:tiles)tile.setFloor(Blocks.sand.asFloor());
        });
        Time.delta=1f;
        Vars.state.rules.fire=true;
        Vars.state.rules.env=Env.terrestrial | Env.oxygen;
        LiquidTurret turret=IncinerationDefense.incineration;
        turret.buildType=() -> new Probe(turret);
        Vars.world.tile(20,20).setBlock(turret,Team.sharded);
        Probe build=(Probe)Vars.world.build(20,20);
        check(turret.requirements.length>0 && java.util.Arrays.stream(turret.requirements)
            .anyMatch(s -> s.item==Items.tungsten && s.amount>0),"tungsten construction requirement missing");
        check(!build.acceptItem(null,Items.tungsten),"tungsten incorrectly used as ammo");
        check(build.acceptLiquid(null,Liquids.oil),"oil rejected");
        check(!build.acceptLiquid(null,Liquids.water) && !build.acceptLiquid(null,Liquids.slag),"wrong liquid accepted");
        check(!build.hasAmmo(),"empty turret has ammo");
        build.liquids.add(Liquids.oil,10f);
        build.updateConsumption();
        build.rotation=0f; build.targetPos.set(build.x+150f,build.y);
        build.fire();
        check(Math.abs(build.liquids.get(Liquids.oil)-9f)<0.001f,"shot did not consume one oil");
        build.liquids.clear();
        check(!build.hasAmmo(),"turret fires without oil");
        var type=turret.ammoTypes.get(Liquids.oil);
        Bullet bullet=type.create(build,Team.sharded,300f,300f,0f);
        type.hit(bullet,300f,300f);
        Tile impact=Vars.world.tileWorld(300f,300f);
        Puddle oil=Puddles.get(impact);
        Fire fire=Fires.get(impact);
        check(oil!=null && oil.liquid==Liquids.oil && oil.amount>0f,"impact did not leave real oil");
        check(fire!=null && fire.isAdded(),"impact oil was not ignited");
        int coated=0, burning=0;
        for(Tile tile:Vars.world.tiles){
            if(Puddles.get(tile)!=null) coated++;
            if(Fires.get(tile)!=null) burning++;
        }
        check(coated>1 && burning>1,"missing area coverage");
        for(int i=0;i<60;i++){
            Time.time+=1f;
            oil.update(); fire.update();
        }
        check(Puddles.get(impact)!=null && Fires.get(impact)!=null && Fires.get(impact).isAdded(),
            "burning oil did not persist after impact");
        Bullet expired=type.create(build,Team.sharded,440f,440f,0f);
        type.despawned(expired);
        Tile landing=Vars.world.tileWorld(440f,440f);
        check(Puddles.get(landing)!=null && Fires.get(landing)!=null,"empty-ground expiration did not ignite oil");
        Vars.state.rules.fire=false;
        Bullet noFire=type.create(build,Team.sharded,520f,520f,0f);
        type.hit(noFire,520f,520f);
        check(Fires.get(Vars.world.tileWorld(520f,520f))==null,"ignored native fire-disabled rule");
        Vars.state.rules.fire=true;
        Vars.state.rules.planet=new mindustry.type.Planet("cryon-aravis",Planets.sun,1f,0);
        Vars.state.rules.env=Env.terrestrial;
        AravisEnvironment.refreshLoadedWorld();
        check(Vars.state.rules.hasEnv(Env.oxygen),"old Aravis sector still lacks oxygen");
        Bullet migrated=type.create(build,Team.sharded,600f,600f,0f);
        type.hit(migrated,600f,600f);
        check(Fires.get(Vars.world.tileWorld(600f,600f))!=null,"oil does not burn in migrated Aravis sector");
        check(turret.reload==4f && !turret.extinguish && !type.collidesAir,"firing/ground targeting mismatch");
        System.out.println("PASS: tungsten construction, oil-only shooting/consumption, area oil+fire, persistent burning, empty-ground impact and fire-rule handling");
    }
}
