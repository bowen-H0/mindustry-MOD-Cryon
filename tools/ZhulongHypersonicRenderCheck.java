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

/** Real moving hypersonic projectile and heavy conventional warhead visual check. */
public class ZhulongHypersonicRenderCheck{
    static Fi data;static Unit carrier;static Bullet missile;static int frames,impactFrame=-1;static boolean started,flight;
    public static void main(String[]args)throws Exception{
        data=new Fi(Files.createTempDirectory("cryon-zhulong-render-").toFile());
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlanuch.jar"));System.setProperty("mindustry.data.dir",data.absolutePath());
        Events.on(ClientLoadEvent.class,e->Core.app.post(()->guarded(()->{
            Core.graphics.setFullscreen(false);Core.graphics.setWindowSize(1280,900);
            for(var actor:Core.scene.root.getChildren().copy())if(actor instanceof Dialog d){d.clearActions();d.hide(null);}
            Vars.logic.reset();Vars.state.rules=new Rules();Vars.state.rules.canGameOver=false;Vars.state.rules.waves=false;Vars.state.rules.unitCap=100;
            Vars.world.loadGenerator(300,120,tiles->{for(int x=0;x<300;x++)for(int y=0;y<120;y++)tiles.set(x,y,new Tile(x,y,Blocks.stone,Blocks.air,Blocks.air));});
            carrier=Vars.content.unit("cryon-zhulong").spawn(Team.sharded,350,480);carrier.rotation=0;
            carrier.apply(StatusEffects.unmoving,100000);carrier.apply(StatusEffects.disarmed,100000);
            Unit target=UnitTypes.reign.spawn(Team.crux,1850,480);target.health=target.maxHealth=100000;
            target.apply(StatusEffects.unmoving,100000);target.apply(StatusEffects.disarmed,100000);
            Vars.player.team(Team.sharded);Vars.player.unit(carrier);Vars.logic.play();Vars.renderer.setScale(2.2f);started=true;
        })));
        Events.run(Trigger.update,()->{if(!started)return;guarded(()->{
            frames++;if(missile==null)Core.camera.position.set(350,480);
            if(frames==15)missile=carrier.type.weapons.first().bullet.create(carrier,carrier.team,350,480,0,-1,1,1,null,null,1850,480);
            if(missile==null)return;
            carrier.set(missile.x,480);
            if(missile.isAdded()){
                Core.camera.position.set(missile.x,480);
                if(!flight&&missile.time>=16){capture("flight");flight=true;}
            }else{
                Core.camera.position.set(missile.x,480);
                if(impactFrame<0)impactFrame=frames;
                if(frames-impactFrame==12){capture("impact");if(!flight)throw new AssertionError("missed flight capture");Log.info("ZHULONG_RENDER_PASS: hypersonic flight, Mach cone, sonic clouds and heavy warhead");Core.app.exit();}
            }
            if(frames>180)throw new AssertionError("hypersonic missile did not finish");
        });});
        mindustry.desktop.DesktopLauncher.main(args);
    }
    static void capture(String name){ScreenUtils.saveScreenshot(data.child("zhulong-"+name+".png"));Log.info("ZHULONG_SCREENSHOT @",data.child("zhulong-"+name+".png"));}
    static void guarded(Runnable r){try{r.run();}catch(Throwable t){Log.err(t);System.exit(1);}}
}
