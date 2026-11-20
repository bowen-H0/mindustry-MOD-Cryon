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

/** Native burst mining of tungsten, mandatory inputs, full storage and research. */
public class AravisImpactDrillCheck {
    static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }

    public static void main(String[] args) throws Exception {
        try { run(); System.exit(0); }
        catch(Throwable failure){ failure.printStackTrace(); System.exit(1); }
    }

    static void run() throws Exception {
        Core.app = new MockApplication(); Core.files = new MockFiles();
        Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Core.settings = new Settings(); Core.settings.setDataDirectory(new Fi("/tmp/aravis-impact-drill-check"));
        Core.bundle = I18NBundle.createEmptyBundle();
        Vars.headless = true; Vars.loadLocales = false;
        Vars.net = new Net(null); Vars.platform = new mindustry.core.Platform(){};
        Vars.init(); Vars.content.createBaseContent();
        Mods.ModMeta meta = new Mods.ModMeta(); meta.name = "cryon";
        Mods.LoadedMod mod = new Mods.LoadedMod(new Fi("."), new Fi("."), null,
                AravisImpactDrillCheck.class.getClassLoader(), meta);
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
        for(String name:new String[]{"extension-drill","aravis-impact-drill"}){
            Fi file=new Fi("content/blocks/production/aravis/"+name+".hjson");
            parser.parse(mod,name,file.readString(),file,ContentType.block);
        }
        parser.finishParsing();
        Vars.content.init(); Vars.logic = new Logic();
        Vars.mods.getMods().add(mod);
        for(var block:Vars.content.blocks())block.uiIcon=new arc.graphics.g2d.TextureRegion();
        cryon.TechTree.AravisTechTree.load();
        arc.util.Time.delta=1f;
        Vars.world.loadGenerator(64,64,tiles -> {
            tiles.fill();for(Tile t:tiles)t.setFloor(Blocks.stone.asFloor());
        });
        var drill=(mindustry.world.blocks.production.BurstDrill)Vars.content.block("cryon-aravis-impact-drill");
        check(drill.techNode!=null && drill.techNode.parent.content.name.equals("cryon-extension-drill"),"missing research parent");
        check(drill.shownPlanets.contains(planet),"missing Aravis ownership");
        check(java.util.Arrays.stream(drill.requirements).noneMatch(s -> s.item==mindustry.content.Items.tungsten),"tungsten bootstrapping requirement");
        Tile tile=Vars.world.tile(20,20);
        check(!drill.canPlaceOn(tile,Team.sharded,0),"drill accepts ore-free ground");
        for(int dx=-1;dx<=2;dx++)for(int dy=-1;dy<=2;dy++)
            Vars.world.tile(tile.x+dx,tile.y+dy).setOverlay(Blocks.oreTungsten);
        check(drill.canMine(tile),"cannot mine tungsten");
        check(!((mindustry.world.blocks.production.Drill)Vars.content.block("cryon-extension-drill")).canMine(tile),"old drill unexpectedly mines tungsten");
        check(drill.canPlaceOn(tile,Team.sharded,0),"cannot place on tungsten");
        tile.setBlock(drill,Team.sharded);
        var build=(mindustry.world.blocks.production.BurstDrill.BurstDrillBuild)tile.build;
        check(build.dominantItem==mindustry.content.Items.tungsten && build.dominantItems==16,"tungsten footprint detection failed");
        build.power.status=1f;
        build.updateConsumption();build.updateTile();
        check(build.progress==0f,"dry drill advanced");
        build.liquids.add(mindustry.content.Liquids.water,30f);build.power.status=0f;
        build.updateConsumption();build.updateTile();
        check(build.progress==0f && build.liquids.get(mindustry.content.Liquids.water)==30f,"unpowered drill advances or wastes water");
        for(int tick=0;tick<720;tick++){
            build.power.status=1f;build.liquids.set(mindustry.content.Liquids.water,30f);
            build.updateConsumption();build.updateTile();arc.util.Time.time+=1f;
            if(tick==0)check(Math.abs(build.liquids.get(mindustry.content.Liquids.water)-(30f-1f/6f))<0.001f,"wrong water use");
            if(tick<719)check(build.items.get(mindustry.content.Items.tungsten)==0,"burst drill released ore before impact");
        }
        check(build.items.get(mindustry.content.Items.tungsten)==16,"impact did not produce tungsten batch");
        build.items.set(mindustry.content.Items.tungsten,drill.itemCapacity);
        float progress=build.progress,water=build.liquids.get(mindustry.content.Liquids.water);
        build.updateConsumption();build.updateTile();
        check(build.progress==progress && build.liquids.get(mindustry.content.Liquids.water)==water,"full drill advances or wastes water");
        build.items.clear();build.enabled=false;water=build.liquids.get(mindustry.content.Liquids.water);
        build.updateConsumption();build.updateTile();
        check(build.progress==progress && build.liquids.get(mindustry.content.Liquids.water)==water,"disabled drill advances or wastes water");
        System.out.println("PASS: research, tungsten mining, 16-item impact batch, water use, power/water cutoff, full/disabled state, no tungsten construction cost");
    }
}
