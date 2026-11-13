package cryon.Script;

import arc.Core;
import arc.scene.ui.TextArea;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import cryon.Story.DialogueSequence;
import cryon.UI.RadioDialogue;
import mindustry.Vars;
import mindustry.ui.dialogs.BaseDialog;

/** Placeable map dialogue; the complete source and playback cursor travel with the map save. */
public final class RadioDialogueScript extends MapScript{
    public static final String configPrefix = "radio-dialogue-config:";
    private final String preset;
    private String source;
    private DialogueSequence sequence;
    private int cursor;
    private float remaining = -1f;
    private boolean sent;
    private int pendingRaidCount;

    public RadioDialogueScript(){ this("underice-outpost"); }
    public RadioDialogueScript(String preset){ this.preset = preset; }
    @Override protected void init(){ configure(DialogueSequence.source(preset)); }

    public String source(){ return source; }
    public int cursor(){ return cursor; }
    public float remaining(){ return remaining; }
    public boolean finished(){ return sequence != null && cursor >= sequence.steps.size; }

    public void configure(String text){
        DialogueSequence parsed = DialogueSequence.parse(text);
        source = text;
        sequence = parsed;
        cursor = 0;
        remaining = -1f;
        sent = false;
        pendingRaidCount = 0;
    }

    @Override public void update(){
        if(Vars.net.client() || Vars.state.isEditor() || !Vars.state.isGame() || Vars.state.isPaused()
            || sequence == null || Vars.state.wave < sequence.startWave) return;
        if(pendingRaidCount > 0) triggerRaid(pendingRaidCount);
        if(finished()) return;
        DialogueSequence.Step step = sequence.steps.get(cursor);
        if(remaining < 0f) remaining = step.duration();
        if(!sent){
            if(step.event != null) triggerRaid(step.count);
            if(!step.waiting()) RadioDialogue.broadcastPage(step.team, step.speaker, step.text,
                step.mode, step.hold, Math.max(0f, step.duration() - remaining),
                step.textKey, step.speakerKey, step.duration());
            sent = true;
        }
        remaining = Math.max(0f, remaining - Time.delta / 60f);
        if(remaining <= 0f){ cursor++; remaining = -1f; sent = false; }
    }

    @Override public void write(Writes write){
        write.str(source);
        write.i(cursor);
        write.f(remaining);
    }
    @Override public void read(Reads read){
        configure(read.str());
        cursor = Math.max(0, Math.min(read.i(), sequence.steps.size));
        remaining = read.f();
        if(!Float.isFinite(remaining)) remaining = -1f;
        if(!finished()) remaining = Math.max(-1f, Math.min(remaining, sequence.steps.get(cursor).duration()));
        upgradeLegacyPreset();
        // Also recover a missed event when loading a save made after its dialogue page.
        for(int i = 0; i < cursor; i++){
            DialogueSequence.Step step = sequence.steps.get(i);
            if(step.event != null) pendingRaidCount = step.count;
        }
        sent = false;
    }

    private void triggerRaid(int count){
        cryon.Story.ShardedReceptorRaid.start(build, count);
        pendingRaidCount = Vars.state.rules.tags.getBool(cryon.Story.ShardedReceptorRaid.tag) ? 0 : count;
    }

    /** Upgrade only exact built-in pages, including old literal text embedded in maps. */
    private void upgradeLegacyPreset(){
        String updatedSource = DialogueSequence.source(preset);
        if(source.equals(updatedSource)) return;
        DialogueSequence updated = DialogueSequence.parse(updatedSource);
        if(!sequence.title.equals(updated.title) || sequence.startWave != updated.startWave
            || !sequence.titleKey.isEmpty() && !sequence.titleKey.equals(updated.titleKey)) return;
        int[] mapping = new int[sequence.steps.size + 1];
        int oldIndex = 0, inserted = 0;
        for(int i = 0; i < updated.steps.size; i++){
            DialogueSequence.Step next = updated.steps.get(i);
            if(next.event != null && (oldIndex == sequence.steps.size || sequence.steps.get(oldIndex).event == null)){
                if(!preset.equals("porphyra-landing") || ++inserted > 1) return;
                continue;
            }
            if(oldIndex >= sequence.steps.size) return;
            DialogueSequence.Step old = sequence.steps.get(oldIndex);
            if(old.team != next.team || !old.speaker.equals(next.speaker) || !old.text.equals(next.text)
                || old.mode != next.mode || old.hold != next.hold || old.wait != next.wait
                || !java.util.Objects.equals(old.event, next.event) || old.count != next.count
                || !old.textKey.isEmpty() && !old.textKey.equals(next.textKey)
                || !old.speakerKey.isEmpty() && !old.speakerKey.equals(next.speakerKey)
                || old.durationOverride > 0f && old.durationOverride != next.durationOverride) return;
            mapping[oldIndex++] = i;
        }
        if(oldIndex != sequence.steps.size) return;
        mapping[oldIndex] = updated.steps.size;
        source = updatedSource;
        sequence = updated;
        cursor = mapping[cursor];
    }

    @Override public void buildUI(Table table){
        table.row();
        table.add(sequence == null ? Core.bundle.get("cryon.radio.channel") : sequence.localizedTitle()).wrap().width(300f).row();
        table.label(() -> finished() ? Core.bundle.get("cryon.radio.finished") : Core.bundle.format("cryon.radio.progress", cursor, sequence.steps.size)).row();
        table.button("@cryon.radio.edit", () -> {
            BaseDialog dialog = new BaseDialog("@cryon.radio.editor-title");
            TextArea editor = new TextArea(source);
            float width = Math.max(200f, Math.min(700f, arc.Core.graphics.getWidth() / arc.scene.ui.layout.Scl.scl(1f) - 80f));
            float height = Math.max(160f, Math.min(430f, arc.Core.graphics.getHeight() / arc.scene.ui.layout.Scl.scl(1f) - 260f));
            dialog.cont.pane(t -> t.add(editor).width(width).height(height)).grow().row();
            dialog.cont.add("@cryon.radio.hint")
                .wrap().width(width).pad(8f);
            dialog.addCloseButton();
            dialog.buttons.button("@cryon.radio.save", () -> {
                try{
                    DialogueSequence.parse(editor.getText());
                    build.configure(configPrefix + editor.getText());
                    dialog.hide();
                }catch(RuntimeException error){ Vars.ui.showInfo(Core.bundle.format("cryon.radio.error", error.getMessage())); }
            }).size(240f, 64f);
            dialog.show();
        }).width(300f).row();
        table.button("@cryon.radio.replay", () -> build.configure(configPrefix + source)).width(300f);
    }

    public static void register(){
        ScriptRegistry.register("radio-dialogue", "cryon.radio.custom", RadioDialogueScript::new);
        ScriptRegistry.register("radio-cryon-sector-1", "cryon.radio.preset.cryon-sector-1", () -> new RadioDialogueScript("cryon-sector-1"));
        ScriptRegistry.register("radio-neutron-flux-zone", "cryon.radio.preset.neutron-flux-zone", () -> new RadioDialogueScript("neutron-flux-zone"));
        ScriptRegistry.register("radio-exclusion-zone", "cryon.radio.preset.exclusion-zone", () -> new RadioDialogueScript("exclusion-zone"));
        ScriptRegistry.register("radio-baryon-bastion", "cryon.radio.preset.baryon-bastion", () -> new RadioDialogueScript("baryon-bastion"));
        ScriptRegistry.register("radio-twilight-icefield", "cryon.radio.preset.twilight-icefield", () -> new RadioDialogueScript("twilight-icefield"));
        ScriptRegistry.register("radio-underice", "cryon.radio.underice", () -> new RadioDialogueScript("underice-outpost"));
        ScriptRegistry.register("radio-porphyra", "cryon.radio.porphyra", () -> new RadioDialogueScript("porphyra-landing"));
        ScriptRegistry.register("radio-desolate", "cryon.radio.desolate-preview", () -> new RadioDialogueScript("desolate-captured"));
        ScriptRegistry.register("radio-aravis", "cryon.radio.aravis-preview", () -> new RadioDialogueScript("aravis-captured"));
    }
}
