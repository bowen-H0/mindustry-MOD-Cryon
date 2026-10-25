package cryon.Unity;

import arc.struct.Seq;
import cryon.Type.*;
import mindustry.Vars;
import mindustry.ctype.ContentType;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.*;
import mindustry.type.StatusEffect;
import java.lang.reflect.*;

/** Delegates carrier geometry/physics while combining status and custom impact traits. */
public class UnityHybridBullet extends BulletType{
    public final BulletType carrier;
    private final Seq<StatusEffect> statuses;
    private final Seq<Float> durations;
    private final Seq<BeaconBulletType> beacons;
    private final Seq<KismetBulletType> links;
    public UnityHybridBullet(BulletType carrier,Seq<StatusEffect> statuses,Seq<Float> durations,Seq<BeaconBulletType> beacons,Seq<KismetBulletType> links){
        this.carrier=carrier;this.statuses=statuses;this.durations=durations;this.beacons=beacons;this.links=links;
        Vars.content.getBy(ContentType.bullet).remove(this,true);id=carrier.id;
        copyFields(carrier,this);
    }
    private static void copyFields(BulletType from,BulletType to){
        for(Field f:BulletType.class.getFields())if(!Modifier.isStatic(f.getModifiers()) && !Modifier.isFinal(f.getModifiers())){
            try{f.set(to,f.get(from));}catch(IllegalAccessException e){throw new IllegalStateException(e);}
        }
    }
    public void refresh(){copyFields(carrier,this);}
    public void targetTraits(boolean air,boolean ground){
        for(BeaconBulletType beacon:beacons){beacon.fusedTargeting=true;beacon.collidesAir=air;beacon.collidesGround=ground;}
    }
    private void traits(Bullet b,float x,float y){
        for(BeaconBulletType beacon:beacons)beacon.fuseAt(b.team,x,y);
        for(KismetBulletType link:links)link.fuseAt(b,x,y);
    }
    @Override public void init(Bullet b){carrier.init(b);}
    @Override public void update(Bullet b){
        boolean instant=(carrier instanceof BeaconBulletType || carrier instanceof KismetBulletType) && b.time<1f;
        if(instant){
            traits(b,b.aimX,b.aimY);
            if(carrier instanceof KismetBulletType && b.damage>0f)mindustry.entities.Damage.damage(b.team,b.aimX,b.aimY,Math.max(8f,carrier.hitSize),b.damage);
            carrier.createSplashDamage(b,b.aimX,b.aimY);
            if(carrier.fragBullet!=null)carrier.createFrags(b,b.aimX,b.aimY);
            if(carrier.intervalBullet!=null)for(int i=0;i<Math.max(1,carrier.intervalBullets);i++)carrier.intervalBullet.create(b,b.aimX,b.aimY,b.rotation());
        }
        carrier.update(b);
    }
    @Override public void draw(Bullet b){carrier.draw(b);}
    @Override public void drawLight(Bullet b){carrier.drawLight(b);}
    @Override public void hit(Bullet b,float x,float y){carrier.hit(b,x,y);}
    @Override public void hitEntity(Bullet b,Hitboxc target,float health){
        carrier.hitEntity(b,target,health);traits(b,target.x(),target.y());
        if(target instanceof Unit){Unit unit=(Unit)target;for(int i=0;i<statuses.size;i++)unit.apply(statuses.get(i),durations.get(i));}
    }
    @Override public void hitTile(Bullet b,Building target,float x,float y,float health,boolean direct){carrier.hitTile(b,target,x,y,health,direct);if(direct)traits(b,x,y);}
    @Override public void despawned(Bullet b){carrier.despawned(b);traits(b,b.x,b.y);}
    @Override public void removed(Bullet b){carrier.removed(b);}
    @Override public boolean testCollision(Bullet b,Building target){return carrier.testCollision(b,target);}
}
