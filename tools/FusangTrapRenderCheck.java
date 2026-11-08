import arc.*;
import arc.files.Fi;
import arc.scene.ui.Dialog;
import arc.util.*;
import java.nio.file.Files;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.world.Tile;

/** Live cluttered enemy layout: irregular impacts, charging trap and final laser spikes. */
public class FusangTrapRenderCheck{
    static Fi data;static Bullet salvo;static Unit carrier;static int frames,burstFrame=-1;static boolean started,placed,charged,burst;
    public static void main(String[]args)throws Exception{
        data=new Fi(Files.createTempDirectory("cryon-fusang-trap-render-").toFile());
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlanuch.jar"));System.setProperty("mindustry.data.dir",data.absolutePath());
        Events.on(ClientLoadEvent.class,e->Core.app.post(()->guarded(()->{
            Core.graphics.setFullscreen(false);Core.graphics.setWindowSize(1280,900);
            for(var actor:Core.scene.root.getChildren().copy())if(actor instanceof Dialog d){d.clearActions();d.hide(null);}
            Vars.logic.reset();Vars.state.rules=new Rules();Vars.state.rules.canGameOver=false;Vars.state.rules.waves=false;Vars.state.rules.unitCap=100;
            Vars.world.loadGenerator(140,120,tiles->{for(int x=0;x<140;x++)for(int y=0;y<120;y++)tiles.set(x,y,new Tile(x,y,Blocks.stone,Blocks.air,Blocks.air));});
            carrier=Vars.content.unit("cryon-fusang").spawn(Team.sharded,390,390);carrier.rotation=30;
            carrier.apply(StatusEffects.unmoving,100000);carrier.apply(StatusEffects.disarmed,100000);
            float[][] spots={{570,475},{603,490},{630,455},{675,470},{695,500},{603,580},{625,620},{661,570}};
            for(float[] p:spots){Unit u=UnitTypes.reign.spawn(Team.crux,p[0],p[1]);u.health=u.maxHealth=100000;u.apply(StatusEffects.unmoving,100000);u.apply(StatusEffects.disarmed,100000);}
            Vars.world.tile(67,59).setBlock(Blocks.thoriumWall,Team.crux);Vars.world.build(67,59).health=100000;
            Vars.player.team(Team.sharded);Vars.player.unit(carrier);Vars.logic.play();Vars.renderer.setScale(2.1f);started=true;
        })));
        Events.run(Trigger.update,()->{if(!started)return;guarded(()->{
            frames++;Core.camera.position.set(560,500);
            if(frames==15)salvo=carrier.type.weapons.first().bullet.create(carrier,carrier.team,carrier.x,carrier.y,30,-1,1,1,null,null,630,520);
            if(salvo==null)return;
            try{
                Object state=salvo.data;Class<?> type=state.getClass();
                int landed=type.getField("landed").getInt(state);float armed=type.getField("armedAt").getFloat(state);
                boolean detonated=type.getField("detonated").getBoolean(state);
                if(!placed&&landed>=6){capture("placement");placed=true;}
                if(!charged&&armed>=0&&salvo.time-armed>=7&&!detonated){capture("charge");charged=true;}
                if(detonated&&burstFrame<0)burstFrame=frames;
                if(burstFrame>=0&&!burst&&frames-burstFrame>=13){capture("spikes");burst=true;}
                if(burst&&frames-burstFrame>18){Log.info("FUSANG_TRAP_RENDER_PASS: placement, charge and heavy laser spikes");Core.app.exit();}
                if(frames>250)throw new AssertionError("trap did not finish naturally");
            }catch(ReflectiveOperationException error){throw new RuntimeException(error);}
        });});
        mindustry.desktop.DesktopLauncher.main(args);
    }
    static void capture(String name){ScreenUtils.saveScreenshot(data.child("fusang-"+name+".png"));Log.info("FUSANG_TRAP_SCREENSHOT @",data.child("fusang-"+name+".png"));}
    static void guarded(Runnable r){try{r.run();}catch(Throwable t){Log.err(t);System.exit(1);}}
}
