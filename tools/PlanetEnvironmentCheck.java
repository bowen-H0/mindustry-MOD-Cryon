import arc.Core;
import arc.Settings;
import arc.files.Fi;
import arc.mock.*;
import arc.util.I18NBundle;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.UnitTypes;
import mindustry.entities.abilities.Ability;
import mindustry.game.Rules;
import mindustry.game.Team;
import mindustry.gen.Unit;
import mindustry.net.Net;
import mindustry.world.meta.Env;
import java.io.*;

/** Verifies adaptation defaults, status consequences, and statue save persistence. */
public class PlanetEnvironmentCheck{
    private static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }
    private static Rules rules(String name){
        Rules rules = new Rules(); rules.planet = Vars.content.planet("cryon-" + name); return rules;
    }
    private static Class<?> traits;
    private static Class<?> traitEnum;
    private static mindustry.type.StatusEffect heatStress, coldStress, crystallized;
    private static void apply(Unit unit, Rules rules) throws Exception{
        traits.getMethod("apply", Unit.class, Rules.class).invoke(null, unit, rules);
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean active(Rules rules, String name) throws Exception{
        Object trait = Enum.valueOf((Class)traitEnum, name);
        return (Boolean)traits.getMethod("active", Rules.class, traitEnum).invoke(null, rules, trait);
    }
    public static void main(String[] args) throws Exception{
        Core.app = new MockApplication(); Core.files = new MockFiles();
        Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Core.settings = new Settings();
        Core.settings.setDataDirectory(new Fi("/tmp/cryon-environment-check"));
        Core.bundle = I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(
                Core.settings.getDataDirectory().child("mods/cryon.jar"));
        Vars.headless = true; Vars.loadLocales = false;
        Vars.net = new Net(null); Vars.platform = new mindustry.core.Platform(){};
        mindustry.core.Version.init();
        Vars.init(); Vars.content.createBaseContent(); Vars.content.createModContent(); Vars.content.init();
        check(!Vars.mods.list().find(m -> m.name.equals("cryon")).hasContentErrors(), "Content errors");
        ClassLoader loader = Vars.mods.list().find(m -> m.name.equals("cryon")).main.getClass().getClassLoader();
        traits = Class.forName("cryon.Features.PlanetTraits", true, loader);
        traitEnum = Class.forName("cryon.Features.PlanetTraits$Trait", true, loader);
        traits.getMethod("configure").invoke(null);
        heatStress = Vars.content.statusEffect("cryon-heat-stress");
        coldStress = Vars.content.statusEffect("cryon-cold-stress");
        crystallized = Vars.content.statusEffect("cryon-crystallized");
        Rules heat = rules("aravis"), cold = rules("cryon"), crystal = rules("porphyra");
        check(active(heat, "heat"), "Aravis must be hot");
        check(!active(crystal, "heat"), "Scorching is a native environment, not heat stress");
        Unit cryon = Vars.content.unit("cryon-buffer").create(Team.sharded);
        apply(cryon, heat);
        check(cryon.hasEffect(heatStress), "Cryon unit is not weakened by heat");
        apply(cryon, cold);
        check(!cryon.hasEffect(coldStress), "Cryon unit is not cold adapted");
        Unit nativeUnit = UnitTypes.stell.create(Team.sharded);
        apply(nativeUnit, heat);
        check(!nativeUnit.hasEffect(heatStress), "Default heat adaptation missing");
        apply(nativeUnit, cold);
        check(nativeUnit.hasEffect(coldStress), "Cold incompatibility missing");
        Unit defaultCold = UnitTypes.alpha.create(Team.sharded);
        apply(defaultCold, cold);
        check(!defaultCold.hasEffect(coldStress), "Default cold tolerance missing");
        Unit aravis = Vars.content.unit("cryon-jaspis").create(Team.sharded);
        apply(aravis, cold);
        check(!aravis.hasEffect(coldStress), "Aravis lost default cold tolerance");
        Unit local = Vars.content.unit("cryon-receptor").create(Team.sharded);
        check(local.type.supportsEnv(Env.terrestrial | Env.scorching), "Local unit fails native scorching check");
        check((Vars.content.planet("cryon-porphyra").defaultEnv & Env.scorching) != 0,
                "Porphyra lacks native scorching environment");
        apply(local, crystal);
        check(!local.hasEffect(crystallized), "Native Porphyra unit crystallized");
        apply(local, heat);
        apply(local, cold);
        check(!local.hasEffect(heatStress) && !local.hasEffect(coldStress),
                "Porphyra must tolerate both heat and cold");
        for(var type : Vars.content.units()){
            if(type.supportsEnv(Env.terrestrial | Env.scorching)){
                check(type.immunities.contains(heatStress), "Scorching unit lacks heat tolerance: " + type.name);
            }
            if(type instanceof mindustry.type.unit.ErekirUnitType)
                check(!type.immunities.contains(coldStress), "Erekir unit remains cold tolerant: " + type.name);
        }
        nativeUnit.rotation = 37f;
        nativeUnit.vel.set(3, 2);
        nativeUnit.isShooting = true;
        nativeUnit.abilities = new Ability[]{new cryon.Type.SpaceJumpAbility()};
        apply(nativeUnit, crystal);
        check(nativeUnit.hasEffect(crystallized), "Incompatible unit did not crystallize");
        check(nativeUnit.vel.isZero() && !nativeUnit.isShooting && nativeUnit.disarmed
                && nativeUnit.abilities.length == 0, "Statue still acts or uses abilities");
        nativeUnit.rotation = 90f;
        crystallized.getClass().getMethod("freeze", Unit.class).invoke(crystallized, nativeUnit);
        check(nativeUnit.rotation == 37f, "Statue can turn");
        Vars.world.loadGenerator(32, 32, tiles -> {
            for(int x = 0; x < 32; x++) for(int y = 0; y < 32; y++)
                tiles.set(x, y, new mindustry.world.Tile(x, y, mindustry.content.Blocks.stone,
                        mindustry.content.Blocks.air, mindustry.content.Blocks.air));
        });
        // An occupied tile must survive conversion; the statue should use nearby free space.
        Vars.world.tile(16, 16).setBlock(mindustry.content.Blocks.copperWall, Team.sharded);
        Vars.state.rules.unitCap = 100;
        Vars.state.rules.unitCapVariable = false;
        nativeUnit.set(128f, 128f);
        nativeUnit.add();
        int originalId = nativeUnit.id;
        Class<?> statueBlock = Class.forName("cryon.Type.CrystalStatueBlock", true, loader);
        boolean converted = (Boolean)statueBlock.getMethod("convert", Unit.class).invoke(null, nativeUnit);
        check(converted && !nativeUnit.isAdded() && mindustry.gen.Groups.unit.getByID(originalId) == null,
                "Crystal conversion retained the original unit");
        check(Vars.world.tile(16, 16).block() == mindustry.content.Blocks.copperWall,
                "Crystal statue overwrote an existing building");
        mindustry.gen.Building statue = null;
        int count = 0;
        for(var tile : Vars.world.tiles){
            if(tile.build != null && tile.build.tile == tile && tile.block().name.startsWith("cryon-crystal-statue")){
                statue = tile.build; count++;
            }
        }
        check(count == 1 && statue.team == Team.derelict, "Crystal statue block missing or duplicated");
        String expected = nativeUnit.type.name + "|" + nativeUnit.rotation;
        check(expected.equals(statue.config()), "Statue lost the unit shape or facing");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        statue.write(Writes.get(new DataOutputStream(bytes)));
        mindustry.gen.Building restored = statue.block.buildType.get();
        restored.block = statue.block;
        restored.read(Reads.get(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))), statue.version());
        check(expected.equals(restored.config()), "Statue block lost its data on save/read");
        check(!(Boolean)statueBlock.getMethod("convert", Unit.class).invoke(null, nativeUnit),
                "A removed unit created another statue");
        heat.tags.put("cryon-environment-heat", "false");
        check(!active(heat, "heat"), "Per-map trait override ignored");
        System.out.println("PASS: cold/heat adaptation, native scorching, unit-to-statue block conversion, block save persistence, map overrides");
    }
}
