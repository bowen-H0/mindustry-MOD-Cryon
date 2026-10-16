import arc.util.Time;
import cryon.Script.VirusIntrusionScript;
import cryon.Type.JavaProcessor.JavaProcessorBuild;
import mindustry.Vars;
import mindustry.gen.Unit;

/** Native checks for the persistent 80%-health recovery unlock. */
public final class IntrusionRecoveryCheck {
    public static void main(String[] args){
        try{
            IntrusionMapCheck.run(new String[]{"/tmp/cryon-native-map-check/recovery-map.msav"});
            JavaProcessorBuild controller = (JavaProcessorBuild)Vars.world.build(56, 7);
            VirusIntrusionScript script = (VirusIntrusionScript)controller.script;
            IntrusionAttackCheck.clock(script, VirusIntrusionScript.introTicks + 10f);
            Unit gamma = script.controlledGamma(), boss = script.virusNode();
            gamma.health = 150f;
            boss.health = boss.maxHealth * 0.81f;
            controller.updateTile();
            IntrusionAttackCheck.check(!script.recoveryActive() && gamma.health == 150f, "recovery unlocked early");
            boss.health = boss.maxHealth * 0.8f;
            Time.delta = 1f;
            for(int tick = 0; tick < 60; tick++) controller.updateTile();
            IntrusionAttackCheck.check(script.recoveryActive() && Math.abs(gamma.health - 156f) < 0.01f, "expected six health per second");
            boss.health = boss.maxHealth;
            arc.files.Fi save = new arc.files.Fi("/tmp/cryon-native-map-check/recovery-save.msav");
            mindustry.io.SaveIO.write(save);
            mindustry.io.SaveIO.load(save);
            controller = (JavaProcessorBuild)Vars.world.build(56, 7);
            script = (VirusIntrusionScript)controller.script;
            controller.updateTile();
            IntrusionAttackCheck.check(script.recoveryActive(), "unlock lost after save/load or boss healing");
            gamma = script.controlledGamma();
            gamma.health = 299.99f;
            controller.updateTile();
            IntrusionAttackCheck.check(gamma.health == 300f, "recovery exceeded max health");
            gamma.health = 0f;
            controller.updateTile();
            IntrusionAttackCheck.check(script.finished() && !script.won(), "recovery revived dead Gamma");
            System.out.println("PASS: 80% unlock, 6 HP/sec, persistent unlock/save-load, max health cap, no resurrection");
            System.exit(0);
        }catch(Throwable error){ error.printStackTrace(); System.exit(1); }
    }
}
