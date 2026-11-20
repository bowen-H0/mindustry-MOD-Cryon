package cryon.Features;

import arc.Core;
import arc.Events;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.Texture;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.gl.Shader;
import arc.math.Mat;
import arc.math.geom.Vec3;
import arc.struct.ObjectMap;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType.*;
import mindustry.graphics.g3d.PlanetParams;
import mindustry.graphics.g3d.PlanetRenderer;
import mindustry.type.Planet;

/** Shared, bounded planet thumbnails refreshed before world drawing, never during its sorted batches. */
public final class PlanetProjectionRenderer{
    private static final ObjectMap<Planet, Projection> cache = new ObjectMap<>();
    private static final PlanetParams params = new PlanetParams();
    private static PlanetRenderer renderer;
    private static boolean installed;
    private PlanetProjectionRenderer(){}

    public static void install(){
        if(Vars.headless || installed) return;
        installed = true;
        Events.run(Trigger.preDraw, PlanetProjectionRenderer::refresh);
        Events.on(ResetEvent.class, event -> {
            for(Projection projection : cache.values()) projection.dispose();
            cache.clear();
        });
        Events.on(DisposeEvent.class, event -> {
            for(Projection projection : cache.values()) projection.dispose();
            cache.clear();
            if(renderer != null) renderer.dispose();
            renderer = null;
        });
    }

    public static TextureRegion request(Planet planet){
        if(Vars.headless || planet == null) return null;
        Projection projection = cache.get(planet);
        long frame = Core.graphics.getFrameId();
        if(projection == null){
            projection = new Projection();
            cache.put(planet, projection);
        }
        projection.requested = frame;
        return projection.failed ? null : projection.region;
    }

    private static void refresh(){
        if(cache.isEmpty()) return;
        long frame = Core.graphics.getFrameId();
        for(int i = 0; i < 2; i++){
            Planet oldest = null;
            long last = Long.MAX_VALUE;
            for(var entry : cache){
                Projection p = entry.value;
                if(!p.failed && frame - p.requested <= 3L && frame - p.rendered >= 6L && p.rendered < last){
                    oldest = entry.key;
                    last = p.rendered;
                }
            }
            if(oldest == null) break;
            Projection p = cache.get(oldest);
            render(oldest, p);
            p.rendered = frame;
        }
    }

    private static void render(Planet planet, Projection projection){
        Mat savedProjection = new Mat(Draw.proj()), savedTransform = new Mat(Draw.trans());
        Shader shader = Draw.getShader();
        Blending blending = Draw.getBlend();
        float z = Draw.z();
        boolean bound = false;
        try{
            Draw.flush();
            if(renderer == null) renderer = new PlanetRenderer(){
                @Override public void renderPlanet(Planet ignored, PlanetParams params){
                    Planet selected = params.planet;
                    selected.draw(params, cam.combined, selected.getTransform(mat));
                }
                @Override public void renderTransparent(Planet ignored, PlanetParams params){
                    Planet selected = params.planet;
                    selected.drawClouds(params, cam.combined, selected.getTransform(mat));
                    if(selected.hasAtmosphere) selected.drawAtmosphere(atmosphere, cam);
                }
            };
            int resolution = Vars.mobile ? 256 : 384;
            if(projection.buffer == null){
                projection.buffer = new FrameBuffer(resolution, resolution, true);
                projection.buffer.getTexture().setFilter(Texture.TextureFilter.linear);
                projection.region = new TextureRegion(projection.buffer.getTexture());
                projection.region.flip(false, true);
            }
            Vars.universe.updateGlobal();
            params.planet = planet;
            params.drawUi = params.drawSkybox = false;
            params.uiAlpha = 0f;
            params.alwaysDrawAtmosphere = true;
            params.viewW = params.viewH = resolution;
            params.zoom = planet.name.equals("cryon-porphyra") || planet.name.equals("cryon-cryon") ? 1.1f : 0.6f;
            params.camPos.set(planet.solarSystem.position).sub(planet.position).nor();
            if(params.camPos.isZero()) params.camPos.set(1f, 0f, 1f).nor();
            params.camPos.rotate(Vec3.Y, 28f + Time.time * 0.04f);
            params.camPos.y += 0.28f;
            projection.buffer.begin(Color.clear);
            bound = true;
            renderer.render(params);
            Draw.flush();
        }catch(RuntimeException error){
            projection.failed = true;
            Log.err("[Cryon] Planet projection unavailable; using holographic wireframe", error);
        }finally{
            if(bound) projection.buffer.end();
            Draw.proj(savedProjection);
            Draw.trans(savedTransform);
            Draw.shader(shader);
            Draw.blend(blending);
            Draw.z(z);
            Draw.reset();
        }
    }

    private static final class Projection{
        FrameBuffer buffer;
        TextureRegion region;
        long requested, rendered = -100L;
        boolean failed;
        void dispose(){if(buffer != null) buffer.dispose();}
    }
}
