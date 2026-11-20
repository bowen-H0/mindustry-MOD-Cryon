package cryon.Features;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.*;
import arc.math.Mathf;
import arc.util.*;
import cryon.Content.SacredRemains;
import cryon.Script.AegisSacredRemainsScript;
import mindustry.Vars;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;

/** Visual state only. The processor owns the encounter and actual entrance movement. */
public final class SacredRiftRenderer{
    private static final String packet="cryon-sacred-rift";
    private static boolean installed,active,failed,front,history;
    private static float x,y,dx,dy,elapsed,viewX,viewY,motionX,motionY,lastX,lastY,lastWidth,lastHeight,strength;
    private static FrameBuffer scene;
    private static Shader tunnel,cinema,surface;
    private static Unit drawingUnit;
    private static final Color dark=Color.valueOf("140c22"),violet=Color.valueOf("b28bf0");
    private SacredRiftRenderer(){}
    public static void install(){
        if(installed||Vars.headless)return;installed=true;
        Events.on(ClientLoadEvent.class,e->Vars.netClient.addPacketHandler(packet,SacredRiftRenderer::receive));
        Events.run(Trigger.update,()->{if(active&&Vars.state.isGame()&&!Vars.state.isPaused())elapsed+=Time.delta;});
        Events.run(Trigger.draw,SacredRiftRenderer::draw);
        Events.on(ResetEvent.class,e->reset());Events.on(WorldLoadEvent.class,e->reset());
        Events.on(DisposeEvent.class,e->dispose());
    }
    public static void broadcast(float px,float py,float angle,float progress){
        if(Vars.net.client())return;
        begin(px,py,angle,progress);
        if(Vars.net.server())Call.clientPacketReliable(packet,px+","+py+","+angle+","+progress);
    }
    public static void cancel(){
        active=false;history=false;
        if(Vars.net.server())Call.clientPacketReliable(packet,"stop");
    }
    private static void receive(String message){
        if(message.equals("stop")){active=false;history=false;return;}
        String[] parts=message.split(",");if(parts.length!=4)return;
        try{
            float px=Float.parseFloat(parts[0]),py=Float.parseFloat(parts[1]),angle=Float.parseFloat(parts[2]),time=Float.parseFloat(parts[3]);
            if(Float.isFinite(px)&&Float.isFinite(py)&&Float.isFinite(angle)&&Float.isFinite(time)&&time>=0f&&time<=SacredRemains.riftDuration+60f)begin(px,py,angle,time);
        }catch(NumberFormatException ignored){}
    }
    public static void begin(float px,float py,float angle,float progress){
        if(Vars.headless)return;
        if(!active||Mathf.dst2(x,y,px,py)>1f||progress<elapsed-90f)history=false;
        x=px;y=py;dx=Mathf.cosDeg(angle);dy=Mathf.sinDeg(angle);elapsed=progress;active=true;
    }
    public static boolean active(){return active&&elapsed<SacredRemains.riftDuration+60f;}
    public static float progress(){return elapsed;}
    public static float parallaxX(){return viewX;}
    public static float parallaxY(){return viewY;}
    public static boolean resourcesReady(){return tunnel!=null&&cinema!=null&&surface!=null&&!failed;}
    private static float aperture(){return Mathf.clamp(elapsed/110f)*Mathf.clamp((SacredRemains.riftDuration-elapsed)/95f);}
    private static void draw(){
        if(!active()||!Vars.state.isGame()||Vars.state.isEditor()){if(active){active=false;history=false;}return;}
        boolean visible=Math.abs(x-Core.camera.position.x)<Core.camera.width*.5f+250f&&Math.abs(y-Core.camera.position.y)<Core.camera.height*.5f+250f;
        if(!visible){history=false;return;}
        viewX=Mathf.clamp((Core.camera.position.x-x)/Core.camera.width,-.85f,.85f);
        viewY=Mathf.clamp((Core.camera.position.y-y)/Core.camera.height,-.85f,.85f);
        float cx=Core.camera.position.x,cy=Core.camera.position.y;
        boolean cut=!history||Mathf.dst2(cx,cy,lastX,lastY)>Core.camera.width*Core.camera.width*.04f
            ||Math.abs(Core.camera.width-lastWidth)>lastWidth*.03f||Math.abs(Core.camera.height-lastHeight)>lastHeight*.03f;
        motionX=cut?0f:Mathf.clamp((cx-lastX)/Core.camera.width,-.012f,.012f);
        motionY=cut?0f:Mathf.clamp((cy-lastY)/Core.camera.height,-.012f,.012f);
        lastX=cx;lastY=cy;lastWidth=Core.camera.width;lastHeight=Core.camera.height;history=true;
        strength=Mathf.clamp(elapsed/90f)*Mathf.clamp((SacredRemains.riftDuration+60f-elapsed)/120f);
        if(!failed)try{ensure();}catch(RuntimeException error){failed=true;Log.err("[Cryon] Sacred rift shaders unavailable",error);}
        if(!failed){
            Draw.draw(Layer.min-2f,()->scene.begin(Color.black));
            Draw.draw(Layer.legUnit-.4f,()->portal(false));
            Draw.draw(Layer.legUnit-.2f,()->lightning(false));
            Draw.draw(Layer.legUnit+.4f,()->portal(true));
            Draw.draw(Layer.legUnit+.5f,()->lightning(true));
            Draw.draw(Layer.max+4f,()->{
                Draw.flush();scene.end();Blending previous=Draw.getBlend();
                Blending.normal.apply();scene.blit(cinema);previous.apply();
            });
        }
    }
    private static void ensure(){
        if(tunnel==null)tunnel=new Shader(Vars.tree.get("shaders/sacred-rift.vert"),Vars.tree.get("shaders/sacred-rift.frag")){
            @Override public void apply(){
                setUniformf("u_origin",x,y);setUniformf("u_direction",dx,dy);setUniformf("u_view",viewX,viewY);
                setUniformf("u_time",elapsed/60f);setUniformf("u_open",aperture());setUniformf("u_front",front?1f:0f);
            }
        };
        if(cinema==null)cinema=new Shader(Vars.tree.get("shaders/screenspace.vert"),Vars.tree.get("shaders/sacred-cinema.frag")){
            @Override public void apply(){
                setUniformi("u_texture",0);setUniformf("u_resolution",Core.graphics.getWidth(),Core.graphics.getHeight());
                setUniformf("u_focus",Mathf.clamp((x-Core.camera.position.x)/Core.camera.width+.5f),Mathf.clamp((y-Core.camera.position.y)/Core.camera.height+.5f));
                setUniformf("u_motion",motionX,motionY);setUniformf("u_strength",strength);setUniformf("u_time",elapsed/60f);
                setUniformf("u_impact",Mathf.clamp(1f-Math.abs(elapsed-150f)/35f));
            }
        };
        ensureSurface();
        int w=Math.max(2,Core.graphics.getWidth()),h=Math.max(2,Core.graphics.getHeight());
        if(scene==null){scene=new FrameBuffer(w,h);scene.getTexture().setFilter(Texture.TextureFilter.linear);}
        else if(scene.getWidth()!=w||scene.getHeight()!=h)scene.resize(w,h);
    }
    private static void ensureSurface(){
        if(surface!=null)return;
        surface=new Shader(Vars.tree.get("shaders/sacred-unit.vert"),Vars.tree.get("shaders/sacred-unit.frag")){
            @Override public void apply(){
                setUniformi("u_texture",0);
                Texture texture=drawingUnit.type.region.texture;
                setUniformf("u_pixel",1f/texture.width,1f/texture.height);
                setUniformf("u_plane",x,y);setUniformf("u_direction",dx,dy);
                boolean clip=active()&&elapsed<SacredRemains.riftDuration&&drawingUnit.flag==AegisSacredRemainsScript.bossFlag;
                setUniformf("u_clip",clip?1f:0f);setUniformf("u_light",active()?1f:0.45f);
            }
        };
    }
    public static void drawUnit(Unit unit,Runnable draw){
        Draw.draw(Layer.legUnit,()->{
            Shader previous=Draw.getShader();float z=Draw.z();
            try{
                if(!failed){ensureSurface();drawingUnit=unit;Draw.shader(surface);}
                draw.run();Draw.flush();
            }catch(RuntimeException error){failed=true;Log.err("[Cryon] Sacred remains surface shader failed",error);}
            finally{Draw.shader(previous);Draw.reset();Draw.z(z);}
        });
    }
    private static void portal(boolean foreground){
        float open=aperture();if(open<=0f)return;
        Shader previous=Draw.getShader();float z=Draw.z();
        try{
            Draw.blend();
            if(!foreground){
                Draw.color(dark,open*.75f);
                Fill.light(x+10f,y-12f,64,155f,Color.valueOf("140c22aa"),Color.clear);
            }
            Draw.flush();front=foreground;Draw.shader(tunnel);Draw.color();
            Draw.rect(Core.atlas.white(),x,y,430f,430f);Draw.flush();Draw.shader(previous);
        }finally{Draw.shader(previous);Draw.reset();Draw.z(z);}
    }
    private static void lightning(boolean foreground){
        float open=aperture();if(open<=0f)return;
        float z=Draw.z();Blending blend=Draw.getBlend();
        try{
            Draw.blend(Blending.additive);
            int count=foreground?8:18;
            for(int i=0;i<count;i++){
                long seed=i*137L+(long)(elapsed/(foreground?7f:5f))*31L+(foreground?981L:0L);
                float angle=Mathf.randomSeed(seed,0f,360f),length=Mathf.randomSeed(seed+1,55f,155f)*open;
                float depth=foreground?1.2f:.35f;
                float px=x+viewX*depth*24f,py=y+viewY*depth*24f;
                float sx=px,sy=py;
                for(int j=1;j<=8;j++){
                    float along=j/8f*length,side=j==8?0f:Mathf.randomSeed(seed+j*19L,-12f,12f);
                    float nx=sx+Mathf.cosDeg(angle)*along-Mathf.sinDeg(angle)*side;
                    float ny=sy+Mathf.sinDeg(angle)*along+Mathf.cosDeg(angle)*side;
                    Draw.color(violet,open*(foreground?.25f:.16f));Lines.stroke(foreground?5f:3f);Lines.line(px,py,nx,ny);
                    Draw.color(violet,Color.white,.7f);Draw.alpha(open*.85f);Lines.stroke(foreground?1.1f:.75f);Lines.line(px,py,nx,ny);
                    px=nx;py=ny;
                }
            }
            for(int i=0;i<20;i++){
                float phase=(elapsed*.004f+i*.147f)%1f,angle=i*137.5f+elapsed*.07f;
                float radius=(foreground?150f:100f)*(1f-phase)*open;
                float depth=foreground?1.4f:.4f;
                float px=x+Mathf.cosDeg(angle)*radius+viewX*depth*24f,py=y+Mathf.sinDeg(angle)*radius+viewY*depth*24f;
                Draw.color(violet,phase*open*.65f);Fill.circle(px,py,foreground?1.8f:.9f);
            }
        }finally{Draw.blend(blend);Draw.reset();Draw.z(z);}
    }
    private static void reset(){active=false;history=false;failed=false;}
    private static void dispose(){
        reset();if(scene!=null)scene.dispose();if(tunnel!=null)tunnel.dispose();if(cinema!=null)cinema.dispose();if(surface!=null)surface.dispose();
        scene=null;tunnel=null;cinema=null;surface=null;
    }
}
