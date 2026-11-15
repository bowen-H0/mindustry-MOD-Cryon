import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.*;
import cryon.Type.AbyssLightningBulletType;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.net.Net;
import mindustry.world.*;

/** Real damage and exact field/chain timing without a desktop renderer. */
public class AbyssLightningCheck{
    static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }
    static Unit target(float x, float y, boolean air, Team team){
        Unit u = (air ? UnitTypes.flare : UnitTypes.dagger).create(team);
        u.set(x, y); u.health = u.maxHealth = 100000f; u.add();
        team.data().tree().insert(u);
        return u;
    }
    public static void main(String[] args){
        Core.app = new MockApplication(); Core.files = new MockFiles();
        Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Core.settings = new Settings(); Core.settings.setDataDirectory(new Fi("/tmp/abyss-lightning-check"));
        Core.bundle = I18NBundle.createEmptyBundle();
        Vars.headless = true; Vars.loadLocales = false; Vars.net = new Net(null);
        Vars.platform = new mindustry.core.Platform(){};
        mindustry.core.Version.init(); Vars.init(); Vars.content.createBaseContent();
        AbyssLightningBulletType abyss = new AbyssLightningBulletType();
        AbyssLightningBulletType themis = new AbyssLightningBulletType(true);
        Vars.content.init(); Vars.logic = new Logic();
        runProfile(abyss); runProfile(themis);
        check(abyss.damage == 360f && abyss.field.damagePerSecond == 150f && abyss.field.blastDamage == 300f, "Abyss nerf regressed");
        check(abyss.dischargeInterval / abyss.dischargeCount >= themis.dischargeInterval / themis.dischargeCount * 10f,
            "Abyss flight density is not substantially reduced");
        check(abyss.field.pulseBranches / abyss.field.pulseInterval < themis.field.pulseBranches / themis.field.pulseInterval / 10f,
            "Abyss field density is not substantially reduced");
        check(abyss.field.nodeCount == 9 && abyss.field.blastBranches == 0, "Abyss chain density nerf missing");
        check(themis.damage == 1600f && themis.splashDamage == 1200f && themis.dischargeDamage == 120f
            && themis.field.damagePerSecond == 1000f && themis.field.blastDamage == 2100f && themis.field.finalDamage == 4500f,
            "Themis 50-percent damage reduction regressed");
        System.out.println("PASS: Abyss major damage/density nerf; Themis complete-form strength; both profiles sustain ground/air/building damage, protect allies, and detonate exactly after five seconds");
        System.exit(0);
    }
    static void runProfile(AbyssLightningBulletType shot){
        Vars.logic.reset(); Time.time = 0f;
        Vars.state.rules = new Rules(); Vars.state.rules.unitCap = 100;
        Vars.state.rules.tags.put("cryon-allied-teams", "4");
        Vars.world.loadGenerator(100, 100, tiles -> {
            for(int x = 0; x < 100; x++) for(int y = 0; y < 100; y++)
                tiles.set(x, y, new Tile(x, y, Blocks.sand, Blocks.air, Blocks.air));
        });
        Vars.world.tile(51, 50).setBlock(Blocks.thoriumWallLarge, Team.crux);
        Vars.world.tile(48, 50).setBlock(Blocks.thoriumWallLarge, Team.green);
        Building wall = Vars.world.build(51, 50), allyWall = Vars.world.build(48, 50);
        wall.health = 100000f;
        float wallHp = wall.health, allyWallHp = allyWall.health;
        Unit ground = target(400, 400, false, Team.crux), air = target(430, 400, true, Team.crux);
        Unit edge = target(540, 400, false, Team.crux), far = target(740, 400, false, Team.crux);
        Unit friend = target(410, 400, false, Team.sharded), ally = target(420, 400, false, Team.green);
        Vars.state.teams.updateTeamStats(); Time.delta = 1f;
        Bullet projectile = shot.create((Entityc)null, Team.sharded, 400, 400, 0);
        shot.hit(projectile, 400, 400); shot.despawned(projectile);
        check(Groups.bullet.count(b -> b.type == shot.field) == 1, "impact spawned duplicate fields");
        Bullet field = Groups.bullet.find(b -> b.type == shot.field);
        for(int tick = 0; tick < 300; tick++){
            Time.time = tick; field.time = tick; shot.field.update(field);
            check(field.fdata == 0f, "chain exploded before five seconds");
        }
        check(ground.health < 100000f - shot.field.damagePerSecond * 4f
            && air.health < 100000f - shot.field.damagePerSecond * 4f
            && edge.health < 100000f - shot.field.damagePerSecond * 3.5f, "field lacks sustained ground/air/wide-area damage");
        check(wall.health < wallHp, "field did not damage buildings");
        check(friend.health == 100000 && ally.health == 100000 && allyWall.health == allyWallHp, "field damaged allies");
        check(far.health == 100000, "field damaged distant enemy");
        check(Groups.bullet.contains(b -> b.type == shot.field.discharge), "field lacks Smite-style native lightning");
        float before = ground.health;
        field.time = 300; shot.field.update(field);
        check(field.fdata == 1 && ground.health <= before - shot.field.finalDamage - shot.field.blastDamage, "full-field thunderstorm and first explosion missing at five seconds");
        shot.field.update(field); check(field.fdata == 1, "same node exploded twice");
        field.time = 301; shot.field.update(field); check(field.fdata == 1, "chain has no delay");
        field.time = 300 + shot.field.chainInterval; shot.field.update(field); check(field.fdata == 2, "next chain node did not explode");
        field.time = 300 + (shot.field.nodeCount - 1) * shot.field.chainInterval; shot.field.update(field); check(field.fdata == shot.field.nodeCount, "chain did not finish all nodes");
        check(friend.health == 100000 && ally.health == 100000 && allyWall.health == allyWallHp, "chain damaged allies");
        Time.time = 360; projectile.time = 12; shot.update(projectile);
        Bullet arc = Groups.bullet.find(b -> b.type == shot.discharge);
        check(arc != null && arc.damage == shot.dischargeDamage, "flight lacks powerful native lightning");
        float enemyHp = air.health, allyHp = ally.health;
        shot.discharge.hitEntity(arc, air, air.health);
        shot.discharge.hitEntity(arc, ally, ally.health);
        check(air.health < enemyHp && ally.health == allyHp, "native lightning damage/allied protection failed");
        Bullet expired = shot.create((Entityc)null, Team.sharded, 100, 100, 0);
        shot.despawned(expired);
        check(Groups.bullet.count(b -> b.type == shot.field) == 2, "range expiry did not deploy field");
    }
}
