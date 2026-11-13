package cryon.UI;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.event.Touchable;
import arc.scene.ui.layout.Scl;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.serialization.JsonReader;
import arc.util.serialization.JsonValue;
import mindustry.game.EventType.*;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.ui.Fonts;

import static mindustry.Vars.*;

/** Reusable, non-interactive radio HUD. Call broadcast/broadcastKey from authoritative scripts. */
public final class RadioDialogue extends Element{
    /** Presentation only: encrypted does not restrict recipients or encrypt network packets. */
    public enum Mode{ normal, encrypted }
    private static final Color encryptedAccent = Color.valueOf("76e8c4");
    private static final String packet = "cryon-radio-dialogue";
    private static final int maxQueued = 32, maxText = 2048;
    private static boolean installed;
    private static RadioDialogue overlay;
    private final Seq<Message> queue = new Seq<>();
    private final FontCache body = new FontCache(Fonts.outline);
    private final FontCache heading = new FontCache(Fonts.outline);
    private final FontCache status = new FontCache(Fonts.outline);
    private final GlyphLayout layout = new GlyphLayout();
    private final GlyphLayout statusLayout = new GlyphLayout();
    private final Vec2 point = new Vec2();
    private Message current;
    private Element minimap, coordinates;
    private int glyphs;
    private float age, lifetime, textWidth = -1f, bodyHeight, statusHeight, typingRate = charsPerSecond;
    private static final float charsPerSecond = 24f;

    private static final class Message{
        final Team team;
        final String speaker;
        final Seq<String> sentences;
        final Mode mode;
        float hold, offset, duration;
        int sentence;
        Message(Team team, String speaker, Seq<String> sentences, Mode mode){
            this.team = team; this.speaker = speaker; this.sentences = sentences;
            this.mode = mode;
        }
    }

    private RadioDialogue(){
        name = "cryon-radio-dialogue";
        touchable = Touchable.disabled;
        visible(() -> hudVisible() && (current != null || !queue.isEmpty()));
    }

    public static void install(){
        if(installed) return;
        installed = true;
        Events.on(ClientLoadEvent.class, event -> {
            if(headless) return;
            overlay = new RadioDialogue();
            Core.scene.add(overlay);
            netClient.addPacketHandler(packet, RadioDialogue::receive);
        });
        Events.on(ResetEvent.class, event -> dismissAll());
        Events.on(WorldLoadEvent.class, event -> dismissAll());
    }

    /** Local-only dialogue. Each sentence types and holds separately; commas stay within a sentence. */
    public static void show(Team team, String speaker, String text){
        show(team, speaker, text, Mode.normal);
    }

    public static void show(Team team, String speaker, String text, Mode mode){
        if(headless || overlay == null || state == null || !state.isGame() || team == null
            || text == null || text.trim().isEmpty() || text.length() > maxText) return;
        if(overlay.queue.size >= maxQueued) return;
        Seq<String> sentences = splitSentences(text);
        if(!sentences.isEmpty()) overlay.queue.add(new Message(team, speaker == null ? team.localized() : speaker,
            sentences, mode == null ? Mode.normal : mode));
    }

    private static boolean terminator(char c){
        return c == '。' || c == '！' || c == '？' || c == '!' || c == '?' || c == '；' || c == ';' || c == '…';
    }

    private static boolean closingQuote(char c){
        return c == '”' || c == '’' || c == '」' || c == '』' || c == '"' || c == '\'';
    }

    /** Sentence stops/newlines are page breaks, retaining punctuation and trailing quotes. */
    public static Seq<String> splitSentences(String text){
        Seq<String> result = new Seq<>();
        int start = 0;
        for(int i = 0; i < text.length(); i++){
            char c = text.charAt(i);
            boolean newline = c == '\n' || c == '\r';
            boolean period = c == '.' && (i + 1 == text.length() || Character.isWhitespace(text.charAt(i + 1))
                || closingQuote(text.charAt(i + 1)));
            if(!newline && !period && !terminator(c)) continue;
            int end = newline ? i : i + 1;
            if(!newline){
                while(end < text.length() && (terminator(text.charAt(end)) || text.charAt(end) == '.'
                    || closingQuote(text.charAt(end)))) end++;
                i = end - 1;
            }
            String sentence = text.substring(start, end).trim();
            if(!sentence.isEmpty()) result.add(sentence);
            start = i + 1;
        }
        String tail = text.substring(start).trim();
        if(!tail.isEmpty()) result.add(tail);
        return result;
    }

    /** Shared by the authoritative map timeline and the HUD, independent of font availability. */
    public static float pageDuration(String text, float hold){
        int count = text.codePointCount(0, text.length());
        return 0.25f + count / charsPerSecond + Math.max(4f, count * 0.055f) + 0.4f + hold;
    }

    private static void showPage(Team team, String speaker, String text, Mode mode, float hold, float offset, float duration){
        if(headless || overlay == null || state == null || !state.isGame() || team == null
            || text == null || text.trim().isEmpty() || text.length() > maxText || overlay.queue.size >= maxQueued) return;
        Message message = new Message(team, speaker == null ? team.localized() : speaker, Seq.with(text), mode);
        message.hold = hold;
        message.duration = duration;
        message.offset = Math.min(offset, duration > 0f ? duration : pageDuration(text, hold));
        overlay.queue.add(message);
    }

    /** One configured page, including its extra hold and saved playback position. */
    public static void broadcastPage(Team team, String speaker, String text, Mode mode, float hold, float offset){
        broadcastPage(team, speaker, text, mode, hold, offset, "", "", 0f);
    }

    /** Keys resolve on each client; a shared duration keeps the script's events synchronized. */
    public static void broadcastPage(Team team, String speaker, String text, Mode mode, float hold, float offset,
                                     String textKey, String speakerKey, float duration){
        if(net.client() || team == null || text == null || text.length() > maxText) return;
        if(net.server()){
            JsonValue data = new JsonValue(JsonValue.ValueType.object);
            data.addChild("team", new JsonValue(team.id));
            data.addChild("speaker", new JsonValue(speaker));
            data.addChild("text", new JsonValue(text));
            data.addChild("key", new JsonValue(false));
            data.addChild("encrypted", new JsonValue(mode == Mode.encrypted));
            data.addChild("page", new JsonValue(true));
            data.addChild("hold", new JsonValue(hold));
            data.addChild("offset", new JsonValue(offset));
            data.addChild("textKey", new JsonValue(textKey));
            data.addChild("speakerKey", new JsonValue(speakerKey));
            data.addChild("duration", new JsonValue(duration));
            Call.clientPacketReliable(packet, data.toJson(arc.util.serialization.JsonWriter.OutputType.json));
        }
        showPage(team, resolve(speakerKey, speaker), resolve(textKey, text), mode, hold, offset, duration);
    }

    private static String resolve(String key, String fallback){
        return key.isEmpty() ? fallback : Core.bundle.get(key, fallback);
    }

    /** Server/single-player entry point for literal dialogue. Clients cannot broadcast. */
    public static void broadcast(Team team, String speaker, String text){
        broadcast(team, speaker, text, Mode.normal);
    }

    public static void broadcast(Team team, String speaker, String text, Mode mode){
        send(team, speaker, text, false, mode);
    }

    /** Bundle keys are resolved on each recipient, using their own language. */
    public static void broadcastKey(Team team, String speaker, String key){
        broadcastKey(team, speaker, key, Mode.normal);
    }

    public static void broadcastKey(Team team, String speaker, String key, Mode mode){
        send(team, speaker, key, true, mode);
    }

    private static void send(Team team, String speaker, String text, boolean key, Mode mode){
        if(net.client() || team == null || text == null || text.length() > maxText) return;
        if(net.server()){
            JsonValue data = new JsonValue(JsonValue.ValueType.object);
            data.addChild("team", new JsonValue(team.id));
            data.addChild("speaker", new JsonValue(speaker == null ? team.name : speaker));
            data.addChild("text", new JsonValue(text));
            data.addChild("key", new JsonValue(key));
            data.addChild("encrypted", new JsonValue(mode == Mode.encrypted));
            Call.clientPacketReliable(packet, data.toJson(arc.util.serialization.JsonWriter.OutputType.json));
        }
        show(team, speaker, key ? Core.bundle.get(text) : text, mode);
    }

    private static void receive(String packetData){
        if(packetData == null || packetData.length() > 16384) return;
        try{
            JsonValue data = new JsonReader().parse(packetData);
            if(data.getBoolean("clear", false)){ dismissAll(); return; }
            int id = data.getInt("team");
            if(id < 0 || id >= Team.all.length) return;
            String text = data.getString("text");
            if(data.getBoolean("page", false)){
                float hold = data.getFloat("hold", 0f), offset = data.getFloat("offset", 0f), duration = data.getFloat("duration", 0f);
                if(!Float.isFinite(hold) || !Float.isFinite(offset) || !Float.isFinite(duration)
                    || hold < 0f || hold > 3600f || offset < 0f || duration < 0f || duration > 7200f) return;
                showPage(Team.get(id), resolve(data.getString("speakerKey", ""), data.getString("speaker")),
                    resolve(data.getString("textKey", ""), text),
                    data.getBoolean("encrypted", false) ? Mode.encrypted : Mode.normal, hold, offset, duration);
                return;
            }
            show(Team.get(id), data.getString("speaker"), data.getBoolean("key") ? Core.bundle.get(text) : text,
                data.getBoolean("encrypted", false) ? Mode.encrypted : Mode.normal);
        }catch(RuntimeException ignored){ /* Ignore malformed server presentation data. */ }
    }

    public static void dismissAll(){
        if(overlay == null) return;
        overlay.queue.clear();
        overlay.current = null;
        overlay.age = 0f;
        overlay.visible = false;
    }

    /** Explicit editor/replay action replaces the current radio presentation on every recipient. */
    public static void broadcastDismiss(){
        if(net.client()) return;
        if(net.server()) Call.clientPacketReliable(packet, "{\"clear\":true}");
        dismissAll();
    }

    private boolean hudVisible(){
        return state != null && state.isGame() && ui != null
            && (ui.hudfrag.shown || control != null && control.input != null && control.input.logicCutscene);
    }

    private boolean displayed(Element element){
        if(element == null || !element.hasParent()) return false;
        for(Element e = element; e != null; e = e.parent) if(!e.visible) return false;
        return true;
    }

    @Override public void act(float delta){
        super.act(delta);
        if(state == null || !state.isGame()){ dismissAll(); return; }
        visible = hudVisible() && (current != null || !queue.isEmpty());
        if(!visible) return;
        if(current == null){
            current = queue.remove(0);
            age = current.offset;
            textWidth = -1f;
        }
        float pad = Scl.scl(12f), icon = Scl.scl(64f);
        float width = Math.min(Scl.scl(460f), Core.scene.getWidth() - pad * 2f);
        float available = Math.max(Scl.scl(50f), width - icon - pad * 4f);
        if(textWidth != available){
            textWidth = available;
            layout.setText(Fonts.outline, current.sentences.get(current.sentence), Color.white, textWidth, Align.left, true);
            body.setText(layout, 0f, 0f);
            glyphs = 0;
            for(GlyphLayout.GlyphRun run : layout.runs) glyphs += run.glyphs.size;
            bodyHeight = layout.height;
            heading.setText(current.speaker, 0f, 0f, textWidth, Align.left, false);
            if(current.mode == Mode.encrypted){
                statusLayout.setText(Fonts.outline, Core.bundle.get("cryon.radio.encrypted"), Color.white,
                    Math.max(Scl.scl(50f), width - pad * 5f), Align.left, true);
                status.setText(statusLayout, 0f, 0f);
                statusHeight = statusLayout.height;
            }
            float hold = current.sentence == current.sentences.size - 1 ? current.hold : 0f;
            lifetime = current.duration > 0f ? current.duration : pageDuration(current.sentences.get(current.sentence), hold);
            typingRate = Math.max(charsPerSecond,
                glyphs / Math.max(0.25f, lifetime - hold - 0.65f - Math.max(4f, glyphs * 0.055f)));
        }
        float footer = current.mode == Mode.encrypted ? statusHeight + Scl.scl(12f) : 0f;
        setSize(width, Math.max(Scl.scl(118f), bodyHeight + Scl.scl(76f)) + footer);
        float top = Core.scene.getHeight() - pad;
        if(minimap == null || !minimap.hasParent()) minimap = Core.scene.find("minimap");
        if(coordinates == null || !coordinates.hasParent()) coordinates = Core.scene.find("position");
        if(Core.settings.getBool("minimap") && displayed(minimap)){
            top = Math.min(top, minimap.localToStageCoordinates(point.setZero()).y - pad);
            if((Core.settings.getBool("position") || Core.settings.getBool("mouseposition")) && displayed(coordinates)){
                top = Math.min(top, coordinates.localToStageCoordinates(point.setZero()).y - pad);
            }
        }
        setPosition(Core.scene.getWidth() - width - pad, Math.max(pad, top - height));
        // Stay readable while paused, but never consume queued lines during a pause.
        if(!state.isPaused()) age += Math.min(delta, 0.1f);
        if(age > lifetime){
            if(++current.sentence < current.sentences.size){
                age = 0f;
                textWidth = -1f;
            }else{
                current = null;
                visible = false;
            }
        }
    }

    @Override public void draw(){
        if(current == null || !visible) return;
        float alpha = Mathf.clamp(age / 0.2f) * Mathf.clamp((lifetime - age) / 0.4f) * parentAlpha;
        float pad = Scl.scl(12f), iconSize = Scl.scl(64f);
        boolean encrypted = current.mode == Mode.encrypted;
        Color accent = encrypted ? encryptedAccent : current.team.color;
        Draw.color(0.025f, 0.04f, 0.05f, 0.93f * alpha);
        Fill.crect(x, y, width, height);
        Draw.color(accent, alpha * 0.65f);
        Fill.crect(x, y, Scl.scl(2f), height);
        Fill.crect(x, y + height - Scl.scl(1f), width, Scl.scl(1f));

        float ix = x + width - pad - iconSize, iy = y + height - pad - iconSize;
        Draw.color(accent, alpha * 0.1f);
        Fill.crect(ix - pad / 2f, iy - pad / 2f, iconSize + pad, iconSize + pad);
        TextureRegion emblem = Core.atlas.find("team-" + current.team.name, "clear");
        Draw.color(current.team.color, alpha);
        Draw.rect(emblem, ix + iconSize / 2f, iy + iconSize / 2f, iconSize, iconSize);

        heading.setPosition(x + pad * 1.5f, y + height - pad);
        heading.setColors(accent.r, accent.g, accent.b, alpha);
        heading.draw();
        body.setPosition(x + pad * 1.5f, y + height - Scl.scl(43f));
        body.setColors(0.88f, 0.95f, 0.95f, alpha);
        body.draw(0, Math.min(glyphs, Math.max(0, (int)((age - 0.25f) * typingRate))));

        // Subtle horizontal CRT scanlines plus a moving sweep over the faction emblem.
        Draw.color(accent, alpha * 0.055f);
        float line = Scl.scl(1f);
        for(float sy = y + Scl.scl(4f); sy < y + height; sy += Scl.scl(4f)) Fill.crect(x + line, sy, width - line * 2f, line);
        Draw.color(accent, alpha * 0.3f);
        Fill.crect(ix, iy + iconSize * (1f - (age * 0.45f % 1f)), iconSize, line * 2f);
        Draw.color(accent, alpha * (0.45f + Mathf.absin(age, 0.2f, 0.45f)));
        if(encrypted){
            float lx = x + pad * 1.5f, ly = y + pad;
            // Code-drawn padlock; no texture assets required.
            Lines.stroke(line);
            Lines.rect(lx + line * 2f, ly + line * 6f, line * 7f, line * 7f);
            Fill.crect(lx, ly, line * 11f, line * 8f);
            Draw.color(0.025f, 0.04f, 0.05f, alpha);
            Fill.crect(lx + line * 5f, ly + line * 2f, line, line * 3f);
            status.setPosition(lx + Scl.scl(20f), ly + statusHeight);
            status.setColors(accent.r, accent.g, accent.b, alpha);
            status.draw();
        }else{
            Fill.crect(x + pad * 1.5f, y + pad, Scl.scl(22f), line * 2f);
        }
        Draw.reset();
    }
}
