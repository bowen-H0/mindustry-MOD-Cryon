package cryon.Type;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.util.Time;
import cryon.Features.MapAlliances;
import cryon.Features.OrbitalTargeting;
import mindustry.content.Fx;
import mindustry.entities.bullet.BulletType;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;

import static mindustry.Vars.*;

/** A reusable, single-target tether beam. Pair with Weapon.alwaysContinuous; focus resets on target changes. */
public class LockOnBurnBulletType extends BulletType{
    public float damageInterval = 6f, focusTime = 120f, maxFocusMultiplier = 2f;
    public float beamWidth = 2f, beamLayer = Layer.bullet, aimTolerance = 10f;
    public boolean targetsOrbit;
    public Color beamColor = Color.valueOf("9bdcf7"), hotColor = Color.valueOf("ffd58a");

    public static class BeamState{
        public Teamc target;
        public float focus, endX, endY;
        public boolean active;
    }

    public LockOnBurnBulletType(){
        rangeOverride = 200f;
        speed = 0f;
        lifetime = 20f;
        keepVelocity = false;
        collides = collidesTiles = false;
        absorbable = reflectable = hittable = false;
        pierce = true;
        removeAfterPierce = false;
        optimalLifeFract = 0.5f;
        shootEffect = smokeEffect = hitEffect = despawnEffect = Fx.none;
    }

    @Override public float continuousDamage(){ return damage * 60f / damageInterval; }
    @Override public float estimateDPS(){ return continuousDamage(); }

    @Override public void init(Bullet b){
        super.init(b);
        b.data = new BeamState();
    }

    protected boolean valid(Bullet b, Teamc candidate){
        if(candidate == null || candidate.team() == Team.derelict || MapAlliances.allied(b.team, candidate.team())) return false;
        if(candidate instanceof Unit unit){
            if(!unit.isAdded() || unit.dead || unit.health <= 0f) return false;
            if(unit.type instanceof OrbitalUnitType){
                if(!targetsOrbit || !OrbitalTargeting.test(() -> unit.targetable(b.team))) return false;
            }else if(!(unit.isFlying() ? collidesAir : collidesGround) || !unit.targetable(b.team)) return false;
            return unit.within(b, rangeOverride + unit.hitSize / 2f);
        }
        return candidate instanceof Building building && collidesGround && building.isValid()
            && building.within(b, rangeOverride + building.hitSize() / 2f);
    }

    protected boolean firing(Bullet b){
        if(b.owner instanceof Unit unit){
            if(!unit.isAdded() || unit.dead || !unit.canShoot()) return false;
            for(var mount : unit.mounts) if(mount.bullet == b) return mount.shoot;
            return false;
        }
        if(b.owner instanceof Building building){
            return building.isValid() && building.enabled && building.efficiency > 0f
                && (!(building instanceof mindustry.world.blocks.defense.turrets.Turret.TurretBuild)
                    || ((mindustry.world.blocks.defense.turrets.Turret.TurretBuild)building).isShooting());
        }
        return false;
    }

    protected Teamc intendedTarget(Bullet b){
        if(b.owner instanceof Unit unit && !unit.isPlayer()){
            for(var mount : unit.mounts) if(mount.bullet == b && valid(b, mount.target)) return mount.target;
        }
        return targetAt(b, b.aimX, b.aimY);
    }

    protected Teamc targetAt(Bullet b, float x, float y){
        Teamc closest = null;
        float distance = Float.MAX_VALUE;
        for(Unit unit : Groups.unit){
            if(!valid(b, unit) || !unit.within(x, y, unit.hitSize / 2f + aimTolerance)) continue;
            float dst = unit.dst2(x, y);
            if(dst < distance){ distance = dst; closest = unit; }
        }
        if(closest == null && collidesGround){
            Building building = world.buildWorld(x, y);
            if(valid(b, building)) closest = building;
        }
        return closest;
    }

    @Override public void update(Bullet b){
        if(!(b.data instanceof BeamState state)) return;
        state.active = false;
        if(!firing(b)){
            state.target = null;
            state.focus = 0f;
            return;
        }
        Teamc intended = intendedTarget(b);
        if(intended != null && intended != state.target){ state.target = intended; state.focus = 0f; }
        if(!valid(b, state.target)){
            state.target = null;
            state.focus = 0f;
            return;
        }
        state.active = true;
        state.endX = b.aimX = state.target.getX();
        state.endY = b.aimY = state.target.getY();
        state.focus += Time.delta;
        damageTarget(b, state);
    }

    protected void damageTarget(Bullet b, BeamState state){
        if(b.timer(0, damageInterval) && !net.client()){
            float multiplier = Mathf.lerp(1f, maxFocusMultiplier, Mathf.clamp(state.focus / Math.max(1f, focusTime)));
            float amount = b.damage * multiplier;
            if(state.target instanceof Unit unit) unit.damage(amount);
            else if(state.target instanceof Building building) building.damage(b.team, amount * buildingDamageMultiplier);
        }
        if(!headless && b.timer(1, 5f)) Fx.colorSpark.at(state.endX, state.endY, b.angleTo(state.endX, state.endY), beamColor);
    }

    @Override public void draw(Bullet b){
        if(!(b.data instanceof BeamState state) || !state.active) return;
        float z = Draw.z();
        Draw.z(beamLayer);
        float heat = Mathf.clamp(state.focus / Math.max(1f, focusTime));
        float width = beamWidth * (0.8f + heat * 0.35f) * b.fslope();
        Draw.color(beamColor, hotColor, heat);
        Draw.alpha(0.16f);
        Lines.stroke(width * 4f); Lines.line(b.x, b.y, state.endX, state.endY, false);
        Draw.alpha(0.9f);
        Lines.stroke(width); Lines.line(b.x, b.y, state.endX, state.endY, false);
        Fill.circle(state.endX, state.endY, width * (1.4f + Mathf.absin(2f, 0.3f)));
        Draw.color(Color.white);
        Lines.stroke(width * 0.4f); Lines.line(b.x, b.y, state.endX, state.endY, false);
        Fill.circle(state.endX, state.endY, width * 0.65f);
        Drawf.light(b.x, b.y, state.endX, state.endY, width * 8f, beamColor, 0.6f);
        Draw.reset();
        Draw.z(z);
    }
}
