package cryon.Type;

import arc.Events;
import arc.graphics.Color;
import arc.struct.ObjectMap;
import mindustry.Vars;
import mindustry.entities.Damage;
import mindustry.entities.units.StatusEntry;
import mindustry.game.EventType;
import mindustry.gen.Unit;
import mindustry.type.StatusEffect;

/** A mark is spent once per continuous application; extending the debuff does not rearm it. */
public class PhaseDamageStatus extends StatusEffect{
    private static class State{
        float ceiling;
        boolean armed = true;
        State(Unit unit){ ceiling = unit.health; }
    }
    private final ObjectMap<Unit, State> states = new ObjectMap<>();

    public PhaseDamageStatus(){
        super("phase-damage");
        color = Color.valueOf("bda6ff");
        speedMultiplier = 0.8f;
        reloadMultiplier = damageMultiplier = buildSpeedMultiplier = 0.8f;
        healthMultiplier = 0.9f;
        dragMultiplier = 1.2f;
        effectChance = 0f;
        Events.on(EventType.ResetEvent.class, e -> states.clear());
        Events.on(EventType.UnitDestroyEvent.class, e -> states.remove(e.unit));
        Events.on(EventType.UnitDamageEvent.class, e -> {
            if(Vars.net.client() || e.bullet == null || e.bullet.damage <= 0f
                    || cryon.Features.MapAlliances.allied(e.unit.team, e.bullet.team)) return;
            if(consume(e.unit)){
                float armor = e.unit.armorOverride >= 0f ? e.unit.armorOverride : e.unit.armor;
                float base = e.bullet.type.pierceArmor ? e.bullet.damage
                    : Damage.applyArmor(e.bullet.damage, armor * e.bullet.type.armorMultiplier);
                // The original hit already dealt one share; add four with armor applied only once.
                e.unit.damagePierce(base * 4f);
            }
            trackDamage(e.unit);
        });
    }

    @Override public void applied(Unit unit, float time, boolean extend){
        super.applied(unit, time, extend);
        if(!extend || !states.containsKey(unit)) states.put(unit, new State(unit));
    }

    public boolean consume(Unit unit){
        if(unit.getDuration(this) <= 0f) return false;
        State state = states.get(unit);
        if(state == null){ state = new State(unit); states.put(unit, state); }
        if(!state.armed) return false;
        state.armed = false;
        mindustry.content.Fx.instBomb.at(unit.x, unit.y, color);
        return true;
    }

    public void trackDamage(Unit unit){
        State state = states.get(unit);
        if(state != null) state.ceiling = Math.min(state.ceiling, unit.health);
    }

    @Override public void update(Unit unit, StatusEntry entry){
        super.update(unit, entry);
        unit.armorOverride = unit.type.armor * 0.75f;
        State state = states.get(unit);
        if(state == null){ state = new State(unit); states.put(unit, state); }
        // Reject net recovery from regeneration, healing bullets and repair fields each tick.
        unit.health = Math.min(unit.health, state.ceiling);
        state.ceiling = unit.health;
    }

    @Override public void onRemoved(Unit unit){ states.remove(unit); }
}
