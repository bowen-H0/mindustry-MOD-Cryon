package cryon.Story;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.GlyphLayout;
import arc.math.Mathf;
import arc.math.Interp;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.event.Touchable;
import arc.scene.ui.layout.Scl;
import cryon.Script.DesolatePlainScript;
import mindustry.game.EventType.*;
import mindustry.entities.Effect;
import mindustry.ui.Fonts;
import mindustry.input.InputHandler;

import static mindustry.Vars.*;

/** Local presentation only; all fleet spawning and progress belong to the server processor. */
public final class DesolateFleetCutscene extends Element{
    private static DesolateFleetCutscene overlay;
    private InputHandler input;
    private boolean oldCutscene, oldHideHud;
    private float oldZoom, oldSpeed, returnZoom, oldMinZoom;
    private int lastShot = -1;
    private final GlyphLayout layout = new GlyphLayout();
    private final Vec2 oldPan = new Vec2(), returnTo = new Vec2();

    public static void install(){
        if(!headless) Events.run(Trigger.draw, DesolateFleetArrivalRenderer::draw);
        Events.on(ClientLoadEvent.class, event -> {
            if(headless) return;
            overlay = new DesolateFleetCutscene();
            overlay.touchable = Touchable.disabled;
            Core.scene.add(overlay);
        });
        Events.on(ResetEvent.class, event -> { if(overlay != null) overlay.release(); });
    }

    private DesolatePlainScript director(){
        DesolatePlainScript script = DesolatePlainScript.current();
        return script != null && script.phase == DesolatePlainScript.intro && state.isGame() ? script : null;
    }

    @Override public void act(float delta){
        super.act(delta);
        setSize(Core.scene.getWidth(), Core.scene.getHeight());
        DesolatePlainScript script = director();
        if(script == null || control == null || control.input == null){ release(); return; }
        if(input == null){
            input = control.input;
            oldCutscene = input.logicCutscene;
            oldHideHud = ui.hudfrag.shown;
            oldZoom = input.logicCutsceneZoom;
            oldMinZoom = renderer.minZoom;
            float returnScale = oldCutscene ? Mathf.lerp(oldMinZoom, renderer.maxZoom, oldZoom) : renderer.targetscale;
            float scale = Math.min(1f, Math.min(world.unitWidth(), world.unitHeight()) / 1600f);
            float fit = Math.min(Core.graphics.getWidth(), Core.graphics.getHeight() * 0.8f) / (1450f * scale + 120f);
            renderer.minZoom = Math.min(oldMinZoom, fit / Scl.scl(1f));
            returnZoom = Mathf.clamp((returnScale - renderer.minZoom) / (renderer.maxZoom - renderer.minZoom));
            oldSpeed = input.logicCamSpeed;
            oldPan.set(input.logicCamPan);
            returnTo.set(Core.camera.position);
        }
        input.logicCutscene = true;
        ui.hudfrag.shown = false;
        input.logicCamSpeed = 0.14f;
        // Track the fleet as one advancing mass, keeping the complete battle line readable.
        Vec2 shot = new Vec2().trns(script.heading + 180f, script.entryOffset()).add(script.centerX, script.centerY);
        int shotIndex = script.elapsed >= DesolatePlainScript.fleetArrival ? 1 : 0;
        if(lastShot >= 0 && shotIndex > lastShot && !state.isPaused()){
            Effect.shake(5f, 16f, Core.camera.position);
        }
        lastShot = shotIndex;
        float back = Interp.smooth.apply(Mathf.clamp((script.elapsed - DesolatePlainScript.returnStart)
            / (DesolatePlainScript.introDuration - DesolatePlainScript.returnStart)));
        input.logicCamPan.set(shot).lerp(returnTo, back);
        input.logicCutsceneZoom = Mathf.lerp(0f, returnZoom, back);
    }

    private void release(){
        if(input == null) return;
        input.logicCutscene = oldCutscene;
        if(ui != null) ui.hudfrag.shown = oldHideHud;
        input.logicCamPan.set(oldPan);
        input.logicCutsceneZoom = oldZoom;
        input.logicCamSpeed = oldSpeed;
        renderer.minZoom = oldMinZoom;
        input = null;
        lastShot = -1;
    }

    @Override public void draw(){
        DesolatePlainScript script = director();
        if(script == null) return;
        float fade = Math.min(Mathf.clamp(script.elapsed / 15f), Mathf.clamp((DesolatePlainScript.introDuration - script.elapsed) / 20f));
        float bar = Math.min(80f, height * 0.11f) * fade;
        Draw.color(Color.black, 0.94f);
        Fill.rect(width / 2f, bar / 2f, width, bar);
        Fill.rect(width / 2f, height - bar / 2f, width, bar);
        label(Core.bundle.get("cryon.desolate.fleet-title"), height - bar / 2f, Color.scarlet, fade);
        Draw.reset();
    }

    private void label(String text, float y, Color color, float alpha){
        layout.setText(Fonts.outline, text);
        float scale = Math.min(1f, width * 0.9f / Math.max(1f, layout.width));
        Fonts.outline.draw(text, width / 2f, y, new Color(color.r, color.g, color.b, alpha),
            scale, true, arc.util.Align.center);
    }
}
