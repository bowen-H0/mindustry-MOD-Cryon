package cryon.Features;

import arc.func.Prov;
import arc.struct.ObjectMap;
import java.lang.reflect.*;
import mindustry.Vars;
import mindustry.gen.*;
import mindustry.world.Block;
import rhino.GeneratedClassLoader;
import rhino.ContextFactory;
import rhino.Scriptable;
import rhino.classfile.ClassFileWriter;
import static rhino.classfile.ByteCode.*;

/** Small native subclasses retain each entity's original implementation, class ID and AI. */
final class AllianceAdapters{
    private static final String helper = "cryon/Features/MapAlliances";
    private static final String teamc = "Lmindustry/gen/Teamc;";
    private static final String team = "Lmindustry/game/Team;";
    private static final ObjectMap<Class<?>, AdapterConstructor> constructors = new ObjectMap<>();
    private static int nextId;

    private AllianceAdapters(){}

    static void install(){
        for(Block block : Vars.content.blocks()){
            if(block.buildType == null) continue;
            Prov<Building> original = block.buildType;
            block.buildType = () -> MapAlliances.enabled() ? building(original.get(), block) : original.get();
        }
        for(var type : Vars.content.units()){
            Prov<? extends Unit> original = type.constructor;
            if(original != null) type.constructor = () -> MapAlliances.enabled() ? unit(original.get()) : original.get();
        }
        // Saved and networked units are constructed by entity ID, independently of UnitType.
        for(int id = 0; id < EntityMapping.idMap.length; id++){
            Prov original = EntityMapping.idMap[id];
            if(original == null) continue;
            EntityMapping.idMap[id] = () -> {
                Object entity = original.get();
                return MapAlliances.enabled() && entity instanceof Unit ? unit((Unit)entity) : entity;
            };
        }
    }

    private static Building building(Building original, Block block){
        return (Building)construct(original, block);
    }

    private static Unit unit(Unit original){ return (Unit)construct(original, null); }

    private static Object construct(Object original, Block outer){
        if(original instanceof MapAlliances.Adapted) return original;
        try{
            Class<?> base = original.getClass();
            AdapterConstructor ctor = constructors.get(base);
            if(ctor == null){
                ctor = generate(base, outer);
                constructors.put(base, ctor);
            }
            // Run the real constructor on the subclass. Copying fields from a prototype
            // would bind constructor-created callbacks to the discarded prototype.
            return ctor.create(original, outer);
        }catch(ReflectiveOperationException error){
            throw new IllegalStateException("Cannot install alliance behavior for " + original.getClass().getName(), error);
        }
    }

    private static AdapterConstructor generate(Class<?> base, Block outer) throws ReflectiveOperationException{
        if(Modifier.isFinal(base.getModifiers()) || !Modifier.isPublic(base.getModifiers())){
            throw new IllegalStateException("Alliance entity must be an extensible public class: " + base.getName());
        }
        Constructor<?> parent = null;
        for(Constructor<?> candidate : base.getDeclaredConstructors()){
            int flags = candidate.getModifiers();
            if(!Modifier.isPublic(flags) && !Modifier.isProtected(flags)) continue;
            Class<?>[] args = candidate.getParameterTypes();
            if(args.length == 0 || (args.length == 1 && outer != null && args[0].isInstance(outer))
                    || scriptConstructor(args, outer)){
                parent = candidate;
                break;
            }
        }
        if(parent == null) throw new IllegalStateException("Unsupported alliance entity constructor: " + base.getName());
        String name = "cryon.generated.AllianceEntity" + nextId++, owner = base.getName().replace('.', '/');
        ClassFileWriter code = new ClassFileWriter(name, base.getName(), "AllianceAdapters.java");
        code.addInterface(MapAlliances.Adapted.class.getName());
        String ctorDesc = descriptor(void.class, parent.getParameterTypes());
        code.startMethod("<init>", ctorDesc, ClassFileWriter.ACC_PUBLIC);
        code.addLoadThis();
        // Rhino JavaAdapter constructors prepend the script delegate and context factory
        // to the native constructor arguments. Forward them as well as any enclosing block.
        for(int index = 0; index < parent.getParameterCount(); index++) code.addALoad(index + 1);
        code.addInvoke(INVOKESPECIAL, owner, "<init>", ctorDesc);
        code.add(RETURN); code.stopMethod((short)(parent.getParameterCount() + 1));
        scoped(code, owner, "update", "()V", new Class<?>[0]);
        for(String method : new String[]{"destroy", "killed", "kill"}){
            try{
                Method original = base.getMethod(method);
                if(!Modifier.isFinal(original.getModifiers())) scoped(code, owner, method, "()V", new Class<?>[0]);
            }catch(NoSuchMethodException ignored){}
        }
        if(Unit.class.isAssignableFrom(base)){
            guarded(code, owner, "targetable", "(" + team + ")Z", "mayTarget", "(" + teamc + team + ")Z", new Class<?>[]{mindustry.game.Team.class});
            guarded(code, owner, "collides", "(Lmindustry/gen/Hitboxc;)Z", "mayCollide", "(" + teamc + "Ljava/lang/Object;)Z", new Class<?>[]{Hitboxc.class});
        }else{
            guarded(code, owner, "collide", "(Lmindustry/gen/Bullet;)Z", "mayCollide", "(" + teamc + "Ljava/lang/Object;)Z", new Class<?>[]{Bullet.class});
            guarded(code, owner, "damage", "(" + team + "F)V", "mayDamageFrom", "(" + teamc + team + ")Z", new Class<?>[]{mindustry.game.Team.class, float.class});
            // This overload exists in both supported engine versions and carries deferred splash attribution.
            damageBullet(code, owner);
        }
        guarded(code, owner, "damage", "(F)V", "mayDamage", "(" + teamc + ")Z", new Class<?>[]{float.class});
        guarded(code, owner, "damage", "(FZ)V", "mayDamage", "(" + teamc + ")Z", new Class<?>[]{float.class, boolean.class});
        for(Class<?>[] args : new Class<?>[][]{{float.class}, {float.class, boolean.class}}){
            try{
                Method method = base.getMethod("damagePierce", args);
                if(!Modifier.isFinal(method.getModifiers())) guarded(code, owner, "damagePierce", descriptor(void.class, args), "mayDamage", "(" + teamc + ")Z", args);
            }catch(NoSuchMethodException ignored){}
        }
        // Rhino uses the platform's generated-class loader, including Android's implementation.
        ClassLoader parentLoader = new ClassLoader(MapAlliances.class.getClassLoader()){
            @Override protected Class<?> loadClass(String requested, boolean resolve) throws ClassNotFoundException{
                return requested.equals(base.getName()) ? base : super.loadClass(requested, resolve);
            }
        };
        GeneratedClassLoader loader = Vars.platform.getScriptContext().createClassLoader(parentLoader);
        Class<?> result = loader.defineClass(name, code.toByteArray()); loader.linkClass(result);
        return new AdapterConstructor(result.getConstructor(parent.getParameterTypes()), base);
    }

    private static boolean scriptConstructor(Class<?>[] args, Block outer){
        return (args.length == 2 || (args.length == 3 && outer != null && args[2].isInstance(outer)))
            && args[0] == Scriptable.class && args[1] == ContextFactory.class;
    }

    private static final class AdapterConstructor{
        final Constructor<?> constructor;
        final Field delegee, factory;

        AdapterConstructor(Constructor<?> constructor, Class<?> base) throws NoSuchFieldException{
            this.constructor = constructor;
            Class<?>[] args = constructor.getParameterTypes();
            boolean scripted = args.length >= 2 && args[0] == Scriptable.class && args[1] == ContextFactory.class;
            delegee = scripted ? base.getField("delegee") : null;
            factory = scripted ? base.getField("factory") : null;
        }

        Object create(Object original, Block outer) throws ReflectiveOperationException{
            int count = constructor.getParameterCount();
            if(delegee != null){
                // Reuse this provider instance's script state, never another instance's.
                // The real Rhino constructor creates a new `self` bound to the adapted object.
                Object delegate = delegee.get(original), context = factory.get(original);
                return count == 2 ? constructor.newInstance(delegate, context) : constructor.newInstance(delegate, context, outer);
            }
            return count == 0 ? constructor.newInstance() : constructor.newInstance(outer);
        }
    }

    private static void damageBullet(ClassFileWriter code, String owner){
        String desc = "(Lmindustry/gen/Bullet;" + team + "F)V";
        code.startMethod("damage", desc, ClassFileWriter.ACC_PUBLIC);
        code.addLoadThis(); code.addALoad(2);
        code.addInvoke(INVOKESTATIC, helper, "mayDamageFrom", "(" + teamc + team + ")Z");
        int allowed = code.acquireLabel(); code.add(IFNE, allowed); code.add(RETURN); code.markLabel(allowed);
        code.addLoadThis(); code.addALoad(1); code.addALoad(2); code.addFLoad(3);
        code.addInvoke(INVOKESPECIAL, owner, "damage", desc); code.add(RETURN); code.stopMethod((short)4);
    }

    private static void guarded(ClassFileWriter code, String owner, String method, String desc, String check, String checkDesc, Class<?>[] args){
        code.startMethod(method, desc, ClassFileWriter.ACC_PUBLIC);
        code.addLoadThis();
        if(check.equals("mayTarget") || check.equals("mayCollide") || check.equals("mayDamageFrom")) code.addALoad(1);
        code.addInvoke(INVOKESTATIC, helper, check, checkDesc);
        int allowed = code.acquireLabel(); code.add(IFNE, allowed);
        if(desc.endsWith("Z")){ code.addPush(false); code.add(IRETURN); }else code.add(RETURN);
        code.markLabel(allowed); code.addLoadThis();
        for(int i = 0; i < args.length; i++){
            if(args[i] == float.class) code.addFLoad(i + 1);
            else if(args[i] == boolean.class || args[i] == int.class) code.addILoad(i + 1);
            else code.addALoad(i + 1);
        }
        code.addInvoke(INVOKESPECIAL, owner, method, desc);
        code.add(desc.endsWith("Z") ? IRETURN : RETURN); code.stopMethod((short)(args.length + 1));
    }

    private static void scoped(ClassFileWriter code, String owner, String method, String desc, Class<?>[] args){
        code.startMethod(method, desc, ClassFileWriter.ACC_PUBLIC);
        code.addLoadThis(); code.addInvoke(INVOKESTATIC, helper, "enter", "(" + teamc + ")Lcryon/Features/MapAlliances$Scope;"); code.addAStore(1);
        int start = code.acquireLabel(), end = code.acquireLabel(), handler = code.acquireLabel();
        code.markLabel(start); code.addLoadThis(); code.addInvoke(INVOKESPECIAL, owner, method, desc); code.markLabel(end);
        code.addALoad(1); code.addInvoke(INVOKESTATIC, helper, "exit", "(Lcryon/Features/MapAlliances$Scope;)V"); code.add(RETURN);
        code.markHandler(handler); code.addAStore(2);
        code.addALoad(1); code.addInvoke(INVOKESTATIC, helper, "exit", "(Lcryon/Features/MapAlliances$Scope;)V"); code.addALoad(2); code.add(ATHROW);
        code.addExceptionHandler(start, end, handler, null); code.stopMethod((short)3);
    }

    private static String descriptor(Class<?> result, Class<?>... args){
        StringBuilder value = new StringBuilder("(");
        for(Class<?> arg : args) value.append(signature(arg));
        return value.append(')').append(signature(result)).toString();
    }
    private static String signature(Class<?> type){
        if(type == void.class) return "V";
        if(type == float.class) return "F";
        if(type == boolean.class) return "Z";
        return "L" + type.getName().replace('.', '/') + ";";
    }
}
