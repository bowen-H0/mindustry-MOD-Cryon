package cryon.Features;

import arc.util.Log;
import arc.util.Strings;

/** Wall-clock timings for synchronous work; nested stages must not be added together. */
public final class LoadTiming {
    private LoadTiming() {}

    public static void measure(String stage, Runnable action){
        long started = System.nanoTime();
        boolean completed = false;
        try{
            action.run();
            completed = true;
        }finally{
            Log.info("[CryonLoad] @: @ ms (@)", stage,
                    Strings.fixed((System.nanoTime() - started) / 1_000_000f, 2),
                    completed ? "completed" : "failed");
        }
    }
}
