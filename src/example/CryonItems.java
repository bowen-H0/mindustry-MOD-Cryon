package example;

import arc.graphics.Color;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.util.Log;
import mindustry.Vars;
import mindustry.type.Item;
import mindustry.type.Planet;

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

    public static Item aluminum, magnesium, titanium, scrap, silicon,
            graphite, quartz, salt, sodium, crystalSand,
            dryIce, cryoAlloy, farstarAlloy, nickel, neutronite,
            phaseFabric, surgeAlloy, nanoMaterial, ferrum;

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
        quartz = item("quartz", "f5f5f5", 2, 0f, 0f, 0.1f, 0f);
        salt = item("salt", "ffffff", 1, 0f, 0f, 0.05f, 0f);
        sodium = item("sodium", "f0f0f0", 1, 0.6f, 3.0f, 0.7f, 0f);
        crystalSand = item("crystal-sand", "4F473C", 1, 0f, 0f, 0.1f, 0f);
        dryIce = item("dry-ice", "c8eeff", 1, 0.7f, 0f, 0.1f, 0f);
        cryoAlloy = item("cryo-alloy", "d13b3b", 6, 0f, 0f, 0f, 0f);
        farstarAlloy = item("farstar-alloy", "d0a0ff", 3, 0f, 0f, 0.6f, 0f);
        nickel = item("nickel", "e3decfff", 3, 0f, 0f, 0f, 0f);
        neutronite = item("neutronite", "90c8ffff", 3, 0.1f, 0f, 0.1f, 0.9f);
        nanoMaterial = item("nano-material", "4CE68C", 2, 0f, 0.15f, 0f, 0.3f);

        // ---- Aravis 本星物品 ----
        ferrum = item("ferrum", "8C8C8C", 1, 0f, 0f, 0f, 0f);

        // ================== 白名单登记 ==================
        planet("aluminum", "cryon-cryon");
        planet("magnesium", "cryon-cryon");
        planet("titanium", "cryon-cryon");
        planet("scrap", "cryon-cryon");
        planet("silicon", "cryon-cryon");
        planet("graphite", "cryon-cryon");
        planet("quartz", "cryon-cryon");
        planet("salt", "cryon-cryon");
        planet("sodium", "cryon-cryon");
        planet("crystal-sand", "cryon-cryon");
        planet("dry-ice", "cryon-cryon");
        planet("cryo-alloy", "cryon-cryon");
        planet("farstar-alloy", "cryon-cryon");
        planet("nickel", "cryon-cryon");
        planet("neutronite", "cryon-cryon");
        planet("phase-fabric", "cryon-cryon");
        planet("surge-alloy", "cryon-cryon");
        planet("nano-material", "cryon-cryon");

        planet("ferrum", "cryon-aravis");
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