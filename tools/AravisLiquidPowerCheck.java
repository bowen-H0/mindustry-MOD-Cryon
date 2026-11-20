import arc.Core;
import arc.Settings;
import arc.files.Fi;
import arc.mock.*;
import arc.util.I18NBundle;
import cryon.Content.AravisEnvironment;
import cryon.Content.CryonItems;
import cryon.Content.AravisLiquidResources;
import mindustry.io.SaveIO;
import mindustry.type.Liquid;
import cryon.Generator.AravisPlanetGenerator;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Planets;
import mindustry.core.Logic;
import mindustry.ctype.ContentType;
import mindustry.game.Rules;
import mindustry.game.Team;
import mindustry.mod.ContentParser;
import mindustry.mod.Mods;
import mindustry.net.Net;
import mindustry.type.Planet;
import mindustry.type.Sector;
import mindustry.world.Tile;
import mindustry.world.WorldParams;
import java.util.ArrayDeque;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Native HJSON, research and liquid-only generator integration checks. */
public class AravisLiquidPowerCheck {
    static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }

    public static void main(String[] args) throws Exception {
        try { run(); System.exit(0); }
        catch(Throwable failure){ failure.printStackTrace(); System.exit(1); }
    }

    static void run() throws Exception {
        Core.app = new MockApplication(); Core.files = new MockFiles();
        Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Core.settings = new Settings(); Core.settings.setDataDirectory(new Fi("/tmp/aravis-liquid-power-check"));
        Core.bundle = I18NBundle.createEmptyBundle();
        Vars.headless = true; Vars.loadLocales = false;
        Vars.net = new Net(null); Vars.platform = new mindustry.core.Platform(){};
        Vars.init(); Vars.content.createBaseContent();
        Mods.ModMeta meta = new Mods.ModMeta(); meta.name = "cryon";
        Mods.LoadedMod mod = new Mods.LoadedMod(new Fi("."), new Fi("."), null,
                AravisLiquidPowerCheck.class.getClassLoader(), meta);
        Vars.content.setCurrentMod(mod);
        CryonItems.load(); AravisEnvironment.load();
        Planet planet = new Planet("aravis", Planets.sun, 1f, 2);
        Vars.content.setCurrentMod(null);
        ContentParser parser = new ContentParser();
        for(Fi f : new Fi("content/units/aravis").list())
            parser.parse(mod, f.nameWithoutExtension(), f.readString(), f, ContentType.unit);
        String[] blocks = {"dune-sand", "aeolian-sand", "red-sand", "gravel-sand", "dune-sand-wall",
                "aeolian-sand-wall", "red-sand-wall", "ore-ferrum", "ore-manganese", "ore-nickel"};
        for(String name : blocks){
            Fi f = new Fi("content/blocks/environment/aravis/" + name + ".hjson");
            parser.parse(mod, name, f.readString(), f, ContentType.block);
        }
        for(Fi core : new Fi("content/blocks/storage/aravis").list()){
            parser.parse(mod, core.nameWithoutExtension(), core.readString(), core, ContentType.block);
        }
        Fi pumpFile = new Fi("content/blocks/liquid/aravis/aeolian-pump.hjson");
        parser.parse(mod, "aeolian-pump", pumpFile.readString(), pumpFile, ContentType.block);
        for(String name:new String[]{"aeolian-node","coal-burner","aeolian-turbine-generator","aravis-oil-generator","aravis-slag-generator"}){
            Fi file=new Fi("content/blocks/power/aravis/"+name+".hjson");
            parser.parse(mod,name,file.readString(),file,ContentType.block);
        }
        Fi drillFile=new Fi("content/blocks/production/aravis/aeolian-drill.hjson");
        parser.parse(mod,"aeolian-drill",drillFile.readString(),drillFile,ContentType.block);
        parser.finishParsing();
        Vars.content.init(); Vars.logic = new Logic();
        Vars.mods.getMods().add(mod);
        for(var block:Vars.content.blocks())block.uiIcon=new arc.graphics.g2d.TextureRegion();
        cryon.TechTree.AravisTechTree.load();
        arc.util.Time.delta=1f;
        Vars.world.loadGenerator(48,48,tiles -> {
            tiles.fill();
            for(Tile tile:tiles)tile.setFloor(Blocks.sand.asFloor());
        });
        for(boolean oil:new boolean[]{true,false}){
            String name=oil ? "aravis-oil-generator" : "aravis-slag-generator";
            Liquid fuel=oil ? mindustry.content.Liquids.oil : mindustry.content.Liquids.slag;
            float output=oil ? 8f : 6f;
            var block=(mindustry.world.blocks.power.ConsumeGenerator)Vars.content.block("cryon-"+name);
            check(block.techNode != null,"missing research " + name);
            check(block.techNode.parent.content.name.equals("cryon-aeolian-turbine-generator"),"incorrect research parent");
            check(block.shownPlanets.contains(planet),"wrong planet " + name);
            check(!block.hasItems && block.consPower==null,"unexpected solid fuel or power input");
            Tile tile=Vars.world.tile(20,20);
            tile.setBlock(block,Team.sharded);
            var build=(mindustry.world.blocks.power.ConsumeGenerator.ConsumeGeneratorBuild)tile.build;
            build.updateConsumption();build.updateTile();
            near(build.getPowerProduction(),0f,"empty generator produces power");
            check(build.acceptLiquid(null,fuel),"fuel rejected");
            check(!build.acceptLiquid(null,mindustry.content.Liquids.water),"water accepted as fuel");
            build.liquids.add(fuel,10f);
            build.updateConsumption();build.updateTile();
            near(build.getPowerProduction(),output,"incorrect rated output");
            near(build.liquids.get(fuel),9.8f,"incorrect fuel consumption");
            build.liquids.clear();build.liquids.add(fuel,0.1f);
            build.updateConsumption();build.updateTile();
            near(build.getPowerProduction(),output*0.5f,"partial fuel output incorrect");
            near(build.liquids.get(fuel),0f,"partial fuel consumption incorrect");
            build.updateConsumption();build.updateTile();
            near(build.getPowerProduction(),0f,"exhausted generator produces power");
            build.liquids.add(fuel,10f);build.enabled=false;
            build.updateConsumption();build.updateTile();
            near(build.getPowerProduction(),0f,"disabled generator produces power");
            near(build.liquids.get(fuel),10f,"disabled generator wastes fuel");
            tile.setAir();
            System.out.println("PASS " + name + ": research, rated/partial output, consumption, fuel exhaustion, wrong fuel, disabled state");
        }
    }
    static void near(float actual,float expected,String message){
        check(Math.abs(actual-expected)<0.001f,message+": "+actual);
    }
}
