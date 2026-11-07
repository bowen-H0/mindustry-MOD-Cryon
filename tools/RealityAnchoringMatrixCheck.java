import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.*;
import java.nio.file.Files;
import mindustry.*;
import mindustry.content.Blocks;
import mindustry.core.Logic;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.io.SaveIO;
import mindustry.net.Net;
import mindustry.world.*;
import mindustry.world.meta.BuildVisibility;

/** Native engine integration: links, swept interception, beams, reinforcement and chain reactions. */
public final class RealityAnchoringMatrixCheck{
    static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }
    public static void main(String[] args){
        try{ run(); System.exit(0); }
        catch(Throwable error){ error.printStackTrace(); System.exit(1); }
    }
    static void run() throws Exception{
        Core.app = new MockApplication(); Core.files = new MockFiles(); Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Core.settings = new Settings(); Fi data = new Fi(Files.createTempDirectory("cryon-orbital-check-").toFile());
        Core.settings.setDataDirectory(data); Core.bundle = I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlanuch.jar"));
        Vars.headless = true; Vars.loadLocales = false; Vars.net = new Net(null); Vars.platform = new mindustry.core.Platform(){};
        mindustry.core.Version.init(); Vars.init(); Core.settings.setJson(mindustry.io.JsonIO.json);
        Vars.content.createBaseContent(); Vars.content.createModContent(); Vars.content.init();
        var mod = Vars.mods.locateMod("cryon"); check(mod != null && !mod.hasContentErrors(), "packaged content errors");
        mod.main.getClass().getClassLoader().loadClass("cryon.Features.MapAlliances").getMethod("install").invoke(null);
        var loader=mod.main.getClass().getClassLoader();
        Class<?> tags=loader.loadClass("cryon.Features.ProjectileTags"); tags.getMethod("install").invoke(null);
        var mark=tags.getMethod("mark",mindustry.entities.bullet.BulletType.class,int.class);
        Block node=Vars.content.block("cryon-reality-anchoring-matrix");
        check(node!=null && node.size==9,"anchor content missing");
        Vars.logic=new Logic();Vars.logic.reset();
        Vars.world.loadGenerator(340,260,tiles->{
            for(int x=0;x<340;x++)for(int y=0;y<260;y++)tiles.set(x,y,new Tile(x,y,Blocks.stone,Blocks.air,Blocks.air));
            tiles.get(20,20).setBlock(Blocks.coreShard,Team.sharded);
            tiles.get(170,130).setBlock(node,Team.sharded);tiles.get(180,140).setBlock(node,Team.sharded);
            tiles.get(170,150).setBlock(Vars.content.block("cryon-orbital-power-node"),Team.sharded);
            tiles.get(200,130).setBlock(Blocks.thoriumWallLarge,Team.sharded);
        });
        Vars.state.set(mindustry.core.GameState.State.playing);Vars.state.rules.unitCap=100;Time.delta=1f;
        Vars.state.rules.tags.put("cryon-allied-teams",Integer.toString(Team.green.id));
        Building a=Vars.world.build(170,130), b=Vars.world.build(180,140), source=Vars.world.build(170,150), wall=Vars.world.build(200,130);
        for(Building n:new Building[]{a,b}){n.items.add(mindustry.content.Items.phaseFabric,100);source.configureAny(n.pos());n.shouldConsumePower=true;}
        source.power.graph.update();a.updateTile();b.updateTile();
        check(node.consPower.usage==480f && a.power.status>.99f,"native 28800/s power");
        check(a.items.get(mindustry.content.Items.phaseFabric)==99 && get(a,"fuelTime")==11f,"phase fabric not prepaid");
        for(int i=1;i<60;i++)a.updateTile();
        check(a.items.get(mindustry.content.Items.phaseFabric)==95 && get(a,"fuelTime")==0f,"not one phase fabric every .2s");
        a.updateTile();
        var phase=new mindustry.entities.bullet.BasicBulletType(900f,1000f);phase.lifetime=20;phase.init();mark.invoke(null,phase,1);
        Bullet shot=phase.create(null,Team.crux,200,1040,0);
        check(shot.damage==500f && shot.damageMultiplier()==.5f,"fast crossing did not halve phase damage");
        shot.update();shot.update();check(shot.damage==500f,"overlapping anchors stacked reduction");
        shot.set(2500,1040);shot.update();check(shot.damage==500f,"attenuation disappeared after exit");shot.remove();
        var ordinary=new mindustry.entities.bullet.BasicBulletType(900f,1000f);ordinary.init();
        Bullet normal=ordinary.create(null,Team.crux,200,1040,0);check(normal.damage==1000f,"ordinary bullet weakened");normal.remove();
        Bullet friendly=phase.create(null,Team.green,200,1040,0);check(friendly.damage==1000f,"ally phase bullet weakened");friendly.remove();
        var laser=new mindustry.entities.bullet.LaserBulletType(1000f);laser.length=2500;laser.init();mark.invoke(null,laser,4);
        float hp=wall.health; laser.create(null,Team.crux,200,1040,0);
        check(Math.abs(hp-wall.health-500f)<.1f && laser.damage==1000f,"phase hitscan damage not 50% or type mutated: "+(hp-wall.health));
        Unit target=spawn(mindustry.content.UnitTypes.eclipse,Team.sharded,1600,1100);target.armor=0;target.health=10000;target.maxHealth=20000;
        Vars.state.teams.updateTeamStats();Groups.unit.tree().clear();for(Unit u:Groups.unit)Groups.unit.tree().insert(u);
        for(var td:Vars.state.teams.active){td.tree().clear();for(Unit u:Groups.unit)if(u.team==td.team)td.tree().insert(u);}
        var splash=new mindustry.entities.bullet.BasicBulletType(0,0);splash.splashDamage=1000;splash.splashDamageRadius=30;splash.init();mark.invoke(null,splash,1);
        Bullet blast=splash.create(null,Team.crux,1600,1100,0);splash.createSplashDamage(blast,1600,1100);
        check(Math.abs(target.health-9500)<.1f,"phase explosion not halved: "+target.health);blast.absorb();
        Bullet spatial=phase.create(null,Team.crux,1600,1100,0);
        loader.loadClass("cryon.Type.EonPhaseBulletType").getMethod("areaDamage",Bullet.class,float.class,float.class,boolean.class).invoke(null,spatial,30f,1000f,false);
        check(Math.abs(target.health-9000)<.1f,"custom spatial damage not halved: hp="+target.health+" position="+spatial.x+","+spatial.y+" multiplier="+spatial.damageMultiplier());spatial.remove();target.remove();
        var child=new mindustry.entities.bullet.BasicBulletType(1,400);child.init();phase.fragBullet=child;phase.fragBullets=1;phase.fragOnDespawn=true;
        Bullet parent=phase.create(null,Team.crux,1600,1100,0);parent.time=parent.lifetime;parent.update();
        Bullet fragment=Groups.bullet.find(q->q.type==child);
        check(fragment!=null && fragment.damage==200f,"fragment did not inherit phase tags/attenuation");
        for(Bullet q:Groups.bullet.copy())q.remove();
        Unit enemy=spawn(mindustry.content.UnitTypes.eclipse,Team.crux,400,1040);
        enemy.set(new arc.math.geom.Vec2(1360,1040));check(enemy.x==400,"native teleport entered anchor");
        enemy.set(1360,1040);check(enemy.x==400,"scripted jump entered anchor");
        Unit inside=spawn(mindustry.content.UnitTypes.eclipse,Team.crux,1400,1100);
        inside.set(new arc.math.geom.Vec2(2400,1100));check(inside.x==1400,"enemy teleported out of anchor");
        inside.set(1402,1100);check(inside.x==1402,"ordinary movement blocked");
        Unit ally=spawn(mindustry.content.UnitTypes.eclipse,Team.green,400,1040);ally.set(new arc.math.geom.Vec2(1360,1040));check(ally.x==1360,"allied teleport blocked");
        Class<?> jumpType=loader.loadClass("cryon.Type.SpaceJumpAbility");Object jump=jumpType.getConstructor().newInstance();
        check(jumpType.getMethod("landing",Unit.class,float.class,float.class,arc.math.geom.Vec2.class).invoke(jump,enemy,1360f,1040f,new arc.math.geom.Vec2())==null,"jump preview admitted protected destination");
        jumpType.getMethod("begin",Unit.class,float.class,float.class,float.class,float.class,float.class,int.class).invoke(jump,enemy,1360f,1040f,enemy.x,enemy.y,0f,5);
        jumpType.getMethod("update",Unit.class).invoke(jump,enemy);
        check(!(boolean)jumpType.getMethod("active").invoke(jump),"charging jump not cancelled");
        source.enabled=false;source.power.graph.update();enemy.set(new arc.math.geom.Vec2(1360,1040));check(enemy.x==1360,"blackout did not release teleport lock");
        enemy.set(400,1040); enemy.rotation=0f; jump=jumpType.getConstructor().newInstance();
        jumpType.getMethod("begin",Unit.class,float.class,float.class,float.class,float.class,float.class,int.class).invoke(jump,enemy,1000f,1040f,400f,1040f,0f,5);
        for(int i=0;i<200 && (boolean)jumpType.getMethod("active").invoke(jump);i++)jumpType.getMethod("update",Unit.class).invoke(jump,enemy);
        check(Math.abs(enemy.x-1000f)<.1f,"unprotected charged jump no longer completes");
        source.enabled=true;source.power.graph.update();
        float paid=get(a,"fuelTime");Fi save=data.child("anchor.msav");SaveIO.write(save);SaveIO.load(save);
        a=Vars.world.build(170,130);check(get(a,"fuelTime")==paid && a.items.get(mindustry.content.Items.phaseFabric)==94,"anchor paid reserve lost");
        var stat=(mindustry.world.meta.Stat)tags.getField("tagStat").get(null);
        var constructor=Vars.content.block("cryon-quantum");
        check(constructor.stats.toMap().get(stat.category).containsKey(stat),"turret tags absent from properties");
        check(mindustry.content.UnitTypes.eclipse.stats.toMap().get(stat.category).containsKey(stat),"unit weapon tags absent from properties");
        int constructMask=(int)tags.getMethod("mask",mindustry.entities.bullet.BulletType.class).invoke(null,((mindustry.world.blocks.defense.turrets.PowerTurret)constructor).shootType);
        check((constructMask&3)==3,"constructor weapon not phase tagged");
        System.out.println("REALITY_ANCHOR_CHECK_PASS: exact 5 phase/s, native power, normal/phase/allied bullets, nonstacking 50%, laser/splash/spatial/fragment damage, native/scripted/charged teleport locks, walking, allied passage, blackout, save/load, weapon property tags");
    }
    static float get(Building b,String key)throws Exception{return b.getClass().getField(key).getFloat(b);}
    static Unit spawn(mindustry.type.UnitType t,Team team,float x,float y){Unit u=t.create(team);u.set(x,y);u.add();return u;}
}
