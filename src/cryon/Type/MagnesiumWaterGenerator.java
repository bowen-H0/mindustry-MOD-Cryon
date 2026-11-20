package cryon.Type;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.util.Time;
import cryon.Content.CryonItems;
import mindustry.content.*;
import mindustry.type.*;
import mindustry.world.blocks.power.ConsumeGenerator;
import mindustry.world.meta.Env;
import static mindustry.type.ItemStack.with;

/** Sealed electrochemical cell: magnesium and water in, power and hydrogen out. */
public class MagnesiumWaterGenerator extends ConsumeGenerator{
    public MagnesiumWaterGenerator(String name){
        super(name);
        size = 3;
        health = 720;
        hasItems = hasLiquids = true;
        itemCapacity = 20;
        liquidCapacity = 60f;
        powerProduction = 1200f / 60f;
        itemDuration = 120f;
        consumeItem(CryonItems.magnesium, 2);
        consumeLiquid(Liquids.water, 12f / 60f);
        outputLiquid = new LiquidStack(Liquids.hydrogen, 6f / 60f);
        envEnabled |= Env.space;
        buildType = MagnesiumWaterGeneratorBuild::new;
        requirements(Category.power, with(CryonItems.aluminum, 100, CryonItems.magnesium, 60, Items.graphite, 40));
    }

    public class MagnesiumWaterGeneratorBuild extends ConsumeGeneratorBuild{
        @Override public boolean shouldConsume(){
            return super.shouldConsume() && enabled
                    && liquids.get(outputLiquid.liquid) + outputLiquid.amount * delta() <= liquidCapacity - 0.001f;
        }

        @Override public void updateTile(){
            dumpLiquid(outputLiquid.liquid);
            boolean active = efficiency > 0f && shouldConsume();
            productionEfficiency = active ? efficiency : 0f;
            warmup = Mathf.lerpDelta(warmup, productionEfficiency, warmupSpeed);
            totalTime += warmup * Time.delta;
            if(!active) return;

            if(generateTime <= 0f){
                consume();
                generateTime = 1f;
            }
            float progress = delta() * productionEfficiency;
            liquids.add(outputLiquid.liquid, outputLiquid.amount * progress);
            // Preserve unspent magnesium when water is missing, disabled, or the outlet is blocked.
            generateTime = Math.max(0f, generateTime - progress / itemDuration);
        }

        @Override public void draw(){
            super.draw();
            float z = Draw.z();
            Draw.z(z + 0.01f);
            Draw.color(Color.valueOf("7de5df"));
            Draw.alpha(0.12f + 0.65f * warmup);
            for(int side : Mathf.signs){
                Fill.rect(x + side * 5.5f, y, 2f, 10f);
            }
            Draw.reset();
            Draw.z(z);
        }
    }
}
