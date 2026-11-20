package cryon.TechTree;

import arc.Core;
import cryon.Content.CryonItems;
import cryon.Features.CryonContent;
import mindustry.Vars;
import mindustry.ctype.Content;
import mindustry.type.SectorPreset;
import mindustry.type.*;
import mindustry.world.Block;

public class AravisTechTree extends ModPlanetTechTree {

    public AravisTechTree(){
        super("cryon");
    }

    @Override
    protected void registerEntries(){
        // 根节点本身不写进 entries，它由 rootBlock() 指定
        // 这里只写挂在根节点下面的子节点
        addAuto(Kind.BLOCK, "core-conquest", null);
        addAuto(Kind.BLOCK, "core-dominion", "core-conquest");

        // ---- BLOCK ----
        addAuto(Kind.BLOCK, "anti-wind-conveyor", "core-conquest");
        addAuto(Kind.BLOCK, "stack-router", "anti-wind-conveyor");
        addAuto(Kind.BLOCK, "ferrum-unloader", "anti-wind-conveyor");
        addAuto(Kind.BLOCK, "manganese-duct", "anti-wind-conveyor");
        addAuto(Kind.BLOCK, "manganese-crosser", "manganese-duct");
        addAuto(Kind.BLOCK, "manganese-bridge", "manganese-duct");



        addAuto(Kind.BLOCK, "manganese-router", "manganese-crosser");

        addAuto(Kind.BLOCK, "manganese-sorter", "manganese-crosser");
        addAuto(Kind.BLOCK, "manganese-inverted-sorter", "manganese-sorter");


        addAuto(Kind.BLOCK, "manganese-overflow-gate", "manganese-crosser");
        addAuto(Kind.BLOCK, "manganese-underflow-gate", "manganese-overflow-gate");







        addAuto(Kind.BLOCK, "aeolian-drill", "core-conquest");
        addAuto(Kind.BLOCK, "extension-drill", "aeolian-drill");
        addAuto(Kind.BLOCK, "aravis-impact-drill", "extension-drill");
        addAuto(Kind.BLOCK, "aeolian-pump", "aeolian-drill");
        addAuto(Kind.LIQUID, "water", "aeolian-pump");
        addAuto(Kind.LIQUID, "oil", "aeolian-pump");
        addAuto(Kind.LIQUID, "slag", "aeolian-pump");
        addAuto(Kind.BLOCK, "aeolian-conduit", "aeolian-pump");
        addAuto(Kind.BLOCK, "aeolian-liquid-router", "aeolian-conduit");
        addAuto(Kind.BLOCK, "aeolian-liquid-junction", "aeolian-conduit");
        addAuto(Kind.BLOCK, "aeolian-liquid-bridge", "aeolian-conduit");
        addAuto(Kind.BLOCK, "aeolian-node", "aeolian-drill");
        addAuto(Kind.BLOCK, "large-team-flag-projector", "aeolian-node");
        addAuto(Kind.BLOCK, "planet-projector", "large-team-flag-projector");
        addAuto(Kind.BLOCK, "wind-turbine", "aeolian-node");
        addAuto(Kind.BLOCK, "coal-burner", "aeolian-node");
        addAuto(Kind.BLOCK, "aeolian-turbine-generator", "coal-burner");
        addAuto(Kind.BLOCK, "aravis-oil-generator", "aeolian-turbine-generator");
        addAuto(Kind.BLOCK, "aravis-slag-generator", "aeolian-turbine-generator");
        addAuto(Kind.BLOCK, "aeolian-solar-panel", "wind-turbine");
        addAuto(Kind.BLOCK, "aeolian-large-solar-panel", "aeolian-solar-panel");

        addAuto(Kind.BLOCK, "metallurgical-furnace", "aeolian-drill");
        addAuto(Kind.BLOCK, "advanced-alloy-furnace", "metallurgical-furnace");
        addAuto(Kind.BLOCK, "aravis-regen-projector", "aeolian-node");


        addAuto(Kind.BLOCK, "fusillade", "core-conquest");
        addAuto(Kind.BLOCK, "chisel", "fusillade");
        addAuto(Kind.BLOCK, "saxum", "chisel");
        addAuto(Kind.BLOCK, "afterglow-cannon", "saxum");
        addAuto(Kind.BLOCK, "purge", "afterglow-cannon");
        addAuto(Kind.BLOCK, "terra", "saxum");
        addAuto(Kind.BLOCK, "fractura", "chisel");
        addAuto(Kind.BLOCK, "volans", "fusillade");

        addAuto(Kind.BLOCK, "arche", "fusillade");
        addAuto(Kind.BLOCK, "emberglow", "arche");
        addAuto(Kind.BLOCK, "incineration", "emberglow");


        addAuto(Kind.BLOCK, "iron-wall", "fusillade");
        addAuto(Kind.BLOCK, "iron-wall-large", "iron-wall");
        addAuto(Kind.BLOCK, "steel-wall", "iron-wall");
        addAuto(Kind.BLOCK, "steel-wall-large", "steel-wall");
        addAuto(Kind.BLOCK, "armor-alloy-wall", "steel-wall");
        addAuto(Kind.BLOCK, "armor-alloy-wall-large", "armor-alloy-wall");

        addAuto(Kind.BLOCK, "mech-unit-factory", "core-conquest");
        addAuto(Kind.BLOCK, "aravis-army-headquarters", "mech-unit-factory");
        addAuto(Kind.BLOCK, "aravis-air-headquarters", "aravis-army-headquarters");
        add(Kind.UNIT, "aravis-abrade", "aravis-army-headquarters", r(CryonItems.ferrum, 180, CryonItems.steel, 100, CryonContent.item("silicon"), 80));
        add(Kind.UNIT, "aravis-rattle", "aravis-abrade", r(CryonItems.hardSteel, 220, CryonItems.armorAlloy, 160, CryonContent.item("silicon"), 180));
        add(Kind.UNIT, "aravis-usurp", "aravis-rattle", r(CryonItems.hardSteel, 450, CryonItems.armorAlloy, 320, CryonItems.nickel, 220));
        add(Kind.UNIT, "aravis-deter", "aravis-usurp", r(CryonItems.armorAlloy, 800, CryonContent.item("tungsten"), 500, CryonContent.item("silicon"), 600));
        add(Kind.UNIT, "aravis-overbear", "aravis-deter", r(CryonItems.armorAlloy, 1800, CryonContent.item("tungsten"), 1200, CryonContent.item("silicon"), 1400));
        for(String name : new String[]{"light-tank", "flak-car", "field-artillery", "hover-tank", "shorekeeper"}){
            addAuto(Kind.UNIT, "aravis-" + name, "aravis-army-headquarters");
        }
        for(String name : new String[]{"needlewing", "violet-falcon", "ash-kite"}){
            addAuto(Kind.UNIT, "aravis-" + name, "aravis-air-headquarters");
        }
        addAuto(Kind.BLOCK, "aravis-unit-repairer", "mech-unit-factory");
        addAuto(Kind.BLOCK, "mech-unit-reconstructor", "mech-unit-factory");
        addAuto(Kind.BLOCK, "advanced-mech-reconstructor", "mech-unit-reconstructor");


        add(Kind.UNIT, "jaspis", "mech-unit-factory",r(CryonItems.hardSteel, 60, CryonContent.item("silicon"), 100, CryonItems.ferrum, 100));
        add(Kind.UNIT, "nitrum", "advanced-mech-reconstructor",r(CryonItems.hardSteel, 60, CryonContent.item("silicon"), 100, CryonItems.ferrum, 100));
        add(Kind.UNIT, "obsidianum", "mech-unit-reconstructor",r(CryonItems.armorAlloy, 160, CryonContent.item("silicon"), 200, CryonContent.item("lead"), 200));
        add(Kind.UNIT, "hematites", "nitrum",r(CryonItems.armorAlloy, 160, CryonContent.item("silicon"), 200, CryonContent.item("lead"), 200));







        // ---- ITEM ----
        addAuto(Kind.ITEM, "ferrum", "core-conquest");
        addAuto(Kind.ITEM, "copper", "ferrum");
        addAuto(Kind.ITEM, "lead", "copper");

        addAuto(Kind.ITEM, "manganese", "ferrum");
        addAuto(Kind.ITEM, "steel", "ferrum");
        addAuto(Kind.ITEM, "hard-steel", "steel");

        addAuto(Kind.ITEM, "armor-alloy", "manganese");
        addAuto(Kind.ITEM, "ashes-alloy", "armor-alloy");




        addAuto(Kind.ITEM, "sand", "ferrum");
        addAuto(Kind.ITEM, "av-red-sand", "sand");



        addAuto(Kind.ITEM, "coal", "ferrum");
        addAuto(Kind.ITEM, "silicon", "coal");

        addAuto(Kind.ITEM, "nickel", "manganese");
        addAuto(Kind.ITEM, "tungsten", "nickel");







        // ---- SECTOR ----
        addAuto(Kind.SECTOR, "landing", "core-conquest");
        sectorReq("landing", findSector("desolate-plain"));
        addAuto(Kind.SECTOR, "extension", "landing");
        addAuto(Kind.SECTOR, "ridge", "extension");
        addAuto(Kind.SECTOR, "checkpoint", "ridge");
        addAuto(Kind.SECTOR, "dunes", "checkpoint");
        addAuto(Kind.SECTOR, "ashes", "dunes");
        addAuto(Kind.SECTOR, "aravis-aegis", "ashes");
        addAuto(Kind.SECTOR, "terminus", "aravis-aegis");

        sectorGate("ridge", "aravis-army-headquarters", "aravis-air-headquarters",
                "aravis-light-tank", "aravis-flak-car", "aravis-field-artillery",
                "aravis-hover-tank", "aravis-shorekeeper", "aravis-needlewing",
                "aravis-violet-falcon", "aravis-ash-kite");
        sectorGate("checkpoint", "saxum", "fractura");





    }

    @Override protected Item findItem(String name){ return CryonContent.item(name); }
    @Override protected Liquid findLiquid(String name){ return CryonContent.liquid(name); }
    @Override protected Block findBlock(String name){ return CryonContent.block(name); }
    @Override protected UnitType findUnit(String name){ return CryonContent.unit(name); }
    @Override protected SectorPreset findSector(String name){ return CryonContent.sector(name); }
    @Override
    protected String rootNodeName(){
        return Core.bundle.get("planet.cryon-aravis.name");
    }

    @Override protected Block rootBlock(){ return CryonContent.block("core-conquest"); }
    @Override protected Planet rootPlanet(){ return Vars.content.planet("cryon-aravis"); }

    @Override
    protected void afterLoad(mindustry.content.TechTree.TechNode root, Planet planet){
        SectorPreset dunes = findSector("dunes");
        if(dunes != null){
            var mapRules = dunes.rules;
            dunes.rules = rules -> {
                mapRules.get(rules);
                rules.attackMode = false;
                rules.waves = true;
                rules.winWave = 50;
            };
        }
    }

    public static void load(){
        new AravisTechTree().run();
    }
}
