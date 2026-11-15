import arc.*;
import arc.files.Fi;
import arc.graphics.*;
import arc.math.geom.*;
import arc.scene.Element;
import arc.scene.ui.Dialog;
import arc.scene.ui.layout.Table;
import arc.util.*;
import java.lang.reflect.Method;
import mindustry.*;
import mindustry.game.EventType.*;
import mindustry.game.Team;
import mindustry.graphics.MultiPacker;
import mindustry.world.blocks.TileBitmask;
import mindustry.world.blocks.environment.Floor;
import mindustry.ui.dialogs.BaseDialog;

/** Packaged vanilla/Malis autotiles, source-preserving palette generation and actual editor icons. */
public class MalisMetalFloorRenderCheck{
    static Fi data;
    static void check(boolean value, String message){if(!value)throw new AssertionError(message);}
    static void guarded(Runnable action){try{action.run();}catch(Throwable error){Log.err(error);System.exit(1);}}
    static boolean close(int a, int b){
        for(int shift : new int[]{24,16,8,0}) if(Math.abs((a>>>shift&255)-(b>>>shift&255))>1)return false;
        return true;
    }
    static void palette(Floor floor) throws Exception{
        // v160's get(String) reads the active atlas; isolate this palette fixture in its own packer.
        MultiPacker packer = new MultiPacker(){
            @Override public arc.graphics.g2d.PixmapRegion get(String name){return getPacked(name);}
            @Override public boolean has(String name){return getPacked(name)!=null;}
        };
        Pixmap pixels = new Pixmap(5, 1);
        int[] originals = {Team.crux.color.rgba(), 0x777777ff, 0x708090ff,
            Team.crux.color.cpy().a(0.5f).rgba(), 0xf2555500};
        for(int i=0;i<originals.length;i++)pixels.set(i,0,originals[i]);
        packer.add(MultiPacker.PageType.environment,"malis-test-source",pixels);pixels.dispose();
        Method copy=floor.getClass().getDeclaredMethod("copy",MultiPacker.class,String.class,String.class);copy.setAccessible(true);
        copy.invoke(floor,packer,"malis-test-source","malis-test-result");
        var result=packer.get("malis-test-result");
        check(close(result.get(0,0),Team.malis.color.rgba()),"Crux accent does not become Malis palette");
        check(result.get(1,0)==originals[1]&&result.get(2,0)==originals[2],"Neutral metal changed");
        check(close(result.get(3,0),Team.malis.color.cpy().a(0.5f).rgba())&&result.get(4,0)==originals[4],"Alpha/transparent pixels changed");
        for(int i=0;i<originals.length;i++)check(packer.get("malis-test-source").get(i,0)==originals[i],"Source sprite modified");
        copy.invoke(floor,packer,"malis-test-source","malis-test-source");
        check(packer.get("malis-test-source").get(0,0)==originals[0],"Existing sprite overwritten");
        packer.dispose();
    }
    static void checkFloor(Floor floor, Floor source){
        check(floor.autotile&&floor.autotileVariants==source.autotileVariants,"Autotile behavior changed");
        check(floor.region.found()&&floor.fullIcon.found()&&floor.uiIcon.found(),"Missing editor icon: "+floor.name);
        check(floor.emitLight==source.emitLight&&floor.lightRadius==source.lightRadius,"Lighting behavior changed");
        if(floor.emitLight)check(floor.lightColor.r==Team.malis.color.r&&floor.lightColor.b==Team.malis.color.b,"Light is not Malis colored");
        for(int v=1;v<=floor.autotileVariants;v++)for(int i=0;i<47;i++){
            String suffix=(floor.autotileVariants>1?"-"+v:"")+"-"+i;
            check(Core.atlas.has(floor.name+suffix),"Missing autotile: "+floor.name+suffix);
            var a=Core.atlas.find(floor.name+suffix);var b=Core.atlas.find(source.name+suffix);
            check(a.width==b.width&&a.height==b.height,"Autotile size changed");
        }
        check(source.minfo.mod==null,"Vanilla floor replaced");
    }
    static boolean filled(int x,int y){return x>=0&&y>=0&&x<5&&y<5&&!(x==2&&y==2);}
    static void swatch(Table parent, Floor floor){
        parent.table(card->{
            card.add(floor.localizedName).padBottom(8f).row();
            card.table(tiles->{
                for(int y=4;y>=0;y--){
                    for(int x=0;x<5;x++){
                        if(!filled(x,y)){tiles.add().size(32f);continue;}
                        int bits=0;
                        for(int d=0;d<8;d++)if(filled(x+Geometry.d8[d].x,y+Geometry.d8[d].y))bits|=1<<d;
                        String suffix=(floor.autotileVariants>1?"-"+(1+(x+y)%floor.autotileVariants):"")+"-"+TileBitmask.values[bits];
                        tiles.image(Core.atlas.find(floor.name+suffix)).size(32f);
                    }
                    tiles.row();
                }
            });
        }).pad(16f);
    }
    static void test() throws Exception{
        check(!Vars.mods.locateMod("cryon").hasContentErrors(),"Packaged content errors");
        Floor[] originals=new Floor[3],variants=new Floor[3];int[] numbers={6,12,4};
        for(int i=0;i<numbers.length;i++){
            originals[i]=Vars.content.block("metal-tiles-"+numbers[i]).asFloor();
            variants[i]=Vars.content.block("cryon-malis-metal-tiles-"+numbers[i]).asFloor();
            checkFloor(variants[i],originals[i]);
        }
        palette(variants[0]);
        Vars.ui.editor.show();Vars.ui.editor.clearActions();
        Vars.editor.beginEdit(30,20);
        for(int i=0;i<variants.length;i++){
            Floor floor=variants[i];Vars.editor.drawBlock=floor;
            Vars.editor.tiles().get(6+i*6,10).setFloor(floor);
            check(Vars.editor.tiles().get(6+i*6,10).floor()==floor,"Editor cannot place Malis floor");
        }
        BaseDialog dialog=new BaseDialog("Malis · 金属地板对照");
        for(Floor floor:originals)swatch(dialog.cont,floor);
        dialog.cont.row();
        for(Floor floor:variants)swatch(dialog.cont,floor);
        dialog.addCloseButton();dialog.show();dialog.clearActions();dialog.color.a=1f;
        Core.scene.act();Core.scene.draw();ScreenUtils.saveScreenshot(data.child("malis-metal-tiles.png"));
        Log.info("MALIS_METAL_FLOOR_PASS: three new editor floors, 47 masks each, four variants for tile 12, icons, Malis lights, exact source/neutral/alpha preservation and custom sprite priority");
        Core.app.exit();
    }
    public static void main(String[] args){
        data=Fi.get(System.getProperty("mindustry.data.dir"));Fi repo=Fi.get(System.getProperty("cryon.repo","."));
        repo.child("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        repo.child("单位发射台library.jar").copyTo(data.child("mods/unitlaunch.jar"));
        Events.on(ClientLoadEvent.class,event->Timer.schedule(()->guarded(()->{
            for(Element element:Core.scene.root.getChildren().copy())if(element instanceof Dialog dialog){dialog.clearActions();dialog.hide(null);}
            Core.graphics.setFullscreen(false);
            arc.backend.sdl.jni.SDL.SDL_RestoreWindow(((arc.backend.sdl.SdlApplication)Core.app).getWindow());
            Core.graphics.setWindowSize(1100,850);
            Timer.schedule(()->guarded(()->{try{test();}catch(Exception error){throw new RuntimeException(error);}}),1f);
        }),3f));
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
