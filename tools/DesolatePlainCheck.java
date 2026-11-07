import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.*;
import java.lang.reflect.*;
import java.nio.file.Files;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.ctype.ContentType;
import mindustry.entities.abilities.Ability;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.io.SaveIO;
import mindustry.net.Net;
import mindustry.type.*;

/** Actual packaged content and original map, isolated from all player saves. */
public final class DesolatePlainCheck{
    static Class<?> directorType;
    static Object director;
    static Building processor;
    static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }
    static int number(String field) throws Exception{ return directorType.getField(field).getInt(director); }
    static boolean bool(String field) throws Exception{ return directorType.getField(field).getBoolean(director); }
    static void tick() throws Exception{ Time.delta = 1f; processor.getClass().getMethod("updateTile").invoke(processor); }
    static void advance(int ticks) throws Exception{ for(int i = 0; i < ticks; i++) tick(); }
    static int fleetCount(){ return Groups.unit.count(u -> u.flag >= 840100d && u.flag < 840108d); }
    static void locate() throws Exception{
        processor = Groups.build.find(b -> {
            try{ return directorType.isInstance(b.getClass().getField("script").get(b)); }
            catch(Exception ignored){ return false; }
        });
        check(processor != null, "sector processor not installed");
        director = processor.getClass().getField("script").get(processor);
    }
    static Unit spawn(UnitType type, Team team, float x, float y){
        Unit unit = type.create(team); unit.set(x, y); unit.add(); return unit;
    }
    static void index(){
        Vars.state.teams.updateTeamStats();
        for(Team team : new Team[]{Team.sharded, Team.crux, Team.green}){
            team.data().tree().clear();
            for(Unit unit : Groups.unit) if(unit.team == team) team.data().tree().insert(unit);
        }
    }
    public static void main(String[] args) throws Exception{
        try{ run(); System.exit(0); }
        catch(Throwable error){ error.printStackTrace(); System.exit(1); }
    }
    static void run() throws Exception{
        Core.app = new MockApplication(); Core.files = new MockFiles();
        Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Core.settings = new Settings();
        Fi data = new Fi(Files.createTempDirectory("cryon-desolate-check-").toFile());
        Core.settings.setDataDirectory(data);
        Core.bundle = I18NBundle.createBundle(new Fi("bundles/bundle"));
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlanuch.jar"));
        Vars.headless = true; Vars.loadLocales = false; Vars.net = new Net(null);
        Vars.platform = new mindustry.core.Platform(){};
        mindustry.core.Version.init(); Vars.init();
        Core.settings.setJson(mindustry.io.JsonIO.json);
        Vars.content.createBaseContent(); Vars.content.createModContent(); Vars.content.init();
        var mod = Vars.mods.locateMod("cryon");
        check(mod != null && !mod.hasContentErrors(), "packaged content errors");
        ClassLoader loader = mod.main.getClass().getClassLoader();
        directorType = loader.loadClass("cryon.Script.DesolatePlainScript");
        directorType.getMethod("install").invoke(null);
        Vars.logic = new Logic(); Vars.logic.reset();
        SaveIO.load(new Fi("maps/cryon-desolate-plain.msav"));
        SaveIO.write(data.child("baseline.msav"));
        SectorPreset preset = Vars.content.getByName(ContentType.sector, "cryon-desolate-plain");
        check(preset != null, "missing sector preset");
        Vars.state.rules.sector = preset.sector;
        Vars.state.rules.winWave = 100;
        Vars.state.set(mindustry.core.GameState.State.playing);
        int buildingsBefore = Groups.build.size();
        Events.fire(new PlayEvent());
        Events.fire(new WorldLoadEvent());
        Events.fire(new SaveLoadEvent(false));
        check(Groups.build.size() == buildingsBefore && Vars.state.rules.winWave == 100
            && !Vars.state.rules.tags.containsKey("cryon-desolate-director"), "unconfigured map was automatically modified");
        mindustry.world.Tile place = null;
        for(var tile : Vars.world.tiles){
            if(tile.block() == Blocks.air && tile.overlay() != Blocks.spawn && !tile.floor().isDeep()){ place = tile; break; }
        }
        check(place != null, "no empty test tile");
        place.setBlock(Vars.content.block("cryon-java-processor"), Vars.state.rules.waveTeam);
        place.build.getClass().getMethod("setScript", String.class).invoke(place.build, "desolate_plain");
        locate();
        Building first = processor;
        Events.fire(new PlayEvent()); locate();
        check(first == processor && Groups.build.size() == buildingsBefore + 1, "manual processor duplicated");
        check(Vars.state.rules.winWave == 0, "fleet victory gate absent");
        check(!Vars.state.rules.spawns.contains(g -> g.type.name.matches("cryon-(roche|threshold|eon)")), "boss still in waves");
        check(!Vars.state.rules.spawns.contains(g -> g.end > 98), "ordinary waves continue past final battle");
        for(Unit unit : Groups.unit.copy()) unit.remove();
        Vars.state.wave = 20; tick();
        check(!bool("cleansingStarted"), "nuclear cleansing started before wave 20 ended");
        Unit blocker = spawn(UnitTypes.flare, Team.crux, 200, 200);
        Vars.state.wave = 21; tick();
        check(!bool("cleansingStarted"), "wave 20 enemies still alive");
        Vars.state.wave = 22; tick();
        check(bool("cleansingStarted") && bool("running"), "overlapping waves prevented nuclear cleansing");
        Vars.state.wave = 100; tick();
        check(number("phase") == 0, "cinematic started before regular enemies cleared");
        for(Unit unit : Groups.unit.copy()) if(unit.team == Team.crux) unit.remove();
        tick();
        check(number("phase") == 1 && !Vars.state.rules.waveTimer, "cinematic did not start");
        advance(14);
        check(fleetCount() == 8 && Groups.unit.count(u -> u.type.name.equals("cryon-eon")) == 2, "fleet did not enter together");
        check(Groups.unit.count(u -> u.type.name.equals("cryon-threshold")) == 2, "wrong flanks");
        check(fleetCount() == 8 && Groups.unit.count(u -> u.type.name.equals("cryon-roche")) == 4, "wrong rear fleet");
        float offset = (float)directorType.getMethod("entryOffset").invoke(director);
        check(offset > 0f, "fleet has no approach movement");
        advance(195);
        check((float)directorType.getMethod("entryOffset").invoke(director) < offset, "fleet did not advance toward formation");
        for(int i = 0; i < 8; i++){
            Unit unit = (Unit)directorType.getMethod("fleetUnit", int.class).invoke(director, i);
            arc.math.geom.Vec2 position = (arc.math.geom.Vec2)directorType.getMethod("entryPosition", int.class).invoke(director, i);
            check(unit.dst(position) < 0.1f, "formation displaced");
            check(unit.x > 0 && unit.y > 0 && unit.x < Vars.world.unitWidth() && unit.y < Vars.world.unitHeight(), "fleet outside map");
        }
        Fi save = data.child("intro.msav");
        SaveIO.write(save); SaveIO.load(save); locate();
        check(number("phase") == 1 && fleetCount() == 8 && bool("cleansingStarted"), "cinematic save state lost");
        advance(150);
        check(number("phase") == 2 && fleetCount() == 8, "cinematic duplicated or failed to release fleet");
        for(int i = 0; i < 8; i++){
            Unit unit = (Unit)directorType.getMethod("fleetUnit", int.class).invoke(director, i);
            arc.math.geom.Vec2 target = (arc.math.geom.Vec2)directorType.getMethod("formation", int.class).invoke(director, i);
            check(unit.dst(target) < 0.1f, "fleet did not settle at its final position");
        }
        for(Unit unit : Groups.unit.copy()) if(unit.team == Team.crux) unit.remove();
        // Custom-map win gate follows the same final clear without touching campaign progression.
        Vars.state.rules.sector = null;
        tick();
        check(number("phase") == 3 && Vars.state.rules.winWave == 100 && !bool("running"), "victory/nuclear shutdown failed");
        SaveIO.write(save); SaveIO.load(save); locate();
        check(number("phase") == 3 && Vars.state.rules.winWave == 100, "completed save became locked");

        // Isolate drain from the pulse and verify actual enemy damage, friendly health and healing.
        for(Unit unit : Groups.unit.copy()) unit.remove();
        Vars.state.rules.tags.put("cryon-allied-teams", Integer.toString(Team.green.id));
        Unit roche = spawn(Vars.content.unit("cryon-roche"), Team.sharded, 1000, 1000);
        Unit friend = spawn(UnitTypes.toxopid, Team.sharded, 1001, 1000);
        Unit ally = spawn(UnitTypes.toxopid, Team.green, 1002, 1000);
        Unit enemy = spawn(UnitTypes.toxopid, Team.crux, 1010, 1000);
        Ability ability = java.util.Arrays.stream(roche.abilities)
            .filter(a -> a.getClass().getSimpleName().equals("RocheFieldAbility")).findFirst().orElseThrow();
        ability.getClass().getField("reload").setFloat(ability, Float.MAX_VALUE);
        ability.getClass().getField("drainReload").setFloat(ability, 1f);
        roche.health = roche.maxHealth / 2f;
        index();
        float before = roche.health, friendBefore = friend.health, allyBefore = ally.health, enemyBefore = enemy.health;
        Time.delta = 1f; ability.update(roche);
        check(friend.health == friendBefore && ally.health == allyBefore, "drain attacked same-team/allied units");
        check(enemy.health < enemyBefore && roche.health > before, "drain did not damage enemy and heal Roche");
        check(Math.abs((roche.health - before) - (enemyBefore - enemy.health)) < 0.1f, "healing differs from actual damage");
        enemy.remove(); index(); before = roche.health;
        ability.update(roche);
        check(roche.health == before && friend.health == friendBefore && ally.health == allyBefore, "no-enemy drain used an ally");
        System.out.println("DESOLATE_CHECK_PASS: unconfigured map unchanged, manual processor, wave boundaries, nuclear activation, eight-ship formation, cinematic save/load, victory/save recovery, enemy-only drain and allied safety");
    }
}
