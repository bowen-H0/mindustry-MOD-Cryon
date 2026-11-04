package cryon.Content;

import arc.graphics.Color;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.util.Log;
import mindustry.Vars;
import mindustry.type.Item;

/**
 * Cryon mod 的全部物品注册。
 *
 * 约定：
 *  - new Item("aluminum") 会被 Mindustry 自动注册为 "cryon-aluminum"
 *  - 短名（不带前缀）用于科技树、目录索引、白名单
 *  - 白名单 itemPlanets 记录每个短名物品属于哪些星球
 *
 * 添加新物品：
 *  1. 在声明区加 public static Item xxx;
 *  2. 在 load() 里调 item("xxx", ...) 赋值
 *  3. 用 planet("xxx", "cryon-cryon") 加白名单
 *  4. 新物品的 item(...) 调用写在已有物品之后，避免 id 错位
 */
public class CryonItems{

    // ================== 物品声明 ==================

    public static Item aluminum, magnesium, quartz, salt, sodium, crystalSand,
            dryIce, cryoAlloy, farstarAlloy, nickel, neutronite, nanoMaterial, ferrum,steel,hardSteel,redSand, manganese, armorAlloy,ashesAlloy,
            rhenium, technetium, promethium, neptunium, oganesson, gammaCrystal, protocolPoint, laminatedRhenium, phaseCrystal, porphyraAlloy, rheniumPlate,
            abyssalSediment;

    // ================== 白名单 ==================

    /** 短名 -> 该物品所属星球 id 的集合 */
    public static final ObjectMap<String, ObjectSet<String>> itemPlanets = new ObjectMap<>();

    /** 给某个物品登记所属星球 */
    public static void planet(String shortName, String... planetIds){
        ObjectSet<String> set = itemPlanets.get(shortName, ObjectSet::new);
        for(String id : planetIds) set.add(id);
    }

    /** 查某个短名物品属于哪些星球（返回 null 表示没登记） */
    public static ObjectSet<String> planetsOf(String shortName){
        return itemPlanets.get(shortName);
    }

    /** 查某个物品是否属于指定星球 */
    public static boolean belongsTo(String shortName, String planetId){
        ObjectSet<String> set = itemPlanets.get(shortName);
        return set != null && set.contains(planetId);
    }

    // ================== 封装 ==================

    /** 创建一个物品（名字不带前缀，Mindustry 会自动加） */
    public static Item item(String shortName, Color color, int hardness,
                            float explosiveness, float flammability,
                            float charge, float radioactivity){
        Item item = new Item(shortName);
        item.color = color;
        item.hardness = hardness;
        item.explosiveness = explosiveness;
        item.flammability = flammability;
        item.charge = charge;
        item.radioactivity = radioactivity;
        return item;
    }
    /** 8 参数重载：颜色用 hex 字符串 */
    public static Item item(String shortName, String colorHex, int hardness,
                            float explosiveness, float flammability,
                            float charge, float radioactivity){
        return item(shortName, Color.valueOf(colorHex), hardness,
                explosiveness, flammability, charge, radioactivity);
    }

    /** 简化重载：只给颜色和硬度，其他属性默认 0 */
    public static Item item(String shortName, String colorHex, int hardness){
        return item(shortName, Color.valueOf(colorHex), hardness, 0f, 0f, 0f, 0f);
    }

    // ================== 注册 ==================

    public static void load(){
        // ---- Cryon 本星物品 ----
        aluminum = item("aluminum", "c8d4e0", 1, 0.05f, 0.1f, 0.6f, 0f);
        magnesium = item("magnesium", "e3e3e3", 1, 0.85f, 2.2f, 0.8f, 0f);
        quartz = item("quartz", "f5f5f5", 0, 0f, 0f, 0.1f, 0f);
        salt = item("salt", "ffffff", 1, 0f, 0f, 0.05f, 0f);
        sodium = item("sodium", "f0f0f0", 0, 0.6f, 3.0f, 0.7f, 0f);
        crystalSand = item("crystal-sand", "4F473C", 1, 0f, 0f, 0.1f, 0f);
        dryIce = item("dry-ice", "c8eeff", 1, 0.7f, 0f, 0.1f, 0f);
        cryoAlloy = item("cryo-alloy", "d13b3b", 0, 0f, 0f, 0f, 0f);
        farstarAlloy = item("farstar-alloy", "d0a0ff", 0, 0f, 0f, 0.6f, 0f);
        nickel = item("nickel", "e3decfff", 3, 0f, 0f, 0f, 0f);
        neutronite = item("neutronite", "90c8ffff", 0, 0.1f, 0f, 0.1f, 0.9f);
        nanoMaterial = item("nano-material", "4CE68C", 0, 0f, 0.15f, 0f, 0.3f);

        // ---- Aravis 本星物品 ----
        ferrum = item("ferrum", "8C8C8C", 1, 0f, 0f, 0f, 0f);
        steel = item("steel", "a7a9ae", 4, 0f, 0f, 0f, 0f);
        hardSteel=item("hard-steel", "71797e", 4, 0f, 0f, 0f, 0f);
        redSand = item("av-red-sand", "c1563a", 1);
        manganese = item("manganese", "b8a9c9", 1);
        armorAlloy = item("armor-alloy", "5f6b7a", 0);
        ashesAlloy= item("ashes-alloy", "c4513a", 0);
        // Porphyra: structural metal -> electronics -> radiation -> nuclear industry -> superheavy matter.
        rhenium = item("rhenium", "cbb6da", 1);
        technetium = item("technetium", "a785d0", 2);
        promethium = item("promethium", "df87d8", 3, 0f, 0f, 0.15f, 0.35f);
        neptunium = item("neptunium", "8772bd", 4, 0.1f, 0f, 0.15f, 0.55f);
        oganesson = item("oganesson", "e9ddff", 5, 0.2f, 0f, 0.4f, 0.8f);
        gammaCrystal = item("gamma-crystal", "80e8b1", 1);
        protocolPoint = item("protocol-point", "79b8ef", 0);
        laminatedRhenium = item("laminated-rhenium", "b4adc9", 0);
        phaseCrystal = item("phase-crystal", "b9f0db", 0);
        porphyraAlloy = item("porphyra-alloy", "e4b9ef", 0);
        rheniumPlate = item("rhenium-plate", "cbb6da", 0);
        // Append new items so existing content registration order remains stable.
        abyssalSediment = item("abyssal-sediment", "879894", 0);
        abyssalSediment.lowPriority = true;
        // ================== 白名单登记 ==================
        planet("aluminum", "cryon-cryon");
        planet("magnesium", "cryon-cryon");
        planet("titanium", "cryon-cryon", "cryon-porphyra");
        planet("scrap", "cryon-cryon");
        planet("silicon", "cryon-cryon","cryon-aravis", "cryon-porphyra");
        planet("graphite", "cryon-cryon", "cryon-porphyra");
        planet("quartz", "cryon-cryon");
        planet("salt", "cryon-cryon");
        planet("sodium", "cryon-cryon");
        planet("crystal-sand", "cryon-cryon");
        planet("dry-ice", "cryon-cryon");
        planet("abyssal-sediment", "cryon-cryon", "cryon-cryon-depths");
        planet("cryo-alloy", "cryon-cryon");
        planet("farstar-alloy", "cryon-cryon");
        planet("nickel", "cryon-cryon","cryon-aravis");
        planet("neutronite", "cryon-cryon");
        planet("phase-fabric", "cryon-cryon");
        planet("surge-alloy", "cryon-cryon");
        planet("nano-material", "cryon-cryon");

        planet("ferrum", "cryon-aravis");
        planet("steel", "cryon-aravis");
        planet("hard-steel", "cryon-aravis");
        planet("av-red-sand", "cryon-aravis");
        planet("manganese", "cryon-aravis");
        planet("armor-alloy", "cryon-aravis");
        planet("gamma-crystal", "cryon-porphyra");
        planet("protocol-point", "cryon-porphyra");
        planet("laminated-rhenium", "cryon-porphyra");
        planet("rhenium-plate", "cryon-porphyra");
        planet("phase-crystal", "cryon-porphyra");
        planet("porphyra-alloy", "cryon-porphyra");
        planet("rhenium", "cryon-porphyra");
        planet("technetium", "cryon-porphyra");
        planet("promethium", "cryon-porphyra");
        planet("neptunium", "cryon-porphyra");
        planet("oganesson", "cryon-porphyra");


    }

    // ================== 便捷查询 ==================

    /** 按短名找 Item */
    public static Item get(String shortName){
        return Vars.content.item(shortName);
    }

    /** 让 ModPlanetTechTree 能按名字找物品 */
    public static Item find(String name){
        return get(name);
    }

    /** 调试：打印所有已登记物品的星球归属 */
    public static void dumpPlanets(){
        for(var entry : itemPlanets){
            Log.info("[CryonItems] @ -> @", entry.key, entry.value);
        }
    }
}
