package example;

import mindustry.ctype.*;
import mindustry.type.*;
import mindustry.world.Block;

import static mindustry.Vars.*;

public class CryonContent{

    @SuppressWarnings("unchecked")
    public static <T extends UnlockableContent> T get(ContentType type, String id){
        T result = (T)content.getByName(type, id);
        if (result != null) return result;

        return (T)content.getByName(type, "cryon-" + id);
    }



    public static Block block(String id){
        return get(ContentType.block, id);
    }

    public static Item item(String id){
        return get(ContentType.item, id);
    }

    public static UnitType unit(String id){
        return get(ContentType.unit, id);
    }

    public static Liquid liquid(String id){
        return get(ContentType.liquid, id);
    }

    public static SectorPreset sector(String id) {
        return get(ContentType.sector, id);
    }
}