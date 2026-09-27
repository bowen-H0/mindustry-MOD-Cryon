package cryon.Features;

import mindustry.type.Category;

import java.lang.reflect.*;
import java.util.*;

public class EnumAdder {

    /** 在指定位置插入新分类。insertIndex = 0 表示插到最前面。 */
    public static Category addCategory(String name, int insertIndex) {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Constructor<?> unsafeCtor = unsafeClass.getDeclaredConstructors()[0];
            unsafeCtor.setAccessible(true);
            Object unsafe = unsafeCtor.newInstance();

            Method mAllocateInstance  = unsafeClass.getMethod("allocateInstance", Class.class);
            Method mObjectFieldOffset = unsafeClass.getMethod("objectFieldOffset", Field.class);
            Method mPutObject         = unsafeClass.getMethod("putObject", Object.class, long.class, Object.class);
            Method mPutInt            = unsafeClass.getMethod("putInt", Object.class, long.class, int.class);
            Method mStaticFieldOffset = unsafeClass.getMethod("staticFieldOffset", Field.class);
            Method mStaticFieldBase   = unsafeClass.getMethod("staticFieldBase", Field.class);

            Category newVal = (Category) mAllocateInstance.invoke(unsafe, Category.class);

            // name 正常写
            Field nameField = Enum.class.getDeclaredField("name");
            long nameOffset = (long) mObjectFieldOffset.invoke(unsafe, nameField);
            mPutObject.invoke(unsafe, newVal, nameOffset, name);

            // ordinal 仍然放在末尾编号（不和已有的10个冲突即可，不需要和数组位置一致）
            Field ordinalField = Enum.class.getDeclaredField("ordinal");
            long ordinalOffset = (long) mObjectFieldOffset.invoke(unsafe, ordinalField);
            int newOrdinal = Category.all.length; // 沿用原来的“追加编号”逻辑
            mPutInt.invoke(unsafe, newVal, ordinalOffset, newOrdinal);

            // 关键改动：按 insertIndex 插入数组，而不是永远追加到最后
            List<Category> list = new ArrayList<>(Arrays.asList(Category.all));
            insertIndex = Math.max(0, Math.min(insertIndex, list.size()));
            list.add(insertIndex, newVal);
            Category[] newAll = list.toArray(new Category[0]);

            setStaticViaUnsafe(unsafe, mStaticFieldOffset, mStaticFieldBase, mPutObject, Category.class, "all", newAll);
            setStaticViaUnsafe(unsafe, mStaticFieldOffset, mStaticFieldBase, mPutObject, Category.class, "$VALUES", newAll);

            return newVal;
        } catch (Throwable t) {
            throw new RuntimeException("Failed to add category: " + name, t);        }
    }

    private static void setStaticViaUnsafe(Object unsafe, Method mStaticFieldOffset, Method mStaticFieldBase,
                                           Method mPutObject, Class<?> clazz, String fieldName, Object value) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            Object base = mStaticFieldBase.invoke(unsafe, field);
            long offset = (long) mStaticFieldOffset.invoke(unsafe, field);
            mPutObject.invoke(unsafe, base, offset, value);
        } catch (NoSuchFieldException e) {
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}