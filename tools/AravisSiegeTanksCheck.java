import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.*;
import arc.util.io.*;
import java.io.*;
import java.nio.file.Files;
import mindustry.*;
import mindustry.ai.UnitCommand;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.abilities.MoveEffectAbility;
import mindustry.game.Team;
import mindustry.game.Objectives;
import mindustry.game.Saves;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.*;
import mindustry.net.Net;
import mindustry.type.*;
import mindustry.world.Tile;
import mindustry.world.blocks.payloads.UnitPayload;
import mindustry.world.blocks.units.UnitFactory;

/** Packaged manufacture, combat layers, native hover movement, transport commands and serialization. */
public class AravisSiegeTanksCheck{
    static UnitType unit(String name){return Vars.content.unit("cryon-aravis-"+name);}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void tick(Building build,int count){for(int i=0;i<count;i++){Time.time++;Time.delta=1f;build.updateConsumption();build.updateTile();}}
    static void index(){
        Vars.state.teams.updateTeamStats();
        for(Team team:new Team[]{Team.sharded,Team.crux}){
            team.data().tree().clear();
            for(Unit unit:Groups.unit)if(unit.team==team)team.data().tree().insert(unit);
        }
    }
    public static void main(String[] args){try{run();System.exit(0);}catch(Throwable error){error.printStackTrace();System.exit(1);}}
    static void run() throws Exception{
        Core.app=new MockApplication();Core.files=new MockFiles();Core.graphics=new MockGraphics();Core.audio=new MockAudio();
        Core.settings=new Settings();Core.settings.setDataDirectory(new Fi(Files.createTempDirectory("cryon-aravis-rts-").toFile()));
        Core.bundle=I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(Core.settings.getDataDirectory().child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(Core.settings.getDataDirectory().child("mods/unitlaunch.jar"));
        Vars.headless=true;Vars.loadLocales=false;Vars.net=new Net(null);Vars.platform=new mindustry.core.Platform(){};
        mindustry.core.Version.init();Vars.init();Vars.content.createBaseContent();Vars.content.createModContent();Vars.content.init();
        var mod=Vars.mods.locateMod("cryon");check(mod!=null&&!mod.hasContentErrors(),"Packaged content errors");
        ClassLoader loader=mod.main.getClass().getClassLoader();
        for(UnitType type:Vars.content.units())type.uiIcon=new arc.graphics.g2d.TextureRegion();
        loader.loadClass("cryon.TechTree.AravisTechTree").getMethod("load").invoke(null);Timer.instance().clear();
        Vars.logic=new Logic();Vars.logic.reset();Vars.state.rules.unitCap=100;Vars.state.rules.canGameOver=false;
        Vars.world.loadGenerator(120,120,tiles -> {
            tiles.fill();for(Tile tile:tiles)tile.setFloor(Blocks.stone.asFloor());
        });
        Time.delta=1f;
        String[] names={"abrade","rattle","usurp","deter","overbear"};
        var upgrades=(arc.struct.ObjectMap<UnitType,UnitType>)loader.loadClass("cryon.Content.AravisSiegeTanks").getField("upgrades").get(null);
        check(upgrades.size==4,"expected four upgrade routes");
        float lastHealth=0,lastArmor=0,lastRange=0,lastDamage=0;
        for(int i=0;i<names.length;i++){
            UnitType type=unit(names[i]);
            check(type!=null && type.constructor.get() instanceof Tankc,"not a tank " + names[i]);
            check(!type.flying && !type.omniMovement && type.targetGround && !type.targetAir,"wrong movement/targeting");
            check(type.techNode!=null && type.techNode.parent.content.name.equals(i==0?"cryon-aravis-army-headquarters":unit(names[i-1]).name),"incorrect tier research chain");
            check(type.shownPlanets.contains(Vars.content.planet("cryon-aravis")),"wrong planet");
            check(type.health>lastHealth && type.armor>lastArmor && type.range>lastRange,"tiers do not progress");
            if(i<4){
                UnitType mech=Vars.content.unit("cryon-"+new String[]{"jaspis","obsidianum","nitrum","hematites"}[i]);
                check(type.health<mech.health && type.armor<mech.armor,"siege tank is not more fragile than same-tier mech");
            }
            check(type.targetFlags[0]==mindustry.world.meta.BlockFlag.turret,"missing siege targeting");
            if(i<4)check(upgrades.get(type)==unit(names[i+1]),"wrong upgrade route");
            else check(upgrades.get(type)==null,"T5 unexpectedly upgrades");
            Unit tank=type.create(Team.sharded);tank.set(300,480);tank.add();
            check(tank.mounts.length>0,"missing weapon mounts");
            check(tank.mounts[0].weapon.x==0f && tank.mounts[0].weapon.y==0f,
                "main turret is not mounted at the hull center: " + names[i]);
            BulletType shell=type.weapons.first().bullet;
            check(!shell.collidesAir && shell.buildingDamageMultiplier>1f,"missing ground siege damage");
            Vars.world.tile(60,60).setBlock(Blocks.surgeWall,Team.crux);
            Vars.world.tile(62,60).setBlock(Blocks.surgeWall,Team.sharded);
            Building target=Vars.world.build(60,60),friendly=Vars.world.build(62,60);
            target.health=target.maxHealth=100000f;
            float health=target.health,friendHealth=friendly.health;
            Bullet bullet=shell.create(tank,Team.sharded,target.x,target.y,0f);
            if(!(shell instanceof mindustry.entities.bullet.ArtilleryBulletType))target.collision(bullet);
            shell.hit(bullet,target.x,target.y);
            Time.update(); // Native building splash resolves on the following tick.
            float damage=health-target.health;
            check(damage>lastDamage,"higher tier does not increase building impact damage: " + names[i]);
            check(friendly.health==friendHealth,"siege splash hit friendly building");
            for(var block:Vars.content.blocks()){
                if(block instanceof UnitFactory factory)check(!factory.plans.contains(p -> p.unit==type),"unexpected factory recipe");
                if(block instanceof mindustry.world.blocks.units.Reconstructor reconstructor)
                    check(!reconstructor.upgrades.contains(pair -> pair[0]==type || pair[1]==type),"unexpected reconstructor recipe");
            }
            tank.remove();
            Vars.world.tile(60,60).setAir();Vars.world.tile(62,60).setAir();
            crushing(type);
            lastHealth=type.health;lastArmor=type.armor;lastRange=type.range;lastDamage=damage;
            System.out.println("PASS " + type.name + ": tank, tier research, siege damage=" + damage);
        }
        check(unit("rattle").weapons.first().shoot.shots==3,"T2 burst missing");
        check(unit("usurp").weapons.first().bullet.pierceBuilding,"T3 building penetration missing");
        check(unit("deter").weapons.first().shoot.shots==2,"T4 twin artillery missing");
        check(unit("overbear").weapons.size==3,"T5 auxiliary guns missing");
        check(unit("overbear").weapons.get(1).x==-unit("overbear").weapons.get(2).x
            && Math.abs(unit("overbear").weapons.get(1).x)==13f,"T5 side guns lost symmetric mounting");
        System.out.println("PASS: packaged five-tier siege tank line, upgrade/research routes, progression, building damage, friendly splash immunity and no production recipes");
    }

    static void crushing(UnitType type){
        Tile tile=Vars.world.tile(80,80);
        tile.setBlock(Blocks.conveyor,Team.crux);
        Building building=tile.build;building.health=building.maxHealth=100000f;
        Unit tank=type.create(Team.sharded);tank.set(tile.worldx(),tile.worldy());tank.add();
        // Teleporting into the test fixture must not count as movement.
        tank.lastX=tank.x;tank.lastY=tank.y;tank.deltaX=tank.deltaY=0f;
        ((Tankc)tank).walked(false);
        tank.controller(new mindustry.entities.units.AIController(){@Override public void updateUnit(){}});
        float health=building.health;
        tank.update();
        check(building.health==health,"stationary tank crushed building");
        tank.moveAt(new arc.math.geom.Vec2(1f,0f));tank.update();
        check(building.health<health,"moving tank did not crush enemy building: "+type.name);
        tile.setBlock(Blocks.conveyor,Team.sharded);
        building=tile.build;health=building.health;
        tank.moveAt(new arc.math.geom.Vec2(1f,0f));tank.update();
        check(building.health==health,"tank crushed friendly building");
        tile.setBlock(Blocks.conveyor,Team.crux);
        building=tile.build;health=building.health;
        tank.apply(StatusEffects.disarmed,60f);
        tank.moveAt(new arc.math.geom.Vec2(1f,0f));tank.update();
        check(building.health==health,"disarmed tank still crushed building");
        tank.remove();tile.setAir();
    }
}
