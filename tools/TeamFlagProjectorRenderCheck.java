import arc.*;
import arc.files.Fi;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.FrameBuffer;
import arc.math.Mat;
import arc.struct.IntMap;
import arc.util.*;
import mindustry.*;
import mindustry.content.Blocks;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.Building;
import mindustry.world.*;

/** Real desktop GL check through mod packing, UI merging, map loading and drawing. */
public class TeamFlagProjectorRenderCheck{
    static final IntMap<Pixmap> expected = new IntMap<>();
    static boolean ready;
    static final Team[] teams = {Team.sharded, Team.crux, Team.malis, Team.derelict, Team.get(169), Team.get(170), Team.get(171)};
    static void check(boolean valid,String message){ if(!valid) throw new AssertionError(message); }
    public static void main(String[] args){
        Fi dir = new Fi(System.getProperty("mindustry.data.dir"));
        new Fi(System.getProperty("cryon.test.jar", "build/libs/mindustry-MOD-JavaDesktop.jar")).copyTo(dir.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(dir.child("mods/unitlanuch.jar"));
        Events.on(AtlasPackEvent.class,event->{
            for(Team team : teams){
                PixmapRegion pixmap = event.multiPacker.get(team.id >= 169 ? "cryon-team-" + team.name : "team-" + team.name);
                check(pixmap != null,"Source emblem missing: " + team);
                expected.put(team.id, team.id >= 169 ? new Pixmap(new Fi("sprites/team-" + team.name + ".png")) : pixmap.crop());
            }
        });
        Events.on(ClientLoadEvent.class,event->Core.app.post(()->{
            try{
                Core.graphics.setFullscreen(false); Core.graphics.setWindowSize(1200,700);
                for(var element : Core.scene.root.getChildren().copy())
                    if(element instanceof arc.scene.ui.Dialog dialog) dialog.hide();
                Vars.logic.reset();
                Rules rules = new Rules(); rules.waves = rules.waveTimer = rules.canGameOver = false;
                Vars.state.rules = rules;
                Vars.world.loadGenerator(128,80,tiles->{
                    for(int x=0;x<128;x++)for(int y=0;y<80;y++)
                        tiles.set(x,y,new Tile(x,y,(x+y)%9==0 ? Blocks.basalt : Blocks.sand,Blocks.air,Blocks.air));
                });
                Block projector = Vars.content.block("cryon-team-flag-projector");
                for(int i=0;i<teams.length;i++){
                    Team team = teams[i]; int x = 34 + i*12;
                    Vars.world.tile(x,35).setBlock(projector,team);
                    Building build = Vars.world.tile(x,35).build;
                    TextureRegion symbol = (TextureRegion)build.getClass().getMethod("teamSymbol").invoke(build);
                    verify(team,symbol);
                    Vars.world.tile(x,34).setBlock(Blocks.powerSource,team);
                }
                Vars.player.team(Team.sharded); Vars.logic.play();
                ready = true;
                Timer.schedule(()->{
                    for(int i=0;i<teams.length;i++){
                        Building build = Vars.world.tile(34+i*12,35).build;
                        if(teams[i] != Team.derelict) check(build.efficiency > 0.99f, "Projector fixture has no power: " + teams[i]);
                    }
                    ScreenUtils.saveScreenshot(dir.child("projectors.png"));
                    Log.info("PROJECTOR_GL_PASS: all @ emblems match source alpha after actual atlas packing and UI merging", teams.length);
                    Core.app.exit();
                },4f);
            }catch(Throwable error){ Log.err(error); System.exit(1); }
        }));
        Events.run(Trigger.update,()->{
            if(ready){ Core.camera.position.set(560f,330f); Vars.renderer.setScale(2f); }
        });
        mindustry.desktop.DesktopLauncher.main(args);
    }
    static void verify(Team team,TextureRegion symbol){
        check(symbol != null && !symbol.texture.isDisposed(),"Invalid symbol texture: " + team);
        check(Core.gl.glIsTexture(symbol.texture.getTextureObjectHandle()),"Not a real GL texture: " + team);
        Pixmap original = expected.get(team.id);
        check(symbol.width == original.width && symbol.height == original.height,"Wrong emblem dimensions: " + team);
        Mat projection = new Mat(Draw.proj());
        FrameBuffer buffer = new FrameBuffer(symbol.width,symbol.height);
        Draw.flush(); buffer.begin(Color.clear);
        Draw.proj().setOrtho(0,0,symbol.width,symbol.height);
        Draw.blend(Blending.disabled); Draw.color();
        Draw.rect(symbol,symbol.width/2f,symbol.height/2f,symbol.width,symbol.height); Draw.flush();
        Pixmap readback = ScreenUtils.getFrameBufferPixmap(0,0,symbol.width,symbol.height);
        buffer.end(); Draw.blend(); Draw.proj(projection);
        int mismatch = 0;
        for(int y=0;y<original.height;y++)for(int x=0;x<original.width;x++){
            int expectedAlpha = original.get(x,y)&255;
            int pixel = readback.get(x,original.height-1-y);
            if(Math.abs((pixel&255)-expectedAlpha)>2 || (expectedAlpha>0 && (pixel>>>8)!=0xffffff)) mismatch++;
        }
        Log.info("Projector GPU readback @: @x@ mismatched pixels=@",team.name,original.width,original.height,mismatch);
        check(mismatch == 0,"GPU emblem differs from source (possible terrain texture): " + team);
        readback.dispose(); buffer.dispose(); original.dispose();
    }
}
