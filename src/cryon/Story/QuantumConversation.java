package cryon.Story;

/** One round per screen; text and choices are bundle keys, independent of rendering. */
public final class QuantumConversation{
    public enum Contact{ crux, malis, pioneer }
    public enum Choice{ listen, refuse, agree }
    public final Contact contact;
    private final boolean repeated;
    private int round;
    public boolean accepted;
    public boolean ended;

    public QuantumConversation(Contact contact){ this(contact, false, false, false); }

    public QuantumConversation(Contact contact, boolean repeated, boolean allied, boolean refused){
        this.contact = contact;
        this.repeated = repeated;
        if(contact == Contact.crux && repeated) ended = true;
        if(contact == Contact.pioneer){
            if(allied) round = 5;
            else if(refused) round = 6;
        }
    }

    /** Snapshot history before the building records this new contact. */
    public static QuantumConversation begin(Contact contact){
        return new QuantumConversation(contact, StoryProgress.contacted(contact),
                StoryProgress.get(StoryProgress.Flag.underground), StoryProgress.pioneerRefused());
    }
    public String npc(){
        if(contact == Contact.crux) return repeated ? "" : "crux.enemy";
        if(contact == Contact.malis) return repeated ? "malis.reconnect" : "malis.negotiate";
        return new String[]{"pioneer.offer", "pioneer.explain", "pioneer.final", "pioneer.reject", "pioneer.accept",
                "pioneer.allied-return", "pioneer.refused-return"}[round];
    }
    public String player(){
        if(contact == Contact.crux) return "";
        if(contact == Contact.malis) return "player.silence";
        if(round == 5) return "player.silence";
        if(round == 6) return "";
        return new String[]{"player.wary", "player.question", "player.consider", "player.refuse", "player.agree"}[round];
    }
    public Choice[] choices(){
        if(contact != Contact.pioneer || ended) return new Choice[0];
        if(round == 0) return new Choice[]{Choice.listen, Choice.refuse};
        if(round == 2 || round == 6) return new Choice[]{Choice.agree, Choice.refuse};
        return new Choice[0];
    }
    public void next(){
        if(ended || choices().length > 0) return;
        if(contact == Contact.pioneer && round == 1) round = 2;
        else ended = true;
    }
    /** Returns true only when the player makes the final agreement. */
    public boolean choose(Choice choice){
        boolean allowed = false;
        for(Choice option : choices()) if(option == choice) allowed = true;
        if(!allowed) return false;
        if(choice == Choice.refuse) round = 3;
        else if(choice == Choice.listen) round = 1;
        else{ round = 4; accepted = true; return true; }
        return false;
    }
    public boolean lastRound(){ return contact != Contact.pioneer || (round >= 3 && round != 6); }
}
