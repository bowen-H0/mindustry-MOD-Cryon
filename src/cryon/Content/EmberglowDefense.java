package cryon.Content;

import arc.graphics.Color;
import mindustry.content.*;
import mindustry.entities.UnitSorts;
import mindustry.entities.bullet.PointLaserBulletType;
import mindustry.gen.Sounds;
import mindustry.type.Category;
import mindustry.world.blocks.defense.turrets.ContinuousTurret;
import mindustry.world.draw.DrawTurret;
import static mindustry.type.ItemStack.with;

/** Oil-fed counterpart to Lustre's sustained, slowly tracking point laser. */
public final class EmberglowDefense{
    public static ContinuousTurret emberglow;

    public static void load(){
        emberglow = new ContinuousTurret("emberglow"){{
            requirements(Category.turret, with(CryonItems.hardSteel, 160,
                CryonItems.armorAlloy, 120, Items.silicon, 200, CryonItems.nickel, 80));
            size = 3;
            health = 1800;
            range = 232f;
            liquidCapacity = 50f;
            shootY = 5f;
            shootCone = 360f;
            shootWarmupSpeed = 0.08f;
            aimChangeSpeed = 0.9f;
            rotateSpeed = 0.9f;
            predictTarget = false;
            scaleDamageEfficiency = true;
            unitSort = UnitSorts.strongest;
            shootSound = Sounds.none;
            loopSound = Sounds.beamLustre;
            loopSoundVolume = 1f;
            consumeLiquid(Liquids.oil, 0.2f);
            consumePower(2.5f);
            drawer = new DrawTurret("cryon-");
            shootType = new PointLaserBulletType(){{
                damage = 85f;
                damageInterval = 5f;
                buildingDamageMultiplier = 0.35f;
                color = Color.valueOf("ffb35c");
                hitColor = lightColor = Color.valueOf("ff8b45");
                hitEffect = Fx.hitFlameSmall;
                beamEffect = Fx.colorTrail;
                status = StatusEffects.burning;
                statusDuration = 180f;
            }};
        }};
    }
}
