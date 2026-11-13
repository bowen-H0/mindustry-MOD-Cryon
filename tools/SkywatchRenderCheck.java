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
import mindustry.world.blocks.units.UnitFactory;

/** Real factory output must use its default support AI; no manually assigned Skywatch controller. */
public class SkywatchRenderCheck{
    static Fi dir;
    static Building target;
    static UnitFactory.UnitFactoryBuild factory;
    static UnitType skywatch;
    static boolean running, observedPending, factoryProduced, captured;
    static void check(boolean value, String message){ if(!value) throw new AssertionError(message); }
    static void guarded(Runnable action){ try{ action.run(); }catch(Throwable error){ Log.err(error); System.exit(1); } }

    public static void main(String[] args){
        dir = Fi.get(System.getProperty("mindustry.data.dir"));
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(dir.child("mods").child("cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(dir.child("mods").child("unitlaunch.jar"));
        Events.on(ClientLoadEvent.class, event -> Timer.schedule(() -> guarded(() -> {
            for(var element : Core.scene.root.getChildren().copy()) if(element instanceof Dialog dialog){ dialog.clearActions(); dialog.hide(null); }
            Core.graphics.setFullscreen(false); Core.graphics.setWindowSize(1100, 760);
            Vars.logic.reset(); Rules rules = new Rules();
            rules.waves = rules.waveTimer = rules.canGameOver = false;
            rules.unitCap = 100; rules.defaultTeam = Team.sharded; rules.waveTeam = Team.crux;
            Vars.state.rules = rules;
            Vars.world.loadGenerator(96, 96, tiles -> {
                for(int x = 0; x < 96; x++) for(int y = 0; y < 96; y++)
                    tiles.set(x, y, new Tile(x, y, Blocks.sand, Blocks.air, Blocks.air));
            });
            skywatch = Vars.content.unit("cryon-skywatch");
            check(skywatch != null && Core.atlas.isFound(skywatch.region) && Core.atlas.isFound(skywatch.uiIcon), "Missing Skywatch body/icon");
            UnitFactory pad = (UnitFactory)Vars.content.block("cryon-orbital-deployment-pad");
            check(Core.atlas.isFound(pad.region), "Missing deployment pad sprite");
            check(Core.atlas.isFound(pad.outRegion) && Core.atlas.isFound(pad.topRegion), "Missing pad output/top overlay");
            Vars.world.tile(15, 20).setBlock(Blocks.coreShard, Team.sharded);
            Vars.world.tile(22, 45).setBlock(Vars.content.block("cryon-orbital-power-node"), Team.sharded);
            Vars.world.tile(30, 45).setBlock(pad, Team.sharded);
            Vars.world.tile(38, 35).setBlock(Vars.content.block("cryon-beacon"), Team.sharded);
            Vars.world.tile(59, 35).setBlock(Blocks.thoriumWallLarge, Team.crux);
            target = Vars.world.build(59, 35); target.health = 100000f;
            factory = (UnitFactory.UnitFactoryBuild)Vars.world.build(30, 45);
            factory.currentPlan = 0; factory.enabled = false;
            Building power = Vars.world.build(22, 45);
            power.configureAny(factory.pos()); power.configureAny(Vars.world.build(38, 35).pos());
            power.power.graph.update();
            Vars.logic.play(); Vars.player.team(Team.sharded);
            Vars.player.unit(UnitTypes.gamma.spawn(Team.sharded, 330, 240));
            Vars.renderer.setScale(3f);
            running = true;
            Timer.schedule(() -> guarded(() -> {
                check(target.health == 100000f && observedPending, "Player beacon fired without factory-produced support");
                ScreenUtils.saveScreenshot(dir.child("skywatch-awaiting-support.png"));
                factory.enabled = true;
                UnitFactory.UnitPlan plan = pad.plans.first();
                for(ItemStack cost : plan.requirements) factory.items.add(cost.item, cost.amount);
                factory.progress = plan.time;
            }), 4f);
            Timer.schedule(() -> guarded(() -> {
                check(factoryProduced, "Real deployment pad did not launch Skywatch");
                check(target.health < 100000f, "Default Skywatch support AI delivered no light-column damage");
                check(captured, "No visible beacon beam effect");
                Unit unit = Groups.unit.find(value -> value.type == skywatch);
                check(unit != null, "Skywatch vanished");
                check(!unit.playerControllable() && !unit.isCommandable()
                        && unit.controller().getClass().getSimpleName().equals("SkywatchAI"), "Factory output can be controlled by players");
                ScreenUtils.saveScreenshot(dir.child("skywatch-support-overview.png"));
                Log.info("SKYWATCH_RENDER_PASS: factory output, default support AI, player marker waits, beam/impact, new sprites, damage=@ controller=@",
                        100000f - target.health, unit.controller().getClass().getSimpleName());
                Core.app.exit();
            }), 18f);
        }), 3f));
        Events.run(Trigger.update, () -> {
            if(!running || !Vars.state.isGame()) return;
            Core.camera.position.set(365, 315); Vars.player.set(365, 315);
            if(Groups.unit.contains(unit -> unit.type == skywatch)) factoryProduced = true;
            try{
                var cls = Vars.mods.mainLoader().loadClass("cryon.Type.BeaconBulletType");
                for(Object marker : (arc.struct.Seq<?>)cls.getField("markers").get(null))
                    if(marker.getClass().getField("supportRequired").getBoolean(marker)) observedPending = true;
            }catch(Exception error){ throw new RuntimeException(error); }
        });
        Events.run(Trigger.postDraw, () -> {
            if(!running || captured) return;
            for(Bullet bullet : Groups.bullet){
                if(bullet.type == skywatch.weapons.first().bullet && bullet.owner instanceof Unit
                        && bullet.data instanceof arc.math.geom.Vec2 end && bullet.dst(end) > 20f){
                    ScreenUtils.saveScreenshot(dir.child("skywatch-column.png")); captured = true; break;
                }
            }
        });
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
