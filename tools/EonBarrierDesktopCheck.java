import arc.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.world.Tile;

/** Isolated real desktop GL check, including packed distance-mask creation and shader draw. */
public class EonBarrierDesktopCheck{
    static Unit boss;
    static boolean hit;
    public static void main(String[] args){
        Events.on(ClientLoadEvent.class,event->Timer.schedule(()->{
            Core.graphics.setFullscreen(false); Core.graphics.setWindowSize(1000,760);
            Vars.logic.reset();
            Rules rules=new Rules(); rules.waves=rules.waveTimer=rules.canGameOver=false; rules.unitCap=100;
            Vars.state.rules=rules;
            Vars.world.loadGenerator(120,120,tiles->{for(int x=0;x<120;x++)for(int y=0;y<120;y++)
                tiles.set(x,y,new Tile(x,y,Blocks.darksand,Blocks.air,Blocks.air));});
            Vars.logic.play();
            boss=Vars.content.unit("cryon-eon").spawn(Team.sharded,480,480);
            boss.rotation=90;
            Vars.player.team(Team.sharded); Vars.player.unit(boss);
            Vars.renderer.setScale(2f);
            if(!Core.atlas.has("cryon-eon-barrier-field"))throw new AssertionError("Missing packed hull distance field");
            Timer.schedule(()->ScreenUtils.saveScreenshot(Vars.dataDirectory.child("eon-idle.png")),2f);
            Timer.schedule(()->{hit=true; boss.rawDamage(0.1f);},3f);
            Timer.schedule(()->ScreenUtils.saveScreenshot(Vars.dataDirectory.child("eon-shader-hit.png")),5f);
            Timer.schedule(()->{
                Log.info("EON_BARRIER_DESKTOP_PASS: packed hull field, real GL shader, idle and hit frames rendered");
                Core.app.exit();
            },7f);
        },2f));
        Events.run(Trigger.update,()->{
            if(boss==null)return;
            boss.vel.setZero(); boss.controlWeapons(false,false);
            if(hit)boss.abilities[0].data=0.9f;
        });
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
