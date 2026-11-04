import arc.*;
import arc.files.Fi;
import arc.math.geom.Vec3;
import arc.scene.Element;
import arc.scene.ui.*;
import arc.util.*;
import java.lang.reflect.*;
import mindustry.*;
import mindustry.game.EventType.*;
import mindustry.graphics.g3d.PlanetRenderer;
import mindustry.type.Planet;
import mindustry.ui.dialogs.BaseDialog;

/** Real menu/settings clicks, 3D captures at three camera poses, restoration and persistence. */
public class MainMenuThemeRenderCheck{
    static Fi dir;
    static ClassLoader loader;
    static Object background;
    static Element original;
    static Vec3 sharedCamera, firstDirection;
    static float firstDistance,firstCenter;
    static long lifeSignature;
    static mindustry.core.World originalWorld;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void guarded(Runnable action){try{action.run();}catch(Throwable e){Log.err(e);System.exit(1);}}
    static Object get(Object object,String name){try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception e){throw new RuntimeException(e);}}
    static void time(float seconds){try{Field f=background.getClass().getDeclaredField("elapsed");f.setAccessible(true);f.setFloat(background,seconds);}catch(Exception e){throw new RuntimeException(e);}}
    static void screenshot(String name){ScreenUtils.saveScreenshot(dir.child(name));}
    static long signature(){
        int w=Core.graphics.getWidth(),h=Core.graphics.getHeight();long hash=1;
        var image=ScreenUtils.getFrameBufferPixmap(0,0,w,h);
        try{for(int x=w/2;x<w-16;x+=9)for(int y=h/6;y<h*5/6;y+=9)hash=hash*31+image.get(x,y);}finally{image.dispose();}
        return hash;
    }
    static void visiblePlanet(){
        Element actor=(Element)background;
        check(actor.getWidth()>100&&actor.getHeight()>100,"Background has no drawable bounds");
        int width=Core.graphics.getWidth(),height=Core.graphics.getHeight(),colored=0;
        var image=ScreenUtils.getFrameBufferPixmap(0,0,width,height);
        try{
            for(int x=width*2/5;x<width-16;x+=4)for(int y=height/8;y<height*7/8;y+=4){
                int pixel=image.get(x,y),r=pixel>>>24,g=(pixel>>>16)&255,b=(pixel>>>8)&255;
                if(g>r+8&&b>r+5)colored++;
            }
        }finally{image.dispose();}
        check(colored>100,"Main menu has no visible teal Cryon surface: "+colored);
    }
    static void click(String name){Button button=(Button)Core.scene.find(name);check(button!=null,"Missing theme button: "+name);button.fireClick();}
    static void settings(){
        Vars.ui.settings.show();Core.scene.act();
        TextButton entry=Vars.ui.settings.find(actor->actor instanceof TextButton&&((TextButton)actor).getText().toString().equals(Core.bundle.get("cryon.menu-theme.title")));
        check(entry!=null,"Settings has no Main Menu Theme entry");entry.fireClick();Core.scene.act();
    }
    static PlanetRenderer renderer(){return (PlanetRenderer)get(background,"renderer");}
    static Planet planet(){return Vars.content.planet("cryon-cryon");}
    public static void main(String[] args){
        dir=new Fi(System.getProperty("mindustry.data.dir"));Fi repo=new Fi(System.getProperty("cryon.repo","."));
        repo.child("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(dir.child("mods/cryon.jar"));repo.child("单位发射台library.jar").copyTo(dir.child("mods/unitlanuch.jar"));
        Events.on(ClientLoadEvent.class,event->Timer.schedule(()->guarded(()->{
            Core.graphics.setFullscreen(false);Core.graphics.setWindowSize(1280,900);
            for(var actor:Core.scene.root.getChildren().copy())if(actor instanceof Dialog dialog){dialog.clearActions();dialog.hide(null);}
            loader=Vars.mods.locateMod("cryon").main.getClass().getClassLoader();
            try{
                Field f=loader.loadClass("cryon.Features.MainMenuTheme").getDeclaredField("background");f.setAccessible(true);background=f.get(null);
            }catch(Exception e){throw new RuntimeException(e);}
            check(background instanceof Element,"Theme background did not attach");Element actor=(Element)background;
            original=actor.parent.getChildren().get(actor.getZIndex()+1);sharedCamera=Vars.renderer.planets.cam.position.cpy();
            originalWorld=Vars.world;
            check(!Core.settings.getBool("cryon-menu-theme",false),"Fresh profile is not the default theme");
            if(Core.settings.getString("cryon-menu-theme-id","").isEmpty()){
                Core.settings.put("cryon-menu-theme",true);
                try{check((boolean)loader.loadClass("cryon.Features.MainMenuTheme").getMethod("isCryon").invoke(null),"Legacy Cryon theme was not preserved");}catch(Exception e){throw new RuntimeException(e);}
                Core.settings.put("cryon-menu-theme",false);
            }
            Runnable[] steps={
                ()->{screenshot("menu-default.png");settings();},
                ()->{screenshot("menu-theme-settings.png");click("cryon-menu-theme-cryon");Vars.ui.settings.hide(null);},
                ()->{check(Core.settings.getBool("cryon-menu-theme",false),"Cryon selection not saved");check(!(boolean)get(background,"failed"),"3D renderer fell back");check(renderer()!=null,"3D background never rendered");check(!original.visible&&actor.visible,"Native and Cryon backgrounds draw together");time(0);},
                ()->{
                    visiblePlanet();screenshot("menu-cryon-00.png");var camera=renderer().cam;
                    firstDistance=camera.position.dst(planet().position);firstDirection=camera.position.cpy().sub(planet().position).nor();
                    firstCenter=camera.project(planet().position.cpy()).x;time(35);
                },
                ()->{
                    visiblePlanet();screenshot("menu-cryon-35.png");var camera=renderer().cam;
                    check(Math.abs(camera.position.dst(planet().position)-firstDistance)>0.01f,"Camera does not dolly");
                    check(!camera.position.cpy().sub(planet().position).nor().epsilonEquals(firstDirection,0.001f),"Camera does not orbit/crane");
                    check(Math.abs(camera.project(planet().position.cpy()).x-firstCenter)>0.5f,"Camera only orbits; framing never pans");
                    check(Vars.renderer.planets.cam.position.epsilonEquals(sharedCamera,0.0001f),"Theme mutated the interactive star-map camera");
                    Settings persisted=new Settings();persisted.setDataDirectory(Core.settings.getDataDirectory());persisted.load();check(persisted.getBool("cryon-menu-theme",false),"Theme selection did not persist to disk");time(85);
                },
                ()->{visiblePlanet();screenshot("menu-cryon-85.png");Core.settings.put("campaignselect",true);Vars.ui.planet.show();},
                ()->{
                    check(!actor.visible,"Theme continues rendering behind the star map");
                    try{BaseDialog map=(BaseDialog)loader.loadClass("cryon.Features.CryonCampaignLayers").getMethod("activeDialog").invoke(null);if(map!=null)map.hide(null);}catch(Exception e){throw new RuntimeException(e);}
                    Vars.ui.planet.hide(null);Core.graphics.setWindowSize(720,1000);
                },
                ()->{visiblePlanet();screenshot("menu-cryon-portrait.png");check(!(boolean)get(background,"failed"),"Portrait rendering failed");settings();},
                ()->{click("cryon-menu-theme-life");Core.scene.act();check(((Button)Core.scene.find("cryon-menu-theme-life")).isChecked()&&!((Button)Core.scene.find("cryon-menu-theme-default")).isChecked()&&!((Button)Core.scene.find("cryon-menu-theme-cryon")).isChecked(),"Theme selection is not exclusive");Vars.ui.settings.hide(null);Core.graphics.setWindowSize(1280,900);time(0);},
                ()->{
                    visiblePlanet();screenshot("menu-life-00.png");lifeSignature=signature();
                    check(!original.visible&&actor.visible,"Life theme does not replace native background");
                    check(get(background,"renderer")==null&&get(background,"buffer")==null,"Life theme keeps the unused 3D renderer");
                    Object scene=get(background,"lifeScene");check(scene!=null,"Undersea scene missing");
                    check(get(scene,"environmentShader")!=null&&get(scene,"environmentBuffer")!=null,"Ocean environment effects are not active");
                    check(Core.bundle.get("cryon.menu-theme.remaining-life").equals("海洋"),"Ocean theme still has its previous name");
                    check((int)get(scene,"floraCount")>2500,"Marine map is not rich in life");
                    check(((arc.struct.ObjectSet<?>)get(scene,"floraTypes")).size>=10,"Marine map lacks biological variety");
                    var map=(mindustry.world.Tiles)get(scene,"map");check(map!=Vars.world.tiles&&map.width==96&&map.height==96,"No isolated native marine map");
                    check(Vars.world==originalWorld,"Marine map replaced the player world");
                    Settings persisted=new Settings();persisted.setDataDirectory(Core.settings.getDataDirectory());persisted.load();check(persisted.getString("cryon-menu-theme-id","").equals("remainingLife"),"Life theme did not persist");time(45);
                },
                ()->{visiblePlanet();screenshot("menu-life-45.png");check(signature()!=lifeSignature,"Sea life does not animate");Core.graphics.setWindowSize(720,1000);},
                ()->{visiblePlanet();screenshot("menu-life-portrait.png");settings();},
                ()->{click("cryon-menu-theme-default");Vars.ui.settings.hide(null);},
                ()->{
                    screenshot("menu-default-restored.png");check(original.visible&&!actor.visible,"Default background did not return");
                    check(get(background,"renderer")==null&&get(background,"buffer")==null,"Disabled theme retains GPU resources");
                    check(get(background,"lifeScene")==null,"Disabled ocean theme retains environment resources");
                    Settings persisted=new Settings();persisted.setDataDirectory(Core.settings.getDataDirectory());persisted.load();check(!persisted.getBool("cryon-menu-theme",true),"Default selection did not persist");
                    check(persisted.getString("cryon-menu-theme-id","").equals("standard"),"Default theme ID did not persist");
                    Log.info("MENU_THEME_RENDER_PASS: three exclusive themes, legacy migration, living seabed assets/animation, 3D camera, portrait, isolation and persistence");Core.app.exit();
                }
            };
            int[] index={0};Timer.schedule(()->guarded(steps[index[0]++]),1f,1f,steps.length-1);
        }),3f));
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
