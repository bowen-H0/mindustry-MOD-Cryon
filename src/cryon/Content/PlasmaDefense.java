package cryon.Content;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.Time;
import cryon.Fx.InterfereFx;
import mindustry.graphics.Drawf;
import cryon.Type.PlasmaFence;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.gen.Bullet;
import mindustry.type.*;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.meta.*;

import static mindustry.type.ItemStack.with;

public final class PlasmaDefense{
    public static StatusEffect paralysis;
    public static PlasmaFence plasmaFence;
    public static PowerTurret interfere;
    public static void load(){
        paralysis = new StatusEffect("emp-paralysis"){{
            color = Color.valueOf("a9d8ff");
            speedMultiplier = 0.15f;
            reloadMultiplier = 0.3f;
            effect = InterfereFx.ionTrail;
            effectChance = 0.18f;
        }};
        plasmaFence = new PlasmaFence("plasma-fence"){{
            requirements(Category.defense, with(CryonItems.aluminum, 200, Items.silicon, 150, Items.surgeAlloy, 80));
        }};
        interfere = new PowerTurret("interfere"){{
            requirements(Category.turret, with(CryonItems.aluminum, 45, Items.graphite, 25, Items.silicon, 20));
            size = 2;
            health = 480;
            range = 144f;
            reload = 90f;
            rotateSpeed = 5f;
            consumePower(1.5f);
            shootType = new BasicBulletType(3f, 5f){
                {
                    lifetime = 48f;
                    width = 11f;
                    height = 17f;
                    trailColor = InterfereFx.blue;
                    trailWidth = 2.8f;
                    trailLength = 14;
                    trailEffect = InterfereFx.ionTrail;
                    trailInterval = 3f;
                    lightColor = InterfereFx.blue;
                    lightRadius = 40f;
                    lightOpacity = 0.8f;
                    shootEffect = InterfereFx.muzzle;
                    smokeEffect = Fx.none;
                    hitColor = backColor = paralysis.color;
                    frontColor = Color.white;
                    splashDamage = 8f;
                    splashDamageRadius = InterfereFx.radius;
                    hitEffect = InterfereFx.pulse;
                    despawnEffect = Fx.none;
                    hitSound = mindustry.gen.Sounds.blockExplodeElectric;
                    hitSoundVolume = 0.8f;
                    hitShake = 0.6f;
                    despawnHit = true;
                    status = paralysis;
                    statusDuration = 90f;
                }
                @Override public void draw(Bullet b){
                    super.draw(b);
                    float pulse = 1f + Mathf.absin(Time.time, 2f, 0.2f);
                    Draw.color(InterfereFx.blue);
                    Lines.stroke(1.4f);
                    Lines.circle(b.x, b.y, 6f * pulse);
                    float angle = b.rotation() + Time.time * 11f;
                    float dx = Angles.trnsx(angle, 8f * pulse);
                    float dy = Angles.trnsy(angle, 8f * pulse);
                    Lines.line(b.x - dx, b.y - dy, b.x + dy * 0.35f, b.y - dx * 0.35f);
                    Lines.line(b.x + dy * 0.35f, b.y - dx * 0.35f, b.x + dx, b.y + dy);
                    Draw.color(Color.white);
                    Fill.circle(b.x, b.y, 2.7f * pulse);
                    Drawf.light(b.x, b.y, 40f, InterfereFx.blue, 0.8f);
                    Draw.reset();
                }
                @Override public void hit(Bullet b, float x, float y){
                    super.hit(b, x, y);
                    if(Vars.net.client()) return;
                    Units.nearbyEnemies(b.team, x, y, splashDamageRadius, unit -> {
                        if(unit.hittable() && !cryon.Features.MapAlliances.allied(b.team, unit.team)) unit.apply(paralysis, statusDuration);
                    });
                }
            };
        }};
    }
}
