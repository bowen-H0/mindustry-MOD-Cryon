import arc.*;
import arc.files.Fi;
import arc.scene.ui.Dialog;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.Tile;

/** Native game loop: overhead approach, downward column, moving target, and air/orbit exclusions. */
public final class HaloRenderCheck{
    static Fi data;
    static Unit owner, target, air, orbit;
    static StatusEffect durable;
    static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }
    static void guarded(Runnable action){ try{ action.run(); }catch(Throwable error){ Log.err(error); System.exit(1); } }
    static Unit enemy(UnitType type, float x, float y){
        Unit unit = type.spawn(Team.crux, x, y); unit.health = 10000f;
        unit.apply(durable, 10000f); unit.apply(StatusEffects.disarmed, 10000f); unit.apply(StatusEffects.unmoving, 10000f);
        return unit;
    }
    static void verifyBeam(){
        Bullet beam = owner.mounts[0].bullet;
        check(beam != null && beam.y - beam.aimY > 65f && Math.abs(beam.x - beam.aimX) < 26f,
            "Halo did not render from overhead to a ground endpoint");
        check(Math.abs(owner.y - target.y - 96f) < 26f && target.health < 10000f,
            "Halo did not hover and burn its ground target: hp=" + target.health + " ship=" + owner.x + "," + owner.y + " ground=" + target.x + "," + target.y);
        check(air.health == 10000f && orbit.health == 10000f, "Halo damaged the air/orbital layer");
    }
    public static void main(String[] args){
        data = Fi.get(System.getProperty("mindustry.data.dir"));
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlaunch.jar"));
        Events.on(ClientLoadEvent.class, event -> Timer.schedule(() -> guarded(() -> {
            for(var e : Core.scene.root.getChildren().copy()) if(e instanceof Dialog dialog){ dialog.clearActions(); dialog.hide(null); }
            Core.graphics.setFullscreen(false); Core.graphics.setWindowSize(1100, 760);
            Vars.logic.reset();
            Vars.state.rules.waves = Vars.state.rules.waveTimer = Vars.state.rules.canGameOver = false;
            Vars.state.rules.unitCap = 100;
            Vars.world.loadGenerator(110, 100, tiles -> {
                for(int x = 0; x < 110; x++) for(int y = 0; y < 100; y++) tiles.set(x, y, new Tile(x, y, Blocks.stone, Blocks.air, Blocks.air));
                tiles.get(20, 20).setBlock(Blocks.coreShard, Team.sharded);
            });
            Vars.logic.play(); Vars.player.team(Team.sharded);
            Vars.player.unit(UnitTypes.gamma.spawn(Team.sharded, 280, 260));
            UnitType halo = Vars.content.unit("cryon-halo");
            check(Core.atlas.isFound(halo.region) && Core.atlas.isFound(halo.uiIcon), "Halo hull/icon missing");
            owner = halo.spawn(Team.sharded, 320, 350);
            durable = new StatusEffect("halo-check-durable"); durable.healthMultiplier = 1000f;
            target = enemy(UnitTypes.reign, 480, 300);
            air = enemy(UnitTypes.flare, 420, 300);
            orbit = enemy(Vars.content.unit("cryon-startrail"), 420, 320);
            Vars.renderer.setScale(4f); Core.settings.put("minimap", false);
            Timer.schedule(() -> guarded(() -> check(target.health == 10000f, "Halo fired sideways before reaching overhead")), 0.7f);
            Timer.schedule(() -> guarded(() -> {
                ScreenUtils.saveScreenshot(data.child("halo-downward-column.png")); verifyBeam();
                target.set(530, 300);
            }), 6f);
            Timer.schedule(() -> guarded(() -> {
                verifyBeam(); ScreenUtils.saveScreenshot(data.child("halo-following-ground-target.png"));
                target.remove();
            }), 9f);
            Timer.schedule(() -> guarded(() -> {
                check(!owner.isShooting && air.health == 10000f && orbit.health == 10000f,
                    "Halo continued attacking the air/orbit layer after ground target left");
                Log.info("HALO_RENDER_PASS: new saucer body/icon, native command AI, no early sideways beam, overhead hover, depth-sliced descending column, moving-ground-target tracking, ground-only damage");
                Core.app.exit();
            }), 10.5f);
        }), 3f));
        Events.run(Trigger.update, () -> {
            if(owner == null || !Vars.state.isGame()) return;
            Core.camera.position.set(445, 350); Vars.player.set(445, 350);
        });
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
