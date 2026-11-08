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

/** Real desktop atlas and automatic-dash/chromatic-history smoke test with isolated data. */
public class SupersonicRenderCheck{
    static Fi data;
    static Unit mech;
    static int frames, endedAt=-1, nightAt=-1;
    static boolean started, onset, moving, fading, faded, night;
    public static void main(String[] args)throws Exception{
        data=new Fi(Files.createTempDirectory("cryon-supersonic-render-").toFile());
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlanuch.jar"));
        System.setProperty("mindustry.data.dir",data.absolutePath());
        Events.on(ClientLoadEvent.class,e->Core.app.post(()->guarded(()->{
            Core.graphics.setFullscreen(false);Core.graphics.setWindowSize(1100,760);
            for(var actor:Core.scene.root.getChildren().copy())if(actor instanceof Dialog d){d.clearActions();d.hide(null);}
            Vars.logic.reset();Vars.state.rules=new Rules();Vars.state.rules.canGameOver=false;Vars.state.rules.waves=false;Vars.state.rules.unitCap=100;
            Vars.world.loadGenerator(90,90,tiles->{for(int x=0;x<90;x++)for(int y=0;y<90;y++)tiles.set(x,y,new Tile(x,y,Blocks.stone,Blocks.air,Blocks.air));});
            var type=Vars.content.unit("cryon-supersonic");
            if(type==null||!type.region.found()||!type.baseRegion.found()||!type.legRegion.found())throw new AssertionError("missing T3 body atlas");
            for(String part:new String[]{"upperArmRegion","forearmRegion","handRegion","hiltRegion"}){
                var region=(arc.graphics.g2d.TextureRegion)type.weapons.first().getClass().getField(part).get(type.weapons.first());
                if(region==null||!region.found())throw new AssertionError("missing T3 arm/hilt atlas: "+part);
            }
            mech=type.spawn(Team.sharded,220,320);mech.rotation=0;
            Unit reference=Vars.content.unit("cryon-subsonic").spawn(Team.sharded,215,250);
            reference.apply(StatusEffects.unmoving,100000);reference.apply(StatusEffects.disarmed,100000);
            Vars.player.team(Team.sharded);Vars.player.unit(mech);Vars.logic.play();Vars.renderer.setScale(3);started=true;
        })));
        Events.run(Trigger.update,()->{if(!started)return;guarded(()->{
            frames++;Core.camera.position.set(285,310);
            mech.aim(350,320);mech.controlWeapons(true,false);
            if(frames==45){
                Unit target=UnitTypes.dagger.spawn(Team.crux,350,320);target.health=target.maxHealth=100000;
                target.apply(StatusEffects.unmoving,100000);target.apply(StatusEffects.disarmed,100000);
            }
            var dash=mech.abilities[0];boolean active=(Boolean)dash.getClass().getMethod("active").invoke(dash);
            int trails=(Integer)dash.getClass().getMethod("trailCount").invoke(dash);
            if(active&&!onset){capture("dash-start");onset=true;}
            if(active&&trails>=4&&!moving){capture("chromatic-trail");moving=true;}
            if(onset&&!active&&endedAt<0)endedAt=frames;
            if(endedAt>=0&&!fading&&frames-endedAt>=3){capture("dash-fade");fading=true;}
            if(endedAt>=0&&!faded&&frames-endedAt>=24){
                capture("dash-ended");faded=true;nightAt=frames;
                Vars.state.rules.lighting=true;Vars.state.rules.ambientLight.set(0.04f,0.06f,0.12f,0.9f);
                Vars.enableLight=true;Vars.renderer.drawLight=true;
            }
            if(nightAt>=0&&!night&&frames-nightAt>=12){capture("blade-environment-light");night=true;}
            if(onset&&moving&&fading&&faded&&night){
                Log.info("SUPERSONIC_RENDER_PASS: T3 atlas/IK/hilt, automatic short dash, strong chromatic full-mech trails, startup/landing holds, fade, full-blade terrain illumination");Core.app.exit();
            }
            if(frames>250)throw new AssertionError("missing dash or chromatic trail phase");
        });});
        mindustry.desktop.DesktopLauncher.main(args);
    }
    static void capture(String name){ScreenUtils.saveScreenshot(data.child("supersonic-"+name+".png"));Log.info("SUPERSONIC_SCREENSHOT @",data.child("supersonic-"+name+".png"));}
    interface Check{void run()throws Exception;}
    static void guarded(Check r){try{r.run();}catch(Throwable t){Log.err(t);System.exit(1);}}
}
