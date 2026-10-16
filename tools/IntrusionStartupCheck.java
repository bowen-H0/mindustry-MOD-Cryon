import cryon.Type.JavaProcessor.JavaProcessorBuild;
import cryon.Script.VirusIntrusionScript;
import mindustry.Vars;
import mindustry.gen.Groups;
import arc.util.Time;
/** Loads the bundled map and uses the engine building group instead of direct script calls. */
public class IntrusionStartupCheck {
 public static void main(String[] args)throws Exception{
  IntrusionMapCheck.run(new String[]{"/tmp/cryon-native-map-check/startup.msav"});
  for(int attempt=0; attempt<6; attempt++){
   Vars.logic.reset();
   mindustry.io.SaveIO.load(new arc.files.Fi("maps/cryon-intrusion.msav"));
   JavaProcessorBuild p=(JavaProcessorBuild)Vars.world.build(56,7);
   Vars.logic.play();
   Time.delta=attempt % 2 == 0 ? 1f : 4f;
   Groups.build.update();
   Vars.headless=false;
   Groups.build.update();
   arc.scene.Element hud=arc.Core.scene.getElements().peek();
   hud.remove();
   Groups.build.update();
   IntrusionMapCheck.check(arc.Core.scene.getElements().size > 0, "detached HUD was not restored");
   for(int i=0;i<800;i++){Groups.build.update(); Time.update();}
   IntrusionMapCheck.check(((VirusIntrusionScript)p.script).seconds() > 12f, "bundled map controller stopped advancing");
   IntrusionMapCheck.check(Groups.bullet.size() > 0, "bundled map did not start combat");
   IntrusionMapCheck.check(VirusIntrusionScript.current()==p.script, "old controller survived map reload");
   Vars.headless=true;
  }
  System.out.println("PASS: six repeated bundled map loads, delta 1/4, HUD detach recovery, controller replacement and combat startup");
  System.exit(0);
 }
}
