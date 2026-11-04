import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.*;
import java.nio.file.Files;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.io.SaveIO;
import mindustry.net.Net;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.production.*;
import mindustry.world.consumers.*;
import mindustry.world.meta.Env;

/** Real underwater placement, extraction, transport, deterministic dual output and save/reload. */
public class SeabedSedimentCheck{
    static Item sediment, dryIce, magnesium;
    static void check(boolean valid, String message){ if(!valid) throw new AssertionError(message); }
    static void ticks(Building build, int count){
        for(int i = 0; i < count; i++){ Time.delta = 1; Time.time++; build.updateConsumption(); build.updateTile(); }
    }
    static Building place(Block block, int x, int y){
        check(Build.validPlace(block, Team.sharded, x, y, 0, false, false), "Cannot build on the deep seabed: " + block.name);
        Vars.world.tile(x, y).setBlock(block, Team.sharded, 0);
        return Vars.world.tile(x, y).build;
    }
    public static void main(String[] args) throws Exception{
        Core.app = new MockApplication(); Core.files = new MockFiles(); Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Core.settings = new Settings(); Core.settings.setDataDirectory(new Fi(Files.createTempDirectory("cryon-sediment-check-").toFile()));
        Core.bundle = I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(Core.settings.getDataDirectory().child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(Core.settings.getDataDirectory().child("mods/unitlanuch.jar"));
        Vars.headless = true; Vars.loadLocales = false; Vars.net = new Net(null);
        Vars.platform = new mindustry.core.Platform(){};
        mindustry.core.Version.init(); Vars.init(); Vars.content.createBaseContent(); Vars.content.createModContent(); Vars.content.init();
        var mod = Vars.mods.locateMod("cryon");
        check(mod != null && !mod.hasContentErrors(), "Packaged content errors: " + mod.erroredContent);
        var loader = mod.main.getClass().getClassLoader();
        loader.loadClass("cryon.Features.DeepSeaRules").getMethod("configure").invoke(null);
        sediment = Vars.content.item("cryon-abyssal-sediment");
        dryIce = Vars.content.item("cryon-dry-ice"); magnesium = Vars.content.item("cryon-magnesium");
        check(sediment != null && sediment.hardness == 0 && sediment.lowPriority, "Sediment must be easy to mine with low ore priority");
        int sedimentFloors = 0, mineralFloors = 0;
        for(Block block : Vars.content.blocks()){
            if(!block.name.startsWith("cryon-abyss-") || !(block instanceof mindustry.world.blocks.environment.Floor) || block.isOverlay()) continue;
            if(block.itemDrop == sediment) sedimentFloors++;
            if(block.name.endsWith("titanium-bed")){
                check(block.itemDrop == Items.titanium, "Existing titanium seabed was overwritten"); mineralFloors++;
            }
            if(block.name.endsWith("nickel-bed") || block.name.endsWith("metal-nodules")){
                check(block.itemDrop == Vars.content.item("cryon-nickel"), "Existing nickel seabed was overwritten"); mineralFloors++;
            }
        }
        check(sedimentFloors == 23 && mineralFloors == 6, "Unexpected seabed coverage: " + sedimentFloors + "/" + mineralFloors);
        Vars.logic = new Logic(); Vars.logic.reset();
        Vars.state.rules.env = Env.terrestrial | Env.underwater;
        Vars.world.loadGenerator(72, 64, tiles -> {
            for(int x = 0; x < 72; x++) for(int y = 0; y < 64; y++)
                tiles.set(x, y, new Tile(x, y, Vars.content.block("cryon-abyss-blue-bed"), Blocks.air, Blocks.air));
        });
        Drill drill = (Drill)Vars.content.block("cryon-shattering-drill");
        Drill melting = (Drill)Vars.content.block("cryon-melting-drill");
        GenericCrafter factory = (GenericCrafter)Vars.content.block("cryon-sediment-separator");
        for(Block block : new Block[]{drill, melting, factory}) check(block.supportsEnv(Vars.state.rules.env), "Industry disabled underwater: " + block.name);
        check(Build.validPlace(melting, Team.sharded, 26, 26, 0, false, false), "Powered early drill cannot be placed underwater");
        for(Block block : Vars.content.blocks()) if(block.name.startsWith("cryon-vacuum-")){
            check(Build.validPlace(block, Team.sharded, 30, 30, 0, false, false), "Vacuum logistics cannot be built underwater: " + block.name);
        }
        var harvest = (Drill.DrillBuild)place(drill, 12, 12);
        harvest.onProximityUpdate();
        check(harvest.dominantItem == sediment && harvest.dominantItems == 4, "Drill cannot recognize sediment floors");
        ticks(harvest, 600);
        check(harvest.items.get(sediment) > 0, "Drill did not actually extract sediment");
        // One ore tile must outrank three ordinary sediment tiles.
        Vars.world.tile(12, 12).setOverlay(Vars.content.block("cryon-ore-magnesium"));
        harvest.onProximityUpdate();
        check(harvest.dominantItem == magnesium, "Sediment displaced an existing ore overlay");
        Unit coreUnit = ((mindustry.world.blocks.storage.CoreBlock)Vars.content.block("cryon-core-pioneer")).unitType.create(Team.sharded);
        coreUnit.set(200, 200);
        check(coreUnit.getMineResult(Vars.world.tile(24, 24)) == sediment, "Core unit cannot manually mine sediment");
        var machine = (GenericCrafter.GenericCrafterBuild)place(factory, 40, 40);
        ConsumeItems inputs = factory.findConsumer(c -> c instanceof ConsumeItems);
        ConsumePower power = factory.findConsumer(c -> c instanceof ConsumePower);
        check(inputs.items.length == 1 && inputs.items[0].item == sediment && inputs.items[0].amount == 6, "Incorrect sediment input");
        check(factory.craftTime == 120 && power.usage == 1, "Incorrect cycle time or power use");
        check(factory.outputItems.length == 2 && factory.outputItems[0].item == dryIce && factory.outputItems[0].amount == 2
            && factory.outputItems[1].item == magnesium && factory.outputItems[1].amount == 1, "Output is not deterministic dry ice plus magnesium");
        for(ItemStack cost : factory.requirements) check(cost.item != dryIce && cost.item != magnesium && cost.item != sediment, "Bootstrap requires its own products");
        machine.power.status = 1; ticks(machine, 200);
        check(machine.progress == 0 && machine.items.total() == 0, "Free production without sediment");
        machine.items.add(sediment, 5); ticks(machine, 200);
        check(machine.progress == 0 && machine.items.get(sediment) == 5, "Partial input was consumed");
        machine.items.add(sediment, 1); machine.power.status = 0; ticks(machine, 200);
        check(machine.progress == 0 && machine.items.get(sediment) == 6, "Powerless production consumed sediment");
        machine.power.status = 1; ticks(machine, 130);
        check(machine.items.get(sediment) == 0 && machine.items.get(dryIce) == 2 && machine.items.get(magnesium) == 1, "Recipe did not consume/produce exact quantities");
        ticks(machine, 200);
        check(machine.items.get(dryIce) == 2 && machine.items.get(magnesium) == 1, "Recipe duplicated outputs without input");
        for(Item blocked : new Item[]{dryIce, magnesium}){
            machine.items.clear(); machine.progress = 0;
            machine.items.add(sediment, 6); machine.items.add(blocked, factory.itemCapacity);
            ticks(machine, 200);
            check(machine.items.get(sediment) == 6 && machine.progress == 0, "Full output did not pause the complete recipe");
            machine.items.remove(blocked, factory.itemCapacity); ticks(machine, 130);
            check(machine.items.get(sediment) == 0 && machine.items.get(dryIce) == 2 && machine.items.get(magnesium) == 1, "Unblocking an output did not resume crafting");
        }
        // Actual drill -> three eastbound vacuum ducts -> separator, on deep water throughout.
        var source = (Drill.DrillBuild)place(drill, 12, 26);
        source.onProximityUpdate();
        Building[] ducts = {place(Vars.content.block("cryon-vacuum-conduit"), 14, 26),
            place(Vars.content.block("cryon-vacuum-conduit"), 15, 26), place(Vars.content.block("cryon-vacuum-conduit"), 16, 26)};
        var downstream = (GenericCrafter.GenericCrafterBuild)place(factory, 17, 26);
        downstream.power.status = 1;
        for(int tick = 0; tick < 1500; tick++){
            ticks(source, 1);
            for(Building duct : ducts) ticks(duct, 1);
            ticks(downstream, 1);
        }
        check(downstream.items.get(dryIce) >= 2 && downstream.items.get(magnesium) >= 1, "The underwater production chain cannot transport/craft sediment");
        // Half-finished work and its input must survive a real world save.
        machine.items.clear(); machine.items.add(sediment, 6); machine.progress = 0; ticks(machine, 50);
        float progress = machine.progress;
        Fi save = Core.settings.getDataDirectory().child("sediment-industry.msav");
        SaveIO.write(save); Vars.logic.reset(); SaveIO.load(save);
        var restored = (GenericCrafter.GenericCrafterBuild)Vars.world.tile(40, 40).build;
        check(restored.progress == progress && restored.items.get(sediment) == 6, "Save lost partial progress or ingredients");
        restored.power.status = 1; ticks(restored, 80);
        check(restored.items.get(sediment) == 0 && restored.items.get(dryIce) == 2 && restored.items.get(magnesium) == 1, "Restored recipe lost or duplicated resources");
        loader.loadClass("cryon.TechTree.CryonTechTree").getMethod("load").invoke(null);
        check(sediment.techNode != null && sediment.techNode.parent.content == Vars.content.item("cryon-aluminum"), "Sediment research is inaccessible");
        check(factory.techNode != null && factory.techNode.parent.content == drill, "Separator research is inaccessible");
        check((boolean)loader.loadClass("cryon.Content.CryonItems").getMethod("belongsTo", String.class, String.class)
            .invoke(null, "abyssal-sediment", "cryon-cryon-depths"), "Sediment is hidden from the deep-sea resource list");
        System.out.println("SEABED_SEDIMENT_PASS: 23 sediment floors, 6 preserved mineral floors, deep-water drill/logistics/processing, ore priority, core mining, real extraction/transport, exact dual outputs, power/input/full-output gates, save/reload and research");
        // The research tree schedules an unrelated desktop loadout refresh after this check.
        System.exit(0);
    }
}
