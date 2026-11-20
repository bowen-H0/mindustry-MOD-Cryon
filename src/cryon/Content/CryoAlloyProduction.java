package cryon.Content;

import arc.graphics.Color;
import cryon.Type.SuperphaseSynthesizer;
import cryon.Fx.FactoryProductionFx;
import mindustry.content.*;
import mindustry.type.*;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.draw.*;
import mindustry.world.meta.Env;
import static mindustry.type.ItemStack.with;

/** Endgame alloy production and the local gamma-crystal precursor route. */
public final class CryoAlloyProduction{
    public static Liquid superphaseMatter;
    public static GenericCrafter perfectLatticeAssembler, cryoAlloyStabilizer, gammaCrystallizer;
    public static SuperphaseSynthesizer superphaseSynthesizer;

    private static void configure(GenericCrafter block, int size, float time, float power, String color, int visual){
        block.size = size;
        block.health = size * size * 120;
        block.hasItems = block.hasLiquids = block.hasPower = true;
        block.itemCapacity = 100;
        block.liquidCapacity = 300f;
        block.craftTime = time;
        block.envEnabled |= Env.space;
        block.consumePower(power);
        block.craftEffect = FactoryProductionFx.complete(visual, size, Color.valueOf(color));
        block.updateEffect = FactoryProductionFx.activity(visual, size, Color.valueOf(color));
        block.updateEffectChance = 0.035f;
        block.updateEffectSpread = 0f;
        DrawGlowRegion glow = new DrawGlowRegion("-glow");
        glow.color = Color.valueOf(color);
        glow.alpha = 0.75f;
        glow.glowScale = 8f;
        glow.glowIntensity = 0.15f;
        block.drawer = new DrawMulti(new DrawDefault(), glow, new FactoryProductionFx.ProcessDraw(visual, Color.valueOf(color)));
    }

    public static void load(){
        superphaseMatter = new Liquid("superphase-matter", Color.valueOf("ad94ef"));
        superphaseMatter.gas = true;
        superphaseMatter.gasColor = Color.valueOf("c5b9ff");
        superphaseMatter.lightColor = Color.valueOf("bda4ff55");
        superphaseMatter.coolant = false;
        superphaseMatter.incinerable = false;

        gammaCrystallizer = new GenericCrafter("gamma-crystallizer");
        configure(gammaCrystallizer, 4, 600f, 20f, "80e8b1", 0);
        gammaCrystallizer.requirements(Category.crafting, with(CryonItems.farstarAlloy, 200, CryonItems.nickel, 150,
                Items.silicon, 250, Items.graphite, 200));
        gammaCrystallizer.consumeItems(with(CryonItems.nuclearByproduct, 2, CryonItems.quartz, 4));
        gammaCrystallizer.consumeLiquid(Liquids.cryofluid, 6f / 60f);
        gammaCrystallizer.outputItem = new ItemStack(CryonItems.gammaCrystal, 16);

        perfectLatticeAssembler = new GenericCrafter("perfect-lattice-assembler");
        configure(perfectLatticeAssembler, 4, 600f, 30f, "8affdb", 1);
        perfectLatticeAssembler.requirements(Category.crafting, with(CryonItems.nanoMaterial, 150, Items.graphite, 500,
                CryonItems.nickel, 300, Items.surgeAlloy, 150, Items.silicon, 400));
        perfectLatticeAssembler.consumeItems(with(CryonItems.nanoMaterial, 4, Items.graphite, 20));
        perfectLatticeAssembler.consumeLiquid(Liquids.nitrogen, 12f / 60f);
        perfectLatticeAssembler.outputItem = new ItemStack(CryonItems.perfectLattice, 8);

        superphaseSynthesizer = new SuperphaseSynthesizer("superphase-synthesizer");
        configure(superphaseSynthesizer, 5, 60f, 60f, "c7acff", 2);
        superphaseSynthesizer.requirements(Category.crafting, with(Items.phaseFabric, 300, Items.surgeAlloy, 300,
                CryonItems.nickel, 450, Items.silicon, 500, CryonItems.nanoMaterial, 150));
        superphaseSynthesizer.constructorConsumption = 40f;
        superphaseSynthesizer.outputLiquid = new LiquidStack(superphaseMatter, 24f / 60f);
        superphaseSynthesizer.dumpExtraLiquid = false;

        cryoAlloyStabilizer = new GenericCrafter("cryo-alloy-stabilizer");
        configure(cryoAlloyStabilizer, 7, 180f, 200f, "ff8a9b", 3);
        cryoAlloyStabilizer.requirements(Category.crafting, with(CryonItems.farstarAlloy, 600, Items.surgeAlloy, 500,
                CryonItems.nanoMaterial, 400, Items.titanium, 800, Items.silicon, 800));
        cryoAlloyStabilizer.consumeItems(with(CryonItems.perfectLattice, 2, CryonItems.gammaCrystal, 8));
        cryoAlloyStabilizer.consumeLiquid(superphaseMatter, 12f / 60f);
        cryoAlloyStabilizer.outputItem = new ItemStack(CryonItems.cryoAlloy, 6);
    }
}
