package cryon.Features;

import arc.Core;
import arc.Events;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.Gl;
import arc.graphics.g2d.Draw;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.gl.Shader;
import arc.util.Log;
import cryon.Fx.SpaceJumpFx;
import cryon.Type.SpaceJumpAbility;
import mindustry.Vars;
import mindustry.entities.abilities.Ability;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;

/** Intrusion-style point structures from native hull/weapon/part draws, not an icon mask. */
public class SpaceJumpPointRenderer{
    private static final Ability[] noAbilities = new Ability[0];
    private static final arc.graphics.g2d.TextureRegion invisibleShadow = new arc.graphics.g2d.TextureRegion();
    private static FrameBuffer discarded, hull;
    private static Shader shader;
    private static float progress;
    private static int tier, pointDraws;
    private static boolean failed;
    private static final Blending premultiplied = new Blending(Gl.one, Gl.oneMinusSrcAlpha);

    public static void install(){
        if(Vars.headless) return;
        Events.run(mindustry.game.EventType.Trigger.draw, SpaceJumpPointRenderer::draw);
        Events.on(mindustry.game.EventType.DisposeEvent.class, event -> {
            if(discarded != null) discarded.dispose();
            if(hull != null) hull.dispose();
            if(shader != null) shader.dispose();
        });
    }

    private static float amount(Unit unit){
        for(Ability ability : unit.abilities){
            if(ability instanceof SpaceJumpAbility){
                SpaceJumpAbility jump = (SpaceJumpAbility)ability;
                if(jump.active() && jump.digitalizing()) return arc.math.Mathf.clamp((jump.chargeProgress() - 0.55f) / 0.45f);
            }
        }
        return 0f;
    }

    private static void draw(){
        if(failed || !Vars.state.isGame()) return;
        boolean needed = false;
        for(Unit unit : Groups.unit) if(!unit.dead && amount(unit) > 0f){ needed = true; break; }
        if(!needed) return;
        try{ ensureResources(); }
        catch(Throwable error){ failed = true; Log.err("[SpaceJump] native point renderer unavailable", error); return; }
        pointDraws = 0;
        capture(Layer.flyingUnitLow);
        capture(Layer.flyingUnit);
    }

    private static void capture(float layer){
        Draw.drawRange(layer, 0.49f, () -> discarded.begin(Color.clear), () -> {
            Draw.flush(); discarded.end();
            Blending previous = Draw.getBlend();
            try{
                for(Unit unit : Groups.unit){
                    if(Math.abs(unit.type.flyingLayer - layer) > 0.01f || (!(unit.elevation > 0.5f || (unit.type.flying && unit.dead)))) continue;
                    if(Vars.player != null && unit.inFogTo(Vars.player.team())) continue;
                    float margin = unit.type.clipSize;
                    if(Math.abs(unit.x - Core.camera.position.x) > Core.camera.width / 2f + margin
                        || Math.abs(unit.y - Core.camera.position.y) > Core.camera.height / 2f + margin) continue;
                    progress = amount(unit); tier = SpaceJumpAbility.level(unit);
                    // Each processed hull costs a full-screen framebuffer pass.
                    // Keep excess ships visible through the ordinary native draw.
                    if(progress <= 0f || pointDraws >= (Vars.mobile ? 3 : 12)){ drawNativeHull(unit, layer); Draw.flush(); continue; }
                    pointDraws++;
                    hull.begin(Color.clear);
                    Blending.normal.apply();
                    drawNativeHull(unit, layer);
                    Draw.flush(); hull.end();
                    premultiplied.apply();
                    hull.blit(shader);
                    Blending.normal.apply();
                }
            }finally{ previous.apply(); Draw.reset(); }
        });
    }

    /** Redraw the actual native unit, including animated parts and rotating weapon mounts. */
    private static void drawNativeHull(Unit unit, float layer){
        UnitType type = unit.type;
        Ability[] abilities = unit.abilities;
        arc.graphics.g2d.TextureRegion shadow = type.shadowRegion;
        boolean softShadow = type.drawSoftShadow;
        float shadowAlpha = unit.shadowAlpha, shieldAlpha = unit.shieldAlpha;
        float lightRadius = type.lightRadius;
        arc.struct.Seq<UnitType.UnitEngine> engines = type.engines;
        float z = Draw.z();
        try{
            // Shadows, shield abilities and lights were already queued at their normal layers.
            unit.abilities = noAbilities;
            invisibleShadow.texture = shadow == null ? type.region.texture : shadow.texture;
            type.shadowRegion = invisibleShadow;
            type.drawSoftShadow = false;
            type.lightRadius = 0f;
            unit.shieldAlpha = 0f;
            Draw.z(layer);
            type.draw(unit);
        }finally{
            unit.abilities = abilities;
            type.engines = engines;
            type.shadowRegion = shadow; type.drawSoftShadow = softShadow; type.lightRadius = lightRadius;
            unit.shadowAlpha = shadowAlpha; unit.shieldAlpha = shieldAlpha;
            Draw.reset(); Draw.z(z);
        }
    }

    private static void ensureResources(){
        int width = Core.graphics.getWidth(), height = Core.graphics.getHeight();
        if(discarded == null) discarded = new FrameBuffer();
        if(hull == null) hull = new FrameBuffer();
        discarded.resize(width,height); hull.resize(width,height);
        if(shader == null) shader = new Shader(Vars.tree.get("shaders/screenspace.vert"), Vars.tree.get("shaders/space-jump-points.frag")){
            @Override public void apply(){
                setUniformi("u_texture",0);
                setUniformf("u_pixel",1f/hull.getWidth(),1f/hull.getHeight());
                setUniformf("u_origin",Core.camera.position.x-Core.camera.width/2f,Core.camera.position.y-Core.camera.height/2f);
                setUniformf("u_worldsize",Core.camera.width,Core.camera.height);
                setUniformf("u_progress",progress);
                Color tint = SpaceJumpFx.tint(tier);
                setUniformf("u_tint",tint.r,tint.g,tint.b);
            }
        };
    }
}
