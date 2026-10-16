package cryon.Story;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
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
        if(script.disarmed() || script.finished()){
            // No white/full-screen flashes or map fade-through: the disarmed screen stays black.
            Draw.color(Color.black);
            Fill.crect(0f, 0f, getWidth(), getHeight());
            Draw.reset();
            float elapsed = script.finished() ? script.endingSeconds() : disarmTime;
            float period = script.finished() ? 1.5f : 2f;
            int count = script.finished() ? 2 : 6;
            int index = Math.min(count - 1, (int)(elapsed / period));
            float localTime = elapsed - index * period;
            String key = script.finished() ? (script.won() ? "success-" : "failure-") + (index + 1)
                    : "message-" + (index + 1);
            String full = Core.bundle.get("cryon.intrusion." + key);
            int chars = Math.min(full.length(), Math.max(0, (int)(localTime * 40f)));
            float fade = Math.min(Mathf.clamp(localTime / 0.2f), Mathf.clamp((period - localTime) / 0.25f));
            // Restore solid text at the final message if an external disarm lasts longer.
            if(localTime > period) fade = 1f;
            label(full.substring(0, chars), getHeight() * 0.56f, textColor, fade, 1f);
            drawCorruption(elapsed);
        }else{
            drawBossBar(script);
            label(Core.bundle.format("cryon.intrusion.native-hud", Math.max(0, (int)script.health()),
                    Math.max(0, (int)(script.nodeHealth() * 100f)), (int)Math.ceil(script.remainingSeconds())),
                    getHeight() - Scl.scl(78f), textColor, 1f, 0.8f);
            if(script.orbitalSupportActive()){
                label(Core.bundle.get("cryon.intrusion.orbital-support"),
                        getHeight() - Scl.scl(102f), textColor, 1f, 0.8f);
            }
            if(script.recoveryActive()){
                label(Core.bundle.get("cryon.intrusion.recovery"),
                        getHeight() - Scl.scl(126f), textColor, 1f, 0.8f);
            }
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

    private void drawBossBar(VirusIntrusionScript script){
        float width = Math.max(1f, Math.min(Scl.scl(600f), getWidth() - Scl.scl(32f)));
        float height = Scl.scl(12f), x = (getWidth() - width) / 2f;
        float y = getHeight() - Scl.scl(49f);
        Draw.color(Color.black);
        Fill.crect(x - Scl.scl(8f), y - Scl.scl(6f), width + Scl.scl(16f), height + Scl.scl(37f));
        Draw.color(borderColor);
        Fill.crect(x, y, width * Math.max(trailingHealth, script.nodeHealth()), height);
        Draw.color(bossColor);
        Fill.crect(x, y, width * script.nodeHealth(), height);
        Draw.color(textColor);
        Lines.stroke(Scl.scl(1f));
        Lines.rect(x, y, width, height);
        Draw.color(borderColor);
        for(int i = 1; i <= 2; i++) Lines.line(x + width * i / 3f, y, x + width * i / 3f, y + height);
        Draw.reset();
        label(Core.bundle.format("cryon.intrusion.boss-bar", (int)Math.ceil(script.nodeHealthValue()),
                (int)Math.ceil(script.nodeMaxHealth()), script.phase() + 1),
                getHeight() - Scl.scl(15f), textColor, 1f, 0.9f);
        Lines.stroke(1f);
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
