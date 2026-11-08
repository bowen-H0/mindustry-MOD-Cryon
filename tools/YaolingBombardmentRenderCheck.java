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

/** Real multi-target guidance flares, falling bombs and saturation blasts. */
public class YaolingBombardmentRenderCheck{
    static mindustry.entities.bullet.BulletType flare,bomb;static Fi data;static Unit carrier;static Object strike;static int frames,firstRelease=-1;static boolean started,signals,falling;
    public static void main(String[]args)throws Exception{
        data=new Fi(Files.createTempDirectory("cryon-yaoling-render-").toFile());
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlanuch.jar"));System.setProperty("mindustry.data.dir",data.absolutePath());
        Events.on(ClientLoadEvent.class,e->Core.app.post(()->guarded(()->{
            Core.graphics.setFullscreen(false);Core.graphics.setWindowSize(1280,900);
            for(var actor:Core.scene.root.getChildren().copy())if(actor instanceof Dialog d){d.clearActions();d.hide(null);}
            Vars.logic.reset();Vars.state.rules=new Rules();Vars.state.rules.canGameOver=false;Vars.state.rules.waves=false;Vars.state.rules.unitCap=100;
            Vars.world.loadGenerator(150,140,tiles->{for(int x=0;x<150;x++)for(int y=0;y<140;y++)tiles.set(x,y,new Tile(x,y,Blocks.stone,Blocks.air,Blocks.air));});
            carrier=Vars.content.unit("cryon-yaoling").spawn(Team.sharded,350,380);carrier.rotation=40;
            try{
                Object attack=carrier.type.weapons.first().bullet;
                flare=(mindustry.entities.bullet.BulletType)attack.getClass().getField("flare").get(attack);
                bomb=(mindustry.entities.bullet.BulletType)attack.getClass().getField("bomb").get(attack);
            }catch(ReflectiveOperationException error){throw new RuntimeException(error);}
            carrier.apply(StatusEffects.unmoving,100000);carrier.apply(StatusEffects.disarmed,100000);
            Vars.world.tile(81,62).setBlock(Blocks.coreShard,Team.crux);Vars.world.build(81,62).health=100000;
            Vars.world.tile(65,85).setBlock(Blocks.spectre,Team.crux);Vars.world.build(65,85).health=100000;
            Vars.world.tile(60,54).setBlock(Blocks.thoriumWall,Team.crux);Vars.world.build(60,54).health=100000;
            float[][] spots={{650,645},{625,675},{680,600}};
            for(float[] p:spots){Unit u=UnitTypes.reign.spawn(Team.crux,p[0],p[1]);u.health=u.maxHealth=100000;u.apply(StatusEffects.unmoving,100000);u.apply(StatusEffects.disarmed,100000);}
            Vars.player.team(Team.sharded);Vars.player.unit(carrier);Vars.logic.play();Vars.renderer.setScale(1.9f);started=true;
        })));
        Events.run(Trigger.update,()->{if(!started)return;guarded(()->{
            frames++;Core.camera.position.set(535,535);
            if(frames==15)strike=carrier.type.weapons.first().bullet.create(carrier,carrier.team,carrier.x,carrier.y,40,-1,1,1,null,null,650,500).data;
            if(strike==null)return;
            try{
                Class<?> type=strike.getClass();int faded=type.getField("faded").getInt(strike),impacts=type.getField("impacts").getInt(strike);
                for(Bullet b:Groups.bullet){
                    if(!signals&&b.type==flare&&faded==0&&b.data.getClass().getField("arrived").getBoolean(b.data)){
                        capture("signals");signals=true;
                    }
                    if(!falling&&b.type==bomb){
                        float delay=b.data.getClass().getField("delay").getFloat(b.data);
                        if(b.time-delay>=12&&b.time-delay<32){capture("falling");falling=true;}
                    }
                }
                if(impacts>=32){capture("saturation");Log.info("YAOLING_RENDER_PASS: collision-free flares and 32 falling bombs across strategic targets");Core.app.exit();}
                if(frames>300)throw new AssertionError("multi-target bombardment did not finish");
            }catch(ReflectiveOperationException error){throw new RuntimeException(error);}
        });});
        mindustry.desktop.DesktopLauncher.main(args);
    }
    static void capture(String name){ScreenUtils.saveScreenshot(data.child("yaoling-"+name+".png"));Log.info("YAOLING_SCREENSHOT @",data.child("yaoling-"+name+".png"));}
    static void guarded(Runnable r){try{r.run();}catch(Throwable t){Log.err(t);System.exit(1);}}
}
