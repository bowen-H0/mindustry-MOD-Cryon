import arc.*;
import arc.files.*;
import arc.mock.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.net.*;
import mindustry.world.blocks.production.GenericCrafter;
import cryon.Content.*;

public class SodiumChemistryCheck{
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void tick(GenericCrafter.GenericCrafterBuild b){b.power.status=1;b.updateConsumption();b.updateTile();}
    static GenericCrafter.GenericCrafterBuild place(GenericCrafter block,int x){
        Vars.world.tile(x,20).setBlock(block,Team.sharded);
        return (GenericCrafter.GenericCrafterBuild)Vars.world.build(x,20);
    }
    static void produce(GenericCrafter.GenericCrafterBuild b,mindustry.type.Item item,int amount){
        for(int i=0;i<Math.ceil(((GenericCrafter)b.block).craftTime)+5 && b.items.get(item)<amount;i++)tick(b);
        check(b.items.get(item)==amount,"wrong output for "+b.block.name);
    }
    public static void main(String[]args){
        Core.app=new MockApplication();Core.files=new MockFiles();Core.graphics=new MockGraphics();Core.audio=new MockAudio();
        Core.settings=new Settings();Core.settings.setDataDirectory(new Fi("/tmp/cryon-sodium-chemistry-check"));Core.bundle=I18NBundle.createEmptyBundle();
        Vars.headless=true;Vars.loadLocales=false;Vars.net=new Net(null);Vars.platform=new mindustry.core.Platform(){};
        Vars.init();Vars.content.createBaseContent();CryonItems.load();SodiumChemistry.load();Vars.content.init();
        Vars.world.loadGenerator(70,50,tiles->{for(int x=0;x<70;x++)for(int y=0;y<50;y++)
            tiles.set(x,y,new mindustry.world.Tile(x,y,Blocks.sand,Blocks.air,Blocks.air));});
        Vars.state.rules.env=mindustry.world.meta.Env.space;Time.delta=1;
        for(GenericCrafter block:new GenericCrafter[]{SodiumChemistry.sodiumHydrolyzer,SodiumChemistry.sodaCarbonator,SodiumChemistry.crystalSandFurnace,SodiumChemistry.crystalSandSodiumExtractor})
            check(block.supportsEnv(Vars.state.rules.env),"atmosphere required");
        check(CryonItems.belongsTo("sodium-hydroxide","cryon-cryon") && CryonItems.belongsTo("sodium-carbonate","cryon-cryon"),"missing planet assignment");
        var first=place(SodiumChemistry.sodiumHydrolyzer,15);
        first.items.add(CryonItems.sodium,2);tick(first);
        check(first.progress==0 && first.items.get(CryonItems.sodium)==2,"reacted without water");
        first.liquids.add(Liquids.water,30);first.liquids.set(Liquids.hydrogen,40);tick(first);
        check(first.progress==0 && first.liquids.get(Liquids.water)==30,"full hydrogen outlet did not stop reactor");
        first.liquids.set(Liquids.hydrogen,0);produce(first,CryonItems.sodiumHydroxide,2);
        check(first.items.get(CryonItems.sodium)==0,"sodium not consumed");
        check(first.liquids.get(Liquids.hydrogen)>=3.99f && first.liquids.get(Liquids.hydrogen)<4.1f,"incorrect hydrogen output");
        check(first.liquids.get(Liquids.water)<18.1f,"water not consumed");
        var second=place(SodiumChemistry.sodaCarbonator,30);
        second.items.add(CryonItems.sodiumHydroxide,2);tick(second);check(second.progress==0,"carbonated without dry ice");
        second.items.add(CryonItems.dryIce,1);produce(second,CryonItems.sodiumCarbonate,1);
        check(second.items.get(CryonItems.sodiumHydroxide)==0 && second.items.get(CryonItems.dryIce)==0,"carbonate inputs not consumed");
        var third=place(SodiumChemistry.crystalSandFurnace,45);
        third.items.add(CryonItems.sodiumCarbonate,1);tick(third);check(third.progress==0,"furnace ran without quartz");
        third.items.add(CryonItems.quartz,2);third.power.status=0;third.updateConsumption();third.updateTile();
        check(third.progress==0,"furnace ran without power");
        produce(third,CryonItems.crystalSand,3);
        check(third.items.get(CryonItems.sodiumCarbonate)==0 && third.items.get(CryonItems.quartz)==0,"furnace inputs not consumed");
        third.items.add(CryonItems.crystalSand,17);third.items.add(CryonItems.sodiumCarbonate,1);third.items.add(CryonItems.quartz,2);
        float progress=third.progress;tick(third);check(third.progress==progress,"full item output failed to stop");
        var extractor=place(SodiumChemistry.crystalSandSodiumExtractor,60);
        extractor.items.add(CryonItems.crystalSand,3);extractor.items.add(CryonItems.dryIce,1);
        tick(extractor);check(extractor.progress==0,"extracted without water");
        extractor.liquids.add(Liquids.water,20);
        extractor.items.remove(CryonItems.dryIce,1);tick(extractor);check(extractor.progress==0,"extracted without dry ice");
        extractor.items.add(CryonItems.dryIce,1);
        extractor.power.status=0;extractor.updateConsumption();extractor.updateTile();check(extractor.progress==0,"extracted without power");
        produce(extractor,CryonItems.sodium,1);
        check(extractor.items.get(CryonItems.crystalSand)==0 && extractor.items.get(CryonItems.dryIce)==0,"extractor inputs not consumed");
        check(Math.abs(extractor.liquids.get(Liquids.water)-14f)<0.05f,"incorrect extraction water usage");
        check(SodiumChemistry.crystalSandSodiumExtractor.outputItem.amount==1,"sodium recycle balance changed");
        System.out.println("PASS: sodium/dry-ice/quartz chain and sodium recovery, existing crystal sand, hydrogen byproduct, vacuum support, planet assignments, missing inputs, power loss and full outputs");
    }
}
