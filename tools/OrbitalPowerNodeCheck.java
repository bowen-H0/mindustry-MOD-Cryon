import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.*;
import java.nio.file.Files;
import mindustry.*;
import mindustry.content.Blocks;
import mindustry.core.Logic;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.io.SaveIO;
import mindustry.net.Net;
import mindustry.world.*;
import mindustry.world.meta.BuildVisibility;

/** Actual packaged node: sandbox construction, power graph load, battery and save/load. */
public final class OrbitalPowerNodeCheck{
    static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }
    public static void main(String[] args){
        try{ run(); System.exit(0); }
        catch(Throwable error){ error.printStackTrace(); System.exit(1); }
    }
    static void run() throws Exception{
        Core.app = new MockApplication(); Core.files = new MockFiles(); Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Core.settings = new Settings(); Fi data = new Fi(Files.createTempDirectory("cryon-orbital-check-").toFile());
        Core.settings.setDataDirectory(data); Core.bundle = I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlanuch.jar"));
        Vars.headless = true; Vars.loadLocales = false; Vars.net = new Net(null); Vars.platform = new mindustry.core.Platform(){};
        mindustry.core.Version.init(); Vars.init(); Core.settings.setJson(mindustry.io.JsonIO.json);
        Vars.content.createBaseContent(); Vars.content.createModContent(); Vars.content.init();
        var mod = Vars.mods.locateMod("cryon"); check(mod != null && !mod.hasContentErrors(), "packaged content errors");
        Block node = Vars.content.block("cryon-orbital-power-node");
        check(node != null && node.buildVisibility == BuildVisibility.sandboxOnly && node.placeablePlayer && node.rebuildable,
            "orbital node is not sandbox-buildable");
        var powerNode = (mindustry.world.blocks.power.PowerNode)node;
        var vanillaNode = (mindustry.world.blocks.power.PowerNode)Blocks.powerNode;
        check(powerNode.laserColor1.equals(vanillaNode.laserColor1) && powerNode.laserColor2.equals(vanillaNode.laserColor2),
            "orbital node does not use native power-node colors");
        var offset = node.getClass().getMethod("parallaxOffset", float.class, float.class, float.class, float.class);
        float grounded = (float)offset.invoke(null, 100f, 0f, 0f, 85f);
        float raised = (float)offset.invoke(null, 100f, 0f, 1f, 85f);
        float shifted = (float)offset.invoke(null, 100f, 50f, 1f, 85f);
        check(grounded == 0f && raised > 0f && shifted < raised, "ribbons have no altitude-dependent camera offset");
        check(Math.abs((float)offset.invoke(null, 100000f, 0f, 1f, 85f)) <= 85f, "ribbon offset is not bounded");
        Vars.logic = new Logic(); Vars.logic.reset();
        Vars.world.loadGenerator(100, 140, tiles -> {
            for(int x = 0; x < 100; x++) for(int y = 0; y < 140; y++) tiles.set(x, y, new Tile(x, y, Blocks.stone, Blocks.air, Blocks.air));
            tiles.get(20, 20).setBlock(Blocks.coreShard, Team.sharded);
            tiles.get(45, 35).setBlock(node, Team.sharded);
            tiles.get(55, 35).setBlock(Blocks.siliconSmelter, Team.sharded);
            tiles.get(55, 40).setBlock(Blocks.battery, Team.sharded);
        });
        Time.delta = 1f;
        check(!node.isPlaceable() && !Build.validPlace(node, Team.sharded, 75, 75, 0), "node can be constructed in survival");
        Vars.state.rules.infiniteResources = true;
        check(node.isPlaceable() && Build.validPlace(node, Team.sharded, 75, 75, 0), "node cannot be constructed in sandbox");
        Vars.state.rules.editor = true;
        check(node.isPlaceable() && Build.validPlace(node, Team.sharded, 75, 75, 0), "editor cannot place node");
        Vars.state.rules.editor = false; Vars.state.rules.infiniteResources = false;
        Building source = Vars.world.build(45, 35), consumer = Vars.world.build(55, 35), battery = Vars.world.build(55, 40);
        source.configureAny(consumer.pos()); source.configureAny(battery.pos());
        check(source.power.graph == consumer.power.graph && source.power.graph == battery.power.graph, "native power links failed");
        float original = consumer.block.consPower.usage;
        try{
            consumer.block.consPower.usage = 1000000000000f;
            consumer.shouldConsumePower = true;
            check(source.getPowerProduction() > 1000000000000f && Float.isFinite(source.getPowerProduction()),
                "output has a fixed finite power-source ceiling: output=" + source.getPowerProduction() + " needed="
                    + source.power.graph.getPowerNeeded() + " enabled=" + source.enabled + " consumer=" + consumer.block.consPower.getClass().getSimpleName());
            source.power.graph.update();
            check(consumer.power.status >= 0.999f && Float.isFinite(consumer.power.status), "large load is not fully supplied");
            check(battery.power.status >= 0.999f && Float.isFinite(battery.power.status), "battery not fully charged");
            source.enabled = false;
            check(source.getPowerProduction() == 0f, "disabled orbital receiver still produces power");
            source.enabled = true;
        }finally{ consumer.block.consPower.usage = original; }
        Fi save = data.child("orbital.msav"); SaveIO.write(save); SaveIO.load(save);
        source = Vars.world.build(45, 35); consumer = Vars.world.build(55, 35);
        check(source.block == node && source.enabled && source.getPowerProduction() > 0f, "power node lost after save/load");
        check(source.power.graph == consumer.power.graph, "power links lost after save/load");
        System.out.println("ORBITAL_POWER_CHECK_PASS: packaged node, sandbox construction, survival rejection, editor placement, native links, demand-scaled finite power, battery, disable, save/load");
    }
}
