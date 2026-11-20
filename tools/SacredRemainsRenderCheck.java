import arc.*;
import arc.files.Fi;
import arc.graphics.g2d.*;
import arc.scene.Element;
import arc.scene.ui.Dialog;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.entities.units.AIController;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;

/** Actual atlas, three-legged walking, persistent Ichor and artificial-rift render check. */
public class SacredRemainsRenderCheck{
    static Fi data;
    static boolean running;
    static Unit boss;
    static Class<?> content;
    static float[] originalFeet;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void guarded(Runnable task){try{task.run();}catch(Throwable error){Log.err(error);System.exit(1);}}
    static void screenshot(String name){Core.scene.act();Core.scene.draw();ScreenUtils.saveScreenshot(data.child(name));}
    static void test()throws Exception{
        check(!Vars.mods.locateMod("cryon").hasContentErrors(),"Content errors");
        content=Vars.mods.locateMod("cryon").main.getClass().getClassLoader().loadClass("cryon.Content.SacredRemains");
        UnitType type=Vars.content.unit("cryon-sacred-remains");
        for(TextureRegion region:new TextureRegion[]{type.region,type.fullIcon,type.legRegion,type.legBaseRegion,type.jointRegion,type.baseJointRegion,type.footRegion,type.baseRegion,type.weapons.first().region})check(region!=null&&region.found(),"Missing boss sprite: "+region);
        Vars.logic.reset();Vars.state.rules=new Rules();Vars.state.rules.canGameOver=false;Vars.state.rules.waves=false;Vars.state.rules.lighting=false;
        Vars.world.loadGenerator(140,110,tiles->{for(int x=0;x<140;x++)for(int y=0;y<110;y++)tiles.set(x,y,new Tile(x,y,Blocks.basalt,Blocks.air,Blocks.air));});
        Vars.world.tile(20,20).setBlock(Blocks.coreShard,Team.sharded);
        Vars.world.tile(110,90).setBlock(Blocks.coreShard,Team.malis);
        Vars.player.team(Team.sharded);Vars.player.unit(UnitTypes.alpha.spawn(Team.sharded,450,300));
        Vars.logic.play();Vars.ui.hudfrag.shown=false;running=true;
        var rift=(mindustry.entities.Effect)content.getField("rift").get(null);
        Core.camera.position.set(520,420);Core.camera.update();
        rift.at(520,400,0f,Team.malis.color);
        check(Groups.draw.contains(e -> e instanceof EffectState effect&&effect.effect==rift),"Rift effect was culled before camera initialization");
        Timer.schedule(()->guarded(()->screenshot("sacred-remains-rift.png")),1.6f);
        Timer.schedule(()->guarded(()->{
            check(Vars.content.liquid("cryon-ichor").uiIcon.found()&&Vars.content.statusEffect("cryon-ichor-corrosion").uiIcon.found(),"Missing liquid/status icon");
            boss=type.spawn(Team.malis,520,400);boss.rotation=90f;
            boss.controller(new AIController(){@Override public void updateMovement(){boss.moveAt(new arc.math.geom.Vec2(0.3f,0));}});
            boss.apply(StatusEffects.disarmed,100000f);
            originalFeet=new float[6];for(int i=0;i<3;i++){originalFeet[i*2]=((Legsc)boss).legs()[i].base.x;originalFeet[i*2+1]=((Legsc)boss).legs()[i].base.y;}
            Timer.schedule(()->guarded(()->{
                check(((Legsc)boss).legs().length==3,"Wrong leg count");
                int shifted=0;for(int i=0;i<3;i++)if(Math.abs(((Legsc)boss).legs()[i].base.x-originalFeet[i*2])>2f)shifted++;
                check(shifted>0,"Tripod feet never moved");
                boolean puddles=false;for(var e:Groups.all)if(e instanceof Puddle p&&p.liquid.name.equals("cryon-ichor"))puddles=true;
                check(puddles,"No visible Ichor trail");screenshot("sacred-remains-walking.png");
                boss.unapply(StatusEffects.disarmed);
                Unit target=UnitTypes.mace.spawn(Team.sharded,boss.x+100,boss.y+50);target.apply(StatusEffects.unmoving,100000f);target.health=100000f;
                Unit target2=UnitTypes.mace.spawn(Team.sharded,boss.x-80,boss.y-20);target2.apply(StatusEffects.unmoving,100000f);target2.health=100000f;
                Timer.schedule(()->guarded(()->{
                    check(Groups.bullet.count(b -> b.owner==boss)>0,"Boss did not spray from its weapons");
                    screenshot("sacred-remains-spray.png");
                    Log.info("SACRED_REMAINS_RENDER_PASS: packed body/legs/joints/feet/mouth, moving native tripod, Ichor trail, aimed spray, lightning rift");Core.app.exit();
                }),1.8f);
            }),5f);
        }),4f);
    }
    public static void main(String[] args){
        data=Fi.get(System.getProperty("mindustry.data.dir"));Fi repo=Fi.get(System.getProperty("cryon.repo"));
        repo.child("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        repo.child("单位发射台library.jar").copyTo(data.child("mods/unitlaunch.jar"));
        Events.on(ClientLoadEvent.class,event->Timer.schedule(()->guarded(()->{
            for(Element element:Core.scene.root.getChildren().copy())if(element instanceof Dialog dialog){dialog.clearActions();dialog.hide(null);}
            Core.graphics.setFullscreen(false);arc.backend.sdl.jni.SDL.SDL_RestoreWindow(((arc.backend.sdl.SdlApplication)Core.app).getWindow());Core.graphics.setWindowSize(1200,850);
            Timer.schedule(()->guarded(()->{try{test();}catch(Exception error){throw new RuntimeException(error);}}),1f);
        }),3f));
        Events.run(Trigger.update,()->{if(running){Core.camera.position.set(520,420);Vars.player.set(520,420);Vars.renderer.setScale(2.6f);}});
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
