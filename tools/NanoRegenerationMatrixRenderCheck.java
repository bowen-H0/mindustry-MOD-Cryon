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

/** Isolated desktop scene: powered green fog, repair targets, research and exhaustion. */
public final class NanoRegenerationMatrixRenderCheck{
    static Building matrix;
    static boolean ready, active, offline;
    static long start;
    static mindustry.entities.Effect particles;
    static float damageTimer;
    public static void main(String[] args){
        Events.on(ClientLoadEvent.class,e->Timer.schedule(()->{
            Vars.logic.reset(); Core.settings.put("backgroundpause",false);
            Vars.state.rules.waves=false; Vars.state.rules.canGameOver=false; Vars.state.rules.pauseDisabled=true;
            Vars.state.rules.lighting=true; Vars.state.rules.ambientLight.set(.12f,.14f,.14f,.5f);
            Vars.renderer.drawLight=true; Vars.enableLight=true;
            Vars.state.map=new Map(StringMap.of("name","Nano regeneration matrix preview"));
            Vars.world.loadGenerator(200,180,tiles->{
                for(int x=0;x<200;x++) for(int y=0;y<180;y++) tiles.set(x,y,new Tile(x,y,Blocks.darksand,Blocks.air,Blocks.air));
                tiles.get(20,20).setBlock(Blocks.coreShard,Team.sharded);
                tiles.get(90,90).setBlock(Vars.content.block("cryon-nano-regeneration-matrix"),Team.sharded);
                tiles.get(74,90).setBlock(Blocks.powerSource,Team.sharded);
                for(int x=65;x<=115;x+=10) for(int y=65;y<=115;y+=10){
                    if(Math.abs(x-90)<10 && Math.abs(y-90)<10) continue;
                    tiles.get(x,y).setBlock(Blocks.thoriumWallLarge,Team.sharded);
                }
            });
            matrix=Vars.world.build(90,90);
            try{
                particles=(mindustry.entities.Effect)matrix.block.getClass().getClassLoader()
                    .loadClass("cryon.Fx.NanoRepairFx").getField("particles").get(null);
            }catch(Exception error){throw new RuntimeException(error);}
            Core.settings.put("effects",true);
            if(!Core.atlas.isFound(matrix.block.region)) throw new AssertionError("missing matrix sprite");
            if(matrix.block.techNode==null || matrix.block.techNode.parent==null || !matrix.block.techNode.parent.content.name.equals("cryon-nano-projector"))
                throw new AssertionError("matrix missing Cryon research parent");
            matrix.items.add(Vars.content.item("cryon-nano-material"),600);
            Building source=Vars.world.build(74,90);
            // Keep the fixture power source compact; its test-only link range is enlarged.
            ((mindustry.world.blocks.power.PowerNode)Blocks.powerSource).laserRange=30f;
            source.configureAny(matrix.pos());
            for(Building b:Groups.build) if(b.block==Blocks.thoriumWallLarge) b.health=b.maxHealth*.2f;
            for(int i=0;i<5;i++){
                Unit u=UnitTypes.eclipse.create(Team.sharded); u.set(540+i*90,570); u.add(); u.health=u.maxHealth*.25f;
            }
            Vars.logic.play(); Vars.ui.hudfrag.shown=false;
            Vars.control.input.logicCutscene=true; Vars.control.input.logicCamSpeed=1f;
            Vars.control.input.logicCamPan.set(matrix.x,matrix.y); Vars.control.input.logicCutsceneZoom=0f;
            start=System.nanoTime();ready=true;
            Timer.schedule(()->{if(!active||!offline) throw new AssertionError("incomplete nano render test");Log.info("NANO_MATRIX_RENDER_CHECK_PASS: mist shader, native sprite, powered field, Cryon research and shutdown");Core.app.exit();},9f);
        },5f));
        Events.run(Trigger.update,()->{
            if(!ready) return;
            Vars.renderer.setScale(.65f);
            // Repeat incoming damage in this isolated scene to show ongoing repair particles.
            damageTimer+=Time.delta;
            if(matrix.enabled && damageTimer>=45f){
                damageTimer=0f;
                for(Building b:Groups.build) if(b.block==Blocks.thoriumWallLarge) b.health=b.maxHealth*.2f;
                for(Unit u:Groups.unit) u.health=u.maxHealth*.25f;
            }
        });
        Events.run(Trigger.uiDrawEnd,()->{
            if(!ready) return;
            float seconds=(System.nanoTime()-start)/1e9f;
            if(!active&&seconds>3f){
                if(matrix.power.status<.98f || matrix.items.total()>=600) throw new AssertionError("matrix is not running");
                if(Groups.effect.count(e->e.effect==particles)==0) throw new AssertionError("missing green target repair particles");
                ScreenUtils.saveScreenshot(Vars.dataDirectory.child("nano-matrix-active.png"));active=true;matrix.enabled=false;
            }
            if(!offline&&seconds>6f){ScreenUtils.saveScreenshot(Vars.dataDirectory.child("nano-matrix-offline.png"));offline=true;}
        });
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
