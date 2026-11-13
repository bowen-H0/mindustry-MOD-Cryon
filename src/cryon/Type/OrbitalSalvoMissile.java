package cryon.Type;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.math.Angles;
import cryon.Features.MapAlliances;
import mindustry.content.Fx;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.graphics.Layer;

/** Xi He's rear/flank launch route, tuned for the orbital arsenal's staggered salvos. */
public class OrbitalSalvoMissile extends MissileTruckBullets.FlankingMissile implements OrbitalProjectile{
    public float launchForwardOffset = 6f;
    public OrbitalSalvoMissile(float range){
        super(range);
        speed = 4.8f;
        damage = 65f;
        splashDamage = 45f;
        splashDamageRadius = 30f;
        lifetime = range / speed + 100f;
        collidesAir = collidesGround = true;
        absorbable = reflectable = false;
        hittable = true;
        homingRange = 200f;
        trailColor = backColor = hitColor = Color.valueOf("9bdcf7");
        frontColor = Color.white;
        trailLength = 20;
        hitEffect = despawnEffect = Fx.blastExplosion;
    }
    @Override public void init(Bullet b){
        super.init(b);
        if(!(b.data instanceof MissileTruckBullets.FlankState state)) return;
        float dx = Angles.trnsx(state.heading, launchForwardOffset), dy = Angles.trnsy(state.heading, launchForwardOffset);
        state.center.add(dx, dy);
        state.rear.add(dx, dy);
        for(var point : state.route) point.add(dx, dy);
        b.set(state.rear);
        b.lastX = b.x; b.lastY = b.y;
        b.vel.trns(b.angleTo(state.route[0]), speed);
    }
    @Override public boolean testCollision(Bullet b, Building building){
        return building.team != Team.derelict && !MapAlliances.allied(b.team, building.team) && super.testCollision(b, building);
    }
    @Override public void hitEntity(Bullet b, Hitboxc entity, float health){
        if(entity instanceof Unit unit && unit.type instanceof OrbitalUnitType) return;
        if(entity instanceof Teamc team && MapAlliances.allied(b.team, team.team())) return;
        super.hitEntity(b, entity, health);
    }
    @Override public void createSplashDamage(Bullet b, float x, float y){
        var scope = MapAlliances.enterTeam(b.team);
        try{ super.createSplashDamage(b, x, y); }finally{ MapAlliances.exit(scope); }
    }
    @Override public void draw(Bullet b){
        float z = Draw.z();
        Draw.z(b.data instanceof MissileTruckBullets.FlankState state && !state.cruising
            ? OrbitalUnitType.orbitalLayer + 0.1f : Layer.bullet);
        super.draw(b);
        Draw.z(z);
    }
}
