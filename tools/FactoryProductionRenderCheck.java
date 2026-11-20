import arc.*;
import arc.files.Fi;
import arc.scene.ui.Dialog;
import arc.util.*;
import java.nio.file.Files;
import mindustry.*;
import mindustry.content.*;
import mindustry.entities.Effect;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.world.Tile;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.consumers.*;

/** Native GL smoke check for the four production overlays, process particles and completion effects. */
public class FactoryProductionRenderCheck{
    static Fi data;static int frames;static boolean started;
    static GenericCrafter.GenericCrafterBuild[] builds=new GenericCrafter.GenericCrafterBuild[4];
    public static void main(String[]args)throws Exception{
        data=new Fi(Files.createTempDirectory("cryon-factory-fx-").toFile());
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlaunch.jar"));
        System.setProperty("mindustry.data.dir",data.absolutePath());
        Events.on(ClientLoadEvent.class,e->Core.app.post(()->guarded(()->{
            Core.graphics.setFullscreen(false);Core.graphics.setWindowSize(1280,900);
            for(var actor:Core.scene.root.getChildren().copy())if(actor instanceof Dialog d){d.clearActions();d.hide(null);}
            Vars.logic.reset();Vars.state.rules=new Rules();Vars.state.rules.canGameOver=false;Vars.state.rules.waves=false;
            Vars.world.loadGenerator(140,120,tiles->{for(int x=0;x<140;x++)for(int y=0;y<120;y++)tiles.set(x,y,new Tile(x,y,Blocks.stone,Blocks.air,Blocks.air));});
            Vars.world.tile(30,30).setBlock(Blocks.powerSource,Team.sharded);
            Building power=Vars.world.build(30,30);power.enabled=true;
            String[]names={"gamma-crystallizer","perfect-lattice-assembler","superphase-synthesizer","cryo-alloy-stabilizer"};
            int[][]positions={{45,65},{75,65},{45,40},{75,40}};
            for(int i=0;i<4;i++){
                GenericCrafter block=(GenericCrafter)Vars.content.block("cryon-"+names[i]);
                if(!block.region.found()||!Core.atlas.find(block.name+"-glow").found()||block.craftEffect==Fx.none||block.updateEffect==Fx.none)
                    throw new AssertionError("missing production sprite/effect: "+names[i]);
                Vars.world.tile(positions[i][0],positions[i][1]).setBlock(block,Team.sharded);
                var build=(GenericCrafter.GenericCrafterBuild)Vars.world.build(positions[i][0],positions[i][1]);builds[i]=build;
                build.enabled=true;build.wasVisible=true;build.progress=.95f;
                for(Consume consume:block.consumers){
                    if(consume instanceof ConsumeItems items)for(var stack:items.items)build.items.add(stack.item,Math.min(block.itemCapacity,stack.amount*3));
                    if(consume instanceof ConsumeLiquid liquid)build.liquids.set(liquid.liquid,block.liquidCapacity*.9f);
                }
                power.power.graph.add(build);
            }
            Vars.player.team(Team.sharded);Vars.logic.play();Vars.renderer.setScale(3f);started=true;
        })));
        Events.run(Trigger.update,()->{
            if(!started)return;
            guarded(()->{
                frames++;Core.camera.position.set(480,420);Vars.renderer.setScale(3f);
                if(frames<110){
                    for(var b:builds)b.power.status=1;
                    try{builds[2].getClass().getMethod("constructorStatus",float.class).invoke(builds[2],1f);}
                    catch(ReflectiveOperationException error){throw new RuntimeException(error);}
                }
                if(frames==110)for(var b:builds)b.enabled=false;
                if(frames==30||frames==55||frames==90||frames==185){
                    ScreenUtils.saveScreenshot(data.child("factory-production-"+frames+".png"));
                    Log.info("FACTORY_FX screenshot=@",data.child("factory-production-"+frames+".png"));
                }
                if(frames==200){
                    for(var b:builds)if(b.efficiency!=0)throw new AssertionError("disabled factory still active");
                    Log.info("FACTORY_FX_PASS: four native overlays, animated process/completion effects, atlas assets and disabled shutdown");Core.app.exit();
                }
            });
        });
        mindustry.desktop.DesktopLauncher.main(args);
    }
    static void guarded(Runnable action){try{action.run();}catch(Throwable error){Log.err(error);System.exit(1);}}
}
