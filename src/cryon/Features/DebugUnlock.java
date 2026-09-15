package cryon.Features;

import mindustry.content.TechTree;

public class DebugUnlock{

    public static boolean enabled = true;

    public static void apply(){
        if(!enabled) return;

        TechTree.all.each(n -> {
            if(n.content != null){
                try {
                    n.content.unlock();
                } catch(Exception e) {
                }
            }
        });

    }
}