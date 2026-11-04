import arc.*;
import arc.files.Fi;
import arc.scene.ui.*;
import arc.util.*;
import java.lang.reflect.*;
import mindustry.*;
import mindustry.game.EventType.*;
import mindustry.graphics.g3d.PlanetParams;
import mindustry.type.*;
import mindustry.ui.dialogs.BaseDialog;

/** Real native/library star maps, including planets that already have old bases. */
public class StoryGateRenderCheck{
    static Fi dir;
    static ClassLoader loader;
    static BaseDialog library;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void guarded(Runnable action){try{action.run();}catch(Throwable e){Log.err(e);System.exit(1);}}
    @SuppressWarnings({"rawtypes","unchecked"})
    static void flag(String name,boolean enabled)throws Exception{
        Class<?> progress=loader.loadClass("cryon.Story.StoryProgress"),flags=loader.loadClass("cryon.Story.StoryProgress$Flag");
        progress.getMethod("set",flags,boolean.class).invoke(null,Enum.valueOf((Class)flags,name),enabled);
    }
    static PlanetParams params(BaseDialog map)throws Exception{return (PlanetParams)map.getClass().getField("state").get(map);}
    static void view(BaseDialog map,Planet planet)throws Exception{map.getClass().getMethod("viewPlanet",Planet.class,boolean.class).invoke(map,planet,false);Events.fire(Trigger.uiDrawBegin);Core.scene.act();}
    static void test(BaseDialog map)throws Exception{
        Planet surface=Vars.content.planet("cryon-cryon"),porphyra=Vars.content.planet("cryon-porphyra"),depths=Vars.content.planet("cryon-cryon-depths");
        view(map,surface);
        TextButton below=map.find("cryon-layer-underground");check(below!=null&&below.isDisabled(),"Underground button enabled before agreement");
        below.fireClick();check(params(map).planet==surface,"Disabled underground control bypassed story gate");
        check(map.find(actor->actor instanceof TextButton&&((TextButton)actor).getText().toString().equals(porphyra.localizedName))==null,"Old Porphyra base exposes locked system in planet list");
        view(map,porphyra);check(params(map).planet==surface,"Direct Porphyra view bypassed act two");
        view(map,depths);check(params(map).planet==surface,"Direct underground view bypassed agreement");
        flag("actTwo",true);view(map,porphyra);check(params(map).planet==porphyra&&porphyra.visible&&porphyra.accessible,"Act two failed to open Porphyra");
        flag("actTwo",false);Events.fire(Trigger.uiDrawBegin);check(params(map).planet==surface,"Relocking leaves Porphyra accessible");
        flag("underground",true);below=map.find("cryon-layer-underground");Core.scene.act();below.fireClick();
        check(params(map).planet==depths,"Agreement fails to open underground");
        flag("underground",false);Events.fire(Trigger.uiDrawBegin);check(params(map).planet==surface,"Relocking leaves underground accessible");
    }
    public static void main(String[] args){
        dir=new Fi(System.getProperty("mindustry.data.dir"));Fi repo=new Fi(System.getProperty("cryon.repo","."));
        repo.child("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(dir.child("mods/cryon.jar"));repo.child("单位发射台library.jar").copyTo(dir.child("mods/unitlanuch.jar"));
        Events.on(ClientLoadEvent.class,event->Timer.schedule(()->guarded(()->{
            for(var actor:Core.scene.root.getChildren().copy())if(actor instanceof Dialog dialog){dialog.clearActions();dialog.hide(null);}
            loader=Vars.mods.locateMod("cryon").main.getClass().getClassLoader();
            Planet planet=Vars.content.planet("cryon-porphyra");
            var sector=planet.getStartSector();sector.info.hasCore=true;sector.save=Vars.control.saves.new SaveSlot(dir.child("old-base.msav"));
            check(sector.hasBase(),"Old-base fixture failed");Core.settings.put("campaignselect",true);Vars.ui.planet.show();
            Timer.schedule(()->guarded(()->{
                try{
                    library=(BaseDialog)loader.loadClass("cryon.Features.CryonCampaignLayers").getMethod("activeDialog").invoke(null);
                    check(library!=null&&library!=Vars.ui.planet,"Library star map was not selected");test(library);library.hide(null);
                    var lock=library.getClass().getField("lockPlanetReplace");lock.setBoolean(null,true);
                    Vars.ui.planet.show();test(Vars.ui.planet);Vars.ui.planet.hide(null);lock.setBoolean(null,false);
                }catch(Exception e){throw new RuntimeException(e);}
                Log.info("STORY_GATE_RENDER_PASS: native/library maps, old bases, disabled underground, direct-view rejection and live flag changes");Core.app.exit();
            }),1f);
        }),3f));
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
