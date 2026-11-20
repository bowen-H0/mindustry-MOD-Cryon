import arc.*;
import arc.files.*;
import arc.mock.*;
import arc.util.*;
import arc.util.io.*;
import java.io.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.net.*;
import cryon.Content.CryonItems;
import cryon.Type.*;

public class NuclearByproductCheck{
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void tick(Building b,int count){for(int i=0;i<count;i++){Time.time++;Time.delta=1;b.updateTile();}}
    static RBMKReactor.ReactorPart.ReactorPartBuild rod(RBMKReactor.ReactorPart block,int x,int y,Team team){
        Vars.world.tile(x,y).setBlock(block,team);return (RBMKReactor.ReactorPart.ReactorPartBuild)Vars.world.build(x,y);
    }
    public static void main(String[]args)throws Exception{
        Core.app=new MockApplication();Core.files=new MockFiles();Core.graphics=new MockGraphics();Core.audio=new MockAudio();
        Core.settings=new Settings();Core.settings.setDataDirectory(new Fi("/tmp/cryon-nuclear-byproduct-check"));Core.bundle=I18NBundle.createEmptyBundle();
        Vars.headless=true;Vars.loadLocales=false;Vars.net=new Net(null);Vars.platform=new mindustry.core.Platform(){};
        Vars.init();Vars.content.createBaseContent();CryonItems.load();
        var fuel=new RBMKReactor.ReactorPart("byproduct-test-fuel",RBMKReactor.Kind.fuel);
        fuel.np.step=1;fuel.np.burnPerFission=1;fuel.np.heatPerFission=0;fuel.np.nu=0;
        fuel.np.moveFast=fuel.np.moveSlow=0;fuel.byproductCapacity=2;
        var collector=new NuclearByproductCollector("byproduct-test-collector");
        Vars.content.init();
        Vars.world.loadGenerator(100,100,tiles->{for(int x=0;x<100;x++)for(int y=0;y<100;y++)
            tiles.set(x,y,new mindustry.world.Tile(x,y,Blocks.sand,Blocks.air,Blocks.air));});
        Time.delta=1;
        var first=rod(fuel,20,20,Team.sharded);first.items.add(CryonItems.neutronite,4);
        first.core.slow=2;tick(first,1);
        check(first.fuelLeft==0.5f && first.items.get(CryonItems.nuclearByproduct)==0,"produced waste before fuel exhaustion");
        first.core.slow=2;tick(first,1);check(first.items.get(CryonItems.nuclearByproduct)==1 && first.fuelLeft==0,"spent fuel did not yield one byproduct");
        tick(first,3);check(first.items.get(CryonItems.nuclearByproduct)==1,"idle rod duplicated waste");
        first.core.slow=4;tick(first,1);check(first.items.get(CryonItems.nuclearByproduct)==2,"second exhausted fuel missing");
        int remaining=first.items.get(CryonItems.neutronite);first.core.slow=4;tick(first,1);
        check(first.fuelLeft==0 && first.items.get(CryonItems.neutronite)==remaining,"full waste buffer loaded more fuel");
        first.items.remove(CryonItems.nuclearByproduct,1);first.core.slow=0;tick(first,1);
        check(first.fuelLeft==1 && first.items.get(CryonItems.neutronite)==remaining-1,"fuel failed to resume after collection");
        first.fuelLeft=.375f;first.items.set(CryonItems.nuclearByproduct,2);
        ByteArrayOutputStream data=new ByteArrayOutputStream();first.writeAll(new Writes(new DataOutputStream(data)));
        first.items.clear();first.fuelLeft=0;
        first.readAll(new Reads(new DataInputStream(new ByteArrayInputStream(data.toByteArray()))),first.version());
        check(first.fuelLeft==.375f && first.items.get(CryonItems.nuclearByproduct)==2,"save lost fuel or byproducts");
        var second=rod(fuel,20,24,Team.sharded);second.items.add(CryonItems.nuclearByproduct,2);
        var enemy=rod(fuel,23,24,Team.crux);enemy.items.add(CryonItems.nuclearByproduct,2);
        var far=rod(fuel,50,20,Team.sharded);far.items.add(CryonItems.nuclearByproduct,2);
        Vars.world.tile(25,20).setBlock(collector,Team.sharded);
        var pull=(NuclearByproductCollector.NuclearByproductCollectorBuild)Vars.world.build(25,20);pull.efficiency=1;
        int fuelBefore=first.items.get(CryonItems.neutronite);
        tick(pull,10);
        check(pull.items.get(CryonItems.nuclearByproduct)==2,"collector throughput incorrect");
        check(first.items.get(CryonItems.nuclearByproduct)==1 && second.items.get(CryonItems.nuclearByproduct)==1,"collector did not poll rods fairly");
        check(first.items.get(CryonItems.neutronite)==fuelBefore,"collector removed fuel");
        check(enemy.items.get(CryonItems.nuclearByproduct)==2 && far.items.get(CryonItems.nuclearByproduct)==2,"collector crossed team/range boundary");
        pull.efficiency=0;tick(pull,10);check(pull.items.get(CryonItems.nuclearByproduct)==2,"unpowered collector extracted items");
        pull.efficiency=1;pull.enabled=false;tick(pull,10);check(pull.items.get(CryonItems.nuclearByproduct)==2,"disabled collector extracted items");
        pull.enabled=true;pull.items.set(CryonItems.nuclearByproduct,collector.itemCapacity);tick(pull,10);
        check(first.items.get(CryonItems.nuclearByproduct)==1 && second.items.get(CryonItems.nuclearByproduct)==1,"full collector destroyed source items");
        Vars.world.tile(27,20).setBlock(Blocks.conveyor,Team.sharded,0);
        tick(pull,2);check(Vars.world.build(27,20).items.get(CryonItems.nuclearByproduct)>0,"collector did not output to conveyor");
        pull.items.clear();first.tile.remove();second.team=Team.crux;tick(pull,10);
        check(pull.items.total()==0,"stale/deleted/hostile source was extracted");
        check(!pull.acceptItem(null,CryonItems.neutronite),"collector accepted fuel input");
        System.out.println("PASS: spent-fuel yield, no idle duplication, full-buffer pause/resume, save persistence, automatic/fair collection, fuel protection, team/range filtering, power/disabled/full pauses, conveyor output and stale-source checks");
    }
}
