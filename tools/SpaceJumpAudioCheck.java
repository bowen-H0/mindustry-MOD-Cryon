import arc.Core;
import arc.audio.Sound;
import arc.mock.MockAudio;
import cryon.Features.SpaceJumpAudio;
import cryon.Type.SpaceJumpAbility;
import mindustry.Vars;
import mindustry.entities.abilities.Ability;
import mindustry.game.Team;
import mindustry.gen.Unit;

/** Exercise audio timing and cancellation without requiring an audio device. */
public class SpaceJumpAudioCheck{
 static class Audio extends MockAudio{
  boolean playing; int stops, changes;
  public boolean isPlaying(int voice){return playing;}
  public void stop(int voice){playing=false;stops++;}
  public void set(int voice,float pan,float volume){changes++;}
  public void setPitch(int voice,float pitch){}
 }
 static class Clip extends Sound{
  int loops, releases; Audio audio;
  Clip(Audio value){audio=value;}
  public float calcVolume(float x,float y){return 1;}
  public float calcPan(float x,float y){return 0;}
  public int loop(float volume,float pitch,float pan){loops++;audio.playing=true;return 1;}
  public int at(float x,float y,float pitch,float volume){releases++;return 2;}
 }
 static void set(String name,Object value)throws Exception{
  java.lang.reflect.Field field=SpaceJumpAudio.class.getDeclaredField(name);field.setAccessible(true);field.set(null,value);
 }
 static void check(boolean value,String text){if(!value)throw new AssertionError(text);}
 public static void main(String[] args)throws Exception{
  SpaceJumpCheck.main(args);
  Audio audio=new Audio(); Core.audio=audio;
  Clip hum=new Clip(audio), release=new Clip(audio);set("charge",hum);set("fold",release);
  Vars.state.set(mindustry.core.GameState.State.playing);
  Unit unit=mindustry.content.UnitTypes.flare.create(Team.sharded);unit.set(100,100);unit.elevation=1;unit.rotation=180;unit.add();unit.dead=false;mindustry.gen.Groups.unit.add(unit);
  SpaceJumpAbility jump=new SpaceJumpAbility();unit.abilities=new Ability[]{jump};
  check(jump.jump(unit,800,100),"charge request rejected");
  Vars.headless=false;
  SpaceJumpAudio.update();check(hum.loops==0,"sound started during alignment");
  int ticks=0;while(jump.chargeProgress()==0 && ticks++<1000)jump.update(unit);
  SpaceJumpAudio.update();check(hum.loops==1,"charge sound did not start with charge");
  for(int i=0;i<10;i++){jump.update(unit);SpaceJumpAudio.update();}
  check(hum.loops==1 && audio.changes>0,"charge sound replayed instead of continuously updating");
  SpaceJumpAudio.fold(unit,100,100,800,100,1);
  check(!audio.playing && release.releases==1,"fold did not stop hum and release exactly once");
  SpaceJumpAudio.update();check(audio.playing,"test charge not active");
  jump.cancel(unit);SpaceJumpAudio.update();check(!audio.playing,"cancel left charge sound playing");
  jump.begin(unit,800,100,100,100,0,1);jump.update(unit);SpaceJumpAudio.update();
  check(audio.playing,"second charge silent");
  Vars.state.set(mindustry.core.GameState.State.paused);SpaceJumpAudio.update();
  check(!audio.playing,"pause left charge sound playing");
  Vars.headless=true;
  System.out.println("PASS: silent alignment, continuous charge, synchronized release, cancellation and pause stop audio");
 }
}
