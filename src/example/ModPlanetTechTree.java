package example;

import arc.files.Fi;
import arc.struct.*;
import arc.util.Log;
import mindustry.Vars;
import mindustry.content.TechTree.TechNode;
import mindustry.ctype.Content;
import mindustry.ctype.UnlockableContent;
import mindustry.game.Objectives.*;
import mindustry.mod.Mods;
import mindustry.type.*;
import mindustry.world.Block;
import mindustry.world.consumers.*;

import static mindustry.content.TechTree.*;

/**
 * 通用的"星球科技树"基类。
 * 子类只需要：
 *  1. 在 registerEntries() 里调用 add/addAuto/sectorReq/sectorGate 填数据
 *  2. 实现 findItem/findLiquid/findBlock/findUnit/findSector 告诉基类怎么按 id 找内容
 *  3. 实现 rootBlock/rootPlanet 告诉基类根节点方块和挂载星球
 *
 * 同一个内容允许出现在多棵树里（比如 titanium 同时属于 Cryon 和 Aravis），
 * 每棵树都会各自正常调用 node(...) 建立独立的父子结构，
 * 星球归属（shownPlanets / databaseTabs）则是跨树累加的并集，不会互相覆盖。
 */
public abstract class ModPlanetTechTree {

    // ================== 数据结构 ==================

    public enum Kind{ ITEM, LIQUID, BLOCK, UNIT_BLOCK, UNIT, SECTOR }

    public static class Entry {
        Kind kind;
        String name;
        String parent;
        ItemStack[] manualReqs;

        Entry(Kind kind, String name, String parent, ItemStack[] manualReqs){
            this.kind = kind;
            this.name = name;
            this.parent = parent;
            this.manualReqs = manualReqs;
        }
    }

    // ================== 每棵树独立持有的状态 ==================

    protected final String modName;

    protected Seq<Entry> entries = new Seq<>();
    protected ObjectMap<String, Entry> byName = new ObjectMap<>();
    protected ObjectMap<String, Seq<Entry>> childrenOf = new ObjectMap<>();
    protected ObjectMap<String, Object[]> sectorPrereqs = new ObjectMap<>();
    protected ObjectMap<String, String[]> sectorGateTable = new ObjectMap<>();
    protected ObjectMap<String, Seq<String>> sectorGateReverse = new ObjectMap<>();
    protected ObjectMap<String, String> contentDirIndex = new ObjectMap<>();
    protected static boolean databaseTabsCleared = false;
    protected static final ObjectMap<String, ObjectSet<String>> globalPlanetOwners = new ObjectMap<>();


    protected ModPlanetTechTree(String modName){
        this.modName = modName;
    }


    // ================== 可调参数（子类可覆盖不同数值） ==================

    protected float depthCostStep(){ return 1.50f; }
    protected int itemAutoBase(){ return 30; }

    // ================== 深度计算 ==================

    protected int nextDepth(){
        TechNode ctx = context();
        return ctx == null ? 0 : ctx.depth + 1;
    }

    protected float depthMultiplier(){
        return 1f + nextDepth() * depthCostStep();
    }

    // ================== 自动花费 / 自动前提 ==================

    protected ItemStack[] scaledBlockCost(Block block){
        float mult = depthMultiplier();
        ItemStack[] base = block.requirements;
        ItemStack[] scaled = new ItemStack[base.length];
        for(int i = 0; i < base.length; i++){
            scaled[i] = new ItemStack(base[i].item, Math.max(1, Math.round(base[i].amount * mult)));
        }
        return scaled;
    }

    protected Seq<Objective> autoObjectives(Block block){
        Seq<Objective> objs = new Seq<>();
        ObjectSet<Item> seenItems = new ObjectSet<>();
        ObjectSet<Liquid> seenLiquids = new ObjectSet<>();

        for(ItemStack stack : block.requirements){
            if(seenItems.add(stack.item)) objs.add(new Research(stack.item));
        }

        for(Consume c : block.consumers){
            if(c instanceof ConsumeItems ci){
                for(ItemStack stack : ci.items){
                    if(seenItems.add(stack.item)) objs.add(new Research(stack.item));
                }
            }else if(c instanceof ConsumeLiquid cl){
                if(seenLiquids.add(cl.liquid)) objs.add(new Research(cl.liquid));
            }else if(c instanceof ConsumeLiquids cls){
                for(LiquidStack stack : cls.liquids){
                    if(seenLiquids.add(stack.liquid)) objs.add(new Research(stack.liquid));
                }
            }
        }
        return objs;
    }

    // ================== 建树 DSL（子类在 registerEntries() 里调用） ==================

    protected void add(Kind kind, String name, String parent, ItemStack... reqs){
        entries.add(new Entry(kind, name, parent, reqs.length == 0 && kind != Kind.UNIT ? null : reqs));
    }

    protected void addAuto(Kind kind, String name, String parent){
        entries.add(new Entry(kind, name, parent, null));
    }

    protected void sectorReq(String sectorName, Object... prereqItems){
        sectorPrereqs.put(sectorName, prereqItems);
    }

    protected void sectorGate(String sectorName, String... gatedNames){
        sectorGateTable.put(sectorName, gatedNames);
    }

    protected static ItemStack[] r(Object... pairs){
        ItemStack[] arr = new ItemStack[pairs.length / 2];
        for(int i = 0; i < arr.length; i++){
            arr[i] = new ItemStack((Item)pairs[i * 2], (Integer)pairs[i * 2 + 1]);
        }
        return arr;
    }

    // ================== 索引 ==================

    protected void index(){
        for(Entry e : entries) byName.put(e.name, e);
        for(Entry e : entries){
            if(e.parent != null){
                childrenOf.get(e.parent, Seq::new).add(e);
            }
        }

        for(var entry : sectorGateTable){
            String sectorName = entry.key;
            for(String gatedName : entry.value){
                sectorGateReverse.get(gatedName, Seq::new).add(sectorName);
            }
        }
    }

    protected Seq<Objective> sectorGateObjectives(String name){
        Seq<Objective> objs = new Seq<>();
        Seq<String> sectors = sectorGateReverse.get(name);
        if(sectors == null) return objs;
        for(String sectorName : sectors){
            SectorPreset preset = findSector(sectorName);
            if(preset == null){
                Log.warn("[@] sectorGate: sector not found: @", modName, sectorName);
                continue;
            }
            objs.add(new SectorComplete(preset));
        }
        return objs;
    }

    protected ItemStack[] autoItemCost(String parentName){
        Entry parentEntry = byName.get(parentName);
        if(parentEntry == null || parentEntry.kind != Kind.ITEM) return new ItemStack[]{};
        Item parentItem = findItem(parentEntry.name);
        int amount = Math.round(itemAutoBase() * depthMultiplier());
        return new ItemStack[]{ new ItemStack(parentItem, amount) };
    }

    // ================== 内容查找：子类实现 ==================

    protected abstract Item findItem(String name);
    protected abstract Liquid findLiquid(String name);
    protected abstract Block findBlock(String name);
    protected abstract UnitType findUnit(String name);
    protected abstract SectorPreset findSector(String name);

    // ================== 递归建树 ==================

    protected void buildNode(Entry e, Planet targetPlanet){
        switch(e.kind){
            case ITEM -> {
                Item item = findItem(e.name);
                if(item == null){ Log.warn("[@] Item not found: @, skipping", modName, e.name); return; }

                if(e.manualReqs != null){
                    node(item, e.manualReqs, () -> buildChildrenOf(e.name, targetPlanet));
                }else{
                    Seq<Objective> objs = Seq.with(new Produce(item));
                    node(item, new ItemStack[]{}, objs, () -> buildChildrenOf(e.name, targetPlanet));
                }
                markPlanet(item, targetPlanet);
            }
            case LIQUID -> {
                Liquid liquid = findLiquid(e.name);
                if(liquid == null){ Log.warn("[@] Liquid not found: @, skipping", modName, e.name); return; }

                Seq<Objective> objs = Seq.with(new Produce(liquid));
                node(liquid, new ItemStack[]{}, objs, () -> buildChildrenOf(e.name, targetPlanet));
                markPlanet(liquid, targetPlanet);
            }
            case BLOCK, UNIT_BLOCK -> {
                Block block = findBlock(e.name);
                if(block == null){ Log.warn("[@] Block not found: @, skipping", modName, e.name); return; }

                Seq<Objective> gateObjs = sectorGateObjectives(e.name);
                Seq<Objective> objs = autoObjectives(block);
                objs.addAll(gateObjs);
                if(e.manualReqs != null){
                    node(block, e.manualReqs, objs, () -> buildChildrenOf(e.name, targetPlanet));
                }else{
                    node(block, scaledBlockCost(block), objs, () -> buildChildrenOf(e.name, targetPlanet));
                }
                markPlanet(block, targetPlanet);
            }
            case UNIT -> {
                UnitType unit = findUnit(e.name);
                if(unit == null){ Log.warn("[@] Unit not found: @, skipping", modName, e.name); return; }

                Seq<Objective> objs = sectorGateObjectives(e.name);
                node(unit, e.manualReqs, objs, () -> buildChildrenOf(e.name, targetPlanet));
                markPlanet(unit, targetPlanet);
            }
            case SECTOR -> {
                SectorPreset sector = findSector(e.name);
                if(sector == null){ Log.warn("[@] Sector not found: @, skipping", modName, e.name); return; }

                Seq<Objective> objs = new Seq<>();
                if(e.parent != null){
                    Entry parentEntry = byName.get(e.parent);
                    if(parentEntry != null && parentEntry.kind == Kind.SECTOR){
                        SectorPreset parentSector = findSector(e.parent);
                        if(parentSector != null) objs.add(new SectorComplete(parentSector));
                        else Log.warn("[@] Parent sector not found: @ (required by @)", modName, e.parent, e.name);
                    }
                }

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

                node(sector, new ItemStack[]{}, objs, () -> buildChildrenOf(e.name, targetPlanet));
                markPlanet(sector, targetPlanet);
            }
        }
    }

    private void markPlanet(UnlockableContent u, Planet targetPlanet){
        // 物品：查 CryonItems 白名单
        if(u instanceof Item){
            String shortName = stripModPrefix(u.name);
            ObjectSet<String> planets = CryonItems.planetsOf(shortName);

            if(planets != null && planets.size > 0){
                for(String pid : planets){
                    Planet p = Vars.content.planet(pid);
                    if(p == null){
                        Log.warn("[@] markPlanet: item @ 白名单里的星球 @ 不存在", modName, u.name, pid);
                        continue;
                    }
                    if(u.uiIcon == null){
                        Log.warn("[@] skip shownPlanets/databaseTabs for @ (item, uiIcon=null)", modName, u.name);
                        continue;
                    }
                    ObjectSet<String> owners = globalPlanetOwners.get(u.name, ObjectSet::new);
                    if(owners.add(p.name)){
                        if(u.shownPlanets == null) u.shownPlanets = new ObjectSet<>();
                        u.shownPlanets.add(p);
                        u.databaseTabs.add(p);
                    }
                }
                return;
            }
            // 白名单没登记就按原来的默认逻辑走
        }

        // 非物品，或物品没登记白名单：按目录/默认星球
        String folder = contentDirIndex.get(stripModPrefix(u.name));
        Planet resolved = planetForFolder(folder);
        if(resolved == null) resolved = targetPlanet;
        if(resolved == null) return;

        if(u.uiIcon != null){
            ObjectSet<String> owners = globalPlanetOwners.get(u.name, ObjectSet::new);
            if(owners.add(resolved.name)){
                if(u.shownPlanets == null) u.shownPlanets = new ObjectSet<>();
                u.shownPlanets.add(resolved);
                u.databaseTabs.add(resolved);
            }
        }else{
            Log.warn("[@] skip shownPlanets/databaseTabs for @ (type=@, uiIcon=null)",
                    modName, u.name, u.getClass().getSimpleName());
        }
    }

    protected void buildChildrenOf(String name, Planet targetPlanet){
        Seq<Entry> children = childrenOf.get(name);
        if(children == null) return;
        for(Entry c : children) buildNode(c, targetPlanet);
    }

    // ================== 目录索引 ==================

    protected void indexContentDirs(){
        Mods.LoadedMod self = Vars.mods.locateMod(modName);
        if(self == null){
            Log.err("[@] Could not find mod '@'", modName, modName);
            return;
        }

        Fi contentRoot = self.root.child("content");
        Log.info("[@] mod root = @, content root = @, exists = @",
                modName, self.root.absolutePath(), contentRoot.absolutePath(), contentRoot.exists());

        if(!contentRoot.exists()){
            Log.err("[@] Content directory not found: @", modName, contentRoot.absolutePath());
            return;
        }

        scanContentDir(contentRoot);
        Log.info("[@] Indexed directory assignments for @ content files", modName, contentDirIndex.size);

        // 关键:把具体内容打出来,直接看 ferrum/aeolian-drill/core-conquest 这几个有没有被正确记录
        for(String key : new String[]{"ferrum", "aeolian-drill", "core-conquest", "aeolian-sand", "red-sand"}){
            Log.info("[@] contentDirIndex[@] = @", modName, key, contentDirIndex.get(key));
        }
    }
    protected void scanContentDir(Fi dir){
        for(Fi f : dir.list()){
            if(f.isDirectory()){
                scanContentDir(f);
            }else{
                String ext = f.extension();
                if(ext.equalsIgnoreCase("hjson") || ext.equalsIgnoreCase("json")){
                    String folder = f.parent().name();
                    if(planetForFolder(folder) != null){
                        contentDirIndex.put(f.nameWithoutExtension(), folder);
                    }
                }
            }
        }
    }

    /** 目录名 -> 星球，不同的树如果目录/星球映射不一样，子类可以覆盖这个方法 */
    protected Planet planetForFolder(String folderName){
        if(folderName == null) return null;
        return switch(folderName){
            case "aravis" -> Vars.content.planet("cryon-aravis");
            case "aurelia" -> Vars.content.planet("cryon-aurelia");
            default -> null;
        };
    }

    protected String stripModPrefix(String name){
        String prefix = modName + "-";
        return name.startsWith(prefix) ? name.substring(prefix.length()) : name;
    }

    protected void assignPlanetTabs(UnlockableContent u, Planet defaultPlanet){
        // 物品：查 CryonItems 白名单
        if(u instanceof Item){
            String shortName = stripModPrefix(u.name);
            ObjectSet<String> planets = CryonItems.planetsOf(shortName);

            if(planets != null && planets.size > 0){
                for(String pid : planets){
                    Planet p = Vars.content.planet(pid);
                    if(p == null){
                        Log.warn("[@] assignPlanetTabs: item @ 白名单里的星球 @ 不存在", modName, u.name, pid);
                        continue;
                    }
                    if(u.uiIcon == null){
                        Log.warn("[@] skip shownPlanets/databaseTabs for @ (item, uiIcon=null)", modName, u.name);
                        continue;
                    }
                    ObjectSet<String> owners = globalPlanetOwners.get(u.name, ObjectSet::new);
                    if(owners.add(p.name)){
                        if(u.shownPlanets == null) u.shownPlanets = new ObjectSet<>();
                        u.shownPlanets.add(p);
                        u.databaseTabs.add(p);
                    }
                }
                return;
            }
            // 白名单没登记就按原来的默认逻辑走
        }

        // 非物品，或物品没登记白名单：按目录/默认星球
        String folder = contentDirIndex.get(stripModPrefix(u.name));
        Planet target = planetForFolder(folder);
        if(target == null) target = defaultPlanet;
        if(target == null) return;

        if(u.uiIcon != null){
            ObjectSet<String> owners = globalPlanetOwners.get(u.name, ObjectSet::new);
            if(owners.add(target.name)){
                if(u.shownPlanets == null) u.shownPlanets = new ObjectSet<>();
                u.shownPlanets.add(target);
                u.databaseTabs.add(target);
            }
        }else{
            Log.warn("[@] skip shownPlanets/databaseTabs for @ (type=@, uiIcon=null)",
                    modName, u.name, u.getClass().getSimpleName());
        }
    }


    // ================== 子类需要提供的信息 ==================

    /** 调用 add/addAuto/sectorReq/sectorGate 把这棵树的数据填进去，只会在 run() 里跑一次 */
    protected abstract void registerEntries();

    /** 这棵树的根节点方块（比如 core-pioneer） */
    protected abstract Block rootBlock();

    /** 这棵树挂载到的星球 */
    protected abstract Planet rootPlanet();

    /** tech tree 节点的名字，一般不需要改 */
    protected String rootNodeName(){ return modName; }

    /** run() 收尾之后想额外做的事情（比如刷新 loadout 缓存），子类可选择性覆盖 */
    protected void afterLoad(TechNode root, Planet planet){}

    // ================== 入口 ==================

    public void run(){



        registerEntries();
        index();
        indexContentDirs();

        Planet planet = rootPlanet();
        Block core = rootBlock();

        if(core == null){
            Log.err("[@] 根节点方块未找到!", modName);
            return;
        }

        var root = nodeRoot(rootNodeName(), core, true,
                () -> buildChildrenOf(stripModPrefix(core.name), planet));
        core.alwaysUnlocked = true;
        root.planet = planet;
        planet.techTree = root;

        markPlanet(core, planet);

        // ===== tech tree 遍历：代价小，覆盖面也小 =====
        Seq<TechNode> stack = Seq.with(root);
        while(!stack.isEmpty()){
            TechNode node = stack.pop();
            if(node.content != null){
                UnlockableContent u = node.content;
                if(!u.name.startsWith(modName + "-")){
                    assignPlanetTabs(u, planet);
                }
            }
            stack.addAll(node.children);
        }

        for(Seq<Content> seq : Vars.content.getContentMap()){
            for(Content content : seq){
                if(content instanceof UnlockableContent u
                        && u.name.startsWith(modName + "-")){
                    String folder = contentDirIndex.get(stripModPrefix(u.name));
                    Planet resolved = planetForFolder(folder);
                    // 只有目录明确指向当前树星球时才加
                    if(resolved != null && resolved == planet){
                        assignPlanetTabs(u, planet);
                    }
                }
            }
        }

        afterLoad(root, planet);

        Log.info("[@] Tech tree loaded", modName);
    }
    /** 全局清理，整个 mod 加载时只调一次，跟哪棵树无关 */
    public static void globalCleanup(String modName){
        if(databaseTabsCleared) return;
        databaseTabsCleared = true;

        int cleared = 0;
        for(Seq<Content> seq : Vars.content.getContentMap()){
            for(Content content : seq){
                if(content instanceof UnlockableContent u && u.name.startsWith(modName + "-")){
                    u.databaseTabs.clear();
                    cleared++;
                }
            }
        }
        Log.info("[@] cleared databaseTabs of @ contents", modName, cleared);

        int cleaned = 0;
        for(Seq<Content> seq : Vars.content.getContentMap()){
            for(Content content : seq){
                if(content instanceof UnlockableContent u
                        && u.name.startsWith(modName + "-")
                        && u.shownPlanets != null
                        && u.shownPlanets.size > 0){
                    boolean hasForeign = false;
                    for(Planet p : u.shownPlanets){
                        if(p != null && !p.name.startsWith(modName + "-")){
                            hasForeign = true;
                            break;
                        }
                    }
                    if(hasForeign){
                        u.shownPlanets.clear();
                        cleaned++;
                    }
                }
            }
        }
        Log.info("[@] cleaned shownPlanets of @ contents", modName, cleaned);
    }
}