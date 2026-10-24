import arc.Core;
import arc.Settings;
import arc.files.Fi;
import arc.mock.*;
import arc.util.I18NBundle;
import arc.util.Time;
import arc.util.io.*;
import cryon.Content.CryonItems;
import cryon.Script.NukeStrikeScript;
import cryon.Type.*;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.net.Net;
import mindustry.type.*;
import java.io.*;
import java.util.Arrays;

/** Headless regression checks for strategic interception, supply gating and script saves. */
public class NuclearArbiterCheck{
    static void check(boolean condition, String message){if(!condition) throw new AssertionError(message);}
    public static void main(String[] args){
        try{run();System.exit(0);}catch(Throwable error){error.printStackTrace();System.exit(1);}
    }
    static NuclearArbiter type;
    static JavaProcessor.JavaProcessorBuild processor;
    static NuclearArbiter.NuclearArbiterBuild silo, second;
    static NukeStrikeScript strike(){
        NukeStrikeScript nuke = new NukeStrikeScript();
        nuke.attach(processor);
        processor.script = nuke;
        nuke.phase = NukeStrikeScript.FALL;
        nuke.tx = 800f; nuke.ty = 800f; nuke.blastRadius = 240f;
        return nuke;
    }
    static NukeStrikeScript midflight(){
        NukeStrikeScript nuke=strike();nuke.fallTimer=110f;return nuke;
    }
    static void tick(NukeStrikeScript nuke){
        silo.updateTile();second.updateTile();nuke.update();
    }
    static void supply(NuclearArbiter.NuclearArbiterBuild b){
        b.items.clear();
        b.items.add(Items.silicon,160);b.items.add(Items.surgeAlloy,80);
        b.items.add(Items.phaseFabric,40);b.items.add(CryonItems.neutronite,60);
        b.power.status=1f;b.enabled=true;b.charge=type.chargeTime;
        b.updateConsumption();
    }
    static byte[] save(NukeStrikeScript nuke)throws Exception{
        ByteArrayOutputStream data=new ByteArrayOutputStream();
        nuke.write(new Writes(new DataOutputStream(data)));return data.toByteArray();
    }
    static NukeStrikeScript load(byte[] data){
        NukeStrikeScript nuke=new NukeStrikeScript();nuke.attach(processor);
        nuke.read(new Reads(new DataInputStream(new ByteArrayInputStream(data))));return nuke;
    }
    static void run()throws Exception{
        Core.app=new MockApplication();Core.files=new MockFiles();Core.graphics=new MockGraphics();Core.audio=new MockAudio();
        Core.settings=new Settings();Core.settings.setDataDirectory(new Fi("/tmp/cryon-anti-nuke-check"));
        Core.bundle=I18NBundle.createEmptyBundle();
        Vars.headless=true;Vars.loadLocales=false;Vars.net=new Net(null);
        Vars.platform=new mindustry.core.Platform(){};
        Vars.init();Vars.content.createBaseContent();CryonItems.load();
        type=new NuclearArbiter("test-disarmament");JavaProcessor jp=new JavaProcessor("test-jp");
        Vars.content.init();Vars.logic=new Logic();Time.delta=1f;
        Vars.world.loadGenerator(256,256,tiles->{tiles.fill();for(var tile:tiles) tile.setFloor(Blocks.sand.asFloor());});
        Vars.world.tile(20,20).setBlock(jp,Team.crux);
        processor=(JavaProcessor.JavaProcessorBuild)Vars.world.tile(20,20).build;
        Vars.world.tile(96,100).setBlock(type,Team.sharded);
        silo=(NuclearArbiter.NuclearArbiterBuild)Vars.world.tile(96,100).build;
        Vars.world.tile(104,100).setBlock(type,Team.sharded);
        second=(NuclearArbiter.NuclearArbiterBuild)Vars.world.tile(104,100).build;
        Vars.state.rules.unitCap=100;
        check(type.size==5 && type.consPower.usage==60f,"Wrong footprint/power demand");
        supply(silo);supply(second);
        Unit victim=UnitTypes.dagger.create(Team.sharded);victim.set(800f,800f);victim.add();float health=victim.health;
        check(victim.isAdded() && !victim.dead,"Test victim was not spawned alive");
        Vars.world.tile(101,101).setBlock(Blocks.copperWall,Team.sharded);
        Building protectedWall=Vars.world.tile(101,101).build;
        float wallHealth=protectedWall.health;
        NukeStrikeScript nuke=strike();
        nuke.update();
        check(silo.door==0f && silo.launchHold>0f,"Hatch should start opening, not jump open");
        while(nuke.fallTimer < nuke.fallTime * (float)Math.sqrt(0.5f)) tick(nuke);
        check(silo.door>0.95f,"Hatch did not fully open while tracking the threat");
        check(!nuke.intercepting && silo.items.total()>0 && second.items.total()>0,
            "Silo launched before the missile completed half its descent");
        nuke.update();
        check(nuke.intercepting,"Ready silo did not launch");
        check((silo.items.total()==0) != (second.items.total()==0),"Expected exactly one ammunition expenditure");
        check((silo.charge==0f) != (second.charge==0f),"Expected exactly one charging cycle reset");
        byte[] flight=save(nuke);NukeStrikeScript loaded=load(flight);
        check(loaded.intercepting && loaded.fallTimer==nuke.fallTimer,"In-flight state lost on save/load");
        NukeStrikeScript legacy=load(Arrays.copyOf(flight,flight.length-14));
        check(!legacy.intercepting && legacy.phase==NukeStrikeScript.FALL,"Legacy save incompatible");
        for(int i=0;i<300;i++) tick(nuke);
        check(nuke.phase==NukeStrikeScript.DONE && !nuke.alive,"Intercepted strike did not terminate");
        check(victim.health==health && !victim.dead,"Neutralization damaged a unit: health="+victim.health+" before="+health+" dead="+victim.dead);
        check(protectedWall.isValid() && protectedWall.health==wallHealth,"Neutralization damaged a building");
        check(nuke.blastTimer==0f && nuke.waveR==0f,"Neutralization entered nuclear blast flow");
        second.enabled=false;silo.enabled=true;
        supply(silo);silo.items.clear();nuke=midflight();nuke.update();
        check(!nuke.intercepting,"Missing ammunition allowed interception");
        supply(silo);silo.power.status=0f;silo.updateConsumption();nuke=midflight();nuke.update();
        check(!nuke.intercepting,"Unpowered silo intercepted");
        supply(silo);silo.charge=0f;nuke=midflight();nuke.update();
        check(!nuke.intercepting,"Uncharged silo intercepted");
        supply(silo);nuke=midflight();nuke.tx=1800f;nuke.update();
        check(!nuke.intercepting,"Silo intercepted outside coverage");
        supply(silo);nuke=midflight();nuke.build.team=Team.sharded;nuke.update();
        check(!nuke.intercepting,"Silo intercepted its own team's missile");
        processor.team=Team.crux;silo.enabled=false;nuke=midflight();
        for(int i=0;i<150;i++)nuke.update();
        check(nuke.phase==NukeStrikeScript.BLAST,"Unprotected nuke no longer detonates");
        System.out.println("PASS: interception/no nuclear damage; single ammo consumption; power/ammo/charge/range/team gates; legacy and in-flight saves; normal detonation.");
    }
}
