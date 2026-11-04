import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.blocks.defense.turrets.PointDefenseTurret;
import mindustry.world.consumers.ConsumeItems;
import java.io.*;

/** Runs packaged content, transaction/save tests, real crafting and projectile interception. */
public class PorphyraIndustryCheck{
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void ticks(Building build,int count){
        for(int i=0;i<count;i++){Time.delta=1;Time.time+=1;build.updateConsumption();build.updateTile();}
    }
    static Building place(String name,int x,int y){
        Vars.world.tile(x,y).setBlock(Vars.content.block("cryon-porphyra-"+name),Team.sharded);
        return Vars.world.tile(x,y).build;
    }
    public static void main(String[] args)throws Exception{
        PorphyraContentCheck.main(args);
        Vars.logic.reset(); Vars.state.rules = new Rules();
        Vars.world.loadGenerator(80,80,tiles->{
            for(int x=0;x<80;x++)for(int y=0;y<80;y++)tiles.set(x,y,new Tile(x,y,Blocks.sand,Blocks.air,Blocks.air));
        });
        String[] plants={"plate-press","protocol-encoder","trade-tower","rhenium-press","crystal-stabilizer","alloy-forge","plasma-condenser","laser-defense"};
        for(String name:plants) check(Vars.content.block("cryon-porphyra-"+name).techNode!=null,"Missing research: "+name);
        for(String name:new String[]{"rhenium-plate","protocol-point","laminated-rhenium","phase-crystal","porphyra-alloy"})
            check(Vars.content.item("cryon-"+name).techNode!=null,"Missing item research: "+name);
        var tower=Vars.content.block("cryon-porphyra-trade-tower");
        Item[] imports=(Item[])tower.getClass().getField("imports").get(tower);
        int[] prices=(int[])tower.getClass().getField("prices").get(tower);
        int batchSize=tower.getClass().getField("batchSize").getInt(tower);
        Item points=Vars.content.item("cryon-protocol-point");
        var trade=place("trade-tower",12,12);
        check(imports.length==3 && imports[0]==Items.silicon && imports[1]==Items.graphite
                && imports[2]==Items.titanium,"Trade whitelist is not exactly silicon, graphite and titanium");
        check(trade.acceptItem(trade,points) && !trade.acceptItem(trade,Items.silicon)
                && !trade.acceptItem(trade,Vars.content.item("cryon-rhenium")),"Trade accepts non-currency items");
        trade.power.status=1; ticks(trade,200);
        check(trade.items.total()==0 && progress(trade)==0,"Free imports without currency");
        trade.items.add(points,7); ticks(trade,200);
        check(trade.items.get(Items.silicon)==0 && trade.items.get(points)==7,"Insufficient currency was spent");
        for(int index=0;index<3;index++){
            trade.items.clear(); select(trade,index);setProgress(trade,0);
            trade.items.add(points,prices[index]);
            trade.power.status=0;ticks(trade,200);
            check(trade.items.get(imports[index])==0 && progress(trade)==0,"Trade ran without power");
            trade.power.status=1;ticks(trade,185);
            check(trade.items.get(imports[index])==batchSize && trade.items.get(points)==0,
                    "Incorrect payment/output: "+imports[index]);
            ticks(trade,200);check(trade.items.get(imports[index])==batchSize,"Trade duplicated output");
        }
        // Input and imported goods must not compete for the same storage budget.
        for(int index=0;index<3;index++){
            trade.items.clear();select(trade,index);setProgress(trade,0);
            trade.items.add(points,120);
            check(trade.shouldConsume(), "Full protocol storage deadlocks order " + index);
            check(!trade.acceptItem(trade,points), "Overfilled protocol storage accepted more currency");
            ticks(trade,180);
            check(trade.items.get(points)==120-prices[index] && trade.items.get(imports[index])==batchSize,
                    "Full protocol storage cannot trade: " + imports[index]);
            check(trade.acceptItem(trade,points), "Imported goods block protocol refilling");
        }
        select(trade,0);trade.items.clear();setProgress(trade,0);
        trade.items.add(points,120);trade.items.add(Items.silicon,110);
        ticks(trade,180);
        check(trade.items.get(Items.silicon)==120 && trade.items.get(points)==112,
                "Last available output batch was incorrectly blocked");
        ticks(trade,200);
        check(trade.items.get(Items.silicon)==120 && trade.items.get(points)==112,
                "Full output storage did not pause trading");
        trade.items.remove(Items.silicon,10);ticks(trade,180);
        check(trade.items.get(Items.silicon)==120 && trade.items.get(points)==104,
                "Trade did not resume after output was removed");
        trade.items.clear();trade.items.add(points,119);
        trade.items.add(Items.graphite,60);trade.items.add(Items.titanium,60);setProgress(trade,0);
        check(!trade.shouldConsume() && trade.acceptItem(trade,points),
                "Mixed imported goods must share output storage, independently from currency");
        check(trade.acceptStack(points,100,trade)==1 && trade.acceptStack(Items.silicon,100,trade)==0,
                "Stack transfers do not respect the independent currency buffer");
        select(trade,0);trade.items.clear();trade.items.add(points,8);trade.items.add(Items.graphite,112);
        setProgress(trade,0);ticks(trade,200);
        check(trade.items.get(points)==8 && trade.items.get(Items.silicon)==0,"Full output spent currency");
        trade.items.clear();trade.items.add(points,20);ticks(trade,60);
        check(progress(trade)>0,"Trade did not progress");
        select(trade,1);check(progress(trade)==0 && trade.items.get(points)==20,"Changing order lost currency or progress was carried over");
        select(trade,-1);select(trade,99);check(selected(trade)==1,"Invalid order accepted");
        ticks(trade,45);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();trade.write(Writes.get(new DataOutputStream(bytes)));
        var restored=place("trade-tower",20,12);
        restored.read(Reads.get(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))),trade.version());
        check(selected(restored)==selected(trade) && progress(restored)==progress(trade),"Trade order/progress did not survive save/load");
        craft("plate-press",30,22);
        craft("protocol-encoder",30,12);craft("rhenium-press",38,12);
        craft("crystal-stabilizer",46,12);craft("alloy-forge",54,12);craft("plasma-condenser",62,12);
        interception();
        System.out.println("PASS: three-resource trade whitelist, exact charges, no free/powerless/full-store trades, config/save, five processing recipes and powered projectile interception");
    }
    static void select(Building build,int value)throws Exception{build.getClass().getMethod("select",int.class).invoke(build,value);}
    static int selected(Building build)throws Exception{return build.getClass().getField("selected").getInt(build);}
    static float progress(Building build)throws Exception{return build.getClass().getField("progress").getFloat(build);}
    static void setProgress(Building build,float value)throws Exception{build.getClass().getField("progress").setFloat(build,value);}
    static void craft(String name,int x,int y){
        var build=(GenericCrafter.GenericCrafterBuild)place(name,x,y);
        var block=(GenericCrafter)build.block;
        ConsumeItems ingredients=block.findConsumer(c->c instanceof ConsumeItems);
        build.power.status=1;ticks(build,(int)block.craftTime+5);
        check(build.progress==0,"Crafter ran without ingredients: "+name);
        for(var input:ingredients.items)build.items.add(input.item,input.amount);
        if(name.equals("alloy-forge"))build.liquids.add(Vars.content.liquid("cryon-porphyra-plasma"),30);
        build.power.status=0;ticks(build,(int)block.craftTime+5);
        check(build.progress==0,"Crafter ran without power: "+name);
        build.power.status=1;ticks(build,(int)block.craftTime+5);
        if(block.outputItem!=null){
            check(build.items.get(block.outputItem.item)==block.outputItem.amount,"Wrong crafting output: "+name);
            for(var input:ingredients.items)check(build.items.get(input.item)==0,"Recipe did not consume ingredients: "+name);
        }else check(build.liquids.get(block.outputLiquid.liquid)>0,"Condenser produced no plasma");
        for(var cost:block.requirements){
            if(block.outputItem!=null)check(cost.item!=block.outputItem.item,"Crafter requires its own product to build: "+name);
        }
    }
    static Bullet bullet(PointDefenseTurret.PointDefenseBuild build,Team team,boolean hittable,float damage,float distance){
        for(var old:Groups.bullet.copy())old.remove();
        var type=new mindustry.entities.bullet.BasicBulletType(0f,damage);type.hittable=hittable;type.lifetime=1000;
        var bullet=type.create((Entityc)null,team,build.x+distance,build.y,0);
        Groups.bullet.tree().insert(bullet);
        build.target=bullet;build.rotation=0;build.reloadCounter=0;
        return bullet;
    }
    static void interception(){
        var turret=(PointDefenseTurret)Vars.content.block("cryon-porphyra-laser-defense");
        turret.shootSound=Sounds.none;turret.beamEffect=turret.hitEffect=turret.shootEffect=Fx.none;
        var build=(PointDefenseTurret.PointDefenseBuild)place("laser-defense",40,42);
        var target=bullet(build,Team.crux,true,120,30);
        build.power.status=0;ticks(build,40);check(target.damage==120,"Laser defense fired without power");
        build.power.status=1;ticks(build,14);check(target.damage<120,"Laser defense did not weaken an enemy projectile");
        ticks(build,70);check(!target.isAdded(),"Laser defense failed to destroy a projectile");
        target=bullet(build,Team.sharded,true,120,30);ticks(build,50);check(target.damage==120 && target.isAdded(),"Friendly projectile was intercepted");
        target=bullet(build,Team.crux,false,120,30);ticks(build,50);check(target.damage==120 && target.isAdded(),"Unhittable projectile was intercepted");
        target=bullet(build,Team.crux,true,120,turret.range+40);ticks(build,50);check(target.damage==120,"Out-of-range projectile was intercepted");
        target=bullet(build,Team.crux,true,120,30);build.enabled=false;ticks(build,50);check(target.damage==120,"Disabled defense fired");
    }
}
