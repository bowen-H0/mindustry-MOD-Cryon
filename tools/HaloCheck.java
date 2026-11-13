import arc.*;
import arc.math.geom.Vec2;
import arc.util.*;
import arc.util.io.*;
import java.io.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.Tile;

/** Runs Transit regression first, then the actual Halo weapon, hover controller and assembler callback. */
public final class HaloCheck extends TransitCheck{
    static void hoverTick(){
        tick(true);
        owner.move(owner.vel.x, owner.vel.y);
        owner.vel.scl(1f - owner.type.drag);
        index();
    }
    public static void main(String[] args){
        try{ run(); halo(); System.exit(0); }catch(Throwable error){ error.printStackTrace(); System.exit(1); }
    }
    static void halo() throws Exception{
        ClassLoader loader = Vars.mods.locateMod("cryon").main.getClass().getClassLoader();
        Class<?> orbital = loader.loadClass("cryon.Content.OrbitalUnits");
        orbital.getMethod("configureProduction").invoke(null);
        orbital.getMethod("configureProduction").invoke(null);
        UnitType halo = Vars.content.unit("cryon-halo");
        check(halo != null && halo.flying && halo.targetGround && !halo.targetAir, "missing ground-only Halo");
        check(halo.techNode != null && halo.techNode.parent.content == transit, "Halo research is not after Transit");
        check(java.util.Objects.equals(halo.engineColor, startrail.engineColor), "default orbital engine color changed");
        var assembler = Vars.content.block("cryon-t3universal-assembler");
        assembler.getClass().getMethod("initCapacities").invoke(assembler);
        var plans = (arc.struct.Seq<?>)field(assembler, "plans");
        int planIndex = -1, found = 0;
        for(int i = 0; i < plans.size; i++) if(field(plans.get(i), "unit") == halo){ planIndex = i; found++; }
        check(found == 1, "Halo assembler recipe duplicated/missing");
        Object plan = plans.get(planIndex);
        var requirements = (arc.struct.Seq<PayloadStack>)field(plan, "requirements");
        check(requirements.first().item == transit && requirements.first().amount == 2, "Halo must consume two T2 orbital units");

        Vars.logic.reset(); Vars.state.rules.unitCap = 100; Vars.state.rules.canGameOver = false;
        Vars.world.loadGenerator(160, 120, tiles -> {
            for(int x = 0; x < 160; x++) for(int y = 0; y < 120; y++) tiles.set(x, y, new Tile(x, y, Blocks.stone, Blocks.air, Blocks.air));
            tiles.get(10, 10).setBlock(Blocks.coreShard, Team.sharded);
        });
        Vars.state.set(mindustry.core.GameState.State.playing); Time.setDeltaProvider(() -> 1f);
        Vars.world.tile(25, 20).setBlock(assembler, Team.sharded);
        Building build = Vars.world.build(25, 20);
        build.getClass().getField("currentPlan").setInt(build, planIndex);
        PayloadSeq payloads = (PayloadSeq)field(build, "blocks");
        for(PayloadStack cost : requirements) payloads.add(cost.item, cost.amount);
        for(LiquidStack cost : (LiquidStack[])field(plan, "liquidReq")) build.liquids.add(cost.liquid, 100f);
        build.getClass().getMethod("spawned").invoke(build);
        check(Groups.unit.contains(unit -> unit.type == halo) && payloads.isEmpty(), "assembler callback failed to create Halo/consume payloads");
        for(Unit unit : Groups.unit.copy()) unit.remove(); build.tile.remove();

        owner = spawn(halo, Team.sharded, 300, 350); beam = null;
        target = spawn(UnitTypes.reign, Team.crux, 470, 300);
        Unit air = spawn(UnitTypes.flare, Team.crux, 340, 350);
        Unit orbit = spawn(startrail, Team.crux, 350, 350);
        Unit friend = spawn(UnitTypes.dagger, Team.sharded, 470, 300);
        Unit ally = spawn(UnitTypes.dagger, Team.green, 470, 300);
        Vars.state.rules.tags.put("cryon-allied-teams", Integer.toString(Team.green.id)); index();
        for(int i = 0; i < 30; i++) hoverTick();
        check(target.health == 100000f, "Halo fired a flat long-range beam before reaching overhead");
        for(int i = 0; i < 300; i++) hoverTick();
        check(beam != null && target.health < 100000f, "Halo did not approach, hover and burn its ground target");
        check(Math.abs(owner.x - target.x) < 12f && Math.abs(owner.y - target.y - 96f) < 12f,
            "Halo hovered alongside target instead of above it: " + owner.x + "," + owner.y);
        check(beam.y - beam.aimY > 75f && Math.abs(beam.x - beam.aimX) < 12f, "beam was not projected downward");
        check(air.health == 100000f && orbit.health == 100000f && friend.health == 100000f && ally.health == 100000f,
            "ground beam damaged air/orbit/friendly units");
        target.set(520, 320); index();
        float before = target.health; beam.type.update(beam);
        check((boolean)field(beam.data, "active") && beam.aimX == target.x && beam.aimY == target.y,
            "beam lost a moving target inside the hover allowance");
        for(int i = 0; i < 180; i++) hoverTick();
        check(target.health < before && Math.abs(owner.y - target.y - 96f) < 12f, "Halo failed to follow moving ground target");

        var hoverPoint = beam.type.getClass().getMethod("hoverPoint", Unit.class, Teamc.class, Vec2.class);
        target.remove();
        Vars.world.tile(70, 40).setBlock(Blocks.coreShard, Team.crux);
        Building enemyCore = Vars.world.build(70, 40); enemyCore.health = enemyCore.maxHealth = 100000f;
        Vec2 station = (Vec2)hoverPoint.invoke(beam.type, owner, enemyCore, new Vec2());
        owner.set(station); owner.vel.setZero(); index();
        for(int i = 0; i < 100; i++) hoverTick();
        check(enemyCore.health < 100000f, "Halo did not attack enemy ground structures");
        enemyCore.tile.remove();
        for(int i = 0; i < 100; i++) hoverTick();
        check(owner.mounts[0].target == null && !owner.isShooting, "Halo AI acquired ordinary air/orbital units");
        for(Unit invalid : new Unit[]{air, orbit, friend, ally}){
            station = (Vec2)hoverPoint.invoke(halo.weapons.first().bullet, owner, invalid, new Vec2());
            owner.set(station); Bullet shot = force(invalid); before = invalid.health;
            for(int i = 0; i < 30; i++){ Time.update(); shot.type.update(shot); }
            check(invalid.health == before && !(boolean)field(shot.data, "active"), "manual fire bypassed Halo target filter");
            shot.remove();
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); owner.write(Writes.get(new DataOutputStream(bytes)));
        Unit restored = (Unit)EntityMapping.map(owner.classId()).get();
        restored.read(Reads.get(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        check(restored.type == halo && !restored.checkTarget(true, true), "Halo lost orbital identity on serialization");
        controlAndMovingAim(halo);
        System.out.println("HALO_PASS: T3 recipe/production callback, research, overhead approach/hover, no early flat beam, downward origin/endpoint, ground-only damage, moving target reacquisition, structures, ally/manual filters, serialization; Transit regression also passed");
    }

    static void controlAndMovingAim(UnitType halo) throws Exception{
        for(Unit unit : Groups.unit.copy()) unit.remove();
        for(Bullet bullet : Groups.bullet.copy()) bullet.remove();
        owner = spawn(halo, Team.sharded, 300, 396); beam = null;
        target = spawn(UnitTypes.reign, Team.crux, 310, 300);
        index();
        for(int i = 0; i < 120; i++) hoverTick();
        float hp = target.health;
        int tracked = 0;
        for(int i = 0; i < 240; i++){
            target.vel.set(0.65f, 0f);
            target.move(0.65f, 0f); index(); hoverTick();
            check(owner.mounts[0].aimX == target.x && owner.mounts[0].aimY == target.y,
                "AI led a zero-travel-time beam away from the moving target");
            if(beam != null && (boolean)field(beam.data, "active")) tracked++;
        }
        check(target.health < hp && tracked > 200, "beam repeatedly lost a continuously moving target: tracked=" + tracked);
        Unit chosen = spawn(UnitTypes.reign, Team.crux, owner.x - 15, owner.y - 50); index();
        for(int i = 0; i < 50; i++) hoverTick();
        check(owner.mounts[0].target == target, "AI flickered to a nearer target while its lock was valid");
        ((mindustry.ai.types.CommandAI)owner.controller()).commandTarget(chosen);
        for(int i = 0; i < 40; i++) hoverTick();
        check(owner.mounts[0].target == chosen, "explicit attack order ignored selected ground target");

        // Use the real possession handler, then aim in four directions without the old 96-unit offset gate.
        Player player = Player.create(); player.team(Team.sharded); player.add(); Vars.player = player;
        player.unit(UnitTypes.gamma.spawn(Team.sharded, 200, 200));
        mindustry.input.InputHandler.unitControl(player, owner);
        check(player.unit() == owner && owner.isPlayer(), "native player possession rejected Halo");
        for(Unit unit : Groups.unit.copy()) if(unit != owner) unit.remove();
        owner.set(500, 400); owner.vel.setZero();
        target = spawn(UnitTypes.reign, Team.crux, 600, 400);
        Bullet shot = force(target);
        for(float[] aim : new float[][]{{600,400}, {500,500}, {400,400}, {500,300}}){
            target.set(aim[0], aim[1]); index(); owner.aim(target); hp = target.health;
            for(int i = 0; i < 20; i++){ Time.update(); shot.type.update(shot); }
            check(target.health < hp && field(shot.data, "target") == target
                && shot.aimX == target.x && shot.aimY == target.y, "possessed Halo cannot aim normally in direction " + java.util.Arrays.toString(aim));
        }
        chosen = spawn(UnitTypes.reign, Team.crux, 560, 450); index();
        hp = target.health; float chosenHp = chosen.health;
        owner.aim(chosen); // mount.target deliberately still references the first target.
        for(int i = 0; i < 20; i++){ Time.update(); shot.type.update(shot); }
        check(chosen.health < chosenHp && target.health == hp && field(shot.data, "target") == chosen,
            "manual cursor switch kept burning the previous target");
        hp = chosen.health;
        owner.aim(440, 460);
        for(int i = 0; i < 20; i++){ Time.update(); shot.type.update(shot); }
        check((boolean)field(shot.data, "active") && field(shot.data, "target") == null
            && shot.aimX == 440 && shot.aimY == 460 && chosen.health == hp, "empty cursor aim retained lock or gave no beam feedback");
        owner.aim(1200, 400); shot.type.update(shot);
        check(shot.dst(shot.aimX, shot.aimY) <= halo.range + 0.01f, "manual aiming exceeded normal weapon range");
        Vars.world.tile(68, 50).setBlock(Blocks.thoriumWall, Team.crux);
        Building wall = Vars.world.build(68, 50); hp = wall.health; owner.aim(wall);
        for(int i = 0; i < 20; i++){ Time.update(); shot.type.update(shot); }
        check(wall.health < hp, "manual ground beam could not hit a wall under the cursor");
        owner.controlWeapons(false, false); shot.type.update(shot);
        check(!(boolean)field(shot.data, "active"), "manual release did not stop beam");
        player.clearUnit(); player.remove(); Vars.player = null;
        shot.remove(); owner.mounts[0].bullet = null; owner.resetController();
        ((mindustry.ai.types.CommandAI)owner.controller()).commandTarget(wall);
        hp = wall.health;
        for(int i = 0; i < 180; i++) hoverTick();
        check(wall.health < hp, "AI explicit attack could not damage an otherwise non-auto-targeted wall");
        System.out.println("HALO_CONTROL_PASS: continuous moving target with live aim, stable AI target, explicit attack order, native possession, four-direction manual aiming, cursor target switching, empty-ground feedback, range clamp, manual wall damage, release");
    }
}
