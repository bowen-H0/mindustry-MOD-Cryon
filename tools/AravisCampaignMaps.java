import arc.Core;
import arc.Settings;
import arc.files.Fi;
import arc.mock.*;
import arc.util.I18NBundle;
import arc.struct.StringMap;
import cryon.Content.*;
import cryon.Generator.AravisPlanetGenerator;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.ctype.ContentType;
import mindustry.game.*;
import mindustry.io.*;
import mindustry.mod.*;
import mindustry.net.Net;
import mindustry.type.*;
import mindustry.world.*;
import java.util.ArrayDeque;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Reproducible native campaign map exporter; uses no player saves. */
public class AravisCampaignMaps {
    static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }
    public static void main(String[] args){
        try { run(); System.exit(0); } catch(Throwable t){ t.printStackTrace(); System.exit(1); }
    }
    static void run() throws Exception {
        Core.app = new MockApplication(); Core.files = new MockFiles();
        Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Core.settings = new Settings(); Core.settings.setDataDirectory(new Fi("/tmp/aravis-generator-check"));
        Core.bundle = I18NBundle.createEmptyBundle();
        Vars.headless = true; Vars.loadLocales = false;
        Vars.net = new Net(null); Vars.platform = new mindustry.core.Platform(){};
        Vars.init(); Vars.content.createBaseContent();
        Mods.ModMeta meta = new Mods.ModMeta(); meta.name = "cryon";
        Mods.LoadedMod mod = new Mods.LoadedMod(new Fi("."), new Fi("."), null,
                AravisCampaignMaps.class.getClassLoader(), meta);
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
        parser.finishParsing();
        Vars.content.init(); Vars.logic = new Logic();

        for(boolean finale : new boolean[]{false, true}) export(planet, finale);
        Vars.mods.getMods().add(mod);
        SectorPreset vanillaAegis=Vars.content.sector("aegis");
        Planet vanillaAegisPlanet=vanillaAegis.planet;
        for(Fi f : new Fi("content/sectors/aravis").list())
            parser.parse(mod, f.nameWithoutExtension(), f.readString(), f, ContentType.sector);
        parser.finishParsing();
        for(SectorPreset preset : Vars.content.sectors()) preset.init();
        cryon.TechTree.AravisTechTree.load();
        String[] names={"landing","extension","ridge","checkpoint","dunes","ashes","aravis-aegis","terminus"};
        for(int i=0;i<names.length;i++){
            SectorPreset preset=Vars.content.sector("cryon-"+names[i]);
            check(preset!=null && preset.sector.id==i && preset.generator!=null,"Missing campaign map "+names[i]);
            check(preset.techNode!=null,"Missing research node "+names[i]);
            if(i>0){
                SectorPreset previous=Vars.content.sector("cryon-"+names[i-1]);
                check(preset.techNode.objectives.contains(o -> o instanceof mindustry.game.Objectives.SectorComplete
                        && ((mindustry.game.Objectives.SectorComplete)o).preset==previous),"Missing capture gate "+names[i]);
            }
        }
        check(Vars.content.sector("aegis")==vanillaAegis && vanillaAegis.planet==vanillaAegisPlanet,"Vanilla Aegis was overwritten");
        check(Vars.content.sector("cryon-terminus").isLastSector,"Final sector flag missing");
        Core.settings.setJson(JsonIO.json);
        planet.generator=new AravisPlanetGenerator();
        planet.campaignRuleDefaults.fog=true;
        planet.campaignRules.fog=true;
        // Match the Aravis campaign rules installed by CryonJavaMod.
        planet.ruleSetter=r -> {r.fog=true; r.waveTeam=Team.malis; AravisEnvironment.applyWeather(r);};
        for(String name : new String[]{"aravis-aegis","terminus"}){
            SectorPreset preset=Vars.content.sector("cryon-"+name);
            Vars.logic.reset();
            Vars.world.loadSector(preset.sector);
            check(Vars.state.rules.fog,"Campaign load lost fog");
            check(Vars.state.rules.attackMode,"Campaign victory mode mismatch");
            check(Vars.state.teams.get(Team.malis).cores.size==(name.equals("terminus") ? 3 : 2),"Campaign enemy cores missing");
            System.out.println("PASS native campaign load: "+name);
        }
        System.out.println("PASS: all eight native maps resolve; sequential capture gates and finale flag");
    }
    static void clear(int x, int y, int radius){
        for(int dx=-radius; dx<=radius; dx++) for(int dy=-radius; dy<=radius; dy++){
            Tile t=Vars.world.tile(x+dx,y+dy);
            if(t!=null && dx*dx+dy*dy<=radius*radius){ t.setBlock(Blocks.air); t.setOverlay(Blocks.air); }
        }
    }
    static void fort(int x, int y, boolean main){
        clear(x,y,19);
        Vars.world.tile(x,y).setBlock(main ? Blocks.coreNucleus : Blocks.coreShard, Team.malis);
        for(int dx=-12; dx<=12; dx++) if(Math.abs(dx)>3){
            Vars.world.tile(x+dx,y-12).setBlock(Blocks.thoriumWall,Team.malis);
            Vars.world.tile(x+dx,y+12).setBlock(Blocks.thoriumWall,Team.malis);
        }
        for(int dy=-11; dy<=11; dy++) if(Math.abs(dy)>3){
            Vars.world.tile(x-12,y+dy).setBlock(Blocks.thoriumWall,Team.malis);
            Vars.world.tile(x+12,y+dy).setBlock(Blocks.thoriumWall,Team.malis);
        }
        for(int dx : new int[]{-8,8}) for(int dy : new int[]{-7,7}){
            Tile t=Vars.world.tile(x+dx,y+dy);
            t.setBlock(main ? Blocks.spectre : Blocks.salvo,Team.malis);
            t.build.items.add(main ? Items.thorium : Items.copper,1000);
        }
    }
    static void export(Planet planet, boolean finale) throws Exception {
        String name=finale ? "terminus" : "aravis-aegis";
        int size=finale ? 288 : 256;
        Vars.logic.reset();
        Rules r=new Rules(); Vars.state.rules=r;
        r.planet=planet; r.sector=planet.sectors.get(finale ? 7 : 6);
        AravisPlanetGenerator generator=new AravisPlanetGenerator();
        Vars.world.loadGenerator(size,size,tiles -> generator.generate(tiles,r.sector,new WorldParams()));
        r.fog=true; r.waveTeam=Team.malis; r.defaultTeam=Team.sharded;
        r.attackMode=true; r.waves=true; r.winWave=0;
        r.waveSpacing=60f*(finale ? 150 : 100); r.initialWaveSpacing=60f*300;
        r.coreDestroyClear=true; r.placeRangeCheck=false; r.polygonCoreProtection=false;
        r.teams.get(Team.malis).cheat=true;
        r.spawns.clear();
        wave("jaspis",0,3,2,18);
        wave("obsidianum",8,1,3,10);
        wave("nitrum",20,1,4,7);
        wave("hematites",40,1,7,3);
        if(finale){
            // Two outposts flank the approach to the final command fortress.
            int[][] forts={{size/2-45,size/2},{size/2+45,size/2+25},{size/2,size-44}};
            // Clear traversable roads from the landing area to every enemy core.
            for(int[] f:forts) for(int y=size/5+22;y<=f[1];y++){
                int x=Math.round(size/2f+(f[0]-size/2f)*(y-size/5f)/(f[1]-size/5f));
                clear(x,y,7);
            }
            for(int i=0;i<forts.length;i++) fort(forts[i][0],forts[i][1],i==2);
            // The accelerator is a recoverable landmark; victory uses native enemy-core destruction.
            clear(size/2,size-19,8);
            Vars.world.tile(size/2,size-19).setBlock(Blocks.interplanetaryAccelerator,Team.derelict);
            // Keep reinforcements outside the fortress footprint.
            for(Tile t:Vars.world.tiles) if(t.overlay()==Blocks.spawn) t.setOverlay(Blocks.air);
            clear(size/2,size-7,4);
            Vars.world.tile(size/2,size-7).setOverlay(Blocks.spawn);
        }else{
            // Two fortified cores guard the accelerator's outer approaches.
            int[][] forts={{size/2-40,size/2+18},{size/2+38,size-48}};
            for(int[] f:forts) for(int y=size/5+22;y<=f[1];y++){
                int x=Math.round(size/2f+(f[0]-size/2f)*(y-size/5f)/(f[1]-size/5f));
                clear(x,y,7);
            }
            for(int[] f:forts) fort(f[0],f[1],false);
            for(Tile t:Vars.world.tiles) if(t.overlay()==Blocks.spawn) t.setOverlay(Blocks.air);
            clear(size/2,size-7,4);
            Vars.world.tile(size/2,size-7).setOverlay(Blocks.spawn);
        }
        AravisEnvironment.applyWeather(r);
        Vars.state.teams.get(Team.sharded).core().items.add(Items.copper,600);
        Vars.state.teams.get(Team.sharded).core().items.add(CryonItems.ferrum,800);
        Vars.state.teams.get(Team.sharded).core().items.add(Items.lead,400);
        StringMap tags=new StringMap();
        tags.put("name",finale ? "Terminus" : "Aegis"); tags.put("author","Cryon");
        // Sector/planet links are supplied by the campaign when loading the map.
        r.sector=null;
        tags.put("rules",JsonIO.write(r));
        Vars.state.map=new mindustry.maps.Map(tags);
        Fi file=new Fi("maps/cryon-"+name+".msav");
        MapIO.writeMap(file,Vars.state.map);
        check(SaveIO.isSaveValid(file),"Invalid map: "+name);
        SaveIO.load(file);
        check(Vars.state.rules.fog && Vars.state.rules.waveTeam==Team.malis,"Lost fog/team");
        check(Vars.state.rules.attackMode,"Lost victory mode");
        check(Vars.state.teams.get(Team.sharded).cores.size==1,"Missing landing core");
        check(Vars.state.teams.get(Team.malis).cores.size==(finale ? 3 : 2),"Wrong enemy core count");
        Tile start=Vars.state.teams.get(Team.sharded).core().tile;
        boolean[] seen=new boolean[size*size]; ArrayDeque<Tile> q=new ArrayDeque<>();
        Tile exit=Vars.world.tile(start.x,start.y+3); q.add(exit); seen[exit.array()]=true;
        java.util.Set<String> ores=new java.util.HashSet<>(); int spawns=0;
        while(!q.isEmpty()){
            Tile t=q.remove(); if(t.overlay()==Blocks.spawn) spawns++;
            if(t.overlay().itemDrop!=null) ores.add(t.overlay().name);
            for(int d=0;d<4;d++) {Tile n=t.nearby(d); if(n!=null && !n.solid() && !seen[n.array()]){seen[n.array()]=true;q.add(n);}}
        }
        check(spawns>0,"No route to reinforcement spawn");
        for(String ore:new String[]{"cryon-ore-ferrum","ore-copper","ore-coal","cryon-ore-manganese","cryon-ore-nickel"})
            check(ores.contains(ore),"Unreachable ore: "+ore);
        for(var core:Vars.state.teams.get(Team.malis).cores){
            boolean reachable=false;
            for(int dx=-4;dx<=4;dx++) for(int dy=-4;dy<=4;dy++){
                Tile t=Vars.world.tile(core.tileX()+dx,core.tileY()+dy);
                if(t!=null && seen[t.array()]) reachable=true;
            }
            check(reachable,"Unreachable enemy core");
        }
        BufferedImage img=new BufferedImage(size*3,size*3,BufferedImage.TYPE_INT_RGB);
        for(Tile t:Vars.world.tiles){
            int rgb=t.floor().mapColor.rgb888();
            if(t.block().solid) rgb=t.build==null ? 0x584d40 : t.team().color.rgb888();
            if(t.overlay().itemDrop!=null) rgb=t.overlay().itemDrop.color.rgb888();
            if(t.overlay()==Blocks.spawn) rgb=0xff2222;
            for(int dx=0;dx<3;dx++) for(int dy=0;dy<3;dy++) img.setRGB(t.x*3+dx,(size-1-t.y)*3+dy,rgb);
        }
        Fi preview=new Fi("build/reports/"+name+"-preview.png");preview.parent().mkdirs();
        ImageIO.write(img,"png",preview.file());
        System.out.println("PASS "+name+": native roundtrip, victory rules, cores, ore access and ground routes");
    }
    static void wave(String name,int begin,int amount,int spacing,int max){
        SpawnGroup g=new SpawnGroup(Vars.content.unit("cryon-"+name));
        g.begin=begin;g.unitAmount=amount;g.spacing=spacing;g.max=max;g.unitScaling=8;
        Vars.state.rules.spawns.add(g);
    }
}
