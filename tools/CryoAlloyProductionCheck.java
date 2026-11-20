import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.*;
import arc.util.io.*;
import java.io.*;
import java.nio.file.Files;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.net.Net;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.heat.HeatBlock;
import mindustry.world.blocks.production.*;
import mindustry.world.meta.*;

/** Real mod loading, complete recipes, actual constructor links and retained legacy save behavior. */
public class CryoAlloyProductionCheck{
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static Item item(String name){return Vars.content.item("cryon-"+name);}
    static Building place(Block block,int x,int y){Vars.world.tile(x,y).setBlock(block,Team.sharded);return Vars.world.build(x,y);}
    static void advance(){Time.delta=1;Time.time++;}
    static void step(Building b){if(b.power!=null)b.power.status=1;b.updateConsumption();b.updateTile();}
    static void produce(GenericCrafter.GenericCrafterBuild b,Item output,int amount){
        for(int i=0;i<((GenericCrafter)b.block).craftTime+5 && b.items.get(output)<amount;i++){advance();step(b);}
        check(b.items.get(output)==amount,"output missing for "+b.block.name+" enabled="+b.enabled+" efficiency="+b.efficiency+" progress="+b.progress+(b instanceof HeatCrafter.HeatCrafterBuild ? " heat="+((HeatCrafter.HeatCrafterBuild)b).heat : ""));
    }
    static class HeatFixture extends Block{
        HeatFixture(){super("alloy-heat-fixture");solid=update=destructible=true;buildType=HeatFixtureBuild::new;}
        class HeatFixtureBuild extends Building implements HeatBlock{
            public float heat(){return 40;}public float heatFrac(){return 1;}
        }
    }
    public static void main(String[]args)throws Exception{
        Core.app=new MockApplication();Core.files=new MockFiles();Core.graphics=new MockGraphics();Core.audio=new MockAudio();
        Core.settings=new Settings();Fi data=new Fi(Files.createTempDirectory("cryon-alloy-check-").toFile());Core.settings.setDataDirectory(data);Core.bundle=I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlaunch.jar"));
        Vars.headless=true;Vars.loadLocales=false;Vars.net=new Net(null);Vars.platform=new mindustry.core.Platform(){};
        mindustry.core.Version.init();Vars.init();Vars.content.createBaseContent();Vars.content.createModContent();
        HeatFixture heat=new HeatFixture();Vars.content.init();
        check(!Vars.mods.locateMod("cryon").hasContentErrors(),"mod content errors");
        Vars.world.loadGenerator(140,120,tiles->{for(int x=0;x<140;x++)for(int y=0;y<120;y++)tiles.set(x,y,new Tile(x,y,Blocks.sand,Blocks.air,Blocks.air));});
        Time.delta=1;Vars.state.rules.env=Env.space;
        GenericCrafter gamma=(GenericCrafter)Vars.content.block("cryon-gamma-crystallizer");
        GenericCrafter lattice=(GenericCrafter)Vars.content.block("cryon-perfect-lattice-assembler");
        GenericCrafter phase=(GenericCrafter)Vars.content.block("cryon-superphase-synthesizer");
        GenericCrafter alloy=(GenericCrafter)Vars.content.block("cryon-cryo-alloy-stabilizer");
        Liquid gas=Vars.content.liquid("cryon-superphase-matter"),tritium=Vars.content.liquid("cryon-tritium");
        check(gas!=null && gas.gas && !gas.coolant,"superphase matter is not a distinct gas");
        for(Block block:new Block[]{gamma,lattice,phase,alloy}){
            check(block.supportsEnv(Env.space),"new production requires atmosphere");
            for(ItemStack req:block.requirements)check(req.item!=item("cryo-alloy"),"circular alloy building cost");
        }
        var g=(GenericCrafter.GenericCrafterBuild)place(gamma,60,70);
        g.items.add(item("quartz"),4);g.liquids.add(Liquids.cryofluid,300);advance();step(g);
        check(g.progress==0,"gamma crystallizer ran without nuclear byproducts");
        g.items.add(item("nuclear-byproduct"),2);g.liquids.set(Liquids.cryofluid,0);advance();step(g);
        check(g.progress==0,"gamma crystallizer ran without coolant");
        g.liquids.set(Liquids.cryofluid,300);g.power.status=0;g.updateConsumption();g.updateTile();
        check(g.progress==0,"gamma crystallizer ran without power");
        produce(g,item("gamma-crystal"),16);
        check(g.items.get(item("nuclear-byproduct"))==0 && g.items.get(item("quartz"))==0,"gamma recipe inputs not consumed");
        check(Math.abs(g.liquids.get(Liquids.cryofluid)-240)<.3,"wrong gamma coolant usage");
        g.items.add(item("quartz"),4);g.items.add(item("nuclear-byproduct"),2);produce(g,item("gamma-crystal"),32);
        var l=(GenericCrafter.GenericCrafterBuild)place(lattice,20,20);
        l.items.add(item("nano-material"),4);l.items.add(Items.graphite,20);advance();step(l);check(l.progress==0,"lattice ran without nitrogen");
        l.liquids.add(Liquids.nitrogen,300);produce(l,item("perfect-lattice"),8);
        check(l.items.get(item("nano-material"))==0 && l.items.get(Items.graphite)==0,"lattice did not consume solids");
        check(Math.abs(l.liquids.get(Liquids.nitrogen)-180)<.3,"wrong nitrogen consumption");

        var p=(GenericCrafter.GenericCrafterBuild)place(phase,88,40);
        p.liquids.add(tritium,300);advance();step(p);
        check(p.liquids.get(gas)==0 && p.liquids.get(tritium)==300,"unlinked synth produced gas or consumed tritium");
        Block sourceBlock=Vars.content.block("cryon-constructor-source");sourceBlock.getClass().getField("production").setFloat(sourceBlock,20);
        Building source=place(sourceBlock,74,40),node=place(Vars.content.block("cryon-constructor-node"),80,40);source.enabled=true;
        float before=p.liquids.get(tritium);
        for(int i=0;i<60;i++){advance();step(source);step(node);step(p);}
        check(p.efficiency>.49f && p.efficiency<.51f,"constructor shortage did not scale output: "+p.efficiency);
        check(p.liquids.get(gas)>11.9f && p.liquids.get(gas)<12.1f,"half-supply gas production incorrect: "+p.liquids.get(gas));
        check(Math.abs(before-p.liquids.get(tritium)-p.liquids.get(gas)/4f)<.01,"tritium and gas scaling diverged");
        p.liquids.set(gas,300);before=p.liquids.get(tritium);
        advance();step(source);step(node);step(p);check(p.liquids.get(tritium)==before,"blocked gas outlet consumed tritium");
        place(Blocks.conduit,91,40);advance();step(source);step(node);step(p);
        check(Vars.world.build(91,40).liquids.get(gas)>0,"gas did not enter conduit");
        Vars.world.tile(91,40).remove();p.liquids.set(gas,0);node.tile.remove();before=p.liquids.get(tritium);
        advance();step(p);check(p.liquids.get(tritium)==before && p.liquids.get(gas)==0,"node removal did not stop synthesis");
        node=place(Vars.content.block("cryon-constructor-node"),80,40);sourceBlock.getClass().getField("production").setFloat(sourceBlock,40);
        for(int i=0;i<5;i++){advance();step(source);step(node);step(p);}
        check(p.efficiency>.99f && p.liquids.get(gas)>0,"reconnected constructor supply did not resume synthesis");
        p.power.status=0;p.updateConsumption();p.updateTile();check(p.efficiency==0,"synth ignored power loss");

        check(alloy.craftTime==180f && alloy.outputItem.amount==6,"alloy must produce 6 per 3-second batch");
        var a=(GenericCrafter.GenericCrafterBuild)place(alloy,30,50);
        a.items.add(item("perfect-lattice"),2);a.liquids.add(gas,300);advance();step(a);check(a.progress==0,"alloy ran without gamma crystals");
        check(g.items.get(item("gamma-crystal"))==32,"local gamma supply did not match alloy demand");
        g.items.remove(item("gamma-crystal"),8);a.items.add(item("gamma-crystal"),8);produce(a,item("cryo-alloy"),6);
        g.items.set(item("gamma-crystal"),100);g.items.add(item("quartz"),4);g.items.add(item("nuclear-byproduct"),2);
        float gammaProgress=g.progress, coolant=g.liquids.get(Liquids.cryofluid);advance();step(g);
        check(g.progress==gammaProgress && g.liquids.get(Liquids.cryofluid)==coolant,"full gamma output wasted coolant");
        check(a.items.get(item("perfect-lattice"))==0 && a.items.get(item("gamma-crystal"))==0,"alloy solids not consumed");
        check(Math.abs(a.liquids.get(gas)-264)<.5,"wrong superphase consumption");

        HeatCrafter legacy=(HeatCrafter)Vars.content.block("cryon-fusion-casting-furnace");
        check(legacy.buildVisibility==BuildVisibility.hidden && !legacy.placeablePlayer && !legacy.canPlaceOn(Vars.world.tile(20,80),Team.sharded,0),"legacy construction still allowed");
        var old=(HeatCrafter.HeatCrafterBuild)place(legacy,20,80);old.enabled=true;place(heat,19,80);
        old.items.add(Items.graphite,10);old.items.add(item("quartz"),10);old.items.add(item("sodium"),5);old.progress=.5f;
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();old.writeAll(new Writes(new DataOutputStream(bytes)));
        old.tile.remove();old=(HeatCrafter.HeatCrafterBuild)place(legacy,20,80);
        old.readAll(new Reads(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))),old.version());
        check(old.progress==.5f && old.items.get(item("sodium"))==5,"legacy save recipe/progress lost");
        produce(old,item("cryo-alloy"),1);
        check(old.items.get(Items.graphite)==0 && old.items.get(item("quartz"))==0 && old.items.get(item("sodium"))==0,"legacy recipe did not retain original inputs");
        System.out.println("CRYO_ALLOY_PASS: local gamma crystal synthesis and alloy use, coolant/input/power/full-output checks, native three-stage recipes, gas pipe output, real constructor links, partial supply, disconnect/reconnect, blocked gas, missing resources/power, legacy placement prohibition and saved legacy furnace production");
    }
}
