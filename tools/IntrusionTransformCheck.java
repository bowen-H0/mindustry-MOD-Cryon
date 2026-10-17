import arc.files.Fi;
import arc.util.Time;
import cryon.Script.VirusIntrusionScript;
import cryon.Story.IntrusionContent;
import cryon.Type.JavaProcessor.JavaProcessorBuild;
import mindustry.Vars;
import mindustry.content.StatusEffects;
import mindustry.content.UnitTypes;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.io.SaveIO;

/** Transition freeze, paused battle clock and mid-dialogue save/load. */
public class IntrusionTransformCheck {
    static void check(boolean ok, String message){ IntrusionAttackCheck.check(ok, message); }
    public static void main(String[] args){
        try{
            IntrusionMapCheck.run(new String[]{"/tmp/cryon-native-map-check/transform-map.msav"});
            JavaProcessorBuild controller = (JavaProcessorBuild)Vars.world.build(56, 7);
            VirusIntrusionScript script = (VirusIntrusionScript)controller.script;
            IntrusionAttackCheck.clock(script, VirusIntrusionScript.introTicks + 30f);
            Unit pilot = script.controlledPlayer(), boss = script.virusNode();
            boss.health = boss.maxHealth / 2f;
            float x = pilot.x, y = pilot.y;
            float battleTime = script.seconds(), remaining = script.remainingSeconds();
            controller.updateTile();
            check(script.transforming() && !script.enraged(), "dialogue did not begin before rage");
            for(int i = 0; i < 120; i++){
                pilot.set(x + 80f, y + 50f);
                pilot.vel.set(2f, 3f);
                IntrusionContent.packet.create(controller, Vars.state.rules.waveTeam, x, y, 0f);
                controller.updateTile();
                check(Groups.bullet.isEmpty() && pilot.x == x && pilot.y == y && pilot.vel.isZero(), "combat moved during dialogue");
            }
            check(script.seconds() == battleTime && script.remainingSeconds() == remaining, "combat timer advanced during dialogue");
            check(pilot.type == UnitTypes.gamma && pilot.hasEffect(StatusEffects.disarmed)
                    && pilot.hasEffect(StatusEffects.invincible), "pilot changed or lost protection during dialogue");
            float elapsed = script.transformSeconds();
            Fi save = new Fi("/tmp/cryon-native-map-check/transform-save.msav");
            SaveIO.write(save);
            SaveIO.load(save);
            controller = (JavaProcessorBuild)Vars.world.build(56, 7);
            script = (VirusIntrusionScript)controller.script;
            Time.delta = 1f;
            check(script.transforming() && script.transformSeconds() == elapsed, "dialogue progress lost on reload");
            while(script.transforming()) controller.updateTile();
            check(script.enraged() && script.seconds() == battleTime, "transition consumed combat time");
            controller.updateTile();
            pilot = script.controlledPlayer();
            check(pilot.type == VirusIntrusionScript.playerUnitType() && !pilot.hasEffect(StatusEffects.disarmed)
                    && !pilot.hasEffect(StatusEffects.unmoving) && pilot.hasEffect(StatusEffects.invincible), "combat did not resume cleanly");
            check(Groups.bullet.size() == 3 && script.phase() == 0, "rage spiral did not begin after dialogue");
            float after = script.transformSeconds();
            controller.updateTile();
            check(!script.transforming() && after == 0f, "dialogue replayed after upgrade");
            System.out.println("PASS: dialogue freeze, paused countdown, no premature upgrade, mid-dialogue save/load, protected resume without replay");
            System.exit(0);
        }catch(Throwable error){ error.printStackTrace(); System.exit(1); }
    }
}
