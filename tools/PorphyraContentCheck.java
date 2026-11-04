import arc.Core;
import arc.Events;
import arc.Settings;
import arc.files.Fi;
import arc.mock.*;
import arc.util.I18NBundle;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.core.Logic;
import mindustry.game.Objectives.SectorComplete;
import mindustry.game.EventType.SectorCaptureEvent;
import mindustry.game.Team;
import mindustry.mod.Mods;
import mindustry.net.Net;
import mindustry.type.Planet;
import mindustry.world.WorldParams;
import mindustry.world.blocks.defense.turrets.ItemTurret;

/** Loads the packaged mod and verifies the staged Porphyra campaign. */
public class PorphyraContentCheck{
    private static void check(boolean valid, String message){
        if(!valid) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception{
        Core.app = new MockApplication(); Core.files = new MockFiles();
        Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Core.settings = new Settings();
        Core.settings.setDataDirectory(new Fi("/tmp/porphyra-content-check"));
        Core.bundle = I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(
                Core.settings.getDataDirectory().child("mods/cryon.jar"));
        Vars.headless = true; Vars.loadLocales = false;
        Vars.net = new Net(null); Vars.platform = new mindustry.core.Platform(){};
        mindustry.core.Version.init();
        Vars.init(); Vars.content.createBaseContent(); Vars.content.createModContent(); Vars.content.init();
        Mods.LoadedMod mod = Vars.mods.list().find(m -> m.name.equals("cryon"));
        check(mod != null && !mod.hasContentErrors(), "Porphyra content errors: " + (mod == null ? "mod missing" : mod.erroredContent));
        Planet star = Vars.content.planet("cryon-kyanos");
        cryon.Features.NormaTeam.init();
        cryon.Features.AllianceTeam.init();
        Planet planet = Vars.content.planet("cryon-porphyra");
        check(star != null && planet != null && planet.parent == star, "Porphyra system missing");
        check(!star.accessible && !star.alwaysUnlocked, "Kyanos must be inaccessible");
        check(!planet.allowLaunchToNumbered, "Numbered sectors bypass campaign progression");
        check(planet.defaultCore != null && planet.defaultCore.name.equals("cryon-core-thread"), "Default core missing");
        planet.generator = new cryon.Generator.PorphyraPlanetGenerator();
        cryon.TechTree.PorphyraTechTree.load();
        String[] stages = {"porphyra-landing", "porphyra-glassfield", "porphyra-recrystallization",
                "porphyra-lightband", "porphyra-pale-ridge", "porphyra-phase-scar",
                "porphyra-neptunium-basin", "porphyra-ashen-arc", "porphyra-heavy-element",
                "porphyra-final-core"};
        for(int i = 0; i < stages.length; i++){
            var preset = Vars.content.sector("cryon-" + stages[i]);
            check(preset != null && preset.planet == planet && preset.sector.id == i,
                    "Missing campaign stage " + i);
            check(preset.techNode != null, "Stage missing from tech tree: " + stages[i]);
            check(preset.captureWave == 0, "Stage can be won by surviving waves: " + stages[i]);
            check(preset.generator != null && preset.generator.map != null,
                    "Stage map missing: " + stages[i]);
            check(preset.generator.map.rules().spawns.contains(g -> g.type == Vars.content.unit("cryon-receptor")),
                    "Stage map lacks dedicated enemy waves: " + stages[i]);
            check(preset.generator.map.rules().winWave == 0
                    && preset.generator.map.rules().attackMode
                    && preset.generator.map.rules().waveTeam == cryon.Features.NormaTeam.team,
                    "Stage must be an enemy-base assault: " + stages[i]);
            if(i > 0){
                var previous = Vars.content.sector("cryon-" + stages[i - 1]);
                check(preset.techNode.objectives.contains(o -> o instanceof SectorComplete
                        && ((SectorComplete)o).preset == previous), "Stage order broken at " + i);
            }
        }
        String[] turretNames = {"etch", "excite", "pulse", "dissociate", "deflect", "glow",
                "detune", "overload", "critical", "collapse"};
        for(String name : turretNames){
            var turret = Vars.content.block("cryon-porphyra-" + name);
            check(turret != null && turret.techNode != null, "Turret missing: " + name);
        }
        ItemTurret etch = (ItemTurret)Vars.content.block("cryon-porphyra-etch");
        ItemTurret collapse = (ItemTurret)Vars.content.block("cryon-porphyra-collapse");
        check(etch.ammoTypes.get(Vars.content.item("cryon-rhenium")).pierce, "Etch must pierce");
        check(collapse.ammoTypes.get(Vars.content.item("cryon-oganesson")).splashDamageRadius >= 140,
                "Collapse area damage missing");
        check(etch.shootEffect == Fx.shootBigColor && collapse.shootEffect == Fx.shootBigColor,
                "Turret muzzle flashes must use ammo color");
        String[] ores = {"rhenium", "technetium", "promethium", "neptunium", "oganesson"};
        for(String name : ores){
            check(Vars.content.item("cryon-" + name) != null, "Missing item: " + name);
            check(Vars.content.block("cryon-ore-" + name) != null, "Missing ore: " + name);
        }
        var gamma = Vars.content.item("cryon-gamma-crystal");
        check(gamma != null && gamma.hardness == 1 && gamma.techNode != null,
                "Starter gamma crystal item or research missing");
        check(Vars.content.block("cryon-ore-gamma-crystal").itemDrop == gamma,
                "Gamma crystal ore has wrong drop");
        for(String name : new String[]{"thread", "process", "kernel"}){
            var miner = Vars.content.unit("cryon-" + name);
            check(miner.mineTier >= gamma.hardness && miner.mineItems.contains(gamma),
                    "Core unit cannot mine gamma crystal: " + name);
        }
        check(((mindustry.world.blocks.production.Drill)Vars.content.block("cryon-porphyra-drill")).tier >= gamma.hardness,
                "Starter drill cannot mine gamma crystal");
        for(int tier = 1; tier < ores.length; tier++){
            var gate = Vars.content.sector("cryon-" + stages[tier * 2 - 1]);
            check(Vars.content.item("cryon-" + ores[tier]).techNode.objectives.contains(
                    o -> o instanceof SectorComplete && ((SectorComplete)o).preset == gate),
                    ores[tier] + " sector gate missing");
        }
        String[][] lines = {{"receptor", "synapse", "ganglion", "cortex", "connectome"},
                {"monomer", "dimer", "oligomer", "polymer", "macromolecule"},
                {"particle", "momentum", "inertia", "gravity", "singularity"}};
        for(String[] line : lines) for(String name : line)
            check(Vars.content.unit("cryon-" + name) != null
                    && Vars.content.unit("cryon-" + name).techNode != null, "Missing unit: " + name);
        for(String[] line : lines) for(String name : line)
            checkWeaponSprites(name);
        for(String name : new String[]{"thread", "process", "kernel"}) checkWeaponSprites(name);
        for(String name : lines[0]){
            var hover = Vars.content.unit("cryon-" + name);
            var elude = mindustry.content.UnitTypes.elude;
            check(hover.parts.count(p -> p instanceof mindustry.entities.part.HoverPart) == 2
                    && hover.parts.first() instanceof mindustry.entities.part.HoverPart
                    && ((mindustry.entities.part.HoverPart)hover.parts.first()).mirror,
                    "Missing mirrored hover rings: " + name);
            check(hover.parts.count(p -> p instanceof cryon.Type.PorphyraLightPart) == 1,
                    "Missing animated hull light layer: " + name);
            for(var weapon : hover.weapons)
                check(weapon.bullet.hitColor.equals(arc.graphics.Color.white),
                        "Hover bullet effects must be white: " + name);
            check(hover.constructor.get().getClass() == elude.constructor.get().getClass(),
                    "Hover entity differs from Elude: " + name);
            check(!hover.flying && hover.hovering && !hover.canDrown
                    && hover.useEngineElevation == elude.useEngineElevation
                    && hover.shadowElevation == elude.shadowElevation
                    && hover.accel == elude.accel && hover.drag == elude.drag,
                    "Hover movement differs from Elude: " + name);
        }
        for(String name : lines[1]) for(var weapon : Vars.content.unit("cryon-" + name).weapons)
            check(weapon.bullet.hitColor.equals(arc.graphics.Color.valueOf("78baff")),
                    "Tank bullet effects must be blue: " + name);
        var synapse = Vars.content.unit("cryon-synapse");
        check(synapse.engines.size == 2 && synapse.engines.get(0).x == -3.5f
                && synapse.engines.get(1).x == 3.5f, "Synapse twin engines are not spaced correctly");
        check(synapse.abilities.count(a -> a instanceof mindustry.entities.abilities.MoveEffectAbility) == 2,
                "Synapse needs two particle trails");
        for(String[] line : lines) for(String name : line)
            check(cryon.Features.SpaceJump.excluded(Vars.content.unit("cryon-" + name)),
                    "Porphyra unit can acquire space jump: " + name);
        for(String name : new String[]{"thread", "process", "kernel"})
            check(cryon.Features.SpaceJump.excluded(Vars.content.unit("cryon-" + name)),
                    "Porphyra core unit can acquire space jump: " + name);
        for(String name : new String[]{"duct", "router", "bridge", "junction", "sorter", "unloader",
                "mass-driver", "conduit",
                "liquid-router", "liquid-tank", "node", "solar", "battery", "reactor",
                "drill", "deep-drill", "plasma-forge", "unit-factory", "reconstructor-2",
                "reconstructor-3", "reconstructor-4", "reconstructor-5"})
            check(Vars.content.block("cryon-porphyra-" + name) != null
                    && Vars.content.block("cryon-porphyra-" + name).techNode != null,
                    "Missing facility: " + name);
        for(String name : new String[]{"thread", "process", "kernel"}){
            check(Vars.content.unit("cryon-" + name) != null, "Missing core unit: " + name);
            check(Vars.content.block("cryon-core-" + name) != null, "Missing core: " + name);
        }
        check(Vars.content.unit("cryon-thread").weapons.first().bullet.shootEffect == Fx.shootSmallColor
                && Vars.content.unit("cryon-process").weapons.first().bullet.shootEffect == Fx.shootBigColor
                && Vars.content.unit("cryon-kernel").weapons.first().bullet.shootEffect == Fx.shootBigColor,
                "Core unit muzzle flashes must use bullet color");
        Vars.logic = new Logic();
        for(int stage : new int[]{0, 2, 4, 6, 8, 9}){
            var sector = planet.sectors.get(stage);
            Vars.logic.reset();
            Vars.state.rules.sector = sector;
            Vars.state.rules.planet = planet;
            Vars.world.loadGenerator(192, 192, tiles -> planet.generator.generate(tiles, sector, new WorldParams()));
            checkStarterMinerals();
            check(Vars.state.rules.defaultTeam == cryon.Features.AllianceTeam.team
                    && Vars.world.tile(96, 38).team() == cryon.Features.AllianceTeam.team,
                    "Player faction must be Alliance on stage " + stage);
            check(Vars.world.tile(96, 38).block().name.equals("cryon-core-thread"),
                    "Landing core missing on stage " + stage);
            check(Vars.state.rules.attackMode && Vars.state.rules.winWave == 0
                    && Vars.state.rules.waveTeam == cryon.Features.NormaTeam.team
                    && Vars.state.rules.spawns.contains(g -> g.type == Vars.content.unit("cryon-receptor"))
                    && Vars.state.rules.spawns.contains(g -> g.type == Vars.content.unit("cryon-monomer"))
                    && Vars.state.rules.spawns.contains(g -> g.type == Vars.content.unit("cryon-particle")),
                    "Dedicated enemy roster missing on stage " + stage);
            check(Vars.state.rules.spawns.contains(g -> !g.type.name.startsWith("cryon-")) == false,
                    "Vanilla enemy leaked into stage " + stage);
            int[] oreCounts = new int[5];
            for(var tile : Vars.world.tiles){
                for(int i = 0; i < ores.length; i++)
                    if(tile.overlay().name.equals("cryon-ore-" + ores[i])) oreCounts[i]++;
            }
            for(int i = 0; i < ores.length; i++)
                check((oreCounts[i] > 0) == (i <= stage / 2),
                        "Ore progression incorrect on stage " + stage + ": " + ores[i]);
        }
        for(int stage = 0; stage < stages.length; stage++){
            var preset = Vars.content.sector("cryon-" + stages[stage]);
            Vars.logic.reset();
            Vars.state.rules.sector = preset.sector;
            Vars.state.rules.planet = planet;
            Vars.world.loadGenerator(192, 192,
                    tiles -> preset.generator.generate(tiles, new WorldParams()));
            checkStarterMinerals();
            check(Vars.state.rules.defaultTeam == cryon.Features.AllianceTeam.team
                    && Vars.world.tile(96, 38).team() == cryon.Features.AllianceTeam.team,
                    "Player faction must be Alliance on stage " + stage);
            check(Vars.world.tile(96, 38).block().name.equals("cryon-core-thread"),
                    "Packaged stage map lacks a landing core: " + stage);
            String enemyCore = stage == 9 ? "cryon-ring-control-center"
                    : stage >= 8 ? "cryon-core-kernel" : stage >= 4 ? "cryon-core-process" : "cryon-core-thread";
            boolean foundEnemyCore = false;
            int loadedDefenses = 0;
            for(var tile : Vars.world.tiles){
                if(tile.block().name.equals(enemyCore) && tile.team() == cryon.Features.NormaTeam.team){
                    foundEnemyCore = true;
                }
                if(tile.build instanceof ItemTurret.ItemTurretBuild && tile.build.tile == tile
                        && tile.team() == cryon.Features.NormaTeam.team
                        && ((ItemTurret.ItemTurretBuild)tile.build).totalAmmo > 0) loadedDefenses++;
            }
            check(foundEnemyCore, "Packaged stage map lacks the enemy control core: " + stage);
            check(loadedDefenses >= 2 + (stage / 3) * 2,
                    "Packaged stage map lacks armed defenses: " + stage + " / " + loadedDefenses);
        }
        check(Vars.content.item("cryon-electronic-component") == null
                && Vars.content.block("cryon-porphyra-electronics-assembler") == null,
                "Removed electronics content is still registered");
        for(String name : new String[]{"sorter", "unloader", "battery", "unit-factory"}){
            boolean usesGamma = false;
            for(var cost : Vars.content.block("cryon-porphyra-" + name).requirements)
                if(cost.item == gamma) usesGamma = true;
            check(usesGamma, "Gamma crystal is not used directly by " + name);
        }
        var unitFactory = (mindustry.world.blocks.units.UnitFactory)Vars.content.block("cryon-porphyra-unit-factory");
        for(var plan : unitFactory.plans){
            boolean hasGamma = false, hasMetal = false;
            for(var cost : plan.requirements){
                check(cost.amount > 0, "Invalid unit material amount");
                if(cost.item == gamma) hasGamma = true;
                if(cost.item == Vars.content.item("cryon-rhenium")) hasMetal = true;
            }
            check(hasGamma && hasMetal, "Starter unit requires both rhenium and gamma: " + plan.unit);
        }
        for(int tier = 2; tier <= 5; tier++){
            var reconstructor = Vars.content.block("cryon-porphyra-reconstructor-" + tier);
            mindustry.world.consumers.ConsumeItems inputs = reconstructor.findConsumer(c -> c instanceof mindustry.world.consumers.ConsumeItems);
            check(inputs != null && inputs.items.length >= 2, "Single-material unit upgrade at tier " + tier);
            boolean hasGamma = false, hasMetal = false;
            for(var cost : inputs.items){
                if(cost.item == gamma) hasGamma = cost.amount > 0;
                else if(cost.amount > 0) hasMetal = true;
            }
            check(hasGamma && hasMetal, "Reconstructor must consume gamma and tier material: " + tier);
        }
        float previousWallHealth = 0;
        for(String name : new String[]{"rhenium", "gamma", "neptunium"}){
            var small = Vars.content.block("cryon-porphyra-" + name + "-wall");
            var large = Vars.content.block("cryon-porphyra-" + name + "-wall-large");
            check(small instanceof mindustry.world.blocks.defense.Wall
                    && large instanceof mindustry.world.blocks.defense.Wall,
                    "Missing defensive walls: " + name);
            check(small.size == 1 && large.size == 2 && small.health > previousWallHealth
                    && large.health == small.health * 4,
                    "Incorrect wall size or durability progression: " + name);
            check(small.category == mindustry.type.Category.defense && large.category == small.category
                    && small.techNode != null && large.techNode != null,
                    "Walls missing from construction or research: " + name);
            for(var cost : small.requirements){
                boolean matched = false;
                for(var largeCost : large.requirements)
                    if(cost.item == largeCost.item && largeCost.amount == cost.amount * 4) matched = true;
                check(matched, "Large wall costs must scale with occupied area: " + name);
            }
            previousWallHealth = small.health;
        }
        // Simulate the previous release's campaign save, then fire the load hook.
        Team norma = cryon.Features.NormaTeam.team;
        check(norma.id == 169 && Team.get(169) == norma && norma != Team.green,
                "Independent Norma registration missing");
        check(Team.green.name.equals("green") && Team.green.color.equals(arc.graphics.Color.valueOf("54d67d")),
                "Vanilla green was modified");
        mindustry.world.Tile legacyCore = null;
        for(var tile : Vars.world.tiles){
            if(tile.isCenter() && tile.build != null && tile.team() == norma
                    && tile.block() instanceof mindustry.world.blocks.storage.CoreBlock){
                legacyCore = tile;
                break;
            }
        }
        check(legacyCore != null, "Migration test lacks an enemy core");
        legacyCore.build.changeTeam(Team.green);
        var legacyUnit = mindustry.content.UnitTypes.dagger.spawn(Team.green, legacyCore.worldx(), legacyCore.worldy());
        Vars.state.rules.waveTeam = Team.green;
        Vars.state.rules.teams.get(Team.green).unitDamageMultiplier = 2.5f;
        var legacyWave = new mindustry.game.SpawnGroup(mindustry.content.UnitTypes.dagger);
        legacyWave.team = Team.green;
        Vars.state.rules.spawns.add(legacyWave);
        var sector = Vars.state.rules.sector;
        Vars.state.rules.sector = null;
        Events.fire(new mindustry.game.EventType.WorldLoadEvent());
        check(legacyCore.team() == Team.green && Vars.state.rules.waveTeam == Team.green,
                "Migration affected a non-campaign map");
        Vars.state.rules.sector = sector;
        Events.fire(new mindustry.game.EventType.WorldLoadEvent());
        check(legacyCore.team() == norma && legacyUnit.team == norma
                && Vars.state.rules.waveTeam == norma && legacyWave.team == norma,
                "Legacy campaign factions were not migrated");
        check(Vars.state.rules.teams.get(norma).unitDamageMultiplier == 2.5f,
                "Migration lost the enemy team rules");
        check(norma.cores().contains((mindustry.world.blocks.storage.CoreBlock.CoreBuild)legacyCore.build), "Migration did not update core team indexes");
        Events.fire(new mindustry.game.EventType.WorldLoadEvent());
        check(legacyCore.team() == norma, "Migration is not idempotent");
        cryon.Features.PorphyraCampaign.install();
        var playerCore = Vars.world.tile(96, 38);
        playerCore.build.changeTeam(Team.sharded);
        var playerUnit = mindustry.content.UnitTypes.dagger.spawn(Team.sharded, playerCore.worldx(), playerCore.worldy());
        Vars.state.rules.defaultTeam = Team.sharded;
        Vars.state.rules.teams.get(Team.sharded).unitHealthMultiplier = 1.7f;
        Vars.state.rules.tags.remove("cryon-porphyra-alliance");
        var savedSector = Vars.state.rules.sector;
        Vars.state.rules.sector = null;
        Events.fire(new mindustry.game.EventType.WorldLoadEvent());
        check(playerCore.team() == Team.sharded && playerUnit.team == Team.sharded,
                "Alliance migration affected a non-campaign map");
        Vars.state.rules.sector = savedSector;
        Events.fire(new mindustry.game.EventType.WorldLoadEvent());
        Team alliance = cryon.Features.AllianceTeam.team;
        check(playerCore.team() == alliance && playerUnit.team == alliance
                && Vars.state.rules.defaultTeam == alliance && Vars.state.rules.waveTeam == norma,
                "Legacy Porphyra player faction did not migrate to Alliance");
        check(Vars.state.rules.teams.get(alliance).unitHealthMultiplier == 1.7f,
                "Player team rules lost during migration");
        Vars.state.rules.teams.get(alliance).unitHealthMultiplier = 1.9f;
        Events.fire(new mindustry.game.EventType.WorldLoadEvent());
        check(Vars.state.rules.teams.get(alliance).unitHealthMultiplier == 1.9f,
                "Repeated migration overwrote Alliance rules");
        Events.fire(new SectorCaptureEvent(planet.sectors.get(9), true));
        check(Core.settings.getBool("cryon-porphyra-network-disabled", false)
                && !planet.allowWaves && !planet.allowSectorInvasion,
                "Final conquest did not disable the ring defense network");
        System.out.println("PASS: 10 stages, staged ores, dedicated enemies, 15 units, 10 turrets and facilities");
    }

    private static void checkStarterMinerals(){
        int gamma = 0, rhenium = 0;
        for(var tile : Vars.world.tiles){
            if(tile.block().solid || arc.math.Mathf.dst(tile.x, tile.y, 96, 38) > 18f) continue;
            if(tile.overlay().name.equals("cryon-ore-gamma-crystal")) gamma++;
            if(tile.overlay().name.equals("cryon-ore-rhenium")) rhenium++;
        }
        check(gamma > 0 && rhenium > 0, "Landing area must have both starter ores on " + Vars.state.rules.sector.id + ": gamma=" + gamma + ", rhenium=" + rhenium);
    }

    private static void checkWeaponSprites(String unitName){
        for(var weapon : Vars.content.unit("cryon-" + unitName).weapons){
            check(weapon.name.startsWith("cryon-porphyra-"), "Unexpected weapon prefix: " + weapon.name);
            String basename = weapon.name.substring("cryon-".length());
            check(new Fi("sprites/units/weapons/porphyra/" + basename + ".png").exists(),
                    "Loaded weapon name does not match a sprite: " + weapon.name);
        }
    }
}
