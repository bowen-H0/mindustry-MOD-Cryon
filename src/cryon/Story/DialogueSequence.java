package cryon.Story;

import arc.Core;
import arc.struct.Seq;
import arc.util.serialization.JsonReader;
import arc.util.serialization.JsonValue;
import cryon.Features.AllianceTeam;
import cryon.Features.PioneerTeam;
import cryon.UI.RadioDialogue;
import mindustry.Vars;
import mindustry.game.Team;

/** Data-only dialogue. Timings are seconds; no Java code is evaluated from map data. */
public final class DialogueSequence{
    public static final int maxSource = 48000;
    public final String title;
    public final String titleKey;
    public final Seq<Step> steps = new Seq<>();
    public final int startWave;

    public static final class Step{
        public final Team team;
        public final String speaker, text;
        public final RadioDialogue.Mode mode;
        public final float hold, wait;
        public String event;
        public String textKey = "", speakerKey = "";
        public float durationOverride;
        public int count;
        Step(Team team, String speaker, String text, RadioDialogue.Mode mode, float hold, float wait){
            this.team = team; this.speaker = speaker; this.text = text;
            this.mode = mode; this.hold = hold; this.wait = wait;
        }
        public boolean waiting(){ return team == null; }
        public String localizedText(){ return resolve(textKey, text); }
        public String localizedSpeaker(){ return resolve(speakerKey, speaker); }
        public float duration(){
            return waiting() ? wait : durationOverride > 0f ? durationOverride : RadioDialogue.pageDuration(localizedText(), hold);
        }
    }

    public String localizedTitle(){ return resolve(titleKey, title); }

    private static String resolve(String key, String fallback){
        return key.isEmpty() || Core.bundle == null ? fallback : Core.bundle.get(key, fallback);
    }

    private static String key(JsonValue data, String name){
        String value = data.getString(name, "");
        if(value.length() > 160) throw new IllegalArgumentException(name + ": key too long");
        return value;
    }

    public static String source(String id){
        if(!id.matches("[a-z0-9-]+")) throw new IllegalArgumentException("Invalid dialogue id");
        return Vars.mods.locateMod("cryon").root.child("dialogues").child(id + ".hjson").readString("UTF-8");
    }

    public static DialogueSequence parse(String source){ return new DialogueSequence(source); }

    private static float seconds(JsonValue data, String key, float fallback){
        float value = data.getFloat(key, fallback);
        if(!Float.isFinite(value) || value < 0f || value > 3600f)
            throw new IllegalArgumentException(key + ": expected 0..3600 seconds");
        return value;
    }

    private DialogueSequence(String source){
        if(source == null || source.length() > maxSource
            || source.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxSource)
            throw new IllegalArgumentException("Dialogue too large");
        JsonValue root = new JsonReader().parse(source);
        if(!root.isObject()) throw new IllegalArgumentException("Expected dialogue object");
        title = root.getString("title", "监听频道");
        titleKey = key(root, "titleKey");
        if(title.length() > 120) throw new IllegalArgumentException("Title too long");
        startWave = root.getInt("startWave", 0);
        if(startWave < 0) throw new IllegalArgumentException("startWave must be nonnegative");
        float delay = seconds(root, "startDelay", 0f);
        if(delay > 0f) steps.add(new Step(null, "", "", RadioDialogue.Mode.normal, 0f, delay));
        JsonValue list = root.get("steps");
        if(list == null || !list.isArray() || list.size == 0 || list.size > 128)
            throw new IllegalArgumentException("Expected 1..128 steps");
        for(JsonValue entry : list){
            if(!entry.isObject()) throw new IllegalArgumentException("Expected step object");
            if(entry.has("event")){
                String event = entry.getString("event");
                if(!event.equals("sharded-receptor-raid") || entry.has("text") || entry.has("textKey") || entry.has("wait"))
                    throw new IllegalArgumentException("Invalid dialogue event");
                int count = entry.getInt("count", 6);
                if(count < 1 || count > 12) throw new IllegalArgumentException("Raid count must be 1..12");
                Step step = new Step(null, "", "", RadioDialogue.Mode.normal, 0f, 0f);
                step.event = event;
                step.count = count;
                steps.add(step);
                continue;
            }
            if(entry.has("wait")){
                if(entry.has("text") || entry.has("textKey")) throw new IllegalArgumentException("wait and text require separate steps");
                steps.add(new Step(null, "", "", RadioDialogue.Mode.normal, 0f, seconds(entry, "wait", 0f)));
                continue;
            }
            Team team = faction(entry.getString("team"));
            String speaker = entry.getString("speaker", defaultSpeaker(team));
            String textKey = key(entry, "textKey");
            String speakerKey = entry.has("speakerKey") ? key(entry, "speakerKey")
                : entry.has("speaker") ? "" : "cryon.radio.speaker." + entry.getString("team");
            String text = entry.getString("text", textKey);
            if(text.trim().isEmpty() || text.length() > 2048 || speaker.length() > 120)
                throw new IllegalArgumentException("Empty or oversized dialogue text/speaker");
            String mode = entry.getString("mode", "normal");
            if(!mode.equals("normal") && !mode.equals("encrypted")) throw new IllegalArgumentException("Unknown mode: " + mode);
            float hold = seconds(entry, "hold", 0f);
            float duration = seconds(entry, "duration", 0f);
            if(duration > 0f && duration < hold + 0.65f) throw new IllegalArgumentException("duration too short for hold");
            // One localization key is one page, irrespective of the recipient language's punctuation.
            Seq<String> pages = textKey.isEmpty() ? RadioDialogue.splitSentences(text) : Seq.with(text);
            for(int i = 0; i < pages.size; i++){
                Step step = new Step(team, speaker, pages.get(i), RadioDialogue.Mode.valueOf(mode),
                    i == pages.size - 1 ? hold : 0f, 0f);
                step.textKey = textKey;
                step.speakerKey = speakerKey;
                step.durationOverride = duration;
                steps.add(step);
            }
        }
        if(steps.size > 512) throw new IllegalArgumentException("Too many dialogue pages");
    }

    private static String defaultSpeaker(Team team){
        if(team == PioneerTeam.team) return "先驱";
        if(team == AllianceTeam.team) return "联盟";
        if(team == Team.sharded) return "Sharded";
        if(team == Team.crux) return "Crux";
        return "Malis";
    }

    private static Team faction(String id){
        switch(id){
            case "pioneer": return PioneerTeam.team;
            case "alliance": return AllianceTeam.team;
            case "sharded": return Team.sharded;
            case "crux": return Team.crux;
            case "malis": return Team.malis;
            default: throw new IllegalArgumentException("Unknown team: " + id);
        }
    }
}
