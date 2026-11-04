package cryon.TechTree;

import arc.Core;
import cryon.Features.CryonContent;
import mindustry.Vars;
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.type.Planet;
import mindustry.type.SectorPreset;
import mindustry.type.UnitType;
import mindustry.world.Block;

public class PorphyraTechTree extends ModPlanetTechTree{
    public PorphyraTechTree(){ super("cryon"); }

    @Override protected void registerEntries(){
        String[] sectors = {"porphyra-landing", "porphyra-glassfield", "porphyra-recrystallization",
                "porphyra-lightband", "porphyra-pale-ridge", "porphyra-phase-scar",
                "porphyra-neptunium-basin", "porphyra-ashen-arc", "porphyra-heavy-element",
                "porphyra-final-core"};
        addAuto(Kind.SECTOR, sectors[0], "core-thread");
        for(int i = 1; i < sectors.length; i++) addAuto(Kind.SECTOR, sectors[i], sectors[i - 1]);

        addAuto(Kind.BLOCK, "core-process", "core-thread");
        addAuto(Kind.BLOCK, "core-kernel", "core-process");
        sectorGate(sectors[2], "core-process");
        sectorGate(sectors[6], "core-kernel");

        addAuto(Kind.ITEM, "rhenium", "core-thread");
        addAuto(Kind.ITEM, "gamma-crystal", "core-thread");
        addAuto(Kind.ITEM, "technetium", "rhenium");
        addAuto(Kind.ITEM, "promethium", "technetium");
        addAuto(Kind.ITEM, "neptunium", "promethium");
        addAuto(Kind.ITEM, "oganesson", "neptunium");
        sectorGate(sectors[1], "technetium");
        sectorGate(sectors[3], "promethium");
        sectorGate(sectors[5], "neptunium");
        sectorGate(sectors[7], "oganesson");

        addAuto(Kind.BLOCK, "porphyra-rhenium-wall", "core-thread");
        addAuto(Kind.BLOCK, "porphyra-rhenium-wall-large", "porphyra-rhenium-wall");
        addAuto(Kind.BLOCK, "porphyra-gamma-wall", "porphyra-rhenium-wall");
        addAuto(Kind.BLOCK, "porphyra-gamma-wall-large", "porphyra-gamma-wall");
        addAuto(Kind.BLOCK, "porphyra-neptunium-wall", "porphyra-gamma-wall");
        addAuto(Kind.BLOCK, "porphyra-neptunium-wall-large", "porphyra-neptunium-wall");
        sectorGate(sectors[1], "porphyra-gamma-wall");
        sectorGate(sectors[5], "porphyra-neptunium-wall");

        addAuto(Kind.BLOCK, "porphyra-duct", "core-thread");
        addAuto(Kind.BLOCK, "porphyra-router", "porphyra-duct");
        addAuto(Kind.BLOCK, "porphyra-junction", "porphyra-duct");
        addAuto(Kind.BLOCK, "porphyra-bridge", "porphyra-duct");
        addAuto(Kind.BLOCK, "porphyra-sorter", "porphyra-router");
        addAuto(Kind.BLOCK, "porphyra-unloader", "porphyra-router");
        addAuto(Kind.BLOCK, "porphyra-mass-driver", "porphyra-bridge");
        addAuto(Kind.BLOCK, "porphyra-conduit", "porphyra-duct");
        addAuto(Kind.BLOCK, "porphyra-liquid-router", "porphyra-conduit");
        addAuto(Kind.BLOCK, "porphyra-liquid-tank", "porphyra-liquid-router");
        addAuto(Kind.BLOCK, "porphyra-shore-pump", "porphyra-conduit");
        addAuto(Kind.LIQUID, "gamma-brine", "porphyra-shore-pump");
        addAuto(Kind.LIQUID, "crystal-slurry", "porphyra-shore-pump");
        sectorGate(sectors[1], "porphyra-shore-pump");
        addAuto(Kind.BLOCK, "porphyra-node", "core-thread");
        addAuto(Kind.BLOCK, "porphyra-solar", "porphyra-node");
        addAuto(Kind.BLOCK, "porphyra-battery", "porphyra-solar");
        addAuto(Kind.BLOCK, "porphyra-reactor", "porphyra-solar");
        addAuto(Kind.BLOCK, "porphyra-drill", "core-thread");
        addAuto(Kind.BLOCK, "porphyra-deep-drill", "porphyra-drill");
        addAuto(Kind.BLOCK, "porphyra-plate-press", "porphyra-drill");
        addAuto(Kind.ITEM, "rhenium-plate", "porphyra-plate-press");
        addAuto(Kind.BLOCK, "porphyra-plasma-forge", "porphyra-conduit");
        addAuto(Kind.LIQUID, "porphyra-plasma", "porphyra-plasma-forge");
        sectorGate(sectors[2], "porphyra-deep-drill", "porphyra-battery");
        sectorGate(sectors[1], "porphyra-sorter", "porphyra-unloader");
        sectorGate(sectors[5], "porphyra-mass-driver");
        sectorGate(sectors[3], "porphyra-plasma-forge");
        sectorGate(sectors[4], "porphyra-reactor");

        addAuto(Kind.BLOCK, "porphyra-protocol-encoder", "porphyra-drill");
        addAuto(Kind.ITEM, "protocol-point", "porphyra-protocol-encoder");
        addAuto(Kind.BLOCK, "porphyra-trade-tower", "porphyra-protocol-encoder");
        addAuto(Kind.ITEM, "silicon", "porphyra-trade-tower");
        addAuto(Kind.ITEM, "graphite", "porphyra-trade-tower");
        addAuto(Kind.ITEM, "titanium", "porphyra-trade-tower");
        addAuto(Kind.BLOCK, "porphyra-rhenium-press", "porphyra-trade-tower");
        addAuto(Kind.ITEM, "laminated-rhenium", "porphyra-rhenium-press");
        addAuto(Kind.BLOCK, "porphyra-crystal-stabilizer", "porphyra-rhenium-press");
        addAuto(Kind.ITEM, "phase-crystal", "porphyra-crystal-stabilizer");
        addAuto(Kind.BLOCK, "porphyra-alloy-forge", "porphyra-crystal-stabilizer");
        addAuto(Kind.ITEM, "porphyra-alloy", "porphyra-alloy-forge");
        addAuto(Kind.BLOCK, "porphyra-plasma-condenser", "porphyra-crystal-stabilizer");
        addAuto(Kind.BLOCK, "porphyra-laser-defense", "porphyra-gamma-wall");
        sectorGate(sectors[1], "porphyra-protocol-encoder", "porphyra-laser-defense");
        sectorGate(sectors[3], "porphyra-crystal-stabilizer");
        sectorGate(sectors[5], "porphyra-alloy-forge", "porphyra-plasma-condenser");

        String[] turrets = {"etch", "excite", "pulse", "dissociate", "deflect",
                "glow", "detune", "overload", "critical", "collapse"};
        for(int i = 0; i < turrets.length; i++){
            String name = "porphyra-" + turrets[i];
            addAuto(Kind.BLOCK, name, i == 0 ? "core-thread" : "porphyra-" + turrets[i - 1]);
            if(i > 0) sectorGate(sectors[i - 1], name);
        }

        addAuto(Kind.BLOCK, "porphyra-unit-factory", "porphyra-plate-press");
        String[] lines = {"receptor,synapse,ganglion,cortex,connectome",
                "monomer,dimer,oligomer,polymer,macromolecule",
                "particle,momentum,inertia,gravity,singularity"};
        for(String line : lines){
            String[] units = line.split(",");
            for(int i = 0; i < units.length; i++){
                addAuto(Kind.UNIT, units[i], i == 0 ? "porphyra-unit-factory" : units[i - 1]);
                if(i > 0) sectorGate(sectors[i * 2 - 1], units[i]);
            }
        }
        for(int tier = 2; tier <= 5; tier++){
            String name = "porphyra-reconstructor-" + tier;
            addAuto(Kind.BLOCK, name, tier == 2 ? "porphyra-unit-factory" : "porphyra-reconstructor-" + (tier - 1));
            sectorGate(sectors[(tier - 1) * 2 - 1], name);
        }
    }

    @Override protected Item findItem(String name){ return CryonContent.item(name); }
    @Override protected Liquid findLiquid(String name){ return CryonContent.liquid(name); }
    @Override protected Block findBlock(String name){ return CryonContent.block(name); }
    @Override protected UnitType findUnit(String name){ return CryonContent.unit(name); }
    @Override protected SectorPreset findSector(String name){ return CryonContent.sector(name); }
    @Override protected String rootNodeName(){ return Core.bundle.get("planet.cryon-porphyra.name"); }
    @Override protected Block rootBlock(){ return CryonContent.block("core-thread"); }
    @Override protected Planet rootPlanet(){ return Vars.content.planet("cryon-porphyra"); }

    public static void load(){ new PorphyraTechTree().run(); }
}
