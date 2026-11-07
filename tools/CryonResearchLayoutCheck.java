import arc.*;
import arc.files.Fi;
import arc.scene.ui.Dialog;
import arc.util.Log;
import java.nio.file.Files;
import java.util.HashSet;
import mindustry.*;
import mindustry.content.Planets;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.graphics.g3d.PlanetParams;
import mindustry.ui.dialogs.BaseDialog;

/** Real library-map research entry: switched trees must be laid out, not stacked at (0, 0). */
public class CryonResearchLayoutCheck{
    static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception{
        Fi data = new Fi(Files.createTempDirectory("cryon-research-layout-").toFile());
        new Fi(args.length > 0 ? args[0] : "build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlanuch.jar"));
        System.setProperty("mindustry.data.dir", data.absolutePath());
        Events.on(ClientLoadEvent.class, event -> Core.app.post(() -> {
            try{
                for(var actor : Core.scene.root.getChildren().copy())
                    if(actor instanceof Dialog dialog){ dialog.clearActions(); dialog.hide(null); }
                var surface = Vars.content.planet("cryon-cryon");
                var loader = Vars.mods.locateMod("cryon").main.getClass().getClassLoader();
                Core.settings.put("campaignselect", true);
                var libraryLoader = Vars.mods.locateMod("unitlanuch").main.getClass().getClassLoader();
                Object ui = libraryLoader.loadClass("ul.CVars").getField("cui").get(null);
                BaseDialog library = (BaseDialog)ui.getClass().getField("cplanet").get(ui);
                library.show();
                check(library != null && library != Vars.ui.planet && library.isShown(), "library map not open");
                library.getClass().getMethod("viewPlanet", mindustry.type.Planet.class, boolean.class)
                        .invoke(library, surface, false);
                check(((PlanetParams)library.getClass().getField("state").get(library)).planet == surface, "wrong library planet");
                // Native ResearchDialog uses the native map/campaign context first.
                Vars.ui.planet.state.planet = Planets.serpulo;
                for(int run = 0; run < 5; run++){
                    Vars.ui.research.rebuildTree(Planets.serpulo.techTree);
                    Vars.ui.research.show();
                    check(Vars.ui.research.root.node == surface.techTree, "wrong research root");
                    HashSet<String> positions = new HashSet<>();
                    int visible = 0;
                    for(var node : Vars.ui.research.nodes){
                        if(node.visible){ visible++; positions.add(node.x + "," + node.y); }
                    }
                    Log.info("RESEARCH_LAYOUT run=@ nodes=@ visible=@ positions=@", run, Vars.ui.research.nodes.size, visible, positions.size());
                    check(Vars.ui.research.nodes.size > 100, "missing tree branches");
                    check(visible > 1 && positions.size() == visible, "research nodes overlap at the same position");
                    Vars.ui.research.hide(null);
                }
                Log.info("RESEARCH_LAYOUT PASS: five library-map openings retain distinct node positions");
                Core.app.exit();
            }catch(Throwable error){ Log.err(error); System.exit(1); }
        }));
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
