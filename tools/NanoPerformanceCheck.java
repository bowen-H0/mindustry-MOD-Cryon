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

/** Large-colony microbenchmarks plus spatial-cache invalidation and save/load checks. */
public final class NanoPerformanceCheck{
    static Class<?> colonies,terrain;static Block drill,floor,warehouse,loader,unload,large,combat,ultimate,cleaver,unloader;
    static UnitType carrier,breaker,annihilator,weaver;static Team nano;static Item material;
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
        check(carrier.hidden&&carrier.hideDatabase,"retired carrier leaked into active content");
        check(breaker.health==16000&&breaker.armor==28&&annihilator.health==120000&&annihilator.armor==70,"specialist strength");
        weaver=Vars.content.unit("cryon-nano-weaver");
        Class<?> construction=cl.loadClass("cryon.Features.NanoConstruction"),industry=cl.loadClass("cryon.Features.NanoIndustry");
        Vars.logic=new Logic();Vars.logic.reset();
        Vars.world.loadGenerator(240,180,tiles->{for(int x=0;x<240;x++)for(int y=0;y<180;y++)tiles.set(x,y,new Tile(x,y,floor,Blocks.air,Blocks.air));});
        Vars.state.set(mindustry.core.GameState.State.playing);Vars.state.rules.canGameOver=false;Time.delta=1;
        Block wall=block("rogue-nano-wall");var request=construction.getMethod("request",Tile.class,Block.class,int.class);
        for(int x=10;x<110;x++)for(int y=10;y<50;y++){
            Tile t=Vars.world.tile(x,y);t.setBlock(wall,nano);request.invoke(null,t,wall,0);
        }
        var reserved=construction.getMethod("reserved",Tile.class);var sites=construction.getMethod("sites",Block.class);
        Tile outside=Vars.world.tile(230,160),inside=Vars.world.tile(10,10);
        check((boolean)reserved.invoke(null,inside)&&!(boolean)reserved.invoke(null,outside),"spatial reservation correctness");
        for(int i=0;i<24;i++){Unit u=weaver.create(nano);u.set((130+i)*8,100*8);u.add();}
        for(int warm=0;warm<3;warm++){for(int i=0;i<1000;i++)reserved.invoke(null,outside);for(int i=0;i<50;i++)sites.invoke(null,warehouse);}
        var blueprintsField=construction.getDeclaredField("blueprints");blueprintsField.setAccessible(true);
        @SuppressWarnings("unchecked") var plans=(arc.struct.IntMap<mindustry.entities.units.BuildPlan>)blueprintsField.get(null);
        long begin=System.nanoTime();for(int i=0;i<20000;i++)linearReserved(plans,outside);long linear=System.nanoTime()-begin;
        begin=System.nanoTime();for(int i=0;i<20000;i++)reserved.invoke(null,outside);long reservation=System.nanoTime()-begin;
        begin=System.nanoTime();for(int i=0;i<4000;i++)sites.invoke(null,warehouse);long siteTime=System.nanoTime()-begin;
        begin=System.nanoTime();for(int frame=0;frame<600;frame++){for(Unit u:Groups.unit)u.update();Time.update();Vars.state.tick++;}long idle=System.nanoTime()-begin;
        construction.getMethod("withdraw",Tile.class).invoke(null,inside);check(!(boolean)reserved.invoke(null,inside),"retired reservation cache stale");
        check(((arc.struct.Seq<?>)sites.invoke(null,warehouse)).isEmpty(),"incorrect warehouse sites");
        construction.getMethod("request",Tile.class,Block.class,int.class).invoke(null,outside,warehouse,0);
        check((boolean)reserved.invoke(null,Vars.world.tile(228,158)),"multiblock footprint missing");
        check(((arc.struct.Seq<?>)sites.invoke(null,warehouse)).size==1,"new warehouse site cache stale");
        Fi save=data.child("performance.msav");SaveIO.write(save);SaveIO.load(save);
        check((boolean)reserved.invoke(null,Vars.world.tile(228,158))&&!(boolean)reserved.invoke(null,inside),"spatial cache lost after save/load");
        // A burst of nearby mines must be served over several planning pulses, never all at once.
        Vars.logic.reset();
        Vars.world.loadGenerator(160,100,tiles->{
            for(int x=0;x<160;x++)for(int y=0;y<100;y++)tiles.set(x,y,new Tile(x,y,floor,Blocks.air,Blocks.air));
            tiles.get(80,50).setBlock(warehouse,nano);
            for(int i=0;i<16;i++){tiles.get(50+i,35).setOverlay(block("nano-ore"));tiles.get(50+i,35).setBlock(drill,nano);tiles.get(50+i,35).build.configureAny(Boolean.TRUE);}
        });
        Vars.state.set(mindustry.core.GameState.State.playing);Vars.state.rules.canGameOver=false;Time.delta=1;
        arc.struct.Seq<Building> mines=new arc.struct.Seq<>();for(Building b:nano.data().buildings)if(b.block==drill)mines.add(b);
        var step=industry.getMethod("step",arc.struct.Seq.class);var searches=industry.getDeclaredField("routeSearches");searches.setAccessible(true);
        int peakSearches=0;
        for(int i=0;i<24;i++){
            construction.getMethod("step").invoke(null);step.invoke(null,mines);
            int count=searches.getInt(null);check(count<=4,"route planning exceeded per-pulse search budget");peakSearches=Math.max(peakSearches,count);
            Time.time+=120f;Vars.state.tick+=120;
        }
        check(peakSearches==4,"route-budget fixture did not saturate the search budget");
        var retries=industry.getDeclaredField("routeRetry");retries.setAccessible(true);
        var retryMap=(arc.struct.IntFloatMap)retries.get(null);
        int attempted=mines.count(b->{try{return retryMap.containsKey(b.pos())||((arc.struct.IntSeq)b.getClass().getField("supplyRoute").get(b)).size>0;}catch(Exception e){throw new RuntimeException(e);}});
        check(attempted==16,"deferred mines never received their search turn: "+attempted);
        check(((arc.struct.IntSeq)Vars.world.build(80,50).getClass().getField("supplyRoute").get(Vars.world.build(80,50))).size>0,"diagonal mine was not connected to warehouse");
        System.out.printf("NANO_PERFORMANCE_CHECK_PASS: 4000 structures, 24 idle builders, linear_reservation_ms=%.3f cached_reservation_ms=%.3f site_queries_ms=%.3f idle_600_frames_ms=%.3f%n",linear/1e6,reservation/1e6,siteTime/1e6,idle/1e6);
    }
    static boolean linearReserved(arc.struct.IntMap<mindustry.entities.units.BuildPlan> plans,Tile t){
        for(var p:plans.values())if(!p.breaking){int off=-(p.block.size-1)/2,dx=t.x-p.x,dy=t.y-p.y;if(dx>=off&&dx<off+p.block.size&&dy>=off&&dy<off+p.block.size)return true;}
        return false;
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
        for(Unit u:Groups.unit.copy())if(!u.dead&&(u.type==carrier||u.type==weaver))u.update();
        Groups.bullet.update();Groups.bullet.collide();Time.update();Vars.state.tick++;
    }
}
