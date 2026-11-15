package cryon.Type;

import arc.graphics.g2d.*;
import arc.util.Time;
import cryon.Content.CryonItems;
import mindustry.content.*;
import mindustry.entities.pattern.ShootPattern;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.draw.DrawTurret;
import mindustry.type.Category;
import mindustry.type.ItemStack;

/** The completed elder: layered monochrome halos and a threefold decree. */
public class ThemisTurret extends PowerTurret{
    public ThemisTurret(String name){
        super(name);
        requirements(Category.turret, ItemStack.with(
            CryonItems.farstarAlloy, 3200, CryonItems.cryoAlloy, 1800,
            CryonItems.nanoMaterial, 2400, CryonItems.neutronite, 1400
        ));
        size = 5;
        health = 32000;
        range = 520f;
        reload = 240f;
        shootY = 8f;
        recoil = 5f;
        rotateSpeed = 2.5f;
        shootCone = 8f;
        inaccuracy = 3f;
        heatRequirement = 100f;
        maxHeatEfficiency = 1f;
        consumePower(4000f / 60f);
        shootWarmupSpeed = 0.025f;
        minWarmup = 0.9f;
        warmupMaintainTime = 180f;
        unitSort = mindustry.entities.UnitSorts.strongest;
        shootSound = Sounds.shootSmite;
        loopSound = Sounds.loopGlow;
        loopSoundVolume = 0.65f;
        shoot = new ShootPattern();
        shoot.shots = 3;
        shoot.shotDelay = 12f;
        shoot.firstShotDelay = 60f;
        shootType = new AbyssLightningBulletType(true);
        researchCostMultiplier = 0.1f;
        clipSize = 300f;
        drawer = new LawDrawer();
        limitRange(8f);
    }

    /** A single independent themis.png body with the shared Cryon base. */
    public static class LawDrawer extends DrawTurret{
        public LawDrawer(){ super("cryon-"); }

        @Override public void load(Block block){
            super.load(block);
            preview = block.region;
        }

        @Override public void draw(Building build){
            TurretBuild tb = (TurretBuild)build;
            float z = Draw.z(), warmup = build.warmup();
            Draw.z(Layer.block);
            Draw.color();
            float footprint = build.block.size * mindustry.Vars.tilesize;
            Draw.rect(base, build.x, build.y, footprint, footprint);
            Draw.z(Layer.turret);
            Draw.color();
            Draw.rect(build.block.region, build.x + tb.recoilOffset.x, build.y + tb.recoilOffset.y,
                build.block.region.width * Draw.scl,
                build.block.region.height * Draw.scl, tb.drawrot());
            Draw.z(Layer.effect);
            float alpha = 0.25f + warmup * 0.65f;
            float radius = 72f + warmup * 28f;
            ThemisLawFx.corona(build.x, build.y, radius, Time.time * 1.2f, alpha, warmup);
            Drawf.light(build.x, build.y, radius * 1.7f, ThemisLawFx.gold, 0.15f + warmup * 0.3f);
            Draw.reset();
            Draw.z(z);
        }
    }
}
