package cryon.Story;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.GlyphLayout;
import arc.math.geom.Rect;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.event.Touchable;
import arc.scene.ui.layout.Scl;
import arc.util.Align;
import cryon.Script.VirusIntrusionScript;
import mindustry.ui.Fonts;

/** Screen HUD and sequential, black-screen disarm narrative; native combat remains in the map. */
public final class IntrusionOverlay extends Element {
    private static IntrusionOverlay overlay;
    private final Color textColor = Color.valueOf("ad7774");
    private final Color bossColor = Color.valueOf("944448");
    private final Color borderColor = Color.valueOf("633337");
    private final Color glitchColor = Color.valueOf("582c32");
    private float trailingHealth = 1f, disarmTime;
    private boolean wasDisarmed;
    private final Rect combatPanel = new Rect();
    private final GlyphLayout lineLayout = new GlyphLayout();
    private static final String[] corruption = {
        "[ 0x43525558 / AUTH:?? / LINK_LOST ]",
        "[ SYS::CTRL 00 A7 -- -- / CRC:FAIL ]",
        "[ R/W DENIED / 7F ?? 02 / CRUX ]",
        "[ MEMORY::0x???? / RECOVER_PENDING ]"
    };

    public static void show(){
        if(overlay != null) overlay.remove();
        overlay = new IntrusionOverlay();
        overlay.touchable = Touchable.disabled;
        overlay.resizeToScreen();
        Core.scene.add(overlay);
    }

    /** Scene rebuilds can detach the HUD while its controller continues running. */
    public static void ensureShown(){
        if(Core.scene != null && (overlay == null || overlay.getScene() != Core.scene || !overlay.hasParent())) show();
    }

    public static void hide(){
        if(overlay != null) overlay.remove();
        overlay = null;
    }

    private void resizeToScreen(){
        // Plain Element does not implement layout; fillParent alone leaves width/height at zero.
        setBounds(0f, 0f, Core.scene.getWidth(), Core.scene.getHeight());
    }

    @Override public void act(float delta){
        super.act(delta);
        resizeToScreen();
        VirusIntrusionScript script = VirusIntrusionScript.current();
        if(script == null){ remove(); if(overlay == this) overlay = null; return; }
        trailingHealth = Mathf.lerp(trailingHealth, script.nodeHealth(), 1f - (float)Math.exp(-delta * 4f));
        boolean disarmed = script.disarmed();
        if(disarmed){
            if(script.seconds() < VirusIntrusionScript.introTicks / 60f) disarmTime = script.seconds();
            else if(!wasDisarmed) disarmTime = 0f;
            else if(!mindustry.Vars.state.isPaused()) disarmTime += Math.min(delta, 0.1f);
        }
        wasDisarmed = disarmed;
        if(!Core.scene.hasDialog()) toFront();
    }

    @Override public void draw(){
        VirusIntrusionScript script = VirusIntrusionScript.current();
        if(script == null) return;
        if(script.transforming() || script.disarmed() || script.finished()){
            // No white/full-screen flashes or map fade-through: the disarmed screen stays black.
            Draw.color(Color.black);
            Fill.crect(0f, 0f, getWidth(), getHeight());
            Draw.reset();
            float elapsed = script.finished() ? script.endingSeconds() : script.transforming() ? script.transformSeconds() : disarmTime;
            float period = script.finished() ? 1.5f : 2f;
            int count = script.finished() ? 2 : script.transforming() ? 3 : 6;
            int index = Math.min(count - 1, (int)(elapsed / period));
            float localTime = elapsed - index * period;
            String key = script.finished() ? (script.won() ? "success-" : "failure-") + (index + 1)
                    : (script.transforming() ? "transform-" : "message-") + (index + 1);
            String full = Core.bundle.get("cryon.intrusion." + key);
            int chars = Math.min(full.length(), Math.max(0, (int)(localTime * 40f)));
            float fade = Math.min(Mathf.clamp(localTime / 0.2f), Mathf.clamp((period - localTime) / 0.25f));
            // Restore solid text at the final message if an external disarm lasts longer.
            if(localTime > period) fade = 1f;
            label(full.substring(0, chars), getHeight() * 0.56f, textColor, fade, 1f);
            drawCorruption(elapsed);
        }else{
            drawBossBar(script);
        }
        Draw.reset();
    }

    private void drawCorruption(float elapsed){
        int step = (int)(elapsed / 0.65f);
        // Only dim text fragments blink, at a slow cadence. The background never flashes.
        if(step % 3 != 1){
            label(corruption[step % corruption.length], getHeight() * 0.43f, glitchColor, 0.85f, 0.72f);
        }
        if(step % 4 == 0){
            label("// NULL -> RETRY :: ? ? ?", getHeight() * 0.68f, glitchColor, 0.5f, 0.65f);
        }
    }

    /** Compact panel below native HUD controls; safeInset is expressed in scene coordinates. */
    public static void combatBounds(Rect out, float width, float height, boolean mobile, float safeInset){
        float margin = safeInset + Scl.scl(16f);
        float panelWidth = Math.max(1f, Math.min(Scl.scl(340f), width - margin * 2f));
        float panelHeight = Math.min(Scl.scl(56f), height * 0.28f);
        float x = (width - panelWidth) / 2f;
        float topGap = Scl.scl(mobile ? 160f : 64f);
        if(mobile && width > height && height < Scl.scl(480f)
                && width - Scl.scl(320f) - margin * 2f >= Scl.scl(96f)){
            // Short landscape screens: use the space right of the native unit panel,
            // so moving the bar down cannot cover the player at screen center.
            panelWidth = Math.max(1f, Math.min(panelWidth, width - Scl.scl(320f) - margin * 2f));
            x = width - margin - panelWidth;
            topGap = Scl.scl(68f);
        }
        float inset = Math.min(Math.max(0f, height - panelHeight - Scl.scl(16f)), safeInset + topGap);
        out.set(x, height - inset - panelHeight, panelWidth, panelHeight);
    }

    private void drawBossBar(VirusIntrusionScript script){
        // Insets are physical pixels. Conservatively reserve the largest edge inset for cutouts.
        float safe = 0f;
        int[] insets = Core.graphics.getSafeInsets();
        if(insets != null) for(int inset : insets) safe = Math.max(safe, inset);
        safe *= getHeight() / Math.max(1, Core.graphics.getHeight());
        combatBounds(combatPanel, getWidth(), getHeight(), mindustry.Vars.mobile, safe);
        float unit = combatPanel.height / 56f;
        float x = combatPanel.x + unit * 12f, width = Math.max(1f, combatPanel.width - unit * 24f);
        float top = combatPanel.y + combatPanel.height;
        float y = top - unit * 31f, height = unit * 6f;
        Draw.color(Color.black, 0.85f);
        Fill.crect(combatPanel.x, combatPanel.y, combatPanel.width, combatPanel.height);
        Draw.color(borderColor);
        Fill.crect(x, y, width * Math.max(trailingHealth, script.nodeHealth()), height);
        Draw.color(bossColor);
        Fill.crect(x, y, width * script.nodeHealth(), height);
        Draw.color(textColor);
        Lines.stroke(Scl.scl(1f));
        Lines.rect(x, y, width, height);
        Lines.line(x + width * 0.5f, y, x + width * 0.5f, y + height);
        Draw.reset();
        combatLine(Core.bundle.format("cryon.intrusion.compact-boss", (int)Math.ceil(script.nodeHealth() * 100f),
                script.enraged() ? "II" : "I"), x, top - unit * 5f, width, 0.8f * unit / Scl.scl());
        String attack = Core.bundle.get("cryon.intrusion." + (script.enraged() ? "short-rage-" : "mode-") + script.phase());
        combatLine(Core.bundle.format("cryon.intrusion.compact-status", (int)Math.ceil(script.health()),
                (int)Math.ceil(script.remainingSeconds()), attack), x, top - unit * 37f, width, 0.7f * unit / Scl.scl());
        Lines.stroke(1f);
    }

    private void combatLine(String value, float x, float y, float width, float scale){
        float oldX = Fonts.outline.getData().scaleX, oldY = Fonts.outline.getData().scaleY;
        boolean oldMarkup = Fonts.outline.getData().markupEnabled;
        Fonts.outline.getData().markupEnabled = false;
        Fonts.outline.getData().setScale(Scl.scl(scale));
        lineLayout.setText(Fonts.outline, value);
        if(lineLayout.width > width) Fonts.outline.getData().setScale(Scl.scl(scale) * width / lineLayout.width);
        Fonts.outline.setColor(textColor);
        // Combat labels always stay on one line; narrative text keeps its own wrapped layout.
        Fonts.outline.draw(value, x, y, width, Align.center, false);
        Fonts.outline.getData().markupEnabled = oldMarkup;
        Fonts.outline.getData().setScale(oldX, oldY);
        Fonts.outline.setColor(Color.white);
    }

    private void label(String value, float y, Color color, float alpha, float scale){
        float oldX = Fonts.outline.getData().scaleX, oldY = Fonts.outline.getData().scaleY;
        Fonts.outline.getData().setScale(Scl.scl(scale));
        Fonts.outline.setColor(color.r, color.g, color.b, alpha);
        boolean oldMarkup = Fonts.outline.getData().markupEnabled;
        Fonts.outline.getData().markupEnabled = false; // Corrupted [..] strings are literal text.
        Fonts.outline.draw(value, 12f, y, Math.max(1f, getWidth() - 24f), Align.center, true);
        Fonts.outline.getData().markupEnabled = oldMarkup;
        Fonts.outline.getData().setScale(oldX, oldY);
        Fonts.outline.setColor(Color.white);
    }
}
