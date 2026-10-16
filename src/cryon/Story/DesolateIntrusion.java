package cryon.Story;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.util.Log;
import cryon.Script.VirusIntrusionScript;
import mindustry.core.GameState;
import mindustry.game.EventType.*;
import mindustry.game.Rules;
import mindustry.game.Saves.SaveSlot;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.io.SaveIO;
import mindustry.mod.Mods;
import mindustry.type.Sector;

import static mindustry.Vars.*;

/** Saves the real campaign, loads a native map, then restores and resolves the real battle. */
public final class DesolateIntrusion {
    private static final String stageKey = "cryon-intrusion-stage";
    private static final String winKey = "cryon-intrusion-win-wave";
    private static final String timerKey = "cryon-intrusion-wave-timer";
    public static final String campaignKey = "cryon-intrusion-campaign";
    private static boolean installed, switching, transitionFailed;
    private static float checkTimer;

    private DesolateIntrusion() {}
    private static Fi snapshot(){ return dataDirectory.child("cryon-intrusion/reality.msav"); }
    private static Fi outcome(){ return dataDirectory.child("cryon-intrusion/outcome.txt"); }

    public static void install(){
        if(installed) return;
        installed = true;
        IntrusionRenderer.install();
        Events.on(ClientLoadEvent.class, event -> {
            if(headless) return;
            ui.settings.addCategory(Core.bundle.get("cryon.intrusion.settings"), table -> {
                table.add(Core.bundle.get("cryon.intrusion.preview-description")).wrap().width(440f).pad(12f);
                table.row();
                table.button(Core.bundle.get("cryon.intrusion.preview"), () -> {
                    ui.settings.hide(null);
                    preview();
                }).width(300f).height(54f).pad(12f);
            });
        });
        Events.on(WorldLoadEvent.class, event -> prepareWorld());
        Events.on(SaveLoadEvent.class, event -> prepareWorld());
        Events.on(PlayEvent.class, event -> prepareWorld());
        Events.on(UnitDestroyEvent.class, event -> {
            if(!eligible() || state.gameOver || !stage().isEmpty()) return;
            if(event.unit.team != state.rules.waveTeam || !event.unit.type.name.equals("cryon-roche") || state.wave < 99) return;
            if(Groups.unit.contains(u -> u != event.unit && u.team == state.rules.waveTeam
                    && u.type.name.equals("cryon-roche") && !u.dead && u.health > 0f)) return;
            state.rules.winWave = 0;
            Rules owner = state.rules;
            Core.app.post(() -> { if(state.rules == owner && eligible() && !state.gameOver) begin(); });
        });
        Events.run(Trigger.update, DesolateIntrusion::update);
    }

    private static void prepareWorld(){
        checkTimer = 0f;
        if(switching || IntrusionMap.active()) return;
        transitionFailed = false;
        if(!eligible()){
            if(state != null && state.rules.tags.containsKey(winKey)) restoreRules();
            return;
        }
        if(state.rules.sector.info.wasCaptured || stage().equals("cleared") || stage().equals("failed")) return;
        if(!state.rules.tags.containsKey(winKey)){
            state.rules.tags.put(winKey, Integer.toString(state.rules.winWave > 0 ? state.rules.winWave : 100));
        }
        state.rules.winWave = 0;
        if(stage().equals("active")) state.rules.waveTimer = false;
    }

    private static void restoreRules(){
        state.rules.winWave = state.rules.tags.getInt(winKey, 100);
        if(state.rules.tags.containsKey(timerKey)) state.rules.waveTimer = Boolean.parseBoolean(state.rules.tags.get(timerKey));
    }

    private static boolean eligible(){
        if(switching || headless || net.active() || state == null || state.isEditor() || !state.isCampaign()) return false;
        Sector sector = state.rules.sector;
        return sector != null && sector.preset != null && sector.preset.name.equals("cryon-desolate-plain");
    }
    private static String stage(){ return state.rules.tags.get(stageKey, ""); }

    private static void update(){
        if(transitionFailed || !eligible() || state.isMenu() || state.gameOver) return;
        if(stage().equals("active")){
            if(outcome().exists() && !outcome().readString().equals("pending")){
                boolean won = outcome().readString().equals("won");
                queue(() -> restoreReality(won));
            }else{
                queue(() -> enter(true));
            }
        }else if(stage().equals("failed")){
            lose();
        }else if(stage().isEmpty() && !state.rules.sector.info.wasCaptured){
            checkTimer += Core.graphics.getDeltaTime();
            if(checkTimer >= 0.5f){
                checkTimer = 0f;
                if(state.wave >= state.rules.tags.getInt(winKey, 100) && state.enemies == 0 && !spawner.isSpawning()) begin();
            }
        }
    }

    private static void queue(Runnable action){
        if(switching) return;
        switching = true;
        Core.app.post(() -> {
            try{ action.run(); }
            catch(Exception error){
                transitionFailed = true;
                if(!state.isMenu()) state.set(GameState.State.paused);
                Log.err("[CryonIntrusion] Map transition failed; reality checkpoint retained.", error);
                if(!headless && ui != null) ui.showException(Core.bundle.get("cryon.intrusion.transition-error"), error);
            }finally{ switching = false; }
        });
    }

    private static void begin(){
        if(switching || !stage().isEmpty()) return;
        state.rules.tags.put(stageKey, "active");
        state.rules.tags.put(timerKey, Boolean.toString(state.rules.waveTimer));
        state.rules.waveTimer = false;
        state.rules.winWave = 0;
        queue(() -> {
            snapshot().parent().mkdirs();
            SaveIO.write(snapshot()); // Abort before resetting the world if this fails.
            outcome().writeString("pending");
            if(control.saves.getCurrent() != null) control.saves.getCurrent().save();
            enter(true);
        });
    }

    private static void enter(boolean campaign){
        if(campaign && !snapshot().exists()){
            // Resuming an older active campaign save which predates the real-map implementation.
            snapshot().parent().mkdirs();
            SaveIO.write(snapshot());
            outcome().writeString("pending");
        }
        control.saves.resetSave(); // The submap must never autosave over the real sector slot.
        logic.reset();
        Mods.LoadedMod self = mods.locateMod("cryon");
        Fi map = self == null ? null : self.root.child("maps/cryon-intrusion.msav");
        if(map != null && map.exists()){
            SaveIO.load(map);
        }else{
            IntrusionMap.generate();
        }
        state.rules.tags.put(campaignKey, Boolean.toString(campaign));
        state.rules.tags.put(IntrusionMap.mapKey, "true");
        state.rules.canGameOver = false;
        player.reset();
        player.team(state.rules.defaultTeam);
        logic.play();
        Log.info("[CryonIntrusion] Native map loaded; locked Java processor controls Gamma combat.");
        Log.info("[CryonIntrusion] Startup: state=@, controller=@, buildings=@, units=@",
                state.getState(), world.build(56, 7), Groups.build.size(), Groups.unit.size());
    }

    /** Called only by the map's Java processor after its end-of-battle effect. */
    public static void finish(boolean won){
        if(switching || !IntrusionMap.active()) return;
        boolean campaign = state.rules.tags.getBool(campaignKey);
        queue(() -> {
            if(campaign){
                outcome().writeString(won ? "won" : "lost");
                restoreReality(won);
            }else{
                control.saves.resetSave();
                logic.reset();
                state.set(GameState.State.menu);
            }
        });
    }

    private static void restoreReality(boolean won){
        if(!snapshot().exists()) throw new IllegalStateException("Reality checkpoint is missing");
        control.saves.resetSave();
        logic.reset();
        SaveIO.load(snapshot());
        restoreRules();
        state.rules.tags.put(stageKey, won ? "cleared" : "failed");
        state.set(GameState.State.playing);
        if(won) Call.sectorCapture(); else lose();
        if(state.rules.sector == null) throw new IllegalStateException("Checkpoint does not contain the campaign sector");
        SaveSlot realSlot = control.saves.new SaveSlot(control.saves.getSectorFile(state.rules.sector));
        realSlot.save();
        // Delete only our temporary checkpoint, and only after the real slot was saved successfully.
        snapshot().delete();
        outcome().delete();
    }

    private static void lose(){
        if(state.gameOver) return;
        state.set(GameState.State.playing);
        state.teams.playerCores().copy().each(core -> core.kill());
        state.gameOver = true;
        Events.fire(new GameOverEvent(state.rules.waveTeam));
    }

    public static void preview(){
        if(headless) return;
        Core.app.post(() -> {
            if(net.active()){
                ui.showInfo(Core.bundle.get("cryon.intrusion.preview-network"));
            }else if(!state.isMenu()){
                ui.showInfo(Core.bundle.get("cryon.intrusion.preview-menu"));
            }else{
                if(ui.consolefrag != null) ui.consolefrag.hide();
                transitionFailed = false;
                queue(() -> enter(false));
            }
        });
    }

}
