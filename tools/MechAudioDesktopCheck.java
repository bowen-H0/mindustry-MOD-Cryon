import arc.*;
import arc.audio.Sound;
import arc.files.Fi;
import arc.scene.ui.Dialog;
import arc.util.*;
import java.nio.file.Files;
import java.lang.reflect.*;
import java.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.Tile;

/** Real desktop decoding plus action-aligned, spatial one-shot audio checks. */
public class MechAudioDesktopCheck{
    static Class<?> audioType;
    static Unit mech, victim, dasher;
    static Method shoot, play;
    static Map<String,Clip> clips=new HashMap<>();
    static Object readyCue;
    static int frames;
    static boolean started;
    static class Clip extends Sound{
        final Sound original;
        int calls;
        float pitch,volume;
        Clip(Sound sound){original=sound;}
        @Override public float calcVolume(float x,float y){return original.calcVolume(x,y);}
        @Override public int at(float x,float y,float p,float v){calls++;pitch=p;volume=v;return original.at(x,y,p,v);}
    }
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static int calls(String name){return clips.get(name).calls;}
    public static void main(String[] args)throws Exception{
        Fi data=new Fi(Files.createTempDirectory("cryon-mech-audio-").toFile());
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlanuch.jar"));
        System.setProperty("mindustry.data.dir",data.absolutePath());
        Events.on(ClientLoadEvent.class,e->Core.app.post(()->guarded(()->{
            for(var actor:Core.scene.root.getChildren().copy())if(actor instanceof Dialog d){d.clearActions();d.hide(null);}
            audioType=Vars.mods.locateMod("cryon").main.getClass().getClassLoader().loadClass("cryon.Features.MechAudio");
            for(String name:new String[]{"ready","light","reverse","heavy","hit","armour","parry","dashCharge","dashBurst","dashBrake","stepLight","stepHeavy","collapse"}){
                Sound original=(Sound)audioType.getField(name).get(null);
                check(original.file!=null&&original.file.exists(),"missing clip: "+name);
                if(Core.audio.initialized()){original.load(original.file);check(original.getLength()>0.05f,"failed to decode clip: "+name);}
                if(name.equals("armour")&&Core.audio.initialized()){
                    check(original.bus!=null&&original.bus!=Core.audio.soundBus,"impact still uses environmental reverb bus");
                    check(original.getLength()<0.16f,"impact still has a long ringing tail");
                }
                Clip clip=new Clip(original);clips.put(name,clip);
                // Footsteps and death keep their actual native Sound references.
                if(!name.startsWith("step")&&!name.equals("collapse"))audioType.getField(name).set(null,clip);
            }
            Vars.logic.reset();Vars.state.rules=new Rules();Vars.state.rules.canGameOver=false;Vars.state.rules.waves=false;Vars.state.rules.unitCap=100;
            Vars.world.loadGenerator(100,100,tiles->{for(int x=0;x<100;x++)for(int y=0;y<100;y++)tiles.set(x,y,new Tile(x,y,Blocks.stone,Blocks.air,Blocks.air));});
            UnitType type=Vars.content.unit("cryon-subsonic");
            check(type.stepSound==clips.get("stepLight").original&&type.deathSound==clips.get("collapse").original,"T1 native step/death mapping");
            UnitType t3=Vars.content.unit("cryon-supersonic");check(t3.stepSound==clips.get("stepHeavy").original,"T3 native heavy steps missing");
            mech=type.spawn(Team.sharded,200,300);mech.rotation=0;
            mech.controller(new mindustry.entities.units.AIController(){@Override public void updateUnit(){unit.vel.setZero();unit.controlWeapons(false,false);}});
            victim=UnitTypes.dagger.spawn(Team.crux,222,300);victim.health=victim.maxHealth=100000;
            victim.apply(StatusEffects.unmoving,100000);victim.apply(StatusEffects.disarmed,100000);
            Weapon weapon=type.weapons.first();
            shoot=weapon.getClass().getDeclaredMethod("shoot",Unit.class,mindustry.entities.units.WeaponMount.class,float.class,float.class,float.class);shoot.setAccessible(true);
            Class<?> cue=audioType.getClassLoader().loadClass("cryon.Features.MechAudio$Cue");
            readyCue=Enum.valueOf((Class)cue,"ready");play=audioType.getMethod("play",cue,Unit.class,float.class,float.class,float.class);
            Vars.player.team(Team.sharded);Vars.logic.play();Core.camera.position.set(300,350);started=true;
        })));
        Events.run(Trigger.update,()->{if(!started)return;guarded(()->{
            frames++;Core.camera.position.set(300,350);
            if(frames==30||frames==70||frames==110||frames==150){
                shoot.invoke(mech.type.weapons.first(),mech,mech.mounts[0],mech.x,mech.y,0f);
                check(calls("ready")==1+(frames-30)/40,"ready sound did not follow attack start");
            }
            if(frames==185){
                check(calls("light")>=1&&calls("reverse")>=1&&calls("heavy")>=2,"combo sounds missing");
                check(calls("armour")==4&&calls("hit")==0,"all hits must clang once per swing: "+calls("armour"));
                check(clips.get("armour").volume>=0.9f&&clips.get("armour").pitch>=1,"metal impact lacks loudness/sharpness");
                victim.remove();victim=Vars.content.unit("cryon-buffer").spawn(Team.crux,222,300);victim.health=victim.maxHealth=100000;
                victim.apply(StatusEffects.unmoving,100000);victim.apply(StatusEffects.disarmed,100000);
            }
            if(frames==190)shoot.invoke(mech.type.weapons.first(),mech,mech.mounts[0],mech.x,mech.y,0f);
            if(frames==220)check(calls("armour")==5,"armour strike should clang exactly once");
            if(frames==230){
                var mount=mech.mounts[0];mount.reload=100;mount.shoot=false;
                mount.getClass().getField("age").setFloat(mount,7);mount.getClass().getField("hold").setFloat(mount,0);
                mount.getClass().getField("combo").setInt(mount,0);mount.getClass().getField("swingSide").setInt(mount,1);
                mount.getClass().getField("parried").setInt(mount,0);mount.getClass().getField("parryHold").setBoolean(mount,false);
                var bullet=new mindustry.entities.bullet.BasicBulletType(3,20);bullet.init();bullet.load();
                for(int i=0;i<3;i++)bullet.create(null,Team.crux,220,300,180);
            }
            if(frames==255)check(calls("parry")==1,"multi-bullet parry ping repeated: "+calls("parry"));
            if(frames==260){
                dasher=Vars.content.unit("cryon-supersonic").spawn(Team.sharded,400,440);dasher.rotation=0;
                dasher.controller(new mindustry.entities.units.AIController(){@Override public void updateUnit(){unit.vel.setZero();unit.controlWeapons(false,false);}});
                Unit target=UnitTypes.dagger.spawn(Team.crux,530,440);target.health=target.maxHealth=100000;
                target.apply(StatusEffects.unmoving,100000);target.apply(StatusEffects.disarmed,100000);
            }
            if(frames>=300&&calls("dashBrake")>0){
                check(calls("dashCharge")==1&&calls("dashBurst")==1&&calls("dashBrake")==1,
                    "dash phases duplicated or silent: "+calls("dashCharge")+","+calls("dashBurst")+","+calls("dashBrake"));
                int count=calls("ready");
                play.invoke(null,readyCue,mech,100000f,100000f,1f);check(calls("ready")==count,"inaudible distant event played");
                Vars.state.set(mindustry.core.GameState.State.paused);play.invoke(null,readyCue,mech,mech.x,mech.y,1f);
                check(calls("ready")==count,"paused event played");Vars.state.set(mindustry.core.GameState.State.playing);
                Vars.headless=true;play.invoke(null,readyCue,mech,mech.x,mech.y,1f);Vars.headless=false;
                check(calls("ready")==count,"headless event played");
                check(clips.get("dashBurst").pitch<clips.get("ready").pitch,"heavy mech did not sound lower");
                Log.info("MECH_AUDIO_PASS: 13 assets/native decode, combo/normal/armour/parry timing, native footsteps/death, three dash phases exactly once, tier pitch, distance/pause/headless suppression");
                Core.app.exit();
            }
            if(frames>500)throw new AssertionError("dash sound timeout: "+calls("dashCharge")+","+calls("dashBurst")+","+calls("dashBrake"));
        });});
        mindustry.desktop.DesktopLauncher.main(args);
    }
    interface Check{void run()throws Exception;}
    static void guarded(Check check){try{check.run();}catch(Throwable error){Log.err(error);System.exit(1);}}
}
