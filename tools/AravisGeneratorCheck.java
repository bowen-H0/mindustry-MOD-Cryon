import arc.Core;
import arc.Settings;
import arc.files.Fi;
import arc.mock.*;
import arc.util.I18NBundle;
import cryon.Content.AravisEnvironment;
import cryon.Content.CryonItems;
import cryon.Generator.AravisPlanetGenerator;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Planets;
import mindustry.core.Logic;
import mindustry.ctype.ContentType;
import mindustry.game.Rules;
import mindustry.mod.ContentParser;
import mindustry.mod.Mods;
import mindustry.net.Net;
import mindustry.type.Planet;
import mindustry.type.Sector;
import mindustry.world.Tile;
import mindustry.world.WorldParams;
import java.util.ArrayDeque;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Headless integration check of real Aravis terrain, landing core, ores and wave routes. */
public class AravisGeneratorCheck {
    static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }

    public static void main(String[] args) throws Exception {
        try { run(); System.exit(0); }
        catch(Throwable failure){ failure.printStackTrace(); System.exit(1); }
    }

    static void run() throws Exception {
        Core.app = new MockApplication(); Core.files = new MockFiles();
        Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Core.settings = new Settings(); Core.settings.setDataDirectory(new Fi("/tmp/aravis-generator-check"));
        Core.bundle = I18NBundle.createEmptyBundle();
        Vars.headless = true; Vars.loadLocales = false;
        Vars.net = new Net(null); Vars.platform = new mindustry.core.Platform(){};
        Vars.init(); Vars.content.createBaseContent();
        Mods.ModMeta meta = new Mods.ModMeta(); meta.name = "cryon";
        Mods.LoadedMod mod = new Mods.LoadedMod(new Fi("."), new Fi("."), null,
                AravisGeneratorCheck.class.getClassLoader(), meta);
        Vars.content.setCurrentMod(mod);
        CryonItems.load(); AravisEnvironment.load();
        Planet planet = new Planet("aravis", Planets.sun, 1f, 2);
        Vars.content.setCurrentMod(null);
        ContentParser parser = new ContentParser();
        for(Fi f : new Fi("content/units/aravis").list())
            parser.parse(mod, f.nameWithoutExtension(), f.readString(), f, ContentType.unit);
        String[] blocks = {"dune-sand", "aeolian-sand", "red-sand", "gravel-sand", "dune-sand-wall",
                "aeolian-sand-wall", "red-sand-wall", "ore-ferrum", "ore-manganese", "ore-nickel"};
        for(String name : blocks){
            Fi f = new Fi("content/blocks/environment/aravis/" + name + ".hjson");
            parser.parse(mod, name, f.readString(), f, ContentType.block);
        }
        for(Fi core : new Fi("content/blocks/storage/aravis").list()){
            parser.parse(mod, core.nameWithoutExtension(), core.readString(), core, ContentType.block);
        }
        parser.finishParsing();
        Vars.content.init(); Vars.logic = new Logic();
        Vars.mods.getMods().add(mod);
        cryon.TechTree.AravisTechTree.load();
        for(String name : new String[]{"vindex", "imperator"}){
            mindustry.type.UnitType unit = Vars.content.unit("cryon-" + name);
            check(unit.techNode == null, "Core unit should not have a research node: " + name);
        }
        check(Vars.content.block("cryon-core-dominion").techNode != null,
                "Advanced core research node missing");
        System.out.println("PASS: core upgrade remains in tech tree; core units have no research nodes");
        AravisPlanetGenerator generator = new AravisPlanetGenerator();
        planet.generator = generator;
        long first = 0;
        for(int run = 0; run < 8; run++){
            Vars.logic.reset();
            Sector sector = planet.sectors.get(run == 7 ? 0 : run);
            Vars.state.rules = new Rules();
            Vars.state.rules.sector = sector; Vars.state.rules.planet = planet;
            WorldParams params = new WorldParams();
            Vars.world.loadGenerator(192, 192, tiles -> generator.generate(tiles, sector, params));
            Tile start = Vars.world.tile(96, 38);
            check(start.build != null && start.block().name.equals("cryon-core-conquest"), "Wrong/missing landing core");
            check(Vars.state.rules.spawns.size == 3 && Vars.state.rules.waves && !Vars.state.rules.attackMode,
                    "Missing survival waves");
            check(Vars.state.rules.weather.contains(e -> e.weather == AravisEnvironment.sandstorm && e.always),
                    "Missing permanent storm");
            boolean[] visited = new boolean[192 * 192];
            ArrayDeque<Tile> queue = new ArrayDeque<>();
            Tile exit = Vars.world.tile(96, 42); queue.add(exit); visited[exit.array()] = true;
            int reachedSpawn = 0, walls = 0;
            java.util.Set<String> ores = new java.util.HashSet<>();
            while(!queue.isEmpty()){
                Tile tile = queue.remove();
                if(tile.overlay() == Blocks.spawn) reachedSpawn++;
                if(tile.overlay().itemDrop != null) ores.add(tile.overlay().name);
                for(int d = 0; d < 4; d++){
                    Tile next = tile.nearby(d);
                    if(next != null && !next.solid() && !visited[next.array()]){
                        visited[next.array()] = true; queue.add(next);
                    }
                }
            }
            for(String ore : new String[]{"cryon-ore-ferrum", "ore-copper", "ore-coal", "cryon-ore-manganese", "cryon-ore-nickel"})
                check(ores.contains(ore), "No reachable " + ore + " in sector " + sector.id);
            check(reachedSpawn == 1, "No ground route to enemy spawn");
            long hash = 1;
            for(Tile tile : Vars.world.tiles){
                check(!tile.floor().isLiquid, "Liquid in dry desert");
                if(tile.block().solid && tile.build == null) walls++;
                hash = hash * 31 + tile.floorID(); hash = hash * 31 + tile.blockID(); hash = hash * 31 + tile.overlayID();
            }
            check(walls > 100, "No desert ridges");
            if(run == 0){ first = hash; preview(); }
            if(run == 1) check(hash != first, "Different sectors have identical terrain");
            if(run == 7){
                check(hash == first, "Generation is not reproducible");
                checkCoreUpgrade(start);
            }
            System.out.println("PASS sector " + sector.id + ": core, reachable ores/spawn, " + walls + " wall tiles");
        }
    }

    static void checkCoreUpgrade(Tile start){
        mindustry.world.blocks.storage.CoreBlock initial = (mindustry.world.blocks.storage.CoreBlock)start.block();
        mindustry.world.blocks.storage.CoreBlock advanced = (mindustry.world.blocks.storage.CoreBlock)
                Vars.content.block("cryon-core-dominion");
        check(initial.unitType.name.equals("cryon-vindex"), "Starter core still uses Alpha");
        check(advanced.unitType.name.equals("cryon-imperator"), "Wrong advanced core unit");
        check(advanced.size == initial.size + 1, "Core upgrade must add exactly one tile");
        for(mindustry.type.UnitType type : new mindustry.type.UnitType[]{initial.unitType, advanced.unitType}){
            check(!type.lowAltitude, "Core unit should fly at high altitude");
            for(mindustry.type.Weapon weapon : type.weapons)
                check(weapon.name.isEmpty() || weapon.name.equals("cryon-"),
                        "High-altitude core unit should not use weapon sprites: " + weapon.name);
        }
        check(advanced.canReplace(initial), "Advanced core cannot replace Conquest");
        for(mindustry.type.ItemStack stack : advanced.requirements) start.build.items.add(stack.item, stack.amount * 2);
        check(advanced.canPlaceOn(start, start.team(), 0), "Core upgrade cannot be placed");
        Vars.player = mindustry.gen.Player.create(); Vars.player.team(start.team());
        mindustry.world.blocks.storage.CoreBlock.playerSpawn(start, Vars.player);
        check(Vars.player.unit().type == initial.unitType && Vars.player.unit().spawnedByCore(), "Starter respawn failed");
        Vars.player.unit().remove(); Vars.player.clearUnit();
        start.setBlock(advanced, Vars.state.rules.defaultTeam, 0);
        mindustry.world.blocks.storage.CoreBlock.playerSpawn(start, Vars.player);
        check(Vars.player.unit().type == advanced.unitType && Vars.player.unit().spawnedByCore(), "Advanced respawn failed");
        check(advanced.unitType.buildSpeed > initial.unitType.buildSpeed
                && advanced.unitType.itemCapacity > initial.unitType.itemCapacity
                && advanced.unitType.mineTier >= CryonItems.nickel.hardness
                && advanced.unitType.mineItems.contains(CryonItems.nickel), "Advanced engineering stats invalid");
        check(advanced.unitType.coreUnitDock && !advanced.unitType.isEnemy, "Core unit flags missing");
        checkRepairAttack(initial.unitType);
        checkRepairAttack(advanced.unitType);
        System.out.println("PASS: core upgrade placement, both player respawns and advanced nickel mining");
    }

    static void checkRepairAttack(mindustry.type.UnitType unit){
        Tile friend = Vars.world.tile(105, 30), enemy = Vars.world.tile(110, 30);
        friend.setBlock(Blocks.copperWall, Vars.state.rules.defaultTeam, 0);
        enemy.setBlock(Blocks.copperWall, mindustry.game.Team.crux, 0);
        for(mindustry.type.Weapon weapon : unit.weapons){
            mindustry.entities.bullet.BulletType type = weapon.bullet;
            check(type.collidesTeam && type.heals() && type.damage > 0, "Repair/combat properties missing");
            mindustry.gen.Bullet shot = mindustry.gen.Bullet.create();
            shot.type = type; shot.team = friend.team(); shot.damage = type.damage;
            friend.build.health = friend.build.maxHealth / 2f;
            float before = friend.build.health;
            type.hitTile(shot, friend.build, friend.worldx(), friend.worldy(), before, false);
            check(friend.build.health > before, "Attack did not heal allied building");
            enemy.build.health = 1f;
            enemy.build.collision(shot);
            type.hitTile(shot, enemy.build, enemy.worldx(), enemy.worldy(), 1f, true);
            check(enemy.build != null && enemy.build.health == 1f, "Attack damaged/destroyed enemy building");
        }
        System.out.println("PASS: " + unit.name + " repairs allies and cannot destroy a 1 HP enemy building");
    }

    static void preview() throws Exception {
        BufferedImage image = new BufferedImage(768, 768, BufferedImage.TYPE_INT_RGB);
        for(Tile tile : Vars.world.tiles){
            int rgb = tile.floor().mapColor.rgb888();
            if(tile.block().solid) rgb = tile.build == null ? 0x584d40 : 0x84f491;
            if(tile.overlay().itemDrop != null) rgb = tile.overlay().itemDrop.color.rgb888();
            if(tile.overlay() == Blocks.spawn) rgb = 0xff5555;
            for(int dx=0;dx<4;dx++) for(int dy=0;dy<4;dy++)
                image.setRGB(tile.x*4+dx, (191-tile.y)*4+dy, rgb);
        }
        Fi output = new Fi("build/reports/aravis-desert-preview.png"); output.parent().mkdirs();
        ImageIO.write(image, "png", output.file());
    }
}
