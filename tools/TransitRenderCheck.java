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

/** Native game loop and unmodified player command controller; real orbital target and exclusions. */
public final class TransitRenderCheck{
    static Unit owner, target, air, ground;
    static Building wall;
    static Fi data;
    static boolean sawBeam;
    static mindustry.type.StatusEffect durable;
    static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }
    static void guarded(Runnable action){ try{ action.run(); }catch(Throwable error){ Log.err(error); System.exit(1); } }
    static Unit enemy(mindustry.type.UnitType type, float x, float y){
        Unit unit = type.spawn(Team.crux, x, y); unit.health = unit.maxHealth = 100000f;
        unit.apply(durable, 100000f);
        unit.apply(StatusEffects.disarmed, 100000f); unit.apply(StatusEffects.unmoving, 100000f); return unit;
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
            Vars.world.loadGenerator(100, 90, tiles -> {
                for(int x = 0; x < 100; x++) for(int y = 0; y < 90; y++) tiles.set(x, y, new Tile(x, y, Blocks.stone, Blocks.air, Blocks.air));
                tiles.get(30, 25).setBlock(Blocks.coreShard, Team.sharded);
                tiles.get(49, 39).setBlock(Blocks.thoriumWallLarge, Team.crux);
            });
            wall = Vars.world.build(49, 39);
            Vars.logic.play(); Vars.player.team(Team.sharded);
            Vars.player.unit(UnitTypes.gamma.spawn(Team.sharded, 290, 250));
            var transit = Vars.content.unit("cryon-transit");
            check(Core.atlas.isFound(transit.region) && Core.atlas.isFound(transit.uiIcon), "Transit sprite/icon missing");
            owner = transit.spawn(Team.sharded, 320, 350);
            durable = new mindustry.type.StatusEffect("transit-check-durable"); durable.healthMultiplier = 1000f;
            target = enemy(Vars.content.unit("cryon-startrail"), 460, 350);
            air = enemy(UnitTypes.flare, 370, 330);
            ground = enemy(UnitTypes.dagger, 430, 250);
            Vars.renderer.setScale(4f);
            Core.settings.put("minimap", false);
            Timer.schedule(() -> guarded(() -> {
                check(sawBeam && target.health < 100000f, "Native Transit failed to acquire/damage an orbital target");
                check(air.health == 100000f && ground.health == 100000f && wall.health == wall.maxHealth,
                    "Orbit-only beam damaged air/ground: air=" + air.health + " ground=" + ground.health + " wall=" + wall.health);
                ScreenUtils.saveScreenshot(data.child("transit-orbital-beam.png"));
                target.set(450, 420);
            }), 3f);
            Timer.schedule(() -> guarded(() -> {
                Bullet beam = owner.mounts[0].bullet;
                check(beam != null && arc.math.Mathf.dst(beam.aimX, beam.aimY, target.x, target.y) < 2f,
                    "Native beam endpoint did not follow moving orbital target");
                ScreenUtils.saveScreenshot(data.child("transit-moving-target.png"));
                target.remove();
            }), 4.5f);
            Timer.schedule(() -> guarded(() -> {
                check(!owner.isShooting && air.health == 100000f && ground.health == 100000f && wall.health == wall.maxHealth,
                    "Transit attacked conventional units after its orbital target disappeared");
                Log.info("TRANSIT_RENDER_PASS: new body/icon, default command AI, real continuous orbit-only beam, moving-target tracking, no ordinary air/ground/building damage, stops when no orbital enemies remain");
                Core.app.exit();
            }), 6f);
        }), 3f));
        Events.run(Trigger.update, () -> {
            if(owner == null || !Vars.state.isGame()) return;
            Core.camera.position.set(390, 345); Vars.player.set(390, 345);
            for(Bullet b : Groups.bullet) if(b.owner == owner && b.type.getClass().getSimpleName().equals("OrbitalBurnBeamBulletType")) sawBeam = true;
        });
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
