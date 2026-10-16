import arc.Core;
import arc.Settings;
import arc.assets.AssetManager;
import arc.files.Fi;
import arc.mock.*;
import arc.util.I18NBundle;
import arc.util.Time;
import cryon.CryonJavaMod;
import cryon.Script.ScriptRegistry;
import cryon.Script.VirusIntrusionScript;
import cryon.Story.IntrusionContent;
import cryon.Story.IntrusionMap;
import cryon.Type.JavaProcessor;
import cryon.Type.JavaProcessor.JavaProcessorBuild;
import mindustry.Vars;
import mindustry.core.Logic;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.io.SaveIO;
import mindustry.mod.Mods;
import mindustry.net.Net;

/** Native map exporter and integration check, isolated from all player data. */
public class IntrusionMapCheck {
    static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        try{
            run(args);
            System.exit(0);
        }catch(Throwable error){ error.printStackTrace(); System.exit(1); }
    }
    static void run(String[] args) throws Exception {
        Core.app = new MockApplication();
        Core.files = new MockFiles();
        Core.graphics = new MockGraphics();
        Core.audio = new MockAudio();
        Core.settings = new Settings();
        Core.settings.setDataDirectory(new Fi("/tmp/cryon-native-map-check"));
        Core.bundle = I18NBundle.createEmptyBundle();
        Core.assets = null;
        Vars.headless = true;
        Vars.loadLocales = false;
        Vars.net = new Net(null);
        Vars.platform = new mindustry.core.Platform() {};
        Vars.init();
        Core.assets = new AssetManager();
        Vars.content.createBaseContent();
        Mods.ModMeta meta = new Mods.ModMeta();
        meta.name = "cryon";
        meta.version = "test";
        Mods.LoadedMod mod = new Mods.LoadedMod(new Fi("."), new Fi("."), null, IntrusionMapCheck.class.getClassLoader(), meta);
        Vars.content.setCurrentMod(mod);
        IntrusionContent.load();
        CryonJavaMod.javaProcessor = new JavaProcessor("java-processor");
        CryonJavaMod.javaProcessor.privileged = true;
        Vars.content.setCurrentMod(null);
        ScriptRegistry.register("virus-intrusion", "Virus", VirusIntrusionScript::new);
        Vars.content.init();
        Vars.logic = new Logic();
        Vars.player = Player.create();
        Vars.player.team(Team.sharded);
        IntrusionMap.generate();
        check(Vars.world.width() == 112 && Vars.world.height() == 144, "wrong map dimensions");
        JavaProcessorBuild processor = (JavaProcessorBuild)Vars.world.build(56, 7);
        check(processor.script instanceof VirusIntrusionScript && processor.scriptLocked(), "missing locked script");
        processor.setScript("");
        check(processor.script instanceof VirusIntrusionScript, "script lock bypassed");
        check(Vars.state.rules.isBanned(mindustry.content.Blocks.duo), "building whitelist not locked");
        check(!Vars.state.rules.canGameOver && !Vars.state.rules.waves, "ordinary map win/loss not gated");
        Fi exported = new Fi(args.length > 0 ? args[0] : "/tmp/cryon-native-map-check/intrusion.msav");
        IntrusionMap.export(exported);
        check(SaveIO.isSaveValid(exported), "invalid .msav map");
        SaveIO.load(exported);
        check(IntrusionMap.active(), "exported map lost its rules tag");
        processor = (JavaProcessorBuild)Vars.world.build(56, 7);
        check(processor.scriptLocked(), "exported map lost locked processor");
        Time.delta = 1f;
        processor.updateTile();
        Unit gamma = Vars.player.unit();
        check(gamma.type == mindustry.content.UnitTypes.gamma && gamma.isAdded(), "player not controlling native Gamma");
        Unit virus = Groups.unit.find(u -> u.type == IntrusionContent.virus);
        check(virus != null && virus.team == Team.crux, "native virus target not spawned");
        for(int i = 0; i < VirusIntrusionScript.introTicks + 5; i++) processor.updateTile();
        check(Groups.bullet.size() > 0, "processor did not emit native bullets");
        check(Groups.bullet.first().damage > 1f, "packet damage incorrectly overwritten");
        check(gamma.plans.isEmpty(), "Gamma build queue not cleared");
        Fi save = new Fi("/tmp/cryon-native-map-check/roundtrip.msav");
        SaveIO.write(save);
        SaveIO.load(save);
        processor = (JavaProcessorBuild)Vars.world.build(56, 7);
        check(processor.scriptLocked(), "processor lost script after save/load");
        check(Groups.unit.contains(u -> u.type == mindustry.content.UnitTypes.gamma), "Gamma lost on roundtrip");
        processor.updateTile();
        check(!((VirusIntrusionScript)processor.script).finished(), "roundtrip falsely resolved challenge");
        gamma = Vars.player.unit();
        virus = Groups.unit.find(u -> u.type == IntrusionContent.virus);
        gamma.unapply(mindustry.content.StatusEffects.disarmed);
        gamma.unapply(mindustry.content.StatusEffects.invincible);
        gamma.rotation = 90f;
        gamma.aim(virus);
        gamma.controlWeapons(true, true);
        int before = Groups.bullet.size();
        for(int i = 0; i < 30; i++){
            for(mindustry.entities.units.WeaponMount mount : gamma.mounts){
                mount.weapon.update(gamma, mount);
            }
            Time.update();
        }
        check(Groups.bullet.size() > before, "native Gamma weapons did not fire");
        mindustry.gen.Bullet friendly = Groups.bullet.find(b -> b.team == Team.sharded);
        check(friendly != null, "no real friendly projectile");
        virus.unapply(mindustry.content.StatusEffects.invincible);
        float healthBefore = virus.health;
        friendly.type.hitEntity(friendly, virus, virus.health);
        check(virus.health < healthBefore, "Gamma projectile cannot damage virus");
        virus.health = 0f;
        processor.updateTile();
        check(((VirusIntrusionScript)processor.script).finished() && ((VirusIntrusionScript)processor.script).won(), "virus destruction did not win");
        SaveIO.load(save);
        processor = (JavaProcessorBuild)Vars.world.build(56, 7);
        processor.updateTile();
        Vars.player.unit().health = 0f;
        processor.updateTile();
        check(((VirusIntrusionScript)processor.script).finished() && !((VirusIntrusionScript)processor.script).won(), "Gamma destruction did not lose");
        // Verify actual world snapshot restoration, including base contents and tile changes.
        SaveIO.load(exported);
        Vars.world.tile(10, 10).setBlock(mindustry.content.Blocks.copperWall, Team.sharded);
        Vars.world.build(56, 14).items.add(mindustry.content.Items.copper, 1234);
        Fi reality = new Fi("/tmp/cryon-native-map-check/reality-check.msav");
        SaveIO.write(reality);
        Vars.logic.reset();
        IntrusionMap.generate();
        check(Vars.world.tile(10, 10).block() != mindustry.content.Blocks.copperWall, "new map retained old tiles");
        SaveIO.load(reality);
        check(Vars.world.tile(10, 10).block() == mindustry.content.Blocks.copperWall, "reality tile was not restored");
        check(Vars.world.build(56, 14).items.get(mindustry.content.Items.copper) == 1234, "reality core items lost");
        // Plain Element HUDs must be sized explicitly, including after screen resizing.
        processor = (JavaProcessorBuild)Vars.world.build(56, 7);
        processor.updateTile();
        Core.gl = (arc.graphics.GL20)java.lang.reflect.Proxy.newProxyInstance(
                arc.graphics.GL20.class.getClassLoader(), new Class<?>[]{arc.graphics.GL20.class},
                (proxy, method, values) -> null);
        Core.graphics = new MockGraphics(){
            @Override public int getWidth(){ return 800; }
            @Override public int getHeight(){ return 600; }
        };
        Core.scene = new arc.scene.Scene();
        cryon.Story.IntrusionOverlay.show();
        arc.scene.Element hud = Core.scene.getElements().peek();
        check(hud.getWidth() == 800 && hud.getHeight() == 600, "HUD has zero or wrong dimensions");
        Core.scene.getViewport().setWorldSize(720, 1280);
        hud.act(1f / 60f);
        check(hud.getWidth() == 720 && hud.getHeight() == 1280, "HUD does not follow screen resizing");
        check(IntrusionMap.visibleMinX > 14 * 8f + 4f && IntrusionMap.visibleMaxX < 98 * 8f - 4f,
                "side plastanium walls not inside black mask");
        check(IntrusionMap.visibleMinY > 22 * 8f + 4f && IntrusionMap.visibleMaxY < 130 * 8f - 4f,
                "top/bottom plastanium walls not inside black mask");
        System.out.println("PASS: native map export, rule lock, processor lock, Gamma control, real enemy bullets, save/load roundtrip, Gamma firing/damage, native victory/defeat, world snapshot restoration, HUD dimensions/resizing, wall mask bounds");
        System.out.println("Map: " + exported.absolutePath());
    }
}
