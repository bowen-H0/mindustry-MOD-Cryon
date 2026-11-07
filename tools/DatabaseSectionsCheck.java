import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.struct.*;
import arc.util.*;
import java.nio.file.Files;
import mindustry.*;
import mindustry.content.*;
import mindustry.ctype.*;
import mindustry.net.Net;
import mindustry.ui.dialogs.DatabaseDialog;
import mindustry.world.meta.BuildVisibility;

/** Exercise packaged classification and the actual native DatabaseDialog grouping algorithm. */
public final class DatabaseSectionsCheck{
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[]args){try{run();System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
    static void run()throws Exception{
        Core.app=new MockApplication();Core.files=new MockFiles();Core.graphics=new MockGraphics();Core.audio=new MockAudio();
        Core.settings=new Settings();Fi data=new Fi(Files.createTempDirectory("database-sections-").toFile());
        Core.settings.setDataDirectory(data);Core.bundle=I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlanuch.jar"));
        Vars.headless=true;Vars.loadLocales=false;Vars.net=new Net(null);Vars.platform=new mindustry.core.Platform(){};
        mindustry.core.Version.init();Vars.init();Core.settings.setJson(mindustry.io.JsonIO.json);
        Vars.content.createBaseContent();String copperCategory=Items.copper.databaseCategory,daggerCategory=UnitTypes.dagger.databaseCategory;
        Vars.content.createModContent();
        check(java.util.Objects.equals(copperCategory,Items.copper.databaseCategory)&&java.util.Objects.equals(daggerCategory,UnitTypes.dagger.databaseCategory),"vanilla category changed");
        Vars.content.init();
        var mod=Vars.mods.locateMod("cryon");check(mod!=null&&!mod.hasContentErrors(),"content errors");
        var sections=mod.main.getClass().getClassLoader().loadClass("cryon.Features.DatabaseSections");
        sections.getMethod("configure").invoke(null);
        var nanoTab=Vars.content.item("cryon-database-nano");var bossTab=Vars.content.item("cryon-database-boss");
        check(nanoTab!=null&&bossTab!=null&&nanoTab.hidden&&bossTab.hidden&&nanoTab.hideDatabase&&bossTab.hideDatabase,"database-only tab markers");
        String[] bosses={"vain","roche","threshold","hyperion","oblivion","destroyer","eon"};
        for(String name:bosses){var unit=Vars.content.unit("cryon-"+name);check(unit!=null&&"unit".equals(unit.databaseCategory)&&unit.databaseTabs.size==1&&unit.databaseTabs.contains(bossTab)&&!unit.allDatabaseTabs,"boss classification: "+name);}
        String[] nanoBlocks={"nano-floor","nano-ore","nano-terrain-wall","rogue-nano-drill","nano-machine-factory","nano-large-factory","nano-combat-factory","nano-ultimate-factory","nano-warehouse","rogue-nano-unloader","nano-cleaver","nano-bridge"};
        for(String name:nanoBlocks){var block=Vars.content.block("cryon-"+name);check(block!=null&&"block".equals(block.databaseCategory)&&block.databaseTabs.size==1&&block.databaseTabs.contains(nanoTab)&&!block.allDatabaseTabs,"nano block classification: "+name);}
        for(String name:new String[]{"nano-eroder","nano-weaver","nano-armor-breaker","nano-annihilator"})check(Vars.content.unit("cryon-"+name).databaseTabs.contains(nanoTab),"nano unit classification");
        check(Vars.content.item("cryon-nano-material").databaseTabs.contains(nanoTab),"nano material omitted");
        check(!"cryon-boss".equals(Vars.content.unit("cryon-destroyer-body").databaseCategory)&&Vars.content.unit("cryon-destroyer-body").hidden,"hidden boss body exposed");
        check(!"cryon-boss".equals(Vars.content.unit("cryon-intrusion-virus").databaseCategory)&&Vars.content.unit("cryon-intrusion-virus").hidden,"story gating changed");
        var warehouse=Vars.content.block("cryon-nano-warehouse");Vars.state.rules.infiniteResources=false;
        check(warehouse.isHidden()&&!warehouse.isPlaceable(),"database visibility leaked into campaign placement");
        check(Vars.content.block("cryon-nano-floor").isHidden(),"environment became buildable outside database");
        Vars.state.rules.infiniteResources=true;check(warehouse.isHidden(),"exclusive warehouse appeared in player sandbox build menu");Vars.state.rules.infiniteResources=false;
        for(String name:new String[]{"nano-reactor","nano-suppressor","nano-regeneration-matrix","nano-wall-large","nano-projector","nano-material-weaver","emp-bomb"})check(!Vars.content.block("cryon-"+name).databaseTabs.contains(nanoTab),"player device leaked into creature database: "+name);
        var pipe=Vars.content.block("cryon-reinforced-vacuum-duct");check(pipe.databaseTabs.contains(nanoTab)&&pipe.databaseTabs.size>1,"shared pipe lost player/nano tabs");
        for(String name:new String[]{"grey-goo-loader","grey-goo-unload-point"})check(Vars.content.block("cryon-"+name).hideDatabase&&Vars.content.block("cryon-"+name).isHidden(),"retired freight port exposed");
        check(Vars.content.unit("cryon-grey-goo-carrier").hidden&&Vars.content.unit("cryon-grey-goo-carrier").hideDatabase,"retired carrier exposed");
        var visibility=warehouse.buildVisibility;ObjectMap<Content,Short> ids=new ObjectMap<>();
        for(var list:Vars.content.getContentMap())for(Content content:list)ids.put(content,content.id);
        sections.getMethod("configure").invoke(null);check(warehouse.buildVisibility==visibility,"repeat configure nested visibility wrapper");
        for(var entry:ids)check(entry.key.id==entry.value,"category changed registry IDs");
        var frame=sections.getDeclaredMethod("databaseFrame",StackTraceElement[].class);frame.setAccessible(true);
        check((boolean)frame.invoke(null,(Object)new StackTraceElement[]{new StackTraceElement("mindustry.ui.dialogs.DatabaseDialog","rebuild","DatabaseDialog.java",1)}),"native database scope not recognized");
        check(!(boolean)frame.invoke(null,(Object)new StackTraceElement[]{new StackTraceElement("mindustry.world.Block","isPlaceable","Block.java",1)}),"placement incorrectly accepted as database scope");
        var element=new arc.scene.Element();var watch=sections.getDeclaredMethod("watchDialog",arc.scene.Element.class);watch.setAccessible(true);watch.invoke(null,element);
        var opening=sections.getDeclaredField("opening");opening.setAccessible(true);boolean[] firstShown={false};
        element.addListener(event->{if(event instanceof arc.scene.event.VisibilityEvent&&!((arc.scene.event.VisibilityEvent)event).isHide()){
            try{firstShown[0]=opening.getBoolean(null);}catch(Exception error){throw new RuntimeException(error);}
        }return false;});
        element.fire(new arc.scene.event.VisibilityEvent(false));check(firstShown[0],"first database opening was not captured before native shown callback");
        element.fire(new arc.scene.event.VisibilityEvent(true));check(!opening.getBoolean(null),"database hide did not clear opening scope");
        // No rendering/UI constructor is needed to run the native, data-only category sorter.
        var unsafeClass=Class.forName("sun.misc.Unsafe");var singleton=unsafeClass.getDeclaredField("theUnsafe");singleton.setAccessible(true);Object unsafe=singleton.get(null);
        DatabaseDialog dialog=(DatabaseDialog)unsafeClass.getMethod("allocateInstance",Class.class).invoke(unsafe,DatabaseDialog.class);
        var contentsField=DatabaseDialog.class.getDeclaredField("sortedContents");contentsField.setAccessible(true);contentsField.set(dialog,new OrderedMap<>());
        var sort=DatabaseDialog.class.getDeclaredMethod("sortContents");sort.setAccessible(true);sort.invoke(dialog);
        @SuppressWarnings("unchecked") var grouped=(OrderedMap<String,OrderedMap<String,Seq<UnlockableContent>>>)contentsField.get(dialog);
        check(!grouped.containsKey("cryon-nano")&&!grouped.containsKey("cryon-boss"),"old subcategories were not removed");
        check(grouped.containsKey("block")&&grouped.containsKey("unit")&&grouped.containsKey("item"),"native content categories lost");
        var tabsMethod=DatabaseDialog.class.getDeclaredMethod("checkTabList");tabsMethod.setAccessible(true);tabsMethod.invoke(dialog);
        var tabsField=DatabaseDialog.class.getDeclaredField("allTabs");tabsField.setAccessible(true);
        @SuppressWarnings("unchecked") var tabs=(Seq<UnlockableContent>)tabsField.get(dialog);
        check(tabs.contains(Planets.serpulo)&&tabs.contains(Planets.erekir)&&tabs.contains(nanoTab)&&tabs.contains(bossTab),"custom tabs are not parallel with planet tabs");
        int nanoCount=0,bossCount=0;
        for(var list:Vars.content.getContentMap())for(Content content:list)if(content instanceof UnlockableContent){
            var entry=(UnlockableContent)content;
            if(entry.databaseTabs.contains(nanoTab))nanoCount++;
            if(entry.databaseTabs.contains(bossTab))bossCount++;
        }
        check(nanoCount==19&&bossCount==7,"top tab contents incorrect");
        System.out.println("DATABASE_SECTIONS_CHECK_PASS: nano/BOSS top tabs parallel with planets, original category restoration, exclusive nano content and shared pipe, 7 boss series, hidden/story exclusions, faction-only construction and player content separated, idempotence and stable IDs");
    }
}
