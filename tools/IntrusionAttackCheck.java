import arc.math.Angles;
import arc.util.Time;
import cryon.Script.VirusIntrusionScript;
import cryon.Story.IntrusionContent;
import cryon.Story.IntrusionMap;
import cryon.Type.JavaProcessor.JavaProcessorBuild;
import mindustry.Vars;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Unit;

/** Native-engine regression checks for attack geometry, cadence, homing and persistence. */
public final class IntrusionAttackCheck {
    static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }
    static void clock(VirusIntrusionScript script, float ticks) throws Exception {
        java.lang.reflect.Field time = VirusIntrusionScript.class.getDeclaredField("time");
        java.lang.reflect.Field timer = VirusIntrusionScript.class.getDeclaredField("shootTimer");
        java.lang.reflect.Field volley = VirusIntrusionScript.class.getDeclaredField("volley");
        time.setAccessible(true); timer.setAccessible(true); volley.setAccessible(true);
        time.setFloat(script, ticks); timer.setFloat(script, 0); volley.setInt(script, 0);
        Time.delta = 1f;
        Groups.bullet.clear();
    }
    static float distance(float a, float b){
        return Math.abs(((a - b + 540f) % 360f) - 180f);
    }
    public static void main(String[] args){
        try{
            IntrusionMapCheck.run(new String[]{"/tmp/cryon-native-map-check/attacks.msav"});
            JavaProcessorBuild processor = (JavaProcessorBuild)Vars.world.build(56, 7);
            VirusIntrusionScript script = (VirusIntrusionScript)processor.script;
            Unit gamma = Vars.player.unit();
            float aim = Angles.angle(IntrusionMap.nodeX, IntrusionMap.nodeY, gamma.x, gamma.y);
            clock(script, VirusIntrusionScript.introTicks);
            for(int tick = 0; tick < 28; tick++) processor.updateTile();
            check(Groups.bullet.size() == 3, "mode 1 must fire exactly three consecutive shots");
            Groups.bullet.each(b -> check(distance(b.rotation(), aim) <= 3.01f, "mode 1 spread exceeds three degrees"));
            for(int tick = 0; tick < 40; tick++) processor.updateTile();
            check(Groups.bullet.size() == 3, "mode 1 lacks a pause between bursts");
            clock(script, VirusIntrusionScript.introTicks + VirusIntrusionScript.attackModeTicks);
            processor.updateTile();
            check(script.phase() == 1 && Groups.bullet.size() == 7, "mode 2 is not a sparse seven-shot fan");
            final int[] index = {0};
            Groups.bullet.each(b -> {
                float expected = aim - 90f + index[0]++ * 30f;
                check(distance(b.rotation(), expected) < 0.1f, "mode 2 angle: actual=" + b.rotation() + " expected=" + expected);
            });
            clock(script, VirusIntrusionScript.introTicks + VirusIntrusionScript.attackModeTicks * 2f);
            processor.updateTile();
            check(script.phase() == 2 && Groups.bullet.size() == 1, "mode 3 must fire one seeker");
            Bullet seeker = Groups.bullet.first();
            check(seeker.type == IntrusionContent.seeker && seeker.vel.len() < gamma.type.speed, "seeker must be slower than Gamma");
            gamma.set(IntrusionMap.nodeX + 200f, IntrusionMap.nodeY - 150f);
            float targetAngle = seeker.angleTo(gamma);
            float before = seeker.vel.angle();
            seeker.time = 0f;
            seeker.type.updateHoming(seeker);
            check(distance(before, seeker.vel.angle()) < 0.01f, "seeker ignores its initial homing delay");
            seeker.time = 20f;
            seeker.type.updateHoming(seeker);
            check(distance(seeker.vel.angle(), targetAngle) < distance(before, targetAngle), "seeker does not turn toward Gamma");
            check(seeker.vel.len() < gamma.type.speed, "homing increases speed past Gamma");
            arc.files.Fi save = new arc.files.Fi("/tmp/cryon-native-map-check/seeker-roundtrip.msav");
            mindustry.io.SaveIO.write(save);
            mindustry.io.SaveIO.load(save);
            processor = (JavaProcessorBuild)Vars.world.build(56, 7);
            script = (VirusIntrusionScript)processor.script;
            check(script.phase() == 2, "attack mode lost on save load");
            // Mindustry does not save transient bullets; verify the restored firing schedule instead.
            Time.delta = 1f;
            for(int tick = 0; tick < 120; tick++) processor.updateTile();
            check(Groups.bullet.contains(b -> b.type == IntrusionContent.seeker), "restored mode does not resume seeker fire");
            for(int mode = 0; mode < 7; mode++){
                clock(script, VirusIntrusionScript.introTicks + mode * VirusIntrusionScript.attackModeTicks);
                check(script.phase() == mode % 3, "attack modes do not cycle 1-2-3-1");
            }
            System.out.println("PASS: three-shot burst/spread/pause, exact 180-degree sparse fan, slower Gamma-targeted homing, mode cycle and save/load");
            System.exit(0);
        }catch(Throwable error){ error.printStackTrace(); System.exit(1); }
    }
}
