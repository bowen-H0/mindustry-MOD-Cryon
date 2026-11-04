package cryon.Features;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.Texture;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.gl.FrameBuffer;
import arc.math.Mathf;
import arc.math.Mat;
import arc.math.geom.Vec3;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.event.Touchable;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType.*;
import mindustry.gen.Icon;
import mindustry.graphics.g3d.PlanetParams;
import mindustry.graphics.g3d.PlanetRenderer;
import mindustry.type.Planet;
import mindustry.ui.Styles;

/** A menu-only star-map scene, independent of the interactive campaign camera. */
public final class MainMenuTheme{
    public static final String setting = "cryon-menu-theme";
    public static final String themeSetting = "cryon-menu-theme-id";
    public enum Theme{ standard, cryon, remainingLife }
    private static boolean installed;
    private static Background background;

    private MainMenuTheme(){}
    public static Theme current(){
        String id = Core.settings.getString(themeSetting, "");
        for(Theme theme : Theme.values()) if(theme.name().equals(id)) return theme;
        // Preserve the earlier two-theme setting on existing installations.
        return Core.settings.getBool(setting, false) ? Theme.cryon : Theme.standard;
    }
    public static boolean isCryon(){ return current() == Theme.cryon; }

    public static void select(boolean cryon){
        select(cryon ? Theme.cryon : Theme.standard);
    }

    public static void select(Theme theme){
        Core.settings.put(themeSetting, theme.name());
        Core.settings.put(setting, theme == Theme.cryon);
        Core.settings.forceSave();
        if(background != null){ background.dispose(); background.elapsed = 0f; }
    }

    public static void install(){
        if(installed || Vars.headless) return;
        installed = true;
        Events.on(ClientLoadEvent.class, event -> {
            Vars.ui.settings.addCategory(Core.bundle.get("cryon.menu-theme.title"), Icon.planet, table -> {
                table.add("@cryon.menu-theme.description").wrap().width(420f).pad(16f).row();
                table.button("@cryon.menu-theme.default", Styles.togglet, () -> select(false))
                        .checked(button -> current() == Theme.standard).width(420f).height(64f).pad(6f).get().name = "cryon-menu-theme-default";
                table.row();
                table.button("@cryon.menu-theme.cryon", Styles.togglet, () -> select(true))
                        .checked(button -> isCryon()).width(420f).height(64f).pad(6f).get().name = "cryon-menu-theme-cryon";
                table.row();
                table.button("@cryon.menu-theme.remaining-life", Styles.togglet, () -> select(Theme.remainingLife))
                        .checked(button -> current() == Theme.remainingLife).width(420f).height(64f).pad(6f).get().name = "cryon-menu-theme-life";
                table.row();
            });
            Core.app.post(MainMenuTheme::attach);
        });
        Events.run(Trigger.universeDrawBegin, () -> {
            if(background != null && background.rendering) background.moveCamera();
        });
        Events.on(DisposeEvent.class, event -> { if(background != null) background.dispose(); });
    }

    private static void attach(){
        if(Vars.ui.menuGroup.find("cryon-menu-background") != null) return;
        Element container = Vars.ui.menuGroup.find("menu container");
        if(container == null) container = Vars.ui.menuGroup.find("buttons");
        if(container == null) throw new IllegalStateException("Cannot find native main-menu container");
        Element root = container;
        while(root.parent != null && root.parent != Vars.ui.menuGroup) root = root.parent;
        if(!(root instanceof Group) || !((Group)root).hasChildren()){
            throw new IllegalStateException("Cannot find native main-menu background");
        }
        Group group = (Group)root;
        Element original = group.getChildren().first();
        background = new Background();
        background.name = "cryon-menu-background";
        background.setFillParent(true);
        background.touchable = Touchable.disabled;
        background.visible(background::active);
        // Keep the original actor and renderer intact; only one background draws at a time.
        original.visible(() -> !background.active());
        group.addChildAt(0, background);
    }

    private static class Background extends Element{
        private final PlanetParams params = new PlanetParams();
        private final Mat projection = new Mat();
        private final Vec3 right = new Vec3(), up = new Vec3(), target = new Vec3();
        private PlanetRenderer renderer;
        private FrameBuffer buffer;
        private UnderseaMenuScene lifeScene;
        private float elapsed;
        private boolean rendering, failed;

        boolean active(){
            if(current() == Theme.standard || failed || Vars.state == null || !Vars.state.isMenu()) return false;
            var map = CryonCampaignLayers.activeDialog();
            return (map == null || !map.isShown()) && !Vars.ui.planet.isShown()
                    && !Vars.ui.editor.isShown() && Vars.content.planet("cryon-cryon") != null;
        }

        @Override public void act(float delta){
            super.act(delta);
            if(active()) elapsed += Math.min(delta, 1f / 15f);
        }

        @Override public void draw(){
            validate();
            if(current() == Theme.remainingLife){
                if(lifeScene == null) lifeScene = new UnderseaMenuScene();
                lifeScene.draw(x, y, width, height, elapsed);
                return;
            }
            Planet planet = Vars.content.planet("cryon-cryon");
            Planet depths = Vars.content.planet("cryon-cryon-depths");
            boolean surfaceVisible = planet.visible, depthsVisible = depths != null && depths.visible;
            boolean captured = false;
            Draw.flush();
            projection.set(Draw.proj());
            try{
                if(renderer == null) renderer = new PlanetRenderer();
                int screenW = Core.graphics.getWidth(), screenH = Core.graphics.getHeight();
                float scale = Math.min(1f, (Vars.mobile ? 1080f : 1600f) / Math.max(screenW, screenH));
                int renderW = Math.max(2, (int)(screenW * scale)), renderH = Math.max(2, (int)(screenH * scale));
                if(buffer == null){
                    buffer = new FrameBuffer(renderW, renderH, true);
                    buffer.getTexture().setFilter(Texture.TextureFilter.linear);
                }else buffer.resize(renderW, renderH);
                Vars.universe.updateGlobal();
                planet.visible = true;
                if(depths != null) depths.visible = false;
                params.planet = planet;
                params.drawUi = false;
                params.uiAlpha = 0f;
                params.drawSkybox = true;
                params.viewW = renderW;
                params.viewH = renderH;
                // Stay near the lit hemisphere, drifting through a slow orbital arc.
                params.camPos.set(planet.solarSystem.position).sub(planet.position).nor();
                if(params.camPos.isZero()) params.camPos.set(1f, 0f, 0f);
                params.camPos.rotate(Vec3.Y, 32f + 24f * Mathf.sin(elapsed * 0.022f));
                params.camPos.y += 0.22f + 0.12f * Mathf.sin(elapsed * 0.037f + 0.6f);
                params.zoom = 0.66f + 0.12f * Mathf.sin(elapsed * 0.029f + 0.8f);
                buffer.begin(Color.black);
                captured = true;
                rendering = true;
                renderer.render(params);
                rendering = false;
                buffer.end();
                captured = false;
                Draw.proj(projection);
                Draw.reset();
                Draw.rect(Draw.wrap(buffer.getTexture()), x + width / 2f, y + height / 2f, width, -height);
                // A quiet left-side shade keeps native menu buttons readable.
                float left = Color.toFloatBits(0f, 0f, 0f, 0.48f), clear = Color.toFloatBits(0f, 0f, 0f, 0f);
                Fill.quad(x, y, left, x + width * 0.7f, y, clear,
                        x + width * 0.7f, y + height, clear, x, y + height, left);
                Draw.reset();
            }catch(RuntimeException error){
                failed = true;
                Log.err("[Cryon] Main-menu planet rendering failed; restoring the default background", error);
            }finally{
                rendering = false;
                if(captured) buffer.end();
                planet.visible = surfaceVisible;
                if(depths != null) depths.visible = depthsVisible;
                Draw.proj(projection);
                Draw.reset();
            }
        }

        /** Called after the native camera setup and before any planet geometry is rendered. */
        void moveCamera(){
            var camera = renderer.cam;
            right.set(camera.direction).crs(camera.up).nor();
            up.set(camera.up);
            float radius = params.planet.radius;
            // Truck and crane the eye while panning across an independent, moving look-at point.
            camera.position.mulAdd(right, radius * 0.24f * Mathf.sin(elapsed * 0.041f));
            camera.position.mulAdd(up, radius * 0.12f * Mathf.sin(elapsed * 0.031f + 1.3f));
            float aspect = (float)params.viewW / params.viewH;
            float framing = aspect > 1.2f ? -0.46f : -0.10f;
            target.set(params.planet.position).mulAdd(right, radius * (framing + 0.15f * Mathf.sin(elapsed * 0.026f)));
            target.mulAdd(up, radius * 0.10f * Mathf.sin(elapsed * 0.039f + 0.4f));
            camera.lookAt(target);
            camera.up.rotate(camera.direction, 1.8f * Mathf.sin(elapsed * 0.023f));
            camera.update();
            params.camDir.set(camera.direction);
            params.camUp.set(camera.up);
            renderer.batch.proj(camera.combined);
            renderer.projector.proj(camera.combined);
        }

        void dispose(){
            if(lifeScene != null){ lifeScene.dispose(); lifeScene = null; }
            if(buffer != null){ buffer.dispose(); buffer = null; }
            if(renderer != null){ renderer.dispose(); renderer = null; }
            failed = false;
        }
    }
}
