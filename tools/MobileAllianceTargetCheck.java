import arc.*;
import arc.files.Fi;
import arc.graphics.Camera;
import arc.mock.*;
import arc.util.*;
import java.lang.reflect.Method;
import java.nio.file.Files;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.input.MobileInput;
import mindustry.net.Net;
import mindustry.world.Tile;

/** Execute native mobile movement/autofire against real allied/enemy entities. */
public class MobileAllianceTargetCheck{
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[]args)throws Exception{
        try{run();System.exit(0);}catch(Throwable error){error.printStackTrace();System.exit(1);}
    }
    static void run()throws Exception{
        Core.app=new MockApplication();Core.files=new MockFiles();Core.graphics=new MockGraphics();Core.audio=new MockAudio();
        Core.input=new MockInput();Core.camera=new Camera();Core.settings=new Settings();
        Fi data=new Fi(Files.createTempDirectory("cryon-mobile-alliance-").toFile());Core.settings.setDataDirectory(data);
        Core.bundle=I18NBundle.createEmptyBundle();Core.settings.put("autotarget",true);
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlanuch.jar"));
        Vars.headless=true;Vars.loadLocales=false;Vars.net=new Net(null);Vars.platform=new mindustry.core.Platform(){};
        mindustry.core.Version.init();Vars.init();Vars.content.createBaseContent();Vars.content.createModContent();Vars.content.init();
        var mod=Vars.mods.locateMod("cryon");check(mod!=null&&!mod.hasContentErrors(),"content errors");
        var loader=mod.main.getClass().getClassLoader();var alliances=loader.loadClass("cryon.Features.MapAlliances");
        alliances.getMethod("install").invoke(null);Vars.logic=new Logic();Vars.logic.reset();
        Vars.state.rules.tags.put("cryon-allied-teams",String.valueOf(Team.green.id));
        Vars.world.loadGenerator(80,80,tiles->{for(int x=0;x<80;x++)for(int y=0;y<80;y++)tiles.set(x,y,new Tile(x,y,Blocks.stone,Blocks.air,Blocks.air));});
        Vars.state.set(mindustry.core.GameState.State.playing);Time.delta=1;
        Unit shooter=UnitTypes.dagger.spawn(Team.sharded,240,240);
        Vars.player=Player.create();Vars.player.team(Team.sharded);Vars.player.unit(shooter);Vars.player.set(shooter);
        Core.camera.position.set(shooter.x,shooter.y);
        Vars.world.tile(32,30).setBlock(Blocks.copperWall,Team.green);
        Vars.world.tile(38,30).setBlock(Blocks.copperWall,Team.crux);
        Building friend=Vars.world.build(32,30),enemy=Vars.world.build(38,30);
        index();alliances.getMethod("refresh").invoke(null);
        Method movement=MobileInput.class.getDeclaredMethod("updateMovement",Unit.class);movement.setAccessible(true);
        MobileInput nativeInput=new MobileInput();movement.invoke(nativeInput,shooter);
        check(nativeInput.target==friend,"native fixture did not reproduce allied building lock");
        MobileInput input=(MobileInput)loader.loadClass("cryon.Type.AllianceMobileInput").getConstructor().newInstance();
        var present=Vars.state.teams.present;var active=Vars.state.teams.active;
        movement.invoke(input,shooter);check(input.target==enemy,"mobile autoaim did not skip allied building: target="+input.target+", enemy="+enemy+", range="+shooter.range()+", green="+Team.green.id+", present="+Vars.state.teams.present);
        input.target=friend;Vars.player.shooting=true;movement.invoke(input,shooter);
        check(input.target==enemy&&!Vars.player.shooting,"stale allied building lock persisted");
        enemy.tile.remove();index();input.target=null;movement.invoke(input,shooter);
        check(input.target==null&&!Vars.player.shooting,"ally-only scene caused automatic fire");
        Unit ally=UnitTypes.dagger.spawn(Team.green,256,248),hostile=UnitTypes.dagger.spawn(Team.crux,288,248);
        index();input.target=ally;movement.invoke(input,shooter);
        check(input.target==hostile,"stale allied unit lock did not switch to enemy");
        check(Vars.state.teams.present==present&&Vars.state.teams.active==active,"mobile search leaked filtered team lists");
        hostile.remove();ally.remove();index();input.target=null;input.manualShooting=true;movement.invoke(input,shooter);
        check(Vars.player.shooting,"manual fire was disabled");
        input.manualShooting=false;
        Unit healer=UnitTypes.poly.spawn(Team.sharded,240,240);Vars.player.unit(healer);Vars.player.set(healer);
        Vars.world.tile(30,32).setBlock(Blocks.copperWall,Team.sharded);Building damaged=Vars.world.build(30,32);damaged.health-=20;
        input.target=damaged;index();movement.invoke(input,healer);
        check(input.target==damaged&&Vars.player.shooting,"native same-team healing was blocked");
        System.out.println("MOBILE_ALLIANCE_TARGET_PASS: native bug reproduced, allied buildings/units skipped, enemy acquisition, ally-only no fire, scope restoration, manual fire and own-team healing");
    }
    static void index(){
        Vars.state.teams.updateTeamStats();
        for(Team team:new Team[]{Team.sharded,Team.green,Team.crux}){
            team.data().tree().clear();for(Unit unit:Groups.unit)if(unit.team==team)team.data().tree().insert(unit);
        }
    }
}
