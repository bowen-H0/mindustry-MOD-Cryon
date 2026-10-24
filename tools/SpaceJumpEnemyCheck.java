import cryon.Type.SpaceJumpAbility;
import mindustry.Vars;
import mindustry.ai.types.FlyingAI;
import mindustry.content.UnitTypes;
import mindustry.game.Team;
import mindustry.gen.*;

public class SpaceJumpEnemyCheck{
    static void check(boolean value, String message){ if(!value) throw new AssertionError(message); }
    static class EnemyAI extends FlyingAI{
        Teamc destination;
        @Override public Teamc findMainTarget(float x, float y, float range, boolean air, boolean ground){ return destination; }
    }
    public static void main(String[] args){
        SpaceJumpCheck.main(args);
        Groups.unit.clear(); Groups.build.clear();
        Vars.state.rules.sector = null;
        Unit target = UnitTypes.dagger.create(Team.sharded);
        target.set(1600, 100); target.dead = false;
        Unit enemy = UnitTypes.flare.create(Team.crux);
        enemy.set(100, 100); enemy.elevation = 1; enemy.dead = false; enemy.rotation = 0;
        EnemyAI ai = new EnemyAI(); ai.destination = target; enemy.controller(ai);
        SpaceJumpAbility jump = new SpaceJumpAbility();
        jump.update(enemy);
        check(jump.active(), "ordinary non-RTS enemy did not charge");
        int ticks = 0;
        while(jump.active() && ticks++ < 1000) jump.update(enemy);
        check(enemy.x > 1000 && enemy.controller() == ai, "enemy hop failed or replaced its AI");
        jump.afterUpdate(enemy);
        enemy.set(100, 100); target.set(200, 100);
        jump = new SpaceJumpAbility(); jump.update(enemy);
        check(!jump.active(), "ordinary enemy jumped at close range");
        target.set(1600, 100); ai.destination = null;
        jump.update(enemy); check(!jump.active(), "targetless enemy jumped");
        ai.destination = target; enemy.team = Team.sharded;
        jump.update(enemy); check(!jump.active(), "friendly ordinary AI was enabled");
        enemy.team = Team.crux; enemy.apply(mindustry.content.StatusEffects.boss, 60);
        jump.update(enemy); check(!jump.active(), "boss enemy jumped");
        System.out.println("PASS: ordinary enemy AI distant hop without RTS/campaign, original controller retained, near/targetless/friendly/boss exclusions");
    }
}
