import arc.Core;
import arc.input.KeyCode;
import arc.mock.MockInput;
import arc.scene.Element;
import arc.scene.Scene;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import cryon.Features.SpaceJump;
import cryon.Type.SpaceJumpAbility;
import mindustry.Vars;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.gen.UnitEntity;

public class SpaceJumpPreviewCheck{
 static class Input extends MockInput{
  KeyCode tap; boolean held; float worldX=2000;
  public boolean keyTap(KeyCode key){return key==tap;}
  public boolean keyDown(KeyCode key){return held && (key==KeyCode.altLeft || key==KeyCode.altRight);}
  public float mouseWorldX(){return worldX;}
  public float mouseWorldY(){return 100;}
 }
 static Object get(String name)throws Exception{
  java.lang.reflect.Field f=SpaceJump.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);
 }
 static void update()throws Exception{
  java.lang.reflect.Method m=SpaceJump.class.getDeclaredMethod("updatePreview");m.setAccessible(true);m.invoke(null);
 }
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  SpaceJumpCheck.main(args);
  Core.gl=new arc.mock.MockGL20();Core.input=new Input();Core.scene=new Scene();
  Input input=(Input)Core.input;
  Vars.state.rules.sector=mindustry.content.Planets.serpulo.sectors.first();
  Vars.state.set(mindustry.core.GameState.State.playing);
  Unit unit=mindustry.content.UnitTypes.flare.create(mindustry.game.Team.sharded);unit.set(100,100);unit.elevation=1;unit.dead=false;
  java.lang.reflect.Field added=UnitEntity.class.getDeclaredField("added");added.setAccessible(true);added.set(unit,true);
  SpaceJumpAbility jump=new SpaceJumpAbility();unit.abilities=new mindustry.entities.abilities.Ability[]{jump};
  Vars.player=Player.create();Vars.player.unit(unit);
  input.held=true;update();
  check(get("previewUnit")==unit && !jump.active(),"held Alt did not open preview");
  input.held=false;update();check(get("previewUnit")==null && !jump.active(),"release did not close unconfirmed preview");
  input.held=true;update();
  Element overlay=(Element)get("previewInput");
  click(overlay);update();check(jump.active() && get("previewUnit")==null,"held Alt plus click failed to confirm fold");
  update();check(get("previewUnit")==null && jump.active(),"confirmed preview reopened while Alt was held");
  input.held=false;update();check(!jump.active(),"release did not cancel unfinished manual charge");
  input.worldX=200;input.held=true;update();click(overlay);update();
  check(!jump.active() && get("previewUnit")==unit,"near preview could confirm jump");
  input.held=false;update();input.worldX=2000;input.held=true;update();click(overlay);update();
  check(jump.active(),"far jump no longer confirms");
  input.tap=KeyCode.escape;update();check(get("previewUnit")==null && !jump.active(),"Esc did not cancel charge");
  input.tap=null;update();check(get("previewUnit")==null,"cancelled preview reopened without releasing Alt");
  input.held=false;update();Vars.state.rules.sector=null;input.held=true;update();check(get("previewUnit")==null,"non-campaign preview allowed");
  System.out.println("PASS: hold Alt preview, release cancels preview/charge, held click confirmation, near rejection, far confirmation, Esc latch and campaign restriction");
 }
 static void click(Element overlay){
  for(arc.scene.event.EventListener listener:overlay.getListeners())if(listener instanceof InputListener){
   check(((InputListener)listener).touchDown(new InputEvent(),0,0,0,KeyCode.mouseLeft),"click not consumed");
  }
 }
}
