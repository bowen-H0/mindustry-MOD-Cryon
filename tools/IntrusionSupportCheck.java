import arc.util.Time;
import cryon.Script.*;
import cryon.Story.IntrusionContent;
import cryon.Type.JavaProcessor.JavaProcessorBuild;
import mindustry.Vars;
import mindustry.gen.Groups;
import mindustry.gen.Unit;

/** Half-health rage, retired orbital support, real attack escalation and persistence. */
public final class IntrusionSupportCheck {
    static void check(boolean ok, String message){ IntrusionAttackCheck.check(ok, message); }
    public static void main(String[] args){
        try{
            IntrusionMapCheck.run(new String[]{"/tmp/cryon-native-map-check/rage-map.msav"});
            ScriptRegistry.register("orbital_strike", "Orbital", OrbitalStrikeScript::new);
            JavaProcessorBuild controller = (JavaProcessorBuild)Vars.world.build(56, 7);
            VirusIntrusionScript script = (VirusIntrusionScript)controller.script;
            IntrusionAttackCheck.clock(script, VirusIntrusionScript.introTicks + 10f);
            Unit boss = script.virusNode();
            Unit initialPlayer = script.controlledPlayer();
            check(initialPlayer.type == mindustry.content.UnitTypes.gamma, "player must start as Gamma");
            check(initialPlayer.maxHealth == 300f, "Gamma health changed");
            check(initialPlayer.hitSize == 6f && boss.maxHealth == 10000f, "encounter collision/health balance missing");
            initialPlayer.health = 150f;
            boss.health = boss.maxHealth * 0.51f;
            controller.updateTile();
            check(!script.enraged(), "rage triggered above half health");
            boss.health = boss.maxHealth * 0.5f;
            Groups.bullet.clear();
            controller.updateTile();
            check(script.transforming() && !script.enraged(), "half health skipped transformation dialogue");
            check(script.controlledPlayer().type == mindustry.content.UnitTypes.gamma, "player upgraded before dialogue");
            float pausedClock = script.seconds();
            for(int i = 0; i < (int)VirusIntrusionScript.transformTicks; i++) controller.updateTile();
            check(!script.transforming() && Math.abs(script.seconds() - pausedClock) < 0.02f,
                    "dialogue did not pause the combat countdown");
            float rageStart = script.seconds() * 60f;
            check(script.enraged() && script.bossStage() == 2 && script.phase() == 0, "half health did not start stage two");
            check(script.controlledPlayer().type == VirusIntrusionScript.playerUnitType(), "half health did not upgrade player");
            check(script.controlledPlayer().maxHealth == VirusIntrusionScript.upgradedHealth, "encounter health limit missing");
            check(script.controlledPlayer().hitSize == 6f, "upgraded hitbox was not reduced");
            check(script.controlledPlayer().abilities.length == 0 && script.controlledPlayer().shield == 0f
                    && script.controlledPlayer().armor == 0f, "defensive abilities survived encounter limits");
            check(VirusIntrusionScript.playerUnitType().health == 680f
                    && VirusIntrusionScript.playerUnitType().abilities.size == 3, "campaign unit definition was mutated");
            check(Math.abs(script.controlledPlayer().health / VirusIntrusionScript.upgradedHealth - initialPlayer.health / 300f) < 0.001f, "upgrade lost health fraction");
            check(!initialPlayer.isAdded() && Vars.player.unit() == script.controlledPlayer(), "player control not transferred");
            check(Vars.world.build(56, 30) == null, "orbital support still deployed");
            check(Groups.bullet.size() == 3, "spiral needs three arms");
            for(int i = 0; i < 12; i++) controller.updateTile();
            check(Groups.bullet.size() == 6, "spiral cadence must be twelve ticks");
            check(Groups.bullet.contains(bullet -> IntrusionAttackCheck.distance(bullet.rotation(), 16f) < 0.01f),
                    "spiral arms did not rotate");
            Groups.bullet.each(bullet -> check(Math.abs(bullet.vel.len() - 2.3f) < 0.01f, "spiral speed"));
            IntrusionAttackCheck.clock(script, rageStart + VirusIntrusionScript.rageModeTicks);
            controller.updateTile();
            check(script.phase() == 1 && Groups.bullet.size() == 12, "ring needs twelve shots and a gap");
            float gap = arc.math.Angles.angle(cryon.Story.IntrusionMap.nodeX, cryon.Story.IntrusionMap.nodeY,
                    script.controlledPlayer().x, script.controlledPlayer().y);
            Groups.bullet.each(bullet -> check(IntrusionAttackCheck.distance(bullet.rotation(), gap) >= 47.9f, "ring blocked safe gap"));
            IntrusionAttackCheck.clock(script, rageStart + VirusIntrusionScript.rageModeTicks * 2f);
            controller.updateTile();
            check(script.phase() == 2 && Groups.bullet.isEmpty(), "ring laser fired before warning");
            float locked = Float.parseFloat(Vars.state.rules.tags.get("cryon-intrusion-warning-angle"));
            script.controlledPlayer().set(cryon.Story.IntrusionMap.nodeX + 150f, cryon.Story.IntrusionMap.nodeY);
            for(int i = 0; i < 41; i++) controller.updateTile();
            check(Groups.bullet.isEmpty(), "ring warning shorter than forty-two ticks");
            controller.updateTile();
            check(Groups.bullet.count(b -> b.type == IntrusionContent.ringLasers[0]) == 26,
                    "first native laser ring must contain twenty-six segments with a gap");
            check(IntrusionContent.ringLasers[0].getClass() == mindustry.entities.bullet.LaserBulletType.class,
                    "laser uses a custom implementation");
            for(int i = 0; i < 32; i++) controller.updateTile();
            check(Groups.bullet.count(b -> b.type == IntrusionContent.ringLasers[1]) == 26, "second native ring missing");
            for(int i = 0; i < 32; i++) controller.updateTile();
            check(Groups.bullet.count(b -> b.type == IntrusionContent.ringLasers[2]) == 26, "third native ring missing");
            Unit target = script.controlledPlayer();
            target.unapply(mindustry.content.StatusEffects.invincible);
            target.set(cryon.Story.IntrusionMap.nodeX + 150f, cryon.Story.IntrusionMap.nodeY);
            Groups.unit.updatePhysics();
            Vars.state.teams.updateTeamStats();
            float beforeBeam = target.health;
            IntrusionContent.ringLasers[0].create(controller, Vars.state.rules.waveTeam, target.x - 10f, target.y, 0f);
            check(target.health < beforeBeam, "native laser line collision did not damage player");
            IntrusionAttackCheck.clock(script, rageStart + VirusIntrusionScript.rageModeTicks * 3f);
            controller.updateTile();
            check(script.phase() == 3 && Groups.bullet.isEmpty(), "chain must have its own warned fourth mode");
            for(int i = 0; i < 42; i++) controller.updateTile();
            check(Groups.bullet.contains(b -> b.type == IntrusionContent.chainLightning), "native lightning did not spawn");
            check(IntrusionContent.chainLightning.getClass() == mindustry.entities.bullet.LightningBulletType.class,
                    "lightning uses a custom implementation");
            check(Groups.bullet.size() > 1, "native lightning did not create engine damage segments");
            target.set(cryon.Story.IntrusionMap.playerX, cryon.Story.IntrusionMap.playerY);
            // Save during a new warning and verify the attack resumes from the saved schedule.
            for(int i = 0; i < 84; i++) controller.updateTile();
            check(Vars.state.rules.tags.containsKey("cryon-intrusion-warning-angle"), "next warning missing");
            // Stage two stays latched even if another effect heals the Boss.
            boss.health = boss.maxHealth;
            controller.updateTile();
            check(script.enraged(), "rage reverted after healing");
            // Clean up the support controller left in old saves.
            Vars.world.tile(56, 30).setBlock(cryon.CryonJavaMod.javaProcessor, Vars.state.rules.defaultTeam);
            ((JavaProcessorBuild)Vars.world.build(56, 30)).setScript("orbital_strike");
            controller.updateTile();
            check(Vars.world.build(56, 30) == null, "legacy orbital support survived");
            arc.files.Fi save = new arc.files.Fi("/tmp/cryon-native-map-check/rage-save.msav");
            mindustry.io.SaveIO.write(save);
            mindustry.io.SaveIO.load(save);
            controller = (JavaProcessorBuild)Vars.world.build(56, 7);
            script = (VirusIntrusionScript)controller.script;
            Time.delta = 1f;
            controller.updateTile();
            check(script.enraged() && script.phase() == 3, "rage stage/mode lost on reload");
            check(Vars.state.rules.tags.containsKey("cryon-intrusion-warning-angle"), "warning lost on reload");
            check(script.controlledPlayer().type == VirusIntrusionScript.playerUnitType(), "player type lost on reload");
            Unit upgraded = script.controlledPlayer();
            check(upgraded.maxHealth == VirusIntrusionScript.upgradedHealth && upgraded.abilities.length == 0,
                    "encounter limits lost after reload");
            upgraded.health = 100f;
            for(int i = 0; i < 60; i++) controller.updateTile();
            check(Math.abs(upgraded.health - 102f) < 0.02f, "stage-two healing is not two HP/sec");
            check(Vars.state.rules.teams.get(Vars.state.rules.defaultTeam).unitDamageMultiplier == 0.8f,
                    "stage-two damage reduction missing");
            int upgradedId = upgraded.id;
            controller.updateTile();
            check(script.controlledPlayer().id == upgradedId, "upgrade repeated after reload");
            upgraded.unapply(mindustry.content.StatusEffects.disarmed);
            upgraded.rotation = 90f;
            upgraded.aim(script.virusNode());
            upgraded.controlWeapons(true, true);
            Groups.bullet.clear();
            for(int i = 0; i < 30; i++){
                for(mindustry.entities.units.WeaponMount mount : upgraded.mounts) mount.weapon.update(upgraded, mount);
                Time.update();
            }
            check(Groups.bullet.contains(b -> b.team == Vars.state.rules.defaultTeam), "upgraded native weapons cannot fire");
            System.out.println("PASS: Transliminal, half-health rage, rotating spiral, safe-gap ring, native laser rings/line damage, native lightning segments, persistent stage/mode, retired support cleanup");
            System.exit(0);
        }catch(Throwable error){ error.printStackTrace(); System.exit(1); }
    }
    private static float scriptSpeed(){ return VirusIntrusionScript.playerUnitType().speed; }
}
