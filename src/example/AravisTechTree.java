package example;

import arc.Core;
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
        addAuto(Kind.BLOCK, "core-conquest", null);

        // ---- BLOCK ----
        addAuto(Kind.BLOCK, "aeolian-drill", "core-conquest");

        // ---- ITEM ----
        addAuto(Kind.ITEM, "ferrum", "core-conquest");



        // ---- SECTOR ----
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

    public static void load(){
        new AravisTechTree().run();
    }
}