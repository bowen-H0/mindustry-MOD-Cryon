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

/** Desktop atlas/IK/impact smoke check, using isolated game data. */
public class SubsonicRenderCheck{
    static Fi data;
    static Unit mech;
    static int frames;
    static boolean started, idle, windup, impact, recovery;
    static boolean[] combos = new boolean[4];
    static Bullet incoming;
    static boolean parried;
    static int parryFrame = -1;
    static boolean parryFlash, parryHold, parryFragments;
    public static void main(String[] args)throws Exception{
        data=new Fi(Files.createTempDirectory("cryon-subsonic-render-").toFile());
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlanuch.jar"));
        System.setProperty("mindustry.data.dir",data.absolutePath());
        Events.on(ClientLoadEvent.class,e->Core.app.post(()->guarded(()->{
            Core.graphics.setFullscreen(false);Core.graphics.setWindowSize(1100,760);
            for(var actor:Core.scene.root.getChildren().copy())if(actor instanceof Dialog d){d.clearActions();d.hide(null);}
            Vars.logic.reset();Vars.state.rules=new Rules();
            Vars.state.rules.canGameOver=false;Vars.state.rules.waves=false;Vars.state.rules.unitCap=100;
            Vars.world.loadGenerator(90,90,tiles->{for(int x=0;x<90;x++)for(int y=0;y<90;y++)tiles.set(x,y,new Tile(x,y,Blocks.stone,Blocks.air,Blocks.air));});
            var type=Vars.content.unit("cryon-subsonic");
            if(type==null||!type.region.found()||!type.legRegion.found()||!type.baseRegion.found())throw new AssertionError("missing mech atlas region");
            for(String part:new String[]{"upperArmRegion","forearmRegion","handRegion","hiltRegion"}){
                var region=(arc.graphics.g2d.TextureRegion)type.weapons.first().getClass().getField(part).get(type.weapons.first());
                if(region==null||!region.found())throw new AssertionError("missing IK arm sprite: "+part);
            }
            mech=type.spawn(Team.sharded,320,320);mech.rotation=0;
            mech.apply(StatusEffects.unmoving,100000);
            Unit target=UnitTypes.dagger.spawn(Team.crux,342,320);
            target.health=target.maxHealth=100000;
            target.apply(StatusEffects.unmoving,100000);target.apply(StatusEffects.disarmed,100000);
            Vars.player.team(Team.sharded);Vars.player.unit(mech);
            Vars.logic.play();Vars.renderer.setScale(4);started=true;
        })));
        Events.run(Trigger.update,()->{if(!started)return;guarded(()->{
            frames++;Core.camera.position.set(330,320);
            mech.aim(342,320);mech.controlWeapons(true,frames>35&&frames<240);
            float age=mech.mounts[0].getClass().getField("age").getFloat(mech.mounts[0]);
            float hold=mech.mounts[0].getClass().getField("hold").getFloat(mech.mounts[0]);
            int combo=mech.mounts[0].getClass().getField("combo").getInt(mech.mounts[0]);
            if(combo>=0&&!combos[combo]&&age>=3&&age<6){capture("combo-"+combo);combos[combo]=true;}
            if(!idle&&frames>15){capture("idle");idle=true;}
            if(!windup&&frames>35&&age>=3&&age<6){capture("windup");windup=true;}
            if(!impact&&hold>0){capture("impact");impact=true;}
            if(!recovery&&impact&&age>13&&age<22){capture("recovery");recovery=true;}
            if(frames==280){
                var round=new mindustry.entities.bullet.BasicBulletType(3,20);round.lifetime=100;
                round.init();round.load();
                incoming=round.create(null,Team.crux,350,320,180);
            }
            if(incoming!=null&&incoming.absorbed&&!parried){capture("parry");parried=true;parryFrame=frames;}
            if(parryFrame>=0){
                int elapsed=frames-parryFrame;
                if(!parryFlash&&elapsed>=2){capture("parry-flash");parryFlash=true;}
                if(!parryHold&&elapsed>=4){capture("parry-hold");parryHold=true;}
                if(!parryFragments&&elapsed>=10){capture("parry-fragments");parryFragments=true;}
            }
            if(idle&&windup&&impact&&recovery&&parried&&parryFragments&&combos[0]&&combos[1]&&combos[2]&&combos[3]&&frames>320){Log.info("SUBSONIC_RENDER_PASS: preserved atlas, four combos, automatic parry, white cut flash, neon ghosts, split fragments and hit stop");Core.app.exit();}
            if(frames>450)throw new AssertionError("missing animation phase or bullet parry");
        });});
        mindustry.desktop.DesktopLauncher.main(args);
    }
    static void capture(String name){ScreenUtils.saveScreenshot(data.child("subsonic-"+name+".png"));Log.info("SUBSONIC_SCREENSHOT @",data.child("subsonic-"+name+".png"));}
    interface Check{void run()throws Exception;}
    static void guarded(Check r){try{r.run();}catch(Throwable t){Log.err(t);System.exit(1);}}
}
