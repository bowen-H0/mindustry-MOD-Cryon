package example;

import mindustry.Vars;
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

        // ---- ITEM ----
        addAuto(Kind.ITEM, "aluminum", "conquest-core");
        addAuto(Kind.ITEM, "magnesium", "conquest-core");

        // ---- BLOCK ----
        addAuto(Kind.BLOCK, "conquest-core", null); // 如果想让 rootBlock 之外的逻辑也能按名字查到它，可选

        // ---- SECTOR ----
        addAuto(Kind.SECTOR, "aravis-sector-1", "conquest-core");

        // 其余 Aravis 内容按同样格式补在这里
    }

    @Override protected Item findItem(String name){ return CryonContent.item(name); }
    @Override protected Liquid findLiquid(String name){ return CryonContent.liquid(name); }
    @Override protected Block findBlock(String name){ return CryonContent.block(name); }
    @Override protected UnitType findUnit(String name){ return CryonContent.unit(name); }
    @Override protected SectorPreset findSector(String name){ return CryonContent.sector(name); }

    @Override protected Block rootBlock(){ return CryonContent.block("core-conquest"); }
    @Override protected Planet rootPlanet(){ return Vars.content.planet("cryon-aravis"); }

    public static void load(){
        new AravisTechTree().run();
    }
}