import arc.*;
import arc.files.Fi;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.*;
import arc.scene.Element;
import arc.scene.ui.Dialog;
import arc.scene.ui.layout.Table;
import arc.util.*;
import arc.util.io.*;
import java.io.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.type.Planet;
import mindustry.world.*;

/** Packaged real-GL check of emblem scaling, planet meshes, selection, serialization and power. */
public class LandmarkProjectorCheck{
    static Fi data;
    static boolean running;
    static Building flag, globe;
    static Class<?> renderer;
    static final String[] planets = {"cryon-aravis","serpulo","cryon-cryon","cryon-porphyra","erekir","sun"};
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static void guarded(Runnable task){try{task.run();}catch(Throwable error){Log.err(error);System.exit(1);}}
    static void configure(String name){globe.configured(null,name);check(name.equals(globe.config()),"Selection rejected: "+name);}
    static void test() throws Exception{
        check(!Vars.mods.locateMod("cryon").hasContentErrors(),"Content errors");
        renderer=Vars.mods.locateMod("cryon").main.getClass().getClassLoader().loadClass("cryon.Features.PlanetProjectionRenderer");
        Block large=Vars.content.block("cryon-large-team-flag-projector"), planet=Vars.content.block("cryon-planet-projector");
        Block small=Vars.content.block("cryon-team-flag-projector");
        check(small.size==2&&small.getClass().getField("projectionScale").getFloat(small)==1f,"Original projector changed");
        check(large.size==4&&large.getClass().getField("projectionScale").getFloat(large)==2.5f,"Large flag size");
        for(Block block:new Block[]{large,planet}){
            check(block.region.found()&&block.fullIcon.found(),"Missing landmark art");
            check(block.techNode!=null&&block.shownPlanets.contains(Vars.content.planet("cryon-aravis")),"Missing Aravis research");
            check(block.consPower.usage>0f&&block.requirements.length>0,"Missing resource/power cost");
        }
        Vars.logic.reset();Vars.state.rules=new Rules();Vars.state.rules.canGameOver=false;
        Vars.state.rules.waves=false;Vars.state.rules.lighting=false;
        Vars.world.loadGenerator(140,100,tiles->{for(int x=0;x<140;x++)for(int y=0;y<100;y++)tiles.set(x,y,new Tile(x,y,Blocks.basalt,Blocks.air,Blocks.air));});
        Vars.world.tile(42,38).setBlock(large,Team.malis);flag=Vars.world.build(42,38);
        Vars.world.tile(88,38).setBlock(planet,Team.malis);globe=Vars.world.build(88,38);
        for(int x:new int[]{42,88})Vars.world.tile(x,36).setBlock(Blocks.powerSource,Team.malis);
        check(flag.getClass().getMethod("teamSymbol").invoke(flag)!=null,"Missing Malis emblem");
        check("cryon-aravis".equals(globe.config()),"Default planet");
        configure("cryon-porphyra");globe.configured(null,"nonexistent-planet");check("cryon-porphyra".equals(globe.config()),"Invalid configuration accepted");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();globe.write(Writes.get(new DataOutputStream(bytes)));
        Building restored=planet.newBuilding();restored.create(planet,Team.malis);restored.read(Reads.get(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))),globe.version());
        check(globe.config().equals(restored.config()),"Planet choice did not survive serialization");
        globe.configured(null,null);check("cryon-aravis".equals(globe.config()),"Clear configuration failed");
        Table config=new Table();globe.buildConfiguration(config);check(config.getChildren().size>0,"No configuration UI");
        Vars.player.team(Team.sharded);Vars.player.unit(UnitTypes.alpha.spawn(Team.sharded,540,340));
        Vars.logic.play();Vars.ui.hudfrag.shown=false;running=true;
        verifyPlanet(0);
    }
    static void verifyPlanet(int index){
        configure(planets[index]);
        Timer.schedule(()->guarded(()->{
            check(flag.efficiency>0.99f&&globe.efficiency>0.99f,"Power fixture failed");
            try{
                Planet planet=Vars.content.planet(planets[index]);
                TextureRegion region=(TextureRegion)renderer.getMethod("request",Planet.class).invoke(null,planet);
                check(region!=null,"Planet model failed: "+planet.name);
                var cacheField=renderer.getDeclaredField("cache");cacheField.setAccessible(true);
                arc.struct.ObjectMap<?,?> cache=(arc.struct.ObjectMap<?,?>)cacheField.get(null);
                Object projection=((arc.struct.ObjectMap)cache).get(planet);
                var bufferField=projection.getClass().getDeclaredField("buffer");bufferField.setAccessible(true);
                FrameBuffer buffer=(FrameBuffer)bufferField.get(projection);
                Draw.flush();buffer.begin();Pixmap pix=ScreenUtils.getFrameBufferPixmap(0,0,buffer.getWidth(),buffer.getHeight());buffer.end();
                int lit=0;for(int y=0;y<pix.height;y++)for(int x=0;x<pix.width;x++)if((pix.get(x,y)>>>8)!=0)lit++;
                check(lit>500,"Empty 3D planet framebuffer: "+planet.name+" pixels="+lit);
                PixmapIO.writePng(data.child("planet-"+planet.name+".png"),pix);pix.dispose();
                if(index==0)ScreenUtils.saveScreenshot(data.child("landmark-projectors.png"));
                Log.info("PLANET_MESH_PASS @ pixels=@",planet.name,lit);
                if(index+1<planets.length){verifyPlanet(index+1);return;}
                Vars.player.team(Team.malis);
                Table config=new Table();globe.buildConfiguration(config);
                arc.scene.ui.Button open=config.find(e -> e instanceof arc.scene.ui.Button);
                check(open!=null,"Missing choose-planet button");open.fireClick();
                Dialog picker=(Dialog)Core.scene.find("cryon-planet-projector-picker");
                check(picker!=null,"Planet picker did not open");
                for(Planet option:Vars.content.planets())check(picker.find("cryon-project-planet-"+option.name)!=null,"Missing planet option: "+option.name);
                picker.clearActions();picker.color.a=1f;
                Timer.schedule(()->guarded(()->{
                    ScreenUtils.saveScreenshot(data.child("planet-picker.png"));
                    ((arc.scene.ui.Button)picker.find("cryon-project-planet-serpulo")).fireClick();
                    check("serpulo".equals(globe.config()),"Picker click did not configure planet");
                    picker.hide(null);
                }),0.75f);
                globe.enabled=false;flag.enabled=false;
                Timer.schedule(()->guarded(()->{
                    check(globe.efficiency==0f&&flag.efficiency==0f,"Disabled projector consumes/renders");
                    Log.info("LANDMARK_PROJECTORS_PASS: emblem, six planet meshes, config validation/clear, save/load, UI, research, power, original preserved");Core.app.exit();
                }),1.2f);
            }catch(Exception error){throw new RuntimeException(error);}
        }),1.6f);
    }
    public static void main(String[] args){
        data=Fi.get(System.getProperty("mindustry.data.dir"));Fi repo=Fi.get(System.getProperty("cryon.repo"));
        repo.child("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        repo.child("单位发射台library.jar").copyTo(data.child("mods/unitlaunch.jar"));
        Events.on(ClientLoadEvent.class,event->Timer.schedule(()->guarded(()->{
            for(Element element:Core.scene.root.getChildren().copy())if(element instanceof Dialog dialog){dialog.clearActions();dialog.hide(null);}
            Core.graphics.setFullscreen(false);arc.backend.sdl.jni.SDL.SDL_RestoreWindow(((arc.backend.sdl.SdlApplication)Core.app).getWindow());Core.graphics.setWindowSize(1200,850);
            Timer.schedule(()->guarded(()->{try{test();}catch(Exception error){throw new RuntimeException(error);}}),1f);
        }),3f));
        Events.run(Trigger.update,()->{if(running){Core.camera.position.set(520,410);Vars.player.set(520,410);Vars.renderer.setScale(1.8f);}});
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
