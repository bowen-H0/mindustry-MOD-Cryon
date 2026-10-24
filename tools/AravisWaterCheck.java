import arc.Core;
import arc.Settings;
import arc.files.Fi;
import arc.mock.*;
import arc.util.I18NBundle;
import arc.struct.StringMap;
import cryon.Content.*;
import cryon.Generator.AravisPlanetGenerator;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.ctype.ContentType;
import mindustry.game.*;
import mindustry.io.*;
import mindustry.mod.*;
import mindustry.net.Net;
import mindustry.type.*;
import mindustry.world.*;
import java.util.ArrayDeque;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Headless integration checks for optional Aravis water cooling and surface-water pumping. */
public class AravisWaterCheck {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void near(float actual,float expected,String message){check(Math.abs(actual-expected)<0.001f,message+": "+actual);}
    public static void main(String[] args){try{run();System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
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
                AravisWaterCheck.class.getClassLoader(), meta);
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
        for(String folder : new String[]{"liquid/aravis", "production/aravis", "units/aravis", "turrets/aravis", "power/aravis"})
            for(Fi f : new Fi("content/blocks/"+folder).list())
                parser.parse(mod, f.nameWithoutExtension(), f.readString(), f, ContentType.block);
        Vars.content.setCurrentMod(mod);
        cryon.Type.MultiRecipeCrafter furnace=new cryon.Type.MultiRecipeCrafter("water-test-furnace");
        furnace.waterCooling=new cryon.Type.WaterCooling(0.1f,1.5f);
        furnace.hasItems=true; furnace.hasPower=true; furnace.consumePower(1f);
        furnace.recipes.add(new cryon.Type.MultiRecipeCrafter.CraftRecipe(
            ItemStack.with(Items.copper,1),ItemStack.with(Items.lead,1),60f));
        Vars.content.setCurrentMod(null);
        parser.finishParsing();
        Vars.content.init(); Vars.logic = new Logic();


        arc.util.Time.delta=1f;
        Vars.world.loadGenerator(64,64,tiles -> {
            tiles.fill();
            for(Tile t:tiles)t.setFloor(Blocks.sand.asFloor());
            tiles.get(4,4).setBlock(Vars.content.block("cryon-core-conquest"),Team.sharded);
        });
        Vars.state.rules.unitCap=100;
        var coal=(mindustry.world.blocks.power.ConsumeGenerator)Vars.content.block("cryon-coal-burner");
        var productionDrill=Vars.content.block("cryon-extension-drill");
        var factory=Vars.content.block("cryon-mech-unit-factory");
        var largeSolar=(mindustry.world.blocks.power.SolarGenerator)Vars.content.block("cryon-aeolian-large-solar-panel");
        check(coal.powerProduction<productionDrill.consPower.usage,"One coal generator still covers advanced mining");
        check(factory.consPower.usage>=coal.powerProduction*4,"Factory lacks industrial power demand");
        check(largeSolar.powerProduction<factory.consPower.usage,"Solar alone trivializes industrial power");

        // A surface pump cannot be placed on dry sand or produce water there.
        var pumpBlock=(mindustry.world.blocks.production.Pump)Vars.content.block("cryon-aeolian-pump");
        Tile pt=Vars.world.tile(10,10);
        check(!pumpBlock.canPlaceOn(pt,Team.sharded,0),"Pump permits dry-land placement");
        pt.setBlock(pumpBlock,Team.sharded);
        var dryPump=(mindustry.world.blocks.production.Pump.PumpBuild)pt.build;
        dryPump.power.status=1f;dryPump.updateConsumption();dryPump.updateTile();
        near(dryPump.liquids.get(Liquids.water),0f,"Pump extracted groundwater on dry land");
        pt.setAir();
        for(int dx=-1;dx<=1;dx++) for(int dy=-1;dy<=1;dy++)
            Vars.world.tile(10+dx,10+dy).setFloor(Blocks.water.asFloor());
        check(pumpBlock.size==3,"Pump must occupy 3x3 tiles");
        check(pumpBlock.canPlaceOn(pt,Team.sharded,0),"Pump rejects surface water");
        pt.setBlock(pumpBlock,Team.sharded);
        var pump=(mindustry.world.blocks.production.Pump.PumpBuild)pt.build;
        pump.power.status=1f;pump.updateConsumption();pump.updateTile();
        near(pump.liquids.get(Liquids.water),0.3f,"Surface-water pump output incorrect");
        for(String name:new String[]{"aeolian-conduit","aeolian-liquid-router","aeolian-liquid-junction","aeolian-liquid-bridge"})
            check(Vars.content.block("cryon-"+name)!=null,"Missing water transport: "+name);
        Tile ft=Vars.world.tile(20,20);ft.setBlock(furnace,Team.sharded);
        var smelter=(cryon.Type.MultiRecipeCrafter.MultiRecipeCrafterBuild)ft.build;
        smelter.currentRecipe=0; smelter.items.add(Items.copper,10);smelter.power.status=1f;
        smelter.updateConsumption();smelter.updateTile();float dry=smelter.progress;
        near(smelter.efficiency,1f,"Dry furnace stopped");
        smelter.liquids.add(Liquids.water,10);smelter.progress=0;
        smelter.updateConsumption();smelter.updateTile();near(smelter.progress/dry,1.5f,"Furnace boost missing");
        near(smelter.liquids.get(Liquids.water),9.9f,"Wrong cooling consumption");
        smelter.liquids.clear();smelter.updateConsumption();near(smelter.efficiency,1f,"Water loss stopped furnace");
        smelter.liquids.add(Liquids.water,10);smelter.items.clear();smelter.updateConsumption();
        near(smelter.liquids.get(Liquids.water),10f,"Idle furnace wasted water");
        smelter.items.add(Items.copper,10);smelter.power.status=0;smelter.updateConsumption();
        near(smelter.liquids.get(Liquids.water),10f,"Unpowered furnace wasted water");
        smelter.power.status=1;smelter.liquids.clear();smelter.liquids.add(Liquids.water,0.05f);
        smelter.updateConsumption();near(smelter.efficiency,1.25f,"Partial water boost incorrect");
        check(smelter.liquids.get(Liquids.water)>=0,"Negative water");
        for(String name:new String[]{"mech-unit-factory","mech-unit-reconstructor","advanced-mech-reconstructor"}){
            Tile t=Vars.world.tile(40,40);t.setBlock(Vars.content.block("cryon-"+name),Team.sharded);
            var b=t.build;check(b.block.hasLiquids,"Missing liquid module "+name);
            near(b.efficiencyScale(),1f,"Dry speed incorrect "+name);
            b.liquids.add(Liquids.water,20);near(b.efficiencyScale(),1.5f,"Wet speed incorrect "+name);
            // Exercise native factory/reconstructor progress rather than only the multiplier.
            for(Item item:Vars.content.items()) b.items.set(item,1000);
            if(b instanceof mindustry.world.blocks.units.UnitFactory.UnitFactoryBuild){
                ((mindustry.world.blocks.units.UnitFactory.UnitFactoryBuild)b).currentPlan=0;
            }else{
                var recon=(mindustry.world.blocks.units.Reconstructor.ReconstructorBuild)b;
                var type=((mindustry.world.blocks.units.Reconstructor)b.block).upgrades.first()[0];
                recon.payload=new mindustry.world.blocks.payloads.UnitPayload(type.create(Team.sharded));
            }
            var unitBuild=(mindustry.world.blocks.units.UnitBlock.UnitBuild)b;
            b.power.status=1f;unitBuild.speedScl=1f;b.liquids.clear();b.updateTile();
            unitBuild.progress=0;b.updateConsumption();b.updateTile();float dryProgress=unitBuild.progress;
            check(dryProgress>0,"Dry production failed "+name);
            unitBuild.progress=0;b.liquids.add(Liquids.water,20);b.updateConsumption();b.updateTile();
            near(unitBuild.progress/dryProgress,1.5f,"Native production boost failed "+name);
            b.enabled=false;b.power.status=1;float before=b.liquids.get(Liquids.water);b.updateConsumption();
            near(b.liquids.get(Liquids.water),before,"Disabled building wasted water "+name);
            t.setAir();
        }
        for(String name:new String[]{"aeolian-drill","extension-drill"}){
            var drill=(mindustry.world.blocks.production.Drill)Vars.content.block("cryon-"+name);
            check(drill.optionalConsumers.length>0 && drill.consumesLiquid(Liquids.water),"Drill cooling missing "+name);
            near(drill.liquidBoostIntensity*drill.liquidBoostIntensity,1.5f,"Drill cooling bypasses slow production "+name);
        }
        for(String name:new String[]{"fusillade","chisel","saxum","fractura","volans","arche","afterglow-cannon"}){
            var turret=(mindustry.world.blocks.defense.turrets.Turret)Vars.content.block("cryon-"+name);
            check(turret.coolant!=null && turret.coolant.optional && turret.consumesLiquid(Liquids.water),"Turret cooling missing "+name);
        }
        smelter.power.status=1f;smelter.liquids.add(Liquids.water,10f);
        smelter.items.set(Items.lead,furnace.itemCapacity);
        float fullWater=smelter.liquids.get(Liquids.water);smelter.updateConsumption();
        near(smelter.liquids.get(Liquids.water),fullWater,"Full output wasted water");
        smelter.items.clear();smelter.items.add(Items.copper,10);
        SaveIO.write(new Fi("/tmp/aravis-water-check.msav"));
        SaveIO.load(new Fi("/tmp/aravis-water-check.msav"));
        var restored=(cryon.Type.MultiRecipeCrafter.MultiRecipeCrafterBuild)Vars.world.build(20,20);
        check(restored.currentRecipe==0 && restored.liquids.get(Liquids.water)>0,"Save lost recipe/water");
        Vars.world.tile(12,10).setBlock(Vars.content.block("cryon-aeolian-conduit"),Team.sharded,0);
        Vars.world.tile(13,10).setBlock(Vars.content.block("cryon-aeolian-liquid-router"),Team.sharded);
        Vars.world.tile(14,10).setBlock(furnace,Team.sharded);
        var suppliedFurnace=(cryon.Type.MultiRecipeCrafter.MultiRecipeCrafterBuild)Vars.world.build(14,10);
        suppliedFurnace.currentRecipe=0;suppliedFurnace.items.add(Items.copper,100);
        boolean delivered=false;
        for(int tick=0;tick<360;tick++){
            arc.util.Time.time+=1f;
            for(int x:new int[]{10,12,13,14}){
                var b=Vars.world.build(x,10);if(b.power!=null)b.power.status=1f;
                b.updateConsumption();b.updateTile();
            }
            delivered |= suppliedFurnace.efficiency>1.001f;
        }
        check(delivered,"Pump/conduit/router failed to deliver cooling water");
        var repairBlock=(mindustry.world.blocks.units.RepairTower)Vars.content.block("cryon-aravis-unit-repairer");
        Vars.world.tile(35,15).setBlock(repairBlock,Team.sharded);
        var repair=(mindustry.world.blocks.units.RepairTower.RepairTowerBuild)Vars.world.build(35,15);
        var type=Vars.content.unit("cryon-jaspis");
        var ally=type.create(Team.sharded);ally.set(repair.x+16,repair.y);ally.health=100;ally.add();
        var enemy=type.create(Team.malis);enemy.set(repair.x+20,repair.y);enemy.health=100;enemy.add();
        var outside=type.create(Team.sharded);outside.set(repair.x+repairBlock.range+24,repair.y);outside.health=100;outside.add();
        Team.sharded.data().tree().insert(ally);Team.sharded.data().tree().insert(outside);
        Team.malis.data().tree().insert(enemy);
        repair.power.status=1;repair.refresh=6;repair.updateConsumption();repair.updateTile();
        check(repair.targets.contains(ally) && !repair.targets.contains(enemy) && !repair.targets.contains(outside),"Repair target filtering incorrect");
        float health=ally.health;repair.updateConsumption();repair.updateTile();
        near(ally.health-health,0.6f,"Dry repair speed incorrect");
        repair.liquids.add(Liquids.water,10);health=ally.health;
        repair.updateConsumption();repair.updateTile();near(ally.health-health,0.9f,"Water-cooled repair speed incorrect");
        near(enemy.health,100,"Repairer healed enemy");near(outside.health,100,"Repairer healed outside radius");
        repair.power.status=0;health=ally.health;float stored=repair.liquids.get(Liquids.water);
        repair.updateConsumption();repair.updateTile();near(ally.health,health,"Unpowered repairer healed unit");
        near(repair.liquids.get(Liquids.water),stored,"Unpowered repairer wasted water");
        repair.power.status=1;repair.targets.clear();repair.refresh=0;repair.updateConsumption();
        near(repair.liquids.get(Liquids.water),stored,"Idle repairer wasted water");
        System.out.println("PASS: repair targets, dry/wet healing, range, enemy exclusion and power loss");
        var turbineBlock=(mindustry.world.blocks.power.ConsumeGenerator)Vars.content.block("cryon-aeolian-turbine-generator");
        Vars.world.tile(46,15).setBlock(turbineBlock,Team.sharded);
        var turbine=(mindustry.world.blocks.power.ConsumeGenerator.ConsumeGeneratorBuild)Vars.world.build(46,15);
        turbine.updateConsumption();turbine.updateTile();near(turbine.getPowerProduction(),0,"Empty turbine generated power");
        turbine.liquids.add(Liquids.water,40);turbine.updateConsumption();turbine.updateTile();
        near(turbine.getPowerProduction(),0,"Turbine ran without coal");
        turbine.items.add(Items.coal,10);float waterBefore=turbine.liquids.get(Liquids.water);
        turbine.updateConsumption();turbine.updateTile();near(turbine.getPowerProduction()*60f,360f,"Turbine rated output incorrect");
        near(waterBefore-turbine.liquids.get(Liquids.water),0.2f,"Turbine water demand incorrect");
        check(turbine.items.get(Items.coal)<10,"Turbine consumed no coal");
        turbine.liquids.clear();int fuelBefore=turbine.items.get(Items.coal);
        turbine.updateConsumption();turbine.updateTile();near(turbine.getPowerProduction(),0,"Turbine ran without water");
        check(turbine.items.get(Items.coal)==fuelBefore,"Dry turbine wasted coal");
        turbine.liquids.add(Liquids.water,40);turbine.enabled=false;waterBefore=turbine.liquids.get(Liquids.water);
        turbine.updateConsumption();turbine.updateTile();near(turbine.getPowerProduction(),0,"Disabled turbine generated power");
        near(turbine.liquids.get(Liquids.water),waterBefore,"Disabled turbine wasted water");
        turbine.enabled=true;turbine.items.clear();
        for(int i=0;i<120;i++){turbine.liquids.add(Liquids.water,1);turbine.updateConsumption();turbine.updateTile();}
        near(turbine.getPowerProduction(),0,"Exhausted turbine kept generating power");
        System.out.println("PASS: turbine rated output, resource consumption, fuel exhaustion, water shortage and disabled state");
        var scorched=(mindustry.world.blocks.defense.turrets.ItemTurret)Vars.content.block("cryon-afterglow-cannon");
        var shell=scorched.ammoTypes.get(CryonItems.ashesAlloy);
        check(shell.hitEffect instanceof cryon.Fx.AfterglowImpact,"Scorched Earth main effect not loaded");
        check(shell.fragBullet.hitEffect instanceof cryon.Fx.AfterglowImpact
            && ((cryon.Fx.AfterglowImpact)shell.fragBullet.hitEffect).small,"Submunition visual variant missing");
        near(shell.damage,40,"Scorched Earth direct damage changed");near(shell.splashDamage,100,"Scorched Earth splash damage changed");
        near(shell.fragBullet.splashDamage,35,"Submunition damage changed");
        check(shell.fragBullets==5 && scorched.shoot.shots==7,"Scorched Earth cluster count changed");
        near(scorched.reload,150,"Scorched Earth reload changed");
        System.out.println("PASS: Scorched Earth visual types and original combat values");
        var nitrum=Vars.content.unit("cryon-nitrum");
        check(nitrum.weapons.size==2,"Nitrum lost its twin flamethrowers");
        float flameDps=0;
        for(var weapon:nitrum.weapons){
            var flame=(mindustry.entities.bullet.ContinuousFlameBulletType)weapon.bullet;
            check(flame.length<100 && weapon.shootStatus!=StatusEffects.slow,"Nitrum close-assault specialization missing");
            flameDps+=flame.continuousDamage();
        }
        near(flameDps,1440f,"Nitrum twin flame DPS incorrect");
        var assault=nitrum.create(Team.sharded);assault.set(240,240);assault.add();
        var closeTarget=UnitTypes.reign.create(Team.malis);closeTarget.set(290,240);closeTarget.add();
        var distantTarget=UnitTypes.reign.create(Team.malis);distantTarget.set(400,240);distantTarget.add();
        Team.malis.data().tree().insert(closeTarget);Team.malis.data().tree().insert(distantTarget);
        var flame=(mindustry.entities.bullet.ContinuousFlameBulletType)nitrum.weapons.first().bullet;
        Vars.state.teams.updateTeamStats();
        var shot=flame.create(assault,Team.sharded,assault.x,assault.y,0f);
        shot.time=30f;
        float closeHealth=closeTarget.health,farHealth=distantTarget.health;
        flame.applyDamage(shot);
        check(closeTarget.health<closeHealth,"Nitrum flame failed to hurt nearby armored target");
        near(distantTarget.health,farHealth,"Nitrum flame reached distant target");
        System.out.println("PASS: Nitrum twin flame DPS, close armored target damage and range boundary");
        Vars.mods.getMods().add(mod);
        cryon.TechTree.AravisTechTree.load();
        for(String name:new String[]{"aeolian-pump","aeolian-conduit","aeolian-liquid-router","aeolian-liquid-junction","aeolian-liquid-bridge"})
            check(Vars.content.block("cryon-"+name).techNode!=null,"Missing research "+name);
        check(repairBlock.techNode!=null,"Unit repairer research missing");
        check(turbineBlock.techNode!=null,"Turbine research missing");
        System.out.println("PASS: native HJSON types, surface-water pumping and dry-land rejection, transport, dry/wet/partial cooling, idle and power loss, factories, drills and seven turrets");
    }
}
