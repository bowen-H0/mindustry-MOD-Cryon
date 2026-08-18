package example;

import arc.struct.*;
import arc.util.Log;
import arc.util.Time;
import example.CryonContent;
import mindustry.Vars;
import mindustry.content.Planets;
import mindustry.content.TechTree;
import mindustry.ctype.*;
import mindustry.game.Objectives.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.consumers.*;
import mindustry.content.TechTree.TechNode;

import static mindustry.Vars.schematics;
import static mindustry.Vars.universe;
import static mindustry.content.TechTree.*;

public class CryonTechTree{

    // ================== 可调参数 ==================

    /** 建筑标记 auto 时,每深一级科技树,材料成本额外增加的比例 */
    static final float DEPTH_COST_STEP = 1.50f;

    /** 物品/液体标记 auto 时的基础花费数量(乘以深度倍率) */
    static final int ITEM_AUTO_BASE = 30;

    // ================== 深度计算 ==================

    static int nextDepth(){
        TechNode ctx = context();
        return ctx == null ? 0 : ctx.depth + 1;
    }

    static float depthMultiplier(){
        return 1f + nextDepth() * DEPTH_COST_STEP;
    }

    // ================== 自动花费 / 自动前提 ==================

    static ItemStack[] scaledBlockCost(Block block){
        float mult = depthMultiplier();
        ItemStack[] base = block.requirements;
        ItemStack[] scaled = new ItemStack[base.length];
        for(int i = 0; i < base.length; i++){
            scaled[i] = new ItemStack(base[i].item, Math.max(1, Math.round(base[i].amount * mult)));
        }
        return scaled;
    }

    static Seq<Objective> autoObjectives(Block block){
        Seq<Objective> objs = new Seq<>();
        ObjectSet<Item> seenItems = new ObjectSet<>();
        ObjectSet<Liquid> seenLiquids = new ObjectSet<>();

        for(ItemStack stack : block.requirements){
            if(seenItems.add(stack.item)){
                objs.add(new Research(stack.item));
            }
        }

        for(Consume c : block.consumers){
            if(c instanceof ConsumeItems ci){
                for(ItemStack stack : ci.items){
                    if(seenItems.add(stack.item)){
                        objs.add(new Research(stack.item));
                    }
                }
            }else if(c instanceof ConsumeLiquid cl){
                if(seenLiquids.add(cl.liquid)){
                    objs.add(new Research(cl.liquid));
                }
            }
            else if(c instanceof ConsumeLiquids cls){
                for(LiquidStack stack : cls.liquids){
                    if(seenLiquids.add(stack.liquid)){
                        objs.add(new Research(stack.liquid));
                    }
                }
            }
        }
        return objs;
    }

    // ================== 数据表定义 ==================

    enum Kind{ ITEM, LIQUID, BLOCK, UNIT_BLOCK, UNIT, SECTOR }

    static class Entry {
        Kind kind;
        String name;
        String parent;
        ItemStack[] manualReqs;
        Object[] prereqs;

        Entry(Kind kind, String name, String parent, ItemStack[] manualReqs) {
            this.kind = kind;
            this.name = name;
            this.parent = parent;
            this.manualReqs = manualReqs;
        }
    }
    static Seq<Entry> entries = new Seq<>();
    static ObjectMap<String, Entry> byName = new ObjectMap<>();
    static ObjectMap<String, Seq<Entry>> childrenOf = new ObjectMap<>();
    static ObjectMap<String, Object[]> sectorPrereqs = new ObjectMap<>();

        static void add(Kind kind, String name, String parent, ItemStack... reqs){
        entries.add(new Entry(kind, name, parent, reqs.length == 0 && kind != Kind.UNIT ? null : reqs));
    }
        static void sectorReq(String sectorName, Object... prereqItems){
            sectorPrereqs.put(sectorName, prereqItems);
        }
    /** 原始表:sector -> [被这个 sector 门控的内容名]  */
    static ObjectMap<String, String[]> sectorGateTable = new ObjectMap<>();

    /** 反向索引:内容名 -> 需要先占领哪些 sector(自动生成,不要手填) */
    static ObjectMap<String, Seq<String>> sectorGateReverse = new ObjectMap<>();

    static void sectorGate(String sectorName, String... gatedNames){
        sectorGateTable.put(sectorName, gatedNames);
    }

    /** 明确标 auto 的重载,避免和"空数组"混淆 */
    static void addAuto(Kind kind, String name, String parent){
        entries.add(new Entry(kind, name, parent, null));
    }

    static ItemStack[] r(Object... pairs){
        ItemStack[] arr = new ItemStack[pairs.length / 2];
        for(int i = 0; i < arr.length; i++){
            arr[i] = new ItemStack((Item)pairs[i * 2], (Integer)pairs[i * 2 + 1]);
        }
        return arr;
    }

    static{
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
        addAuto(Kind.ITEM, "quartz", "salt");
        addAuto(Kind.ITEM, "salt", "aluminum");
        addAuto(Kind.ITEM, "scrap", "magnesium");
        addAuto(Kind.ITEM, "silicon", "crystal-sand");
        addAuto(Kind.ITEM, "titanium", "scrap");
        addAuto(Kind.ITEM, "surge-alloy", "titanium");
        addAuto(Kind.ITEM, "nanofiber", "phase-fabric");




        // ---- LIQUID(全部 auto) ----
        addAuto(Kind.LIQUID, "deuterium", "hydrogen");
        addAuto(Kind.LIQUID, "hydrogen", "water");
        addAuto(Kind.LIQUID, "nitrogen", "water");
        addAuto(Kind.LIQUID, "ozone", "water");
        addAuto(Kind.LIQUID, "slag", "water");
        addAuto(Kind.LIQUID, "tritium", "deuterium");
        addAuto(Kind.LIQUID, "water", "dry-ice");

        // ---- BLOCK ----
        addAuto(Kind.BLOCK, "abyss", "penetrate");



        addAuto(Kind.BLOCK, "aggregated-wall", "aluminum-wall-large");
        addAuto(Kind.BLOCK, "aggregated-wall-large", "aggregated-wall");
        addAuto(Kind.BLOCK, "agitator-tower", "spiral");
        addAuto(Kind.BLOCK, "aluminum-node", "hydrothermal-generator");
        addAuto(Kind.BLOCK, "aluminum-wall", "spiral");
        addAuto(Kind.BLOCK, "aluminum-wall-large", "aluminum-wall");
        addAuto(Kind.BLOCK, "cavity", "spiral");
        addAuto(Kind.BLOCK, "nickel-wall", "cryo-titanium-wall-large");
        addAuto(Kind.BLOCK, "nickel-wall-large", "nickel-wall");

        addAuto(Kind.BLOCK, "composite-wall", "cryo-titanium-wall-large");
        addAuto(Kind.BLOCK, "charged-surge-wall", "cryo-titanium-wall-large");
        addAuto(Kind.BLOCK, "charged-surge-wall-large", "charged-surge-wall");




        addAuto(Kind.BLOCK, "core-pioneer", null); // 根节点,单独处理
        addAuto(Kind.BLOCK, "cryo-conduit", "core-pioneer");
        addAuto(Kind.BLOCK, "cryo-constructor", "silicon-separator");
        addAuto(Kind.BLOCK, "cryo-container", "vacuum-conduit");
        addAuto(Kind.BLOCK, "cryo-electric-heater", "magnesium-converter");
        addAuto(Kind.BLOCK, "cryo-electrolyzer", "cryon-water-extractor");
        addAuto(Kind.BLOCK, "cryo-heat-redirector", "cryo-electric-heater");
        addAuto(Kind.BLOCK, "cryo-illuminator", "hydrothermal-generator");


        addAuto(Kind.BLOCK, "cryo-liquid-bridge", "cryo-conduit");
        addAuto(Kind.BLOCK, "cryo-phase-fabric-bridge", "cryo-liquid-bridge");

        addAuto(Kind.BLOCK, "cryo-liquid-container", "cryo-conduit");
        addAuto(Kind.BLOCK, "cryo-liquid-junction", "cryo-conduit");
        addAuto(Kind.BLOCK, "cryo-liquid-router", "cryo-conduit");
        addAuto(Kind.BLOCK, "cryo-liquid-tank", "cryo-liquid-container");
        addAuto(Kind.BLOCK, "cryo-message", "silicon-separator");
        addAuto(Kind.BLOCK, "cryo-payload-conveyor", "vacuum-conduit");
        addAuto(Kind.BLOCK, "cryo-payload-conveyor-large", "cryo-payload-conveyor");
        addAuto(Kind.BLOCK, "cryo-mender", "hydrothermal-generator");
        addAuto(Kind.BLOCK, "cryo-repair-tower", "cryo-mender");
        addAuto(Kind.BLOCK, "cryo-titanium-wall", "aluminum-wall-large");
        addAuto(Kind.BLOCK, "cryo-titanium-wall-large", "cryo-titanium-wall");
        addAuto(Kind.BLOCK, "cryo-vault", "cryo-container");
        addAuto(Kind.BLOCK, "cryon-water-extractor", "magnesium-converter");
        addAuto(Kind.BLOCK, "denial", "spiral");
        addAuto(Kind.BLOCK, "deuterium-reactor", "magnesium-generator");
        addAuto(Kind.BLOCK, "neutronite-decay-generator", "deuterium-reactor");
        addAuto(Kind.BLOCK, "neutronite-thermal-battery", "neutronite-decay-generator");
        addAuto(Kind.BLOCK, "phase-reactor", "deuterium-reactor");
        addAuto(Kind.BLOCK, "hydrogen-generator", "magnesium-generator");
        addAuto(Kind.BLOCK, "ozone-generator", "hydrogen-generator");

        addAuto(Kind.BLOCK, "constructor-node", "phase-reactor");
        addAuto(Kind.BLOCK, "construct-wave-emitter", "constructor-node");
        addAuto(Kind.BLOCK, "constructor-drill", "construct-wave-emitter");



        addAuto(Kind.BLOCK, "dry-ice-sublimator", "magnesium-converter");
        addAuto(Kind.BLOCK, "farstar-forge", "magnesium-converter");
        addAuto(Kind.BLOCK, "surge-alloy-forge", "farstar-forge");


        addAuto(Kind.BLOCK, "flux-barrier", "micro-projector");
        addAuto(Kind.BLOCK, "gem", "spiral");
        addAuto(Kind.BLOCK, "heating-furnace", "cryo-electric-heater");
        addAuto(Kind.BLOCK, "hydrothermal-generator", "shattering-drill");
        addAuto(Kind.BLOCK, "isotope-separator", "cryo-electrolyzer");
        addAuto(Kind.BLOCK, "magnesium-converter", "silicon-separator");
        addAuto(Kind.BLOCK, "magnesium-generator", "silicon-separator");
        addAuto(Kind.BLOCK, "melting-drill", "hydrothermal-generator");
        addAuto(Kind.BLOCK, "micro-projector", "cryo-mender");
        addAuto(Kind.BLOCK, "nebula", "gem");


        addAuto(Kind.BLOCK, "neutron-activator", "quartz-reactor");
        addAuto(Kind.BLOCK, "nickel-drill", "titanium-drill");
        addAuto(Kind.BLOCK, "nitrogen-separator", "magnesium-converter");
        addAuto(Kind.BLOCK, "overload-battery", "hydrothermal-generator");
        addAuto(Kind.BLOCK, "penetrate", "cavity");
        addAuto(Kind.BLOCK, "phase-constructor", "neutron-activator");
        addAuto(Kind.BLOCK, "nanofiber-weaver", "phase-constructor");
        addAuto(Kind.BLOCK, "quartz-reactor", "magnesium-converter");
        addAuto(Kind.BLOCK, "scrap-pyrolyzer", "magnesium-converter");
        addAuto(Kind.BLOCK, "shattering-drill", "core-pioneer");
        addAuto(Kind.BLOCK, "silicon-separator", "hydrothermal-generator");

        addAuto(Kind.BLOCK, "slag-extractor", "hydrothermal-generator");

        addAuto(Kind.BLOCK, "slag-power-generator", "magnesium-generator");

        addAuto(Kind.BLOCK, "small-launch-pad", "vacuum-conduit");
        addAuto(Kind.BLOCK, "spark", "gem");
        addAuto(Kind.BLOCK, "spiral", "core-pioneer");
        addAuto(Kind.BLOCK, "titanium-drill", "melting-drill");
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
        addAuto(Kind.BLOCK, "titanium-cargo-loader", "vacuum-conduit");
        addAuto(Kind.BLOCK, "titanium-cargo-unload-point", "titanium-cargo-loader");





        addAuto(Kind.BLOCK, "vulcan", "torrent");

        // ---- UNIT_BLOCK ----
        addAuto(Kind.UNIT_BLOCK, "mechanical-assembler", "benignitas");
        addAuto(Kind.UNIT_BLOCK, "mechanical-factory", "unit-projector");
        addAuto(Kind.UNIT_BLOCK, "t2factory", "unit-projector");
        addAuto(Kind.UNIT_BLOCK, "t3universal-assembler", "t2factory");
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

        // ---- SECTOR(全部 auto,前提条件后续手动补) ----
        addAuto(Kind.SECTOR, "cryon-fusion-bastion", "cryon-neutron-flux-zone");
        addAuto(Kind.SECTOR, "cryon-gravel-ice", "cryon-shattered-abyss");
        addAuto(Kind.SECTOR, "cryon-ice-shoal", "cryon-sector-1");
        addAuto(Kind.SECTOR, "cryon-neutron-flux-zone", "cryon-gravel-ice");
        addAuto(Kind.SECTOR, "cryon-sector-1", "core-pioneer");
        addAuto(Kind.SECTOR, "cryon-sector-frost-outpost", "cryon-sector-shattered-shoal");
        addAuto(Kind.SECTOR, "cryon-sector-glacial-basin", "cryon-ice-shoal");
        addAuto(Kind.SECTOR, "cryon-sector-shattered-shoal", "cryon-ice-shoal");
        addAuto(Kind.SECTOR, "cryon-shattered-abyss", "cryon-sector-frost-outpost");

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
                "neutron-activator", "nanofiber-weaver", "phase-constructor",
                "t3universal-assembler", "nebula");

        sectorGate("cryon-fusion-bastion",
                "phase-reactor", "agitator-tower");
    }

    // ================== 索引 ==================

    static void index(){
        for(Entry e : entries) byName.put(e.name, e);
        for(Entry e : entries){
            if(e.parent != null){
                childrenOf.get(e.parent, Seq::new).add(e);
            }
        }

        // 生成 sectorGate 反向索引
        for(var entry : sectorGateTable){
            String sectorName = entry.key;
            for(String gatedName : entry.value){
                sectorGateReverse.get(gatedName, Seq::new).add(sectorName);
            }
        }
    }
    static Seq<Objective> sectorGateObjectives(String name){
        Seq<Objective> objs = new Seq<>();
        Seq<String> sectors = sectorGateReverse.get(name);
        if(sectors == null) return objs;
        for(String sectorName : sectors){
            SectorPreset preset = CryonContent.sector(sectorName);
            if(preset == null){
                Log.warn("[CryonTechTree] sectorGate: sector not found: " + sectorName);
                continue;
            }
            objs.add(new SectorComplete(preset));
        }
        return objs;
    }

    // ================== 自动物品花费 ==================

    static ItemStack[] autoItemCost(String parentName){
        Entry parentEntry = byName.get(parentName);
        if(parentEntry == null || parentEntry.kind != Kind.ITEM) return new ItemStack[]{};
        Item parentItem = CryonContent.item(parentEntry.name);
        int amount = Math.round(ITEM_AUTO_BASE * depthMultiplier());
        return new ItemStack[]{ new ItemStack(parentItem, amount) };
    }

    // ================== 递归建树 ==================

    static void buildNode(Entry e){
        Planet cryonPlanet = Vars.content.planet("cryon-cryon");

        switch(e.kind){
            case ITEM -> {
                Item item = CryonContent.item(e.name);
                if (item == null) {
                    Log.warn("[CryonTechTree] Item not found: " + e.name + ", skipping");
                    return;
                }
                TechNode node;
                if(e.manualReqs != null){
                    node = node(item, e.manualReqs, () -> buildChildrenOf(e.name));
                }else{
                    // auto:不消耗材料研究,而是要求玩家先生产出该物品本身
                    Seq<Objective> objs = Seq.with(new Produce(item));
                    node = node(item, new ItemStack[]{}, objs, () -> buildChildrenOf(e.name));
                }

                // 设置 shownPlanets
                if (item.name.startsWith("cryon-")) {
                    // 如果是 cryon 前缀，直接覆盖
                    item.shownPlanets = ObjectSet.with(cryonPlanet);
                } else {
                    // 如果不是 cryon 前缀，添加 cryon
                    if (item.shownPlanets == null) {
                        item.shownPlanets = new ObjectSet<>();
                    }
                    item.shownPlanets.add(cryonPlanet);
                }
            }
            case LIQUID -> {
                Liquid liquid = CryonContent.liquid(e.name);
                if (liquid == null) {
                    Log.warn("[CryonTechTree] Liquid not found: " + e.name + ", skipping");
                    return;
                }
                TechNode node = node(liquid, new ItemStack[]{}, () -> buildChildrenOf(e.name));

                if (liquid.name.startsWith("cryon-")) {
                    liquid.shownPlanets = ObjectSet.with(cryonPlanet);
                } else {
                    if (liquid.shownPlanets == null) {
                        liquid.shownPlanets = new ObjectSet<>();
                    }
                    liquid.shownPlanets.add(cryonPlanet);
                }
            }
            case BLOCK, UNIT_BLOCK -> {
                Block block = CryonContent.block(e.name);
                if (block == null) {
                    Log.warn("[CryonTechTree] Block not found: " + e.name + ", skipping");
                    return;
                }
                Seq<Objective> gateObjs = sectorGateObjectives(e.name);

                TechNode node;
                if(e.manualReqs != null){
                    Seq<Objective> objs = autoObjectives(block);
                    objs.addAll(gateObjs);
                    node = node(block, e.manualReqs, objs, () -> buildChildrenOf(e.name));
                }else{
                    Seq<Objective> objs = autoObjectives(block);
                    objs.addAll(gateObjs);
                    node = node(block, scaledBlockCost(block), objs, () -> buildChildrenOf(e.name));
                }

                if (block.name.startsWith("cryon-")) {
                    block.shownPlanets = ObjectSet.with(cryonPlanet);
                } else {
                    if (block.shownPlanets == null) {
                        block.shownPlanets = new ObjectSet<>();
                    }
                    block.shownPlanets.add(cryonPlanet);
                }
            }
            case UNIT -> {
                UnitType unit = CryonContent.unit(e.name);
                if (unit == null) {
                    Log.warn("[CryonTechTree] Unit not found: " + e.name + ", skipping");
                    return;
                }
                Seq<Objective> objs = sectorGateObjectives(e.name);
                TechNode node = node(unit, e.manualReqs, objs, () -> buildChildrenOf(e.name));


                if (unit.name.startsWith("cryon-")) {
                    unit.shownPlanets = ObjectSet.with(cryonPlanet);
                } else {
                    if (unit.shownPlanets == null) {
                        unit.shownPlanets = new ObjectSet<>();
                    }
                    unit.shownPlanets.add(cryonPlanet);
                }
            }
            case SECTOR -> {
                SectorPreset sector = CryonContent.sector(e.name);
                if (sector == null) {
                    Log.warn("[CryonTechTree] Sector not found: " + e.name + ", skipping");
                    return;
                }

                Seq<Objective> objs = new Seq<>();
                Object[] reqs = sectorPrereqs.get(e.name);
                if(reqs != null){
                    for(Object o : reqs){
                        if(o instanceof Item item) objs.add(new Research(item));
                        else if(o instanceof Liquid liquid) objs.add(new Research(liquid));
                        else if(o instanceof Block block) objs.add(new Research(block));
                        else if(o instanceof UnitType unit) objs.add(new Research(unit));
                        else if(o instanceof SectorPreset preset) objs.add(new SectorComplete(preset));
                    }
                }

                TechNode node = node(sector, new ItemStack[]{}, objs, () -> buildChildrenOf(e.name));

                if (sector.name.startsWith("cryon-")) {
                    sector.shownPlanets = ObjectSet.with(cryonPlanet);
                } else {
                    if (sector.shownPlanets == null) {
                        sector.shownPlanets = new ObjectSet<>();
                    }
                    sector.shownPlanets.add(cryonPlanet);
                }
            }
        }
    }

    static void buildChildrenOf(String name){
        Seq<Entry> children = childrenOf.get(name);
        if(children == null) return;
        for(Entry c : children) buildNode(c);
    }

    // ================== 入口 ==================


    public static void load(){
        index();

        Planet cryonPlanet = Vars.content.planet("cryon-cryon");
        Block core = CryonContent.block("core-pioneer");

        if (core == null) {
            Log.err("[CryonTechTree] core-pioneer not found!");
            return;
        }

        var root = nodeRoot("cryon", core, true, () -> {
            buildChildrenOf("core-pioneer");
        });
        core.alwaysUnlocked = true;
        root.planet = cryonPlanet;
        cryonPlanet.techTree = root;

        core.shownPlanets = ObjectSet.with(cryonPlanet);

        // 统一处理所有 cryon- 前缀的内容
        for (Seq<Content> seq : Vars.content.getContentMap()) {
            for (Content content : seq) {
                if (content instanceof UnlockableContent u && u.name.startsWith("cryon-")) {
                    u.shownPlanets = ObjectSet.with(cryonPlanet);
                    // 清理 databaseTabs，只保留 cryon-cryon
                    u.databaseTabs.clear();
                    u.databaseTabs.add(cryonPlanet);
                }
            }
        }

        /* 隐藏小行星
        for (Planet p : Vars.content.planets()) {
            if (p != cryonPlanet && p != Planets.serpulo && p != Planets.erekir && p != Planets.sun) {
                p.hideDatabase = true;
                p.databaseTabs.clear();
            }
        }*/

        // 清理 cryon 自身的 databaseTabs
        //cryonPlanet.databaseTabs.clear();
        Time.runTask(10f, () -> {
            Log.info("[CryonTechTree] Refreshing loadout cache...");

            // 重新加载 schematics
            schematics.load();

            // 强制刷新所有核心的 loadout
            for(Block block : Vars.content.blocks()){
                if(block instanceof CoreBlock schematics_core){
                    // 这会触发重新从配置文件读取
                    universe.getLoadout(schematics_core);
                }
            }

            Log.info("[CryonTechTree] Loadout refresh complete");
        });

        Log.info("[CryonTechTree] Tech tree loaded");
        //DebugUnlock.enabled = true;
        //DebugUnlock.apply();
    }
}