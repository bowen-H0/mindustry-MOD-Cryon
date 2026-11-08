import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.*;
import arc.math.geom.Vec2;
import java.nio.file.Files;
import java.lang.reflect.Method;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.net.Net;
import mindustry.type.*;
import mindustry.world.Tile;
import mindustry.world.blocks.units.UnitFactory;

/** Repeatable warmed-up parry benchmark: 200 nearby projectiles, CPU time and thread allocations. */
public class MechPerfCheck{
    static UnitType type;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static Object field(Object value,String name)throws Exception{return value.getClass().getField(name).get(value);}
    static boolean active(Object dash)throws Exception{return (Boolean)dash.getClass().getMethod("active").invoke(dash);}
    static float cooldown(Object dash)throws Exception{return (Float)dash.getClass().getMethod("cooldown").invoke(dash);}
    static void index(){
        Vars.state.teams.updateTeamStats();Groups.unit.tree().clear();
        for(Unit u:Groups.unit)Groups.unit.tree().insert(u);
        for(Team team:new Team[]{Team.sharded,Team.crux,Team.green}){
            team.data().tree().clear();for(Unit u:Groups.unit)if(u.team==team)team.data().tree().insert(u);
        }
    }
    static void world(){
        Vars.logic.reset();Vars.state.rules=new Rules();Vars.state.rules.unitCap=100;Vars.state.rules.canGameOver=false;
        Vars.world.loadGenerator(140,110,tiles->{for(int x=0;x<140;x++)for(int y=0;y<110;y++)tiles.set(x,y,new Tile(x,y,Blocks.stone,Blocks.air,Blocks.air));});
        Vars.state.set(mindustry.core.GameState.State.playing);Time.setDeltaProvider(()->1f);Time.delta=1;
    }
    public static void main(String[]args)throws Exception{try{run();System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
    static void run()throws Exception{
        Core.app=new MockApplication();Core.files=new MockFiles();Core.graphics=new MockGraphics();Core.audio=new MockAudio();
        Core.settings=new Settings();Core.settings.setDataDirectory(new Fi(Files.createTempDirectory("cryon-missile-trucks-").toFile()));
        Core.bundle=I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(Core.settings.getDataDirectory().child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(Core.settings.getDataDirectory().child("mods/unitlanuch.jar"));
        Vars.headless=true;Vars.loadLocales=false;Vars.net=new Net(null);Vars.platform=new mindustry.core.Platform(){};
        mindustry.core.Version.init();Vars.init();Vars.content.createBaseContent();Vars.content.createModContent();Vars.content.init();
        var mod=Vars.mods.locateMod("cryon");check(mod!=null&&!mod.hasContentErrors(),"packaged content errors");

        ClassLoader loader=mod.main.getClass().getClassLoader();
        loader.loadClass("cryon.Content.ShadowMech").getMethod("configureProduction").invoke(null);
        loader.loadClass("cryon.TechTree.CryonTechTree").getMethod("load").invoke(null);
        Timer.instance().clear();



        Vars.logic=new Logic();world();
        Unit owner=Vars.content.unit("cryon-subsonic").spawn(Team.sharded,400,400);owner.rotation=0;
        var weapon=owner.type.weapons.first();var mount=owner.mounts[0];
        var age=mount.getClass().getField("age");var hold=mount.getClass().getField("hold");
        mount.getClass().getField("combo").setInt(mount,0);
        mount.getClass().getField("swingSide").setInt(mount,1);
        var round=new mindustry.entities.bullet.BasicBulletType(0.01f,1);round.lifetime=1000000;
        for(int i=0;i<200;i++)round.create(null,Team.crux,owner.x-40f,owner.y-30f+(i%60),0f);
        Groups.bullet.tree().clear();for(Bullet b:Groups.bullet)Groups.bullet.tree().insert(b);
        var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
        for(int pass=0;pass<4;pass++){
            long alloc=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
            for(int i=0;i<1200;i++){
                age.setFloat(mount,6);hold.setFloat(mount,0);mount.reload=1000;mount.shoot=false;
                weapon.update(owner,mount);
            }
            long elapsed=System.nanoTime()-start,bytes=bean.getThreadAllocatedBytes(thread)-alloc;
            if(pass>0)System.out.printf("MECH_PERF: 200 nearby projectiles, 1200 parry updates: %.2f ms, %.0f bytes/update%n",elapsed/1e6,bytes/1200d);
        }
        check(Groups.bullet.size()==200,"performance fixture unexpectedly parried bullets");
    }
}
