import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.struct.Seq;
import arc.util.*;
import java.nio.file.Files;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.io.SaveIO;
import mindustry.net.Net;
import mindustry.type.ItemStack;
import mindustry.world.*;
import mindustry.world.blocks.ConstructBlock;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import rhino.*;

/** Real Rhino adapters, including inner builds, through construction, callbacks and world saves. */
public class AllianceScriptAdapterCheck{
    static Context context;
    static Scriptable scope;
    static final Seq<Building> originals = new Seq<>();
    static void check(boolean valid, String message){ if(!valid) throw new AssertionError(message); }
    static Building scriptBuild(Block block, boolean turret){
        ScriptableObject.putProperty(scope, "buildBlock", Context.javaToJS(block, scope));
        Object value = context.evaluateString(scope,
            "new JavaAdapter(" + (turret ? "Packages.mindustry.world.blocks.defense.turrets.ItemTurret.ItemTurretBuild" : "Packages.mindustry.gen.Building") + ", {" +
            "  _counter: 7," +
            "  updateTile: function(){ this._counter++; this.cdump = this._counter; }," +
            "  write: function(w){ this.super$write(w); w.i(this._counter); }," +
            "  read: function(r,v){ this.super$read(r,v); this._counter = r.i(); this.cdump = this._counter; }" +
            "}" + (turret ? ", buildBlock" : "") + ")", "alliance-script-constructor-check", 1);
        Building result = (Building)Context.jsToJava(value, Building.class);
        originals.add(result);
        return result;
    }
    static void selfBound(Building building) throws Exception{
        Scriptable self = (Scriptable)building.getClass().getField("self").get(building);
        check(self instanceof Wrapper && ((Wrapper)self).unwrap() == building, "Script this is bound to the discarded prototype");
    }
    public static void main(String[] args) throws Exception{
        Core.app = new MockApplication(); Core.files = new MockFiles(); Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Core.settings = new Settings(); Core.settings.setDataDirectory(new Fi(Files.createTempDirectory("cryon-script-allies-").toFile()));
        Core.bundle = I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(Core.settings.getDataDirectory().child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(Core.settings.getDataDirectory().child("mods/unitlanuch.jar"));
        Vars.headless = true; Vars.loadLocales = false; Vars.net = new Net(null); Vars.platform = new mindustry.core.Platform(){};
        mindustry.core.Version.init(); Vars.init(); Vars.content.createBaseContent(); Vars.content.createModContent();
        context = Vars.platform.getScriptContext(); scope = context.initStandardObjects();
        Block plain = new Block("alliance-script-build-test");
        plain.update = true; plain.destructible = true; plain.health = 500;
        plain.buildType = () -> scriptBuild(plain, false);
        ItemTurret turret = new ItemTurret("alliance-script-inner-build-test");
        turret.size = 1; turret.health = 400; turret.requirements(mindustry.type.Category.turret, ItemStack.empty);
        turret.ammo(Items.copper, new mindustry.entities.bullet.BasicBulletType(2, 10));
        turret.buildType = () -> scriptBuild(turret, true);
        Vars.content.init();
        var mod = Vars.mods.locateMod("cryon"); check(!mod.hasContentErrors(), "Packaged content error");
        var alliances = mod.main.getClass().getClassLoader().loadClass("cryon.Features.MapAlliances");
        alliances.getMethod("install").invoke(null);
        Vars.logic = new Logic(); Vars.logic.reset();
        alliances.getMethod("setAlly", Rules.class, Team.class, boolean.class).invoke(null, Vars.state.rules, Team.green, true);
        Vars.world.loadGenerator(64, 64, tiles -> {
            for(int x = 0; x < 64; x++) for(int y = 0; y < 64; y++) tiles.set(x, y, new Tile(x, y, Blocks.stone, Blocks.air, Blocks.air));
        });
        System.out.println("SCRIPT_ADAPTER_CTORS: " + java.util.Arrays.toString(scriptBuild(plain, false).getClass().getConstructors())
            + "; " + java.util.Arrays.toString(scriptBuild(turret, true).getClass().getConstructors()));
        // This is the same constructFinish -> Tile.setBlock -> Block.newBuilding path as the report.
        ConstructBlock.constructFinish(Vars.world.tile(20, 20), plain, null, (byte)0, Team.green, null);
        ConstructBlock.constructFinish(Vars.world.tile(24, 20), turret, null, (byte)0, Team.green, null);
        ConstructBlock.constructFinish(Vars.world.tile(28, 20), plain, null, (byte)0, Team.green, null);
        Building first = Vars.world.tile(20, 20).build, inner = Vars.world.tile(24, 20).build, second = Vars.world.tile(28, 20).build;
        for(Building build : new Building[]{first, inner, second}){
            check(build.getClass().getName().startsWith("cryon.generated.AllianceEntity"), "Script build bypassed alliance behavior");
            selfBound(build); build.update(); check(build.cdump == 8, "Original script update callback was lost");
            float health = build.health;
            build.damage(Team.sharded, 30); check(build.health == health, "Allied damage bypassed scripted building protection");
            build.damage(Team.crux, 30); check(build.health < health, "Enemy damage was disabled");
        }
        check(inner instanceof ItemTurret.ItemTurretBuild, "Inner build lost its original class");
        check(inner.block == turret, "Inner constructor was not supplied the owning block");
        first.update(); check(first.cdump == 9 && second.cdump == 8, "Cached constructor shared script state across buildings");
        for(Building original : originals) check(original.cdump == 0, "Callbacks mutated the discarded source object");
        Fi save = Core.settings.getDataDirectory().child("scripted-alliance.msav");
        SaveIO.write(save); Vars.logic.reset(); SaveIO.load(save);
        Building loaded = Vars.world.tile(20, 20).build, loadedInner = Vars.world.tile(24, 20).build;
        selfBound(loaded); selfBound(loadedInner);
        check(loaded.cdump == 9 && loadedInner.cdump == 8, "Script write/read state was lost");
        loaded.update(); loadedInner.update();
        check(loaded.cdump == 10 && loadedInner.cdump == 9, "Loaded callbacks target the wrong object");
        float health = loaded.health; loaded.damage(Team.sharded, 30);
        check(loaded.health == health, "Loaded scripted building lost its alliance protection");
        Vars.logic.reset();
        Building unaffected = plain.buildType.get();
        check(!unaffected.getClass().getName().startsWith("cryon.generated."), "Non-alliance maps changed their scripted providers");
        System.out.println("ALLIANCE_SCRIPT_ADAPTER_PASS: real Rhino constructors with/without outer block, constructFinish, script this/callbacks, per-instance state, friendly/enemy damage, script save/reload and map isolation");
    }
}
