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
import mindustry.type.*;
import mindustry.world.Tile;

/** Real GL atlas/weapon smoke check and deterministic screenshots, isolated from campaign saves. */
public class MissileTruckRenderCheck{
    static Fi data;static Unit[] units=new Unit[5];static int frames;static boolean started;
    public static void main(String[]args)throws Exception{
        data=new Fi(Files.createTempDirectory("cryon-missile-render-").toFile());
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlanuch.jar"));
        System.setProperty("mindustry.data.dir",data.absolutePath());
        Events.on(ClientLoadEvent.class,e->Core.app.post(()->guarded(()->{
            Core.graphics.setFullscreen(false);Core.graphics.setWindowSize(1280,900);
            for(var actor:Core.scene.root.getChildren().copy())if(actor instanceof Dialog d){d.clearActions();d.hide(null);}
            Vars.logic.reset();Vars.state.rules=new Rules();Vars.state.rules.canGameOver=false;Vars.state.rules.waves=false;Vars.state.rules.unitCap=100;
            Vars.world.loadGenerator(160,140,tiles->{for(int x=0;x<160;x++)for(int y=0;y<140;y++)tiles.set(x,y,new Tile(x,y,Blocks.stone,Blocks.air,Blocks.air));});
            String[] names={"jinwu","xihe","fusang","yaoling","zhulong"};
            for(int i=0;i<5;i++){
                UnitType type=Vars.content.unit("cryon-"+names[i]);
                if(!type.region.found()||!type.weapons.first().region.found()||!type.treadRegions[0][3].found())throw new AssertionError("missing carrier/launcher/tread sprite: "+names[i]);
                units[i]=type.spawn(Team.sharded,320+i*120,380);units[i].rotation=90;
                units[i].apply(StatusEffects.unmoving,100000);units[i].apply(StatusEffects.disarmed,100000);
            }
            Vars.player.team(Team.sharded);Vars.player.unit(units[2]);Vars.logic.play();
            Vars.renderer.setScale(1.5f);started=true;
        })));
        Events.run(Trigger.update,()->{
            if(!started)return;
            guarded(()->{
                frames++;Core.camera.position.set(580,520);
                if(frames==15){
                    for(int i=0;i<5;i++){
                        Unit u=units[i];float targetX=320+i*120,targetY=i==0?510:i==1?570:650;
                        if(i<2){ Vars.world.tileWorld(targetX,targetY).setBlock(Blocks.thoriumWall,Team.crux); Vars.world.buildWorld(targetX,targetY).health=100000; }
                        u.type.weapons.first().bullet.create(u,u.team,u.x,u.y,90,-1,1,1,null,null,targetX,targetY);
                    }
                }
                if(frames==65){
                    try{
                        Object type=units[0].type.weapons.first().bullet;
                        Object fragments=type.getClass().getField("fragment").get(type);
                        if(!Groups.bullet.contains(b->b.type==fragments))throw new AssertionError("native building collision did not release Jinwu fragments");
                    }catch(ReflectiveOperationException error){throw new RuntimeException(error);}
                }
                if(frames==20||frames==30||frames==50||frames==65||frames==115||frames==205){
                    ScreenUtils.saveScreenshot(data.child("missile-carriers-"+frames+".png"));
                    Log.info("MISSILE_RENDER screenshot=@",data.child("missile-carriers-"+frames+".png"));
                }
                if(frames==230){Log.info("MISSILE_RENDER_PASS: all five body/launcher/tread atlases and live attack effects");Core.app.exit();}
            });
        });
        mindustry.desktop.DesktopLauncher.main(args);
    }
    static void guarded(Runnable r){try{r.run();}catch(Throwable t){Log.err(t);System.exit(1);}}
}
