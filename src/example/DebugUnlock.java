package example;

import mindustry.Vars;
import mindustry.game.SectorInfo;
import mindustry.type.Planet;
import mindustry.type.Sector;
import mindustry.content.TechTree;
import arc.util.Log;
import mindustry.ctype.Content;

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