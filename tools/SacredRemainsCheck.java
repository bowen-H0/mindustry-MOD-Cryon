import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.*;
import arc.util.io.*;
import java.io.*;
import java.nio.file.Files;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.net.Net;
import mindustry.type.*;
import mindustry.world.*;

/** Packaged Java-processor trigger, persisted phases, one-shot spawning, native tripod and Ichor hazards. */
public class SacredRemainsCheck{
    static final String id="aegis-sacred-remains",tag="cryon-aegis-sacred-remains-started";
    static UnitType type;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static int stage(Building build)throws Exception{Object script=build.getClass().getField("script").get(build);return (int)script.getClass().getMethod("stage").invoke(script);}
    static void tick(Building build,int n){for(int i=0;i<n;i++){Time.delta=1f;Time.time++;build.updateTile();for(Unit unit:Groups.unit.copy())if(unit.controller().getClass().getSimpleName().equals("SacredRemainsArrivalAI")){unit.updateLastPosition();unit.update();}}}
    static Building processor(int x,int y){Vars.world.tile(x,y).setBlock(Vars.content.block("cryon-java-processor"),Team.sharded);Building build=Vars.world.build(x,y);build.configured(null,id);return build;}
    static void checkpoint(Building build)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();build.write(Writes.get(new DataOutputStream(bytes)));
        int phase=stage(build);build.read(Reads.get(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))),build.version());
        check(stage(build)==phase,"Lost saved phase "+phase);
    }
    static Unit boss(){for(Unit unit:Groups.unit)if(unit.type==type&&!unit.dead)return unit;return null;}
    public static void main(String[] args){try{test();System.exit(0);}catch(Throwable error){error.printStackTrace();System.exit(1);}}
    static void test()throws Exception{
        Core.app=new MockApplication();Core.files=new MockFiles();Core.graphics=new MockGraphics();Core.audio=new MockAudio();
        Core.settings=new Settings();Core.settings.setDataDirectory(new Fi(Files.createTempDirectory("sacred-remains-check").toFile()));Core.bundle=I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(Core.settings.getDataDirectory().child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(Core.settings.getDataDirectory().child("mods/unitlaunch.jar"));
        Vars.headless=true;Vars.loadLocales=false;Vars.net=new Net(null);Vars.platform=new mindustry.core.Platform(){};
        mindustry.core.Version.init();Vars.init();Vars.content.createBaseContent();Vars.content.createModContent();Vars.content.init();Timer.instance().clear();
        check(!Vars.mods.locateMod("cryon").hasContentErrors(),"Content errors");
        Vars.logic=new Logic();Vars.logic.reset();Vars.state.rules.canGameOver=true;Vars.state.rules.unitCap=0;
        Vars.state.rules.attackMode=true;Vars.state.rules.waves=true;Vars.state.rules.waveTeam=Team.malis;
        Vars.world.loadGenerator(110,110,tiles->{for(int x=0;x<110;x++)for(int y=0;y<110;y++)tiles.set(x,y,new Tile(x,y,Blocks.stone,Blocks.air,Blocks.air));});
        Vars.world.tile(20,20).setBlock(Blocks.coreShard,Team.sharded);Vars.world.tile(30,20).setBlock(Blocks.coreShard,Team.sharded);
        Vars.world.tile(80,80).setBlock(Blocks.coreShard,Team.malis);Vars.world.tile(90,80).setBlock(Blocks.coreShard,Team.malis);
        Vars.state.set(mindustry.core.GameState.State.playing);
        Building build=processor(65,65),duplicate=processor(60,65);
        type=Vars.content.unit("cryon-sacred-remains");Liquid ichor=Vars.content.liquid("cryon-ichor");
        check(type!=null&&ichor!=null&&ichor.effect.damage>=4f,"Missing high-damage boss/liquid");
        check(type.legCount==3&&type.legGroupSize==1&&type.constructor.get() instanceof Legsc,"Not a native tripod");
        check(type.immunities.contains(ichor.effect)&&!type.playerControllable&&!type.useUnitCap,"Boss safety/cap rules");
        check(type.weapons.size==4,"Missing multi-direction spray vents");
        for(var weapon:type.weapons)check(weapon.bullet instanceof mindustry.entities.bullet.LiquidBulletType&&weapon.bullet.collidesGround&&!weapon.bullet.collidesAir,"Wrong spray combat layer");
        tick(build,1);tick(duplicate,1);checkpoint(build);
        Vars.state.set(mindustry.core.GameState.State.paused);Vars.world.build(30,20).kill();tick(build,100);check(stage(build)==0,"Paused/player core loss triggered encounter");
        Vars.state.set(mindustry.core.GameState.State.playing);tick(build,1);check(stage(build)==0,"Player core loss triggered encounter");
        Vars.world.build(80,80).kill();tick(build,1);
        check(stage(build)==1&&Vars.state.rules.tags.getBool(tag),"First Malis core loss did not trigger");
        check(!Vars.state.rules.attackMode&&!Vars.state.rules.waves&&Vars.state.rules.canGameOver,"Premature capture guard disabled player defeat");
        tick(duplicate,1);check(stage(duplicate)==4,"Duplicate processor triggered second encounter");
        checkpoint(build);tick(build,500);checkpoint(build);
        while(stage(build)==1)tick(build,1);
        check(stage(build)==2&&boss()==null,"Missing rift telegraph");tick(build,100);checkpoint(build);
        Fi save=Core.settings.getDataDirectory().child("encounter.msav");
        mindustry.io.SaveIO.write(save);mindustry.io.SaveIO.load(save);
        build=Vars.world.build(65,65);Vars.state.set(mindustry.core.GameState.State.playing);
        check(stage(build)==2&&Vars.state.rules.tags.getBool(tag),"Native save lost breach/tag");
        tick(build,160);check(stage(build)==2&&boss()!=null,"Boss did not start walking during breach");
        float entryX=boss().x,entryY=boss().y;
        mindustry.io.SaveIO.write(save);mindustry.io.SaveIO.load(save);
        build=Vars.world.build(65,65);Vars.state.set(mindustry.core.GameState.State.playing);
        check(stage(build)==2&&boss()!=null&&boss().flag==584103d,"Native save lost emerging boss/marker");
        tick(build,360);check(stage(build)==3&&boss().dst(entryX,entryY)>100f,"Boss did not physically finish walking out");
        Unit boss=boss();check(boss.team==Team.malis&&boss.getDuration(StatusEffects.boss)>0f&&((Legsc)boss).legs().length==3,"Wrong spawned boss: team="+boss.team+" status="+boss.getDuration(StatusEffects.boss)+" legs="+((Legsc)boss).legs().length);
        checkpoint(build);tick(build,300);check(Groups.unit.count(u->u.type==type)==1,"Reload duplicated boss");
        boss.vel.set(1f,0f);
        for(int i=0;i<40;i++)for(var ability:boss.abilities){Time.time++;Time.delta=1f;ability.update(boss);}
        Puddle puddle=null;for(var entity:Groups.all)if(entity instanceof Puddle found){puddle=found;break;}
        check(puddle!=null,"Walking boss did not drip Ichor");
        check(puddle.liquid==ichor&&puddle.amount>=35f,"Trail cannot inflict native puddle status");
        Unit victim=UnitTypes.dagger.create(Team.sharded);victim.set(puddle.x,puddle.y);victim.add();
        Vars.state.teams.updateTeamStats();Team.sharded.data().tree().clear();Team.sharded.data().tree().insert(victim);
        Groups.unit.tree().clear();Groups.unit.tree().insert(victim);
        puddle.update();victim.update();check(victim.hasEffect(ichor.effect),"Native puddle did not apply corrosive status");
        float before=victim.health;for(int i=0;i<10;i++)victim.update();check(victim.health<before-20f,"Ichor not damaging");
        boss.apply(ichor.effect,120f);check(!boss.hasEffect(ichor.effect),"Boss poisoned itself");
        var stream=type.weapons.first().bullet;var bullet=stream.create(boss,Team.malis,420f,420f,0f);stream.hit(bullet,420f,420f,true);
        check(mindustry.entities.Puddles.get(Vars.world.tileWorld(420,420))!=null,"Spray impact did not leave Ichor");
        boss.remove();tick(build,1);check(stage(build)==4&&Vars.state.rules.attackMode&&Vars.state.rules.waves,"Victory/wave rules not restored");
        checkpoint(build);tick(build,100);check(boss()==null,"Finished encounter respawned");
        Log.info("SACRED_REMAINS_PASS: first Malis core only, paused guard, dialogue/rift/boss save-load, single spawn, capture guard/restore, native tripod, damaging puddles, immunity, spray residue");
    }
}
