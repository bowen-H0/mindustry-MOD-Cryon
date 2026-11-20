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
import mindustry.type.Item;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.entities.bullet.BulletType;

/** Isolated desktop atlas, custom audio and concave laser rendering check. */
public class FaultRenderCheck{
    static Fi data; static int frames; static boolean started;
    static int releases, releaseFrame, releaseVoice = -1;
    static ItemTurret turret; static ItemTurret.ItemTurretBuild build; static BulletType beam;
    public static void main(String[] args)throws Exception{
        data=new Fi(Files.createTempDirectory("cryon-fault-render-").toFile());
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlaunch.jar"));
        System.setProperty("mindustry.data.dir",data.absolutePath());
        Events.on(ClientLoadEvent.class,e->Core.app.post(()->guarded(()->{
            Core.graphics.setFullscreen(false); Core.graphics.setWindowSize(1100,800);
            for(var actor:Core.scene.root.getChildren().copy())if(actor instanceof Dialog d){d.clearActions();d.hide(null);}
            Vars.logic.reset(); Vars.state.rules=new Rules(); Vars.state.rules.canGameOver=false; Vars.state.rules.waves=false; Vars.state.rules.unitCap=100;
            Vars.world.loadGenerator(90,90,tiles->{for(int x=0;x<90;x++)for(int y=0;y<90;y++)tiles.set(x,y,new Tile(x,y,Blocks.stone,Blocks.air,Blocks.air));});
            turret=(ItemTurret)Vars.content.block("cryon-fault");
            if(turret==null || !turret.region.found() || !turret.uiIcon.found())throw new AssertionError("missing Fault atlas/icon");
            if(turret.shootSound==Sounds.none || turret.chargeSound==Sounds.none)throw new AssertionError("custom audio missing");
            Vars.world.tile(40,30).setBlock(turret,Team.sharded);
            build=(ItemTurret.ItemTurretBuild)Vars.world.build(40,30);build.rotation=90;build.enabled=false;
            Item magnesium=Vars.content.item("cryon-magnesium");beam=turret.ammoTypes.get(magnesium);
            mindustry.world.blocks.defense.turrets.Turret lancer=(mindustry.world.blocks.defense.turrets.Turret)Blocks.lancer;
            if(turret.shootSound!=lancer.shootSound || turret.chargeSound!=lancer.chargeSound)
                throw new AssertionError("Lancer audio binding missing");
            arc.audio.Sound original = turret.shootSound;
            arc.audio.Sound observed = new arc.audio.Sound(){
                @Override public int at(float x, float y, float pitch, float volume){
                    releases++; releaseFrame=frames;
                    releaseVoice=original.at(x,y,pitch,volume);
                    Log.info("FAULT_NATIVE_RELEASE frame=@ voice=@ duration=@",frames,releaseVoice,original.getLength());
                    return releaseVoice;
                }
            };
            turret.shootSound=beam.shootSound=observed;
            build.handleStack(magnesium, 2, null);
            for(int x=37;x<=43;x+=3){
                Vars.world.tile(x,46).setBlock(Blocks.titaniumWall,Team.crux);
                Vars.world.build(x,46).health=100000;
            }
            Vars.player.team(Team.sharded);Vars.logic.play();Vars.renderer.setScale(3f);started=true;
        })));
        Events.run(Trigger.update,()->{
            if(!started)return;
            guarded(()->{
                frames++;Core.camera.position.set(324,310);Vars.renderer.setScale(3f);
                if(frames==5){
                    try{
                        java.lang.reflect.Method shoot=mindustry.world.blocks.defense.turrets.Turret.TurretBuild.class.getDeclaredMethod("shoot",BulletType.class);
                        shoot.setAccessible(true);shoot.invoke(build,beam);
                    }catch(ReflectiveOperationException error){throw new RuntimeException(error);}
                }
                if(frames==16||frames==33||frames==39){
                    ScreenUtils.saveScreenshot(data.child("fault-"+frames+".png"));
                    Log.info("FAULT_RENDER screenshot=@",data.child("fault-"+frames+".png"));
                }
                if(frames==80){
                    if(build.totalShots!=1 || releases!=1 || releaseFrame<=5 || releaseVoice<0)
                        throw new AssertionError("native delayed firing cue missing or duplicated: shots="+build.totalShots+" releases="+releases+" voice="+releaseVoice);
                    Log.info("FAULT_RENDER_PASS: native charge-to-fire scheduling, exactly one native Lancer release, atlas, icon and beam");Core.app.exit();
                }
            });
        });
        mindustry.desktop.DesktopLauncher.main(args);
    }
    static void guarded(Runnable action){try{action.run();}catch(Throwable error){Log.err(error);System.exit(1);}}
}
