import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.*;
import java.nio.file.Files;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.io.SaveIO;
import mindustry.net.Net;
import mindustry.world.Tile;

/** Actual scripted spawn, native weapon damage, Alliance targets and native save restoration. */
public final class PorphyraRaidCheck{
    static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }
    static void tick(){
        Time.delta = 1f; Vars.state.teams.updateTeamStats();
        Groups.unit.updatePhysics(); Groups.bullet.updatePhysics();
        for(Unit unit : Groups.unit.copy()) if(!unit.dead && unit.team == Team.sharded) unit.update();
        Groups.bullet.update(); Groups.bullet.collide(); Time.update(); Vars.state.tick++;
    }
    public static void main(String[] args){
        try{ run(); System.exit(0); }
        catch(Throwable error){ error.printStackTrace(); System.exit(1); }
    }
    static void run() throws Exception{
        Core.app = new MockApplication(); Core.files = new MockFiles(); Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Fi data = new Fi(Files.createTempDirectory("cryon-porphyra-raid-").toFile());
        Core.settings = new Settings(); Core.settings.setDataDirectory(data);
        Core.bundle = I18NBundle.createBundle(new Fi("bundles/bundle"));
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlaunch.jar"));
        Vars.headless = true; Vars.loadLocales = false; Vars.net = new Net(null);
        Vars.platform = new mindustry.core.Platform(){};
        mindustry.core.Version.init(); Vars.init(); Core.settings.setJson(mindustry.io.JsonIO.json);
        Vars.content.createBaseContent(); Vars.content.createModContent(); Vars.content.init();
        var loader = Vars.mods.locateMod("cryon").main.getClass().getClassLoader();
        Class<?> raid = loader.loadClass("cryon.Story.ShardedReceptorRaid");
        Class<?> allies = loader.loadClass("cryon.Features.MapAlliances");
        allies.getMethod("install").invoke(null); raid.getMethod("install").invoke(null);
        String tag = (String)raid.getField("tag").get(null);
        double flag = raid.getField("unitFlag").getDouble(null);
        Vars.logic = new Logic(); Vars.logic.reset();
        Team alliance = Team.get(170);
        Vars.state.rules.defaultTeam = alliance; Vars.state.rules.waves = false; Vars.state.rules.canGameOver = false;
        Vars.world.loadGenerator(100, 100, tiles -> {
            for(int x = 0; x < 100; x++) for(int y = 0; y < 100; y++) tiles.set(x, y, new Tile(x, y, Blocks.stone, Blocks.air, Blocks.air));
            tiles.get(20, 20).setBlock(Blocks.coreShard, alliance);
            tiles.get(24, 24).setBlock(Vars.content.block("cryon-java-processor"), alliance);
        });
        allies.getMethod("setAlly", Rules.class, Team.class, boolean.class).invoke(null, Vars.state.rules, Team.crux, true);
        allies.getMethod("setAlly", Rules.class, Team.class, boolean.class).invoke(null, Vars.state.rules, Team.malis, true);
        Vars.state.set(mindustry.core.GameState.State.playing);
        Building processor = Vars.world.build(24, 24);
        processor.getClass().getMethod("setScript", String.class).invoke(processor, "radio-porphyra");
        Object script = processor.getClass().getField("script").get(processor);
        Time.delta = 36000f;
        for(int i = 0; i < 150 && !Vars.state.rules.tags.getBool(tag); i++) script.getClass().getMethod("update").invoke(script);
        check(Vars.state.rules.tags.getBool(tag), "Default Porphyra dialogue failed to trigger raid");
        check(Groups.unit.count(u -> u.team == Team.sharded && u.type.name.equals("cryon-receptor") && !u.dead) == 6,
            "Expected six live Sharded receptors, including without a Sharded core");
        check(Groups.unit.count(u -> u.team == Team.crux) == 0, "Raid created unwanted Crux defenders");
        for(Unit unit : Groups.unit) if(unit.team == Team.sharded){
            check(unit.flag == flag && unit.controller().getClass().getSimpleName().equals("ShardedReceptorRaidAI"), "Missing raid AI/marker");
            check(!unit.tileOn().block().solid, "Raid spawned inside solid terrain");
            check(unit.dst(alliance.core()) > 400f, "Raid spawned too close to the Alliance landing core");
        }
        // Observe damage to the player's Alliance base, without deliberately targeting Crux.
        Building playerCore = alliance.core();
        playerCore.health = 10000f;
        Unit protectedUnit = UnitTypes.dagger.spawn(Team.crux, 160f, 100f);
        float protectedHealth = protectedUnit.health;
        Unit playerUnit = UnitTypes.dagger.spawn(alliance, 160f, 100f);
        Groups.unit.updatePhysics(); Vars.state.teams.updateTeamStats();
        Unit attacker = Groups.unit.find(u -> u.team == Team.sharded);
        Teamc selected = attacker.controller().getClass().getMethod("findTarget", float.class, float.class, float.class, boolean.class, boolean.class)
            .invoke(attacker.controller(), 160f, 100f, 30f, true, true) instanceof Teamc target ? target : null;
        check(selected == playerUnit, "Raid failed to select the player's Alliance unit alongside Crux");
        playerUnit.remove();
        var controlPath = Vars.controlPath;
        Vars.controlPath = null; // Flat fixture needs no asynchronous pathfinding service.
        for(int i = 0; i < 600; i++) tick();
        Vars.controlPath = controlPath;
        check(playerCore.health < 10000f, "Raid caused no actual native weapon damage to the Alliance base");
        check(protectedUnit.health == protectedHealth, "Raid deliberately attacked Crux instead of Alliance");
        int count = Groups.unit.count(u -> u.team == Team.sharded);
        raid.getMethod("start", Building.class, int.class).invoke(null, processor, 6);
        check(Groups.unit.count(u -> u.team == Team.sharded) == count, "Repeated trigger duplicated raid");
        Fi save = data.child("raid.msav"); SaveIO.write(save); SaveIO.load(save);
        check(Vars.state.rules.tags.getBool(tag), "Raid one-shot flag did not survive save");
        check(Groups.unit.count(u -> u.team == Team.sharded && u.flag == flag && !u.dead) == count, "Save lost living raid units");
        for(Unit unit : Groups.unit) if(unit.team == Team.sharded && unit.flag == flag)
            check(unit.controller().getClass().getSimpleName().equals("ShardedReceptorRaidAI"), "Save restored generic AI instead of raid AI");
        processor = Vars.world.build(24, 24);
        raid.getMethod("start", Building.class, int.class).invoke(null, processor, 6);
        check(Groups.unit.count(u -> u.team == Team.sharded) == count, "Reload trigger duplicated raid");
        // A new configured batch still targets the Alliance without creating Crux guards.
        Vars.state.rules.tags.remove(tag);
        int guards = Groups.unit.count(u -> u.team == Team.crux);
        raid.getMethod("start", Building.class, int.class).invoke(null, processor, 2);
        check(Groups.unit.count(u -> u.team == Team.crux) == guards, "Existing Crux targets caused unnecessary defenders");
        check(Groups.unit.count(u -> u.team == Team.sharded) == count + 2, "Configured raid count ignored");
        System.out.println("PORPHYRA_RAID_PASS: dialogue trigger, six live receptors, no Crux fallback, actual Alliance base damage, Crux exclusion, one-shot and native save AI restoration");
    }
}
