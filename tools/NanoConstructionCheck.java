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

/** Packaged autonomous construction and conveyor-fed nano industry. */
public final class NanoConstructionCheck{
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
        Class<?> construction=cl.loadClass("cryon.Features.NanoConstruction");
        Vars.logic=new Logic();Vars.logic.reset();
        Vars.world.loadGenerator(240,180,tiles->{
            for(int x=0;x<240;x++)for(int y=0;y<180;y++)tiles.set(x,y,new Tile(x,y,floor,Blocks.air,Blocks.air));
            tiles.get(5,5).setBlock(Blocks.coreShard,Team.sharded);
            for(int[] vein:new int[][]{{35,55},{120,105},{205,55}})
                for(int x=vein[0];x<=vein[0]+18;x+=6)for(int y=vein[1];y<=vein[1]+18;y+=6)tiles.get(x,y).setOverlay(block("nano-ore"));
        });
        Vars.state.set(mindustry.core.GameState.State.playing);Vars.state.rules.canGameOver=false;Vars.state.rules.enemyCoreBuildRadius=0;Time.delta=1;
        terrain.getMethod("rebuild").invoke(null);
        for(int i=0;i<12;i++)colonies.getMethod("step").invoke(null);
        check(find(drill)==null&&find(warehouse)==null,"planner instantly placed completed buildings");
        check(count(weaver)>=2&&weaver.legCount==6,"regional native construction workers not seeded");
        boolean progressing=false,direct=false,reclaimed=false;int frame;
        for(frame=0;frame<65000;frame++){
            tick();if(frame%120==0)colonies.getMethod("step").invoke(null);
            reclaimed|=Groups.build.contains(b->b instanceof mindustry.world.blocks.ConstructBlock.ConstructBuild c&&c.activeDeconstruct);
            progressing|=Groups.build.contains(b->b instanceof mindustry.world.blocks.ConstructBlock.ConstructBuild c&&c.progress>0&&c.progress<1);
            if(frame%6000==0)System.out.println("BUILD_SIM frame="+frame+" "+counts()+" builders="+count(weaver));
            if(frame%600==0&&find(combat)!=null&&find(ultimate)!=null&&nano.data().buildings.count(b->b.block==warehouse)>=3){
                for(Building b:Groups.build)if(b.block==drill&&feeder(b)&&((arc.struct.IntSeq)b.getClass().getField("supplyRoute").get(b)).size>0)direct=true;
                if(direct&&frame>20000)break;
            }
        }
        check(progressing,"native construction never advanced gradually");
        check(nano.data().buildings.count(b->b.block==warehouse)>=3,"remote veins did not create independent bases: "+counts());
        check(direct,"nearby resource deposits did not plan direct belts");
        Building connected=null;
        for(Building b:Groups.build)if(b.block==drill&&feeder(b)){
            var path=(arc.struct.IntSeq)b.getClass().getField("supplyRoute").get(b);
            if(path.size>4&&java.util.Arrays.stream(path.toArray()).allMatch(pos->Vars.world.tile(pos).block()==block("reinforced-vacuum-duct"))){connected=b;break;}
        }
        check(connected!=null,"no completed local belt route");
        var oldPath=new arc.struct.IntSeq((arc.struct.IntSeq)connected.getClass().getField("supplyRoute").get(connected));
        Tile obstacle=Vars.world.tile(oldPath.get(oldPath.size/2));obstacle.setBlock(Blocks.thoriumWall,Team.sharded);
        colonies.getMethod("step").invoke(null);
        var detour=(arc.struct.IntSeq)connected.getClass().getField("supplyRoute").get(connected);
        check(!detour.contains(obstacle.pos())&&detour.size>0,"slime routing did not avoid an obstructed supply path");
        check(obstacle.block()==Blocks.thoriumWall,"routing overwrote another faction's structure");
        Building store=find(warehouse);float hp=store.health;store.damage(1800);
        for(int i=0;i<1800&&store.health<hp;i++)tick();
        check(store.health>hp-900,"workers did not strongly repair warehouse");
        Building belt=nano.data().buildings.find(b->b.block==block("reinforced-vacuum-duct"));check(belt!=null,"no physical supply belts");
        Tile broken=belt.tile;broken.setBlock(Blocks.air);
        colonies.getMethod("step").invoke(null);check(broken.block()==Blocks.air,"damaged belt instantly respawned");
        for(int i=0;i<6000&&broken.block()!=block("reinforced-vacuum-duct");i++)tick();
        check(broken.block()==block("reinforced-vacuum-duct"),"blueprint belt was not rebuilt by workers");
        // Native deconstruction must pass through a real construction block rather than delete the structure.
        Tile scrap=Vars.world.tile(20,145);scrap.setBlock(block("rogue-nano-wall"),nano);
        construction.getMethod("request",Tile.class,Block.class,int.class).invoke(null,scrap,block("rogue-nano-wall"),0);
        Unit nearby=weaver.create(nano);nearby.set(scrap.worldx()-40,scrap.worldy());nearby.add();
        construction.getMethod("withdraw",Tile.class).invoke(null,scrap);
        check(scrap.block()==block("rogue-nano-wall"),"retirement instantly deleted building");
        boolean dismantling=false;
        for(int i=0;i<1800&&scrap.block()!=Blocks.air;i++){tick();dismantling|=scrap.build instanceof mindustry.world.blocks.ConstructBlock.ConstructBuild;}
        check(dismantling&&scrap.block()==Blocks.air,"native self-deconstruction failed");
        // The blueprints outlive the warehouse itself and survive native serialization.
        int sx=store.tileX(),sy=store.tileY();store.tile.setBlock(Blocks.air);
        Fi save=data.child("construction.msav");SaveIO.write(save);SaveIO.load(save);Vars.state.set(mindustry.core.GameState.State.playing);
        check(Vars.world.tile(sx,sy).block()==Blocks.air,"save resurrected completed structure");
        for(int i=0;i<9000&&Vars.world.tile(sx,sy).block()!=warehouse;i++){tick();if(i%120==0)colonies.getMethod("step").invoke(null);}
        check(Vars.world.tile(sx,sy).block()==warehouse,"destroyed warehouse blueprint lost across save/load");
        check(!nano.data().isAlive(),"distributed bases became victory objectives");
        check(!Team.sharded.rules().infiniteResources&&!Vars.state.rules.infiniteResources,"construction changed player resource rules");
        // Isolated paid-work check, including sandbox mode: no mines and no warehouse can subsidize it.
        Vars.logic.reset();
        Vars.world.loadGenerator(64,64,tiles->{for(int x=0;x<64;x++)for(int y=0;y<64;y++)tiles.set(x,y,new Tile(x,y,floor,Blocks.air,Blocks.air));});
        Vars.state.set(mindustry.core.GameState.State.playing);Vars.state.rules.canGameOver=false;Vars.state.rules.infiniteResources=true;Time.delta=1;
        Unit paid=weaver.create(nano);paid.set(25*8,30*8);paid.add();Tile site=Vars.world.tile(30,30);
        construction.getMethod("request",Tile.class,Block.class,int.class).invoke(null,site,block("rogue-nano-wall"),0);
        for(int i=0;i<120;i++)tick();check(site.block()==Blocks.air,"empty worker built without carried material");
        paid.stack.item=material;paid.stack.amount=2;tick();
        check(site.build instanceof mindustry.world.blocks.ConstructBlock.ConstructBuild,"sandbox worker instantly finished construction");
        for(int i=0;i<400&&site.block()!=block("rogue-nano-wall");i++)tick();
        check(site.block()==block("rogue-nano-wall")&&paid.stack.amount==0,"worker did not consume exactly two nano material to build one wall");
        check(Vars.state.rules.infiniteResources&&!nano.rules().infiniteResources,"scoped native builder rules leaked");
        Tile canceled=Vars.world.tile(30,34);paid.stack.item=material;paid.stack.amount=20;
        construction.getMethod("request",Tile.class,Block.class,int.class).invoke(null,canceled,block("nano-machine-factory"),0);
        for(int i=0;i<200&&!(canceled.build instanceof mindustry.world.blocks.ConstructBlock.ConstructBuild);i++)tick();
        check(canceled.build instanceof mindustry.world.blocks.ConstructBlock.ConstructBuild,"cancellation fixture never started");
        construction.getMethod("withdraw",Tile.class).invoke(null,canceled);
        for(int i=0;i<1500&&canceled.block()!=Blocks.air;i++)tick();
        check(canceled.block()==Blocks.air,"worker kept building an obsolete plan instead of dismantling it");
        System.out.println("NANO_CONSTRUCTION_CHECK_PASS: native gradual building, six-legged physical builders, distributed vein bases, local direct belts, strong repair, reconstructing destroyed belts/warehouses, native dismantling, durable blueprints, coreless victory independence");
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
