import arc.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.Tile;

/** Real desktop GL smoke check; uses only an isolated temporary data directory. */
public class CrawlerRenderCheck{
    private static Unit fortress;
    private static int captures;
    public static void main(String[] args){
        Events.on(ClientLoadEvent.class, event -> Timer.schedule(() -> {
            Core.graphics.setFullscreen(false);
            Core.graphics.setWindowSize(1000, 700);
            Vars.logic.reset();
            Rules rules = new Rules();
            rules.waves = rules.waveTimer = rules.canGameOver = false;
            rules.unitCap = 100;
            rules.defaultTeam = Team.sharded;
            rules.waveTeam = Team.crux;
            Vars.state.rules = rules;
            Vars.world.loadGenerator(96, 96, tiles -> {
                for(int x = 0; x < 96; x++) for(int y = 0; y < 96; y++)
                    tiles.set(x, y, new Tile(x,y,Blocks.sand,Blocks.air,Blocks.air));
            });
            for(int i = 0; i < 4; i++){
                Vars.world.tile(58 + i * 3, 34).setBlock(Blocks.graphitePress,Team.crux,0);
                Vars.world.build(58 + i * 3, 34).health = 10000000;
            }
            Vars.logic.play();
            try{ checkProduction(); }catch(Exception error){ throw new RuntimeException(error); }
            UnitType type = Vars.content.unit("cryon-veritas");
            if(type == null || type.weapons.size != 13) throw new AssertionError("fortress not loaded");
            fortress = type.spawn(Team.sharded, 320, 320);
            fortress.rotation = 0;
            Vars.player.team(Team.sharded);
            Vars.player.unit(fortress);
            for(int i = 0; i < 3; i++){
                Unit target = UnitTypes.reign.spawn(Team.crux, 485 + i * 12, 295 + i * 25);
                target.health = target.maxHealth = 10000000;
                target.apply(StatusEffects.unmoving, 100000);
                target.apply(StatusEffects.disarmed, 100000);
            }
            Unit aircraft = UnitTypes.eclipse.spawn(Team.crux,420,380);
            aircraft.health = aircraft.maxHealth = 10000000;
            aircraft.apply(StatusEffects.unmoving,100000);
            aircraft.apply(StatusEffects.disarmed,100000);
            Vars.renderer.setScale(1f);
            for(int i = 0; i < 3; i++){
                final int shot = i;
                Timer.schedule(() -> {
                    ScreenUtils.saveScreenshot(Vars.dataDirectory.child("fortress-" + shot + ".png"));
                    captures++;
                }, 5 + i * 4);
            }
            Timer.schedule(() -> {
                if(captures != 3) throw new AssertionError("missing screenshots");
                Log.info("CRAWLER_RENDER_PASS: 13 mounts, orange native effects, ground and air targets");
                Core.app.exit();
            }, 15);
        }, 2));
        Events.run(Trigger.update, () -> {
            if(fortress == null || !fortress.isAdded()) return;
            fortress.aim(495,320);
            fortress.controlWeapons(true,true);
        });
        mindustry.desktop.DesktopLauncher.main(args);
    }
    private static void checkProduction() throws Exception{
        mindustry.world.blocks.units.UnitFactory factory = (mindustry.world.blocks.units.UnitFactory)Vars.content.block("cryon-mechanical-factory");
        mindustry.world.blocks.units.UnitAssembler assembler = (mindustry.world.blocks.units.UnitAssembler)Vars.content.block("cryon-mechanical-assembler");
        if(!factory.plans.contains(p -> p.unit.name.equals("cryon-benignitas"))
                || !assembler.plans.contains(p -> p.unit.name.equals("cryon-salus"))) throw new AssertionError("broken T1/T2 chain");
        String[] outputs = {"propugno","ratio","veritas"};
        String[] previous = {"salus","propugno","ratio"};
        for(int i = 0; i < 3; i++){
            mindustry.world.Block block = Vars.content.block("cryon-t" + (i + 3) + "universal-assembler");
            arc.struct.Seq<?> plans = (arc.struct.Seq<?>)block.getClass().getField("plans").get(block);
            UnitType output = Vars.content.unit("cryon-" + outputs[i]);
            if(output.techNode == null || output.techNode.parent.content != block) throw new AssertionError("missing crawler research node");
            int chosen = -1;
            for(int j = 0; j < plans.size; j++) if(plans.get(j).getClass().getField("unit").get(plans.get(j)) == output) chosen = j;
            if(chosen < 0) throw new AssertionError("missing production plan: " + outputs[i]);
            Object plan = plans.get(chosen);
            Vars.world.tile(10,10 + i * 26).setBlock(block,Team.sharded,0);
            Building build = Vars.world.build(10,10 + i * 26);
            build.getClass().getField("currentPlan").setInt(build,chosen);
            UnitType input = Vars.content.unit("cryon-" + previous[i]);
            if(!build.acceptPayload(build,new mindustry.world.blocks.payloads.UnitPayload(input.create(Team.sharded))))
                throw new AssertionError("previous crawler payload rejected");
            arc.struct.Seq<?> requirements = (arc.struct.Seq<?>)plan.getClass().getField("requirements").get(plan);
            mindustry.type.PayloadSeq inventory = (mindustry.type.PayloadSeq)build.getClass().getField("blocks").get(build);
            for(Object req : requirements){
                PayloadStack stack = (PayloadStack)req;
                inventory.add(stack.item,stack.amount);
            }
            LiquidStack[] liquids = (LiquidStack[])plan.getClass().getField("liquidReq").get(plan);
            for(LiquidStack liquid : liquids){
                if(!block.liquidFilter[liquid.liquid.id] || !build.acceptLiquid(build,liquid.liquid))
                    throw new AssertionError("production liquid rejected");
                build.liquids.add(liquid.liquid,200f);
            }
            build.efficiency = 1;
            if(build.power != null) build.power.status = 1;
            build.getClass().getField("constructorSatisfaction").setFloat(build,1);
            arc.math.geom.Vec2 spawn = (arc.math.geom.Vec2)build.getClass().getMethod("getUnitSpawn").invoke(build);
            int count = block.getClass().getField("dronesCreated").getInt(block);
            UnitType droneType = (UnitType)block.getClass().getField("droneType").get(block);
            float area = block.getClass().getField("areaSize").getInt(block);
            @SuppressWarnings("unchecked") arc.struct.Seq<Unit> drones = (arc.struct.Seq<Unit>)build.getClass().getField("units").get(build);
            for(int d = 0; d < count; d++){
                Unit drone = droneType.create(Team.sharded);
                arc.math.geom.Vec2 point = new arc.math.geom.Vec2().trns(d * 90 + 45,area / 2f * arc.math.Mathf.sqrt2 * Vars.tilesize).add(spawn);
                drone.set(point); drone.rotation = d * 90 + 225; drone.add(); drones.add(drone);
            }
            float time = plan.getClass().getField("time").getFloat(plan);
            build.getClass().getField("progress").setFloat(build,1f - 0.1f / time);
            Vars.state.teams.updateTeamStats();
            int before = Groups.unit.count(u -> u.type == output);
            float delta = Time.delta;
            Time.delta = 1;
            build.updateTile();
            Time.delta = delta;
            if(Groups.unit.count(u -> u.type == output) != before + 1) throw new AssertionError("crawler assembly did not complete: " + outputs[i]);
            if(inventory.get(input) != 0) throw new AssertionError("previous units were not consumed");
            drones.each(Unit::remove); drones.clear();
            Groups.unit.copy().each(u -> { if(u.type == output) u.remove(); });
            build.tile.setAir();
            Log.info("CRAWLER_PRODUCTION_PASS: @, research, input payload, liquids, native assembly and consumption",outputs[i]);
        }
    }

}
