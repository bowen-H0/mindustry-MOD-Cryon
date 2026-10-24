import arc.util.Time;
import arc.util.io.*;
import cryon.Script.*;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.Team;
import java.io.*;

/** Integration checks for repeated salvos and independent, reusable nuclear strike state. */
public class NuclearCleansingCheck{
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        try{run();System.exit(0);}catch(Throwable error){error.printStackTrace();System.exit(1);}
    }
    static NuclearCleansingScript create(){
        NuclearCleansingScript script=new NuclearCleansingScript();
        script.attach(NuclearArbiterCheck.processor);
        NuclearArbiterCheck.processor.script=script;
        return script;
    }
    static void run()throws Exception{
        NuclearArbiterCheck.run();
        Vars.world.tile(210,210).setBlock(Blocks.copperWall,Team.sharded);
        Vars.world.tile(220,30).setBlock(Blocks.copperWall,Team.sharded);
        Vars.state.teams.updateTeamStats();
        NuclearCleansingScript script=create();
        script.configure("5,3,2,false,1,4,false,true");
        for(int i=0;i<299;i++)script.update();
        check(script.strikes.isEmpty(),"Salvo launched before interval elapsed");
        script.update();
        check(script.strikes.size==3,"Wrong missile count in fixed-power salvo");
        for(NukeStrikeScript strike:script.strikes){
            check(strike.power==2f && strike.phase==NukeStrikeScript.LOCK,"Fixed power/lock flow differs from single-strike implementation");
            check(!strike.friendlyFire && !strike.showStation && strike.simpleWarning,"Wrong friendly fire / duplicated station HUD");
        }
        check(script.strikes.get(0).tx!=script.strikes.get(1).tx
            || script.strikes.get(0).ty!=script.strikes.get(1).ty,"Salvo did not spread across available targets");
        for(int i=0;i<300;i++)script.update();
        check(script.strikes.size==6,"Second salvo did not overlap with first");
        script.configure("5,3,2,false,1,4,false,false");
        int firstId=script.strikes.first().strikeId;
        NukeStrikeScript first=script.findStrike(firstId), other=script.strikes.get(1);
        first.phase=NukeStrikeScript.FALL;other.phase=NukeStrikeScript.FALL;
        first.beginInterception(100f,100f);first.intercepted(first.tx,first.missileY());
        check(first.phase==NukeStrikeScript.DONE && other.phase==NukeStrikeScript.FALL,"Intercepting one missile canceled its peers");
        int sequence=script.nextStrikeId;
        for(int i=0;i<300;i++)script.update();
        check(script.nextStrikeId==sequence,"Stopped scheduler launched a new salvo");
        check(other.fallTimer>0f,"Stopping discarded already committed strikes");
        script=create();script.configure("30,12,3,true,1.5,4.5,true,true");
        check(script.launchSalvo()==12,"Random salvo count wrong");
        boolean varied=false;float power=script.strikes.first().power;
        for(NukeStrikeScript strike:script.strikes){
            check(strike.power>=1.5f && strike.power<=4.5f,"Random power out of configured bounds");
            varied|=strike.power!=power;
        }
        check(varied,"Random powers did not vary by missile");
        script.strikes.first().phase=NukeStrikeScript.FALL;
        script.strikes.first().fallTimer=17f;
        script.strikes.first().beginInterception(300f,300f);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        script.write(new Writes(new DataOutputStream(bytes)));
        NuclearCleansingScript restored=create();
        restored.read(new Reads(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        check(restored.strikes.size==12 && restored.running && restored.randomPower && restored.strikes.first().simpleWarning,"Saved salvo/settings lost");
        check(restored.findStrike(script.strikes.first().strikeId).intercepting
            && restored.strikes.first().fallTimer==17f,"In-flight child interception state lost");
        for(int i=0;i<8;i++)restored.launchSalvo();
        check(restored.strikes.size==NuclearCleansingScript.maxActive,"Concurrency cap not enforced");
        restored.removed();check(restored.strikes.isEmpty()&&!restored.running,"Removal left strikes running");
        // No enemies: retry later without accumulating waiting missiles.
        for(var data:Vars.state.teams.present) if(data.team!=Team.crux)
            for(var b:data.buildings.copy()) b.tile.setAir();
        Vars.state.teams.updateTeamStats();
        script=create();script.configure("5,12,2,false,1,4,true,true");
        for(int i=0;i<1200;i++)script.update();
        check(script.strikes.isEmpty()&&script.nextStrikeId==1,"Empty map accumulated or launched nukes");
        System.out.println("PASS: intervals, overlapping salvos, target spread, fixed/random power, independent interception, stop behavior, saves, capacity and empty-map retry.");
    }
}
