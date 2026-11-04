import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.*;
import mindustry.gen.Building;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.units.*;
import mindustry.world.blocks.payloads.UnitPayload;
import mindustry.world.consumers.ConsumeItems;

/** Real unit manufacture/upgrade stalls without processed materials and consumes them on completion. */
public class PorphyraUnitProductionCheck{
    static void check(boolean valid,String message){if(!valid)throw new AssertionError(message);}
    static boolean processed(Item item){return item.name.equals("cryon-rhenium-plate")
            || item.name.equals("cryon-laminated-rhenium") || item.name.equals("cryon-phase-crystal")
            || item.name.equals("cryon-porphyra-alloy");}
    static void ticks(Building build,int count){for(int i=0;i<count;i++){Time.time++;Time.delta=1;build.updateConsumption();build.updateTile();}}
    static Building place(String name,int x,int y){Vars.world.tile(x,y).setBlock(Vars.content.block("cryon-porphyra-"+name),Team.sharded);return Vars.world.tile(x,y).build;}
    public static void main(String[] args)throws Exception{
        PorphyraIndustryCheck.main(args);
        Vars.logic.reset();Vars.state.rules=new Rules();Vars.state.rules.unitCap=100;
        Vars.world.loadGenerator(80,80,tiles->{for(int x=0;x<80;x++)for(int y=0;y<80;y++)tiles.set(x,y,new Tile(x,y,Blocks.sand,Blocks.air,Blocks.air));});
        var press=Vars.content.block("cryon-porphyra-plate-press");
        for(var cost:press.requirements)check(cost.item.hardness<=1,"Starter plate press needs late materials");
        check(!press.techNode.objectives.contains(o -> o instanceof mindustry.game.Objectives.SectorComplete),"Starter plate press is sector gated");
        var factory=(UnitFactory)Vars.content.block("cryon-porphyra-unit-factory");
        for(int index=0;index<factory.plans.size;index++){
            var build=(UnitFactory.UnitFactoryBuild)place("unit-factory",15+index*20,20);
            build.currentPlan=index;build.power.status=1;
            var plan=factory.plans.get(index);int processedCount=0;
            for(var cost:plan.requirements){if(processed(cost.item))processedCount++;else build.items.add(cost.item,cost.amount);}
            check(processedCount>0,"Unit lacks processed inputs: "+plan.unit);
            ticks(build,(int)plan.time+5);
            check(build.payload==null,"Unit manufactured without processed materials: "+plan.unit);
            for(var cost:plan.requirements)if(processed(cost.item))build.items.add(cost.item,cost.amount);
            ticks(build,(int)plan.time+1);
            check(build.payload!=null && build.payload.unit.type==plan.unit,"Unit manufacture failed: "+plan.unit);
            for(var cost:plan.requirements)check(build.items.get(cost.item)==0,"Unit recipe did not consume "+cost.item);
        }
        for(int tier=2;tier<=5;tier++){
            var block=(Reconstructor)Vars.content.block("cryon-porphyra-reconstructor-"+tier);
            var build=(Reconstructor.ReconstructorBuild)place("reconstructor-"+tier,12+(tier-2)*18,55);
            ConsumeItems recipe=block.findConsumer(c->c instanceof ConsumeItems);
            var path=block.upgrades.first();build.payload=new UnitPayload(path[0].create(Team.sharded));build.power.status=1;
            int processedCount=0;
            for(var cost:recipe.items){if(processed(cost.item))processedCount++;else build.items.add(cost.item,cost.amount);}
            check(processedCount>0,"Upgrade lacks processed inputs at T"+tier);
            ticks(build,(int)block.constructTime+5);
            check(build.payload.unit.type==path[0],"Upgrade succeeded without processed materials at T"+tier);
            for(var cost:recipe.items)if(processed(cost.item))build.items.add(cost.item,cost.amount);
            ticks(build,(int)block.constructTime+1);
            check(build.payload.unit.type==path[1],"Upgrade failed at T"+tier);
            for(var cost:recipe.items)check(build.items.get(cost.item)==0,"Upgrade did not consume "+cost.item);
        }
        System.out.println("PASS: all three starter units and T2-T5 upgrades require and consume processed materials; starter plate press uses only local early resources");
    }
}
