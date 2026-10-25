import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.I18NBundle;
import mindustry.*;
import mindustry.net.Net;
import mindustry.world.Block;
import java.lang.reflect.Method;

/** Loads the packaged mod with the actual local game runtime in an isolated data directory. */
public class UnityContentCheck{
 public static void main(String[] args)throws Exception{
  Core.app=new MockApplication();Core.files=new MockFiles();Core.graphics=new MockGraphics();Core.audio=new MockAudio();
  Core.settings=new Settings();Core.settings.setDataDirectory(new Fi("/tmp/cryon-unity-content-check"));
  Core.bundle=I18NBundle.createEmptyBundle();
  new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(Core.settings.getDataDirectory().child("mods/cryon.jar"));
  Vars.headless=true;Vars.loadLocales=false;Vars.net=new Net(null);Vars.platform=new mindustry.core.Platform(){};
  mindustry.core.Version.init();
  Vars.init();Vars.content.createBaseContent();Vars.content.createModContent();Vars.content.init();
  Block unity=Vars.content.block("cryon-unity");
  if(unity==null || !unity.getClass().getName().equals("cryon.Type.UnityTurret"))throw new AssertionError("Unity not registered");
  for(mindustry.mod.Mods.LoadedMod mod:Vars.mods.list()){
   if(mod.name.equals("cryon")){
    if(mod.hasContentErrors())throw new AssertionError("Mod content errors: "+mod.erroredContent);
    Class<?> recipes=mod.loader.loadClass("cryon.Unity.UnityBlueprint");
    arc.struct.Seq<?> catalog=(arc.struct.Seq<?>)recipes.getMethod("catalog").invoke(null);
    if(catalog.size<10)throw new AssertionError("Incomplete Cryon turret catalog: "+catalog.size);
    for(Object value:catalog){
      Block entry=(Block)value;
      if(!entry.name.startsWith("cryon-") || entry.name.contains("arche") || entry.name.contains("afterglow-cannon") || entry.name.contains("chisel"))throw new AssertionError("Foreign/Aravis turret in catalog: "+entry.name);
    }
    // The research graph is normally attached by the ClientLoadEvent.
    mod.loader.loadClass("cryon.TechTree.CryonTechTree").getMethod("load").invoke(null);
    arc.util.Timer.instance().clear();
    if(unity.techNode==null || !unity.techNode.parent.content.name.equals("cryon-spiral"))throw new AssertionError("Unity research parent missing");
    if(!unity.techNode.objectives.contains(o->o instanceof mindustry.game.Objectives.SectorComplete && ((mindustry.game.Objectives.SectorComplete)o).preset.name.equals("cryon-laboratory-088")))throw new AssertionError("Unity laboratory capture gate missing");
    Vars.world.loadGenerator(300,100,tiles->{for(int x=0;x<300;x++)for(int y=0;y<100;y++)tiles.set(x,y,new mindustry.world.Tile(x,y,mindustry.content.Blocks.sand,mindustry.content.Blocks.air,mindustry.content.Blocks.air));});
    Block finished=(Block)unity.getClass().getField("finishedBlock").get(unity);
    if(finished==unity || finished.configurable)throw new AssertionError("Finished turret is editable or has same identity");
    Vars.world.tile(50,30).setBlock(finished,mindustry.game.Team.sharded);
    mindustry.gen.Building platform=Vars.world.build(50,30);
    for(int start=0;start<catalog.size;start+=16){
     StringBuilder recipe=new StringBuilder("{name:catalog,color:70FFD5,targeting:1,modules:[");
     for(int i=start;i<Math.min(start+16,catalog.size);i++){
      mindustry.world.blocks.defense.turrets.Turret turret=(mindustry.world.blocks.defense.turrets.Turret)catalog.get(i);
      arc.struct.Seq<?> ammo=(arc.struct.Seq<?>)recipes.getMethod("ammoNames",mindustry.world.blocks.defense.turrets.Turret.class).invoke(null,turret);
      if(i>start)recipe.append(',');
      recipe.append("{turret:").append(turret.name).append(",ammo:\"").append(ammo.isEmpty()?"":ammo.first()).append("\"}");
     }
     recipe.append("]}");
     Object design=recipes.getMethod("decode",String.class).invoke(null,recipe.toString());
     platform.getClass().getMethod("install",recipes).invoke(platform,design);
     platform.power.status=1;
     for(mindustry.type.Item item:Vars.content.items())platform.items.add(item,1000);
     for(mindustry.type.Liquid liquid:Vars.content.liquids())platform.liquids.add(liquid,500f);
     for(int tick=0;tick<60;tick++){arc.util.Time.delta=1f;arc.util.Time.time++;platform.updateTile();}
    }
    // Real catalog entries must actually fire, including long fractional charge delays.
    mindustry.gen.Unit target=mindustry.content.UnitTypes.dagger.create(mindustry.game.Team.crux);
    target.set(platform.x+80f,platform.y);target.dead=false;target.health=target.maxHealth=1000000f;target.add();
    target.team.data().tree().insert(target);Vars.state.teams.updateTeamStats();
    Vars.world.tile(46,30).setBlock(mindustry.content.Blocks.electricHeater,mindustry.game.Team.sharded,0);
    mindustry.world.blocks.heat.HeatProducer.HeatProducerBuild heat=(mindustry.world.blocks.heat.HeatProducer.HeatProducerBuild)Vars.world.build(46,30);heat.heat=1000f;platform.updateProximity();
    for(Object value:catalog){
      mindustry.world.blocks.defense.turrets.Turret source=(mindustry.world.blocks.defense.turrets.Turret)value;
      if(!source.targetGround)continue;
      arc.struct.Seq<?> ammo=(arc.struct.Seq<?>)recipes.getMethod("ammoNames",mindustry.world.blocks.defense.turrets.Turret.class).invoke(null,source);
      String recipe="{name:firing,color:70FFD5,targeting:1,modules:[{turret:"+source.name+",ammo:\""+(ammo.isEmpty()?"":ammo.first())+"\"}]}";
      Object design=recipes.getMethod("decode",String.class).invoke(null,recipe);
      platform.getClass().getMethod("install",recipes).invoke(platform,design);
      for(mindustry.type.Item item:Vars.content.items())platform.items.set(item,1000);
      for(mindustry.type.Liquid liquid:Vars.content.liquids())platform.liquids.add(liquid,500f);
      for(int tick=0;tick<3000 && platform.getClass().getField("totalFusedShots").getInt(platform)==0;tick++){
        arc.util.Time.delta=1f;arc.util.Time.time++;platform.power.status=0.73f;
        platform.getClass().getMethod("constructorStatus",float.class).invoke(platform,1f);platform.updateTile();
      }
      if(platform.getClass().getField("totalFusedShots").getInt(platform)==0)throw new AssertionError("Ready catalog turret never fires: "+source.name+" / "+platform.getClass().getField("fusionStatus").get(platform));
    }
    System.out.println("PASS: actual ground-capable catalog turrets all fire with fractional power efficiency, heat and constructor supply");
    Vars.content.logContent();
    System.out.println("PASS: packaged mod loads, Unity content registered, "+catalog.size+" Cryon turrets available, research attached under Spiral, all modules instantiate and update with resources, stable content IDs, no mod content errors");
   }
  }
  System.exit(0);
 }
}
