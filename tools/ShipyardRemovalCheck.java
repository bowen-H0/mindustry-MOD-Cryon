import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.I18NBundle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import mindustry.*;
import mindustry.content.Blocks;
import mindustry.core.Logic;
import mindustry.io.*;
import mindustry.net.Net;
import mindustry.world.Tile;

/** Snapshot with the old mod, then rewrite/verify the six maps with the removed content absent. */
public class ShipyardRemovalCheck{
    static final String removed = "cryon-abyssal-shipyard";
    static final Fi baseline = new Fi("tools/shipyard-removal-baseline.properties");
    static void check(boolean value, String message){ if(!value) throw new AssertionError(message); }
    static String fingerprint() throws Exception{
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        for(Tile tile : Vars.world.tiles){
            boolean yard = tile.block().name.equals(removed);
            String value = tile.pos() + ":" + tile.floor().name + ":" + tile.overlay().name + ":"
                + (yard ? Blocks.air.name : tile.block().name) + ":" + (yard ? 0 : tile.team().id);
            if(!yard && tile.isCenter() && tile.build != null){
                var build = tile.build;
                value += ":" + build.rotation + ":" + build.health + ":" + build.enabled
                    + ":" + (build.items == null ? "" : build.items.toString());
                if(build.liquids != null) for(var liquid : Vars.content.liquids()){
                    float amount = build.liquids.get(liquid);
                    if(amount != 0f) value += ":" + liquid.name + "=" + amount;
                }
                if(build.power == null && build.config() != null) value += ":" + JsonIO.write(build.config());
            }
            hash.update((value + "\n").getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(hash.digest());
    }
    public static void main(String[] args) throws Exception{
        String mode = args.length == 0 ? "verify" : args[0];
        boolean snapshot = mode.equals("snapshot"), rewrite = mode.equals("rewrite");
        Core.app = new MockApplication(); Core.files = new MockFiles(); Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Fi data = new Fi(Files.createTempDirectory("cryon-shipyard-removal-").toFile());
        Core.settings = new Settings(); Core.settings.setDataDirectory(data); Core.bundle = I18NBundle.createEmptyBundle();
        new Fi(snapshot ? "/tmp/cryon-before-shipyard-removal.jar" : "build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlanuch.jar"));
        Vars.headless = true; Vars.loadLocales = false; Vars.net = new Net(null); Vars.platform = new mindustry.core.Platform(){};
        mindustry.core.Version.init(); Vars.init(); Vars.content.createBaseContent(); Vars.content.createModContent(); Vars.content.init();
        var mod = Vars.mods.locateMod("cryon");
        check(mod != null && !mod.hasContentErrors(), "Content errors");
        check((Vars.content.block(removed) != null) == snapshot, "Unexpected shipyard content registration");
        var layers = mod.main.getClass().getClassLoader().loadClass("cryon.Features.CryonCampaignLayers");
        layers.getMethod("configure").invoke(null);
        Vars.logic = new Logic();
        Properties expected = new Properties();
        if(!snapshot) try(var input = baseline.read()){ expected.load(input); }
        Map<Fi, Fi> pending = new LinkedHashMap<>();
        for(String stage : (String[])layers.getField("stages").get(null)){
            String name = "cryon-" + stage + ".msav";
            Fi file = new Fi("maps/cryon-cryon-depths").child(name);
            if(!snapshot && !rewrite) file = mod.root.child("maps").child("cryon-cryon-depths").child(name);
            var map = MapIO.createMap(file, false);
            Vars.logic.reset(); MapIO.loadMap(map);
            String actual = fingerprint();
            if(snapshot){
                int yards = 0;
                for(Tile tile : Vars.world.tiles) if(tile.isCenter() && tile.block().name.equals(removed)) yards++;
                check(yards == 1, "Expected one original shipyard: " + name);
                expected.setProperty(name, actual);
            }else{
                check(actual.equals(expected.getProperty(name)), "Removal changed terrain, ores, unrelated buildings or inventory: " + name);
                for(Tile tile : Vars.world.tiles){
                    check(!tile.block().name.equals(removed), "Shipyard remains: " + name);
                    if(tile.isCenter() && tile.build != null && tile.build.power != null){
                        var links = tile.build.power.links;
                        for(int i = links.size - 1; i >= 0; i--){
                            var target = Vars.world.build(links.get(i));
                            if(target == null || target.power == null){
                                check(rewrite, "Stale power link in packaged map: " + name);
                                links.removeIndex(i);
                            }
                        }
                    }
                }
                if(rewrite){
                    Fi output = new Fi(file.path() + ".without-shipyard");
                    MapIO.writeMap(output, map);
                    Vars.logic.reset(); MapIO.loadMap(MapIO.createMap(output, false));
                    check(actual.equals(fingerprint()), "Map rewrite changed unrelated content: " + name);
                    pending.put(output, file);
                }
            }
            System.out.println("SHIPYARD_MAP_" + mode.toUpperCase(Locale.ROOT) + " " + name + ": " + actual);
        }
        if(snapshot){
            StringBuilder values = new StringBuilder("# Original six maps, with only shipyard tiles normalized to air.\n");
            for(String name : new TreeSet<>(expected.stringPropertyNames())) values.append(name).append('=').append(expected.getProperty(name)).append('\n');
            baseline.writeString(values.toString());
        }
        for(var entry : pending.entrySet()) entry.getKey().moveTo(entry.getValue());
        System.out.println("SHIPYARD_REMOVAL_" + mode.toUpperCase(Locale.ROOT) + "_PASS: six maps; all terrain, overlays, other buildings and inventories preserved");
    }
}
