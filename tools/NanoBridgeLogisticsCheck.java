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

/** Real timed bridge construction, cross-river mining transport, exclusive ownership and old-save retirement. */
public final class NanoBridgeLogisticsCheck{
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
        check(carrier!=null&&carrier.flying&&carrier.weapons.isEmpty()&&carrier.itemCapacity==120,"carrier must be unarmed flying worker");
        check(breaker.health==16000&&breaker.armor==28&&annihilator.health==120000&&annihilator.armor==70,"specialist strength");
        weaver=Vars.content.unit("cryon-nano-weaver");
        Class<?> construction=cl.loadClass("cryon.Features.NanoConstruction");
        Block bridge=block("nano-bridge"),pipe=block("reinforced-vacuum-duct");
        check(bridge instanceof mindustry.world.blocks.distribution.ItemBridge,"bridge must use native ItemBridge");
        var nativeBridge=(mindustry.world.blocks.distribution.ItemBridge)bridge;
        check(nativeBridge.range==12&&!bridge.hasPower,"bridge range/power rules");
        for(String name:new String[]{"rogue-nano-drill","rogue-nano-wall","nano-machine-factory","nano-large-factory","nano-combat-factory","nano-ultimate-factory","nano-warehouse","rogue-nano-unloader","nano-cleaver","nano-bridge"}){
            Block exclusive=block(name);Vars.state.rules.infiniteResources=true;
            check(exclusive.isHidden(),"player sandbox exposes exclusive building: "+name);
            check(!exclusive.canPlaceOn(null,Team.sharded,0),"player can manufacture exclusive building: "+name);
        }
        Vars.state.rules.infiniteResources=false;
        Vars.logic=new Logic();Vars.logic.reset();
        Vars.world.loadGenerator(160,110,tiles->{
            for(int x=0;x<160;x++)for(int y=0;y<110;y++)tiles.set(x,y,new Tile(x,y,x>=50&&x<=56?Blocks.deepwater:floor,Blocks.air,Blocks.air));
            tiles.get(5,5).setBlock(Blocks.coreShard,Team.sharded);
            tiles.get(85,55).setBlock(warehouse,nano);
            tiles.get(30,55).setOverlay(block("nano-ore"));tiles.get(30,55).setBlock(drill,nano);tiles.get(30,55).build.configureAny(Boolean.TRUE);
        });
        Vars.state.set(mindustry.core.GameState.State.playing);Vars.state.rules.canGameOver=false;Vars.state.rules.enemyCoreBuildRadius=0;Time.delta=1;
        for(int[] position:new int[][]{{27,57},{80,57}}){Unit worker=weaver.create(nano);worker.stack.item=material;worker.stack.amount=120;worker.set(position[0]*8,position[1]*8);worker.add();}
        Building mine=Vars.world.build(30,55);boolean gradual=false,delivered=false;
        for(int frame=0;frame<30000;frame++){
            tick();if(frame%120==0)colonies.getMethod("step").invoke(null);
            gradual|=Groups.build.contains(b->b instanceof mindustry.world.blocks.ConstructBlock.ConstructBuild c&&c.current==bridge&&c.progress>0&&c.progress<1);
            delivered|=Vars.world.build(85,55).items.get(material)>0;
            if(frame%6000==0)System.out.println("BRIDGE_SIM frame="+frame+" bridges="+Groups.build.count(b->b.block==bridge)+" warehouse="+Vars.world.build(85,55).items.get(material));
            if(delivered&&gradual&&frame>3000)break;
        }
        check(gradual,"bridge did not pass through native timed construction");check(delivered,"real mined material never crossed river into warehouse");
        var path=(arc.struct.IntSeq)mine.getClass().getField("supplyRoute").get(mine);check(path.size>0,"feeder has no saved pipe/bridge route");
        mindustry.world.blocks.distribution.ItemBridge.ItemBridgeBuild crossing=null;
        for(Building b:Groups.build)if(b.block==bridge){
            var endpoint=(mindustry.world.blocks.distribution.ItemBridge.ItemBridgeBuild)b;Tile other=Vars.world.tile(endpoint.link);
            if(other!=null&&b.tileX()<50&&other.x>56){crossing=endpoint;break;}
        }
        check(crossing!=null&&nativeBridge.linkValid(crossing.tile,Vars.world.tile(crossing.link)),"cross-river bridge endpoints not correctly configured");
        check(Vars.world.tile(53,55).floor()==Blocks.deepwater&&Vars.world.tile(53,55).block()==Blocks.air,"bridge replaced water with ground or teleport blocks");
        int bx=crossing.tileX(),by=crossing.tileY(),link=crossing.link;crossing.tile.setBlock(Blocks.air);
        Fi save=data.child("bridge.msav");SaveIO.write(save);SaveIO.load(save);Vars.state.set(mindustry.core.GameState.State.playing);
        check(Vars.world.tile(bx,by).block()==Blocks.air,"load instantly restored broken bridge");
        for(int i=0;i<15000&&Vars.world.tile(bx,by).block()!=bridge;i++){tick();if(i%120==0)colonies.getMethod("step").invoke(null);}
        check(Vars.world.tile(bx,by).block()==bridge,"bridge blueprint not rebuilt after save/load");
        var restored=(mindustry.world.blocks.distribution.ItemBridge.ItemBridgeBuild)Vars.world.build(bx,by);
        check(restored.link==link,"native bridge link/config lost through blueprint save/load");
        restored.items.add(material,8);boolean moved=false;for(int i=0;i<120;i++){tick();moved|=restored.moved;}
        check(moved,"restored native bridge did not resume real item transport");
        check(Groups.unit.count(u->u.type==carrier)==0&&!Groups.build.contains(b->b.block==loader||b.block==unload),"retired air logistics reappeared in colony");
        check(!nano.data().isAlive(),"bridge network became a capture objective");
        // The retained large factory now produces construction organisms, never air freight.
        check(large.getClass().getField("outputUnit").get(large)==weaver,"large factory still produces retired carriers");
        Vars.logic.reset();
        Vars.world.loadGenerator(80,60,tiles->{
            for(int x=0;x<80;x++)for(int y=0;y<60;y++)tiles.set(x,y,new Tile(x,y,floor,Blocks.air,Blocks.air));
            tiles.get(15,20).setBlock(large,nano,0);tiles.get(45,20).setBlock(large,Team.sharded,0);
        });
        Vars.state.set(mindustry.core.GameState.State.playing);Vars.state.rules.canGameOver=false;Time.delta=1;
        Vars.world.build(15,20).items.set(material,30);Vars.world.build(45,20).items.set(material,30);
        for(int i=0;i<700;i++)tick();
        check(Groups.unit.count(u->u.type==weaver&&u.team==nano)==1&&Vars.world.build(15,20).items.get(material)==0,"large factory did not produce one paid construction organism");
        check(Groups.unit.count(u->u.team==Team.sharded)==0&&Vars.world.build(45,20).items.get(material)==30,"player-owned exclusive factory produced organisms");
        // Old definitions exist only to read old saves. Retire both ports and carrier cargo without loss.
        Vars.logic.reset();
        Vars.world.loadGenerator(44,44,tiles->{
            for(int x=0;x<44;x++)for(int y=0;y<44;y++)tiles.set(x,y,new Tile(x,y,Blocks.stone,Blocks.air,Blocks.air));
            tiles.get(5,5).setBlock(Blocks.coreShard,Team.sharded);tiles.get(28,28).setBlock(warehouse,nano);
        });
        Vars.state.set(mindustry.core.GameState.State.playing);Vars.state.rules.canGameOver=false;
        Vars.world.tile(12,10).setBlock(loader,nano);Vars.world.tile(14,10).setBlock(unload,nano);Vars.world.tile(10,20).setBlock(loader,Team.sharded);Vars.world.tile(10,30).setBlock(loader,Team.green);
        Vars.world.build(12,10).items.set(material,40);Vars.world.build(14,10).items.set(material,60);Vars.world.build(10,20).items.set(material,13);Vars.world.build(10,30).items.set(material,17);
        Unit legacy=carrier.spawn(nano,20*8,15*8);legacy.stack.item=material;legacy.stack.amount=20;
        Unit player=carrier.spawn(Team.sharded,20*8,20*8);player.stack.item=material;player.stack.amount=7;
        construction.getMethod("request",Tile.class,Block.class,int.class).invoke(null,Vars.world.tile(12,10),loader,0);
        Fi old=data.child("retired.msav");SaveIO.write(old);SaveIO.load(old);
        check(Groups.unit.count(u->u.type==carrier)==0,"old carrier survived retirement");
        check(Vars.world.tile(12,10).block()==pipe&&Vars.world.tile(14,10).block()==pipe&&Vars.world.tile(10,20).block()==pipe,"old ports were not converted to pipe");
        check(Vars.world.build(28,28).items.get(material)==120&&Vars.world.build(5,5).items.get(material)==20,"old cargo was lost or duplicated: warehouse="+Vars.world.build(28,28).items.get(material)+" core="+Vars.world.build(5,5).items.get(material));
        Fi reserve=data.child("reserved-cargo.msav");SaveIO.write(reserve);SaveIO.load(reserve);
        Vars.world.tile(36,34).setBlock(Blocks.coreShard,Team.green);
        cl.loadClass("cryon.Features.RetiredNanoLogistics").getMethod("migrate").invoke(null);
        check(Vars.world.build(36,34).items.get(material)==17,"cargo without storage was not preserved across save/load");
        colonies.getMethod("step").invoke(null);check(Vars.world.tile(12,10).block()==pipe,"old port blueprint was rebuilt");
        System.out.println("NANO_BRIDGE_LOGISTICS_CHECK_PASS: native item bridge, real timed endpoint construction, mined cargo across deep river, persistent links and reconstruction, exclusive faction placement, no air freight, captured-sector independence, lossless old-save retirement and deferred cargo recovery");
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
        for(Unit u:Groups.unit.copy())if(!u.dead&&(u.type==weaver))u.update();
        Groups.bullet.update();Groups.bullet.collide();Time.update();Vars.state.tick++;
    }
}
