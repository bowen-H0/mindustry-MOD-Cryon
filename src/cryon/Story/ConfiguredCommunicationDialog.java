package cryon.Story;

import arc.Core;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Scl;
import arc.util.Align;
import arc.util.Time;
import cryon.Type.QuantumCommunicationComputer.QuantumComputerBuild;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.Icon;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

/** Terminal presentation of the same HJSON pages used by placeable radio processors. */
public final class ConfiguredCommunicationDialog extends BaseDialog{
    private final QuantumComputerBuild computer;
    private final DialogueSequence sequence;
    private final String chapter;
    private int cursor;
    private float typed, elapsed;
    private Label words;

    public ConfiguredCommunicationDialog(QuantumComputerBuild computer, String chapter){
        super(DialogueSequence.parse(DialogueSequence.source(chapter)).localizedTitle());
        this.computer = computer;
        this.chapter = chapter;
        sequence = DialogueSequence.parse(DialogueSequence.source(chapter));
        addCloseButton();
        onResize(this::page);
        shown(this::page);
        update(() -> {
            if(!computer.ready() || Vars.player == null || !computer.interactable(Vars.player.team())){ hide(); return; }
            DialogueSequence.Step step = sequence.steps.get(cursor);
            String text = step.localizedText();
            elapsed += Time.delta / 60f;
            if(step.waiting()){
                if(elapsed >= step.wait) advance();
            }else{
                typed = Math.min(text.length(), typed + Time.delta * 0.65f);
                int end = (int)typed;
                if(end > 0 && end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) end--;
                words.setText(text.substring(0, end));
            }
        });
    }

    private void advance(){
        if(cursor == sequence.steps.size - 1){
            computer.configure(chapter.equals("aravis-captured") ? 5 : 4);
            hide();
        }else{
            cursor++;
            typed = elapsed = 0f;
            page();
        }
    }

    private void page(){
        DialogueSequence.Step step = sequence.steps.get(cursor);
        String text = step.localizedText();
        cont.clear();
        float width = Math.max(200f, Math.min(920f, Core.graphics.getWidth() / Scl.scl(1f) - 80f));
        boolean player = step.team == Team.sharded;
        cont.add(step.waiting() ? Core.bundle.get("cryon.radio.silence") : step.localizedSpeaker()).fontScale(1.5f)
            .color(step.waiting() ? Team.derelict.color : step.team.color).pad(14f).row();
        if(!step.waiting()){
            cont.pane(p -> p.table(Styles.black6, speech -> {
                if(!player) speech.image(Core.atlas.find("team-" + step.team.name, Icon.players.getRegion()))
                    .size(72f).color(step.team.color).pad(12f).top();
                words = speech.add("").fontScale(1.5f).wrap().width(Math.max(100f, width - 132f))
                    .minHeight(160f).pad(14f).get();
                words.setAlignment(player ? Align.topRight : Align.topLeft);
                if(player) speech.image(Core.atlas.find("team-" + step.team.name, Icon.players.getRegion()))
                    .size(72f).color(step.team.color).pad(12f).top();
            }).growX()).width(width).maxHeight(Math.max(150f,
                Core.graphics.getHeight() / Scl.scl(1f) - 300f)).row();
            cont.button("@cryon.dialogue.continue", () -> {
                if(typed < text.length()) typed = text.length();
                else if(elapsed >= step.hold) advance();
            }).size(Math.min(width, 320f), 60f).pad(12f)
                .disabled(button -> typed >= text.length() && elapsed < step.hold).get().name = "cryon-configured-continue";
        }
    }
}
