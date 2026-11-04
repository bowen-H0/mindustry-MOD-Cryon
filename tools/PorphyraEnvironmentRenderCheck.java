import arc.*;
import arc.files.Fi;
import arc.util.*;
import mindustry.*;
import mindustry.game.EventType.*;

/** Opens the packaged first campaign map in the actual desktop renderer. */
public class PorphyraEnvironmentRenderCheck{
    static boolean ready;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        Fi dir=new Fi(System.getProperty("mindustry.data.dir"));
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(dir.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(dir.child("mods/unitlanuch.jar"));
        Events.on(ClientLoadEvent.class,event->Core.app.post(()->{
            try{
                Core.graphics.setFullscreen(false);Core.graphics.setWindowSize(1300,850);
                for(var element:Core.scene.root.getChildren().copy())if(element instanceof arc.scene.ui.Dialog dialog)dialog.hide();
                var preset=Vars.content.sector("cryon-porphyra-landing");
                Vars.logic.reset();Vars.state.rules.sector=preset.sector;Vars.state.rules.planet=preset.planet;
                Vars.world.loadGenerator(192,192,tiles->preset.generator.generate(tiles,new mindustry.world.WorldParams()));
                Vars.state.rules.waves=Vars.state.rules.waveTimer=Vars.state.rules.canGameOver=false;
                Vars.state.rules.lighting=false;Vars.state.rules.weather.clear();
                for(String name:new String[]{"gamma-brine","gamma-brine-deep","crystal-slurry","crystal-slurry-deep"}){
                    var floor=Vars.content.block("cryon-porphyra-"+name).asFloor();
                    check(floor.variantRegions!=null&&floor.variantRegions.length==2
                            && floor.variantRegions[0].found()&&floor.variantRegions[1].found(),"Missing varied liquid floor sprite: "+name);
                    check(floor.cacheLayer==mindustry.graphics.CacheLayer.water,"Lava shader still assigned: "+name);
                }
                var pump=Vars.content.block("cryon-porphyra-shore-pump");
                check(pump.region!=null&&pump.region.found(),"Missing pump sprite");
                Vars.player.team(Vars.state.rules.defaultTeam);Vars.logic.play();ready=true;
                Timer.schedule(()->{
                    verifySlurryPixels();
                    ScreenUtils.saveScreenshot(dir.child("basins.png"));
                    Log.info("PORPHYRA_ENVIRONMENT_RENDER_PASS: actual packaged campaign map with four liquid floor sprites and pump texture");
                    Core.app.exit();
                },4f);
            }catch(Throwable error){Log.err(error);System.exit(1);}
        }));
        Events.run(Trigger.update,()->{if(ready){Core.camera.position.set(768,768);Vars.renderer.setScale(1f);}});
        mindustry.desktop.DesktopLauncher.main(args);
    }
    static void verifySlurryPixels(){
        int samples=0,blue=0,green=0,orange=0;
        for(var tile:Vars.world.tiles){
            if(tile.floor().liquidDrop!=Vars.content.liquid("cryon-crystal-slurry") || (tile.x+tile.y)%5!=0)continue;
            var screen=Core.camera.project(tile.worldx(),tile.worldy());
            int x=(int)screen.x,y=(int)screen.y;
            if(x<0||y<0||x>=Core.graphics.getWidth()||y>=Core.graphics.getHeight())continue;
            var pixel=ScreenUtils.getFrameBufferPixmap(x,y,1,1);int rgba=pixel.get(0,0);pixel.dispose();
            int r=(rgba>>>24)&255,g=(rgba>>>16)&255,b=(rgba>>>8)&255;
            if(r>b*1.25f && g>b*1.15f)orange++;
            blue+=b;green+=g;samples++;
        }
        check(samples>20,"No visible slurry samples in the game frame");
        check(orange==0 && blue>green+samples*10,"Slurry still contains orange lava patches or lost its violet palette");
        Log.info("LIQUID_SHADER_PASS: @ surface samples, orange lava pixels=@",samples,orange);
    }

}
