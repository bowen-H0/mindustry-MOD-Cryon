package cryon.Content;

import cryon.Type.*;
import mindustry.content.Items;
import mindustry.world.draw.DrawTurret;
import mindustry.type.Category;
import static mindustry.type.ItemStack.with;

public final class FaultDefense{
    public static FaultTurret fault;
    public static void load(){
        fault = new FaultTurret("fault");
        fault.requirements(Category.turret, with(CryonItems.aluminum, 55, CryonItems.magnesium, 35, Items.graphite, 20));
        fault.size = 2;
        fault.health = 520;
        fault.range = 160f;
        fault.reload = 100f;
        fault.inaccuracy = 12f;
        fault.shootCone = 18f;
        fault.rotateSpeed = 3.5f;
        fault.recoil = 2.2f;
        fault.shake = 1.2f;
        fault.shootY = 6f;
        fault.targetAir = false;
        fault.targetGround = true;
        fault.shoot.firstShotDelay = 24f;
        fault.moveWhileCharging = false;
        fault.accurateDelay = false;
        fault.maxAmmo = 20;
        fault.ammoPerShot = 2;
        fault.ammo(CryonItems.magnesium, new FaultLaserBulletType());
        fault.consumePower(1.5f);
        fault.drawer = new DrawTurret("cryon-");
    }
}
