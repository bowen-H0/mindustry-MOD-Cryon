package cryon.Script;

import arc.Core;
import arc.func.Prov;
import arc.struct.OrderedMap;
import arc.util.Log;

/** 脚本注册表：id -> (显示名, 工厂)。其他 Mod 也可以直接调用 register 注册。 */
public class ScriptRegistry {
    public static class Entry {
        public final String id, name;
        public final Prov<? extends MapScript> factory;

        Entry(String id, String name, Prov<? extends MapScript> factory) {
            this.id = id;
            this.name = name;
            this.factory = factory;
        }

        public String localizedName(){
            return Core.bundle.get(name, name);
        }
    }

    public static final OrderedMap<String, Entry> entries = new OrderedMap<>();

    public static void register(String id, String displayName, Prov<? extends MapScript> factory) {
        entries.put(id, new Entry(id, displayName, factory));
    }

    /** 创建脚本实例，id 为空或未注册返回 null */
    public static MapScript create(String id) {
        if (id == null || id.isEmpty()) return null;
        Entry e = entries.get(id);
        if (e == null) {
            Log.warn("[JavaProcessor] 未注册的脚本: @", id);
            return null;
        }
        return e.factory.get();
    }
}
