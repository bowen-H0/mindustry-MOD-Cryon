import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.*;
import java.nio.file.Files;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.entities.abilities.Ability;
import mindustry.entities.units.BuildPlan;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.io.SaveIO;
import mindustry.net.Net;
import mindustry.type.*;
import mindustry.type.weapons.RepairBeamWeapon;
import mindustry.world.*;
import mindustry.world.blocks.ConstructBlock.ConstructBuild;

/** Real packaged content, real blueprint construction and weapons; no player data is accessed. */
public final class DesolateBattlefieldCheck{
    static Class<?> directorType, fieldType, bomberType;
    static Object director, field;
    static Building processor;
    static Fi data;
    static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }
    static Unit spawn(String name, Team team, float x, float y){
        Unit unit = Vars.content.unit(name).create(team); unit.set(x, y); unit.add(); return unit;
    }
    static void clearUnits(){ for(Unit unit : Groups.unit.copy()) unit.remove(); }
    static boolean role(Unit unit, double flag, int cap){ return unit.flag >= flag && unit.flag < flag + cap && !unit.dead; }
    static Unit builder(){ return Groups.unit.find(u -> role(u, 841000d, 3)); }
    static void index(){
        Vars.state.teams.updateTeamStats();
        for(Team team : new Team[]{Team.sharded, Team.crux, Team.green}){
            team.data().tree().clear();
            for(Unit unit : Groups.unit) if(unit.team == team) team.data().tree().insert(unit);
        }
    }
    static void locate() throws Exception{
        processor = Groups.build.find(b -> {
            try{ return directorType.isInstance(b.getClass().getField("script").get(b)); }
            catch(Exception ignored){ return false; }
        });
        check(processor != null, "manual processor lost");
        director = processor.getClass().getField("script").get(processor);
        field = directorType.getField("battlefield").get(director);
    }
    static void tickDirector() throws Exception{ Time.delta = 1f; directorType.getMethod("update").invoke(director); }
    static void deploy() throws Exception{ fieldType.getMethod("deploySupport").invoke(field); }
    public static void main(String[] args){
        try{ run(); System.exit(0); }
        catch(Throwable error){ error.printStackTrace(); System.exit(1); }
    }
    static void run() throws Exception{
        Core.app = new MockApplication(); Core.files = new MockFiles(); Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Core.settings = new Settings(); data = new Fi(Files.createTempDirectory("cryon-battlefield-check-").toFile());
        Core.settings.setDataDirectory(data); Core.bundle = I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlanuch.jar"));
        Vars.headless = true; Vars.loadLocales = false; Vars.net = new Net(null); Vars.platform = new mindustry.core.Platform(){};
        mindustry.core.Version.init(); Vars.init(); Core.settings.setJson(mindustry.io.JsonIO.json);
        Vars.content.createBaseContent(); Vars.content.createModContent(); Vars.content.init();
        var mod = Vars.mods.locateMod("cryon"); check(mod != null && !mod.hasContentErrors(), "packaged content errors");
        var loader = mod.main.getClass().getClassLoader();
        directorType = loader.loadClass("cryon.Script.DesolatePlainScript");
        fieldType = loader.loadClass("cryon.Story.DesolateBattlefield");
        bomberType = loader.loadClass("cryon.Type.NuclearKamikazeAI");
        directorType.getMethod("install").invoke(null);
        Vars.logic = new Logic(); Vars.logic.reset(); Vars.state.rules.winWave = 100;
        Vars.world.loadGenerator(220, 220, tiles -> {
            for(int x = 0; x < 220; x++) for(int y = 0; y < 220; y++) tiles.set(x, y, new Tile(x, y, Blocks.stone, Blocks.air, Blocks.air));
            tiles.get(20, 20).setBlock(Blocks.coreShard, Team.sharded);
            tiles.get(26, 20).setBlock(Blocks.titaniumWallLarge, Team.sharded);
            tiles.get(32, 20).setBlock(Blocks.titaniumWallLarge, Team.crux);
            tiles.get(210, 210).setBlock(Vars.content.block("cryon-java-processor"), Team.crux);
        });
        Vars.state.set(mindustry.core.GameState.State.playing);
        Vars.world.build(210, 210).getClass().getMethod("setScript", String.class).invoke(Vars.world.build(210, 210), "desolate_plain");
        locate();
        check(Team.crux.core() == null && Team.crux.rules().infiniteResources && !Vars.state.rules.infiniteResources
            && !Team.sharded.rules().infiniteResources, "enemy coreless construction leaked free resources to player");
        Item cargo = Vars.content.item("cryon-nuclear-explosive");
        check(cargo != null && cargo.explosiveness == 2f && cargo.flammability == 0f
            && cargo.charge == 0f && cargo.radioactivity == 0f, "nuclear cargo must have only 200% explosiveness");

        for(int i = 0; i < 150; i++) spawn("flare", Team.crux, 1200 + i % 10, 1200 + i / 10);
        Unit protectedBoss = spawn("flare", Team.crux, 1400, 1400); protectedBoss.flag = 840100d;
        tickDirector();
        int armed = Groups.unit.count(u -> u.flag == 844002d);
        int checked = Groups.unit.count(u -> u.flag == 844001d);
        check(armed > 0 && armed <= 16 && checked > 0, "nuclear probability/cap failed");
        check(protectedBoss.stack.item != cargo && protectedBoss.flag == 840100d, "fleet boss converted into a bomber");
        for(Unit unit : Groups.unit) if(unit.flag == 844002d){
            check(unit.stack.item == cargo && unit.stack.amount > 0 && bomberType.isInstance(unit.controller()), "carrier lacks cargo or kamikaze controller");
        }
        for(int i = 0; i < 120; i++) tickDirector();
        check(Groups.unit.count(u -> u.flag == 844002d) == armed, "checked aircraft rerolled nuclear chance");
        Fi nuclearSave = data.child("nuclear-air.msav");
        SaveIO.write(nuclearSave); SaveIO.load(nuclearSave); locate(); tickDirector();
        check(Groups.unit.count(u -> u.flag == 844002d) == armed && Groups.unit.count(u -> u.flag == 844001d) == checked,
            "save/load rerolled aircraft conversion");
        for(Unit unit : Groups.unit) if(unit.flag == 844002d)
            check(unit.stack.item == cargo && bomberType.isInstance(unit.controller()), "saved bomber did not resume its cargo and AI");
        clearUnits();

        Unit bomber = spawn("flare", Team.crux, 26 * 8f, 20 * 8f);
        Unit nearbyFighter = spawn("flare", Team.sharded, bomber.x + 2, bomber.y + 2);
        fieldType.getMethod("armBomber", Unit.class).invoke(field, bomber);
        Object ai = bomber.controller();
        Building target = (Building)bomberType.getMethod("chooseBuilding").invoke(ai);
        check(target != null && target.team == Team.sharded, "kamikaze failed to prioritize a building over the fighter");
        Building friendly = Vars.world.build(32, 20); float friendlyHealth = friendly.health;
        Building victim = target;
        bomber.controller().updateUnit();
        check(!bomber.dead && bomber.stack.amount > 0, "kamikaze detonated before reaching a building");
        bomber.set(target.x, target.y);
        bomber.controller().updateUnit();
        check(bomber.dead && bomber.stack.item == cargo && bomber.stack.amount > 0,
            "impact did not preserve cargo for the native crash explosion: dead="
            + bomber.dead + " cargo=" + bomber.stack.amount + " health=" + bomber.health + " target=" + target.block.name + " distance=" + bomber.dst(target));
        check(victim.isValid() && victim.health == victim.maxHealth, "impact applied custom explosion damage before native destruction");
        // Exercise the engine's crash destruction, including its scheduled explosion waves.
        bomber.destroy();
        for(int i = 0; i < 90; i++){ Time.delta = 1f; Time.update(); }
        check(!bomber.isAdded(), "native crash did not remove aircraft");
        check(!victim.isValid() || victim.health < victim.maxHealth, "native cargo explosion failed to damage enemy buildings");
        check(friendly.health == friendlyHealth, "nuclear impact damaged its own side");
        float healthAfterCrash = victim.health;
        bomber.destroy();
        for(int i = 0; i < 90; i++){ Time.delta = 1f; Time.update(); }
        check(victim.health == healthAfterCrash, "native cargo exploded twice");
        clearUnits();

        for(int i = 0; i < 8; i++) spawn("dagger", Team.crux, 1050 + i * 3, 1050);
        spawn("dagger", Team.crux, 400, 1450);
        // The director caches one density calculation per second, so refresh its clock first.
        for(int i = 0; i < 61; i++) tickDirector();
        arc.math.geom.Vec2 center = (arc.math.geom.Vec2)fieldType.getMethod("denseCenter").invoke(field);
        check(center.dst(1060, 1050) < 100, "supply station ignored main friendly cluster");
        deploy();
        check(Groups.unit.count(u -> role(u, 841000d, 3)) == 1 && Groups.unit.count(u -> role(u, 842000d, 6)) == 2
            && Groups.unit.count(u -> role(u, 843000d, 6)) == 2, "wrong support deployment");
        Unit builder = builder();
        int before = Groups.build.size();
        boolean assigned = (boolean)fieldType.getMethod("assignStation", Unit.class).invoke(field, builder);
        check(assigned && !builder.plans.isEmpty() && Groups.build.size() == before, "blueprint assignment directly placed buildings");
        check(builder.plans.size > 40, "large repair tower blueprint was not loaded");
        BuildPlan first = builder.buildPlan();
        builder.set(first.drawx(), first.drawy() + 35f);
        Time.delta = 1f; builder.controller().updateUnit(); builder.updateBuildLogic();
        ConstructBuild construction = Groups.build.copy().select(b -> b instanceof ConstructBuild).map(b -> (ConstructBuild)b).first();
        check(construction != null && construction.progress > 0f && construction.progress < 1f, "no real incremental construction site");
        float progress = construction.progress;
        Unit star = Groups.unit.find(u -> role(u, 842000d, 6));
        star.set(builder.x + 8f, builder.y); star.controller().updateUnit(); star.updateBuildLogic();
        check(!star.plans.isEmpty() && construction.progress > progress, "Starlight did not assist native construction");
        String job = Vars.state.rules.tags.get("cryon-desolate-job-841000", "");
        check(!job.isEmpty(), "construction job not persisted");
        Fi save = data.child("construction.msav");
        SaveIO.write(save); SaveIO.load(save); locate(); tickDirector();
        builder = builder(); builder.controller().updateUnit();
        check(Vars.state.rules.tags.get("cryon-desolate-job-841000").equals(job) && !builder.plans.isEmpty(), "construction did not resume after load");
        check(Groups.unit.count(u -> role(u, 841000d, 3)) == 1 && Groups.unit.count(u -> role(u, 842000d, 6)) == 2,
            "support units duplicated on load");
        Team.crux.rules().buildSpeedMultiplier = 50f;
        int ticks = 0;
        while(!builder.plans.isEmpty() && ticks++ < 20000){
            BuildPlan plan = builder.buildPlan(); builder.set(plan.drawx(), plan.drawy() + 30f);
            Time.delta = 1f; builder.updateBuilding = true; builder.updateBuildLogic();
        }
        check(builder.plans.isEmpty(), "native builder stalled before completing blueprint");
        check(Groups.build.contains(b -> b.team == Team.crux && b.block.name.equals("cryon-cryo-repair-tower")), "repair station was never completed");

        Unit healer = Groups.unit.find(u -> role(u, 843000d, 6));
        Unit wounded = spawn("toxopid", Team.crux, 1000, 1000); wounded.health = wounded.maxHealth / 2;
        Unit enemy = spawn("toxopid", Team.sharded, 1020, 1020); enemy.health = enemy.maxHealth / 2;
        healer.set(1000, 1040); index();
        healer.controller().updateUnit();
        check(healer.controller().getClass().getField("repairTarget").get(healer.controller()) == wounded, "Exotic selected the wrong repair target");
        float woundedBefore = wounded.health, enemyBefore = enemy.health;
        for(int i = 0; i < 120; i++){
            Time.delta = 1f; Time.update(); healer.controller().updateUnit();
            for(var mount : healer.mounts) if(mount.weapon instanceof RepairBeamWeapon) mount.weapon.update(healer, mount);
        }
        check(wounded.health > woundedBefore && enemy.health == enemyBefore, "native repair beams did not heal friendly units exclusively");
        for(int i = 0; i < 8; i++) deploy();
        check(Groups.unit.count(u -> role(u, 841000d, 3)) == 3 && Groups.unit.count(u -> role(u, 842000d, 6)) == 6
            && Groups.unit.count(u -> role(u, 843000d, 6)) == 6, "support cap failed");
        Vars.state.wave = 1;
        Vars.state.rules.tags.put("cryon-desolate-support-wave", "1");
        Vars.state.rules.tags.put("cryon-desolate-support-time", "0");
        for(int i = 0; i < 1850; i++) tickDirector();
        check(Vars.state.rules.tags.getFloat("cryon-desolate-support-time", 0f) >= 1800f,
            "repeat support timer incorrectly reused the first 30-second delay");
        // Support must abandon construction/repair when no living combat troops remain.
        for(Unit unit : Groups.unit.copy()) if(unit.team == Team.crux
            && !role(unit, 841000d, 3) && !role(unit, 842000d, 6) && !role(unit, 843000d, 6)) unit.remove();
        Unit wreck = spawn("flare", Team.crux, 1200, 1200); wreck.kill();
        for(int i = 0; i < 61; i++) tickDirector();
        check(wreck.dead, "dead combat fixture revived");
        for(Unit unit : Groups.unit) if(unit.team == Team.crux && !unit.dead){
            check(unit.controller().getClass().getSimpleName().equals("SupportChargeAI"), "support stayed idle instead of charging: " + unit.type);
            check(unit.plans.isEmpty() && !unit.updateBuilding, "charging unit retained an active construction job");
            unit.set(1200, 1200); unit.vel.setZero();
            unit.controller().updateUnit();
            check(unit.vel.len() > 0f && unit.vel.x < 0f && unit.vel.y < 0f, "support did not advance toward enemy core");
        }
        Fi chargeSave = data.child("support-charge.msav");
        SaveIO.write(chargeSave); SaveIO.load(chargeSave); locate(); tickDirector();
        check(Groups.unit.count(u -> u.team == Team.crux && !u.dead
            && u.controller().getClass().getSimpleName().equals("SupportChargeAI")) == 15, "support charge did not resume after save/load");
        spawn("dagger", Team.crux, 900, 900);
        for(int i = 0; i < 61; i++) tickDirector();
        check(builder().controller().getClass().getSimpleName().equals("StationBuilderAI") && builder().updateBuilding,
            "new combat wave did not restore construction support");
        check(Groups.unit.find(u -> role(u, 842000d, 6)).controller().getClass().getSimpleName().equals("StationAssistAI")
            && Groups.unit.find(u -> role(u, 843000d, 6)).controller().getClass().getSimpleName().equals("RepairEscortAI"),
            "new combat wave did not restore assistance and repair roles");
        System.out.println("DESOLATE_BATTLEFIELD_CHECK_PASS: 200% explosive-only cargo, native crash explosion, probability/impact, building targeting, one detonation, friendly safety, coreless native blueprint construction, Starlight assistance, save recovery, Exotic repair beams, reinforcement caps, support-only charge, dead combat exclusion, saved charge recovery, new-wave role restoration");
    }
}
