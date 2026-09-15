package cryon.TechTree;

import arc.Core;
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

        // ---- BLOCK ----
        addAuto(Kind.BLOCK, "anti-wind-conveyor", "core-conquest");
        addAuto(Kind.BLOCK, "stack-router", "anti-wind-conveyor");


        addAuto(Kind.BLOCK, "aeolian-drill", "core-conquest");
        addAuto(Kind.BLOCK, "aeolian-node", "aeolian-drill");
        addAuto(Kind.BLOCK, "wind-turbine", "aeolian-node");
        addAuto(Kind.BLOCK, "metallurgical-furnace", "aeolian-drill");


        addAuto(Kind.BLOCK, "chisel", "core-conquest");
        addAuto(Kind.BLOCK, "iron-wall", "chisel");
        addAuto(Kind.BLOCK, "iron-wall-large", "iron-wall");


        // ---- ITEM ----
        addAuto(Kind.ITEM, "ferrum", "core-conquest");
        addAuto(Kind.ITEM, "copper", "ferrum");
        addAuto(Kind.ITEM, "lead", "copper");

        addAuto(Kind.ITEM, "sand", "ferrum");

        addAuto(Kind.ITEM, "coal", "ferrum");





        // ---- SECTOR ----
        addAuto(Kind.SECTOR, "landing", "core-conquest");


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