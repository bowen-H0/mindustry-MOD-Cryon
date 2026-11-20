import arc.*;
import arc.files.Fi;
import arc.graphics.g2d.Draw;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.ui.Dialog;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.world.*;

/** Real-GL multi-layer parallax, clipped physical emergence, postprocessing, resize and cleanup. */
public class SacredRiftCinemaCheck{
    static Fi data;
    static boolean running,pan;
    static Building processor;
    static Class<?> renderer;
    static Object script;
    static float centerX,centerY,firstBossX,firstBossY,firstView;
    static boolean sawBoss;
    static int step;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void guarded(Runnable task){try{task.run();}catch(Throwable error){Log.err(error);System.exit(1);}}
    static float field(String name)throws Exception{var f=script.getClass().getDeclaredField(name);f.setAccessible(true);return f.getFloat(script);}
    static Unit boss(){return Groups.unit.find(u->u.type.name.equals("cryon-sacred-remains")&&!u.dead);}
    static void shot(String name){Core.scene.act();Core.scene.draw();ScreenUtils.saveScreenshot(data.child(name));}
    static void test()throws Exception{
        check(!Vars.mods.locateMod("cryon").hasContentErrors(),"Content errors");
        renderer=Vars.mods.locateMod("cryon").main.getClass().getClassLoader().loadClass("cryon.Features.SacredRiftRenderer");
        Vars.logic.reset();Vars.state.rules=new Rules();Vars.state.rules.canGameOver=false;Vars.state.rules.waves=false;Vars.state.rules.lighting=false;Vars.state.rules.attackMode=true;Vars.state.rules.waveTeam=Team.malis;
        Vars.world.loadGenerator(150,120,tiles->{for(int x=0;x<150;x++)for(int y=0;y<120;y++)tiles.set(x,y,new Tile(x,y,(x/5+y/5)%2==0?Blocks.basalt:Blocks.stone,Blocks.air,Blocks.air));});
        Vars.world.tile(25,25).setBlock(Blocks.coreShard,Team.sharded);
        Vars.world.tile(120,100).setBlock(Blocks.coreShard,Team.malis);
        Vars.world.tile(70,60).setBlock(Vars.content.block("cryon-java-processor"),Team.sharded);processor=Vars.world.build(70,60);
        processor.configured(null,"aegis-sacred-remains");script=processor.getClass().getField("script").get(processor);
        Vars.player.team(Team.sharded);Vars.player.unit(UnitTypes.alpha.spawn(Team.sharded,400,300));Vars.logic.play();Vars.ui.hudfrag.shown=false;
        processor.updateTile();Vars.world.build(120,100).kill();processor.updateTile();
        var cursor=script.getClass().getDeclaredField("cursor");cursor.setAccessible(true);cursor.setInt(script,2);
        var timer=script.getClass().getDeclaredField("remaining");timer.setAccessible(true);timer.setFloat(script,0f);
        processor.updateTile();
        centerX=field("spawnX");centerY=field("spawnY");
        Core.camera.position.set(centerX,centerY);Core.camera.update();running=true;
    }
    static void inspect()throws Exception{
        if(!running)return;
        int phase=(int)script.getClass().getMethod("stage").invoke(script);
        float time=phase==2?600f-field("remaining"):700f;
        if(step==0&&time>=90f){
            check((boolean)renderer.getMethod("resourcesReady").invoke(null),"Cinematic shaders failed to compile/capture");
            shot("cinema-01-opening.png");step++;
        }else if(step==1&&time>=210f){
            Unit unit=boss();check(unit!=null,"No emerging physical boss");firstBossX=unit.x;firstBossY=unit.y;sawBoss=true;
            check(unit.controller().getClass().getSimpleName().equals("SacredRemainsArrivalAI"),"Boss used combat AI during entrance");
            shot("cinema-02-first-legs.png");firstView=(float)renderer.getMethod("parallaxX").invoke(null);
            pan=true;Core.graphics.setWindowSize(1100,780);step++;
        }else if(step==2&&time>=360f){
            Unit unit=boss();
            check(sawBoss&&unit.dst(firstBossX,firstBossY)>35f,"Boss did not walk: distance="+unit.dst(firstBossX,firstBossY)+" speed="+unit.speed()+" vel="+unit.vel);
            check(Math.abs((float)renderer.getMethod("parallaxX").invoke(null)-firstView)>.03f,"No camera-driven parallax");
            shot("cinema-03-walking.png");step++;
        }else if(step==3&&time>=510f){shot("cinema-04-exit.png");step++;}
        else if(step==4&&phase==3&&!(boolean)renderer.getMethod("active").invoke(null)){
            check(boss()!=null&&!boss().controller().getClass().getSimpleName().equals("SacredRemainsArrivalAI"),"Entrance AI was not released");
            shot("cinema-05-combat.png");running=false;Vars.logic.reset();
            check(!(boolean)renderer.getMethod("active").invoke(null),"Cinematic leaked across reset");
            Log.info("SACRED_RIFT_CINEMA_PASS: GLSL compile, depth/parallax, clipped legs/body, native walking, parallax on pan, framebuffer resize, combat handoff, cleanup");Core.app.exit();
        }
    }
    public static void main(String[] args){
        data=Fi.get(System.getProperty("mindustry.data.dir"));Fi repo=Fi.get(System.getProperty("cryon.repo"));
        repo.child("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));repo.child("单位发射台library.jar").copyTo(data.child("mods/unitlaunch.jar"));
        Events.on(ClientLoadEvent.class,e->Timer.schedule(()->guarded(()->{
            for(Element element:Core.scene.root.getChildren().copy())if(element instanceof Dialog d){d.clearActions();d.hide(null);}
            Core.graphics.setFullscreen(false);arc.backend.sdl.jni.SDL.SDL_RestoreWindow(((arc.backend.sdl.SdlApplication)Core.app).getWindow());Core.graphics.setWindowSize(1200,850);
            Timer.schedule(()->guarded(()->{try{test();}catch(Exception error){throw new RuntimeException(error);}}),1f);
        }),3f));
        Events.run(Trigger.update,()->{if(running){Core.camera.position.set(centerX+(pan?70f:0f),centerY+(pan?20f:0f));Vars.player.set(centerX,centerY);Vars.renderer.setScale(2.7f);}});
        Events.run(Trigger.postDraw,()->guarded(()->{try{inspect();}catch(Exception error){throw new RuntimeException(error);}}));
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
