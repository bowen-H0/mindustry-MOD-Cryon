import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.struct.*;
import arc.math.Angles;
import arc.util.*;
import cryon.Content.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.entities.bullet.BulletType;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.net.Net;
import mindustry.world.blocks.defense.turrets.*;

/** Tungsten consumption, four-petal geometry, alternating curl and spiral fragments. */
public class PurgeDefenseCheck{
    static void check(boolean ok,String message){ if(!ok) throw new AssertionError(message); }
    static class Probe extends ItemTurret.ItemTurretBuild{
        Probe(ItemTurret turret){ turret.super(); }
        void fire(){ shoot(peekAmmo()); }
    }
    public static void main(String[] args){
        Core.app=new MockApplication(); Core.files=new MockFiles();
        Core.graphics=new MockGraphics(); Core.audio=new MockAudio();
        Core.settings=new Settings(); Core.settings.setDataDirectory(new Fi("/tmp/purge-defense-check"));
        Core.bundle=I18NBundle.createEmptyBundle();
        Vars.headless=true; Vars.loadLocales=false; Vars.net=new Net(null);
        Vars.platform=new mindustry.core.Platform(){};
        Vars.init(); Vars.content.createBaseContent(); CryonItems.load();
        BulletType original=((PowerTurret)Blocks.afflict).shootType;
        float conversion=original.ammoMultiplier;
        float homingDelay=original.intervalBullet.homingDelay;
        PurgeDefense.load(); Vars.content.init();
        Vars.world.loadGenerator(80,80,tiles -> {
            tiles.fill(); for(var tile:tiles)tile.setFloor(Blocks.sand.asFloor());
        });
        Time.delta=1f;
        ItemTurret turret=PurgeDefense.purge;
        turret.buildType=() -> new Probe(turret);
        Vars.world.tile(20,20).setBlock(turret,Team.sharded);
        Probe build=(Probe)Vars.world.build(20,20);
        build.updateConsumption();
        check(build.acceptItem(null,Items.tungsten),"tungsten rejected");
        check(!build.acceptItem(null,Items.surgeAlloy),"wrong ammunition accepted");
        check(!build.hasAmmo(),"empty turret has ammo");
        build.handleStack(Items.tungsten,1,null);
        build.updateConsumption();
        check(!build.hasAmmo(),"one tungsten can fire a two-item shot");
        build.handleStack(Items.tungsten,1,null);
        build.updateConsumption();
        check(build.hasAmmo() && build.totalAmmo==2,"two tungsten did not enable firing");
        build.rotation=0f; build.targetPos.set(build.x+250f,build.y);
        build.fire();
        check(build.totalAmmo==0 && !build.hasAmmo(),"shot did not consume exactly two tungsten");
        BulletType barrage=turret.ammoTypes.get(Items.tungsten);
        check(barrage!=original && original.ammoMultiplier==conversion,"native Afflict modified");
        check(barrage.intervalBullet!=original.intervalBullet && original.intervalBullet.homingDelay==homingDelay,
            "native Afflict subprojectiles modified");
        Bullet main=Groups.bullet.find(b -> b.type==barrage);
        check(main!=null,"main projectile not fired");
        int before=Groups.bullet.size();
        main.time=barrage.bulletInterval;
        Time.time+=barrage.bulletInterval+1f;
        barrage.updateBulletInterval(main);
        check(Groups.bullet.size()==before+barrage.intervalBullets,"flight did not emit side bullets");
        check(barrage.intervalBullets==4 && barrage.intervalBullet.homingPower>0f,"missing four-petal barrage");
        Seq<Bullet> petals=new Seq<>();
        Groups.bullet.each(b -> {if(b.type==barrage.intervalBullet)petals.add(b);});
        float phase=petals.first().rotation();
        for(int i=0;i<4;i++){
            Bullet child=petals.get(i);
            check(Angles.angleDist(child.rotation(),phase+i*90f)<0.15f,"petals not equally spaced");
            check(child.owner==build && child.mover!=null,"lost shooter attribution or curved motion");
            float angle=child.rotation();child.time=1f;child.mover.move(child);
            check(Angles.angleDist(child.rotation(),angle-5f)<0.15f,"first petal wave did not curl clockwise: angle="+angle+", after="+child.rotation()+", delta="+Time.delta+", delay="+child.type.homingDelay);
        }
        main.time+=barrage.bulletInterval;
        Time.time+=barrage.bulletInterval+1f;
        barrage.updateBulletInterval(main);
        Seq<Bullet> next=new Seq<>();
        Groups.bullet.each(b -> {if(b.type==barrage.intervalBullet && !petals.contains(b,true))next.add(b);});
        check(next.size==4 && Angles.angleDist(next.first().rotation(),phase)>20f,"successive petal waves do not rotate");
        for(Bullet child:next){
            float angle=child.rotation();child.time=1f;child.mover.move(child);
            check(Angles.angleDist(child.rotation(),angle+5f)<0.15f,"second petal wave did not reverse curl");
        }
        IntSet existing=new IntSet();Groups.bullet.each(b -> existing.add(b.id));
        before=Groups.bullet.size();
        barrage.despawned(main);
        barrage.removed(main);
        check(Groups.bullet.size()==before+barrage.fragBullets,"despawn did not emit radial fragments");
        check(barrage.fragBullets==20,"expected twenty final fragments");
        Seq<Bullet> fragments=new Seq<>();
        Groups.bullet.each(b -> {if(!existing.contains(b.id))fragments.add(b);});
        check(fragments.size==20,"incorrect fragment count");
        float minSpeed=Float.MAX_VALUE,maxSpeed=0f;
        for(int i=0;i<fragments.size;i++){
            Bullet child=fragments.get(i);
            minSpeed=Math.min(minSpeed,child.vel.len());maxSpeed=Math.max(maxSpeed,child.vel.len());
            float angle=child.rotation();child.time=1f;child.mover.move(child);
            check(Angles.angleDist(child.rotation(),angle+(i%2==0?5f:-5f))<0.15f,"fragment rings do not counter-rotate");
        }
        check(maxSpeed/minSpeed>1.5f,"fragment rings lack distinct speeds");
        check(turret.heatRequirement<=0f && turret.consPower==null,"unexpected heat/power requirement");
        System.out.println("PASS: two tungsten per shot, four rotating petals, alternating curl, 20 counter-rotating spiral fragments, shooter attribution and native Afflict preserved");
    }
}
