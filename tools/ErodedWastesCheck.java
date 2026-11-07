import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.struct.StringMap;
import arc.util.*;
import java.nio.file.Files;
import java.util.Locale;
import mindustry.*;
import mindustry.core.Logic;
import mindustry.game.Objectives.*;
import mindustry.io.*;
import mindustry.maps.generators.PlanetGenerator;
import mindustry.net.Net;
import mindustry.type.*;
import mindustry.world.WorldParams;

/** Packaged sector, progression and map check, isolated from player saves. */
public class ErodedWastesCheck{
    static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception{
        try{ run(args.length > 0 && args[0].equals("--generate")); System.exit(0); }
        catch(Throwable error){ error.printStackTrace(); System.exit(1); }
    }
    static void run(boolean generate) throws Exception{
        Core.app = new MockApplication(); Core.files = new MockFiles();
        Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Core.settings = new Settings();
        Fi data = new Fi(Files.createTempDirectory("cryon-eroded-wastes-").toFile());
        Core.settings.setDataDirectory(data);
        Core.bundle = I18NBundle.createBundle(Core.files.internal("bundles/bundle"), Locale.SIMPLIFIED_CHINESE);
        Core.bundle.getProperties().putAll(I18NBundle.createBundle(new Fi("bundles/bundle"), Locale.SIMPLIFIED_CHINESE).getProperties());
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlanuch.jar"));
        Vars.headless = true; Vars.loadLocales = false; Vars.net = new Net(null);
        Vars.platform = new mindustry.core.Platform(){};
        mindustry.core.Version.init(); Vars.init();
        Core.settings.setJson(JsonIO.json);
        Vars.content.createBaseContent(); Vars.content.createModContent(); Vars.content.init();
        var mod = Vars.mods.locateMod("cryon");
        check(mod != null && !mod.hasContentErrors(), "packaged content errors");
        var loader = mod.main.getClass().getClassLoader();
        Vars.schematics = new mindustry.game.Schematics();
        loader.loadClass("cryon.TechTree.CryonTechTree").getMethod("load").invoke(null);
        SectorPreset preset = Vars.content.sector("cryon-eroded-wastes");
        SectorPreset lab = Vars.content.sector("cryon-laboratory-088");
        check(preset != null && preset.planet == lab.planet, "wrong/missing planet");
        check(preset.sector.id == 89 && preset.sector.isNear(lab.sector), "not adjacent to laboratory");
        check(preset.difficulty == 10 && preset.sector.threat >= 1f, "wrong threat");
        check(preset.sector.displayThreat().endsWith(Core.bundle.get("threat.eradication")), "threat label is not eradication");
        check(preset.localizedName.equals("蚀尽荒原"), "wrong Chinese name");
        check(preset.techNode.parent == lab.techNode, "wrong progression parent");
        check(preset.techNode.objectives.contains(o -> o instanceof SectorComplete s && s.preset == lab), "missing laboratory prerequisite");
        check(preset.techNode.objectives.contains(o -> o instanceof Research r && r.content == Vars.content.item("cryon-nano-material")), "missing nano-material prerequisite");
        var reactor = Vars.content.block("cryon-nano-reactor");
        check(reactor != null && reactor.techNode != null && reactor.techNode.objectives.contains(
                o -> o instanceof SectorComplete s && s.preset == preset), "missing reactor sector prerequisite");
        Vars.logic = new Logic(); Vars.logic.reset();
        if(generate){
            PlanetGenerator generator = (PlanetGenerator)loader.loadClass("cryon.Generator.CryonPlanetGenerator").getConstructor().newInstance();
            generator.baseSeed = 12;
            preset.planet.generator = generator;
            Vars.state.rules.sector = preset.sector;
            Vars.state.rules.planet = preset.planet;
            preset.sector.generateEnemyBase = false;
            Vars.world.loadGenerator(320, 320, tiles -> generator.generate(tiles, preset.sector, new WorldParams()));
            Vars.state.rules.winWave = preset.captureWave;
            Vars.state.rules.sector = null;
            StringMap tags = StringMap.of("name", "蚀尽荒原", "description", "实验区附近的荒原。抵御敌军攻势。", "author", "Cryon");
            MapIO.writeMap(new Fi("maps/cryon-eroded-wastes.msav"), new mindustry.maps.Map(tags));
        }else{
            Vars.world.loadSector(preset.sector);
            check(Vars.state.rules.sector == preset.sector, "wrong loaded sector");
        }
        check(Vars.state.rules.waves && !Vars.state.rules.attackMode && Vars.state.rules.winWave == 40, "wrong mission rules");
        check(Vars.state.rules.spawns.size > 0, "missing waves");
        check(Vars.state.rules.defaultTeam.core() != null, "missing player core");
        check(Vars.spawner.countSpawns() > 0, "missing enemy spawn");
        System.out.println("PASS: adjacent sector 89, eradication threat, laboratory + nano-material prerequisites, playable 40-wave map");
    }
}
