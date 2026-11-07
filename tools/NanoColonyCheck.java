import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.*;
import java.nio.file.Files;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.io.SaveIO;
import mindustry.net.Net;
import mindustry.type.UnitType;
import mindustry.world.*;
import mindustry.world.blocks.production.Drill;

/** Real packaged ecology: terrain, corrosion, mining/duct transport/production, combat and captured sectors. */
public final class NanoColonyCheck{
    static Class<?> terrain, colonies;static Block floor,ore,drill,factory,wall,belt;static Team nano;static UnitType eroder;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[]args){try{run();System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
    static void run()throws Exception{
        Core.app=new MockApplication();Core.files=new MockFiles();Core.graphics=new MockGraphics();Core.audio=new MockAudio();
        Core.settings=new Settings();Fi data=new Fi(Files.createTempDirectory("nano-colony-check-").toFile());
        Core.settings.setDataDirectory(data);Core.bundle=I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlanuch.jar"));
        Vars.headless=true;Vars.loadLocales=false;Vars.net=new Net(null);Vars.platform=new mindustry.core.Platform(){};
        mindustry.core.Version.init();Vars.init();Core.settings.setJson(mindustry.io.JsonIO.json);
        Vars.content.createBaseContent();Vars.content.createModContent();Vars.content.init();
        var mod=Vars.mods.locateMod("cryon");check(mod!=null&&!mod.hasContentErrors(),"content errors");
        var loader=mod.main.getClass().getClassLoader();terrain=loader.loadClass("cryon.Content.NanoEnvironment");colonies=loader.loadClass("cryon.Features.NanoColonies");
        terrain.getMethod("install").invoke(null);
        floor=Vars.content.block("cryon-nano-floor");ore=Vars.content.block("cryon-nano-ore");drill=Vars.content.block("cryon-rogue-nano-drill");
        factory=Vars.content.block("cryon-nano-machine-factory");wall=Vars.content.block("cryon-rogue-nano-wall");belt=Vars.content.block("cryon-reinforced-vacuum-duct");
        eroder=Vars.content.unit("cryon-nano-eroder");nano=Team.get(172);
        check(nano.name.equals("nano")&&!nano.isOnlyAI()&&eroder!=null,"nano faction");
        check(Vars.content.item("cryon-nano-material").hardness==4,"hardness must be 4");
        check(eroder.weapons.first().bullet instanceof mindustry.entities.bullet.LaserBulletType,"missing pulse laser");
        check(eroder.weapons.first().bullet.damage==42f,"laser damage");
        Vars.logic=new Logic();Vars.logic.reset();world();
        infect(25,25);check(Vars.world.tile(25,25).overlay()==ore,"normal ore not replaced");
        infect(24,25);check(Vars.world.tile(24,25).overlay()==Blocks.air,"ore randomly created on bare ground");
        check(!((Drill)Blocks.mechanicalDrill).canMine(Vars.world.tile(25,25)),"low tier drill can mine hardness 4");
        check(((Drill)drill).canMine(Vars.world.tile(25,25))&&!((Drill)drill).canMine(Vars.world.tile(75,60)),"dedicated mining filter");
        terrain.getMethod("spreadStep").invoke(null);
        check(Vars.world.tile(27,25).overlay()==ore&&Vars.world.tile(24,23).floor()!=floor,"vein preference missing or ground spread jumped");
        terrain.getMethod("seed",float.class,float.class,int.class).invoke(null,25*8f,25*8f,14);
        colonies.getMethod("step").invoke(null);
        check(find(drill)==null&&find(factory)==null,"planner placed completed structures without builders");
        for(int i=0;i<12000&&(find(factory)==null||find(wall)==null);i++){
            tick();if(i%120==0)colonies.getMethod("step").invoke(null);
        }
        Building miner=find(drill),machine=find(factory);
        check(miner!=null&&machine!=null&&Groups.build.count(b->b.block==belt)>=5,"native drill/duct/factory construction missing");
        check(find(wall)!=null,"regenerating defenses missing");
        check((boolean)machine.getClass().getMethod("frontClear").invoke(machine),"factory frontage blocked by colony");
        boolean transported=false;
        for(int i=0;i<6000;i++){
            tick();if(machine.items.total()>0)transported=true;
            if(Groups.unit.contains(u->u.type==eroder))break;
        }
        check(transported,"native duct failed to transport mined nano material");
        check(Groups.unit.contains(u->u.type==eroder&&u.team==nano),"factory did not produce eroder from mined fuel");
        Unit insect=Groups.unit.find(u->u.type==eroder);insect.set(70*8,40*8);
        terrain.getMethod("spreadByUnit",Unit.class).invoke(null,insect);
        check(Vars.world.tile(70,40).floor()==floor&&Vars.world.tile(69,40).floor()==floor,"eroder did not seed contamination");
        Vars.world.tile(70,20).setBlock(Vars.content.block("cryon-nano-suppressor"),Team.sharded);
        Building suppressor=Vars.world.build(70,20);suppressor.power.status=1;insect.set(70*8,20*8);
        terrain.getMethod("spreadByUnit",Unit.class).invoke(null,insect);
        check(Vars.world.tile(70,20).floor()!=floor,"new suppressor failed to block eroder immediately");
        suppressor.power.status=0;terrain.getMethod("spreadByUnit",Unit.class).invoke(null,insect);
        check(Vars.world.tile(70,20).floor()==floor,"blackout did not allow eroder infection");
        suppressor.power.status=1;insect.set(74*8,20*8);terrain.getMethod("spreadByUnit",Unit.class).invoke(null,insect);
        check(Vars.world.tile(74,20).floor()!=floor,"reactivated suppression missed eroder infection");
        insect.set(70*8,40*8);
        Vars.world.tile(78,40).setBlock(Blocks.vault,Team.sharded);Building laserTarget=Vars.world.build(78,40);float laserHp=laserTarget.health;
        eroder.weapons.first().bullet.create(insect,nano,insect.x,insect.y,0f);
        check(laserTarget.health<laserHp,"pulse laser failed to deal native collision damage");
        Building defense=find(wall);defense.health-=100;float damaged=defense.health;defense.updateTile();
        check(defense.health>damaged&&wall.armor==12f,"nano wall regeneration/armor");
        corrosion();
        // Three clear tiles are mandatory, and a blocked factory never spends fuel/progress.
        machine.items.set(Vars.content.item("cryon-nano-material"),12);machine.getClass().getField("progress").setFloat(machine,0f);
        int fx=machine.tile.x,fy=machine.tile.y;int dx=arc.math.geom.Geometry.d4x(machine.rotation),dy=arc.math.geom.Geometry.d4y(machine.rotation);
        Vars.world.tile(fx+dx*2-dy,fy+dy*2+dx).setBlock(Blocks.copperWall,Team.sharded);
        check(!(boolean)machine.getClass().getMethod("frontClear").invoke(machine),"front side tile ignored");
        for(int i=0;i<500;i++)machine.updateTile();
        check(machine.items.get(Vars.content.item("cryon-nano-material"))==12&&machine.getClass().getField("progress").getFloat(machine)==0,"blocked factory spent material");
        Vars.world.tile(fx+dx*2-dy,fy+dy*2+dx).setBlock(Blocks.air);
        // Victory ignores even an editor-placed disaster core; cleanup cannot neutralize the faction.
        Vars.world.tile(85,10).setBlock(Blocks.coreShard,nano);Vars.state.teams.updateTeamStats();
        check(!nano.data().isAlive()&&!Vars.state.teams.get(nano).isAlive(),"disaster faction counted as capture objective");
        nano.data().destroyToDerelict();nano.data().timeDestroy(25*8,25*8,1000f);
        for(int i=0;i<400;i++)Time.update();
        check(miner.team==nano&&miner.isValid()&&!insect.dead,"capture cleanup destroyed nano faction");
        Vars.state.rules.sector=Planets.serpulo.sectors.first();Vars.state.rules.waves=false;Vars.state.rules.attackMode=false;
        check(Vars.state.rules.sector.isCaptured(),"test sector is not captured");
        insect.update();check(!insect.dead&&insect.isAdded(),"captured sector killed nano unit");
        // Native Logic must allow ordinary victory while nano buildings and units remain.
        Vars.state.rules.sector=null;Vars.state.rules.attackMode=true;Vars.state.rules.waves=false;Vars.state.rules.defaultTeam=Team.sharded;
        var checkGame=Logic.class.getDeclaredMethod("checkGameState");checkGame.setAccessible(true);checkGame.invoke(Vars.logic);
        check(Vars.state.gameOver,"nano faction blocked native attack victory");Vars.state.gameOver=false;
        Fi save=data.child("nano-colony.msav");SaveIO.write(save);SaveIO.load(save);
        check(find(factory)!=null&&find(drill)!=null&&Groups.unit.contains(u->u.type==eroder),"colony save/load lost entities");
        check(!nano.data().isAlive(),"disaster policy lost after load");
        colonies.getMethod("step").invoke(null);
        // Existing optional alliances wrap native entities; colony subclasses must remain compatible.
        var alliances=loader.loadClass("cryon.Features.MapAlliances");alliances.getMethod("install").invoke(null);
        Vars.state.rules.tags.put("cryon-allied-teams","4,172");
        check(!(boolean)alliances.getMethod("allied",Team.class,Team.class).invoke(null,Team.sharded,nano),"disaster faction became an ally");
        Vars.world.tile(60,10).setBlock(wall,nano);Vars.world.tile(65,10).setBlock(factory,nano);
        Building adapted=Vars.world.build(60,10);adapted.health-=100;adapted.updateTile();check(adapted.health>1700,"alliance adapter broke nano regeneration");
        check(eroder.create(nano)!=null,"alliance adapter broke eroder construction");
        System.out.println("NANO_COLONY_CHECK_PASS: hardness, mineral replacement, vein preference, staged construction, real drill/duct/factory economy, eroder seeding, green pulse laser, wall regen, corrosion, frontage, victory exclusion, capture survival, save/load");
    }
    static void world(){
        Vars.world.loadGenerator(100,80,tiles->{
            for(int x=0;x<100;x++)for(int y=0;y<80;y++)tiles.set(x,y,new Tile(x,y,Blocks.stone,Blocks.air,Blocks.air));
            tiles.get(5,5).setBlock(Blocks.coreShard,Team.sharded);
            for(int x=25;x<=27;x++)tiles.get(x,25).setOverlay(Blocks.oreCopper);
            tiles.get(75,60).setOverlay(Blocks.oreTitanium);
        });Vars.state.set(mindustry.core.GameState.State.playing);Vars.state.rules.canGameOver=false;Vars.state.rules.enemyCoreBuildRadius=0;Time.delta=1f;
    }
    static void corrosion()throws Exception{
        infect(60,60);Unit ground=UnitTypes.dagger.spawn(Team.sharded,480,480),air=UnitTypes.flare.spawn(Team.sharded,480,480);
        Unit own=eroder.spawn(nano,480,480);float gh=ground.health,ah=air.health,nh=own.health;
        Vars.world.tile(55,60).setBlock(Blocks.vault,Team.sharded);infect(54,59);Building b=Vars.world.build(55,60);float hp=b.health;
        Building safe=find(factory);float safeHp=safe.health;
        terrain.getMethod("damageTerrain").invoke(null);
        check(ground.health<gh&&air.health==ah&&own.health==nh,"ground-only nano-immune unit corrosion");
        check(b.health==hp-40f&&safe.health==safeHp,"building footprint corrosion/immunity: building="+hp+" -> "+b.health+", nano="+safeHp+" -> "+safe.health+", size="+b.block.size+", grouped="+Groups.build.copy().contains(b)+", floor="+Vars.world.tile(54,59).floor().name);
    }
    static void infect(int x,int y)throws Exception{terrain.getMethod("infect",Tile.class).invoke(null,Vars.world.tile(x,y));}
    static Building find(Block b){return Groups.build.find(x->x.block==b&&x.team==nano);}
    static void tick(){Groups.unit.updatePhysics();for(Unit u:Groups.unit.copy())if(u.type.name.equals("cryon-nano-weaver"))u.update();for(Building b:Groups.build.copy())if(b.isValid())b.update();Time.update();Vars.state.tick+=1;Vars.state.teams.updateTeamStats();}
}
