import arc.util.Time;
import cryon.Script.*;
import cryon.Type.JavaProcessor.JavaProcessorBuild;
import mindustry.Vars;
import mindustry.gen.Unit;

/** Checks the actual processor's half-health unlock and orbital damage. */
public final class IntrusionSupportCheck {
    public static void main(String[] args){
        try{
            IntrusionMapCheck.run(new String[]{"/tmp/cryon-native-map-check/support-map.msav"});
            ScriptRegistry.register("orbital_strike", "Orbital", OrbitalStrikeScript::new);
            JavaProcessorBuild controller = (JavaProcessorBuild)Vars.world.build(56, 7);
            VirusIntrusionScript script = (VirusIntrusionScript)controller.script;
            IntrusionAttackCheck.clock(script, VirusIntrusionScript.introTicks + 10f);
            Unit boss = script.virusNode();
            boss.health = boss.maxHealth * 0.51f;
            controller.updateTile();
            IntrusionAttackCheck.check(!script.orbitalSupportActive(), "support unlocked before half health");
            boss.health = boss.maxHealth * 0.5f;
            controller.updateTile();
            IntrusionAttackCheck.check(script.orbitalSupportActive(), "support missing at half health");
            JavaProcessorBuild support = (JavaProcessorBuild)Vars.world.build(56, 30);
            IntrusionAttackCheck.check(support.team == Vars.state.rules.defaultTeam, "support is not allied");
            controller.updateTile();
            IntrusionAttackCheck.check(Vars.world.build(56, 30) == support, "duplicate support deployment");
            OrbitalStrikeScript orbital = (OrbitalStrikeScript)support.script;
            float health = boss.health, gammaHealth = script.controlledGamma().health;
            Time.delta = 1f;
            boss.unapply(mindustry.content.StatusEffects.invincible);
            mindustry.gen.Groups.unit.updatePhysics();
            for(int tick = 0; tick < 250; tick++) support.updateTile();
            IntrusionAttackCheck.check(boss.health < health, "orbital cannon did not damage boss");
            IntrusionAttackCheck.check(script.controlledGamma().health == gammaHealth, "orbital cannon damaged Gamma");
            arc.files.Fi save = new arc.files.Fi("/tmp/cryon-native-map-check/support-save.msav");
            mindustry.io.SaveIO.write(save);
            mindustry.io.SaveIO.load(save);
            controller = (JavaProcessorBuild)Vars.world.build(56, 7);
            script = (VirusIntrusionScript)controller.script;
            controller.updateTile();
            IntrusionAttackCheck.check(script.orbitalSupportActive(), "support lost on save/load");
            orbital = (OrbitalStrikeScript)((JavaProcessorBuild)Vars.world.build(56, 30)).script;
            IntrusionAttackCheck.check(orbital.range == 1000f && orbital.pulseDamage == 150f, "support parameters lost on save/load");
            System.out.println("PASS: half-health unlock, single allied processor, real orbital damage, Gamma unharmed, save/load support");
            System.exit(0);
        }catch(Throwable error){ error.printStackTrace(); System.exit(1); }
    }
}
