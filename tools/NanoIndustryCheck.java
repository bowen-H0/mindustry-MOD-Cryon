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
import mindustry.type.*;
import mindustry.world.*;

/** Full packaged mine -> belt -> port -> carrier -> warehouse -> unloader -> military economy. */
public final class NanoIndustryCheck{
    static Class<?> colonies,terrain;static Block drill,floor,warehouse,loader,unload,large,combat,ultimate,cleaver,unloader;
    static UnitType carrier,breaker,annihilator;static Team nano;static Item material;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[]args){try{run();System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
    static void run()throws Exception{
        Core.app=new MockApplication();Core.files=new MockFiles();Core.graphics=new MockGraphics();Core.audio=new MockAudio();
        Core.settings=new Settings();Fi data=new Fi(Files.createTempDirectory("nano-industry-check-").toFile());
        Core.settings.setDataDirectory(data);Core.bundle=I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlanuch.jar"));
        Vars.headless=true;Vars.loadLocales=false;Vars.net=new Net(null);Vars.platform=new mindustry.core.Platform(){};
        mindustry.core.Version.init();Vars.init();Core.settings.setJson(mindustry.io.JsonIO.json);
        Vars.content.createBaseContent();Vars.content.createModContent();Vars.content.init();
        var mod=Vars.mods.locateMod("cryon");check(mod!=null&&!mod.hasContentErrors(),"packaged content errors");
        var cl=mod.main.getClass().getClassLoader();colonies=cl.loadClass("cryon.Features.NanoColonies");terrain=cl.loadClass("cryon.Content.NanoEnvironment");terrain.getMethod("install").invoke(null);
        drill=block("rogue-nano-drill");floor=block("nano-floor");warehouse=block("nano-warehouse");loader=block("grey-goo-loader");unload=block("grey-goo-unload-point");
        large=block("nano-large-factory");combat=block("nano-combat-factory");ultimate=block("nano-ultimate-factory");cleaver=block("nano-cleaver");unloader=block("rogue-nano-unloader");
        carrier=Vars.content.unit("cryon-grey-goo-carrier");breaker=Vars.content.unit("cryon-nano-armor-breaker");annihilator=Vars.content.unit("cryon-nano-annihilator");nano=Team.get(172);material=Vars.content.item("cryon-nano-material");
        check(carrier!=null&&carrier.flying&&carrier.weapons.isEmpty()&&carrier.itemCapacity==120,"carrier must be unarmed flying worker");
        check(breaker.health==16000&&breaker.armor==28&&annihilator.health==120000&&annihilator.armor==70,"specialist strength");
        Vars.logic=new Logic();Vars.logic.reset();
        Vars.world.loadGenerator(160,160,tiles->{
            for(int x=0;x<160;x++)for(int y=0;y<160;y++)tiles.set(x,y,new Tile(x,y,floor,Blocks.air,Blocks.air));
            tiles.get(5,5).setBlock(Blocks.coreShard,Team.sharded);
            tiles.get(70,70).setOverlay(block("nano-ore"));tiles.get(70,70).setBlock(drill,nano);
            for(int x=108;x<=144;x+=6)for(int y=90;y<=126;y+=6)tiles.get(x,y).setOverlay(block("nano-ore"));
            for(int x=8;x<=12;x++)for(int y=118;y<=122;y++)tiles.get(x,y).setFloor(Blocks.stone.asFloor());
        });
        Vars.state.set(mindustry.core.GameState.State.playing);Vars.state.rules.canGameOver=false;Time.delta=1;
        terrain.getMethod("rebuild").invoke(null);
        for(int i=0;i<140;i++)colonies.getMethod("step").invoke(null);
        Building store=find(warehouse),works=find(large),battle=find(combat),finalWorks=find(ultimate),gun=find(cleaver);
        check(store!=null&&works!=null&&battle!=null&&finalWorks!=null&&gun!=null,"district not constructed: "+counts());
        check(find(unload)!=null&&find(unloader)!=null&&Groups.build.count(b->b.block==loader)>=10,"air freight feeder network missing");
        check(!(store instanceof mindustry.world.blocks.storage.CoreBlock.CoreBuild)&&!nano.data().isAlive(),"warehouse became a victory core");
        for(Building b:new Building[]{works,battle,finalWorks})check((boolean)b.getClass().getMethod("frontClear").invoke(b),"factory exit obstructed: "+b.block.name);
        int routeLength=((arc.struct.IntSeq)store.getClass().getField("supplyRoute").get(store)).size;
        check(routeLength>0&&store.items.total()==0&&works.items.total()==0,"bootstrap plan missing or free materials conjured");
        boolean flewCargo=false,delivered=false,warehouseFed=false;
        for(int frame=0;frame<36000;frame++){
            tick();
            if(frame%120==0)colonies.getMethod("step").invoke(null);
            warehouseFed|=store.items.get(material)>0;
            flewCargo|=Groups.unit.contains(u->u.type==carrier&&u.hasItem());
            delivered|=Groups.build.contains(b->b.block==unload&&b.items.get(material)>0);
            if(frame%6000==0)System.out.println("INDUSTRY_SIM frame="+frame+" "+counts()+" warehouse="+store.items.get(material)+" combat="+battle.items.get(material)+" ultimate="+finalWorks.items.get(material)+" progress="+finalWorks.getClass().getField("progress").getFloat(finalWorks)+" clear="+finalWorks.getClass().getMethod("frontClear").invoke(finalWorks));
            if(count(carrier)>0&&count(breaker)>0&&count(annihilator)>0&&flewCargo&&delivered)break;
        }
        check(warehouseFed&&count(carrier)>0,"bootstrap belt failed to fund large factory");
        check(flewCargo&&delivered,"carrier did not physically fly real items to unload point");
        check(count(breaker)>0&&count(annihilator)>0,"military factories not supplied/producing: "+counts()+" ultimateStock="+finalWorks.items.get(material));
        check(gun.items.get(material)>0,"warehouse/belt did not supply cleaver ammunition");
        Unit worker=Groups.unit.find(u->u.type==carrier);worker.set(10*8,120*8);
        for(int i=0;i<120;i++){worker.update();Time.update();}
        check(Vars.world.tile(10,120).floor()==Blocks.stone,"carrier spread nano contamination");
        // Real beam target: production chain supplies ammunition and native beam damages it.
        int ex=gun.tile.x-24,ey=gun.tile.y;Vars.world.tile(ex,ey).setBlock(Blocks.thoriumWallLarge,Team.crux);Building enemy=Vars.world.build(ex,ey);float hp=enemy.health;
        for(int i=0;i<600&&enemy.isValid()&&enemy.health==hp;i++)tick();
        check(enemy.health<hp,"cleaver never fired/damaged hostile target");
        Building extractor=Vars.world.build(store.tile.x-3,store.tile.y-2);extractor.enabled=false;
        // Also stop the final adjacent belt so ammunition exhaustion is observable.
        for(int x=store.tile.x-4;x<=store.tile.x-3;x++)for(int y=store.tile.y-5;y<=store.tile.y-2;y++){
            Building b=Vars.world.build(x,y);if(b!=null&&b.block!=warehouse&&b.items!=null)b.items.clear();
        }
        gun.items.clear();gun.getClass().getField("paidTime").setFloat(gun,0);gun.update();
        check(!(boolean)gun.getClass().getMethod("hasAmmo").invoke(gun)&&((arc.struct.Seq<?>)gun.getClass().getField("bullets").get(gun)).isEmpty(),"cleaver fired without paid material");
        // Preserve carried material and bootstrap/feeder plans through native serialization.
        int cargo=carried(),stock=store.items.get(material),workers=count(carrier),breakers=count(breaker),ultimateCount=count(annihilator);
        Fi save=data.child("industry.msav");SaveIO.write(save);SaveIO.load(save);
        store=Vars.world.build(store.tile.x,store.tile.y);
        check(store.items.get(material)==stock&&carried()==cargo&&count(carrier)==workers&&count(breaker)==breakers&&count(annihilator)==ultimateCount,"save lost inventory or units");
        check(((arc.struct.IntSeq)store.getClass().getField("supplyRoute").get(store)).size==routeLength,"save lost bootstrap route");
        check(Groups.build.contains(b->b.block==drill&&feeder(b)),"save lost feeder drill roles");
        Vars.state.rules.sector=Planets.serpulo.sectors.first();Vars.state.rules.waves=false;Vars.state.rules.attackMode=false;
        for(Unit u:Groups.unit.copy())if(u.type==carrier||u.type==breaker||u.type==annihilator){u.update();check(!u.dead,"captured sector killed specialist/worker");}
        check(!nano.data().isAlive(),"warehouse changed disaster victory policy");Vars.state.rules.sector=null;
        // A damaged transport belt is rebuilt without replacing other structures.
        Tile broken=Vars.world.tile(((arc.struct.IntSeq)store.getClass().getField("supplyRoute").get(store)).get(0));broken.setBlock(Blocks.air);
        colonies.getMethod("step").invoke(null);check(broken.block()==block("reinforced-vacuum-duct"),"bootstrap belt not repaired");
        var allies=cl.loadClass("cryon.Features.MapAlliances");allies.getMethod("install").invoke(null);Vars.state.rules.tags.put("cryon-allied-teams","4");
        Vars.world.tile(20,20).setBlock(warehouse,nano);Vars.world.tile(27,20).setBlock(combat,nano);Vars.world.tile(34,20).setBlock(cleaver,nano);
        check(carrier.create(nano)!=null&&breaker.create(nano)!=null&&annihilator.create(nano)!=null,"alliance wrapper construction");
        Building legacy=Groups.build.find(b->b.block==drill);
        legacy.getClass().getField("feeder").setBoolean(legacy,true);
        var bytes=new java.io.ByteArrayOutputStream();legacy.write(arc.util.io.Writes.get(new java.io.DataOutputStream(bytes)));
        byte[] oldBody=java.util.Arrays.copyOf(bytes.toByteArray(),bytes.size()-1);
        legacy.read(arc.util.io.Reads.get(new java.io.DataInputStream(new java.io.ByteArrayInputStream(oldBody))),(byte)1);
        check(!feeder(legacy),"legacy drill save compatibility");
        System.out.println("NANO_INDUSTRY_CHECK_PASS: clustered autonomous district, bootstrap mining/belts, feeder loaders, real flying cargo, warehouse storage/victory exclusion, directional factory/ammo supply, all three factory products, unarmed/nonspreading worker, paid beam damage/starvation, factory clearance, save/load, capture survival, repair and alliances");
    }
    static Block block(String name){return Vars.content.block("cryon-"+name);}
    static Building find(Block block){for(Building b:nano.data().buildings)if(b.block==block&&b.isValid())return b;return null;}
    static int count(UnitType type){return Groups.unit.count(u->u.type==type&&!u.dead);}
    static int carried(){int n=0;for(Unit u:Groups.unit)if(u.type==carrier)n+=u.stack.amount;return n;}
    static boolean feeder(Building b){try{return b.getClass().getField("feeder").getBoolean(b);}catch(Exception e){throw new RuntimeException(e);}}
    static String counts(){return "drills="+Groups.build.count(b->b.block==drill)+", loaders="+Groups.build.count(b->b.block==loader)+", warehouses="+nano.data().buildings.count(b->b.block==warehouse)+", workers="+count(carrier)+", breakers="+count(breaker)+", annihilators="+count(annihilator);}
    static void tick(){
        Vars.state.teams.updateTeamStats();Groups.unit.updatePhysics();Groups.bullet.updatePhysics();
        for(Building b:Groups.build.copy())if(b.isValid())b.update();
        for(Unit u:Groups.unit.copy())if(!u.dead&&u.type==carrier)u.update();
        Groups.bullet.update();Groups.bullet.collide();Time.update();Vars.state.tick++;
    }
}
