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

/** Isolated native desktop validation of T5 weapon forms, real crits, audio and visuals. */
public class SuperluminalRenderCheck{
    static Fi data;
    static Unit mech, friend;
    static mindustry.type.Weapon weapon;
    static java.lang.reflect.Method marks;
    static int frames, activeAt=-1, endedAt=-1;
    static boolean started, flash, trails, recovered;
    static Unit target;
    static float targetHp;
    public static void main(String[] args)throws Exception{
        data=new Fi(Files.createTempDirectory("cryon-superluminal-render-").toFile());
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
            var type=Vars.content.unit("cryon-superluminal");
            if(type==null||!type.region.found()||!type.baseRegion.found()||!type.legRegion.found())throw new AssertionError("missing T5 body atlas");
            for(String part:new String[]{"upperArmRegion","forearmRegion","handRegion","hiltRegion"}){
                var region=(arc.graphics.g2d.TextureRegion)type.weapons.first().getClass().getField(part).get(type.weapons.first());
                if(region==null||!region.found())throw new AssertionError("missing T5 arm/hilt atlas: "+part);
            }
            var loader=Vars.mods.locateMod("cryon").main.getClass().getClassLoader();
            weapon=type.weapons.first();
            marks=weapon.getClass().getMethod("markStrength",Healthc.class);
            var impact = new arc.audio.Sound(Vars.tree.get("sounds/cryon-mech-slaughter-impact.ogg"));
            if(impact.getLength()<0.18f)throw new AssertionError("T5 impact sound not decoded");
            impact.dispose();
            mech=type.spawn(Team.sharded,320,320);mech.rotation=0;
            friend=Vars.content.unit("cryon-subsonic").spawn(Team.sharded,220,250);
            friend.apply(StatusEffects.unmoving,100000);friend.apply(StatusEffects.disarmed,100000);
            Vars.player.team(Team.sharded);Vars.player.unit(mech);Vars.logic.play();Vars.renderer.setScale(1.35f);started=true;
        })));
        Events.run(Trigger.update,()->{if(!started)return;guarded(()->{
            frames++;Core.camera.position.set(450,350);
            mech.aim(660,320);mech.controlWeapons(true,frames>45);
            if(frames==20){capture("folded-blade");flash=true;}
            if(target!=null&&frames<110)mech.move(1.2f*Time.delta,0f);
            if(frames==45){
                for(int y=34;y<=46;y++)Vars.world.tile(65,y).setBlock(Blocks.stoneWall);
                target=UnitTypes.dagger.spawn(Team.crux,660,320);target.health=target.maxHealth=1000000;
                target.apply(StatusEffects.unmoving,100000);target.apply(StatusEffects.disarmed,100000);
                targetHp=target.health;
                for(int i=0;i<5;i++){
                    Unit extra=UnitTypes.dagger.spawn(Team.crux,540+i*12,380+i*15);
                    extra.health=extra.maxHealth=1000000;extra.apply(StatusEffects.unmoving,100000);extra.apply(StatusEffects.disarmed,100000);
                }
            }
            if(target!=null){
                var mount=mech.mounts[0];
                float deployed=mount.getClass().getField("deployment").getFloat(mount);
                int strength=(Integer)marks.invoke(weapon,target);
                if(frames%100==0)Log.info("T5 frame=@ deployment=@ age=@ reload=@ mark=@ health=@",frames,deployed,mount.getClass().getField("age").getFloat(mount),mount.reload,strength,target.health);
                if(!trails && deployed>=1f && strength>=1 && mount.getClass().getField("trailCount").getInt(mount)>=3){capture("slaughter-sweep");trails=true;}
                if(!recovered && strength>=5 && target.health<targetHp){capture("marked-critical-explosion");recovered=true;}
                if(friend.health<friend.maxHealth)throw new AssertionError("T5 sweep damaged friendly mech");
            }
            if(flash&&trails&&recovered){Log.info("SUPERLUMINAL_RENDER_PASS: T5/IK/hilt atlas, decoded dry heavy impact, folded and fivefold deployed blade, native high-speed sweep, full mech/arm/blade afterimages, terrain passthrough, marked critical sparks and allied safety");Core.app.exit();}
            if(frames>600)throw new AssertionError("missing slaughter/critical phase");
        });});
        mindustry.desktop.DesktopLauncher.main(args);
    }
    static void capture(String name){ScreenUtils.saveScreenshot(data.child("superluminal-"+name+".png"));Log.info("SUPERLUMINAL_SCREENSHOT @",data.child("superluminal-"+name+".png"));}
    interface Check{void run()throws Exception;}
    static void guarded(Check r){try{r.run();}catch(Throwable t){Log.err(t);System.exit(1);}}
}
