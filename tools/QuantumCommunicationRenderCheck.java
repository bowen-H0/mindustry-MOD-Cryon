import arc.*;
import arc.files.Fi;
import arc.scene.ui.*;
import arc.util.*;
import java.lang.reflect.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.GameState.State;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.*;

/** Runs the real desktop UI in a temporary profile and captures each conversation layout. */
public class QuantumCommunicationRenderCheck{
    static Fi dir;
    static Building computer;
    static BaseDialog dialog;
    static ClassLoader loader;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void guarded(Runnable action){try{action.run();}catch(Throwable e){Log.err(e);System.exit(1);}}
    static void later(Runnable action){Timer.schedule(()->guarded(action),0.8f);}
    static void click(String name){Button button=dialog.find(name);check(button!=null&&!button.isDisabled(),"Missing or disabled button: "+name);button.fireClick();}
    static void screenshot(String name){Core.scene.act();Core.scene.draw();ScreenUtils.saveScreenshot(dir.child(name));}
    static void complete(){
        try{
            var method=dialog.getClass().getDeclaredMethod("complete");method.setAccessible(true);
            if(!(boolean)method.invoke(dialog))click("cryon-dialogue-continue");
        }catch(Exception e){throw new RuntimeException(e);}
    }
    static boolean underground(){return Core.settings.getBool("cryon-story-underground",false);}
    public static void main(String[] args){
        dir=new Fi(System.getProperty("mindustry.data.dir"));Fi repo=new Fi(System.getProperty("cryon.repo","."));
        repo.child("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(dir.child("mods/cryon.jar"));
        repo.child("单位发射台library.jar").copyTo(dir.child("mods/unitlanuch.jar"));
        Events.on(ClientLoadEvent.class,event->Timer.schedule(()->guarded(()->{
            Core.graphics.setFullscreen(false);Core.graphics.setWindowSize(1280,900);
            for(var actor:Core.scene.root.getChildren().copy())if(actor instanceof Dialog d){d.clearActions();d.hide(null);}
            Vars.logic.reset();Vars.state.rules=new Rules();
            Vars.world.loadGenerator(80,80,tiles->{for(int x=0;x<80;x++)for(int y=0;y<80;y++)tiles.set(x,y,new Tile(x,y,Blocks.sand,Blocks.air,Blocks.air));});
            Vars.player.team(Team.sharded);Vars.player.unit(UnitTypes.alpha.spawn(Team.sharded,240,200));
            var block=Vars.content.block("cryon-quantum-communication-computer");block.unlock();
            Vars.world.tile(30,30).setBlock(block,Team.sharded);computer=Vars.world.build(30,30);computer.power.status=1;
            loader=Vars.mods.locateMod("cryon").main.getClass().getClassLoader();
            try{
                computer.getClass().getMethod("constructorStatus",float.class).invoke(computer,1f);
                Class<?> type=loader.loadClass("cryon.Story.QuantumCommunicationDialog");
                dialog=(BaseDialog)type.getConstructors()[0].newInstance(computer);
            }catch(Exception e){throw new RuntimeException(e);}
            check(block.region.found(),"Computer sprite missing");
            check(!Vars.content.planet("cryon-porphyra").visible&&!Vars.content.planet("cryon-porphyra").accessible,"Porphyra visible before act two");
            Vars.logic.play();Vars.state.set(State.paused);dialog.show();
            Runnable[] steps={
                ()->{screenshot("quantum-contacts.png");click("cryon-contact-pioneer");},
                ()->complete(),
                ()->{screenshot("quantum-offer.png");click("cryon-choice-refuse");},
                ()->complete(),
                ()->{screenshot("quantum-refusal.png");check(!underground(),"Refusal unlocked underground");click("cryon-dialogue-continue");click("cryon-contact-pioneer");},
                ()->complete(),
                ()->{screenshot("quantum-reconsider.png");check(dialog.find("cryon-choice-listen")==null,"Reconnection restarts first negotiation");click("cryon-choice-agree");},
                ()->complete(),
                ()->{screenshot("quantum-alliance.png");check(underground(),"Reconsidered agreement did not unlock underground");check(!Core.settings.getBool("cryon-story-act-two",false),"Alliance wrongly unlocks act two");click("cryon-dialogue-continue");click("cryon-contact-pioneer");},
                ()->complete(),
                ()->{screenshot("quantum-allied-return.png");check(dialog.find("cryon-choice-agree")==null,"Ally is asked to negotiate again");click("cryon-dialogue-continue");click("cryon-contact-malis");},
                ()->complete(),
                ()->{click("cryon-dialogue-continue");click("cryon-contact-malis");},
                ()->complete(),
                ()->{screenshot("quantum-malis-return.png");click("cryon-dialogue-continue");click("cryon-contact-crux");},
                ()->complete(),
                ()->{click("cryon-dialogue-continue");click("cryon-contact-crux");},
                ()->{check(dialog.find("cryon-dialogue-continue")==null&&dialog.find("cryon-contact-crux")!=null,"Repeat Crux contact does not hang up immediately");click("cryon-contact-pioneer");Core.graphics.setWindowSize(720,1000);},
                ()->complete(),
                ()->{screenshot("quantum-portrait.png");try{computer.getClass().getMethod("constructorStatus",float.class).invoke(computer,0f);}catch(Exception e){throw new RuntimeException(e);}},
                ()->{check(!dialog.isShown(),"Dialog stayed open after losing constructor supply");Log.info("QUANTUM_RENDER_PASS: persistent refusal, reconsidered agreement, allied/Malis greetings, immediate Crux hangup, Sharded avatar and portrait layout");Core.app.exit();}
            };
            int[] index={0};Timer.schedule(()->guarded(steps[index[0]++]),0.8f,0.8f,steps.length-1);
        }),3f));
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
