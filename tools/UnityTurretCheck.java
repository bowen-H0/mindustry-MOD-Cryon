import arc.*;
import arc.files.*;
import arc.mock.*;
import arc.util.*;
import mindustry.*;
import mindustry.net.*;
import mindustry.gen.*;
import mindustry.game.*;
import mindustry.ai.*;
import mindustry.ai.types.*;
import cryon.Type.SpaceJumpAbility;
import cryon.Type.UnityTurret;
import cryon.Unity.*;
import cryon.Content.CryonItems;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.type.*;
import mindustry.entities.bullet.*;
import arc.graphics.Color;
import java.io.*;
public class UnityTurretCheck{
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  Core.app=new MockApplication(); Core.files=new MockFiles(); Core.graphics=new MockGraphics(); Core.audio=new MockAudio();
  Core.settings=new Settings(); Core.settings.setDataDirectory(new Fi("/tmp/space-jump-check")); Core.bundle=I18NBundle.createEmptyBundle();
  Vars.headless=true; Vars.loadLocales=false; Vars.net=new Net(null); Vars.platform=new mindustry.core.Platform(){};
  java.lang.reflect.Field maps=mindustry.maps.Maps.class.getDeclaredField("defaultMapNames");
  maps.setAccessible(true);maps.set(null,new String[0]);
  Vars.init(); UnitCommand.loadAll();mindustry.content.TeamEntries.load();mindustry.content.Items.load();
  UnitStance.loadAll();mindustry.content.StatusEffects.load();mindustry.content.Liquids.load();
  mindustry.content.Bullets.load();mindustry.content.UnitTypes.load();mindustry.content.Blocks.load();
  mindustry.content.Loadouts.load();mindustry.content.Weathers.load();mindustry.content.Planets.load(); Vars.content.init();
  Vars.world.loadGenerator(300,100, tiles -> {for(int x=0;x<300;x++)for(int y=0;y<100;y++)tiles.set(x,y,new mindustry.world.Tile(x,y,mindustry.content.Blocks.sand,mindustry.content.Blocks.air,mindustry.content.Blocks.air));});
  Time.delta=1f;


  CryonItems.load();
  mindustry.mod.Mods.ModMeta meta=new mindustry.mod.Mods.ModMeta();meta.name="cryon";meta.internalName="cryon";
  mindustry.mod.Mods.LoadedMod mod=new mindustry.mod.Mods.LoadedMod(new Fi("/tmp/unity-test"),new Fi("/tmp/unity-test"),new mindustry.mod.Mod(){},UnityTurretCheck.class.getClassLoader(),meta);
  PowerTurret gun=new PowerTurret("cryon-gem");gun.minfo.mod=mod;
  gun.requirements(Category.turret,ItemStack.with(mindustry.content.Items.copper,100));
  gun.range=200;gun.reload=4;gun.rotateSpeed=360;gun.shootCone=360;gun.shootType=new BasicBulletType(4,50);gun.shootType.lifetime=50;gun.consumePower(2);gun.init();
  ItemTurret item=new ItemTurret("cryon-spark");item.minfo.mod=mod;
  item.requirements(Category.turret,ItemStack.with(mindustry.content.Items.copper,100));
  item.range=200;item.reload=4;item.rotateSpeed=360;item.shootCone=360;
  item.ammo(mindustry.content.Items.copper,new BasicBulletType(4,20),mindustry.content.Items.lead,new BasicBulletType(4,25));item.init();
  UnityTurret block=new UnityTurret("unity-probe");block.init();block.finishedBlock.init();block.construction.init();
  Vars.world.tile(30,30).setBlock(mindustry.content.Blocks.coreShard,Team.sharded);
  mindustry.gen.Building core=Vars.world.build(30,30);core.items.add(mindustry.content.Items.copper,10000);
  Vars.world.tile(50,30).setBlock(block,Team.sharded);
  UnityTurret.UnityBuild build=(UnityTurret.UnityBuild)Vars.world.build(50,30);
  check(build.mounts.isEmpty(),"empty platform has a weapon");
  UnityBlueprint recipe=new UnityBlueprint();recipe.name="combined probe";recipe.color="70FFD5";recipe.targeting=1;
  recipe.modules.add(new UnityBlueprint.Module(gun.name));
  UnityBlueprint.Module itemModule=new UnityBlueprint.Module(item.name);itemModule.ammo="copper";recipe.modules.add(itemModule);
  recipe=UnityBlueprint.decode(recipe.encode());
  check(recipe.cost()[0].amount==240,"fusion surcharge not applied");
  UnityBlueprint triple=recipe.copy();triple.modules.add(new UnityBlueprint.Module(gun.name));
  check(triple.cost()[0].amount>recipe.cost()[0].amount,"additional modules not more expensive");
  int bulletCount=Vars.content.bullets().size;Color nativeColor=((BasicBulletType)gun.shootType).backColor.cpy();
  build.begin(recipe);
  check(core.items.get(mindustry.content.Items.copper)==10000,"selecting a recipe charged upfront");
  Unit worker=mindustry.content.UnitTypes.poly.create(Team.sharded);worker.set(build.x+1000,build.y);worker.dead=false;worker.add();
  build.assignBuilder(worker);worker.updateBuildLogic();
  check(build.tile.build==build,"out-of-range plan began construction");
  worker.set(build.x+35,build.y);worker.updateBuildLogic();
  mindustry.world.Tile location=build.tile;
  check(location.build instanceof cryon.Type.UnityConstruction.Site,"native placement did not create fusion construction site");
  cryon.Type.UnityConstruction.Site site=(cryon.Type.UnityConstruction.Site)location.build;
  check(site.progress>0 && site.progress<1f,"native construction did not progress");
  for(int i=0;i<20;i++){Time.time++;worker.updateBuildLogic();site.updateTile();}
  check(core.items.get(mindustry.content.Items.copper)<10000 && core.items.get(mindustry.content.Items.copper)>9760,"materials not consumed progressively");
  float stopped=site.progress;worker.clearBuilding();
  for(int i=0;i<20;i++){Time.time++;site.updateTile();worker.updateBuildLogic();}
  check(site.progress==stopped,"construction advanced without active plan");
  ByteArrayOutputStream siteBytes=new ByteArrayOutputStream();site.write(arc.util.io.Writes.get(new DataOutputStream(siteBytes)));
  cryon.Type.UnityConstruction.Site recovered=(cryon.Type.UnityConstruction.Site)block.construction.buildType.get();recovered.create(block.construction,Team.sharded);recovered.tile=location;
  recovered.read(arc.util.io.Reads.get(new DataInputStream(new ByteArrayInputStream(siteBytes.toByteArray()))),(byte)1);
  check(recovered.recipe.modules.size==2 && recovered.progress==stopped,"native construction save/load");
  worker.addBuild(new mindustry.entities.units.BuildPlan(location.x,location.y,0,block.finishedBlock,site.lastConfig),false);
  worker.updateBuildLogic();float warmed=site.progress;worker.updateBuildLogic();float first=site.progress-warmed;
  check(Math.abs(first-worker.type.buildSpeed/recipe.work())<0.00001f && first>0f,"native speed ignored");
  float before=site.progress;worker.buildSpeedMultiplier=2;worker.updateBuildLogic();
  check(Math.abs((site.progress-before)-first*2)<0.00001f,"native speed multiplier ignored");worker.buildSpeedMultiplier=1;
  for(int i=0;i<10000 && location.build instanceof cryon.Type.UnityConstruction.Site;i++){Time.time++;worker.updateBuildLogic();location.build.updateTile();}
  check(location.block()==block.finishedBlock,"upgrade did not replace block identity");
  build=(UnityTurret.UnityBuild)location.build;
  check(build.mounts.size==2 && !build.block.configurable,"finished turret armed or edit state incorrect");
  build.begin(triple);check(build.pending==null,"finished turret accepted an edit");
  worker.updateBuildLogic();check(!worker.isBuilding(),"native completed plan was not cleared");
  check(core.items.get(mindustry.content.Items.copper)==9760,"native total construction cost incorrect");
  check(!build.acceptItem(null,mindustry.content.Items.lead),"unselected ammo accepted");
  check(Vars.content.bullets().size==bulletCount,"private palette polluted content IDs");
  check(((BasicBulletType)gun.shootType).backColor.equals(nativeColor),"native palette mutated");
  Unit enemy=mindustry.content.UnitTypes.dagger.create(Team.crux);enemy.set(build.x+80,build.y);enemy.dead=false;enemy.add();enemy.team.data().tree().insert(enemy);Vars.state.teams.updateTeamStats();
  build.power.status=1;build.items.add(mindustry.content.Items.copper,20);
  for(int i=0;i<50;i++){Time.time++;build.updateTile();}
  check(build.totalFusedShots>0,"fused gun did not shoot");
  check(build.mounts.get(0).build.totalShots==0 && build.mounts.get(1).build.totalShots==0,"ingredients still fired independently");
  check(build.fusion.bullet.damage>20 && build.fusion.bullet.damage<70,"damage was summed instead of blended");
  int rootShots=0;for(Bullet shot:Groups.bullet)if(shot.owner==build && shot.type==build.fusion.bullet)rootShots++;
  check(rootShots==build.totalFusedShots,"more than one primary projectile per trigger");
  check(build.items.get(mindustry.content.Items.copper)<20,"native ammo not consumed");
  check(gun.shootType.damage==50,"source gun modified");
  Unit strong=mindustry.content.UnitTypes.dagger.create(Team.crux);strong.set(build.x+120,build.y);strong.maxHealth=2000;strong.health=1500;strong.dead=false;strong.add();strong.team.data().tree().insert(strong);Vars.state.teams.updateTeamStats();
  build.blueprint.targeting=2;
  for(int i=0;i<25;i++){Time.time++;build.updateTile();}
  check(Math.abs(build.mounts.first().build.targetPos.x-strong.x)<1f,"strongest target policy ignored");
  build.blueprint.targeting=3;
  for(int i=0;i<25;i++){Time.time++;build.updateTile();}
  check(Math.abs(build.mounts.first().build.targetPos.x-enemy.x)<1f,"weakest target policy ignored");
  build.applyBoost(2f,60f);build.updateTile();
  check(build.mounts.first().build.timeScale()==2f,"overdrive did not propagate");
  build.applySlowdown(1f,60f);build.updateTile();
  check(build.mounts.first().build.timeScale()==1f,"expired overdrive remained on module");
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();build.write(arc.util.io.Writes.get(new DataOutputStream(bytes)));
  UnityTurret.UnityBuild restored=(UnityTurret.UnityBuild)block.finishedBlock.buildType.get();restored.create(block.finishedBlock,Team.sharded);restored.tile=build.tile;restored.set(build.x,build.y);
  restored.read(arc.util.io.Reads.get(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))),build.version());
  check(restored.mounts.size==2 && restored.blueprint.color.equals("70FFD5"),"installed blueprint save/load");
  check(restored.mounts.get(1).build.totalAmmo==build.mounts.get(1).build.totalAmmo,"ammo save/load");
  arc.struct.Seq<UnityBlueprint> library=new arc.struct.Seq<>();library.add(recipe);UnityBlueprint.saveLibrary(library);
  check(UnityBlueprint.library().size==1,"local blueprint library save/load: "+Core.settings.getString("cryon-unity-library-v1"));
  // A liquid turret and a sustained laser retain their own ammo/charge behavior.
  LiquidTurret liquid=new LiquidTurret("cryon-torrent");liquid.minfo.mod=mod;liquid.range=200;liquid.reload=4;liquid.rotateSpeed=360;liquid.shootCone=360;
  liquid.requirements(Category.turret,ItemStack.with(mindustry.content.Items.copper,100));liquid.ammo(mindustry.content.Liquids.water,new LiquidBulletType(mindustry.content.Liquids.water));liquid.init();
  LaserTurret laser=new LaserTurret("cryon-cavity");laser.minfo.mod=mod;laser.range=300;laser.reload=30;laser.rotateSpeed=360;laser.shootCone=360;laser.requirements(Category.turret,ItemStack.with(mindustry.content.Items.copper,100));laser.shootType=new ContinuousLaserBulletType(100);laser.consumePower(3);laser.init();
  UnityBlueprint beams=new UnityBlueprint();beams.name="liquid and laser";beams.targeting=1;
  UnityBlueprint.Module water=new UnityBlueprint.Module(liquid.name);water.ammo="water";
  beams.modules.add(water);beams.modules.add(new UnityBlueprint.Module(laser.name));
  beams=UnityBlueprint.decode(beams.encode());build.install(beams);build.power.status=1;
  build.liquids.add(mindustry.content.Liquids.water,500f);
  float initialWater=build.liquids.get(mindustry.content.Liquids.water);
  for(int i=0;i<500;i++){Time.time++;build.updateTile();arc.util.Time.update();}
  check(build.totalFusedShots>0,"liquid/laser fusion did not shoot");
  check(build.fusion.range>liquid.range && build.fusion.range<laser.range,"long range ingredient not blended incrementally");
  check(build.fusion.bullet.intervalBullet!=null && ((cryon.Unity.UnityHybridBullet)build.fusion.bullet.intervalBullet).carrier instanceof ContinuousLaserBulletType,"laser trait was not inherited");
  check(build.liquids.get(mindustry.content.Liquids.water)<initialWater,"liquid supply not consumed");
  check(build.powerUse()>0f,"native power demand missing");
  boolean invalid=false;try{UnityBlueprint.decode("{color:invalid,modules:[]}");}catch(Exception expected){invalid=true;}
  check(invalid,"invalid palette accepted");
  // Legacy armed platforms change block identity without losing loaded ammo or inventories.
  Vars.world.tile(75,30).setBlock(block,Team.sharded);
  UnityTurret.UnityBuild legacy=(UnityTurret.UnityBuild)Vars.world.build(75,30);legacy.install(recipe);
  legacy.items.add(mindustry.content.Items.copper,17);legacy.liquids.add(mindustry.content.Liquids.water,31);
  legacy.mounts.get(1).build.handleItem(null,mindustry.content.Items.copper);
  int legacyAmmo=legacy.mounts.get(1).build.totalAmmo;legacy.updateTile();
  UnityTurret.UnityBuild converted=(UnityTurret.UnityBuild)Vars.world.build(75,30);
  check(converted.block==block.finishedBlock && converted.items.get(mindustry.content.Items.copper)==17 && converted.liquids.get(mindustry.content.Liquids.water)==31 && converted.mounts.get(1).build.totalAmmo==legacyAmmo,"legacy conversion lost turret state");
  Vars.world.tile(90,30).setBlock(block,Team.sharded);
  UnityTurret.UnityBuild oldPending=(UnityTurret.UnityBuild)Vars.world.build(90,30);oldPending.begin(recipe);oldPending.progress=0.25f;
  ByteArrayOutputStream legacyBytes=new ByteArrayOutputStream();oldPending.write(arc.util.io.Writes.get(new DataOutputStream(legacyBytes)));
  byte[] legacyPayload=java.util.Arrays.copyOf(legacyBytes.toByteArray(),legacyBytes.size()-16);
  core.items.remove(recipe.cost());int paidBalance=core.items.get(mindustry.content.Items.copper);
  oldPending.read(arc.util.io.Reads.get(new DataInputStream(new ByteArrayInputStream(legacyPayload))),(byte)1);
  oldPending.updateTile();check(core.items.get(mindustry.content.Items.copper)==paidBalance+recipe.cost()[0].amount,"legacy prepaid upgrade was not refunded before native consumption");
  int refundedBalance=core.items.get(mindustry.content.Items.copper);oldPending.updateTile();
  check(core.items.get(mindustry.content.Items.copper)==refundedBalance,"legacy refund repeated");
  worker.clearBuilding();worker.addBuild(new mindustry.entities.units.BuildPlan(build.tile.x,build.tile.y),false);
  worker.updateBuildLogic();
  check(location.build instanceof cryon.Type.UnityConstruction.Site && ((cryon.Type.UnityConstruction.Site)location.build).activeDeconstruct,"native finished-turret demolition did not start");
  System.out.println("PASS: empty platform, recipe validation, nonlinear costs, progressive core payment, native construction sites and queues, idle/cancel/range gating, speed multipliers, real block replacement, immutable finished turret, native power/item attacks, ammo consumption, palette isolation, stable content IDs, save/load, liquid/laser attacks, power demand and library persistence");
 }
}
