import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.I18NBundle;
import java.nio.file.Files;
import mindustry.*;
import mindustry.content.TechTree;
import mindustry.game.Objectives.Research;
import mindustry.net.Net;
import mindustry.type.*;
import mindustry.world.blocks.defense.turrets.PowerTurret;

/** Packaged content and actual Cryon tree: advanced costs must gate Themis research. */
public class ThemisResearchCheck{
    static void check(boolean ok, String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        Core.app = new MockApplication(); Core.files = new MockFiles();
        Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Core.settings = new Settings();
        Fi data = new Fi(Files.createTempDirectory("themis-research-check-").toFile());
        Core.settings.setDataDirectory(data); Core.bundle = I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlaunch.jar"));
        Vars.headless = true; Vars.loadLocales = false; Vars.net = new Net(null);
        Vars.platform = new mindustry.core.Platform(){};
        mindustry.core.Version.init(); Vars.init();
        Vars.content.createBaseContent(); Vars.content.createModContent(); Vars.content.init();
        var mod = Vars.mods.locateMod("cryon");
        check(mod != null && !mod.hasContentErrors(), "packaged content errors");
        // Headless mode skips atlas loading; planet tabs expect the desktop icon to exist.
        Vars.content.block("cryon-themis").uiIcon = new arc.graphics.g2d.TextureRegion();
        mod.main.getClass().getClassLoader().loadClass("cryon.TechTree.CryonTechTree").getMethod("load").invoke(null);
        var themis = (PowerTurret)Vars.content.block("cryon-themis");
        var abyss = Vars.content.block("cryon-abyss");
        Planet planet = Vars.content.planet("cryon-cryon");
        check(themis.techNode != null && themis.techNode.parent.content == abyss, "Themis must follow Abyss");
        var root = themis.techNode;
        while(root.parent != null) root = root.parent;
        check(root == planet.techTree && TechTree.all.count(n -> n.content == themis) == 1, "Themis absent/duplicated in Cryon tree");
        check(themis.shownPlanets.contains(planet), "Themis not visible on Cryon");
        String[] names = {"farstar-alloy", "cryo-alloy", "nano-material", "neutronite"};
        int[] amounts = {3200, 1800, 2400, 1400};
        check(themis.requirements.length == names.length, "Unexpected construction material");
        for(int i = 0; i < names.length; i++){
            Item item = Vars.content.item("cryon-" + names[i]);
            final Item material = item;
            ItemStack cost = java.util.Arrays.stream(themis.requirements).filter(s -> s.item == material).findFirst().orElse(null);
            check(cost != null && cost.amount == amounts[i], "Incorrect construction cost: " + names[i]);
            check(item.techNode != null, "Material missing research: " + names[i]);
            check(java.util.Arrays.stream(themis.techNode.requirements).anyMatch(s -> s.item == material && s.amount > 0), "Material missing from research cost");
            Research objective = (Research)themis.techNode.objectives.find(o -> o instanceof Research && ((Research)o).content == material);
            check(objective != null, "Missing material research prerequisite");
            item.clearUnlock(); check(!objective.complete(), "Unresearched material does not gate Themis");
            item.quietUnlock(); check(objective.complete(), "Material prerequisite cannot be completed");
        }
        check(themis.size == 5 && themis.heatRequirement == 100f && Math.abs(themis.consPower.usage * 60f - 4000f) < 0.01f,
            "Themis size/power/heat settings regressed");
        System.out.println("PASS: packaged Themis; four advanced Cryon construction/research materials; real material unlock prerequisites; unique Cryon tree node following Abyss; planet visibility; 5x5/4000 power/100 heat");
        System.exit(0);
    }
}
