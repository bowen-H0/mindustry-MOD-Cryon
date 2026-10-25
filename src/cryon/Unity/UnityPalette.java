package cryon.Unity;

import arc.graphics.Color;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.ctype.ContentType;
import mindustry.entities.bullet.BulletType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;

/** Private visual clones retain the native wire ID; never add runtime network content IDs. */
public class UnityPalette{
    private final Color color;
    private final IdentityHashMap<BulletType,BulletType> copies=new IdentityHashMap<>();
    public UnityPalette(String hex){color=hex.isEmpty()?null:Color.valueOf(hex);}
    @SuppressWarnings("unchecked")
    public BulletType apply(BulletType original){
        if(color==null || original==null)return original;
        if(copies.containsKey(original))return copies.get(original);
        BulletType copy=original.copy();
        // BulletType.copy registers a content ID. These copies are local visuals only.
        Vars.content.getBy(ContentType.bullet).remove(copy, true);
        copy.id=original.id;
        copies.put(original,copy);copies.put(copy,copy);
        for(Field f:original.getClass().getFields()){
            if(Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers()))continue;
            try{
                if(f.getType()==Color.class){Color originalColor=(Color)f.get(original);if(originalColor!=null)f.set(copy,color.cpy().a(originalColor.a));}
                else if(f.getType()==Color[].class){
                    Color[] source=(Color[])f.get(original);
                    if(source!=null){Color[] colors=new Color[source.length];for(int i=0;i<colors.length;i++)colors[i]=color.cpy().a(source[i].a);f.set(copy,colors);}
                }
            }catch(IllegalAccessException e){throw new IllegalStateException(e);}
        }
        copy.fragBullet=apply(original.fragBullet);
        copy.intervalBullet=apply(original.intervalBullet);
        copy.spawnBullets=new Seq<>();
        for(BulletType child:original.spawnBullets)copy.spawnBullets.add(apply(child));
        return copy;
    }
}
