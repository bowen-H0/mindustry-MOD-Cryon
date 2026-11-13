import arc.*;
import arc.files.Fi;
import arc.util.*;
import mindustry.*;
import mindustry.ai.types.FlyingAI;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.type.UnitType;
import mindustry.world.Tile;

/** Actual desktop draw, sprite lookup, bomber AI and timed ground bomb detonation. */
public class OrbitalUnitRenderCheck{
    private static Unit bomber;
    private static Building target;
    private static int drops;
    public static void main(String[] args){
        Fi dir = Fi.get(System.getProperty("mindustry.data.dir"));
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(dir.child("mods").child("cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(dir.child("mods").child("unitlaunch.jar"));
        Events.on(ClientLoadEvent.class, event -> Timer.schedule(() -> {
            try{
                for(var element : Core.scene.root.getChildren().copy()){
                    if(element instanceof arc.scene.ui.Dialog dialog){ dialog.clearActions(); dialog.hide(null); }
                }
                Core.graphics.setFullscreen(false); Core.graphics.setWindowSize(1000, 700);
                Vars.logic.reset();
                Rules rules = new Rules();
                rules.waves = rules.waveTimer = rules.canGameOver = false;
                rules.unitCap = 100; rules.defaultTeam = Team.sharded; rules.waveTeam = Team.crux;
                Vars.state.rules = rules;
                Vars.world.loadGenerator(96, 96, tiles -> {
                    for(int x = 0; x < 96; x++) for(int y = 0; y < 96; y++)
                        tiles.set(x, y, new Tile(x, y, Blocks.sand, Blocks.air, Blocks.air));
                });
                Vars.world.tile(52, 45).setBlock(Blocks.thoriumWallLarge, Team.crux);
                target = Vars.world.build(52, 45); target.health = 100000f;
                Vars.logic.play();
                UnitType type = Vars.content.unit("cryon-startrail");
                if(type == null || !Core.atlas.isFound(type.region) || !Core.atlas.isFound(type.uiIcon)){
                    throw new AssertionError("Missing Startrail body/icon");
                }
                bomber = type.spawn(Team.sharded, 390, 330); bomber.controller(new FlyingAI());
                Unit plane = UnitTypes.flare.spawn(Team.sharded, 360, 345);
                plane.apply(StatusEffects.unmoving, 100000); plane.apply(StatusEffects.disarmed, 100000);
                Unit ground = UnitTypes.dagger.spawn(Team.sharded, 360, 310);
                ground.apply(StatusEffects.unmoving, 100000); ground.apply(StatusEffects.disarmed, 100000);
                Vars.player.team(Team.sharded); Vars.player.unit(ground);
                Vars.renderer.setScale(3f);
                Timer.schedule(() -> {
                    try{
                        ScreenUtils.saveScreenshot(dir.child("startrail-orbital.png"));
                        if(drops == 0 || target.health >= 100000f) throw new AssertionError("Bomber AI dropped no damaging bombs: drops="
                                + drops + " hp=" + target.health + " position=" + bomber.x + "," + bomber.y
                                + " range=" + bomber.range() + " paused=" + Vars.state.isPaused()
                                + " shooting=" + bomber.isShooting + " aim=" + bomber.aimX + "," + bomber.aimY);
                        Log.info("ORBITAL_RENDER_PASS: body/icon, high layer, bomber AI drops=@ groundDamage=@", drops, 100000f - target.health);
                        Core.app.exit();
                    }catch(Throwable error){ Log.err(error); System.exit(1); }
                }, 9f);
            }catch(Throwable error){ Log.err(error); System.exit(1); }
        }, 3f));
        Events.run(Trigger.update, () -> {
            if(bomber == null || !Vars.state.isGame()) return;
            Vars.player.set(390, 345); Core.camera.position.set(390, 345);
            for(Bullet bullet : Groups.bullet) if(bullet.owner == bomber && bullet.time <= Time.delta + 0.01f) drops++;
        });
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
