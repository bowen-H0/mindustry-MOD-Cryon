import arc.*;
import arc.files.Fi;
import arc.scene.ui.Dialog;
import arc.struct.IntSet;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.Tile;

/** Native game simulation: T4 guided salvo, then T5 plasma-only orbital hits and all beam mounts. */
public final class OrbitalFleetRenderCheck{
    static Fi data;
    static Unit owner, target;
    static UnitType carrier;
    static StatusEffect durable;
    static int stage;
    static IntSet missiles = new IntSet();
    static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }
    static void guarded(Runnable action){ try{ action.run(); }catch(Throwable error){ Log.err(error); System.exit(1); } }
    static Unit enemy(UnitType type, float x, float y){
        Unit unit = type.spawn(Team.crux, x, y); unit.health = 10000f;
        unit.apply(durable, 10000f); unit.apply(StatusEffects.disarmed, 10000f); unit.apply(StatusEffects.unmoving, 10000f);
        return unit;
    }
    public static void main(String[] args){
        data = Fi.get(System.getProperty("mindustry.data.dir"));
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlaunch.jar"));
        Events.on(ClientLoadEvent.class, event -> Timer.schedule(() -> guarded(() -> {
            for(var e : Core.scene.root.getChildren().copy()) if(e instanceof Dialog dialog){ dialog.clearActions(); dialog.hide(null); }
            Core.graphics.setFullscreen(false); Core.graphics.setWindowSize(1100, 760);
            Vars.logic.reset(); Vars.state.rules.waves = Vars.state.rules.waveTimer = Vars.state.rules.canGameOver = false;
            Vars.state.rules.unitCap = 100;
            Vars.world.loadGenerator(130, 110, tiles -> {
                for(int x = 0; x < 130; x++) for(int y = 0; y < 110; y++) tiles.set(x, y, new Tile(x, y, Blocks.stone, Blocks.air, Blocks.air));
                tiles.get(20,20).setBlock(Blocks.coreShard, Team.sharded);
            });
            Vars.logic.play(); Vars.player.team(Team.sharded);
            Vars.player.unit(UnitTypes.gamma.spawn(Team.sharded, 250, 250));
            durable = new StatusEffect("orbital-capital-test-durable"); durable.healthMultiplier = 1000f;
            UnitType arsenal = Vars.content.unit("cryon-aphelion"); carrier = Vars.content.unit("cryon-firmament");
            check(Core.atlas.isFound(arsenal.region) && Core.atlas.isFound(carrier.region)
                && Core.atlas.isFound(arsenal.uiIcon) && Core.atlas.isFound(carrier.uiIcon), "capital ship sprites/icons missing");
            owner = arsenal.spawn(Team.sharded, 340, 400); target = enemy(UnitTypes.flare, 650, 400); stage = 1;
            Vars.renderer.setScale(3.5f); Core.settings.put("minimap", false);
            Timer.schedule(() -> guarded(() -> ScreenUtils.saveScreenshot(data.child("aphelion-sequential-salvo.png"))), 1.1f);
            Timer.schedule(() -> guarded(() -> {
                check(missiles.size >= 8 && target.health < 10000f, "T4 native salvo failed to launch/home/damage: shots=" + missiles.size + " hp=" + target.health);
                for(Unit unit : Groups.unit.copy()) if(unit != Vars.player.unit()) unit.remove();
                for(Bullet b : Groups.bullet.copy()) b.remove();
                for(Weapon weapon : carrier.weapons) if(weapon.continuous) weapon.aiControllable = false;
                owner = carrier.spawn(Team.sharded, 380, 400);
                target = enemy(Vars.content.unit("cryon-startrail"), 620, 400); stage = 2;
            }), 6f);
            Timer.schedule(() -> guarded(() -> {
                check(target.health < 10000f, "native plasma physics failed to collide with orbital target");
                check(Groups.bullet.count(b -> b.owner == owner && b.type.getClass().getSimpleName().equals("OmniBurnBeamBulletType")) == 0,
                    "plasma-only test was contaminated by beam damage");
                ScreenUtils.saveScreenshot(data.child("firmament-plasma-orbital-hit.png"));
                for(Weapon weapon : carrier.weapons) weapon.aiControllable = true;
                stage = 3;
            }), 10f);
            Timer.schedule(() -> guarded(() -> {
                int beams = Groups.bullet.count(b -> b.owner == owner && b.type.getClass().getSimpleName().equals("OmniBurnBeamBulletType"));
                check(beams == 3, "native carrier did not sustain all three beam mounts: " + beams);
                check(owner.isCommandable() && owner.playerControllable(), "carrier lost ordinary controls");
                ScreenUtils.saveScreenshot(data.child("firmament-full-armament.png"));
                Log.info("ORBITAL_FLEET_RENDER_PASS: new hulls/icons, native eight-missile guided salvo, native plasma-only orbital collision, three sustained beam mounts, normal command/possession flags");
                Core.app.exit();
            }), 12f);
        }), 3f));
        Events.run(Trigger.update, () -> {
            if(owner == null || !Vars.state.isGame()) return;
            Core.camera.position.set(490, 390);
            if(stage == 1) for(Bullet b : Groups.bullet) if(b.owner == owner && b.type.getClass().getSimpleName().equals("OrbitalSalvoMissile")) missiles.add(b.id);
        });
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
