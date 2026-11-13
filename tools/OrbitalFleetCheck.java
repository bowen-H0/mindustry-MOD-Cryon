import arc.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import java.io.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.entities.bullet.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.Tile;

/** Packaged T4/T5 recipes, native staggered weapon fire, guided routes and all-layer projectile collisions. */
public final class OrbitalFleetCheck extends TransitCheck{
    static UnitType arsenal, carrier;
    static ClassLoader loader;
    static void clearCombat(){
        for(Unit unit : Groups.unit.copy()) unit.remove();
        for(Bullet bullet : Groups.bullet.copy()) bullet.remove();
        Time.clear(); beam = null;
    }
    public static void main(String[] args){
        try{ run(); fleet(); System.exit(0); }catch(Throwable error){ error.printStackTrace(); System.exit(1); }
    }
    static void fleet() throws Exception{
        loader = Vars.mods.locateMod("cryon").main.getClass().getClassLoader();
        var content = loader.loadClass("cryon.Content.OrbitalUnits");
        content.getMethod("configureProduction").invoke(null); content.getMethod("configureProduction").invoke(null);
        arsenal = Vars.content.unit("cryon-aphelion"); carrier = Vars.content.unit("cryon-firmament");
        UnitType halo = Vars.content.unit("cryon-halo");
        check(arsenal != null && carrier != null && arsenal.flying && carrier.flying, "missing orbital capitals");
        check(arsenal.techNode.parent.content == halo && carrier.techNode.parent.content == arsenal, "broken orbital T3/T4/T5 research");
        check(java.util.Objects.equals(arsenal.engineColor, startrail.engineColor)
            && java.util.Objects.equals(carrier.engineColor, startrail.engineColor), "default engine color changed");
        check(arsenal.weapons.size == 1 && arsenal.weapons.first().shoot.shots == 8, "T4 missing eight-missile salvo");
        check(carrier.weapons.size == 11 && carrier.weapons.count(w -> w.continuous) == 3, "T5 must have three beams and eight plasma guns");
        Vars.logic.reset(); Vars.state.rules.unitCap = 100; Vars.state.rules.canGameOver = false;
        Vars.world.loadGenerator(180, 140, tiles -> {
            for(int x = 0; x < 180; x++) for(int y = 0; y < 140; y++) tiles.set(x, y, new Tile(x, y, Blocks.stone, Blocks.air, Blocks.air));
            tiles.get(10,10).setBlock(Blocks.coreShard, Team.sharded);
        });
        Vars.state.set(mindustry.core.GameState.State.playing); Time.setDeltaProvider(() -> 1f);
        Vars.state.rules.tags.put("cryon-allied-teams", Integer.toString(Team.green.id));
        production(4, arsenal, halo); production(5, carrier, arsenal); clearCombat();

        owner = spawn(arsenal, Team.sharded, 300, 400);
        target = spawn(UnitTypes.flare, Team.crux, 600, 400); index();
        var gun = owner.mounts[0]; gun.reload = 0;
        IntSet seen = new IntSet(); IntSeq times = new IntSeq();
        for(int frame = 0; frame < 100; frame++){
            tick(true);
            for(Bullet bullet : Groups.bullet) if(bullet.owner == owner && seen.add(bullet.id)) times.add(frame);
        }
        check(times.size == 8, "native T4 weapon did not fire exactly eight missiles: " + times);
        for(int i = 1; i < times.size; i++) check(times.get(i) - times.get(i-1) >= 6 && times.get(i) - times.get(i-1) <= 8,
            "T4 fired simultaneously or lost its sequential timing: " + times);
        for(Bullet bullet : Groups.bullet) if(bullet.owner == owner){
            check((boolean)field(bullet.data, "cruising"), "T4 missile failed to leave rear/flank route");
            check(bullet.vel.len() > 0f, "T4 missile stopped during route");
        }
        var missile = arsenal.weapons.first().bullet;
        for(UnitType type : new UnitType[]{UnitTypes.flare, UnitTypes.reign}){
            clearCombat(); owner = spawn(arsenal, Team.sharded, 300, 400); target = spawn(type, Team.crux, 550, 400);
            Unit orbit = spawn(startrail, Team.crux, 550, 400);
            Unit ally = spawn(UnitTypes.reign, Team.green, 550, 400); index();
            Bullet b = missile.create(owner, owner.team, 300, 400, 0f, -1, 1, 1, null, null, target.x, target.y);
            check(b.x < owner.x && b.vel.len() > 0f, "T4 missile did not launch around the rear");
            check(b.collides(target) && !b.collides(orbit) && !b.collides(ally), "T4 collision layer/team filter failed");
            b.set(target); b.collision(target, target.x, target.y);
            check(target.health < 100000f && orbit.health == 100000f && ally.health == 100000f, "T4 direct/splash damage reached wrong layer/team");
        }
        clearCombat(); owner = spawn(arsenal, Team.sharded, 300, 400);
        Unit onlyOrbit = spawn(startrail, Team.crux, 500, 400); index();
        for(int i = 0; i < 100; i++) tick(true);
        check(owner.mounts[0].target == null && !owner.isShooting, "T4 acquired an orbital target");

        BulletType plasma = carrier.weapons.find(w -> !w.continuous).bullet;
        for(UnitType type : new UnitType[]{UnitTypes.flare, UnitTypes.reign, startrail}){
            clearCombat(); owner = spawn(carrier, Team.sharded, 300, 400); target = spawn(type, Team.crux, 500, 400);
            Unit friend = spawn(type, Team.sharded, 500, 400); Unit ally = spawn(type, Team.green, 500, 400); index();
            for(int i = 0; i < 90; i++){ Time.update(); owner.controller().updateUnit(); }
            check(java.util.Arrays.stream(owner.mounts).anyMatch(m -> m.target == target), "T5 AI failed layer " + type.name);
            Bullet b = plasma.create(owner, owner.team, target.x, target.y, 0f);
            check(b.collides(target) && !b.collides(friend) && !b.collides(ally), "T5 plasma collision failed layer/team " + type.name);
            b.collision(target, target.x, target.y);
            check(target.health < 100000f && friend.health == 100000f && ally.health == 100000f, "T5 plasma harmed wrong team/layer");
            target.health = 100000f;
            Bullet laser = force(target);
            for(int i = 0; i < 20; i++){ Time.update(); laser.type.update(laser); }
            check(target.health < 100000f && field(laser.data, "target") == target, "T5 beam failed layer " + type.name);
            laser.remove();
            Bullet normal = new BasicBulletType(5f, 20f).create(owner, owner.team, target.x, target.y, 0f);
            if(type == startrail) check(!normal.collides(target) && !target.checkTarget(true,true), "T5 leaked orbital access into ordinary bullets");
            normal.remove();
        }
        Vars.world.tile(65,50).setBlock(Blocks.coreShard, Team.crux);
        Building core = Vars.world.build(65,50); float hp = core.health;
        Bullet b = plasma.create(owner, owner.team, core.x, core.y, 0f);
        b.lastX = core.x - 40f; b.lastY = core.y; b.set(core.x + 40f, core.y);
        b.tileRaycast(core.tileX() - 5, core.tileY(), core.tileX() + 5, core.tileY());
        check(core.health < hp, "T5 plasma could not damage ground structures"); b.remove();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); owner.write(Writes.get(new DataOutputStream(bytes)));
        Unit restored = (Unit)EntityMapping.map(owner.classId()).get();
        restored.read(Reads.get(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        check(restored.type == carrier && !restored.checkTarget(true,true), "T5 lost orbital identity on save/load");
        System.out.println("ORBITAL_FLEET_PASS: T4/T5 research and assembly, real eight-shot stagger, flank route, T4 air/ground-only damage, T5 three beams/eight plasma guns, native collision and damage across all three layers, allied safety, default bullet scope isolation, serialization");
    }
    static void production(int tier, UnitType result, UnitType input) throws Exception{
        var assembler = Vars.content.block("cryon-t" + tier + "universal-assembler");
        assembler.getClass().getMethod("initCapacities").invoke(assembler);
        var plans = (Seq<?>)field(assembler, "plans"); int selected = -1, count = 0;
        for(int i = 0; i < plans.size; i++) if(field(plans.get(i), "unit") == result){ selected = i; count++; }
        check(count == 1, "missing/duplicate T" + tier + " plan");
        Object plan = plans.get(selected); var req = (Seq<PayloadStack>)field(plan, "requirements");
        check(req.first().item == input && req.first().amount == 2, "wrong T" + tier + " unit input");
        Vars.world.tile(25,25).setBlock(assembler, Team.sharded); Building build = Vars.world.build(25,25);
        build.getClass().getField("currentPlan").setInt(build, selected);
        PayloadSeq payloads = (PayloadSeq)field(build, "blocks");
        for(PayloadStack cost : req) payloads.add(cost.item, cost.amount);
        for(LiquidStack cost : (LiquidStack[])field(plan, "liquidReq")) build.liquids.add(cost.liquid, 100f);
        build.getClass().getMethod("spawned").invoke(build);
        check(Groups.unit.contains(u -> u.type == result) && payloads.isEmpty(), "T" + tier + " assembly failed to consume/create");
        build.tile.remove();
    }
}
