package cryon.Content;

import arc.struct.Seq;
import cryon.Features.CryonContent;
import cryon.Type.UniversalUnitAssembler;
import mindustry.type.*;
import mindustry.world.Block;
import java.util.Objects;

/** The crawler line uses the same tiered assemblers and fluids as the other armies. */
public final class CrawlerProduction{
    private CrawlerProduction(){}

    public static void addPlans(UniversalUnitAssembler t3, UniversalUnitAssembler t4, UniversalUnitAssembler t5){
        add(t3, BioCrawlerUnits.salus, BioCrawlerUnits.propugno,
                CryonContent.block("composite-wall-large"), 4, CryonContent.liquid("hydrogen"), 15f / 60f, 2000f);
        add(t4, BioCrawlerUnits.propugno, BioCrawlerUnits.ratio,
                CryonContent.block("charged-surge-wall-large"), 5, CryonContent.liquid("nitrogen"), 20f / 60f, 4000f);
        add(t5, BioCrawlerUnits.ratio, BioCrawlerUnits.veritas,
                CryonContent.block("cryo-alloy-wall-large"), 8, CryonContent.liquid("cryofluid"), 30f / 60f, 6000f);
    }

    private static void add(UniversalUnitAssembler assembler, UnitType previous, UnitType output,
                            Block wall, int walls, Liquid fluid, float rate, float time){
        Objects.requireNonNull(previous, "Missing previous crawler tier");
        Objects.requireNonNull(output, "Missing output crawler tier");
        Objects.requireNonNull(wall, "Missing crawler assembly wall");
        Objects.requireNonNull(fluid, "Missing crawler assembly liquid");
        if(assembler.plans.contains(plan -> plan.unit == output)) return;
        UniversalUnitAssembler.AssemblerUnitPlan plan = new UniversalUnitAssembler.AssemblerUnitPlan(
                output, time, Seq.with(new PayloadStack(previous, 2), new PayloadStack(wall, walls)));
        plan.liquidReq = new LiquidStack[]{new LiquidStack(fluid, rate)};
        assembler.plans.add(plan);
    }
}
