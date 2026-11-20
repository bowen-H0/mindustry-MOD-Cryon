package cryon.Content;

import cryon.Type.PurgeBarrageBulletType;
import mindustry.content.Items;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Sounds;
import mindustry.type.Category;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.draw.DrawTurret;
import static mindustry.type.ItemStack.with;

/** Tungsten-fed rotating petal barrage. */
public final class PurgeDefense{
    public static ItemTurret purge;

    public static void load(){
        BulletType barrage = new PurgeBarrageBulletType();
        purge = new ItemTurret("purge");
        purge.requirements(Category.turret, with(CryonItems.hardSteel, 260,
            CryonItems.armorAlloy, 240, Items.silicon, 300, CryonItems.nickel, 180));
        purge.size = 4;
        purge.health = 3200;
        purge.range = 368f;
        purge.reload = 50f;
        purge.rotateSpeed = 1.5f;
        purge.shootCone = 20f;
        purge.inaccuracy = 1f;
        purge.shootY = 10f;
        purge.recoil = 3f;
        purge.shake = 2f;
        purge.cooldownTime = 100f;
        purge.maxAmmo = 40;
        purge.ammoPerShot = 2;
        purge.shootSound = Sounds.shootAfflict;
        purge.ammo(Items.tungsten, barrage);
        purge.drawer = new DrawTurret("cryon-");
    }
}
