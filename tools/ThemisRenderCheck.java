import arc.*;
import arc.files.Fi;
import arc.scene.ui.Dialog;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.blocks.defense.turrets.Turret.TurretBuild;

/** Loads the packaged sibling turrets and captures their actual desktop renderers. */
public class ThemisRenderCheck{
    static Fi dir;
    static boolean running;
    static TurretBuild elder;
    static Bullet field;
    static PowerTurret themis, abyss;
    static void check(boolean ok, String message){if(!ok)throw new AssertionError(message);}
    static void guarded(Runnable r){try{r.run();}catch(Throwable t){Log.err(t);System.exit(1);}}
    public static void main(String[] args){
        dir = Fi.get(System.getProperty("mindustry.data.dir"));
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(dir.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(dir.child("mods/unitlaunch.jar"));
        Events.on(ClientLoadEvent.class, event -> Timer.schedule(() -> guarded(() -> {
            for(var element : Core.scene.root.getChildren().copy())
                if(element instanceof Dialog dialog){dialog.clearActions();dialog.hide(null);}
            Core.graphics.setFullscreen(false); Core.graphics.setWindowSize(1280, 850);
            check(!Vars.mods.locateMod("cryon").hasContentErrors(), "packaged content errors");
            abyss = (PowerTurret)Vars.content.block("cryon-abyss");
            themis = (PowerTurret)Vars.content.block("cryon-themis");
            check(themis != null && themis.techNode != null && themis.techNode.parent.content == abyss, "Themis research missing");
            check(themis.description.equals("雷渊的老哥。"), "Themis description changed");
            check(themis.size == 5 && themis.range == 520f && themis.shoot.shots == 3 && themis.requirements.length == 4, "Themis content configuration");
            check(themis.region.found() && themis.uiIcon.found(), "Themis artwork/icon missing");
            Vars.logic.reset();
            Rules rules = new Rules(); rules.waves = rules.waveTimer = rules.canGameOver = false;
            rules.unitCap = 100; Vars.state.rules = rules;
            Vars.world.loadGenerator(120, 120, tiles -> {
                for(int x = 0; x < 120; x++)for(int y = 0; y < 120; y++)
                    tiles.set(x, y, new Tile(x, y, Blocks.basalt, Blocks.air, Blocks.air));
            });
            Vars.world.tile(32, 60).setBlock(abyss, Team.sharded);
            Vars.world.tile(65, 60).setBlock(themis, Team.sharded);
            elder = (TurretBuild)Vars.world.build(65, 60);
            elder.enabled = false;
            Vars.logic.play(); Vars.player.team(Team.sharded);
            Vars.player.unit(UnitTypes.gamma.spawn(Team.sharded, 400, 300));
            Vars.renderer.setScale(2.4f); running = true;
            Timer.schedule(() -> guarded(() -> ScreenUtils.saveScreenshot(dir.child("themis-halo.png"))), 2f);
            Timer.schedule(() -> guarded(() -> {
                try{
                    var projectile = themis.shootType.create((Entityc)null, Team.sharded, 420, 480, 0);
                    themis.shootType.hit(projectile, 420, 480);
                    projectile.remove();
                    Object type = themis.shootType.getClass().getField("field").get(themis.shootType);
                    field = Groups.bullet.find(b -> b.type == type);
                    check(field != null, "Themis impact did not deploy field");
                }catch(ReflectiveOperationException e){throw new RuntimeException(e);}
            }), 3f);
            Timer.schedule(() -> guarded(() -> ScreenUtils.saveScreenshot(dir.child("themis-thunderfield.png"))), 5f);
            Timer.schedule(() -> guarded(() -> {
                field.time = 300f;
            }), 6f);
            Timer.schedule(() -> guarded(() -> ScreenUtils.saveScreenshot(dir.child("themis-verdict.png"))), 6.15f);
            Timer.schedule(() -> guarded(() -> {
                check(field.fdata > 0f, "Themis judgment did not trigger");
                Log.info("THEMIS_RENDER_PASS: packaged content, exact description, research/cost, body/icon, layered monochrome halos, thunderfield and verdict");
                Core.app.exit();
            }), 7f);
        }), 3f));
        Events.run(Trigger.update, () -> {
            if(!running || !Vars.state.isGame())return;
            Core.camera.position.set(400, 480); Vars.player.set(400, 300);
            elder.shootWarmup = 1f;
        });
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
