import arc.util.Time;
import cryon.Unity.*;
import cryon.Type.*;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.entities.bullet.*;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.blocks.defense.turrets.*;

/** Exercise actual descendant creation rather than only inspecting merged fields. */
public class UnityFusionCheck{
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  UnityTurretCheck.main(args);Time.delta=1f;
  PowerTurret primary=(PowerTurret)Vars.content.block("cryon-gem");
  ItemTurret ingredient=(ItemTurret)Vars.content.block("cryon-spark");
  BulletType original=ingredient.ammoTypes.get(Items.copper);
  LaserBulletType laser=new LaserBulletType(20);laser.length=140;laser.init();
  original.fragBullet=laser;original.fragBullets=2;original.fragOnHit=true;original.fragRandomSpread=0;original.fragSpread=0;
  original.pierce=true;original.pierceCap=3;original.homingPower=0.12f;original.homingRange=100;
  original.status=StatusEffects.corroded;original.statusDuration=120;
  primary.shootType.status=StatusEffects.slow;primary.shootType.statusDuration=120;
  original.intervalBullet=new LightningBulletType();original.intervalBullet.damage=7;original.intervalBullet.init();
  original.intervalBullets=1;original.bulletInterval=4;
  UnityBlueprint design=new UnityBlueprint();design.targeting=1;
  design.modules.add(new UnityBlueprint.Module(primary.name));
  UnityBlueprint.Module second=new UnityBlueprint.Module(ingredient.name);second.ammo="copper";design.modules.add(second);
  int ids=Vars.content.bullets().size;UnityFusion fusion=new UnityFusion(design);
  check(fusion.bullet.pierce && fusion.bullet.pierceCap==3,"finite piercing trait changed to unlimited");
  check(fusion.bullet.homingPower>=0.12f,"homing trait lost");
  check(fusion.bullet.fragBullet instanceof UnityHybridBullet && ((UnityHybridBullet)fusion.bullet.fragBullet).carrier instanceof LaserBulletType,"laser fragments became ordinary shards");
  check(fusion.bullet.damage<primary.shootType.damage+original.damage,"root damage is a plain sum");
  UnityTurret block=(UnityTurret)Vars.content.block("unity-probe");
  Vars.world.tile(120,30).setBlock(block.finishedBlock,Team.sharded);
  UnityTurret.UnityBuild turret=(UnityTurret.UnityBuild)Vars.world.build(120,30);turret.install(design);
  turret.items.add(Items.copper,10);turret.power.status=1;turret.fusionReload=turret.fusion.reload;
  Unit enemy=UnitTypes.dagger.create(Team.crux);enemy.set(turret.x+80,turret.y);enemy.dead=false;enemy.maxHealth=10000;enemy.health=10000;enemy.add();enemy.team.data().tree().insert(enemy);Vars.state.teams.updateTeamStats();
  turret.updateTile();check(turret.totalFusedShots==1,"fusion did not emit exactly one attack cycle");
  Bullet root=null;int roots=0;
  for(Bullet b:Groups.bullet)if(b.owner==turret && b.type==turret.fusion.bullet){root=b;roots++;}
  check(root!=null && roots==1,"parallel primary projectiles emitted");
  root.type.hit(root,root.x+20,root.y);
  int lasers=0;for(Bullet b:Groups.bullet)if(b.owner==turret && b.type==turret.fusion.bullet.fragBullet)lasers++;
  check(lasers==2,"inherited impact did not actually split into two laser bullets: "+lasers);
  root.time=10;Time.time+=100;root.type.update(root);
  int bolts=0;for(Bullet b:Groups.bullet)if(b.owner==turret && b.type==turret.fusion.bullet.intervalBullet)bolts++;
  check(bolts>0,"inherited interval lightning was not emitted");
  root.type.hitEntity(root,enemy,enemy.health);
  check(enemy.hasEffect(StatusEffects.slow) && enemy.hasEffect(StatusEffects.corroded),"combined statuses were not both applied");
  check(original.fragBullet==laser && primary.shootType.damage==50,"fusion modified a source turret");
  check(Vars.content.bullets().size==ids,"fusion added runtime content IDs");
  // Shared minfo alone must never make a vanilla or another-planet turret eligible.
  Blocks.duo.minfo.mod=primary.minfo.mod;
  check(!UnityBlueprint.eligible(Blocks.duo),"vanilla turret entered catalog through modified metadata");
  PowerTurret foreign=new PowerTurret("cryon-afterglow-cannon");foreign.minfo.mod=primary.minfo.mod;
  foreign.requirements(Category.turret,ItemStack.with(Items.copper,1));foreign.shootType=new BasicBulletType(4,5);foreign.init();
  check(!UnityBlueprint.eligible(foreign),"Aravis turret entered Cryon catalog");
  UnityBlueprint legacy=new UnityBlueprint();legacy.modules.add(new UnityBlueprint.Module(foreign.name));
  boolean rejected=false;try{UnityBlueprint.decode(legacy.encode());}catch(IllegalArgumentException expected){rejected=true;}
  check(rejected,"new foreign recipe accepted");check(UnityBlueprint.decodeStored(legacy.encode()).modules.size==1,"legacy foreign recipe broke world loading");
  primary.targetAir=true;primary.targetGround=false;ingredient.targetAir=false;ingredient.targetGround=true;
  UnityBlueprint voted=new UnityBlueprint();voted.modules.addAll(design.modules);voted.modules.add(design.modules.first());
  UnityFusion airMajority=new UnityFusion(voted);
  check(airMajority.targetAir && !airMajority.targetGround,"air majority did not win");
  check(airMajority.bullet.collidesAir && !airMajority.bullet.collidesGround && !airMajority.bullet.collidesTiles,"root collision ignored vote");
  check(airMajority.bullet.fragBullet.collidesAir && !airMajority.bullet.fragBullet.collidesGround,"laser fragments ignored vote");
  check(airMajority.bullet.intervalBullet.collidesAir && !airMajority.bullet.intervalBullet.collidesGround,"interval bullets ignored vote");
  voted.modules.set(2,second);UnityFusion groundMajority=new UnityFusion(voted);
  check(!groundMajority.targetAir && groundMajority.targetGround && groundMajority.bullet.collidesTiles,"ground majority did not win");
  UnityFusion tied=new UnityFusion(design);check(tied.targetAir && tied.targetGround,"tie did not retain both target types");
  primary.targetAir=primary.targetGround=ingredient.targetAir=ingredient.targetGround=true;
  check(original.collidesGround,"voting mutated source bullets");
  System.out.println("PASS: air majority, ground majority, tie, descendant collision filtering and source isolation");
  UnityBlueprint chargedDesign=new UnityBlueprint();chargedDesign.modules.add(new UnityBlueprint.Module(primary.name));
  primary.shoot.firstShotDelay=2.5f;turret.install(chargedDesign);turret.power.status=0.7f;
  for(int tick=0;tick<100;tick++){Time.time++;turret.updateTile();}
  check(turret.totalFusedShots>0,"fractional charge completion restarted forever");
  check(turret.mounts.first().build.isShooting,"native firing state was not synchronized");
  primary.shoot.firstShotDelay=0f;turret.install(design);turret.power.status=1f;
  System.out.println("PASS: fractional charge countdown completes and native firing state stays synchronized");
  // Required heat and optional heat boosts must work without native weapon updates.
  primary.heatRequirement=8f;
  turret.install(design);turret.power.status=1f;turret.items.add(Items.copper,10);
  check(turret.heatRequirement()==8f,"required heat was not exposed to heat network");
  turret.fusionHeat=0f;turret.mounts.first().prepare(enemy,true);
  check(!turret.mounts.first().build.canConsume(),"required heat did not gate firing");
  turret.fusionHeat=8f;turret.mounts.first().prepare(enemy,true);
  check(turret.mounts.first().build.canConsume() && turret.mounts.first().build.heatReq==8f,"required heat did not reach native consumer");
  mindustry.world.blocks.heat.HeatProducer heater=new mindustry.world.blocks.heat.HeatProducer("fusion-heater-probe");heater.init();
  Vars.world.tile(turret.tile.x-3,turret.tile.y).setBlock(heater,Team.sharded,0);
  mindustry.world.blocks.heat.HeatProducer.HeatProducerBuild heaterBuild=(mindustry.world.blocks.heat.HeatProducer.HeatProducerBuild)Vars.world.build(turret.tile.x-3,turret.tile.y);
  heaterBuild.heat=8f;turret.updateProximity();turret.updateTile();
  check(turret.fusionHeat>0f && turret.mounts.first().build.heatReq>0f,"adjacent heater did not supply fused turret");
  primary.heatRequirement=-1f;
  cryon.Type.Constructor.ConstructorTurret construct=new cryon.Type.Constructor.ConstructorTurret("fusion-constructor-probe");
  construct.constructorUse=3f;construct.shootType=new BasicBulletType(4f,10f);construct.init();
  UnityBlueprint constructDesign=new UnityBlueprint();constructDesign.modules.add(new UnityBlueprint.Module(construct.name));
  turret.install(constructDesign);turret.constructorStatus(0f);turret.mounts.first().prepare(enemy,true);
  check(turret.constructorUse()==3f && !turret.mounts.first().build.hasAmmo(),"constructor demand or outage gate lost");
  turret.constructorStatus(0.5f);turret.mounts.first().prepare(enemy,true);
  check(turret.mounts.first().build.hasAmmo(),"constructor supply was not forwarded");
  cryon.Injector.HeatBoostTurret boost=new cryon.Injector.HeatBoostTurret("fusion-heat-probe");
  boost.heatCapacity=4f;boost.maxHeatBoost=1f;boost.ammo(Items.copper,new BasicBulletType(4f,10f));boost.init();
  UnityBlueprint boostDesign=new UnityBlueprint();UnityBlueprint.Module heatModule=new UnityBlueprint.Module(boost.name);heatModule.ammo="copper";boostDesign.modules.add(heatModule);
  turret.install(boostDesign);turret.fusionHeat=4f;turret.mounts.first().prepare(enemy,true);
  cryon.Injector.HeatBoostTurret.HeatBoostTurretBuild heatBuild=(cryon.Injector.HeatBoostTurret.HeatBoostTurretBuild)turret.mounts.first().build;
  check(turret.heatRequirement()==4f && heatBuild.fusionHeatBoost()==1f,"optional heat boost lost");
  System.out.println("PASS: required heat gates, heat allocation, optional heat boost, constructor demand and supply forwarding");
  System.out.println("PASS: one carrier, nonlinear damage, finite piercing, homing, real laser fragmentation, real interval lightning, combined statuses, source isolation, stable IDs, strict Cryon catalog and legacy save compatibility");
 }
}
