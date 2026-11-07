package cryon.Features;

import arc.Events;
import arc.struct.ObjectSet;
import arc.scene.Element;
import arc.scene.event.VisibilityEvent;
import mindustry.Vars;
import mindustry.ctype.UnlockableContent;
import mindustry.game.EventType.ModContentLoadEvent;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.type.Item;
import arc.util.Reflect;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;

/** Real top-level database tabs alongside planets, retaining native content categories. */
public final class DatabaseSections{
    public static final String nano="cryon-nano",boss="cryon-boss";
    public static Item nanoTab,bossTab;
    private static final ObjectSet<String> bosses=ObjectSet.with(
        "cryon-vain","cryon-roche","cryon-threshold","cryon-hyperion",
        "cryon-oblivion","cryon-destroyer","cryon-eon");
    private static final ObjectSet<String> nanoExclusive=ObjectSet.with(
        "cryon-nano-floor","cryon-nano-ore","cryon-nano-terrain-wall","cryon-nano-material",
        "cryon-rogue-nano-drill","cryon-rogue-nano-wall","cryon-rogue-nano-unloader",
        "cryon-nano-machine-factory","cryon-nano-large-factory","cryon-nano-combat-factory",
        "cryon-nano-ultimate-factory","cryon-nano-warehouse","cryon-nano-cleaver","cryon-nano-bridge",
        "cryon-nano-eroder","cryon-nano-weaver","cryon-nano-armor-breaker","cryon-nano-annihilator");
    private static final String sharedPipe="cryon-reinforced-vacuum-duct";
    private static boolean installed,opening;

    public static void load(){
        if(nanoTab!=null)return;
        nanoTab=tab("database-nano");bossTab=tab("database-boss");
    }
    private static Item tab(String name){
        Item marker=new Item(name);marker.hidden=true;marker.hideDatabase=true;marker.alwaysUnlocked=true;
        marker.buildable=false;return marker;
    }

    public static void install(){
        if(installed)return;
        installed=true;Events.on(ModContentLoadEvent.class,e->configure());
        Events.on(ClientLoadEvent.class,e->{if(!Vars.headless)watchDialog(Vars.ui.database);});
    }
    public static String category(UnlockableContent content){
        if(content.minfo.mod==null||!"cryon".equals(content.minfo.mod.name)||!content.name.startsWith("cryon-"))return null;
        if(bosses.contains(content.name))return boss;
        return nanoExclusive.contains(content.name)||sharedPipe.equals(content.name)?nano:null;
    }
    public static void configure(){
        if(nanoTab==null||bossTab==null)return;
        for(var contents:Vars.content.getContentMap())for(var content:contents){
            if(!(content instanceof UnlockableContent))continue;
            UnlockableContent entry=(UnlockableContent)content;String category=category(entry);
            if(category==null)continue;
            entry.databaseCategory=entry.getContentType().name();
            entry.databaseTag=entry instanceof Block?((Block)entry).category.name():null;
            entry.allDatabaseTabs=false;
            if(sharedPipe.equals(entry.name)){
                entry.databaseTabs.addAll(entry.shownPlanets);
                if(entry.databaseTabs.isEmpty())entry.databaseTabs.add(mindustry.content.Planets.serpulo);
                entry.databaseTabs.add(nanoTab);
            }else{entry.databaseTabs.clear();entry.databaseTabs.add(nano.equals(category)?nanoTab:bossTab);}
            if(entry instanceof Block){
                Block block=(Block)entry;
                if(block.buildVisibility!=BuildVisibility.shown&&!(block.buildVisibility instanceof DatabaseVisibility)){
                    block.buildVisibility=new DatabaseVisibility(block.buildVisibility);
                }
            }
        }
        if(!Vars.headless&&Vars.ui!=null&&Vars.ui.database!=null)Reflect.set(Vars.ui.database,"allTabs",null);
    }

    private static class DatabaseVisibility extends BuildVisibility{
        DatabaseVisibility(BuildVisibility original){super(()->original.visible()||renderingDatabase());}
    }
    private static boolean renderingDatabase(){
        if(Vars.headless||Vars.ui==null||Vars.ui.database==null||(!opening&&!Vars.ui.database.isShown()))return false;
        // Only the native content-list rebuild may inspect sandbox/environment entries.
        // Build menus and placement checks keep the original predicate, even with the dialog open.
        return databaseFrame(Thread.currentThread().getStackTrace());
    }
    static void watchDialog(Element dialog){
        // Dialog fires 'shown' before joining the scene; capture must precede its own rebuild.
        dialog.addCaptureListener(event->{
            if(event.targetActor==dialog&&event instanceof VisibilityEvent)opening=!((VisibilityEvent)event).isHide();
            return false;
        });
    }
    static boolean databaseFrame(StackTraceElement[] frames){
        for(StackTraceElement frame:frames){
            if(frame.getClassName().equals("mindustry.ui.dialogs.DatabaseDialog")
                &&(frame.getMethodName().equals("rebuild")||frame.getMethodName().startsWith("lambda$rebuild$")))return true;
        }
        return false;
    }
}
