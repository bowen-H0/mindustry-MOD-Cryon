package cryon.Story;

import arc.Core;
import arc.Events;
import mindustry.Vars;
import mindustry.content.TechTree;
import mindustry.game.EventType.*;
import mindustry.game.Objectives.Objective;
import mindustry.type.Planet;

/** Profile-wide story switches. Loading another sector never resets global progress. */
public final class StoryProgress{
    public enum Flag{
        underground("underground"), actTwo("act-two"), aravisMission("aravis-mission");
        public final String key;
        Flag(String name){ key = "cryon-story-" + name; }
    }
    private static boolean installed;
    private StoryProgress(){}

    public static boolean get(Flag flag){
        return value(flag.key);
    }

    private static boolean value(String key){
        if(Vars.net != null && Vars.net.client() && Vars.state != null){
            return Vars.state.rules.tags.getBool(key);
        }
        return Core.settings != null && Core.settings.getBool(key, false);
    }

    private static String contactKey(QuantumConversation.Contact contact){
        return "cryon-quantum-contacted-" + contact.name();
    }

    public static boolean contacted(QuantumConversation.Contact contact){ return value(contactKey(contact)); }
    public static boolean pioneerRefused(){ return value("cryon-quantum-pioneer-refused"); }

    private static String chapterSector(String chapter){
        if(chapter.equals("desolate-captured")) return "desolate-plain";
        if(chapter.equals("aravis-captured")) return "terminus";
        return null;
    }

    public static boolean chapterAvailable(String chapter){
        String sector = chapterSector(chapter);
        if(sector == null || !get(Flag.underground)) return false;
        if(value("cryon-story-captured-" + sector)) return true;
        if(Vars.net != null && Vars.net.client()) return false;
        var preset = Vars.content.sector("cryon-" + sector);
        return preset != null && preset.sector.isCaptured();
    }

    public static String pendingTerminal(){
        for(String chapter : new String[]{"desolate-captured", "aravis-captured"}){
            if(chapterAvailable(chapter) && !value("cryon-dialogue-read-" + chapter)) return chapter;
        }
        return null;
    }

    public static void completeTerminal(String chapter){
        if(!chapterAvailable(chapter)) return;
        writeValue("cryon-dialogue-read-" + chapter, true);
        set(chapter.equals("aravis-captured") ? Flag.actTwo : Flag.aravisMission, true);
    }

    public static void recordContact(QuantumConversation.Contact contact){
        if(!contacted(contact)){
            writeValue(contactKey(contact), true);
            save();
        }
    }

    public static void recordPioneerRefusal(){
        if(get(Flag.underground)) return;
        writeValue("cryon-quantum-pioneer-refused", true);
        save();
    }

    private static void writeValue(String key, boolean value){
        if(Vars.net == null || !Vars.net.client()) Core.settings.put(key, value);
        if(Vars.state != null) Vars.state.rules.tags.put(key, Boolean.toString(value));
    }

    private static void save(){
        if(Vars.net == null || !Vars.net.client()) Core.settings.forceSave();
        if(Vars.net != null && Vars.net.server()) mindustry.gen.Call.setRules(Vars.state.rules);
    }

    public static void set(Flag flag, boolean value){
        writeValue(flag.key, value);
        if(flag == Flag.underground && value) writeValue("cryon-quantum-pioneer-refused", false);
        applyPlanetGates();
        if(flag == Flag.underground && value) cryon.Features.CryonCampaignLayers.unlockRoute();
        save();
    }

    public static void publish(){
        if(Vars.state != null && (Vars.net == null || !Vars.net.client())){
            for(Flag flag : Flag.values()) Vars.state.rules.tags.put(flag.key, Boolean.toString(get(flag)));
            for(QuantumConversation.Contact contact : QuantumConversation.Contact.values()){
                Vars.state.rules.tags.put(contactKey(contact), Boolean.toString(contacted(contact)));
            }
            Vars.state.rules.tags.put("cryon-quantum-pioneer-refused", Boolean.toString(pioneerRefused()));
            for(String chapter : new String[]{"desolate-captured", "aravis-captured"}){
                String sector = chapterSector(chapter);
                Vars.state.rules.tags.put("cryon-story-captured-" + sector, Boolean.toString(chapterAvailable(chapter)));
                Vars.state.rules.tags.put("cryon-dialogue-read-" + chapter,
                    Boolean.toString(value("cryon-dialogue-read-" + chapter)));
            }
        }
        applyPlanetGates();
    }

    public static void install(){
        if(installed) return;
        installed = true;
        publish();
        Events.on(WorldLoadEvent.class, event -> publish());
        Events.on(ResetEvent.class, event -> publish());
        Events.on(ClientLoadEvent.class, event -> publish());
        Events.on(SectorCaptureEvent.class, event -> {
            if(Vars.net != null && Vars.net.client() || event.sector == null || event.sector.preset == null) return;
            for(String sector : new String[]{"desolate-plain", "terminus"}){
                if(event.sector.preset.name.equals("cryon-" + sector)){
                    writeValue("cryon-story-captured-" + sector, true);
                    save();
                }
            }
        });
        Events.run(Trigger.update, StoryProgress::applyPlanetGates);
    }

    public static void applyPlanetGates(){
        boolean actTwo = get(Flag.actTwo), underground = get(Flag.underground);
        Planet star = Vars.content.planet("cryon-kyanos"), porphyra = Vars.content.planet("cryon-porphyra");
        Planet depths = Vars.content.planet("cryon-cryon-depths");
        if(star != null) star.visible = actTwo;
        if(porphyra != null){
            porphyra.visible = porphyra.accessible = porphyra.alwaysUnlocked = actTwo;
        }
        if(depths != null){
            depths.accessible = depths.alwaysUnlocked = underground;
            if(!underground) depths.visible = false;
        }
    }

    public static boolean undergroundTechnology(String name){
        return name.startsWith("cryon-depths-") || name.equals("cryon-minimus") || name.equals("cryon-danae")
                || name.equals("cryon-pammelas") || name.equals("cryon-schmidti") || name.equals("cryon-sloani")
                || name.equals("cryon-abyssal-mine") || name.equals("cryon-powered-pump")
                || name.equals("cryon-underice-current-generator") || name.equals("cryon-abyssal-sediment")
                || name.equals("cryon-sediment-separator");
    }

    public static void gateResearch(){
        for(var node : TechTree.all){
            if(undergroundTechnology(node.content.name)
                    && !node.objectives.contains(objective -> objective instanceof UndergroundObjective)){
                node.objectives.add(new UndergroundObjective());
            }
        }
    }

    public static class UndergroundObjective implements Objective{
        @Override public boolean complete(){ return get(Flag.underground); }
        @Override public String display(){ return Core.bundle.get("cryon.story.requires-underground"); }
    }
}
