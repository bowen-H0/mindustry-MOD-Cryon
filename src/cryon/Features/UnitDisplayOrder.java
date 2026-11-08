package cryon.Features;

import arc.Events;
import arc.func.Func;
import arc.struct.IntSeq;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import cryon.Type.UniversalUnitAssembler;
import mindustry.Vars;
import mindustry.game.EventType.ModContentLoadEvent;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.blocks.units.Reconstructor;
import mindustry.world.blocks.units.UnitAssembler;
import mindustry.world.blocks.units.UnitFactory;

import java.util.Iterator;

/** Native unit list order, keeping each progression line from low tier to high tier. */
public final class UnitDisplayOrder{
    private static final ObjectMap<String, Integer> ranks = new ObjectMap<>();

    static{
        line("expedition voyager transliminal");
        line("thread process kernel");
        line("comet bolide umbra sagitta hydra");
        line("subsonic transonic supersonic relativistic superluminal");
        line("jaspis obsidianum nitrum hematites");
        line("buffer guardian peak blaze eternal");
        line("monomer dimer oligomer polymer macromolecule");
        line("benignitas salus propugno ratio veritas");
        line("receptor synapse ganglion cortex connectome");
        line("littorina natica murex charonia syrinx");
        line("vindex imperator");
        line("particle momentum inertia gravity singularity");
        line("nadir apex exotic libration lagrange");
        line("vain roche threshold hyperion oblivion destroyer eon");
        line("minimus danae pammelas schmidti sloani");
    }

    private UnitDisplayOrder(){}

    private static void line(String names){
        for(String name : names.split(" ")){
            ranks.put("cryon-" + name, ranks.size);
        }
    }

    public static boolean owns(UnitType unit){
        return unit.minfo.mod != null && "cryon".equals(unit.minfo.mod.name);
    }

    public static int compare(UnitType a, UnitType b){
        if(!owns(a) || !owns(b)) return Integer.compare(a.id, b.id);
        int hidden = Boolean.compare(a.hidden || a.internal, b.hidden || b.internal);
        if(hidden != 0) return hidden;
        int order = Integer.compare(ranks.get(a.name, 10000), ranks.get(b.name, 10000));
        if(order != 0) return order;
        return a.name.compareTo(b.name);
    }

    public static void install(){
        Events.on(ModContentLoadEvent.class, event -> arrangeContent());
    }

    public static void arrangeContent(){
        Seq<UnitType> registry = Vars.content.units();
        IntSeq slots = new IntSeq();
        Seq<UnitType> ordered = new Seq<>();
        for(int i = 0; i < registry.size; i++){
            UnitType unit = registry.get(i);
            if(!owns(unit)) continue;
            slots.add(i);
            ordered.add(unit);
        }
        ordered.sort(UnitDisplayOrder::compare);
        // Keep every other mod/vanilla slot fixed and preserve getByID(id) == content.
        // Save files resolve content names through their content headers on loading.
        for(int i = 0; i < ordered.size; i++){
            UnitType unit = ordered.get(i);
            int slot = slots.get(i);
            registry.set(slot, unit);
            unit.id = (short)slot;
        }
    }

    public static void arrangeProduction(){
        for(Block block : Vars.content.blocks()){
            if(block.minfo.mod == null || !"cryon".equals(block.minfo.mod.name)) continue;
            if(block instanceof UnitFactory factory)
                factory.plans = new DisplayPlans<>(factory.plans, plan -> plan.unit);
            if(block instanceof Reconstructor reconstructor)
                reconstructor.upgrades = new DisplayPlans<>(reconstructor.upgrades, pair -> pair[0]);
            if(block instanceof UnitAssembler assembler)
                assembler.plans = new DisplayPlans<>(assembler.plans, plan -> plan.unit);
            if(block instanceof UniversalUnitAssembler assembler)
                assembler.plans = new DisplayPlans<>(assembler.plans, plan -> plan.unit);
        }
    }

    /** Display iteration is sorted; get/indexOf retain the save/configuration plan indices. */
    public static class DisplayPlans<T> extends Seq<T>{
        private final Func<T, UnitType> unit;

        public DisplayPlans(Seq<T> original, Func<T, UnitType> unit){
            super(original);
            this.unit = unit;
        }

        @Override public Iterator<T> iterator(){
            Seq<T> ordered = new Seq<>(size);
            for(int i = 0; i < size; i++) ordered.add(get(i));
            ordered.sort((a, b) -> compare(unit.get(a), unit.get(b)));
            return ordered.iterator();
        }
    }
}
