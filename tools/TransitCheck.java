import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.*;
import arc.util.io.*;
import java.io.*;
import java.nio.file.Files;
import mindustry.*;
import mindustry.ai.types.CommandAI;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.entities.bullet.BulletType;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.net.Net;
import mindustry.type.*;
import mindustry.world.Tile;
import mindustry.world.blocks.payloads.UnitPayload;
import mindustry.world.blocks.units.Reconstructor;

/** Packaged T2 reconstruction and native continuous weapon against all three combat layers. */
public class TransitCheck{
    static Unit owner, target;
    static Bullet beam;
    static UnitType transit, startrail;
    static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }
    static Object field(Object object, String name) throws Exception{ return object.getClass().getField(name).get(object); }
    static Unit spawn(UnitType type, Team team, float x, float y){
        Unit unit = type.spawn(team, x, y); unit.health = unit.maxHealth = 100000f; unit.armor = 0f; return unit;
    }
    static void index(){
        Vars.state.teams.updateTeamStats();
        for(Team team : new Team[]{Team.sharded, Team.crux, Team.green}){
            team.data().tree().clear();
            for(Unit unit : Groups.unit) if(unit.team == team) team.data().tree().insert(unit);
        }
    }
    static void tick(boolean ai){
        Time.delta = 1f; Time.update();
        if(ai) owner.controller().updateUnit();
        for(var mount : owner.mounts) mount.weapon.update(owner, mount);
        for(Bullet bullet : Groups.bullet.copy()) bullet.update();
        if(owner.mounts[0].bullet != null) beam = owner.mounts[0].bullet;
    }
    static Bullet force(Unit victim){
        if(owner.mounts[0].bullet != null) owner.mounts[0].bullet.remove();
        owner.controlWeapons(true, true); owner.aim(victim);
        var mount = owner.mounts[0]; mount.target = victim;
        Bullet result = mount.weapon.bullet.create(owner, owner.team, owner.x, owner.y, 0f, -1f, 1f, 1f, null, null, victim.x, victim.y);
        mount.bullet = result;
        return result;
    }
    public static void main(String[] args){
        try{ run(); System.exit(0); }catch(Throwable error){ error.printStackTrace(); System.exit(1); }
    }
    static void run() throws Exception{
        Core.app = new MockApplication(); Core.files = new MockFiles(); Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Core.settings = new Settings(); Core.settings.setDataDirectory(new Fi(Files.createTempDirectory("cryon-transit-").toFile()));
        Core.bundle = I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(Core.settings.getDataDirectory().child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(Core.settings.getDataDirectory().child("mods/unitlaunch.jar"));
        Vars.headless = true; Vars.loadLocales = false; Vars.net = new Net(null); Vars.platform = new mindustry.core.Platform(){};
        mindustry.core.Version.init(); Vars.init(); Vars.content.createBaseContent(); Vars.content.createModContent(); Vars.content.init();
        var mod = Vars.mods.locateMod("cryon"); check(mod != null && !mod.hasContentErrors(), "packaged content errors");
        ClassLoader loader = mod.main.getClass().getClassLoader();
        loader.loadClass("cryon.TechTree.CryonTechTree").getMethod("load").invoke(null); Timer.instance().clear();
        transit = Vars.content.unit("cryon-transit"); startrail = Vars.content.unit("cryon-startrail");
        check(transit != null && !transit.targetAir && !transit.targetGround && transit.flying, "Transit is not an orbit-only unit");
        check(transit.techNode != null && transit.techNode.parent.content == startrail, "missing T1 -> T2 research");
        check(java.util.Objects.equals(transit.engineColor, startrail.engineColor) && transit.flyingLayer == startrail.flyingLayer, "orbital defaults changed");
        var weapon = transit.weapons.first(); BulletType burn = weapon.bullet;
        check(weapon.alwaysContinuous && weapon.continuous && !burn.collidesAir && !burn.collidesGround,
            "Transit weapon is not a continuous orbit-only beam");
        check(!burn.absorbable && !burn.reflectable && !burn.hittable
            && loader.loadClass("cryon.Type.OrbitalProjectile").isInstance(burn), "orbital beam intercepted by barriers");
        Vars.logic = new Logic(); Vars.logic.reset(); Vars.state.rules.unitCap = 100;
        Vars.state.rules.canGameOver = false;
        Vars.world.loadGenerator(160, 100, tiles -> {
            for(int x = 0; x < 160; x++) for(int y = 0; y < 100; y++) tiles.set(x, y, new Tile(x, y, Blocks.stone, Blocks.air, Blocks.air));
            tiles.get(10, 10).setBlock(Blocks.coreShard, Team.sharded);
        });
        Vars.state.set(mindustry.core.GameState.State.playing); Time.setDeltaProvider(() -> 1f);
        Reconstructor factory = (Reconstructor)Vars.content.block("cryon-t2factory");
        check(factory.upgrades.contains(pair -> pair[0] == startrail && pair[1] == transit), "missing T2 upgrade recipe");
        Vars.world.tile(20, 20).setBlock(factory, Team.sharded);
        var build = (Reconstructor.ReconstructorBuild)Vars.world.build(20, 20);
        build.payload = new UnitPayload(startrail.create(Team.sharded)); build.progress = factory.constructTime;
        build.efficiency = 1f; build.power.status = 1f;
        for(var consume : factory.consumers) if(consume instanceof mindustry.world.consumers.ConsumeItems items)
            for(ItemStack cost : items.items) build.items.add(cost.item, cost.amount);
        build.updateTile();
        check(build.payload != null && build.payload.unit.type == transit, "native reconstruction did not produce Transit");
        build.tile.remove();

        owner = spawn(transit, Team.sharded, 300, 300);
        check(owner.controller() instanceof CommandAI, "player Transit lost native command support");
        Vars.state.rules.waves = true;
        Unit wave = transit.create(Team.crux);
        check(wave.controller().getClass().getSimpleName().equals("OrbitalCombatAI"), "enemy Transit lacks orbital AI");
        Vars.state.rules.waves = false;
        target = spawn(startrail, Team.crux, 440, 300);
        Unit air = spawn(UnitTypes.flare, Team.crux, 330, 300);
        Unit ground = spawn(UnitTypes.dagger, Team.crux, 335, 300);
        Unit friend = spawn(startrail, Team.sharded, 340, 300);
        Unit ally = spawn(startrail, Team.green, 345, 300);
        Vars.state.rules.tags.put("cryon-allied-teams", Integer.toString(Team.green.id));
        loader.loadClass("cryon.Features.MapAlliances").getMethod("install").invoke(null); index();
        for(int i = 0; i < 90 && beam == null; i++) tick(true);
        check(beam != null && owner.mounts[0].target == target, "native command AI did not select orbital enemy");
        for(int i = 0; i < 12; i++) tick(true);
        check(target.health < 100000f && air.health == 100000f && ground.health == 100000f
            && friend.health == 100000f && ally.health == 100000f, "beam damaged the wrong combat layer/team");
        float before = target.health;
        for(int i = 0; i < 60; i++) tick(true);
        float initialDamage = before - target.health;
        for(int i = 0; i < 120; i++) tick(true);
        before = target.health;
        for(int i = 0; i < 60; i++) tick(true);
        check(before - target.health > initialDamage * 1.3f, "sustained focus failed to ramp damage");
        target.set(440, 340); index(); tick(true);
        check(beam.aimX == target.x && beam.aimY == target.y, "beam endpoint failed to track moving orbital target");
        Unit second = spawn(transit, Team.crux, 430, 330); index();
        owner.mounts[0].target = second; beam.type.update(beam);
        check(field(beam.data, "target") == second && (float)field(beam.data, "focus") <= 1.1f, "target switch retained heat");
        owner.controlWeapons(false, false); before = second.health;
        for(int i = 0; i < 30; i++){ Time.update(); beam.type.update(beam); }
        check(second.health == before && !(boolean)field(beam.data, "active") && (float)field(beam.data, "focus") == 0f,
            "releasing fire left an active damaging beam");

        target.set(1000, 700); second.set(1000, 750); index();
        for(Unit invalid : new Unit[]{air, ground, friend, ally, target}){
            Bullet forced = force(invalid); before = invalid.health;
            for(int i = 0; i < 30; i++){ Time.update(); forced.type.update(forced); }
            check(invalid.health == before && !(boolean)field(forced.data, "active"), "forced attack bypassed orbit/team/range filter: " + invalid.type);
            forced.remove();
        }
        Vars.world.tile(45, 37).setBlock(Blocks.thoriumWall, Team.crux);
        Building wall = Vars.world.build(45, 37); before = wall.health;
        owner.controlWeapons(true, true); owner.mounts[0].target = wall;
        Bullet forced = burn.create(owner, owner.team, 300, 300, 0f, -1, 1, 1, null, null, wall.x, wall.y);
        owner.mounts[0].bullet = forced;
        for(int i = 0; i < 30; i++){ Time.update(); forced.type.update(forced); }
        check(wall.health == before && !(boolean)field(forced.data, "active"), "Transit damaged a ground building"); forced.remove();
        owner.mounts[0].bullet = null;
        for(int i = 0; i < 90; i++) tick(true);
        check(owner.mounts[0].target == null && !owner.isShooting, "Transit AI targeted conventional air/ground after orbital enemies left");
        check(!target.checkTarget(true, true) && !target.targetable(owner.team), "orbital targeting scope leaked");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); owner.write(Writes.get(new DataOutputStream(bytes)));
        Unit restored = (Unit)EntityMapping.map(owner.classId()).get();
        restored.read(Reads.get(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        check(restored.type == transit && !restored.checkTarget(true, true), "Transit lost orbital layer on serialization");
        System.out.println("TRANSIT_PASS: T2 native reconstruction, research, default engines, command/enemy controllers, native sustained beam, moving target, damage ramp/reset, orbit-only damage and acquisition, ally/manual/range filters, release stop, serialization");
    }
}
