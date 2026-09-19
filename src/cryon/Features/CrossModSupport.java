package cryon.Features;

import arc.Events;
import arc.struct.ObjectMap;
import arc.util.Log;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.bullet.BulletType;
import mindustry.game.EventType.*;
import mindustry.gen.Groups;
import mindustry.type.*;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.*;

import java.lang.reflect.Field;

/**
 * 跨模组兼容处理(不依赖其他 mod 的任何类,全部按类名/反射):
 * 1. 把 VanillaExpansion 的 HyperKillBulletType 替换成无害子弹;
 * 2. 关闭 VanillaExpansion 的 HyperUnit 中"检测到其他模组就清除其内容"的逻辑,
 *    保护本模组的单位、建筑和子弹。
 */
public class CrossModSupport {

    private static final Field[] NONE = new Field[0];
    /** 类 -> [multiMod, modFound, statTimer] 字段;不是 HyperUnit 则为 NONE */
    private static final ObjectMap<Class<?>, Field[]> unitClassCache = new ObjectMap<>();

    public static void install(){
        Log.info("[cryon] CrossModSupport: install() called");
        Events.on(ClientLoadEvent.class, e -> run());
        Events.on(WorldLoadEvent.class, e -> run());

        Events.run(Trigger.update, () -> {
            if(Vars.state.isGame()) neutralizeUnits();
        });
    }

    // ───────────── 1. 子弹替换 ─────────────
    private static void run(){
        int found = 0, replaced = 0;

        for(BulletType bt : Vars.content.bullets()){
            if(isTarget(bt)){
                found++;
                Log.info("[cryon] CrossModSupport: found bullet class @", bt.getClass().getName());
            }
        }

        for(UnitType u : Vars.content.units()){
            for(Weapon w : u.weapons){
                if(w.bullet != null && isTarget(w.bullet)){
                    w.bullet = dummy();
                    replaced++;
                }
            }
        }

        for(Block b : Vars.content.blocks()){
            if(b instanceof PowerTurret pt){
                if(pt.shootType != null && isTarget(pt.shootType)){
                    pt.shootType = dummy();
                    replaced++;
                }
            }else if(b instanceof ItemTurret it){
                for(Item key : it.ammoTypes.keys().toSeq()){
                    BulletType bt = it.ammoTypes.get(key);
                    if(bt != null && isTarget(bt)){
                        it.ammoTypes.put(key, dummy());
                        replaced++;
                    }
                }
            }
        }

        Log.info("[cryon] CrossModSupport: found=@ replaced=@", found, replaced);
    }

    private static BulletType dummy(){
        return new BulletType(0f, 0f){{
            lifetime = 1f;
            collides = collidesTiles = collidesAir = collidesGround = false;
            hittable = absorbable = reflectable = false;
            shootEffect = smokeEffect = hitEffect = despawnEffect = Fx.none;
        }};
    }

    private static boolean isTarget(Object o){
        for(Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()){
            if(c.getName().endsWith("HyperKillBulletType")) return true;
        }
        return false;
    }

    // ───────────── 2. 关闭 HyperUnit 的"清除其他模组"逻辑 ─────────────
    private static void neutralizeUnits(){
        Groups.unit.each(u -> {
            Field[] f = resolve(u.getClass());
            if(f.length == 0) return;
            try{
                boolean was = f[0].getBoolean(u);
                f[0].setBoolean(u, false);                    // multiMod = false
                f[1].setBoolean(u, true);                     // modFound = true,不再重新检测
                f[2].setFloat(u, Float.NEGATIVE_INFINITY);    // statTimer:让每5tick的子弹清除永远不执行
                if(was){
                    Log.info("[cryon] CrossModSupport: disabled multiMod on @ (team @)", u.type.name, u.team);
                }
            }catch(Throwable t){
                Log.warn("[cryon] CrossModSupport: failed to patch HyperUnit: @", t.toString());
            }
        });
    }

    private static Field[] resolve(Class<?> c){
        Field[] cached = unitClassCache.get(c);
        if(cached != null) return cached;

        Field[] result = NONE;
        for(Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()){
            if(k.getName().endsWith("HyperUnit")){
                try{
                    Field multi = k.getDeclaredField("multiMod");
                    Field found = k.getDeclaredField("modFound");
                    Field timer = k.getDeclaredField("statTimer");
                    multi.setAccessible(true);
                    found.setAccessible(true);
                    timer.setAccessible(true);
                    result = new Field[]{multi, found, timer};
                }catch(Throwable t){
                    Log.warn("[cryon] CrossModSupport: HyperUnit fields not accessible: @", t.toString());
                }
                break;
            }
        }
        unitClassCache.put(c, result);
        return result;
    }
}