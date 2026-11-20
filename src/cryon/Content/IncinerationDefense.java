package cryon.Content;

import cryon.Type.BurningOilBulletType;
import mindustry.content.*;
import mindustry.gen.Sounds;
import mindustry.type.Category;
import mindustry.world.blocks.defense.turrets.LiquidTurret;
import mindustry.world.draw.DrawTurret;
import static mindustry.type.ItemStack.with;

public final class IncinerationDefense{
    public static LiquidTurret incineration;

    public static void load(){
        incineration = new LiquidTurret("incineration");
        incineration.requirements(Category.turret, with(Items.tungsten, 200,
            CryonItems.hardSteel, 200, CryonItems.armorAlloy, 220, Items.silicon, 240));
        incineration.size = 4;
        incineration.health = 3000;
        incineration.range = 196f;
        incineration.reload = 4f;
        incineration.rotateSpeed = 5f;
        incineration.shootCone = 26f;
        incineration.inaccuracy = 13f;
        incineration.shootY = 15f;
        incineration.recoil = 0.6f;
        incineration.liquidCapacity = 120f;
        incineration.targetAir = false;
        incineration.targetGround = true;
        incineration.extinguish = false;
        incineration.shootSound = Sounds.none;
        incineration.loopSound = Sounds.loopFire;
        incineration.loopSoundVolume = 0.8f;
        incineration.ammo(Liquids.oil, new BurningOilBulletType());
        incineration.drawer = new DrawTurret("cryon-");
    }
}
