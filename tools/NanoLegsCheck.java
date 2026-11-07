import arc.*;
import arc.files.Fi;
import arc.math.geom.Vec2;
import arc.mock.*;
import arc.util.*;
import java.nio.file.Files;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.io.SaveIO;
import mindustry.net.Net;
import mindustry.type.*;
import mindustry.world.*;

/** Native articulated legs, motion and class-ID save migration for nano organisms. */
public final class NanoLegsCheck{
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[]args){try{run();System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
    static void run()throws Exception{
        Core.app=new MockApplication();Core.files=new MockFiles();Core.graphics=new MockGraphics();Core.audio=new MockAudio();Core.settings=new Settings();
        Fi data=new Fi(Files.createTempDirectory("nano-legs-check-").toFile());Core.settings.setDataDirectory(data);Core.bundle=I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlanuch.jar"));
        Vars.headless=true;Vars.loadLocales=false;Vars.net=new Net(null);Vars.platform=new mindustry.core.Platform(){};
        mindustry.core.Version.init();Vars.init();Core.settings.setJson(mindustry.io.JsonIO.json);Vars.content.createBaseContent();Vars.content.createModContent();Vars.content.init();
        var mod=Vars.mods.locateMod("cryon");check(mod!=null&&!mod.hasContentErrors(),"content errors");
        var migration=mod.main.getClass().getClassLoader().loadClass("cryon.Features.NanoLegUnits");
        UnitType eroder=Vars.content.unit("cryon-nano-eroder"),breaker=Vars.content.unit("cryon-nano-armor-breaker"),annihilator=Vars.content.unit("cryon-nano-annihilator"),carrier=Vars.content.unit("cryon-grey-goo-carrier");
        check(eroder.legCount==6&&breaker.legCount==6&&annihilator.legCount==8,"leg count");
        check(eroder.allowLegStep&&breaker.allowLegStep&&annihilator.allowLegStep,"native leg stepping not initialized");
        check(eroder.pathCost==mindustry.ai.ControlPathfinder.costLegs&&annihilator.pathCost==mindustry.ai.ControlPathfinder.costLegs,"native leg pathfinding");
        Vars.logic=new Logic();Vars.logic.reset();
        Vars.world.loadGenerator(160,120,tiles->{
            for(int x=0;x<160;x++)for(int y=0;y<120;y++)tiles.set(x,y,new Tile(x,y,Blocks.stone,Blocks.air,Blocks.air));
            tiles.get(5,5).setBlock(Blocks.coreShard,Team.sharded);
        });Vars.state.set(mindustry.core.GameState.State.playing);Time.delta=1;
        Team nano=Team.get(172);
        Unit small=eroder.spawn(nano,240,240),middle=breaker.spawn(nano,480,240),heavy=annihilator.spawn(nano,720,240);
        for(Unit unit:new Unit[]{small,middle,heavy})check(unit instanceof Legsc&&((Legsc)unit).legs().length==unit.type.legCount,"missing articulated legs: "+unit.type.name);
        check(carrier.hidden&&carrier.hideDatabase,"retired flying worker is exposed");
        Unit old=UnitEntity.create();old.team=nano;old.setType(eroder);old.set(900,700);old.rotation=37;old.health=123.25f;old.shield=27;old.flag=6742;
        old.vel.set(.3f,.1f);old.stack.item=Vars.content.item("cryon-nano-material");old.stack.amount=2;old.mounts[0].reload=17;
        old.apply(StatusEffects.freezing,177);old.add();
        check(!(old instanceof Legsc),"legacy fixture was not old entity class");
        Fi save=data.child("old-nano.msav");SaveIO.write(save);
        migration.getMethod("install").invoke(null);SaveIO.load(save);
        Unit upgraded=Groups.unit.find(u->u.flag==6742);
        check(upgraded instanceof Legsc&&((Legsc)upgraded).legs().length==6,"old class-ID save not upgraded");
        check(upgraded.health==123.25f&&upgraded.shield==27&&upgraded.team==nano&&upgraded.rotation==37,"migration lost combat state");
        check(upgraded.x==900&&upgraded.y==700&&upgraded.vel.epsilonEquals(.3f,.1f,.001f)&&upgraded.stack.amount==2,"migration lost movement/cargo");
        check(Math.abs(upgraded.getDuration(StatusEffects.freezing)-177)<.01f,"migration lost status: "+upgraded.getDuration(StatusEffects.freezing));
        Unit liveOld=UnitEntity.create();liveOld.team=nano;liveOld.setType(breaker);liveOld.set(1100,700);liveOld.health=2345;liveOld.flag=6743;liveOld.mounts[0].reload=23;liveOld.add();
        migration.getMethod("migrate").invoke(null);Unit liveUpgrade=Groups.unit.find(u->u.flag==6743);
        check(liveUpgrade instanceof Legsc&&liveUpgrade.health==2345&&liveUpgrade.mounts[0].reload==23,"live migration lost reload/health");
        int count=Groups.unit.size();migration.getMethod("migrate").invoke(null);check(Groups.unit.size()==count&&Groups.unit.find(u->u.flag==6742)==upgraded,"migration was not idempotent");
        middle=Groups.unit.find(u->u.type==breaker);Vec2 foot=((Legsc)middle).legs()[0].base.cpy(),joint=((Legsc)middle).legs()[0].joint.cpy();float start=middle.x;
        middle.controller(new mindustry.entities.units.AIController(){
            @Override public void updateMovement(){unit.movePref(new Vec2(1,0));}
        });
        for(int i=0;i<120;i++){Vars.state.teams.updateTeamStats();Groups.unit.updatePhysics();middle.update();Time.update();}
        check(middle.x>start+5&&!((Legsc)middle).legs()[0].joint.epsilonEquals(joint,.1f),"legs did not articulate while moving");
        check(!((Legsc)middle).legs()[0].base.epsilonEquals(foot,.1f),"feet did not step");middle.resetController();
        SaveIO.write(save);SaveIO.load(save);
        for(Unit u:Groups.unit)if(u.type==eroder||u.type==breaker||u.type==annihilator)check(u instanceof Legsc,"new legs save reverted entity class");
        try(var zip=new java.util.zip.ZipFile("build/libs/mindustry-MOD-JavaDesktop.jar")){
            for(String name:new String[]{"nano-eroder","nano-armor-breaker","nano-annihilator"})for(String part:new String[]{"leg","leg-base","joint","joint-base","foot"})check(zip.getEntry("sprites/units/"+name+"-"+part+".png")!=null,"missing leg texture: "+name+"-"+part);
        }
        System.out.println("NANO_LEGS_CHECK_PASS: native 6/6/8 legs, stepping and articulated motion, leg pathfinding, actual old UnitEntity save migration with health/shield/status/cargo/reload preserved, idempotence and new save round-trip, packaged textures");
    }
}
