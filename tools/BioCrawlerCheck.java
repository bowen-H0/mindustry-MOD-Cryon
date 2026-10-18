import mindustry.gen.Groups;
import arc.*; import arc.files.*; import arc.mock.*; import arc.assets.*; import arc.util.*; import mindustry.*; import mindustry.mod.*; import mindustry.net.*; import cryon.CryonJavaMod;
public class BioCrawlerCheck {
public static class CountingBuild extends mindustry.gen.Building{
 int ticks;
 @Override public void updateTile(){ ticks++; }
}
public static void main(String[] args) throws Exception {
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
        Mods.LoadedMod mod = new Mods.LoadedMod(new Fi("."), new Fi("."), null, BioCrawlerCheck.class.getClassLoader(), meta);
        Vars.content.setCurrentMod(mod);


cryon.Content.BioCrawlerUnits.load();
cryon.Type.FluxBarrier barrier = new cryon.Type.FluxBarrier("check-flux");
Vars.content.setCurrentMod(null);
mindustry.world.Block probeBlock = new mindustry.world.Block("suppression-probe");
probeBlock.update = true;
probeBlock.destructible = true;
probeBlock.health = 100;
probeBlock.noUpdateDisabled = false;
probeBlock.buildType = CountingBuild::new;
Vars.content.init();
Vars.world.loadGenerator(80,80, tiles -> {
 for(int x = 0; x < 80; x++) for(int y = 0; y < 80; y++)
  tiles.set(x,y,new mindustry.world.Tile(x,y,mindustry.content.Blocks.sand,mindustry.content.Blocks.air,mindustry.content.Blocks.air));
});
Vars.state.rules.unitCap = 100;
Vars.state.rules.waveTeam = mindustry.game.Team.crux;
Vars.state.rules.defaultTeam = mindustry.game.Team.sharded;
Vars.logic = new mindustry.core.Logic();
String[] line = {"benignitas", "salus", "propugno", "ratio", "veritas"};
Class<?>[] nativeTypes = {mindustry.entities.bullet.BasicBulletType.class,
 mindustry.entities.bullet.BasicBulletType.class, mindustry.entities.bullet.LaserBulletType.class,
 mindustry.entities.bullet.ArtilleryBulletType.class, mindustry.entities.bullet.BasicBulletType.class};
int[] mounts = {3,5,7,9,13};
for(int tier = 0; tier < line.length; tier++){
 mindustry.type.UnitType type = Vars.content.unit("cryon-" + line[tier]);
 check(type.weapons.first().bullet.getClass() == nativeTypes[tier], "wrong main attack at tier " + (tier + 1));
 check(type.weapons.size == mounts[tier], "wrong physical mount count at tier " + (tier + 1));
 for(mindustry.type.Weapon weapon : type.weapons){
  check(!(weapon instanceof mindustry.type.weapons.PointDefenseWeapon), "point defense returned");
  check(weapon.bullet.hitColor.equals(cryon.Content.BioCrawlerUnits.orange), "non-orange projectile");
  if(weapon.bullet.collidesAir){
   check(!weapon.bullet.collidesGround && weapon.autoTarget && !weapon.controllable, "AA must independently target aircraft");
  }
  if(weapon.bullet.intervalBullet != null) check(weapon.bullet.intervalBullet.intervalBullet == null, "recursive interval storm");
 }
 check(type.abilities.size == 1 && type.abilities.first() instanceof cryon.Type.FluxSuppressionAbility, "obsolete support abilities remain");
 check((type.abilities.first() instanceof cryon.Type.GlobalSuppressionAbility) == (tier >= 2), "wrong suppression tier");
}
check(cryon.Content.BioCrawlerUnits.benignitas.weapons.find(w -> w.bullet.status == mindustry.content.StatusEffects.burning) != null, "missing flame mounts");
check(cryon.Content.BioCrawlerUnits.salus.weapons.first().bullet.intervalBullet != null, "orb must shed homing rounds");
check(Vars.content.unit("cryon-propugno").weapons.first().shoot.firstShotDelay > 0, "laser lacks charge phase");
check(Vars.content.unit("cryon-ratio").weapons.find(w -> w.bullet instanceof mindustry.entities.bullet.MissileBulletType) != null, "missing missile mounts");
mindustry.entities.bullet.BulletType heavyRound = Vars.content.unit("cryon-veritas").weapons.first().bullet;
check(heavyRound.pierceBuilding && heavyRound.intervalBullet instanceof mindustry.entities.bullet.LightningBulletType,
 "Smite-style charged helix must penetrate and emit lightning");
check(Vars.content.unit("cryon-veritas").weapons.find(w -> w.bullet.fragBullet instanceof mindustry.entities.bullet.LaserBulletType) != null, "missing Malign-style laser deployment");
// T5 suppression must reach beyond the previous hard-coded 160-unit query radius.
mindustry.gen.Unit elite = Vars.content.unit("cryon-veritas").create(mindustry.game.Team.sharded);
elite.set(300,300); elite.add();
Vars.state.teams.updateTeamStats();
elite.team.data().tree().clear(); elite.team.data().tree().insert(elite);
check(cryon.Type.FluxSuppressionAbility.suppressed(mindustry.game.Team.crux, 480,300), "T5 suppression truncated at 160");
check(!cryon.Type.FluxSuppressionAbility.suppressed(mindustry.game.Team.crux, 500,300), "T5 suppression outside radius");
elite.remove(); elite.team.data().tree().clear();
mindustry.gen.Unit source = cryon.Content.BioCrawlerUnits.salus.create(mindustry.game.Team.sharded);
source.set(200,200); source.add();
mindustry.gen.Unit enemy = mindustry.content.UnitTypes.dagger.create(mindustry.game.Team.crux);
enemy.set(225,200); enemy.add();
mindustry.gen.Unit ally = mindustry.content.UnitTypes.dagger.create(mindustry.game.Team.sharded);
ally.set(225,200); ally.add();
Vars.state.teams.updateTeamStats();
source.team.data().tree().clear(); source.team.data().tree().insert(source); source.team.data().tree().insert(ally);
enemy.team.data().tree().clear(); enemy.team.data().tree().insert(enemy);
check(cryon.Type.FluxSuppressionAbility.suppressed(enemy.team,enemy.x,enemy.y), "enemy barrier not suppressed");
check(!cryon.Type.FluxSuppressionAbility.suppressed(source.team,source.x,source.y), "friendly suppressed");
check(!cryon.Type.FluxSuppressionAbility.suppressed(enemy.team,500,500), "outside range suppressed");
cryon.Type.FluxBarrier.ForceBuild build = barrier.new ForceBuild();
build.team = enemy.team; build.x = 225; build.y = 200; build.radscl = 1; build.broken = false;
check(build.realRadius() == 0, "building radius active under suppression");
check(!build.absorbExplosion(225,200,50), "suppressed building absorbed explosion");
source.remove();
check(!cryon.Type.FluxSuppressionAbility.suppressed(enemy.team,enemy.x,enemy.y), "removed source persisted");
check(build.realRadius() > 0, "building failed to recover");
source.add(); source.team.data().tree().insert(source);
mindustry.entities.bullet.BasicBulletType missile = new mindustry.entities.bullet.BasicBulletType(1,10);
missile.lifetime = 300;
// A native flame collision deals damage with no custom collision or renderer.
mindustry.entities.bullet.BulletType flame = cryon.Content.BioCrawlerUnits.benignitas.weapons.find(w -> w.bullet.status == mindustry.content.StatusEffects.burning).bullet;
float before = enemy.health, allyBefore = ally.health;
mindustry.gen.Bullet shot = flame.create(source, source.team, enemy.x, enemy.y, 0);
shot.collision(enemy, enemy.x, enemy.y);
check(enemy.health < before && ally.health == allyBefore, "flame damage / friendly filtering failed");
shot.remove();
// Both flux ability variants must stop interception and resume without damage to the host.
for(mindustry.entities.abilities.Ability shield : new mindustry.entities.abilities.Ability[]{
 new cryon.Type.FluxBarrierAbility(40, 1000, 20), new cryon.Type.SafeFluxBarrierAbility(40, 1000, 20)}){
 shield.created(enemy);
 Time.delta = 1;
 mindustry.gen.Bullet probe = missile.create(source,source.team,enemy.x,enemy.y,0);
 Groups.bullet.updatePhysics();
 shield.update(enemy);
 check(probe.isAdded(), "suppressed unit shield absorbed projectile");
 source.remove(); ally.remove();
 for(int frame = 0; frame < 120; frame++) shield.update(enemy);
 check(!probe.isAdded(), "unit flux shield failed to resume interception");
 source.add(); source.team.data().tree().insert(source);
}
// A charged-laser projectile damages multiple aligned enemies, sparing friendlies.
source.remove(); ally.remove(); enemy.remove();
mindustry.gen.Unit laserSource = Vars.content.unit("cryon-propugno").create(mindustry.game.Team.sharded);
laserSource.set(300,300); laserSource.add();
mindustry.gen.Unit front = mindustry.content.UnitTypes.reign.create(mindustry.game.Team.crux);
front.set(350,300); front.add();
mindustry.gen.Unit rear = mindustry.content.UnitTypes.reign.create(mindustry.game.Team.crux);
rear.set(400,300); rear.add();
mindustry.gen.Unit friendly = mindustry.content.UnitTypes.reign.create(mindustry.game.Team.sharded);
friendly.set(370,300); friendly.add();
Vars.state.teams.updateTeamStats();
front.team.data().tree().clear(); front.team.data().tree().insert(front); front.team.data().tree().insert(rear);
friendly.team.data().tree().clear(); friendly.team.data().tree().insert(laserSource); friendly.team.data().tree().insert(friendly);
float h1 = front.health, h2 = rear.health, hf = friendly.health;
Vars.content.unit("cryon-propugno").weapons.first().bullet.create(laserSource, laserSource.team,300,300,0);
check(front.health < h1 && rear.health < h2 && friendly.health == hf, "laser line penetration / friendly filtering failed");
// Native missile steering turns toward an off-axis ground target.
front.set(350,325); rear.remove();
front.team.data().tree().clear(); front.team.data().tree().insert(front);
mindustry.entities.bullet.BulletType missileType = Vars.content.unit("cryon-ratio").weapons.find(w -> w.bullet instanceof mindustry.entities.bullet.MissileBulletType).bullet;
mindustry.gen.Bullet seeker = missileType.create(laserSource, laserSource.team,300,300,0);
seeker.time = 8f; Time.delta = 1f;
missileType.update(seeker);
check(seeker.rotation() > 0.01f, "native missile did not steer toward off-axis target");
seeker.remove(); front.remove(); friendly.remove(); laserSource.remove();
// Freeze real building updates, preserving team, prior enabled state and overlap recovery.
Vars.state.set(mindustry.core.GameState.State.playing);
Vars.world.tile(25,20).setBlock(probeBlock, mindustry.game.Team.crux,0);
Vars.world.tile(25,23).setBlock(probeBlock, mindustry.game.Team.sharded,0);
Vars.world.tile(27,20).setBlock(probeBlock, mindustry.game.Team.crux,0);
Vars.world.tile(75,75).setBlock(probeBlock, mindustry.game.Team.crux,0);
Events.fire(new mindustry.game.EventType.WorldLoadEvent());
CountingBuild victim = (CountingBuild)Vars.world.build(25,20);
CountingBuild friend = (CountingBuild)Vars.world.build(25,23);
CountingBuild off = (CountingBuild)Vars.world.build(27,20);
CountingBuild far = (CountingBuild)Vars.world.build(75,75);
off.enabled = false;
mindustry.gen.Unit jammer = Vars.content.unit("cryon-propugno").spawn(mindustry.game.Team.sharded,160,160);
mindustry.gen.Unit overlap = Vars.content.unit("cryon-ratio").spawn(mindustry.game.Team.sharded,160,160);
int victimTicks = victim.ticks, allyTicks = friend.ticks, farTicks = far.ticks;
Events.fire(mindustry.game.EventType.Trigger.beforeGameUpdate);
check(!victim.enabled && !victim.isAdded(), "hostile building was not frozen");
check(friend.enabled && friend.isAdded() && far.enabled && far.isAdded(), "friendly/outside building disabled");
Groups.build.update();
check(victim.ticks == victimTicks && friend.ticks > allyTicks && far.ticks > farTicks, "building update freeze or filtering failed");
Events.fire(mindustry.game.EventType.Trigger.afterGameUpdate);
check(victim.enabled && victim.isAdded() && !off.enabled, "prior switch/update state not restored");
jammer.remove();
Events.fire(mindustry.game.EventType.Trigger.beforeGameUpdate);
check(!victim.isAdded(), "overlapping source stopped suppressing");
Events.fire(mindustry.game.EventType.Trigger.afterGameUpdate);
overlap.remove();
Events.fire(mindustry.game.EventType.Trigger.beforeGameUpdate);
check(victim.enabled && victim.isAdded(), "last source removal did not restore building");
Groups.build.update();
check(victim.ticks > victimTicks, "building did not resume work");
Events.fire(mindustry.game.EventType.Trigger.afterGameUpdate);
jammer.add();
Events.fire(mindustry.game.EventType.Trigger.beforeGameUpdate);
Events.fire(new mindustry.game.EventType.SaveWriteEvent());
check(victim.enabled && victim.isAdded() && !off.enabled, "save captured temporary disabled state");
jammer.remove();
Events.fire(mindustry.game.EventType.Trigger.afterGameUpdate);
// Regression: audio callbacks must never enter the main-thread world/radius query.
Thread gameThread = Thread.currentThread();
float[] reportedRadius = {40f};
cryon.Type.FluxBarrier.ForceBuild audioBuild = barrier.new ForceBuild(){
 @Override public float realRadius(){
  check(Thread.currentThread() == gameThread, "audio callback queried live world radius");
  return reportedRadius[0];
 }
};
audioBuild.team = mindustry.game.Team.sharded;
audioBuild.x = audioBuild.y = 600;
audioBuild.broken = false;
audioBuild.deflectBullets();
checkAudioState(audioBuild, true);
reportedRadius[0] = 0;
audioBuild.deflectBullets();
checkAudioState(audioBuild, false);
reportedRadius[0] = 40;
audioBuild.deflectBullets();
checkAudioState(audioBuild, true);
audioBuild.broken = true;
audioBuild.deflectBullets();
checkAudioState(audioBuild, false);
System.out.println("PASS 3/5/7/9/13 orange turret mounts, Erekir interval and laser deployment, flame damage, laser penetration, missile steering and team filtering, suppression tiers/radius, hostile building freeze and resume, overlapping fields, switch/save preservation, barrier recovery and audio-thread isolation");
System.exit(0);
}
static void checkAudioState(cryon.Type.FluxBarrier.ForceBuild build, boolean expected) throws Exception{
 java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
 Thread audio = new Thread(() -> {
  try{
   for(int i = 0; i < 1000; i++) check(build.shouldAmbientSound() == expected, "stale ambient sound state");
  }catch(Throwable error){ failure.set(error); }
 }, "flux-audio-regression");
 audio.start(); audio.join();
 if(failure.get() != null) throw new AssertionError("audio thread regression", failure.get());
}
static void check(boolean value, String message){ if(!value) throw new AssertionError(message); }
}
