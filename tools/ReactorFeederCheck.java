import arc.*;
import arc.files.Fi;
import arc.math.geom.Point2;
import arc.mock.*;
import arc.util.*;
import arc.util.io.*;
import java.io.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.entities.units.BuildPlan;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.net.Net;
import mindustry.type.Item;
import mindustry.world.*;
import mindustry.world.blocks.ConstructBlock;

/** Real schematic serialization, delayed construction, rebuilding and feeder save compatibility. */
public class ReactorFeederCheck{
    static Block feeder, rod;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static int link(Building b)throws Exception{return b.getClass().getField("link").getInt(b);}
    static void tick(Building b,int count){for(int i=0;i<count;i++){Time.delta=1f;Time.time++;b.efficiency=1f;b.updateTile();}}
    static Building place(Block block,int x,int y){Vars.world.tile(x,y).setBlock(block,Team.sharded);return Vars.world.build(x,y);}
    static byte[] save(Building b)throws Exception{
        ByteArrayOutputStream data=new ByteArrayOutputStream();b.write(new Writes(new DataOutputStream(data)));return data.toByteArray();
    }
    static void load(Building b,byte[] data,byte version){b.read(new Reads(new DataInputStream(new ByteArrayInputStream(data))),version);}
    public static void main(String[] args)throws Exception{
        Core.app=new MockApplication();Core.files=new MockFiles();Core.graphics=new MockGraphics();Core.audio=new MockAudio();
        Core.settings=new Settings();Core.settings.setDataDirectory(new Fi("/tmp/reactor-feeder-check"));Core.bundle=I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(Core.settings.getDataDirectory().child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(Core.settings.getDataDirectory().child("mods/unitlanuch.jar"));
        Vars.headless=true;Vars.loadLocales=false;Vars.net=new Net(null);Vars.platform=new mindustry.core.Platform(){};
        mindustry.core.Version.init();Vars.init();Vars.content.createBaseContent();Vars.content.createModContent();Vars.content.init();
        check(!Vars.mods.locateMod("cryon").hasContentErrors(),"Mod content errors");
        Vars.logic=new Logic();Vars.state.rules=new Rules();
        Vars.world.loadGenerator(160,160,tiles->{for(int x=0;x<160;x++)for(int y=0;y<160;y++)tiles.set(x,y,new Tile(x,y,Blocks.sand,Blocks.air,Blocks.air));});
        feeder=Vars.content.block("cryon-reactor-feeder");rod=Vars.content.block("cryon-fuel-rod");
        for(Block block:Vars.content.blocks())block.unlock();
        Building source=place(feeder,20,20);Building originalRod=place(rod,24,21);
        source.wasVisible=originalRod.wasVisible=true;
        source.configured(null,originalRod.pos());
        Vars.schematics=new Schematics();
        Schematic schematic=Schematics.readBase64(Vars.schematics.writeBase64(Vars.schematics.create(20,20,24,21)));
        check(schematic.tiles.size==2,"Schematic omitted reactor buildings");
        for(int rotation=0;rotation<4;rotation++){
            Schematic rotated=Schematics.rotate(schematic,rotation);
            var plans=Vars.schematics.toPlans(rotated,45+rotation*25,60);
            BuildPlan feedPlan=plans.find(p->p.block==feeder),rodPlan=plans.find(p->p.block==rod);
            check(feedPlan!=null&&rodPlan!=null&&feedPlan.config instanceof Point2,"Missing schematic link configuration");
            Point2 offset=(Point2)feedPlan.config;
            check(feedPlan.x+offset.x==rodPlan.x&&feedPlan.y+offset.y==rodPlan.y,"Rotated schematic has the wrong offset");
            ConstructBlock.constructFinish(feedPlan.tile(),feeder,null,(byte)0,Team.sharded,feedPlan.config);
            Building built=feedPlan.build();int expected=Point2.pack(rodPlan.x,rodPlan.y);
            tick(built,90);
            check(link(built)==expected,"Feeder forgot a rod before its construction started (rotation "+rotation+")");
            check(feedPlan.config.equals(built.config()),"Waiting connection was lost when copied");
            byte[] waiting=save(built);built.configured(null,-1);load(built,waiting,built.version());
            check(link(built)==expected,"Waiting connection lost during save/load");
            rodPlan.tile().setBlock(ConstructBlock.get(rod.size),Team.sharded);
            ((ConstructBlock.ConstructBuild)rodPlan.build()).setConstruct(Blocks.air,rod);
            tick(built,60);check(link(built)==expected,"Link lost while rod is under construction");
            ConstructBlock.constructFinish(rodPlan.tile(),rod,null,(byte)0,Team.sharded,null);
            Building target=rodPlan.build();
            Object props=rod.getClass().getField("np").get(rod);
            Item fuel=(Item)props.getClass().getField("fuelItem").get(props);
            built.items.add(fuel,2);tick(built,15);
            check(target.items.get(fuel)==2&&built.items.total()==0,"Completed schematic does not deliver fuel");
            rodPlan.tile().remove();built.items.add(fuel,1);tick(built,90);
            check(link(built)==expected&&built.items.get(fuel)==1,"Destroyed rod erased link or buffered fuel");
            target=place(rod,rodPlan.x,rodPlan.y);tick(built,15);
            check(target.items.get(fuel)==1,"Rebuilt rod did not resume receiving fuel");
            built.configured(null,-1);tick(built,90);check(link(built)==-1,"Explicit disconnection was not preserved");
            ByteArrayOutputStream legacy=new ByteArrayOutputStream();DataOutputStream old=new DataOutputStream(legacy);
            old.writeInt(expected);old.writeFloat(2f);load(built,legacy.toByteArray(),(byte)0);
            check(link(built)==expected,"Legacy absolute link save is incompatible");
        }
        System.out.println("REACTOR_FEEDER_PASS: serialized schematics in all four rotations, delayed construction, save/load, real fuel delivery, rebuilding and explicit disconnect");
    }
}
