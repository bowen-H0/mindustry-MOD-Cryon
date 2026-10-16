package cryon.Story;

import arc.Core;
import arc.Events;
import arc.Settings;
import arc.util.I18NBundle;
import mindustry.Vars;
import mindustry.core.ContentLoader;
import mindustry.core.GameState;
import mindustry.game.EventType.*;
import mindustry.game.Rules;
import mindustry.net.Net;
import mindustry.type.Planet;
import mindustry.type.SectorPreset;

/** Uses engine classes but no window, native graphics, player save, or game assets. */
public final class DesolateIntrusionGateTest {
    private static void check(boolean condition, String message){
        if(!condition) throw new AssertionError(message);
    }

    public static void main(String[] args){
        Core.app = new arc.mock.MockApplication();
        Core.settings = new Settings();
        Core.bundle = I18NBundle.createEmptyBundle();
        Vars.content = new ContentLoader();
        Vars.net = new Net(null);
        Vars.state = new GameState();
        Vars.headless = false;
        Planet planet = new Planet("test-planet", null, 1f, 2);
        SectorPreset preset = new SectorPreset("cryon-desolate-plain", planet, 0);
        Rules rules = Vars.state.rules;
        rules.sector = preset.sector;
        rules.winWave = 100;
        rules.canGameOver = true;
        DesolateIntrusion.install();
        DesolateIntrusion.install();
        Events.fire(new WorldLoadEvent());
        check(rules.winWave == 0, "premature wave victory not blocked");
        check(rules.canGameOver, "ordinary core defeat disabled");
        check(rules.tags.getInt("cryon-intrusion-win-wave") == 100, "original win wave lost");
        Events.fire(new SaveLoadEvent(false));
        check(rules.tags.getInt("cryon-intrusion-win-wave") == 100, "duplicate load overwrites original win wave");
        rules.tags.put("cryon-intrusion-stage", "active");
        rules.tags.put("cryon-intrusion-wave-timer", "true");
        Events.fire(new WorldLoadEvent());
        check(!rules.waveTimer, "pending challenge resumed waves");
        Vars.headless = true;
        Events.fire(new WorldLoadEvent());
        check(rules.winWave == 100 && rules.waveTimer, "server fallback left campaign locked");
        Vars.headless = false;
        Vars.net = new Net(null){ @Override public boolean active(){ return true; } };
        rules.winWave = 0;
        rules.waveTimer = false;
        Events.fire(new WorldLoadEvent());
        check(rules.winWave == 100 && rules.waveTimer, "multiplayer fallback left campaign locked");
        Vars.net = new Net(null);
        rules.tags.put("cryon-intrusion-stage", "cleared");
        Events.fire(new WorldLoadEvent());
        check(rules.winWave == 100, "completed challenge retriggered");
        rules.tags.remove("cryon-intrusion-stage");
        preset.sector.info.wasCaptured = true;
        Events.fire(new WorldLoadEvent());
        check(rules.winWave == 100, "already captured sector gated");
        rules.sector = null;
        Events.fire(new WorldLoadEvent());
        check(rules.winWave == 100, "unrelated world changed");
        final java.util.List<Runnable> posted = new java.util.ArrayList<>();
        Core.app = (arc.Application)java.lang.reflect.Proxy.newProxyInstance(
                arc.Application.class.getClassLoader(), new Class<?>[]{arc.Application.class},
                (proxy, method, values) -> {
                    if(method.getName().equals("post")){ posted.add((Runnable)values[0]); return null; }
                    throw new UnsupportedOperationException(method.getName());
                });
        Vars.state.set(GameState.State.menu);
        DesolateIntrusion.preview();
        check(posted.size() == 1, "main-menu preview silently ignored");
        Vars.headless = true;
        DesolateIntrusion.preview();
        check(posted.size() == 1, "headless preview queued graphical work");
        System.out.println("PASS: victory gate, normal core loss, idempotent setup, pending reload, server/network fallback, completed/captured sectors, main-menu preview scheduling");
    }
}
