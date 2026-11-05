import arc.*;
import arc.files.Fi;
import arc.math.Mathf;
import arc.mock.*;
import arc.struct.Seq;
import arc.util.*;
import mindustry.*;
import mindustry.ai.types.CommandAI;
import mindustry.content.*;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.net.Net;
import mindustry.type.UnitType;

/** Flat-map, finite-army combat benchmark. Uses real units, weapons, statuses and collisions. */
public class EonBalanceCheck{
 public static void main(String[] args)throws Exception{
  int count=args.length==0?30:Integer.parseInt(args[0]);
  long seed=args.length<2?1:Long.parseLong(args[1]);
  Core.app=new MockApplication(); Core.files=new MockFiles(); Core.graphics=new MockGraphics(); Core.audio=new MockAudio();
  Core.settings=new Settings(); Core.settings.setDataDirectory(new Fi("/tmp/cryon-eon-balance-"+count+"-"+seed)); Core.bundle=I18NBundle.createEmptyBundle();
  new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(Core.settings.getDataDirectory().child("mods/cryon.jar"));
  Core.atlas=new arc.graphics.g2d.TextureAtlas(){
   @Override public AtlasRegion find(String name){ return new AtlasRegion(); }
   @Override public boolean has(String name){ return false; }
  };
  Vars.headless=true; Vars.loadLocales=false; Vars.net=new Net(null); Vars.platform=new mindustry.core.Platform(){};
  mindustry.core.Version.init(); Vars.init(); Vars.content.createBaseContent(); Vars.content.createModContent(); Vars.content.init();
  Vars.netServer=new mindustry.core.NetServer(); Vars.netClient=new mindustry.core.NetClient();
  Vars.mods.eachClass(m->{if(m.getClass().getName().equals("cryon.CryonJavaMod"))m.init();});
  for(mindustry.mod.Mods.LoadedMod mod:Vars.mods.list())if(mod.hasContentErrors())throw new AssertionError(mod.erroredContent);
  Vars.world.loadGenerator(400,250,tiles->{for(int x=0;x<400;x++)for(int y=0;y<250;y++)tiles.set(x,y,new mindustry.world.Tile(x,y,Blocks.sand,Blocks.air,Blocks.air));});
  Vars.state.rules.waves=Vars.state.rules.canGameOver=false;
  Vars.state.rules.unitCap=30; Vars.state.set(mindustry.core.GameState.State.playing);
  Mathf.rand.setSeed(seed); Time.setDeltaProvider(()->1f);
  if(args.length>2)Vars.content.unit("cryon-eon").health=Float.parseFloat(args[2]);
  Unit boss=Vars.content.unit("cryon-eon").create(Team.crux); boss.set(1900,1000); boss.rotation=180; boss.add();
  boss.controller(new mindustry.ai.types.FlyingAI());
  Seq<Unit> army=new Seq<>();
  for(int i=0;i<count;i++){
   String name=(i%6==5)?"lagrange":(i%2==0)?"eternal":"hydra";
   UnitType type=Vars.content.unit("cryon-"+name);
   Unit unit=type.create(Team.sharded);
   unit.set(1050+(i%6)*68,750+(i/6)*85); unit.rotation=0; unit.add();
   CommandAI ai=new CommandAI(); unit.controller(ai); ai.commandTarget(boss);
   army.add(unit);
  }
  int ticks=0;
  for(;ticks<60*240 && !boss.dead && army.contains(u->!u.dead && u.isAdded());ticks++){
   Time.update(); Vars.state.teams.updateTeamStats(); Vars.unitPhysics.begin();
   Groups.unit.updatePhysics(); Groups.bullet.updatePhysics(); Groups.unit.update(); Groups.bullet.update(); Groups.bullet.collide();
   if(ticks%120==0) for(Unit unit:army)if(unit.isAdded()&&!unit.dead&&unit.controller() instanceof CommandAI)((CommandAI)unit.controller()).commandTarget(boss);
   if(ticks%1800==0)System.out.println("PROGRESS seconds="+ticks/60+" bossHP="+boss.health+" survivors="+army.count(u->u.isAdded()&&!u.dead));
  }
  int survivors=army.count(u->u.isAdded()&&!u.dead);
  System.out.println("BALANCE_RESULT count="+count+" seed="+seed+" seconds="+ticks/60f+" bossDead="+boss.dead+" bossHP="+Math.max(0,boss.health)+" survivors="+survivors+" casualties="+(count-survivors));
  for(String name:new String[]{"eternal","hydra","lagrange"}){
   int initial=army.count(u->u.type.name.equals("cryon-"+name));
   System.out.println(name+" initial="+initial+" survivors="+army.count(u->u.type.name.equals("cryon-"+name)&&u.isAdded()&&!u.dead));
  }
  System.exit(0);
 }
}
