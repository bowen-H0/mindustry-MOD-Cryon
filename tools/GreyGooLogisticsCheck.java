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
import mindustry.type.*;
import mindustry.world.*;

/** Player-owned freight, including factory startup, real flight, capacity stalls and load recovery. */
public final class GreyGooLogisticsCheck{
    static Block loader,unload,warehouse,factory,belt;static Item nano;static UnitType carrier;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[]args){try{run();System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
    static void run()throws Exception{
        Core.app=new MockApplication();Core.files=new MockFiles();Core.graphics=new MockGraphics();Core.audio=new MockAudio();Core.settings=new Settings();
        Fi data=new Fi(Files.createTempDirectory("grey-goo-player-check-").toFile());Core.settings.setDataDirectory(data);Core.bundle=I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlanuch.jar"));
        Vars.headless=true;Vars.loadLocales=false;Vars.net=new Net(null);Vars.platform=new mindustry.core.Platform(){};
        mindustry.core.Version.init();Vars.init();Core.settings.setJson(mindustry.io.JsonIO.json);Vars.content.createBaseContent();Vars.content.createModContent();Vars.content.init();
        var mod=Vars.mods.locateMod("cryon");check(mod!=null&&!mod.hasContentErrors(),"packaged content errors");
        loader=block("grey-goo-loader");unload=block("grey-goo-unload-point");warehouse=block("nano-warehouse");factory=block("nano-large-factory");belt=block("reinforced-vacuum-duct");
        nano=Vars.content.item("cryon-nano-material");carrier=Vars.content.unit("cryon-grey-goo-carrier");
        Vars.logic=new Logic();Vars.logic.reset();
        Vars.world.loadGenerator(80,60,tiles->{
            for(int x=0;x<80;x++)for(int y=0;y<60;y++)tiles.set(x,y,new Tile(x,y,Blocks.stone,Blocks.air,Blocks.air));
            tiles.get(5,5).setBlock(Blocks.coreShard,Team.sharded);
            tiles.get(10,20).setBlock(belt,Team.sharded,0);tiles.get(11,20).setBlock(belt,Team.sharded,0);tiles.get(12,20).setBlock(loader,Team.sharded);
            tiles.get(12,30).setBlock(factory,Team.sharded,0);
            tiles.get(38,20).setBlock(unload,Team.sharded);tiles.get(42,20).setBlock(warehouse,Team.sharded);
            tiles.get(20,15).setBlock(unload,Team.green);
        });Vars.state.set(mindustry.core.GameState.State.playing);Vars.state.rules.waves=false;Time.delta=1;
        Building origin=Vars.world.build(12,20),works=Vars.world.build(12,30),receiver=Vars.world.build(38,20),store=Vars.world.build(42,20),foreign=Vars.world.build(20,15);
        check(origin.acceptItem(Vars.world.build(11,20),nano),"player-owned loading point rejected friendly input");
        check(!origin.acceptItem(foreign,nano)&&!origin.acceptItem(origin,Items.copper),"loading point accepted wrong owner/item");
        check(works.acceptItem(origin,nano)&&store.acceptItem(receiver,nano),"player factory/warehouse refused freight materials");
        origin.items.add(nano,239);works.items.add(nano,30);Vars.world.build(10,20).handleItem(origin,nano);
        boolean held=false,saved=false;
        for(int frame=0;frame<3600;frame++){
            tick();Unit worker=Groups.unit.find(u->u.type==carrier&&u.team==Team.sharded);
            if(!saved&&worker!=null&&worker.hasItem()){
                check(worker.controller().getClass().getName().contains("GreyGooCarrierAI"),"worker controller lost");
                int cargo=worker.stack.amount;int oldStore=store.items.get(nano),oldBuffer=receiver.items.get(nano);
                // Both receiver and adjacent storage full: loaded workers must keep their cargo.
                store.items.set(nano,warehouse.itemCapacity);receiver.items.set(nano,unload.itemCapacity);
                for(int i=0;i<120;i++)tick();check(worker.stack.amount==cargo,"full destination discarded cargo");held=true;
                store.items.set(nano,oldStore);receiver.items.set(nano,oldBuffer);
                Fi save=data.child("cargo.msav");SaveIO.write(save);SaveIO.load(save);
                origin=Vars.world.build(12,20);works=Vars.world.build(12,30);receiver=Vars.world.build(38,20);store=Vars.world.build(42,20);foreign=Vars.world.build(20,15);
                worker=Groups.unit.find(u->u.type==carrier&&u.team==Team.sharded);
                check(worker!=null&&worker.stack.amount==cargo&&worker.controller().getClass().getName().contains("GreyGooCarrierAI"),"save lost cargo/controller");saved=true;
            }
            if(store.items.get(nano)==240)break;
        }
        check(held&&saved&&store.items.get(nano)==240,"player freight failed after save/capacity recovery: delivered="+store.items.get(nano));
        check(foreign.items.total()==0&&origin.items.total()==0,"freight crossed teams or left input stranded");
        int remaining=store.items.get(nano)+receiver.items.get(nano)+origin.items.get(nano)+works.items.get(nano);
        for(Unit u:Groups.unit)if(u.type==carrier)remaining+=u.stack.amount;
        check(remaining==240,"material duplicated/lost during transport");
        check(!carrier.aiController.get().getClass().getName().contains("FlyingAI"),"fallback controller is not freight AI");
        System.out.println("GREY_GOO_LOGISTICS_CHECK_PASS: player-owned ports, belt input, real factory worker production and flight, warehouse delivery, team/item filters, full-buffer cargo retention, save/load controller and cargo recovery, exact material conservation");
    }
    static Block block(String name){return Vars.content.block("cryon-"+name);}
    static void tick(){Vars.state.teams.updateTeamStats();Groups.unit.updatePhysics();for(Building b:Groups.build.copy())if(b.isValid())b.update();for(Unit u:Groups.unit.copy())if(!u.dead&&u.type==carrier)u.update();Time.update();Vars.state.tick++;}
}
