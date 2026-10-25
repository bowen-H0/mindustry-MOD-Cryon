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
import cryon.Features.SpaceJump;
public class SpaceJumpRtsCheck{
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
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

  SpaceJump.loadCommands();
  check(!SpaceJump.jumpCommand.switchToMove && SpaceJump.jumpCommand.drawTarget,"command target settings");
  Unit u=mindustry.content.UnitTypes.flare.create(Team.sharded);
  u.set(100,100);u.elevation=1;u.add();u.dead=false;u.maxHealth=100;
  u.type.commands=u.type.commands.copy();u.type.commands.add(SpaceJump.jumpCommand);
  CommandAI ai=new CommandAI();u.controller(ai);
  SpaceJumpAbility a=new SpaceJumpAbility();
  ai.command(UnitCommand.moveCommand);ai.commandPosition(new arc.math.geom.Vec2(500,100));
  check(!a.worthwhile(u,500,100),"probe should fail automatic time-saving threshold");
  a.update(u);check(!a.active(),"ordinary movement unexpectedly jumped");
  ai.command(SpaceJump.jumpCommand);ai.commandPosition(new arc.math.geom.Vec2(500,100));
  a.update(u);check(a.active(),"RTS command failed outside campaign");
  int ticks=0;while(a.active() && ticks++<1000)a.update(u);
  check(!a.active() && Math.abs(u.x-500)<0.1f,"ordered jump did not arrive");
  a.afterUpdate(u);
  ai.commandPosition(new arc.math.geom.Vec2(900,100));a.update(u);
  check(!a.active(),"RTS command bypassed cooldown");
  a=new SpaceJumpAbility();ai.commandPosition(new arc.math.geom.Vec2(600,100));a.update(u);
  check(!a.active(),"RTS command bypassed minimum distance");
  ai.commandPosition(new arc.math.geom.Vec2(2000,100));a.update(u);
  check(a.active() && u.dst(a.landingX(),a.landingY())<=480.01f,"RTS command bypassed tier range");
  u.type.commands.add(UnitCommand.repairCommand);ai.command(UnitCommand.repairCommand);a.update(u);check(!a.active(),"incompatible command did not cancel charge");
  System.out.println("PASS: RTS jump, normal movement threshold, non-campaign, arrival, cooldown, minimum distance, tier range and cancellation");
 }
}
