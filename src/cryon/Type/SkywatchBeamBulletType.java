package cryon.Type;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import cryon.Features.MapAlliances;
import cryon.Features.OrbitalSupport;
import cryon.Content.OrbitalUnits;
import cryon.Fx.BeaconFx;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;

/** A unit-fired instant beam and ground explosion; never creates a strike marker or timer. */
public class SkywatchBeamBulletType extends BulletType implements OrbitalProjectile{
    public final Color beamColor = Color.valueOf("57c2ff"), coreColor = Color.valueOf("eaffff");

    public SkywatchBeamBulletType(){
        speed = damage = 0f;
        splashDamage = 220f;
        splashDamageRadius = 80f;
        maxRange = 360f;
        lifetime = 12f;
        drawSize = 900f;
        collides = collidesTiles = collidesAir = false;
        collidesGround = true;
        absorbable = reflectable = hittable = keepVelocity = false;
        despawnEffect = Fx.none;
        hitEffect = BeaconFx.impact;
    }

    @Override public void init(Bullet bullet){
        super.init(bullet);
        if(!(bullet.owner instanceof Unit)){
            cancel(bullet); return;
        }
        Unit shooter = (Unit)bullet.owner;
        if(shooter.type != OrbitalUnits.skywatch || shooter.dead || !shooter.isAdded()
                || bullet.aimX < 0f || bullet.aimY < 0f || !shooter.within(bullet.aimX, bullet.aimY, maxRange)){
            cancel(bullet); return;
        }
        float attenuation = OrbitalSupport.consume(shooter, bullet.aimX, bullet.aimY);
        if(attenuation <= 0f){ cancel(bullet); return; }
        Vec2 end = new Vec2(bullet.aimX, bullet.aimY);
        bullet.data = end;
        hitEffect.at(end.x, end.y, 0f, beamColor);
        Effect.shake(2f, 3f, end.x, end.y);
        if(Vars.net != null && Vars.net.client()) return;
        float amount = splashDamage * attenuation * bullet.damageMultiplier();
        Units.nearbyEnemies(bullet.team, end.x, end.y, splashDamageRadius, unit -> {
            if(unit.isGrounded() && !MapAlliances.allied(unit.team, bullet.team)) unit.damage(amount);
        });
        Vars.indexer.eachBlock(null, end.x, end.y, splashDamageRadius,
                building -> !MapAlliances.allied(building.team, bullet.team),
                building -> building.damage(bullet.team, amount));
    }

    private void cancel(Bullet bullet){
        bullet.data = null;
        bullet.time = lifetime;
    }

    @Override public void draw(Bullet bullet){
        if(!(bullet.data instanceof Vec2)) return;
        Vec2 end = (Vec2)bullet.data;
        float z = Draw.z(), fade = Mathf.clamp(bullet.fout());
        Draw.z(OrbitalUnitType.orbitalLayer + 0.1f);
        Draw.color(beamColor, 0.25f * fade);
        Lines.stroke(11f * fade);
        Lines.line(bullet.x, bullet.y, end.x, end.y);
        Draw.color(beamColor, fade);
        Lines.stroke(4f * fade);
        Lines.line(bullet.x, bullet.y, end.x, end.y);
        Draw.color(coreColor, fade);
        Lines.stroke(1.5f * fade);
        Lines.line(bullet.x, bullet.y, end.x, end.y);
        Drawf.light(bullet.x, bullet.y, end.x, end.y, 16f * fade, beamColor, 0.65f * fade);
        Draw.reset(); Draw.z(z);
    }

    // Damage is applied once on firing, not again when the visual beam expires.
    @Override public void despawned(Bullet bullet){}
}
