package cryon.Content;

import arc.graphics.Color;
import mindustry.content.*;
import mindustry.type.*;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.draw.*;
import mindustry.world.meta.Env;
import static mindustry.type.ItemStack.with;

/** Sodium -> hydroxide -> carbonate -> the existing gameplay crystal-sand material. */
public final class SodiumChemistry{
    public static GenericCrafter sodiumHydrolyzer, sodaCarbonator, crystalSandFurnace, crystalSandSodiumExtractor;

    private static GenericCrafter machine(String name, int size, float craftTime, float power, String glow){
        GenericCrafter block = new GenericCrafter(name);
        block.size = size;
        block.health = size * size * 75;
        block.hasItems = block.hasLiquids = block.hasPower = true;
        block.itemCapacity = 20;
        block.liquidCapacity = 40f;
        block.craftTime = craftTime;
        block.envEnabled |= Env.space;
        block.consumePower(power);
        block.craftEffect = block.updateEffect = Fx.none;
        DrawGlowRegion light = new DrawGlowRegion("-glow");
        light.color = Color.valueOf(glow);
        light.alpha = 0.7f;
        light.glowIntensity = 0.15f;
        block.drawer = new DrawMulti(new DrawDefault(), light);
        return block;
    }

    public static void load(){
        sodiumHydrolyzer = machine("sodium-hydrolyzer", 2, 120f, 1f, "92e9dc");
        sodiumHydrolyzer.requirements(Category.crafting, with(CryonItems.aluminum, 60, CryonItems.magnesium, 30, Items.silicon, 30));
        sodiumHydrolyzer.consumeItems(with(CryonItems.sodium, 2));
        sodiumHydrolyzer.consumeLiquid(Liquids.water, 6f / 60f);
        sodiumHydrolyzer.outputItem = new ItemStack(CryonItems.sodiumHydroxide, 2);
        sodiumHydrolyzer.outputLiquid = new LiquidStack(Liquids.hydrogen, 2f / 60f);
        sodiumHydrolyzer.dumpExtraLiquid = false;

        sodaCarbonator = machine("soda-carbonator", 2, 90f, 1.5f, "a7d4fb");
        sodaCarbonator.requirements(Category.crafting, with(CryonItems.aluminum, 80, Items.graphite, 30, Items.silicon, 40));
        sodaCarbonator.consumeItems(with(CryonItems.sodiumHydroxide, 2, CryonItems.dryIce, 1));
        sodaCarbonator.outputItem = new ItemStack(CryonItems.sodiumCarbonate, 1);

        crystalSandFurnace = machine("crystal-sand-furnace", 3, 120f, 4f, "ffc887");
        crystalSandFurnace.requirements(Category.crafting, with(CryonItems.aluminum, 100, Items.graphite, 50, Items.silicon, 60, CryonItems.quartz, 30));
        crystalSandFurnace.consumeItems(with(CryonItems.sodiumCarbonate, 1, CryonItems.quartz, 2));
        crystalSandFurnace.outputItem = new ItemStack(CryonItems.crystalSand, 3);

        crystalSandSodiumExtractor = machine("crystal-sand-sodium-extractor", 3, 180f, 6f, "a5e9d4");
        crystalSandSodiumExtractor.requirements(Category.crafting, with(CryonItems.aluminum, 100, CryonItems.magnesium, 40, Items.graphite, 40, Items.silicon, 60));
        crystalSandSodiumExtractor.consumeItems(with(CryonItems.crystalSand, 3, CryonItems.dryIce, 1));
        crystalSandSodiumExtractor.consumeLiquid(Liquids.water, 2f / 60f);
        crystalSandSodiumExtractor.outputItem = new ItemStack(CryonItems.sodium, 1);
    }
}
