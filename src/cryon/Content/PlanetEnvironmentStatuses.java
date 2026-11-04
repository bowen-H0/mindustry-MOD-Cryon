package cryon.Content;

import arc.graphics.Color;
import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.struct.IntFloatMap;
import mindustry.entities.abilities.Ability;
import mindustry.entities.units.StatusEntry;
import mindustry.gen.Unit;
import mindustry.type.StatusEffect;

/** Environmental penalties are independent from native environment compatibility. */
public final class PlanetEnvironmentStatuses{
    public static StatusEffect heatStress, coldStress;
    public static CrystalStatus crystallized;

    private PlanetEnvironmentStatuses(){}

    public static void load(){
        heatStress = new EnvironmentStatus("heat-stress"){{
            color = Color.valueOf("ed856a");
            speedMultiplier = 0.7f;
            damageMultiplier = 0.65f;
            reloadMultiplier = 0.65f;
            buildSpeedMultiplier = 0.6f;
            healthMultiplier = 0.75f;
            effectChance = 0f;
        }};
        coldStress = new EnvironmentStatus("cold-stress"){{
            color = Color.valueOf("a6d9f5");
            speedMultiplier = 0.6f;
            damageMultiplier = 0.8f;
            reloadMultiplier = 0.6f;
            buildSpeedMultiplier = 0.6f;
            healthMultiplier = 0.8f;
            effectChance = 0f;
        }};
        crystallized = new CrystalStatus("crystallized");
    }

    public static class EnvironmentStatus extends StatusEffect{
        private final String shortName;

        public EnvironmentStatus(String name){
            super(name);
            shortName = name;
            localizedName = Core.bundle.get("status.cryon-" + name + ".name", localizedName);
            description = Core.bundle.get("status.cryon-" + name + ".description", "");
        }

        @Override public void loadIcon(){
            super.loadIcon();
            uiIcon = fullIcon = Core.atlas.find("cryon-" + shortName, fullIcon);
        }
    }

    public static class CrystalStatus extends EnvironmentStatus{
        private static final Ability[] noAbilities = new Ability[0];
        private final IntFloatMap headings = new IntFloatMap();
        private final Color crystal = Color.valueOf("ad80dc");
        private final Color highlight = Color.valueOf("e9daff");

        public CrystalStatus(String name){
            super(name);
            color = crystal;
            permanent = true;
            disarm = true;
            speedMultiplier = reloadMultiplier = damageMultiplier = buildSpeedMultiplier = 0f;
            effectChance = 0f;
        }

        @Override public void applied(Unit unit, float time, boolean extend){
            super.applied(unit, time, extend);
            freeze(unit);
        }

        @Override public void update(Unit unit, StatusEntry entry){ freeze(unit); }

        public void freeze(Unit unit){
            if(!headings.containsKey(unit.id)) headings.put(unit.id, unit.rotation);
            unit.rotation = headings.get(unit.id);
            unit.vel.setZero();
            unit.isShooting = false;
            unit.disarmed = true;
            unit.mineTile = null;
            unit.plans.clear();
            unit.abilities = noAbilities;
            for(var mount : unit.mounts){
                mount.shoot = false;
                mount.rotate = false;
                if(mount.bullet != null){
                    mount.bullet.remove();
                    mount.bullet = null;
                }
            }
        }

        public void forget(Unit unit){ headings.remove(unit.id, 0f); }
        public void clear(){ headings.clear(); }
        public boolean hasFrozenUnits(){ return !headings.isEmpty(); }

        @Override public void draw(Unit unit){
            float z = Draw.z();
            try{
                float r = Math.max(3f, unit.hitSize * 0.55f);
                Draw.z(z + 0.002f);
                Draw.color(crystal, 0.8f);
                Draw.rect(unit.type.region, unit.x, unit.y, unit.rotation - 90f);
                Fill.poly(unit.x, unit.y, 6, r, unit.rotation + 30f);
                Draw.color(highlight, 0.7f);
                Lines.stroke(1f);
                Lines.poly(unit.x, unit.y, 6, r, unit.rotation + 30f);
                Lines.lineAngle(unit.x, unit.y, unit.rotation + 30f, r);
                Lines.lineAngle(unit.x, unit.y, unit.rotation + 150f, r);
                Lines.lineAngle(unit.x, unit.y, unit.rotation + 270f, r);
            }finally{
                Draw.reset();
                Draw.z(z);
            }
        }
    }
}
