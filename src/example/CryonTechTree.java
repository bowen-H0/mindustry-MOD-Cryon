package example;

import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.TechTree.TechNode;
import mindustry.type.SectorPreset;
import mindustry.type.*;
import mindustry.world.Block;
import mindustry.world.blocks.storage.CoreBlock;

import static mindustry.Vars.schematics;
import static mindustry.Vars.universe;

public class CryonTechTree extends ModPlanetTechTree {

    public CryonTechTree(){
        super("cryon");
    }

    @Override
    protected void registerEntries(){
        // ---- ITEM(全部 auto) ----
        addAuto(Kind.ITEM, "aluminum", "core-pioneer");
        addAuto(Kind.ITEM, "crystal-sand", "magnesium");
        addAuto(Kind.ITEM, "dry-ice", "aluminum");
        addAuto(Kind.ITEM, "farstar-alloy", "titanium");
        addAuto(Kind.ITEM, "graphite", "dry-ice");
        addAuto(Kind.ITEM, "magnesium", "aluminum");
        addAuto(Kind.ITEM, "neutronite", "nickel");
        addAuto(Kind.ITEM, "nickel", "titanium");
        addAuto(Kind.ITEM, "phase-fabric", "neutronite");
        addAuto(Kind.ITEM, "quartz", "silicon");
        addAuto(Kind.ITEM, "salt", "aluminum");
        addAuto(Kind.ITEM, "scrap", "magnesium");
        addAuto(Kind.ITEM, "silicon", "crystal-sand");
        addAuto(Kind.ITEM, "titanium", "scrap");
        addAuto(Kind.ITEM, "surge-alloy", "nickel");
        addAuto(Kind.ITEM, "nano-material", "phase-fabric");

        addAuto(Kind.ITEM, "sodium", "salt");
        addAuto(Kind.ITEM, "cryo-alloy", "sodium");




        // ---- LIQUID(全部 auto) ----
        addAuto(Kind.LIQUID, "deuterium", "hydrogen");
        addAuto(Kind.LIQUID, "hydrogen", "water");
        addAuto(Kind.LIQUID, "nitrogen", "water");
        addAuto(Kind.LIQUID, "ozone", "water");
        addAuto(Kind.LIQUID, "slag", "water");
        addAuto(Kind.LIQUID, "tritium", "deuterium");
        addAuto(Kind.LIQUID, "water", "dry-ice");
        addAuto(Kind.LIQUID, "cryofluid", "water");
        addAuto(Kind.LIQUID, "chlorine", "water");


        // ---- BLOCK ----
        addAuto(Kind.BLOCK, "abyss", "penetrate");



        addAuto(Kind.BLOCK, "aggregated-wall", "aluminum-wall-large");
        addAuto(Kind.BLOCK, "aggregated-wall-large", "aggregated-wall");
        addAuto(Kind.BLOCK, "agitator-tower", "spiral");
        addAuto(Kind.BLOCK, "aluminum-node", "hydrothermal-generator");
        addAuto(Kind.BLOCK, "superconducting-node", "aluminum-node");
        addAuto(Kind.BLOCK, "power-tower-large", "superconducting-node");

        addAuto(Kind.BLOCK, "aluminum-wall", "spiral");
        addAuto(Kind.BLOCK, "aluminum-wall-large", "aluminum-wall");
        addAuto(Kind.BLOCK, "cavity", "spiral");
        addAuto(Kind.BLOCK, "deluge", "cavity");


        addAuto(Kind.BLOCK, "nickel-wall", "cryo-titanium-wall-large");
        addAuto(Kind.BLOCK, "nickel-wall-large", "nickel-wall");

        addAuto(Kind.BLOCK, "composite-wall", "cryo-titanium-wall-large");
        addAuto(Kind.BLOCK, "composite-wall-large", "composite-wall");
        addAuto(Kind.BLOCK, "charged-surge-wall", "cryo-titanium-wall-large");
        addAuto(Kind.BLOCK, "titanium-blast-door", "cryo-titanium-wall-large");


        addAuto(Kind.BLOCK, "charged-surge-wall-large", "charged-surge-wall");

        addAuto(Kind.BLOCK, "cryo-alloy-wall", "charged-surge-wall-large");
        addAuto(Kind.BLOCK, "cryo-alloy-wall-large", "cryo-alloy-wall");
        addAuto(Kind.BLOCK, "nano-wall-large", "charged-surge-wall-large");






        addAuto(Kind.BLOCK, "core-pioneer", null); // 根节点,单独处理
        addAuto(Kind.BLOCK, "core-outpost", "core-pioneer");


        addAuto(Kind.BLOCK, "cryo-conduit", "core-pioneer");
        addAuto(Kind.BLOCK, "reinforced-vacuum-duct", "vacuum-conduit");


        addAuto(Kind.BLOCK, "cryo-constructor", "silicon-separator");
        addAuto(Kind.BLOCK, "cryo-container", "cryo-unloader");

        addAuto(Kind.BLOCK, "cryo-electric-heater", "magnesium-converter");
        addAuto(Kind.BLOCK, "cryo-electrolyzer", "cryon-water-extractor");
        addAuto(Kind.BLOCK, "cryo-heat-redirector", "cryo-electric-heater");
        addAuto(Kind.BLOCK, "cryo-illuminator", "hydrothermal-generator");


        addAuto(Kind.BLOCK, "cryo-liquid-bridge", "cryo-conduit");
        addAuto(Kind.BLOCK, "cryo-phase-fabric-liquid-bridge", "cryo-liquid-bridge");

        addAuto(Kind.BLOCK, "cryo-liquid-container", "cryo-conduit");
        addAuto(Kind.BLOCK, "cryo-liquid-junction", "cryo-conduit");
        addAuto(Kind.BLOCK, "cryo-liquid-router", "cryo-conduit");
        addAuto(Kind.BLOCK, "cryo-liquid-tank", "cryo-liquid-container");
        addAuto(Kind.BLOCK, "cryo-message", "silicon-separator");
        addAuto(Kind.BLOCK, "cryo-processor", "cryo-message");
        addAuto(Kind.BLOCK, "cryo-payload-conveyor", "vacuum-conduit");
        addAuto(Kind.BLOCK, "cryo-unloader", "vacuum-conduit");
        addAuto(Kind.BLOCK, "nickel-conveyor", "cryo-unloader");
        addAuto(Kind.BLOCK, "cryo-payload-conveyor-large", "cryo-payload-conveyor");
        addAuto(Kind.BLOCK, "cryo-mender", "hydrothermal-generator");
        addAuto(Kind.BLOCK, "cryo-repair-tower", "cryo-mender");
        addAuto(Kind.BLOCK, "overdrive-tower", "cryo-mender");
        addAuto(Kind.BLOCK, "nano-projector", "mend-projector-advanced");
        addAuto(Kind.BLOCK, "mend-projector-advanced", "cryo-mender");





        addAuto(Kind.BLOCK, "cryo-titanium-wall", "aluminum-wall-large");
        addAuto(Kind.BLOCK, "cryo-titanium-wall-large", "cryo-titanium-wall");
        addAuto(Kind.BLOCK, "cryo-vault", "cryo-container");
        addAuto(Kind.BLOCK, "cryon-water-extractor", "magnesium-converter");
        addAuto(Kind.BLOCK, "denial", "spiral");
        addAuto(Kind.BLOCK, "deuterium-reactor", "magnesium-generator");
        addAuto(Kind.BLOCK, "neutronite-decay-generator", "deuterium-reactor");
        addAuto(Kind.BLOCK, "neutronite-thermal-battery", "neutronite-decay-generator");


        addAuto(Kind.BLOCK, "phase-reactor", "deuterium-reactor");

        addAuto(Kind.BLOCK, "surge-reactor", "phase-reactor");

        addAuto(Kind.BLOCK, "hydrogen-generator", "magnesium-generator");
        addAuto(Kind.BLOCK, "ozone-generator", "hydrogen-generator");

        addAuto(Kind.BLOCK, "piezo-generator", "magnesium-generator");


        addAuto(Kind.BLOCK, "constructor-node", "aluminum-node");
        addAuto(Kind.BLOCK, "construct-wave-emitter", "constructor-node");
        addAuto(Kind.BLOCK, "constructor-drill", "construct-wave-emitter");
        addAuto(Kind.BLOCK, "wave-reactor", "construct-wave-emitter");




        addAuto(Kind.BLOCK, "dry-ice-sublimator", "magnesium-converter");
        addAuto(Kind.BLOCK, "titanium-water-condenser", "dry-ice-sublimator");


        addAuto(Kind.BLOCK, "farstar-forge", "magnesium-converter");
        addAuto(Kind.BLOCK, "surge-alloy-forge", "farstar-forge");


        addAuto(Kind.BLOCK, "flux-barrier", "micro-projector");
        addAuto(Kind.BLOCK, "aegis-barrier", "flux-barrier");

        addAuto(Kind.BLOCK, "gem", "spiral");
        addAuto(Kind.BLOCK, "heating-furnace", "cryo-electric-heater");
        addAuto(Kind.BLOCK, "hydrothermal-generator", "shattering-drill");

        addAuto(Kind.BLOCK, "isotope-separator", "cryo-electrolyzer");
        addAuto(Kind.BLOCK, "molten-salt-electrolyzer", "cryo-electrolyzer");
        addAuto(Kind.BLOCK, "fusion-casting-furnace", "molten-salt-electrolyzer");
        addAuto(Kind.BLOCK, "sodium-carbon-fixer", "molten-salt-electrolyzer");




        addAuto(Kind.BLOCK, "magnesium-converter", "silicon-separator");
        addAuto(Kind.BLOCK, "electric-arc-graphitizer", "magnesium-converter");
        addAuto(Kind.BLOCK, "magnesium-generator", "silicon-separator");
        addAuto(Kind.BLOCK, "melting-drill", "hydrothermal-generator");
        addAuto(Kind.BLOCK, "micro-projector", "cryo-mender");
        addAuto(Kind.BLOCK, "nebula", "gem");


        addAuto(Kind.BLOCK, "neutron-activator", "quartz-reactor");
        addAuto(Kind.BLOCK, "nickel-drill", "titanium-drill");
        addAuto(Kind.BLOCK, "nitrogen-separator", "magnesium-converter");
        addAuto(Kind.BLOCK, "overload-battery", "hydrothermal-generator");
        addAuto(Kind.BLOCK, "battery-giant", "overload-battery");


        addAuto(Kind.BLOCK, "penetrate", "cavity");
        addAuto(Kind.BLOCK, "phase-constructor", "neutron-activator");
        addAuto(Kind.BLOCK, "nano-material-weaver", "phase-constructor");
        addAuto(Kind.BLOCK, "quartz-reactor", "magnesium-converter");
        addAuto(Kind.BLOCK, "scrap-pyrolyzer", "magnesium-converter");
        addAuto(Kind.BLOCK, "shattering-drill", "core-pioneer");
        addAuto(Kind.BLOCK, "silicon-separator", "hydrothermal-generator");

        addAuto(Kind.BLOCK, "slag-extractor", "hydrothermal-generator");

        addAuto(Kind.BLOCK, "slag-power-generator", "magnesium-generator");

        addAuto(Kind.BLOCK, "spark", "gem");
        addAuto(Kind.BLOCK, "spiral", "core-pioneer");
        addAuto(Kind.BLOCK, "titanium-drill", "melting-drill");
        addAuto(Kind.BLOCK, "coagulation-drill", "melting-drill");


        addAuto(Kind.BLOCK, "torrent", "spiral");

        addAuto(Kind.BLOCK, "vacuum-bridge", "vacuum-conduit");
        addAuto(Kind.BLOCK, "cryo-phase-fabric-bridge", "vacuum-bridge");

        addAuto(Kind.BLOCK, "vacuum-conduit", "core-pioneer");
        addAuto(Kind.BLOCK, "vacuum-crosser", "vacuum-conduit");
        addAuto(Kind.BLOCK, "vacuum-inverted-sorter", "vacuum-sorter");
        addAuto(Kind.BLOCK, "vacuum-router", "vacuum-conduit");
        addAuto(Kind.BLOCK, "vacuum-sorter", "vacuum-crosser");
        addAuto(Kind.BLOCK, "vacuum-overflow-gate", "vacuum-crosser");
        addAuto(Kind.BLOCK, "vacuum-underflow-gate", "vacuum-overflow-gate");
        addAuto(Kind.BLOCK, "small-launch-pad", "vacuum-conduit");
        addAuto(Kind.BLOCK, "small-landing-pad", "small-launch-pad");
        addAuto(Kind.BLOCK, "orbital-ejector", "small-launch-pad");
        addAuto(Kind.BLOCK, "orbital-receiver", "orbital-ejector");

        addAuto(Kind.BLOCK, "cryo-mass-driver", "vacuum-conduit");



        addAuto(Kind.BLOCK, "titanium-cargo-loader", "vacuum-conduit");
        addAuto(Kind.BLOCK, "titanium-cargo-unload-point", "titanium-cargo-loader");


        addAuto(Kind.BLOCK, "aluminum-pump", "cryo-conduit");
        addAuto(Kind.BLOCK, "glacier-melter", "cryon-water-extractor");



        addAuto(Kind.BLOCK, "vulcan", "torrent");
        addAuto(Kind.BLOCK, "aurora", "gem");
        addAuto(Kind.BLOCK, "railgun", "aurora");
        addAuto(Kind.BLOCK, "critical", "deluge");


        addAuto(Kind.BLOCK, "quantum", "vulcan");
        addAuto(Kind.BLOCK, "beacon", "gem");




        // ---- UNIT_BLOCK ----
        addAuto(Kind.UNIT_BLOCK, "mechanical-assembler", "benignitas");
        addAuto(Kind.UNIT_BLOCK, "mechanical-factory", "unit-projector");
        addAuto(Kind.UNIT_BLOCK, "t2factory", "unit-projector");
        addAuto(Kind.UNIT_BLOCK, "t3universal-assembler", "t2factory");
        addAuto(Kind.UNIT_BLOCK, "t4universal-assembler", "t3universal-assembler");
        addAuto(Kind.UNIT_BLOCK, "t5universal-assembler", "t4universal-assembler");




        addAuto(Kind.UNIT_BLOCK, "unit-projector", "core-pioneer");

        // ---- UNIT(全部手动花费) ----
        add(Kind.UNIT, "benignitas", "mechanical-factory", r(CryonContent.item("titanium"), 40, CryonContent.item("silicon"), 50));
        add(Kind.UNIT, "bolide", "t2factory", r(CryonContent.item("titanium"), 300, CryonContent.item("magnesium"), 200, CryonContent.item("silicon"), 300));
        add(Kind.UNIT, "buffer", "unit-projector", r(CryonContent.item("magnesium"), 200, CryonContent.item("silicon"), 50));
        add(Kind.UNIT, "comet", "unit-projector", r(CryonContent.item("magnesium"), 100, CryonContent.item("silicon"), 30));
        add(Kind.UNIT, "guardian", "t2factory", r(CryonContent.item("titanium"), 500, CryonContent.item("silicon"), 1000, CryonContent.item("graphite"), 1000));
        add(Kind.UNIT, "littorina", "unit-projector", r(CryonContent.item("magnesium"), 100, CryonContent.item("silicon"), 60));
        add(Kind.UNIT, "murex", "t3universal-assembler", r(CryonContent.item("farstar-alloy"), 500, CryonContent.item("silicon"), 2000, CryonContent.item("phase-fabric"), 3000));
        add(Kind.UNIT, "natica", "t2factory", r(CryonContent.item("titanium"), 500, CryonContent.item("silicon"), 1000, CryonContent.item("graphite"), 1000));
        add(Kind.UNIT, "peak", "t3universal-assembler", r(CryonContent.item("farstar-alloy"), 500, CryonContent.item("silicon"), 2000, CryonContent.item("phase-fabric"), 3000));
        add(Kind.UNIT, "salus", "mechanical-assembler", r(CryonContent.item("titanium"), 2400, CryonContent.item("farstar-alloy"), 2600, CryonContent.item("silicon"), 2300));
        add(Kind.UNIT, "umbra", "t3universal-assembler", r(CryonContent.item("farstar-alloy"), 500, CryonContent.item("silicon"), 2000, CryonContent.item("phase-fabric"), 3000));
        add(Kind.UNIT, "sagitta", "t4universal-assembler", r(CryonContent.item("surge-alloy"), 3000, CryonContent.item("silicon"), 4000, CryonContent.item("graphite"), 3000, CryonContent.item("neutronite"), 1000));
        add(Kind.UNIT, "blaze", "t4universal-assembler", r(CryonContent.item("surge-alloy"), 3000, CryonContent.item("silicon"), 4000, CryonContent.item("graphite"), 3000, CryonContent.item("neutronite"), 1000));
        add(Kind.UNIT, "charonia", "t4universal-assembler", r(CryonContent.item("surge-alloy"), 3000, CryonContent.item("silicon"), 4000, CryonContent.item("graphite"), 3000, CryonContent.item("neutronite"), 1000));
        add(Kind.UNIT, "eternal", "t5universal-assembler", r(CryonContent.item("cryo-alloy"), 4000, CryonContent.item("phase-fabric"), 4000, CryonContent.item("nano-material"), 2000, CryonContent.item("graphite"), 3000, CryonContent.item("silicon"), 5000));
        add(Kind.UNIT, "syrinx", "t5universal-assembler", r(CryonContent.item("cryo-alloy"), 4000, CryonContent.item("phase-fabric"), 4000, CryonContent.item("nano-material"), 2000, CryonContent.item("graphite"), 3000, CryonContent.item("silicon"), 5000));
        add(Kind.UNIT, "hydra", "t5universal-assembler", r(CryonContent.item("cryo-alloy"), 4000, CryonContent.item("phase-fabric"), 4000, CryonContent.item("nano-material"), 2000, CryonContent.item("graphite"), 3000, CryonContent.item("silicon"), 5000));
        // ---- SECTOR(全部 auto,前提条件后续手动补) ----
        addAuto(Kind.SECTOR, "cryon-fusion-bastion", "cryon-neutron-flux-zone");
        addAuto(Kind.SECTOR, "cryon-gravel-ice", "cryon-shattered-abyss");
        addAuto(Kind.SECTOR, "cryon-ice-shoal", "cryon-sector-1");
        addAuto(Kind.SECTOR, "desolate-zone-87", "cryon-sector-glacial-basin");

        addAuto(Kind.SECTOR, "laboratory-088", "desolate-zone-87");

        addAuto(Kind.SECTOR, "cryon-neutron-flux-zone", "cryon-gravel-ice");
        addAuto(Kind.SECTOR, "cryon-sector-1", "core-pioneer");
        addAuto(Kind.SECTOR, "cryon-sector-frost-outpost", "cryon-sector-shattered-shoal");
        addAuto(Kind.SECTOR, "cryon-sector-glacial-basin", "cryon-ice-shoal");
        addAuto(Kind.SECTOR, "cryon-sector-shattered-shoal", "cryon-ice-shoal");
        addAuto(Kind.SECTOR, "cryon-shattered-abyss", "cryon-sector-frost-outpost");
        addAuto(Kind.SECTOR, "titanium-fortress", "cryon-fusion-bastion");
        addAuto(Kind.SECTOR, "silent-tundra", "titanium-fortress");
        addAuto(Kind.SECTOR, "relay-32a", "cryon-fusion-bastion");
        addAuto(Kind.SECTOR, "stellar-observatory", "silent-tundra");
        addAuto(Kind.SECTOR, "exclusion-zone", "cryon-neutron-flux-zone");
        addAuto(Kind.SECTOR, "baryon-bastion", "exclusion-zone");
        addAuto(Kind.SECTOR, "magnificent-rift", "cryon-sector-shattered-shoal");
        addAuto(Kind.SECTOR, "frost-highway", "magnificent-rift");


        // ---- SECTOR 额外前提条件列表 ----
        sectorReq("cryon-ice-shoal",
                CryonContent.block("melting-drill"),
                CryonContent.block("gem"));

        sectorReq("cryon-sector-glacial-basin",
                CryonContent.block("unit-projector"),
                CryonContent.unit("buffer"),
                CryonContent.item("graphite"));

        sectorReq("cryon-sector-frost-outpost",
                CryonContent.sector("cryon-sector-glacial-basin"),
                CryonContent.unit("comet"),
                CryonContent.item("titanium"));

        sectorReq("cryon-shattered-abyss",
                CryonContent.unit("natica"),
                CryonContent.block("t2factory"));

        sectorReq("cryon-neutron-flux-zone",
                CryonContent.unit("salus"),
                CryonContent.block("deuterium-reactor"),
                CryonContent.unit("guardian"),
                CryonContent.item("farstar-alloy"));

        sectorReq("cryon-fusion-bastion",
                CryonContent.block("t3universal-assembler"),
                CryonContent.unit("umbra"));
        //T3后
        sectorReq("laboratory-088",
                CryonContent.block("t3universal-assembler"));
        sectorReq("titanium-fortress",
                CryonContent.sector("laboratory-088"));

        sectorReq("relay-32a",
                CryonContent.sector("titanium-fortress"));
        sectorReq("baryon-bastion",
                CryonContent.sector("titanium-fortress"));
        sectorReq("magnificent-rift",
                CryonContent.sector("baryon-bastion"));



        // ---- SECTOR 占领门控表(方块/单位) ----

        sectorGate("cryon-sector-1",
                "hydrothermal-generator", "gem");

        sectorGate("cryon-ice-shoal", "magnesium-converter", "cryo-electric-heater",
                "cavity", "micro-projector", "cryo-mender",
                "aggregated-wall", "aggregated-wall-large",
                "magnesium-generator", "unit-projector", "cryon-water-extractor");

        sectorGate("cryon-sector-shattered-shoal",
                "titanium-drill", "scrap-pyrolyzer",
                "cryo-titanium-wall", "cryo-titanium-wall-large",
                "dry-ice-sublimator", "penetrate",
                "hydrogen-generator", "ozone-generator",
                "cryo-electrolyzer", "flux-barrier", "mechanical-factory");

        sectorGate("cryon-sector-glacial-basin",
                "slag-extractor", "slag-power-generator", "comet");

        sectorGate("cryon-sector-frost-outpost",
                "t2factory", "denial", "deuterium-reactor",
                "isotope-separator", "quartz-reactor", "torrent",
                "small-launch-pad", "small-landing-pad");

        sectorGate("cryon-shattered-abyss",
                "spark", "abyss", "farstar-forge", "nitrogen-separator");

        sectorGate("cryon-gravel-ice",
                "mechanical-assembler", "titanium-cargo-loader",
                "titanium-cargo-unload-point", "nickel-drill");

        sectorGate("cryon-neutron-flux-zone",
                "neutron-activator", "phase-constructor",
                "t3universal-assembler", "nebula","vulcan");

        sectorGate("cryon-fusion-bastion",
                "phase-reactor", "agitator-tower");
        sectorGate("relay-32a",
                "beacon");
        sectorGate("stellar-observatory",
                "aurora");

        sectorGate("baryon-bastion",
                "surge-alloy-forge","cryo-mass-driver");
        sectorGate("exclusion-zone",
                "nano-material-weaver","quantum","surge-reactor","t4universal-assembler");
        sectorGate("silent-tundra",
                "deluge");
        sectorGate("magnificent-rift",
                "t5universal-assembler");
    }
    @Override protected Item findItem(String name){ return CryonContent.item(name); }
    @Override protected Liquid findLiquid(String name){ return CryonContent.liquid(name); }
    @Override protected Block findBlock(String name){ return CryonContent.block(name); }
    @Override protected UnitType findUnit(String name){ return CryonContent.unit(name); }
    @Override protected SectorPreset findSector(String name){ return CryonContent.sector(name); }

    @Override protected Block rootBlock(){ return CryonContent.block("core-pioneer"); }
    @Override protected Planet rootPlanet(){ return Vars.content.planet("cryon-cryon"); }

    @Override
    protected void afterLoad(TechNode root, Planet planet){
        Time.runTask(10f, () -> {
            Log.info("[CryonTechTree] Refreshing loadout cache...");
            schematics.load();
            for(Block block : Vars.content.blocks()){
                if(block instanceof CoreBlock core){
                    universe.getLoadout(core);
                }
            }
            Log.info("[CryonTechTree] Loadout refresh complete");
        });
    }

    /** 保留原来的调用方式：CryonTechTree.load() */
    public static void load(){
        new CryonTechTree().run();
    }
}