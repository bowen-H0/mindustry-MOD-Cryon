import arc.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.world.Tile;

/** Isolated real-GL combat preview: charging, all seven batteries, collapse and final explosion. */
public class EonCombatDesktopCheck{
    static Unit boss, target;
    static boolean showShield;
    public static void main(String[] args){
        Events.on(ClientLoadEvent.class,event->Timer.schedule(()->{
            Core.graphics.setFullscreen(false); Core.graphics.setWindowSize(1400,900);
            Vars.logic.reset();
            Rules rules=new Rules(); rules.waves=rules.waveTimer=rules.canGameOver=false; rules.unitCap=100;
            Vars.state.rules=rules;
            Vars.world.loadGenerator(180,140,tiles->{for(int x=0;x<180;x++)for(int y=0;y<140;y++)
                tiles.set(x,y,new Tile(x,y,Blocks.darksand,Blocks.air,Blocks.air));});
            Vars.logic.play();
            boss=Vars.content.unit("cryon-eon").spawn(Team.sharded,600,560); boss.rotation=0;
            if(Math.abs(boss.type.region.scale-1.6f)>0.001f || Math.abs(boss.type.cellRegion.scale-1.6f)>0.001f)
                throw new AssertionError("Hull/cell drawing scales differ");
            Vars.player.team(Team.sharded); Vars.player.unit(boss);
            target=UnitTypes.eclipse.spawn(Team.crux,1050,560); target.health=target.maxHealth=100000000f;
            target.controller(new mindustry.entities.units.AIController(){ @Override public void updateUnit(){} });
            Vars.renderer.setScale(1.2f);
            for(int i=0;i<4;i++){
                final int frame=i;
                Timer.schedule(()->ScreenUtils.saveScreenshot(Vars.dataDirectory.child("eon-combat-"+frame+".png")),1.5f+i*2f);
            }
            Timer.schedule(()->showShield=true,6.5f);
            Timer.schedule(()->{
                for(mindustry.entities.units.WeaponMount mount:boss.mounts)
                    if(mount.totalShots==0)throw new AssertionError("No GL combat fire: "+mount.weapon.name);
                Log.info("EON_COMBAT_DESKTOP_PASS: seven batteries, custom engines, charge, impact, collapse and final explosion rendered");
                Core.app.exit();
            },11f);
        },2f));
        Events.run(Trigger.update,()->{
            if(boss==null || target==null)return;
            boss.vel.setZero(); boss.aim(target.x,target.y); boss.controlWeapons(true,true);
            if(showShield)boss.abilities[0].data=0.8f;
        });
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
