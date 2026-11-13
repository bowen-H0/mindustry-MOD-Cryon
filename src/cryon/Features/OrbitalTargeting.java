package cryon.Features;

import arc.func.Boolp;

/** Scoped access to the orbital layer for explicitly equipped weapons. */
public final class OrbitalTargeting{
    private static final ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0);
    private OrbitalTargeting(){}
    public static boolean enabled(){ return depth.get() > 0; }
    public static void run(Runnable action){
        int previous = depth.get();
        depth.set(previous + 1);
        try{ action.run(); }finally{ depth.set(previous); }
    }
    public static boolean test(Boolp action){
        int previous = depth.get();
        depth.set(previous + 1);
        try{ return action.get(); }finally{ depth.set(previous); }
    }
}
