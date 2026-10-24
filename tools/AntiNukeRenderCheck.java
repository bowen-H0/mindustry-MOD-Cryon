import arc.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.world.Tile;

/** Isolated desktop GL check: a remote processor must not hide the silo's launch effect. */
public class AntiNukeRenderCheck{
    static Building silo;
    static Object nuke;
    static int captured;
    static float age;
    static float value(Object object,String field)throws Exception{return object.getClass().getField(field).getFloat(object);}
    public static void main(String[] args){
        Events.on(ClientLoadEvent.class,event->Timer.schedule(()->{
            try{
                Core.graphics.setFullscreen(false);Core.graphics.setWindowSize(1000,700);
                for(var element:Core.scene.root.getChildren().copy())
                    if(element instanceof arc.scene.ui.Dialog) ((arc.scene.ui.Dialog)element).hide();
                Vars.logic.reset();Rules rules=new Rules();
                rules.waves=rules.waveTimer=rules.canGameOver=false;rules.unitCap=100;
                Vars.state.rules=rules;
                Vars.world.loadGenerator(128,128,tiles->{
                    for(int x=0;x<128;x++)for(int y=0;y<128;y++)tiles.set(x,y,new Tile(x,y,Blocks.sand,Blocks.air,Blocks.air));
                });
                Vars.world.tile(64,64).setBlock(Vars.content.block("cryon-disarmament"),Team.sharded);
                silo=Vars.world.tile(64,64).build;
                Vars.world.tile(61,64).setBlock(Blocks.powerSource,Team.sharded);
                Vars.world.tile(15,15).setBlock(Vars.content.block("cryon-java-processor"),Team.crux);
                Building processor=Vars.world.tile(15,15).build;
                ItemStackFill.fill(silo);
                silo.getClass().getField("charge").setFloat(silo,1800f);
                Class<?> strikeClass=silo.getClass().getClassLoader().loadClass("cryon.Script.NukeStrikeScript");
                nuke=strikeClass.getConstructor().newInstance();
                strikeClass.getMethod("attach",processor.getClass()).invoke(nuke,processor);
                strikeClass.getField("phase").setInt(nuke,3);
                strikeClass.getField("tx").setFloat(nuke,512f);strikeClass.getField("ty").setFloat(nuke,576f);
                strikeClass.getField("blastRadius").setFloat(nuke,240f);
                processor.getClass().getField("script").set(processor,nuke);
                Vars.player.team(Team.sharded);Vars.logic.play();Vars.renderer.setScale(2.5f);
                Timer.schedule(()->{
                    if(captured!=3)throw new AssertionError("Missing launch animation frames: "+captured);
                    Log.info("ANTI_NUKE_ANIMATION_PASS: progressive hatch opening and independently rendered launch");Core.app.exit();
                },6f);
            }catch(Exception error){throw new RuntimeException(error);}
        },2f));
        Events.run(Trigger.update,()->{
            if(silo!=null){Core.camera.position.set(512f,555f);Vars.renderer.setScale(2.5f);}
        });
        Events.run(Trigger.uiDrawEnd,()->{
            if(nuke==null)return;
            try{
                float timer=value(nuke,"fallTimer");
                if((captured==0&&timer>=8f)||(captured==1&&timer>=35f)||(captured==2&&timer>=110f)){
                    if(captured==1 && value(silo,"door")<0.95f)throw new AssertionError("Hatch did not open");
                    if(captured==2 && !nuke.getClass().getField("intercepting").getBoolean(nuke))
                        throw new AssertionError("Interceptor did not launch");
                    ScreenUtils.saveScreenshot(Vars.dataDirectory.child("anti-nuke-"+captured+".png"));
                    Log.info("Animation frame @ fall=@ hatch=@",captured,timer,value(silo,"door"));captured++;
                }
            }catch(Exception error){throw new RuntimeException(error);}
        });
        mindustry.desktop.DesktopLauncher.main(args);
    }
    static class ItemStackFill{
        static void fill(Building silo){
            silo.items.add(Items.silicon,160);silo.items.add(Items.surgeAlloy,80);silo.items.add(Items.phaseFabric,40);
            silo.items.add(Vars.content.item("cryon-neutronite"),60);
        }
    }
}
