import arc.*;
import arc.files.Fi;
import arc.util.Log;
import java.nio.file.Files;
import mindustry.*;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.type.SectorPreset;

/** Existing prerequisites must unlock new sectors after late-built client tech trees. */
public class CryonSectorAutoUnlockCheck{
    static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception{
        Fi data = new Fi(Files.createTempDirectory("cryon-sector-auto-unlock-").toFile());
        new Fi(args.length > 0 ? args[0] : "build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlanuch.jar"));
        System.setProperty("mindustry.data.dir", data.absolutePath());
        // Register first to simulate progress already loaded before ClientLoad tree construction.
        Events.on(ClientLoadEvent.class, event -> {
            SectorPreset lab = Vars.content.sector("cryon-laboratory-088");
            lab.quietUnlock();
            lab.sector.save = Vars.control.saves.new SaveSlot(data.child("laboratory-fixture.msav"));
            lab.sector.info.hasCore = true;
            lab.sector.info.waves = false;
            lab.sector.info.attack = false;
            Vars.content.item("cryon-nano-material").quietUnlock();
            Core.app.post(() -> {
                try{
                    SectorPreset wastes = Vars.content.sector("cryon-eroded-wastes");
                    var nano = Vars.content.item("cryon-nano-material");
                    check(wastes.unlocked(), "existing prerequisites did not automatically unlock new sector on startup");
                    wastes.clearUnlock();
                    lab.sector.info.waves = true;
                    Vars.control.checkAutoUnlocks();
                    check(wastes.locked(), "sector unlocked before laboratory capture");
                    lab.sector.info.waves = false;
                    nano.clearUnlock();
                    Vars.control.checkAutoUnlocks();
                    check(wastes.locked(), "sector unlocked without nano-material");
                    nano.unlock();
                    check(wastes.unlocked(), "nano-material UnlockEvent did not automatically unlock sector");
                    Log.info("SECTOR_AUTO_UNLOCK PASS: existing progress on startup, both prerequisites enforced, material event unlocks without a click");
                    Core.app.exit();
                }catch(Throwable error){ Log.err(error); System.exit(1); }
            });
        });
        mindustry.desktop.DesktopLauncher.main(args.length > 0 ? new String[0] : args);
    }
}
