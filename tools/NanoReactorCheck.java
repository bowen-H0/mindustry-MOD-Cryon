import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.*;
import java.nio.file.Files;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.io.SaveIO;
import mindustry.net.Net;
import mindustry.world.*;

/** Integration checks against the real packaged mod and native world/save APIs. */
public final class NanoReactorCheck{
    static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }
    static Class<?> terrain;
    public static void main(String[] args){
        try{run();System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}
    }
    static void run() throws Exception{
        Core.app=new MockApplication(); Core.files=new MockFiles(); Core.graphics=new MockGraphics(); Core.audio=new MockAudio();
        Core.settings=new Settings(); Fi data=new Fi(Files.createTempDirectory("nano-reactor-check-").toFile());
        Core.settings.setDataDirectory(data); Core.bundle=I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlanuch.jar"));
        Vars.headless=true; Vars.loadLocales=false; Vars.net=new Net(null); Vars.platform=new mindustry.core.Platform(){};
        mindustry.core.Version.init(); Vars.init(); Core.settings.setJson(mindustry.io.JsonIO.json);
        Vars.content.createBaseContent(); Vars.content.createModContent(); Vars.content.init();
        var mod=Vars.mods.locateMod("cryon"); check(mod!=null&&!mod.hasContentErrors(),"mod content errors");
        terrain=mod.main.getClass().getClassLoader().loadClass("cryon.Content.NanoEnvironment");
        terrain.getMethod("install").invoke(null);
        Block reactor=Vars.content.block("cryon-nano-reactor"), floor=Vars.content.block("cryon-nano-floor");
        Block wall=Vars.content.block("cryon-nano-terrain-wall"), ore=Vars.content.block("cryon-nano-ore");
        var nano=Vars.content.item("cryon-nano-material");
        check(reactor!=null&&floor!=null&&wall!=null&&ore.itemDrop==nano,"content registration");
        Vars.logic=new Logic(); Vars.logic.reset();
        Vars.world.loadGenerator(160,160,tiles->{
            for(int x=0;x<160;x++)for(int y=0;y<160;y++)tiles.set(x,y,new Tile(x,y,Blocks.stone,Blocks.air,Blocks.air));
            tiles.get(5,5).setBlock(Blocks.coreShard,Team.sharded);
            tiles.get(40,40).setBlock(reactor,Team.sharded);
            tiles.get(45,40).setBlock(Blocks.stoneWall);
            tiles.get(44,44).setBlock(Blocks.titaniumWall,Team.sharded);
        });
        Vars.state.set(mindustry.core.GameState.State.playing);Time.delta=1f;
        Building b=Vars.world.build(40,40); b.power.status=1f;
        check(b.acceptItem(b,nano)&&b.acceptItem(b,Items.silicon)&&!b.acceptItem(b,Items.copper),"input filter");
        b.items.add(Items.silicon,80);b.items.add(Items.titanium,80);
        tick(b,240);check(b.items.get(nano)==0,"replication without seed");
        b.items.add(nano,1);b.power.status=0;tick(b,240);check(b.items.get(nano)==1,"replication without power");
        b.power.status=1;tick(b,240);check(b.items.get(nano)==2&&b.items.get(Items.silicon)==79&&b.items.get(Items.titanium)==79,"first doubling and cost");
        tick(b,240);check(b.items.get(nano)==4&&b.items.get(Items.silicon)==77&&b.items.get(Items.titanium)==77,"food must scale with existing stock");
        b.items.set(Items.silicon,3);b.items.set(Items.titanium,4);
        tick(b,240);check(b.items.get(nano)==4&&b.items.get(Items.silicon)==3,"short food consumed or replicated");
        b.items.add(Items.silicon,1);tick(b,240);
        check(b.items.get(nano)==8&&b.items.get(Items.silicon)==0&&b.items.get(Items.titanium)==0,"stock food accounting");
        b.enabled=false;tick(b,240);check(b.items.get(nano)==8,"disabled replication");b.enabled=true;
        check(b.removeStack(nano,100)==7&&b.items.get(nano)==1,"seed extracted");
        b.items.set(nano,768);b.enabled=false;tick(b,90);
        check(get(b,"blocked")==90f,"unpowered stock not dangerous");
        Fi save=data.child("nano.msav");SaveIO.write(save);SaveIO.load(save);
        b=Vars.world.build(40,40);check(b.items.get(nano)==768&&get(b,"blocked")==90f,"saved stock or danger lost");
        tick(b,90);check(Vars.world.build(40,40)==null,"blocked reactor did not explode");
        check(Vars.world.tile(40,40).floor()==floor&&Vars.world.tile(45,40).block()==wall,"explosion terrain/wall");
        check(Vars.world.tile(44,44).block()==Blocks.air,"blast did not damage friendly structure");
        int before=count(floor);terrain.getMethod("spreadStep").invoke(null);int after=count(floor);
        check(after>before,"contamination did not spread");
        // Saving terrain is sufficient to reconstruct active edges, including editor-placed ore/walls.
        SaveIO.write(save);SaveIO.load(save);before=count(floor);terrain.getMethod("spreadStep").invoke(null);
        check(count(floor)>before,"spread stopped after load");
        Vars.world.tile(120,120).setBlock(reactor,Team.sharded);
        Building empty=Vars.world.build(120,120);empty.kill();
        check(Vars.world.tile(120,120).floor()==floor,"destroyed empty reactor did not explode");
        Vars.world.tile(100,30).setBlock(reactor,Team.sharded);b=Vars.world.build(100,30);
        b.items.add(nano,600);b.items.add(Items.silicon,600);b.items.add(Items.titanium,600);b.power.status=1;
        tick(b,240);check(Vars.world.build(100,30)==null,"doubling overflow did not explode");
        Vars.world.tile(100,100).setBlock(reactor,Team.sharded);b=Vars.world.build(100,100);
        Vars.world.tile(104,100).setBlock(Blocks.vault,Team.sharded);Building output=Vars.world.build(104,100);
        b.items.add(nano,100);tick(b,60);
        check(output.items.get(nano)==99&&b.items.get(nano)==1,"automatic output/retained seed");
        Vars.world.tile(100,60).setBlock(reactor,Team.sharded);
        Vars.world.tile(106,60).setBlock(reactor,Team.sharded);
        Building first=Vars.world.build(100,60), second=Vars.world.build(106,60);
        first.items.add(nano,600);second.items.add(nano,600);first.kill();
        for(int i=0;i<5;i++)Time.update();
        check(Vars.world.build(106,60)==null&&Vars.world.tile(117,60).floor()==floor,"chain reaction missing");
        containment(floor);
        int infections=count(floor);Vars.state.set(mindustry.core.GameState.State.paused);
        Events.fire(mindustry.game.EventType.Trigger.update);check(count(floor)==infections,"paused spread");
        System.out.println("NANO_REACTOR_CHECK_PASS: content, seed, power, doubling/cost, disable, export, blockage, destruction, overflow, friendly damage, terrain/walls, spread, save/load");
    }
    static void containment(Block floor) throws Exception{
        Block suppressor=Vars.content.block("cryon-nano-suppressor");
        check(suppressor!=null&&suppressor.consPower.usage==30f,"suppressor content/power");
        Vars.world.loadGenerator(100,80,tiles->{
            for(int x=0;x<100;x++)for(int y=0;y<80;y++)tiles.set(x,y,new Tile(x,y,Blocks.stone,Blocks.air,Blocks.air));
            tiles.get(5,5).setBlock(Blocks.coreShard,Team.sharded);
            tiles.get(30,35).setBlock(suppressor,Team.sharded);
        });
        Vars.state.set(mindustry.core.GameState.State.playing);
        Building b=Vars.world.build(30,35);b.power.status=1f;
        terrain.getMethod("infect",Tile.class).invoke(null,Vars.world.tile(30,30));
        terrain.getMethod("spreadStep").invoke(null);
        check(count(floor)==1,"powered suppressor failed to contain source");
        Fi save=Vars.dataDirectory.child("containment.msav");SaveIO.write(save);SaveIO.load(save);
        b=Vars.world.build(30,35);b.power.status=1f;
        terrain.getMethod("spreadStep").invoke(null);check(count(floor)==1,"containment failed after save/load");
        b.enabled=false;terrain.getMethod("spreadStep").invoke(null);check(count(floor)>1,"disabled suppression still active");
        b.enabled=true;int held=count(floor);terrain.getMethod("spreadStep").invoke(null);check(count(floor)==held,"restored power did not contain");
        b.power.status=0;terrain.getMethod("spreadStep").invoke(null);check(count(floor)>held,"blackout did not resume spread");
        b.power.status=1;
        // A source just outside the radius cannot infect a tile inside it.
        terrain.getMethod("infect",Tile.class).invoke(null,Vars.world.tile(51,35));
        terrain.getMethod("spreadStep").invoke(null);
        check(Vars.world.tile(50,35).floor()!=floor&&Vars.world.tile(52,35).floor()==floor,"field boundary or outside spread incorrect");
        // Overlapping fields remain effective when one device goes offline.
        Vars.world.tile(35,35).setBlock(suppressor,Team.sharded);Building second=Vars.world.build(35,35);second.power.status=1;
        b.enabled=false;held=countNear(floor,30,30,5);terrain.getMethod("spreadStep").invoke(null);
        check(countNear(floor,30,30,5)==held,"overlap failed");
        second.kill();terrain.getMethod("spreadStep").invoke(null);
        check(countNear(floor,30,30,5)>held,"destroyed suppressor still active");
        System.out.println("NANO_SUPPRESSOR_CHECK_PASS: power, source and destination coverage, save/load, disable, blackout, restore, outside growth, overlapping fields, destruction");
    }
    static int countNear(Block floor,int x,int y,int r){int n=0;for(int dx=-r;dx<=r;dx++)for(int dy=-r;dy<=r;dy++)if(Vars.world.tile(x+dx,y+dy).floor()==floor)n++;return n;}
    static void tick(Building b,int ticks){for(int i=0;i<ticks&&b.isValid();i++){b.updateTile();Time.update();}}
    static float get(Building b,String name)throws Exception{return b.getClass().getField(name).getFloat(b);}
    static int count(Block floor){int n=0;for(Tile t:Vars.world.tiles)if(t.floor()==floor)n++;return n;}
}
