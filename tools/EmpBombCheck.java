import arc.*;
import arc.files.Fi;
import arc.math.geom.Vec2;
import arc.mock.*;
import arc.util.*;
import java.nio.file.Files;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.core.World;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.io.SaveIO;
import mindustry.net.Net;
import mindustry.world.*;

/** Packaged EMP: exact fuse, native damage/diplomacy, path-aligned conversion and persistence. */
public final class EmpBombCheck{
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[]args){try{run();System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
    static void run()throws Exception{
        Core.app=new MockApplication();Core.files=new MockFiles();Core.graphics=new MockGraphics();Core.audio=new MockAudio();
        Core.settings=new Settings();Fi data=new Fi(Files.createTempDirectory("emp-bomb-check-").toFile());
        Core.settings.setDataDirectory(data);Core.bundle=I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlanuch.jar"));
        Vars.headless=true;Vars.loadLocales=false;Vars.net=new Net(null);Vars.platform=new mindustry.core.Platform(){};
        mindustry.core.Version.init();Vars.init();Core.settings.setJson(mindustry.io.JsonIO.json);
        Vars.content.createBaseContent();Vars.content.createModContent();Vars.content.init();
        var mod=Vars.mods.locateMod("cryon");check(mod!=null&&!mod.hasContentErrors(),"mod content errors");
        var loader=mod.main.getClass().getClassLoader();var terrain=loader.loadClass("cryon.Content.NanoEnvironment");
        terrain.getMethod("install").invoke(null);loader.loadClass("cryon.Features.MapAlliances").getMethod("install").invoke(null);
        Block bomb=Vars.content.block("cryon-emp-bomb"),nanoFloor=Vars.content.block("cryon-nano-floor");
        Block nanoWall=Vars.content.block("cryon-nano-terrain-wall"),nanoOre=Vars.content.block("cryon-nano-ore");
        check(bomb!=null&&bomb.size==2&&!bomb.hasPower&&!bomb.canOverdrive,"EMP content/configuration");
        Vars.logic=new Logic();Vars.logic.reset();
        Vars.world.loadGenerator(90,90,tiles->{
            for(int x=0;x<90;x++)for(int y=0;y<90;y++)tiles.set(x,y,new Tile(x,y,Blocks.stone,Blocks.air,Blocks.air));
            tiles.get(5,5).setBlock(Blocks.coreShard,Team.sharded);
            tiles.get(30,30).setBlock(bomb,Team.sharded);
            tiles.get(35,34).setBlock(Blocks.thoriumWallLarge,Team.crux);
            tiles.get(35,26).setBlock(Blocks.thoriumWallLarge,Team.green);
        });
        Vars.state.set(mindustry.core.GameState.State.playing);Vars.state.rules.unitCap=100;Time.delta=1f;
        Vars.state.rules.tags.put("cryon-allied-teams","4");
        Building b=Vars.world.build(30,30);
        Vec2 point=new Vec2();Tile[] contaminated=new Tile[3];
        for(int i=0;i<3;i++){
            point.set(48f+i*32f,0f);
            contaminated[i]=Vars.world.tile(World.toTile(b.x+point.x),World.toTile(b.y+point.y));
            contaminated[i].setFloor(nanoFloor.asFloor());
        }
        contaminated[1].setBlock(nanoWall);contaminated[2].setOverlay(nanoOre);
        Tile normal=Vars.world.tile(32,30);normal.setOverlay(Blocks.oreCopper);
        Tile distant=Vars.world.tile(65,30);distant.setFloor(nanoFloor.asFloor());
        Unit enemy=UnitTypes.eclipse.spawn(Team.crux,b.x+60,b.y+36),friend=UnitTypes.eclipse.spawn(Team.sharded,b.x+60,b.y-36);
        Unit ally=UnitTypes.eclipse.spawn(Team.green,b.x+40,b.y),far=UnitTypes.eclipse.spawn(Team.crux,b.x+210,b.y);
        float enemyHp=enemy.health,friendHp=friend.health,allyHp=ally.health,farHp=far.health;
        Building enemyBuilding=Vars.world.build(35,34),allyBuilding=Vars.world.build(35,26);
        float buildingHp=enemyBuilding.health,allyBuildingHp=allyBuilding.health;
        for(int i=0;i<90;i++)b.updateTile();check(age(b)==90f,"fuse timing");
        Fi save=data.child("emp.msav");SaveIO.write(save);SaveIO.load(save);b=Vars.world.build(30,30);
        check(age(b)==90f,"fuse lost on save/load");
        // Refresh entity references restored by the native save loader.
        enemy=Groups.unit.find(u->u.team==Team.crux&&u.x<400);friend=Groups.unit.find(u->u.team==Team.sharded);
        ally=Groups.unit.find(u->u.team==Team.green);far=Groups.unit.find(u->u.team==Team.crux&&u.x>400);
        enemyBuilding=Vars.world.build(35,34);allyBuilding=Vars.world.build(35,26);
        for(int i=0;i<89;i++)b.updateTile();check(Vars.world.build(30,30)==b&&age(b)==179f,"EMP exploded before three seconds");
        check(contaminated[0].floor()==nanoFloor,"terrain converted before detonation");
        b.enabled=false;b.updateTile();check(Vars.world.build(30,30)==null,"disabled bomb defused or failed to remove itself");
        check(enemy.health==enemyHp,"EMP still applied instant scripted unit damage");
        Object primaryType=bomb.getClass().getField("arcBullet").get(bomb);
        check(Groups.bullet.count(q->q.type==primaryType)>=24,"no real barrage bullets spawned");
        Bullet moving=Groups.bullet.find(q->q.type==primaryType);float startX=moving.x,startY=moving.y;
        Object discharge=primaryType.getClass().getField("discharge").get(primaryType);
        boolean nativeNodes=false;
        for(int frame=0;frame<55;frame++){
            Vars.state.teams.updateTeamStats();
            Groups.bullet.updatePhysics();Groups.unit.updatePhysics();Groups.bullet.update();Groups.bullet.collide();
            Time.update();Vars.state.tick++;
            nativeNodes|=Groups.bullet.contains(q->q.type.getClass().getName().contains("EmpLightningPoint"));
            if(frame==5)check(moving.dst(startX,startY)>10f,"electric projectile did not travel");
        }
        check(discharge instanceof mindustry.entities.bullet.LightningBulletType&&nativeNodes,"native lightning and real damage node entities not created");
        float dealt=enemyHp-enemy.health;
        check(dealt>0f,"real projectile collisions did not damage enemy: "+dealt);
        check(friend.health==friendHp&&ally.health==allyHp&&far.health==farHp,"friendly/ally/distant unit damaged");
        check(enemyBuilding.health<buildingHp&&allyBuilding.health==allyBuildingHp,"building enemy/ally damage");
        for(int i=0;i<3;i++){
            Tile t=Vars.world.tile(contaminated[i].x,contaminated[i].y);
            check(t.floor()==Blocks.metalFloorDamaged,"nano ground did not become damaged metal");
            check(!(boolean)terrain.getMethod("infected",Tile.class).invoke(null,t),"converted tile remains infected");
        }
        check(Vars.world.tile(contaminated[1].x,contaminated[1].y).block()==Blocks.darkMetal,"nano natural wall did not become dark metal");
        check(Vars.world.tile(contaminated[2].x,contaminated[2].y).overlay()==Blocks.oreScrap,"nano ore did not become scrap");
        check(Vars.world.tile(normal.x,normal.y).floor()==Blocks.stone&&Vars.world.tile(normal.x,normal.y).overlay()==Blocks.oreCopper,"ordinary mineral terrain altered");
        check(Vars.world.tile(distant.x,distant.y).floor()==nanoFloor,"out-of-range contamination cleared");
        Vars.world.tile(65,65).setBlock(bomb,Team.sharded);Vars.world.tile(67,65).setFloor(nanoFloor.asFloor());
        Vars.world.build(65,65).kill();Time.update();check(Vars.world.tile(67,65).floor()==nanoFloor,"early destruction incorrectly triggered EMP");
        SaveIO.write(save);SaveIO.load(save);
        check(Vars.world.tile(contaminated[1].x,contaminated[1].y).block()==Blocks.darkMetal&&Vars.world.tile(contaminated[2].x,contaminated[2].y).overlay()==Blocks.oreScrap,"converted terrain lost on save/load");
        System.out.println("EMP_BOMB_CHECK_PASS: exact three-second fuse, saved fuse, disable cannot defuse, real moving bullet barrage, native lightning/damage nodes, hostile-only collisions, allied protection, range, arc-aligned metal floor/dark metal/scrap conversion, ordinary terrain preserved, early destruction, terrain persistence");
    }
    static float age(Building b)throws Exception{return b.getClass().getField("age").getFloat(b);}
}
