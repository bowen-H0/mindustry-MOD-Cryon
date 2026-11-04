import arc.Core;
import arc.Settings;
import arc.files.Fi;
import arc.mock.*;
import arc.struct.ObjectIntMap;
import arc.struct.Seq;
import arc.util.I18NBundle;
import cryon.Features.UnitDisplayOrder;
import mindustry.Vars;
import mindustry.net.Net;
import mindustry.type.UnitType;
import mindustry.world.blocks.units.UnitFactory;

/** Checks registry lookups and saved plan indices independently of their display order. */
public class UnitDisplayOrderCheck{
    private static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }

    public static void main(String[] args){
        Core.app = new MockApplication(); Core.files = new MockFiles();
        Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Core.settings = new Settings();
        Core.settings.setDataDirectory(new Fi("/tmp/cryon-unit-order-check"));
        Core.bundle = I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(
                Core.settings.getDataDirectory().child("mods/cryon.jar"));
        Vars.headless = true; Vars.loadLocales = false;
        Vars.net = new Net(null); Vars.platform = new mindustry.core.Platform(){};
        mindustry.core.Version.init();
        Vars.init(); Vars.content.createBaseContent();
        UnitType foreign = new UnitType("unit-order-foreign-probe");
        ObjectIntMap<UnitType> originalIds = new ObjectIntMap<>();
        for(UnitType unit : Vars.content.units()) originalIds.put(unit, unit.id);
        Vars.content.createModContent();
        UnitType previous = null;
        int count = 0;
        for(int i = 0; i < Vars.content.units().size; i++){
            UnitType unit = Vars.content.units().get(i);
            check(unit.id == i && Vars.content.unit(unit.id) == unit, "ID lookup broken: " + unit.name);
            check(Vars.content.unit(unit.name) == unit, "Name lookup broken: " + unit.name);
            if(originalIds.containsKey(unit))
                check(unit.id == originalIds.get(unit, -1), "Foreign/vanilla unit moved: " + unit.name);
            if(!UnitDisplayOrder.owns(unit)) continue;
            if(previous != null) check(UnitDisplayOrder.compare(previous, unit) <= 0, "Display order broken");
            check(unit.databaseTag == null, "Unexpected custom unit category: " + unit.name);
            previous = unit; count++;
        }
        check(count >= 50 && Vars.content.unit(foreign.id) == foreign, "Unit registry incomplete");
        Vars.content.init();
        UnitFactory factory = (UnitFactory)Vars.content.block("cryon-unit-projector");
        Seq<UnitFactory.UnitPlan> originalPlans = factory.plans.copy();
        UnitDisplayOrder.arrangeProduction();
        for(int i = 0; i < originalPlans.size; i++){
            check(factory.plans.get(i) == originalPlans.get(i), "Saved production index changed: " + i);
            check(factory.plans.indexOf(originalPlans.get(i)) == i, "Configure index changed: " + i);
        }
        Seq<UnitFactory.UnitPlan> shown = Seq.with((Iterable<UnitFactory.UnitPlan>)factory.plans);
        for(int i = 1; i < shown.size; i++)
            check(UnitDisplayOrder.compare(shown.get(i - 1).unit, shown.get(i).unit) <= 0,
                    "Factory display order broken");
        UnitDisplayOrder.arrangeContent();
        for(UnitType unit : Vars.content.units()) check(Vars.content.unit(unit.id) == unit, "Repeat sort broke ID map");
        System.out.println("PASS: " + count + " mod units naturally ordered; foreign IDs and production indices preserved");
    }
}
