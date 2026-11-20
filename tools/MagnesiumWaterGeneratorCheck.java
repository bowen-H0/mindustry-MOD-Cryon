import arc.*;
import arc.files.*;
import arc.mock.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.net.*;
import cryon.Content.CryonItems;
import cryon.Type.MagnesiumWaterGenerator;

public class MagnesiumWaterGeneratorCheck{
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void tick(MagnesiumWaterGenerator.MagnesiumWaterGeneratorBuild b){b.updateConsumption();b.updateTile();}
    public static void main(String[]args)throws Exception{
        Core.app=new MockApplication();Core.files=new MockFiles();Core.graphics=new MockGraphics();Core.audio=new MockAudio();
        Core.settings=new Settings();Core.settings.setDataDirectory(new Fi("/tmp/cryon-magnesium-water-check"));Core.bundle=I18NBundle.createEmptyBundle();
        Vars.headless=true;Vars.loadLocales=false;Vars.net=new Net(null);Vars.platform=new mindustry.core.Platform(){};
        Vars.init();Vars.content.createBaseContent();CryonItems.load();
        MagnesiumWaterGenerator block=new MagnesiumWaterGenerator("magnesium-water-check");Vars.content.init();
        Vars.world.loadGenerator(50,50,tiles->{for(int x=0;x<50;x++)for(int y=0;y<50;y++)
            tiles.set(x,y,new mindustry.world.Tile(x,y,Blocks.sand,Blocks.air,Blocks.air));});
        Vars.state.rules.env=mindustry.world.meta.Env.space;
        Time.delta=1;
        Vars.world.tile(20,20).setBlock(block,Team.sharded);
        MagnesiumWaterGenerator.MagnesiumWaterGeneratorBuild b=(MagnesiumWaterGenerator.MagnesiumWaterGeneratorBuild)Vars.world.build(20,20);
        check(block.supportsEnv(Vars.state.rules.env),"requires an atmosphere");
        tick(b);check(b.getPowerProduction()==0,"generated power without inputs");
        b.items.add(CryonItems.magnesium,4);tick(b);check(b.getPowerProduction()==0 && b.items.total()==4,"operated without water");
        b.liquids.add(Liquids.water,60);
        for(int i=0;i<60;i++)tick(b);
        check(Math.abs(b.getPowerProduction()*60-1200)<0.01,"incorrect power output");
        check(b.items.get(CryonItems.magnesium)==2,"incorrect magnesium use");
        check(Math.abs(b.liquids.get(Liquids.water)-48)<0.01,"incorrect water use");
        check(Math.abs(b.liquids.get(Liquids.hydrogen)-6)<0.01,"incorrect hydrogen output");
        float remaining=b.generateTime;
        b.liquids.set(Liquids.water,0);tick(b);
        check(b.getPowerProduction()==0 && b.generateTime==remaining,"dry cell wasted stored fuel");
        b.liquids.set(Liquids.water,40);b.liquids.set(Liquids.hydrogen,60);
        tick(b);check(b.getPowerProduction()==0 && b.generateTime==remaining && b.liquids.get(Liquids.water)==40,"full outlet did not pause inputs");
        b.liquids.set(Liquids.hydrogen,0);tick(b);check(b.getPowerProduction()>0,"failed to resume after outlet emptied");
        remaining=b.generateTime;b.enabled=false;tick(b);
        check(b.getPowerProduction()==0 && b.generateTime==remaining,"disabled cell kept consuming");
        b.enabled=true;b.items.clear();
        for(int i=0;i<200;i++){b.liquids.set(Liquids.water,40);b.liquids.set(Liquids.hydrogen,0);tick(b);}
        check(b.getPowerProduction()==0,"generated power after magnesium exhausted");
        System.out.println("PASS: vacuum operation, 1200 power/s, magnesium/water consumption, hydrogen output, dry/full/disabled pauses, resumption and fuel exhaustion");
    }
}
