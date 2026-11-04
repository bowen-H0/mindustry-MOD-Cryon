import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.*;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.Locale;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.net.Net;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.consumers.ConsumePower;

/** Isolated profile test: story switches, planet gates, research, supply and every dialogue branch. */
public class QuantumStoryCheck{
    static ClassLoader loader;
    static Class<?> progress,flag,conversation,contact,choice;
    static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
    static void invoke(String name,String method)throws Exception{loader.loadClass(name).getMethod(method).invoke(null);}
    @SuppressWarnings({"rawtypes","unchecked"})
    static Object value(Class<?> type,String name){return Enum.valueOf((Class)type,name);}
    static boolean get(String name)throws Exception{return (boolean)progress.getMethod("get",flag).invoke(null,value(flag,name));}
    static void set(String name,boolean enabled)throws Exception{progress.getMethod("set",flag,boolean.class).invoke(null,value(flag,name),enabled);}
    static Object call(Object object,String method)throws Exception{return object.getClass().getMethod(method).invoke(object);}
    static Object session(String name)throws Exception{return conversation.getConstructor(contact).newInstance(value(contact,name));}
    static Object reconnect(String name)throws Exception{return conversation.getMethod("begin",contact).invoke(null,value(contact,name));}
    static boolean choose(Object session,String name)throws Exception{return (boolean)conversation.getMethod("choose",choice).invoke(session,value(choice,name));}
    static void tick(Building building){Time.delta=1;building.updateConsumption();building.updateTile();}
    public static void main(String[] args)throws Exception{
        Core.app=new MockApplication();Core.files=new MockFiles();Core.graphics=new MockGraphics();Core.audio=new MockAudio();
        Fi data=new Fi(Files.createTempDirectory("cryon-quantum-story-").toFile());
        Core.settings=new Settings();Core.settings.setDataDirectory(data);Core.settings.load();Core.bundle=I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlanuch.jar"));
        Vars.headless=true;Vars.loadLocales=false;Vars.net=new Net(null);Vars.platform=new mindustry.core.Platform(){};
        mindustry.core.Version.init();Vars.init();Vars.content.createBaseContent();Vars.content.createModContent();Vars.content.init();Vars.logic=new Logic();
        var mod=Vars.mods.locateMod("cryon");check(mod!=null&&!mod.hasContentErrors(),"Mod content errors");loader=mod.main.getClass().getClassLoader();
        progress=loader.loadClass("cryon.Story.StoryProgress");flag=loader.loadClass("cryon.Story.StoryProgress$Flag");
        conversation=loader.loadClass("cryon.Story.QuantumConversation");contact=loader.loadClass("cryon.Story.QuantumConversation$Contact");choice=loader.loadClass("cryon.Story.QuantumConversation$Choice");
        invoke("cryon.Features.CryonCampaignLayers","configure");invoke("cryon.TechTree.CryonTechTree","load");
        // The isolated headless fixture has no graphical loadout cache to refresh.
        Timer.instance().clear();
        invoke("cryon.Features.CryonCampaignLayers","shareResearch");invoke("cryon.Story.StoryProgress","install");
        for(String name:new String[]{"underground","actTwo","aravisMission"})check(!get(name),"Flag does not default to false: "+name);
        Planet porphyra=Vars.content.planet("cryon-porphyra"),star=Vars.content.planet("cryon-kyanos"),depths=Vars.content.planet("cryon-cryon-depths");
        check(!porphyra.visible&&!porphyra.accessible&&!porphyra.alwaysUnlocked&&!star.visible&&!depths.accessible,"Locked planets exposed");
        check(Vars.content.sector("cryon-depths-outpost").locked(),"First underground mission bypasses story lock");
        check(porphyra.parent==star&&porphyra.solarSystem==star&&porphyra.sectors.size>0,"Planet hierarchy: parent="+porphyra.parent+", system="+porphyra.solarSystem+", sectors="+porphyra.sectors.size);
        int gated=0;
        for(var node:TechTree.all){
            if((boolean)progress.getMethod("undergroundTechnology",String.class).invoke(null,node.content.name)){
                var objective=node.objectives.find(o->o.getClass().getSimpleName().equals("UndergroundObjective"));
                check(objective!=null&&!objective.complete(),"Underground research bypass: "+node.content.name);gated++;
            }
        }
        check(gated>=11,"Underground research branch incompletely gated: "+gated);
        for(String future:new String[]{"strait","archipelago","corridor","vents","nexus"}){
            var preset=Vars.content.sector("cryon-depths-"+future);
            check(preset!=null&&preset.techNode==null&&preset.sector.preset==null&&preset.generator.map!=null,"Future underground mission is exposed or its map was removed: "+future);
        }
        Block computer=Vars.content.block("cryon-quantum-communication-computer");
        check(computer.size==5&&computer.configurable&&computer.techNode!=null,"Missing 5x5 terminal or research");
        check(computer.techNode.objectives.contains(o->o instanceof Objectives.SectorComplete&&((Objectives.SectorComplete)o).preset==Vars.content.sector("cryon-relay-32a")),"Terminal is not gated by Relay 32A");
        check(((ConsumePower)computer.findConsumer(c->c instanceof ConsumePower)).usage==5f,"Wrong terminal power demand");
        Vars.state.rules=new Rules();Vars.world.loadGenerator(80,80,tiles->{for(int x=0;x<80;x++)for(int y=0;y<80;y++)tiles.set(x,y,new Tile(x,y,Blocks.sand,Blocks.air,Blocks.air));});
        Vars.world.tile(30,30).setBlock(computer,Team.sharded);Building terminal=Vars.world.build(30,30);computer.unlock();
        Method ready=terminal.getClass().getMethod("ready"),supply=terminal.getClass().getMethod("constructorStatus",float.class);
        check(!(boolean)ready.invoke(terminal),"Unpowered terminal is usable");terminal.configured(null,true);check(!get("underground"),"Unpowered terminal unlocked underground");
        terminal.power.status=1;tick(terminal);check(!(boolean)ready.invoke(terminal),"Terminal works without constructor energy");
        supply.invoke(terminal,0.5f);check(!(boolean)ready.invoke(terminal),"Partial constructor supply opens terminal");
        supply.invoke(terminal,1f);check((boolean)ready.invoke(terminal),"Fully supplied terminal stays locked");
        terminal.enabled=false;check(!(boolean)ready.invoke(terminal),"Disabled terminal remains usable");terminal.enabled=true;
        Vars.world.tile(22,30).setBlock(Vars.content.block("cryon-construct-wave-emitter"),Team.sharded);
        Vars.world.tile(25,30).setBlock(Vars.content.block("cryon-constructor-node"),Team.sharded);
        Building emitter=Vars.world.build(22,30),supplyNode=Vars.world.build(25,30);emitter.items.add(Items.phaseFabric,10);
        supply.invoke(terminal,0f);
        for(int i=0;i<5;i++){emitter.power.status=1;tick(emitter);tick(supplyNode);}
        check((boolean)ready.invoke(terminal),"Real constructor source/node cannot supply the computer");
        emitter.power.status=0;tick(emitter);tick(supplyNode);check(!(boolean)ready.invoke(terminal),"Failed constructor supply leaves terminal online");
        emitter.power.status=1;tick(emitter);tick(supplyNode);check((boolean)ready.invoke(terminal),"Restored constructor supply does not reconnect");
        supplyNode.tile.remove();check(!(boolean)ready.invoke(terminal),"Removing constructor node leaves stale supply");supply.invoke(terminal,1f);
        repeatContacts(terminal,data);
        for(String name:new String[]{"crux","malis"}){
            Object s=session(name);check(((Object[])call(s,"choices")).length==0,"Unexpected negotiation choices");call(s,"next");
            check(conversation.getField("ended").getBoolean(s)&&!get("underground"),"Hostile contact does not hang up");
        }
        Object early=session("pioneer");check(!choose(early,"agree"),"Can agree before hearing offer");
        call(early,"next");check(call(early,"npc").equals("pioneer.offer"),"Continue skips mandatory choice");
        choose(early,"refuse");check(call(early,"npc").equals("pioneer.reject")&&!get("underground"),"Early refusal unlocks technology");
        Object refusal=session("pioneer");choose(refusal,"listen");call(refusal,"next");choose(refusal,"refuse");
        check(call(refusal,"npc").equals("pioneer.reject")&&!get("underground"),"Final refusal unlocks technology");
        Object alliance=session("pioneer");choose(alliance,"listen");call(alliance,"next");check(choose(alliance,"agree"),"Final alliance rejected");
        terminal.configured(null,true);
        Object welcome=reconnect("pioneer");
        check(call(welcome,"npc").equals("pioneer.allied-return")&&call(welcome,"player").equals("player.silence")
                &&((Object[])call(welcome,"choices")).length==0,"Existing ally repeats alliance negotiation instead of welcoming Sharded");
        check(get("underground")&&depths.accessible&&!get("actTwo")&&!get("aravisMission"),"Agreement changed wrong flags");
        check(Vars.content.sector("cryon-depths-outpost").unlocked(),"Agreement did not open underground route");
        depths.getStartSector().info.wasCaptured=true;invoke("cryon.Features.CryonCampaignLayers","unlockRoute");
        check(Vars.content.sector("cryon-depths-strait").locked(),"Capturing the first mission exposes an unreleased mission");
        for(var node:TechTree.all)for(var objective:node.objectives)if(objective.getClass().getSimpleName().equals("UndergroundObjective"))check(objective.complete(),"Unlocked underground research remains gated");
        set("actTwo",true);check(porphyra.visible&&porphyra.accessible&&porphyra.alwaysUnlocked&&star.visible,"Act two does not open Porphyra");
        set("aravisMission",true);
        Core.settings=new Settings();Core.settings.setDataDirectory(data);Core.settings.load();
        for(String name:new String[]{"underground","actTwo","aravisMission"})check(get(name),"Global flag did not persist: "+name);
        check(call(reconnect("pioneer"),"npc").equals("pioneer.allied-return"),"Alliance greeting lost after settings reload");
        Vars.logic.reset();invoke("cryon.Story.StoryProgress","publish");check(get("underground")&&get("actTwo"),"World reset erased global progress");
        set("actTwo",false);check(!porphyra.visible&&!porphyra.accessible&&!porphyra.alwaysUnlocked&&!star.visible,"Porphyra fails to relock");
        for(Locale locale:new Locale[]{Locale.ENGLISH,Locale.SIMPLIFIED_CHINESE}){
            var bundle=I18NBundle.createBundle(new Fi("bundles/bundle"),locale);
            for(String key:new String[]{"malis.reconnect","pioneer.allied-return","pioneer.refused-return"}){
                check(bundle.has("cryon.dialogue."+key),"Missing localized repeat dialogue: "+key);
            }
            for(String who:new String[]{"crux","malis","pioneer"}){
                Object s=session(who);check(bundle.has("cryon.dialogue."+call(s,"npc")),"Missing localized dialogue");
                if(who.equals("pioneer")){
                    choose(s,"listen");check(bundle.has("cryon.dialogue."+call(s,"npc")),"Missing offer explanation");call(s,"next");
                    check(bundle.has("cryon.dialogue."+call(s,"npc")),"Missing final choice text");choose(s,"agree");check(bundle.has("cryon.dialogue."+call(s,"npc")),"Missing agreement text");
                }
            }
        }
        System.out.println("QUANTUM_STORY_PASS: false defaults, persistent global flags, gated underground/Porphyra, Relay 32A research, power/constructor checks, every dialogue choice and EN/ZH text");
    }

    static void repeatContacts(Building terminal,Fi data)throws Exception{
        check(call(reconnect("crux"),"npc").equals("crux.enemy"),"Fresh Crux call skips first dialogue");
        terminal.power.status=0;terminal.configured(null,0);
        check(call(reconnect("crux"),"npc").equals("crux.enemy"),"Offline call was recorded as established");terminal.power.status=1;
        terminal.configured(null,0);Object crux=reconnect("crux");
        check(conversation.getField("ended").getBoolean(crux)&&call(crux,"npc").equals("")&&call(crux,"player").equals(""),"Crux does not immediately hang up on repeat contact");
        check(call(reconnect("malis"),"npc").equals("malis.negotiate"),"Fresh Malis call uses repeat greeting");
        terminal.configured(null,1);Object malis=reconnect("malis");
        check(call(malis,"npc").equals("malis.reconnect")&&call(malis,"player").equals("player.silence"),"Malis repeat round is wrong");
        check(call(reconnect("pioneer"),"npc").equals("pioneer.offer"),"Fresh Pioneer call skips the offer");terminal.configured(null,2);
        terminal.configured(null,3);Object reconsider=reconnect("pioneer");
        check(call(reconsider,"npc").equals("pioneer.refused-return")&&((Object[])call(reconsider,"choices")).length==2
                &&!(boolean)call(reconsider,"lastRound"),"Rejected alliance cannot be reconsidered");
        call(reconsider,"next");check(call(reconsider,"npc").equals("pioneer.refused-return"),"Continue skips reconsideration choice");
        choose(reconsider,"refuse");check(call(reconsider,"npc").equals("pioneer.reject")&&!get("underground"),"Repeat refusal unlocks underground");
        Object changedMind=reconnect("pioneer");check(choose(changedMind,"agree"),"Cannot agree after a prior refusal");
        check(!get("underground"),"Model choice bypasses supplied building configuration");
        Core.settings=new Settings();Core.settings.setDataDirectory(data);Core.settings.load();
        check(conversation.getField("ended").getBoolean(reconnect("crux"))&&call(reconnect("malis"),"npc").equals("malis.reconnect")
                &&call(reconnect("pioneer"),"npc").equals("pioneer.refused-return"),"Contact/refusal history did not survive settings reload");
        System.out.println("QUANTUM_REPEAT_PASS: immediate Crux hangup, Malis return, refusal reconsideration, offline protection and persistent contact history");
    }
}
