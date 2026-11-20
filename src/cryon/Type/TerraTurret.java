package cryon.Type;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.geom.Vec2;
import mindustry.content.*;
import mindustry.entities.Effect;
import mindustry.entities.bullet.*;
import mindustry.entities.pattern.ShootBarrel;
import mindustry.gen.*;
import mindustry.graphics.Drawf;
import mindustry.type.*;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.draw.DrawTurret;
import cryon.Content.CryonItems;

/** Paired, instantaneous ground beams; each volley advances the spectrum. */
public class TerraTurret extends PowerTurret{
    public final BulletType[] beams = new BulletType[4];
    public static final Color[] spectrum = {
        Color.valueOf("ff535b"), Color.valueOf("ffab48"),
        Color.valueOf("55aaff"), Color.valueOf("66ef9b")
    };
    private static final Effect beamEffect = new Effect(10f, 600f, e -> {
        if(!(e.data instanceof Vec2)) return;
        Vec2 end = (Vec2)e.data;
        Draw.color(e.color);
        Lines.stroke(3.2f * e.fout());
        Lines.line(e.x, e.y, end.x, end.y);
        Draw.color(Color.white);
        Lines.stroke(1.1f * e.fout());
        Lines.line(e.x, e.y, end.x, end.y);
        Drawf.light(e.x, e.y, end.x, end.y, 12f * e.fout(), e.color, 0.65f);
        Draw.reset();
    });

    public TerraTurret(String name){
        super(name);
        requirements(Category.turret, ItemStack.with(CryonItems.hardSteel, 180,
            CryonItems.armorAlloy, 160, Items.silicon, 220, CryonItems.nickel, 100));
        size = 3;
        health = 2100;
        range = 240f;
        reload = 6f;
        shootY = 11f;
        rotateSpeed = 7f;
        shootCone = 5f;
        recoil = 1.2f;
        targetAir = false;
        targetGround = true;
        predictTarget = false;
        liquidCapacity = 60f;
        // Both are mandatory feedstocks, rather than optional turret coolant.
        consumeLiquids(LiquidStack.with(Liquids.water, 0.2f, Liquids.slag, 0.2f));
        coolant = null;
        shootSound = Sounds.shootLaser;
        ShootBarrel barrels = new ShootBarrel();
        barrels.barrels = new float[]{-5f, 0f, 0f, 5f, 0f, 0f};
        barrels.shots = 2;
        shoot = barrels;
        for(int i = 0; i < beams.length; i++) beams[i] = new TerraBeam(spectrum[i]);
        shootType = beams[0];
        drawer = new DrawTurret("cryon-");
        buildType = TerraTurretBuild::new;
    }

    public class TerraTurretBuild extends PowerTurretBuild{
        @Override protected void shoot(BulletType type){
            super.shoot(beams[(totalShots / 2) % beams.length]);
        }
    }

    public static class TerraBeam extends PointBulletType{
        public TerraBeam(Color color){
            speed = 240f;
            lifetime = 1f;
            damage = 24f;
            hitSize = 6f;
            collidesAir = false;
            collidesGround = true;
            lightning = 3;
            lightningDamage = 10f;
            lightningLength = 5;
            lightningLengthRand = 4;
            lightningColor = hitColor = trailColor = color;
            hitEffect = Fx.hitLaserColor;
            despawnEffect = Fx.none;
            despawnHit = true;
            shootEffect = Fx.shootSmallColor;
            smokeEffect = trailEffect = Fx.none;
            // Native lightning collision respects the ground-only targeting rule.
            lightningType = new BulletType(0.0001f, 10f){{
                lifetime = 10f;
                hitEffect = Fx.hitLancer;
                despawnEffect = Fx.none;
                status = StatusEffects.shocked;
                statusDuration = 60f;
                hittable = false;
                collidesAir = false;
            }};
        }

        @Override public void init(Bullet b){
            beamEffect.at(b.x, b.y, b.rotation(), hitColor,
                new Vec2(b.x + b.vel.x * b.lifetime, b.y + b.vel.y * b.lifetime));
            super.init(b);
        }
    }
}
