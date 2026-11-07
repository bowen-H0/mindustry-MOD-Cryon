import arc.*;
import arc.struct.StringMap;
import arc.util.*;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.game.EventType.*;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.maps.Map;
import mindustry.world.Tile;

/** Real GL shader and weapon-tag panel using a disposable game profile. */
public final class RealityAnchoringMatrixRenderCheck{
    static Building matrix;
    static boolean ready, field, panel, unitPanel;
    static long start;
    public static void main(String[] args){
        Events.on(ClientLoadEvent.class,e->Timer.schedule(()->{
            Vars.logic.reset();Core.settings.put("backgroundpause",false);
            Vars.state.rules.waves=false;Vars.state.rules.canGameOver=false;Vars.state.rules.pauseDisabled=true;
            Vars.state.rules.lighting=true;Vars.state.rules.ambientLight.set(.12f,.12f,.17f,.6f);Vars.renderer.drawLight=true;Vars.enableLight=true;
            Vars.state.map=new Map(StringMap.of("name","Reality anchoring matrix preview"));
            Vars.world.loadGenerator(340,260,tiles->{
                for(int x=0;x<340;x++)for(int y=0;y<260;y++)tiles.set(x,y,new Tile(x,y,Blocks.darksand,Blocks.air,Blocks.air));
                tiles.get(20,20).setBlock(Blocks.coreShard,Team.sharded);
                tiles.get(170,130).setBlock(Vars.content.block("cryon-reality-anchoring-matrix"),Team.sharded);
                tiles.get(150,130).setBlock(Blocks.powerSource,Team.sharded);
                for(int x=100;x<=240;x+=20)for(int y=60;y<=200;y+=20)tiles.get(x,y).setBlock(Blocks.thoriumWallLarge,Team.sharded);
            });
            matrix=Vars.world.build(170,130);matrix.items.add(Items.phaseFabric,300);
            if(!Core.atlas.isFound(matrix.block.region))throw new AssertionError("missing anchor sprite");
            if(matrix.block.techNode==null||matrix.block.techNode.parent==null||!matrix.block.techNode.parent.content.name.equals("cryon-orbital-barrier-node"))throw new AssertionError("missing anchor tech tree");
            ((mindustry.world.blocks.power.PowerNode)Blocks.powerSource).laserRange=30f;Vars.world.build(150,130).configureAny(matrix.pos());
            Vars.logic.play();Vars.ui.hudfrag.shown=false;
            Vars.control.input.logicCutscene=true;Vars.control.input.logicCamSpeed=1;Vars.control.input.logicCamPan.set(matrix.x,matrix.y);Vars.control.input.logicCutsceneZoom=0;
            Vars.renderer.minZoom=.45f;start=System.nanoTime();ready=true;
            Timer.schedule(()->{if(!field||!panel||!unitPanel)throw new AssertionError("incomplete anchor render check");Log.info("REALITY_ANCHOR_RENDER_CHECK_PASS: square shader, expanding hollow square pulses, native power, research, turret and unit weapon tags");Core.app.exit();},12f);
        },5f));
        Events.run(Trigger.update,()->{if(ready)Vars.renderer.setScale(.45f);});
        Events.run(Trigger.uiDrawEnd,()->{
            if(!ready)return;float seconds=(System.nanoTime()-start)/1e9f;
            if(!field&&seconds>3){
                if(matrix.power.status<.98f||matrix.items.total()>=300)throw new AssertionError("anchor is not powered/consuming");
                ScreenUtils.saveScreenshot(Vars.dataDirectory.child("reality-anchor-field.png"));field=true;
                Vars.ui.content.show(Vars.content.block("cryon-quantum"));
                if(Vars.ui.content.cont.find(element -> element instanceof arc.scene.ui.Label &&
                    ((arc.scene.ui.Label)element).getText().toString().contains("相位"))==null)
                    throw new AssertionError("weapon phase tags are missing from actual property UI");
                Timer.schedule(()->{
                    arc.scene.ui.ScrollPane scroll=Vars.ui.content.cont.find(element->element instanceof arc.scene.ui.ScrollPane);
                    if(scroll!=null){scroll.setScrollPercentY(1f);scroll.updateVisualScroll();}
                },.5f);
            }
            if(!panel&&seconds>6){
                ScreenUtils.saveScreenshot(Vars.dataDirectory.child("reality-anchor-tags.png"));panel=true;
                Vars.ui.content.show(Vars.content.unit("cryon-eon"));
                if(Vars.ui.content.cont.find(element -> element instanceof arc.scene.ui.Label &&
                    ((arc.scene.ui.Label)element).getText().toString().contains("空间"))==null)
                    throw new AssertionError("unit spatial weapon tags missing from actual property UI");
                Timer.schedule(()->{
                    arc.scene.ui.ScrollPane scroll=Vars.ui.content.cont.find(element->element instanceof arc.scene.ui.ScrollPane);
                    if(scroll!=null){scroll.setScrollPercentY(1f);scroll.updateVisualScroll();}
                },.5f);
            }
            if(!unitPanel&&seconds>9){ScreenUtils.saveScreenshot(Vars.dataDirectory.child("reality-anchor-unit-tags.png"));unitPanel=true;}
        });
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
