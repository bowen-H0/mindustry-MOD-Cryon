package cryon.Type;

import arc.Core;
import arc.math.Angles;
import mindustry.Vars;
import mindustry.entities.Units;
import mindustry.entities.abilities.Ability;
import mindustry.gen.Unit;

/** Lay a capped defensive minefield while enemies are in the local combat area. */
public class SeaMineLayerAbility extends Ability{
    public SeaMineBulletType mine;
    public float reload = 300f;
    public int maxMines = 6;

    public SeaMineLayerAbility(SeaMineBulletType mine){ this.mine = mine; }

    @Override public void update(Unit unit){
        if(Vars.net != null && Vars.net.client()) return;
        data += arc.util.Time.delta;
        if(data < reload || unit.tileOn() == null || !unit.tileOn().floor().isLiquid) return;
        boolean[] enemy = {false};
        Units.nearbyEnemies(unit.team, unit.x, unit.y, 210f, other -> { if(!other.dead) enemy[0] = true; });
        if(!enemy[0]) return;
        int count = mindustry.gen.Groups.bullet.count(b -> b.type == mine && b.owner == unit);
        if(count >= maxMines) return;
        mine.create(unit, unit.team, unit.x + Angles.trnsx(unit.rotation + 180f, unit.hitSize),
                unit.y + Angles.trnsy(unit.rotation + 180f, unit.hitSize), unit.rotation + 180f);
        data = 0f;
    }

    @Override public String localized(){ return Core.bundle.get("ability.cryon-sea-mines"); }
    @Override public void addStats(arc.scene.ui.layout.Table table){
        table.add(Core.bundle.format("ability.cryon-sea-mines.description", (int)(reload / 60f), maxMines)).wrap().width(320f).row();
    }
}
