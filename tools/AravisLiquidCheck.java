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

/** Headless integration check of real Aravis terrain, landing core, ores and wave routes. */
public class AravisLiquidCheck {
    static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }

    public static void main(String[] args) throws Exception {
        try { run(); System.exit(0); }
        catch(Throwable failure){ failure.printStackTrace(); System.exit(1); }
    }

    static void run() throws Exception {
        Core.app = new MockApplication(); Core.files = new MockFiles();
        Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Core.settings = new Settings(); Core.settings.setDataDirectory(new Fi("/tmp/aravis-liquid-check"));
        Core.bundle = I18NBundle.createEmptyBundle();
        Vars.headless = true; Vars.loadLocales = false;
        Vars.net = new Net(null); Vars.platform = new mindustry.core.Platform(){};
        Vars.init(); Vars.content.createBaseContent();
        Mods.ModMeta meta = new Mods.ModMeta(); meta.name = "cryon";
        Mods.LoadedMod mod = new Mods.LoadedMod(new Fi("."), new Fi("."), null,
                AravisLiquidCheck.class.getClassLoader(), meta);
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
        parser.finishParsing();
        Vars.content.init(); Vars.logic = new Logic();
        AravisPlanetGenerator generator = new AravisPlanetGenerator();
        planet.generator = generator;
        for(int run=0;run<8;run++){
            Vars.logic.reset();
            Sector sector = planet.sectors.get(run);
            Vars.state.rules = new Rules();
            Vars.state.rules.sector = sector; Vars.state.rules.planet = planet;
            Vars.world.loadGenerator(192,192, tiles -> generator.generate(tiles,sector,new WorldParams()));
            verify("generated sector " + run);
        }
        for(String name:new String[]{"landing","extension","ridge","checkpoint","dunes","ashes","aravis-aegis","terminus"}){
            Vars.logic.reset();
            SaveIO.load(new Fi("maps/cryon-" + name + ".msav"));
            var core = Vars.state.rules.defaultTeam.core();
            check(core != null,"missing core " + name);
            int[] floors=new int[Vars.world.tiles.width*Vars.world.tiles.height];
            for(Tile t:Vars.world.tiles) floors[t.array()]=t.floor().id;
            AravisLiquidResources.ensure(Vars.world.tiles,core.tile);
            for(Tile t:Vars.world.tiles){
                if(t.block()!=Blocks.air || t.overlay()!=Blocks.air || Vars.content.block(floors[t.array()]).asFloor().isLiquid)
                    check(t.floor().id==floors[t.array()],"changed protected terrain " + name);
            }
            verify("campaign map " + name);
        }
        System.out.println("PASS: generated/campaign deposits, preservation and idempotence");
    }
    static void verify(String name){
        var pump = (mindustry.world.blocks.production.Pump)Vars.content.block("cryon-aeolian-pump");
        for(Liquid liquid:new Liquid[]{mindustry.content.Liquids.oil,mindustry.content.Liquids.slag}){
            boolean found=false;
            for(Tile t:Vars.world.tiles){
                boolean square=true;
                for(int dx=-1;dx<=1;dx++) for(int dy=-1;dy<=1;dy++){
                    Tile n=Vars.world.tile(t.x+dx,t.y+dy);
                    if(n==null || n.floor().liquidDrop!=liquid) square=false;
                }
                if(square && pump.canPlaceOn(t,Team.sharded,0) && t.build == null){
                    found=true;
                    t.setBlock(pump,Team.sharded);
                    var build = (mindustry.world.blocks.production.Pump.PumpBuild)t.build;
                    arc.util.Time.delta=1f;
                    build.power.status=1f;
                    build.updateConsumption(); build.updateTile();
                    check(build.liquids.get(liquid)>0f,"pump cannot extract " + liquid.name + " in " + name);
                    t.setAir();
                    break;
                }
            }
            check(found,"missing pumpable " + liquid.name + " in " + name);
        }
        long before=hash();
        AravisLiquidResources.ensure(Vars.world.tiles,Vars.state.rules.defaultTeam.core().tile);
        check(hash()==before,"non-idempotent deposits " + name);
        System.out.println("PASS " + name);
    }
    static long hash(){
        long hash=0;
        for(Tile t:Vars.world.tiles)hash=hash*31+t.floor().id;
        return hash;
    }
}
