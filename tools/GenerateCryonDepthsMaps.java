import arc.Core;
import arc.Settings;
import arc.files.Fi;
import arc.mock.*;
import arc.struct.StringMap;
import arc.util.I18NBundle;
import mindustry.Vars;
import mindustry.core.Logic;
import mindustry.io.MapIO;
import mindustry.maps.Map;
import mindustry.net.Net;
import mindustry.type.Planet;
import mindustry.world.WorldParams;

/** Builds the six fixed Cryon underground campaign maps. */
public class GenerateCryonDepthsMaps{
    public static void main(String[] args) throws Exception{
        Core.app = new MockApplication(); Core.files = new MockFiles();
        Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Core.settings = new Settings();
        Core.settings.setDataDirectory(new Fi("/tmp/cryon-depths-map-build"));
        Core.bundle = I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(
                Core.settings.getDataDirectory().child("mods/cryon.jar"));
        Vars.headless = true; Vars.loadLocales = false;
        Vars.net = new Net(null); Vars.platform = new mindustry.core.Platform(){};
        mindustry.core.Version.init();
        Vars.init(); Vars.content.createBaseContent(); Vars.content.createModContent(); Vars.content.init();
        Planet planet = Vars.content.planet("cryon-cryon-depths");
        cryon.Features.NormaTeam.init();
        cryon.Features.AllianceTeam.init();
        planet.generator = new cryon.Generator.CryonDepthsGenerator();
        Vars.logic = new Logic();
        String[] names = cryon.Features.CryonCampaignLayers.stages;
        Fi dir = new Fi("maps/cryon-cryon-depths");
        dir.mkdirs();
        for(int stage = 0; stage < names.length; stage++){
            var sector = planet.sectors.get(stage);
            Vars.logic.reset();
            Vars.state.rules.sector = sector;
            Vars.state.rules.planet = planet;
            Vars.world.loadGenerator(224, 224, tiles -> planet.generator.generate(tiles, sector, new WorldParams()));
            // Map metadata must be independent of the live campaign object graph.
            Vars.state.rules.sector = null;
            Vars.state.rules.planet = null;
            StringMap tags = new StringMap();
            tags.put("name", names[stage]);
            tags.put("author", "Cryon");
            tags.put("description", "Cryon underground stage " + (stage + 1));
            Fi output = dir.child("cryon-" + names[stage] + ".msav");
            MapIO.writeMap(output, new Map(output, 224, 224, tags, false));
            System.out.println("MAP " + stage + " " + output.length());
        }
    }
}
