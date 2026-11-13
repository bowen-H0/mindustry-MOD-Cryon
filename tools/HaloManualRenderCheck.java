import arc.*;
import arc.files.Fi;
import arc.input.*;
import arc.math.geom.Vec2;
import arc.mock.MockInput;
import arc.scene.ui.Dialog;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.input.InputHandler;
import mindustry.input.Binding;
import mindustry.type.StatusEffect;
import mindustry.world.Tile;

/** Exercises actual DesktopInput and Player control, not a manually updated AI weapon mount. */
public final class HaloManualRenderCheck{
    static Unit owner, target;
    static Fi data;
    static Input original;
    static AimInput scripted;
    static class AimInput extends MockInput{
        float x = 470, y = 370;
        boolean fire, right, tap, release;
        final Vec2 screen = new Vec2();
        @Override public int mouseX(){ return Math.round(Core.camera.project(screen.set(x, y)).x); }
        @Override public int mouseY(){ return Math.round(Core.camera.project(screen.set(x, y)).y); }
        @Override public int mouseX(int pointer){ return mouseX(); }
        @Override public int mouseY(int pointer){ return mouseY(); }
        @Override public boolean keyDown(KeyBind bind){ return bind == Binding.select && fire; }
        @Override public boolean keyDown(KeyCode key){ return key == KeyCode.mouseLeft && fire || key == KeyCode.d && right; }
        @Override public boolean keyTap(KeyBind bind){ return bind == Binding.select && tap; }
        @Override public boolean keyTap(KeyCode key){ return key == KeyCode.mouseLeft && tap; }
        @Override public boolean keyRelease(KeyBind bind){ return bind == Binding.select && release; }
        @Override public float axis(KeyBind bind){ return bind == Binding.moveX && right ? 1f : 0f; }
        @Override public boolean isTouched(){ return fire; }
        @Override public boolean isTouched(int pointer){ return pointer == 0 && fire; }
    }
    static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }
    static void guarded(Runnable action){ try{ action.run(); }catch(Throwable error){ Core.input = original == null ? Core.input : original; Log.err(error); System.exit(1); } }
    static Object field(Object object, String key) throws Exception{ return object.getClass().getField(key).get(object); }
    public static void main(String[] args){
        data = Fi.get(System.getProperty("mindustry.data.dir"));
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlaunch.jar"));
        Events.on(ClientLoadEvent.class, event -> Timer.schedule(() -> guarded(() -> {
            for(var e : Core.scene.root.getChildren().copy()) if(e instanceof Dialog dialog){ dialog.clearActions(); dialog.hide(null); }
            Core.graphics.setFullscreen(false); Core.graphics.setWindowSize(1100, 760);
            Vars.logic.reset(); Vars.state.rules.waves = Vars.state.rules.canGameOver = false; Vars.state.rules.unitCap = 100;
            Vars.world.loadGenerator(100, 100, tiles -> {
                for(int x = 0; x < 100; x++) for(int y = 0; y < 100; y++) tiles.set(x, y, new Tile(x, y, Blocks.stone, Blocks.air, Blocks.air));
                tiles.get(20, 20).setBlock(Blocks.coreShard, Team.sharded);
            });
            Vars.logic.play(); Vars.player.team(Team.sharded);
            Vars.player.unit(UnitTypes.gamma.spawn(Team.sharded, 300, 260));
            owner = Vars.content.unit("cryon-halo").spawn(Team.sharded, 320, 350);
            target = UnitTypes.reign.spawn(Team.crux, 470, 370);
            StatusEffect durable = new StatusEffect("halo-manual-check-durable"); durable.healthMultiplier = 100f;
            target.apply(durable, 10000f); target.apply(StatusEffects.disarmed, 10000f); target.apply(StatusEffects.unmoving, 10000f);
            target.health = 10000f;
            Vars.renderer.setScale(4f); Core.settings.put("minimap", false); Core.settings.put("detach-camera", false);
            InputHandler.unitControl(Vars.player, owner);
            check(Vars.player.unit() == owner && owner.isPlayer(), "native possession failed");
            for(Unit unit : Groups.unit.copy()) if(unit != owner && unit != target) unit.remove();
            original = Core.input; scripted = new AimInput(); Core.input = scripted;
            Timer.schedule(() -> guarded(() -> {
                scripted.fire = scripted.right = scripted.tap = true;
                Vars.control.input.update();
                check(Vars.player.shooting, "DesktopInput did not accept mouse press: canShoot=" + Vars.control.input.canShoot());
            }), 0.35f);
            Timer.schedule(() -> guarded(() -> {
                check(owner.x > 335f, "actual DesktopInput movement did not move possessed Halo: " + owner.x);
                Bullet b = owner.mounts[0].bullet;
                ScreenUtils.saveScreenshot(data.child("halo-possessed-move-and-fire.png"));
                check(b != null && target.health < 10000f && Math.abs(b.aimX - target.x) < 2f
                    && Math.abs(b.aimY - target.y) < 2f, "actual mouse firing missed ground target outside old hover gate: bullet=" + b
                        + " shooting=" + owner.isShooting + " mount=" + owner.mounts[0].shoot + " aim=" + owner.aimX + "," + owner.aimY
                        + " hp=" + target.health + " mouse=" + Core.input.mouseWorldX() + "," + Core.input.mouseWorldY()
                        + " canShoot=" + Vars.control.input.canShoot() + " command=" + Vars.control.input.commandMode
                        + " block=" + Vars.control.input.block + " config=" + Vars.control.input.onConfigurable()
                        + " drop=" + Vars.control.input.isDroppingItem() + " building=" + owner.activelyBuilding() + " mining=" + owner.mining());
                ScreenUtils.saveScreenshot(data.child("halo-possessed-move-and-fire.png"));
                scripted.right = false; scripted.x = 500; scripted.y = 450;
            }), 1.5f);
            Timer.schedule(() -> guarded(() -> {
                try{
                    Bullet b = owner.mounts[0].bullet;
                    check(b != null && (boolean)field(b.data, "active") && field(b.data, "target") == null
                        && Math.abs(b.aimX - scripted.x) < 2f && Math.abs(b.aimY - scripted.y) < 2f,
                        "actual mouse move did not switch beam to empty-ground aim feedback");
                    ScreenUtils.saveScreenshot(data.child("halo-possessed-empty-aim.png"));
                    scripted.fire = false; scripted.release = true;
                    Vars.control.input.update();
                }catch(Exception error){ throw new RuntimeException(error); }
            }), 2.2f);
            Timer.schedule(() -> guarded(() -> {
                check(!owner.isShooting && owner.mounts[0].bullet == null, "actual mouse release did not stop the beam");
                Core.input = original;
                Log.info("HALO_MANUAL_RENDER_PASS: native possession, real DesktopInput movement, cursor aiming/firing without altitude gate, empty-ground visual feedback, mouse release");
                Core.app.exit();
            }), 3f);
        }), 3f));
        Events.run(Trigger.update, () -> { if(owner != null && Vars.state.isGame()) Core.camera.position.set(420, 365); });
        Events.run(Trigger.uiDrawEnd, () -> { if(scripted != null){ scripted.tap = false; scripted.release = false; } });
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
