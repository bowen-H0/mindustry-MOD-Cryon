package cryon.Type;

import arc.math.Mathf;
import arc.util.Time;
import cryon.Content.CryonItems;
import cryon.Features.MapAlliances;
import mindustry.entities.Damage;
import mindustry.entities.units.AIController;
import mindustry.gen.*;
import mindustry.content.Fx;

import static mindustry.Vars.*;

/** An actual flying controller: ignores dogfights and commits its cargo to an enemy building. */
public final class NuclearKamikazeAI extends AIController{
    public Building impactTarget;
    private float retarget;

    @Override public void updateUnit(){
        if(net.client() || unit.dead) return;
        if(unit.stack.item != CryonItems.nuclearExplosive || unit.stack.amount <= 0){ unit.resetController(); return; }
        if(impactTarget == null || !impactTarget.isValid() || MapAlliances.allied(impactTarget.team, unit.team)
            || (retarget -= Time.delta) <= 0f){
            impactTarget = chooseBuilding();
            retarget = 90f;
        }
        unit.controlWeapons(false, false);
        if(impactTarget == null) return;
        unit.lookAt(impactTarget);
        moveTo(impactTarget, 0f, 12f);
        float contact = impactTarget.block.size * tilesize / 2f + unit.hitSize * 0.3f + 8f;
        if(unit.within(impactTarget, contact)) detonate(unit);
    }

    public Building chooseBuilding(){
        Building best = null;
        float score = Float.MAX_VALUE;
        for(Building building : Groups.build){
            if(!building.isValid() || building.team == mindustry.game.Team.derelict
                || MapAlliances.allied(building.team, unit.team)
                || !building.block.targetable || building.block.privileged) continue;
            float priority = building == building.team.core() ? 2.2f : building.block.hasPower ? 1.3f : 1f;
            float candidate = unit.dst(building) / priority;
            if(candidate < score){ score = candidate; best = building; }
        }
        return best;
    }

    public static boolean detonate(Unit bomber){
        if(net.client() || bomber.dead || bomber.stack.item != CryonItems.nuclearExplosive || bomber.stack.amount <= 0) return false;
        int charges = Mathf.clamp(bomber.stack.amount, 1, 6);
        float x = bomber.x, y = bomber.y, radius = 120f + charges * 24f;
        mindustry.game.Team team = bomber.team;
        // Consume the cargo first, so recursive damage/death cannot detonate it twice.
        bomber.clearItem();
        Damage.damage(team, x, y, radius, 6000f * charges, true, true, true);
        Call.effect(Fx.reactorExplosion, x, y, 0f, CryonItems.nuclearExplosive.color);
        Call.effect(Fx.shockwave, x, y, 0f, CryonItems.nuclearExplosive.color);
        bomber.kill();
        return true;
    }
}
