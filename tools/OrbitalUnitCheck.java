import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.*;
import arc.util.io.*;
import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.nio.file.Files;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.entities.Damage;
import mindustry.entities.bullet.*;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.net.Net;
import mindustry.type.*;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.blocks.units.UnitFactory;

/** Packaged orbital production, research, serialization and real native combat queries. */
public class OrbitalUnitCheck{
    static void check(boolean value, String message){ if(!value) throw new AssertionError(message); }
    public static void main(String[] args){
        try{ run(); System.exit(0); }catch(Throwable error){ error.printStackTrace(); System.exit(1); }
    }
    static void run() throws Exception{
        Core.app = new MockApplication(); Core.files = new MockFiles();
        Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Core.settings = new Settings();
        Core.settings.setDataDirectory(new Fi(Files.createTempDirectory("cryon-orbit-").toFile()));
        Core.bundle = I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(Core.settings.getDataDirectory().child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(Core.settings.getDataDirectory().child("mods/unitlaunch.jar"));
        Vars.headless = true; Vars.loadLocales = false; Vars.net = new Net(null);
        Vars.platform = new mindustry.core.Platform(){};
        mindustry.core.Version.init(); Vars.init();
        Vars.content.createBaseContent(); Vars.content.createModContent(); Vars.content.init();
        var mod = Vars.mods.locateMod("cryon");
        check(mod != null && !mod.hasContentErrors(), "Packaged content errors");
        ClassLoader loader = mod.main.getClass().getClassLoader();
        loader.loadClass("cryon.TechTree.CryonTechTree").getMethod("load").invoke(null);
        Timer.instance().clear();
        UnitType startrail = Vars.content.unit("cryon-startrail");
        check(startrail != null && startrail.flying && !startrail.targetAir && startrail.targetGround,
                "Missing orbital ground bomber");
        check(startrail.flyingLayer > UnitTypes.flare.flyingLayer && startrail.shadowElevationScl > UnitTypes.flare.shadowElevationScl,
                "Orbital craft does not draw above ordinary aircraft");
        check(startrail.techNode != null && startrail.techNode.parent.content.name.equals("cryon-unit-projector"),
                "Missing projector research branch");
        Vars.logic = new Logic(); Vars.logic.reset();
        Vars.world.loadGenerator(140, 100, tiles -> {
            for(int x = 0; x < 140; x++) for(int y = 0; y < 100; y++)
                tiles.set(x, y, new Tile(x, y, Blocks.stone, Blocks.air, Blocks.air));
        });
        Vars.state.set(mindustry.core.GameState.State.playing);
        Vars.state.rules.canGameOver = false; Vars.state.rules.unitCap = 100;
        Time.delta = 1;
        UnitFactory factory = (UnitFactory)Vars.content.block("cryon-unit-projector");
        int plan = factory.plans.indexOf(p -> p.unit == startrail);
        check(plan >= 0, "Missing factory plan");
        Vars.world.tile(10, 10).setBlock(factory, Team.sharded);
        UnitFactory.UnitFactoryBuild producer = (UnitFactory.UnitFactoryBuild)Vars.world.build(10, 10);
        producer.currentPlan = plan;
        for(ItemStack cost : factory.plans.get(plan).requirements) producer.items.add(cost.item, cost.amount);
        producer.progress = factory.plans.get(plan).time; producer.updateTile();
        check(producer.payload != null && producer.payload.unit.type == startrail && producer.items.total() == 0,
                "Factory cannot build orbital unit");
        producer.tile.remove();
        Unit orbit = startrail.spawn(Team.crux, 400, 300);
        check(!orbit.checkTarget(true, false) && !orbit.checkTarget(false, true) && !orbit.checkTarget(true, true),
                "Ordinary air/ground target filters expose orbital layer");
        check(!orbit.targetable(Team.sharded), "Ordinary AA can acquire orbital unit");
        BulletType normal = new BasicBulletType(4f, 100f);
        Bullet shot = normal.create(null, Team.sharded, 400, 300, 0f);
        check(!shot.collides(orbit), "Normal projectile collides with orbital unit");
        shot.remove();
        index();
        Vars.world.tile(30, 37).setBlock(Blocks.scatter, Team.sharded);
        ItemTurret.ItemTurretBuild aa = (ItemTurret.ItemTurretBuild)Vars.world.build(30, 37);
        aa.handleStack(Items.lead, 10, null);
        Method find = Turret.TurretBuild.class.getDeclaredMethod("findTarget"); find.setAccessible(true);
        find.invoke(aa);
        check(aa.target == null, "Native scatter locked orbital target");
        aa.tile.remove();
        ItemTurret rail = (ItemTurret)Vars.content.block("cryon-railgun");
        check(rail.getClass().getName().endsWith("OrbitalItemTurret"), "Railgun lost orbital targeting class");
        Vars.world.tile(20, 37).setBlock(rail, Team.sharded);
        ItemTurret.ItemTurretBuild gun = (ItemTurret.ItemTurretBuild)Vars.world.build(20, 37);
        gun.handleStack(Items.surgeAlloy, 30, null);
        Method orbitalFind = gun.getClass().getDeclaredMethod("findTarget"); orbitalFind.setAccessible(true);
        orbitalFind.invoke(gun);
        check(gun.target == orbit, "Railgun cannot acquire orbital target");
        Method valid = gun.getClass().getDeclaredMethod("validateTarget"); valid.setAccessible(true);
        check((Boolean)valid.invoke(gun), "Railgun immediately loses orbital target");
        Unit air = UnitTypes.flare.spawn(Team.crux, 450, 300);
        Unit ground = UnitTypes.dagger.spawn(Team.crux, 500, 300);
        Unit ally = startrail.spawn(Team.sharded, 350, 300);
        index();
        for(BulletType ammo : rail.ammoTypes.values()){
            // Native rail energy is spent on the first tough target; test each layer separately.
            for(Unit target : new Unit[]{orbit, air, ground}){
                orbit.set(400, 600); air.set(450, 650); ground.set(500, 700);
                target.set(400, 300); target.health = target.maxHealth = 100000f;
                ally.health = ally.maxHealth = 100000f;
                index();
                ammo.create(null, Team.sharded, 300, 300, 0f);
                check(target.health < 100000f, "Rail ammo failed layer " + target.type.name + ": " + ammo);
                check(ally.health == 100000f, "Railgun damages allied orbital units");
            }
            check(!orbit.checkTarget(true, true) && !orbit.targetable(Team.sharded), "Orbital scope leaked after firing");
        }
        orbit.set(400, 300); index();
        orbit.health = 100000f;
        RailBulletType ordinaryRail = new RailBulletType(); ordinaryRail.length = 600f; ordinaryRail.damage = 50f;
        ordinaryRail.init(); ordinaryRail.create(null, Team.sharded, 300, 300, 0f);
        check(orbit.health == 100000f, "Ordinary rail bullet damages orbital unit");
        Damage.damage(Team.sharded, 400, 300, 100f, 100f);
        check(orbit.health == 100000f, "Ordinary splash damages orbital unit");
        // Bombs hit ground units, leaving both air layers untouched.
        ground.health = ground.maxHealth = air.health = air.maxHealth = orbit.health = orbit.maxHealth = 100000f;
        ground.set(600, 300); air.set(600, 300); orbit.set(600, 300); index();
        BulletType bomb = startrail.weapons.first().bullet;
        Bullet aimed = bomb.create(ally, Team.sharded, 570, 300, 90f, -1f, 1f, 1f, null, null, 600, 300);
        check(aimed.vel.isZero() && !bomb.keepVelocity && !bomb.collides,
                "Quad-style bomb moves horizontally toward the crosshair");
        aimed.remove();
        Bullet distant = bomb.create(ally, Team.sharded, 570, 300, 0f, -1f, 1f, 1f, null, null, 1000, 300);
        check(distant.vel.len() * bomb.lifetime <= bomb.maxRange + 0.1f, "Manual aim bypasses bomb range");
        distant.remove();
        Bullet drop = bomb.create(ally, Team.sharded, 600, 300, 0f);
        bomb.despawned(drop);
        check(ground.health < 100000f && air.health == 100000f && orbit.health == 100000f,
                "Bomb does not isolate ground targets");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ally.write(Writes.get(new DataOutputStream(bytes)));
        Unit restored = (Unit)EntityMapping.map(ally.classId()).get();
        restored.read(Reads.get(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        check(restored.type == startrail && restored.getClass() == ally.getClass()
                && !restored.checkTarget(true, true), "Orbital layer lost after serialization");
        check(startrail.autoDropBombs && !startrail.physics && startrail.shadowElevationScl >= 10f
                && startrail.flyingLayer > mindustry.graphics.Layer.shields + 10f,
                "Very-high orbit / Quad drop settings missing");
        barriers(loader, startrail, bomb);
        System.out.println("ORBITAL_CHECK_PASS: factory, research, flight layer, native AA exclusion, bullet/splash exclusion, all rail ammo, ground-only bombs, serialization");
    }
    static Field field(Class<?> type, String name) throws Exception{
        Field value = type.getDeclaredField(name); value.setAccessible(true); return value;
    }
    static void barriers(ClassLoader loader, UnitType type, BulletType bomb) throws Exception{
        check(!bomb.absorbable && !bomb.reflectable && !bomb.hittable && bomb.speed == 0f,
                "Bomb can be intercepted or reflected");
        Unit orbital = type.spawn(Team.sharded, 800, 400);
        Unit host = UnitTypes.flare.spawn(Team.crux, 800, 400);
        for(String name : new String[]{"FluxBarrierAbility", "SafeFluxBarrierAbility"}){
            Class<?> cls = loader.loadClass("cryon.Type." + name);
            mindustry.entities.abilities.Ability ability = (mindustry.entities.abilities.Ability)cls.getConstructor().newInstance();
            field(cls, "radscl").setFloat(ability, 1f); field(cls, "warmup").setFloat(ability, 1f);
            Unit control = UnitTypes.flare.spawn(Team.sharded, 800, 400);
            index();
            Bullet dropped = bomb.create(orbital, Team.sharded, 800, 400, 0f);
            Groups.bullet.updatePhysics();
            ability.update(host);
            check(!orbital.dead && orbital.within(800, 400, 0.01f), "Unit field blocks orbit: " + name);
            check(control.dead, "Unit force-field test was not active: " + name);
            check(!dropped.absorbed && dropped.isAdded(), "Unit field absorbed downward bomb: " + name);
            dropped.remove(); control.remove();
        }
        Class<?> flux = loader.loadClass("cryon.Type.FluxBarrier");
        mindustry.world.Block block = Vars.content.block("cryon-flux-barrier");
        Vars.world.tile(100, 50).setBlock(block, Team.crux);
        Building build = Vars.world.build(100, 50);
        build.getClass().getField("radscl").setFloat(build, 1f);
        field(flux, "paramBlock").set(null, block); field(flux, "paramEntity").set(null, build);
        @SuppressWarnings("unchecked") arc.func.Cons<Unit> consumer = (arc.func.Cons<Unit>)field(flux, "unitConsumer").get(null);
        orbital.set(build.x, build.y); consumer.get(orbital);
        check(!orbital.dead && orbital.within(build, 0.01f), "Building force field blocks orbit");
        Unit control = UnitTypes.flare.spawn(Team.sharded, build.x, build.y); consumer.get(control);
        check(control.dead, "Building force-field test was not active");
        control.remove(); build.tile.remove();
        // Linked barrier curtain: a swept crossing must leave the orbital craft unchanged.
        mindustry.world.Block node = Vars.content.block("cryon-orbital-barrier-node");
        Vars.world.tile(90, 25).setBlock(node, Team.crux);
        Vars.world.tile(90, 65).setBlock(node, Team.crux);
        Building a = Vars.world.build(90, 25), b = Vars.world.build(90, 65);
        a.configureAny(b.pos()); a.power.status = b.power.status = 1f;
        check((Boolean)a.getClass().getMethod("active").invoke(a), "Barrier node not active");
        orbital.lastX = 700; orbital.lastY = 360; orbital.set(740, 360); orbital.vel.set(40, 0);
        control = UnitTypes.flare.spawn(Team.sharded, 740, 380);
        control.lastX = 700; control.lastY = 380; control.vel.set(40, 0);
        Class<?> nodes = loader.loadClass("cryon.Type.OrbitalBarrierNode");
        nodes.getMethod("blockUnits").invoke(null);
        check(orbital.x == 740 && orbital.vel.x == 40, "Linked barrier stops orbital craft");
        check(control.x < 740, "Linked barrier test did not block normal aircraft");
        Bullet falling = bomb.create(orbital, Team.sharded, a.x, 360, 0f);
        check(!(Boolean)nodes.getMethod("intercept", Bullet.class).invoke(null, falling), "Linked barrier intercepts bomb");
        falling.remove(); control.remove(); a.tile.remove(); b.tile.remove();
        // The reflection implementation previously ignored the native reflectable flag.
        var reflector = (mindustry.entities.abilities.Ability)loader.loadClass("cryon.Type.ReflectShieldAbility").getConstructor().newInstance();
        orbital.set(host.x, host.y);
        Bullet falling2 = bomb.create(orbital, Team.sharded, host.x, host.y, 0f);
        Groups.bullet.updatePhysics(); reflector.update(host);
        check(falling2.team == Team.sharded && falling2.owner == orbital && !falling2.absorbed, "Reflect field redirects orbital bomb");
        falling2.remove();
        var nativeShield = new mindustry.entities.abilities.ForceFieldAbility(80f, 1f, 1000f, 60f);
        host.shield = 1000f;
        Bullet falling3 = bomb.create(orbital, Team.sharded, host.x, host.y, 0f);
        Groups.bullet.updatePhysics(); nativeShield.update(host);
        check(!falling3.absorbed && falling3.isAdded(), "Native force field absorbs orbital bomb");
        falling3.remove();
        Class<?> bossType = loader.loadClass("cryon.Type.OblivionBossAbility");
        Object bossField = bossType.getConstructor().newInstance();
        for(String mode : new String[]{"absorbBullets", "reflectBullets"}){
            Bullet falling4 = bomb.create(orbital, Team.sharded, host.x, host.y, 0f);
            Groups.bullet.updatePhysics();
            Method intercept = bossType.getDeclaredMethod(mode, Unit.class, float[].class);
            intercept.setAccessible(true); intercept.invoke(bossField, host, new float[6]);
            check(!falling4.absorbed && falling4.team == Team.sharded && falling4.owner == orbital,
                    "Boss field intercepts orbital bomb: " + mode);
            falling4.remove();
        }
        System.out.println("ORBITAL_BARRIER_PASS: building/unit/safe/linked fields, reflection and native shield, Quad-style stationary drop");
    }
    static void index(){
        Vars.state.teams.updateTeamStats();
        for(Team team : new Team[]{Team.sharded, Team.crux}){
            team.data().tree().clear();
            for(Unit unit : Groups.unit) if(unit.team == team) team.data().tree().insert(unit);
        }
    }
}
