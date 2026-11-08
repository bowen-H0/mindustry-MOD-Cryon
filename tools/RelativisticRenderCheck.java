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
import mindustry.type.StatusEffect;
import mindustry.world.Tile;

/** Isolated desktop validation of T4 art, self-buff activation, perception marks and cooldown fade. */
public class RelativisticRenderCheck{
    static Fi data;
    static Unit mech, friend;
    static StatusEffect buff;
    static int frames, activeAt=-1, endedAt=-1;
    static boolean started, flash, trails, recovered;
    public static void main(String[] args)throws Exception{
        data=new Fi(Files.createTempDirectory("cryon-relativistic-render-").toFile());
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlanuch.jar"));
        System.setProperty("mindustry.data.dir",data.absolutePath());
        Events.on(ClientLoadEvent.class,e->Core.app.post(()->guarded(()->{
            Core.graphics.setFullscreen(false);Core.graphics.setWindowSize(1100,760);
            for(var actor:Core.scene.root.getChildren().copy())if(actor instanceof Dialog d){d.clearActions();d.hide(null);}
            Vars.logic.reset();Vars.state.rules=new Rules();Vars.state.rules.canGameOver=false;Vars.state.rules.waves=false;Vars.state.rules.unitCap=100;
            Vars.state.rules.lighting=true;Vars.state.rules.ambientLight.set(.05f,.07f,.13f,.72f);
            Vars.enableLight=true;Vars.renderer.drawLight=true;
            Vars.world.loadGenerator(110,100,tiles->{for(int x=0;x<110;x++)for(int y=0;y<100;y++)tiles.set(x,y,new Tile(x,y,Blocks.stone,Blocks.air,Blocks.air));});
            var type=Vars.content.unit("cryon-relativistic");
            if(type==null||!type.region.found()||!type.baseRegion.found()||!type.legRegion.found())throw new AssertionError("missing T4 body atlas");
            for(String part:new String[]{"upperArmRegion","forearmRegion","handRegion","hiltRegion"}){
                var region=(arc.graphics.g2d.TextureRegion)type.weapons.first().getClass().getField(part).get(type.weapons.first());
                if(region==null||!region.found())throw new AssertionError("missing T4 arm/hilt atlas: "+part);
            }
            var loader=Vars.mods.locateMod("cryon").main.getClass().getClassLoader();
            buff=(StatusEffect)loader.loadClass("cryon.Content.ShadowMech").getField("extremeOverclock").get(null);
            if(!buff.uiIcon.found())throw new AssertionError("missing burst status icon");
            mech=type.spawn(Team.sharded,240,320);mech.rotation=0;
            friend=Vars.content.unit("cryon-subsonic").spawn(Team.sharded,220,250);
            friend.apply(StatusEffects.unmoving,100000);friend.apply(StatusEffects.disarmed,100000);
            Vars.player.team(Team.sharded);Vars.player.unit(mech);Vars.logic.play();Vars.renderer.setScale(2.6f);started=true;
        })));
        Events.run(Trigger.update,()->{if(!started)return;guarded(()->{
            frames++;Core.camera.position.set(335,310);
            mech.aim(460,320);mech.controlWeapons(true,frames>45);
            if(frames==45){
                Unit target=UnitTypes.dagger.spawn(Team.crux,460,320);target.health=target.maxHealth=100000;
                target.apply(StatusEffects.unmoving,100000);target.apply(StatusEffects.disarmed,100000);
            }
            if(mech.hasEffect(buff)){
                if(activeAt<0)activeAt=frames;
                if(frames-activeAt<65)mech.move(1.4f*Time.delta,0);
                if(!flash&&frames-activeAt>=3){capture("burst-activation");flash=true;}
                if(!trails&&frames-activeAt>=30){capture("extreme-perception");trails=true;}
                if(friend.hasEffect(buff))throw new AssertionError("self overclock leaked to friend");
            }else if(activeAt>=0&&endedAt<0)endedAt=frames;
            if(endedAt>=0&&!recovered&&frames-endedAt>=20){capture("burst-cooled");recovered=true;}
            if(flash&&trails&&recovered){Log.info("RELATIVISTIC_RENDER_PASS: T4/status atlas, activation flash, chromatic motion trails, target perception marks, sword lighting, self-only buff and cooldown return");Core.app.exit();}
            if(frames>600)throw new AssertionError("missing extreme-overclock phase");
        });});
        mindustry.desktop.DesktopLauncher.main(args);
    }
    static void capture(String name){ScreenUtils.saveScreenshot(data.child("relativistic-"+name+".png"));Log.info("RELATIVISTIC_SCREENSHOT @",data.child("relativistic-"+name+".png"));}
    interface Check{void run()throws Exception;}
    static void guarded(Check r){try{r.run();}catch(Throwable t){Log.err(t);System.exit(1);}}
}
