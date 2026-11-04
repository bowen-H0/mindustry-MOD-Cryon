package cryon.Story;

import arc.Core;
import arc.graphics.g2d.TextureRegion;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.Time;
import cryon.Features.PioneerTeam;
import cryon.Type.QuantumCommunicationComputer.QuantumComputerBuild;
import cryon.Type.QuantumCommunicationComputer;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

/** RPG-style two-sided rounds with no transcript; clicking first completes the typing. */
public class QuantumCommunicationDialog extends BaseDialog{
    private final QuantumComputerBuild computer;
    private QuantumConversation conversation;
    private String npcText = "", playerText = "";
    private Label npcLabel, playerLabel;
    private float typed;

    public QuantumCommunicationDialog(QuantumComputerBuild computer){
        super("@cryon.quantum.title");
        this.computer = computer;
        addCloseButton();
        shown(this::contacts);
        onResize(() -> { if(conversation == null) contacts(); else round(); });
        update(() -> {
            if(!computer.ready() || Vars.player == null || !computer.interactable(Vars.player.team())){
                hide();
                return;
            }
            if(conversation != null){
                typed = Math.min(npcText.length() + playerText.length(), typed + Time.delta * 0.65f);
                if(npcLabel != null) npcLabel.setText(prefix(npcText, (int)typed));
                if(playerLabel != null) playerLabel.setText(prefix(playerText, (int)typed - npcText.length()));
            }
        });
    }

    private static String prefix(String text, int count){
        int end = Math.max(0, Math.min(count, text.length()));
        if(end > 0 && end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end);
    }
    private float width(){ return Math.min(1080f, Core.graphics.getWidth() / Scl.scl(1f) - 64f); }
    private Team team(){
        return conversation.contact == QuantumConversation.Contact.crux ? Team.crux
                : conversation.contact == QuantumConversation.Contact.malis ? Team.malis : PioneerTeam.team;
    }
    private TextureRegion emblem(Team team){
        return Core.atlas.find("team-" + team.name, Icon.players.getRegion());
    }
    private String text(String key){ return key.isEmpty() ? "" : Core.bundle.get("cryon.dialogue." + key); }
    private boolean complete(){ return typed >= npcText.length() + playerText.length(); }

    private void contacts(){
        conversation = null;
        cont.clear();
        cont.add("@cryon.quantum.contacts").fontScale(1.4f).pad(16f).row();
        for(QuantumConversation.Contact contact : QuantumConversation.Contact.values()){
            Team faction = contact == QuantumConversation.Contact.crux ? Team.crux
                    : contact == QuantumConversation.Contact.malis ? Team.malis : PioneerTeam.team;
            cont.button(button -> {
                button.image(emblem(faction)).size(64f).color(faction.color).pad(12f);
                button.add(faction.localized()).fontScale(1.6f).growX().left();
            }, () -> {
                if(!computer.ready()) return;
                conversation = QuantumConversation.begin(contact);
                computer.configure(contact.ordinal());
                if(conversation.ended){
                    contacts();
                    Vars.ui.showInfoFade(Core.bundle.get("cryon.quantum.disconnected"));
                }else round();
            }).width(Math.min(520f, width())).height(96f).pad(6f).get().name = "cryon-contact-" + contact.name();
            cont.row();
        }
    }

    private void round(){
        typed = 0f;
        npcText = text(conversation.npc());
        playerText = text(conversation.player());
        cont.clear();
        cont.add(team().localized()).color(team().color).fontScale(1.5f).pad(10f).row();
        float width = width();
        cont.pane(screen -> {
            screen.top().defaults().pad(10f);
            screen.table(Styles.black6, speech -> {
                speech.image(emblem(team())).size(80f).color(team().color).top().pad(12f);
                speech.table(words -> {
                    words.add(team().localized()).color(team().color).left().row();
                    npcLabel = words.add("").fontScale(1.6f).wrap().width(Math.max(140f, width - 220f)).minHeight(110f).top().left().get();
                    npcLabel.setAlignment(Align.topLeft);
                }).growX().top().pad(12f);
            }).growX().top().left().row();
            if(!playerText.isEmpty()){
                screen.add().height(28f).row();
                screen.table(Styles.black6, speech -> {
                    speech.table(words -> {
                        words.add(Team.sharded.localized()).color(Team.sharded.color).right().row();
                        playerLabel = words.add("").fontScale(1.6f).wrap().width(Math.max(140f, width - 220f)).minHeight(110f).right().top().get();
                        playerLabel.setAlignment(Align.topRight);
                    }).growX().top().pad(12f);
                    speech.image(emblem(Team.sharded)).size(80f).color(Team.sharded.color).top().pad(12f);
                }).growX().right().row();
            }else playerLabel = null;
        }).width(width).height(Math.min(440f, Math.max(180f, Core.graphics.getHeight() / Scl.scl(1f) - 300f))).row();
        cont.label(() -> Core.bundle.get(conversation.lastRound() && complete()
                ? "cryon.quantum.disconnected" : "cryon.quantum.connected")).color(Pal.gray).pad(6f).row();
        cont.table(actions -> {
            actions.defaults().height(60f).pad(6f);
            if(conversation.choices().length > 0){
                for(QuantumConversation.Choice choice : conversation.choices()){
                    actions.button("@cryon.dialogue.choice." + choice.name(), () -> {
                        if(!complete()) return;
                        if(!computer.ready()) return;
                        if(conversation.choose(choice)) computer.configure(true);
                        else if(choice == QuantumConversation.Choice.refuse) computer.configure(QuantumCommunicationComputer.refusePioneer);
                        round();
                    }).width((width - 24f) / 2f).disabled(button -> !complete()).get().name = "cryon-choice-" + choice.name();
                }
                actions.row();
            }
            actions.button("@cryon.dialogue.continue", () -> {
                if(!complete()){
                    typed = npcText.length() + playerText.length();
                    return;
                }
                if(conversation.choices().length > 0) return;
                conversation.next();
                if(conversation.ended) contacts();
                else round();
            }).width(Math.min(320f, width - 12f)).colspan(Math.max(1, conversation.choices().length))
                    .disabled(button -> complete() && conversation.choices().length > 0).get().name = "cryon-dialogue-continue";
        }).row();
    }
}
