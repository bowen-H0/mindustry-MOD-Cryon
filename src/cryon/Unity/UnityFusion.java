package cryon.Unity;

import arc.math.Mathf;
import arc.struct.Seq;
import cryon.Type.*;
import mindustry.Vars;
import mindustry.ctype.ContentType;
import mindustry.entities.bullet.*;
import mindustry.type.*;
import mindustry.world.blocks.defense.turrets.*;

/** One carrier with recursively fused descendants, never one independent gun per ingredient. */
public class UnityFusion{
    public final BulletType bullet;
    public final float range,reload,rotateSpeed;
    public final boolean targetAir,targetGround;
    private final Seq<BulletType> wireTypes=new Seq<>();
    public UnityFusion(UnityBlueprint recipe){
        Seq<BulletType> roots=new Seq<>();float baseRange=0,rangeBonus=0,logReload=0,rotation=0;int airVotes=0,groundVotes=0;
        for(int i=0;i<recipe.modules.size;i++){
            UnityBlueprint.Module spec=recipe.modules.get(i);Turret t=spec.source();
            roots.add(ammo(spec));
            if(i==0)baseRange=t.range;else rangeBonus+=Math.max(0f,t.range-baseRange)*0.25f/(float)Math.sqrt(i);
            logReload+=Math.log(Math.max(1f,t.reload/Math.max(0.001f,roots.peek().reloadMultiplier)));rotation+=t.rotateSpeed;
            if(t.targetAir)airVotes++;if(t.targetGround)groundVotes++;
        }
        int count=Math.max(1,roots.size);
        range=Math.max(8f,baseRange+Math.min(baseRange*0.75f,rangeBonus));
        reload=(float)Math.exp(logReload/count);rotateSpeed=rotation/count;
        targetAir=airVotes>0 && airVotes>=groundVotes;targetGround=groundVotes>0 && groundVotes>=airVotes;
        bullet=roots.isEmpty()?null:mix(roots,0,recipe.color,false);
        if(bullet!=null){
            scaleRange(bullet,range);
            UnityHybridBullet hybrid=(UnityHybridBullet)bullet;
            int shots=1;for(UnityBlueprint.Module spec:recipe.modules)shots=Math.max(shots,spec.source().shoot.shots);
            if(shots>1 && hybrid.carrier.fragBullet==null){
                BulletType shard=privateCopy(hybrid.carrier);shard.damage*=0.45f;shard.fragBullet=null;shard.intervalBullet=null;shard.spawnBullets=new Seq<>();
                hybrid.carrier.fragBullet=shard;hybrid.carrier.fragBullets=Math.min(8,shots);hybrid.carrier.fragOnHit=hybrid.carrier.fragOnDespawn=true;wireTypes.add(shard);
            }
            applyTargeting(bullet,new arc.struct.ObjectSet<BulletType>(),new java.util.IdentityHashMap<BulletType,BulletType>());
        }
    }
    private void applyTargeting(BulletType type,arc.struct.ObjectSet<BulletType> visited,java.util.IdentityHashMap<BulletType,BulletType> copies){
        if(type==null || !visited.add(type))return;
        BulletType actual=type instanceof UnityHybridBullet?((UnityHybridBullet)type).carrier:type;
        actual.collidesAir=targetAir;actual.collidesGround=targetGround;
        actual.collidesTiles=targetGround;
        if(actual instanceof BeaconBulletType)((BeaconBulletType)actual).fusedTargeting=true;
        applyTargeting(actual.fragBullet,visited,copies);applyTargeting(actual.intervalBullet,visited,copies);
        if(actual.lightningType!=null){
            BulletType original=actual.lightningType,copy=copies.get(original);
            if(copy==null){copy=privateCopy(original);copies.put(original,copy);wireTypes.add(copy);}
            actual.lightningType=copy;applyTargeting(copy,visited,copies);
        }
        if(type instanceof UnityHybridBullet){((UnityHybridBullet)type).targetTraits(targetAir,targetGround);((UnityHybridBullet)type).refresh();}
    }
    public static BulletType ammo(UnityBlueprint.Module spec){
        Turret t=spec.source();
        if(t instanceof ItemTurret)return ((ItemTurret)t).ammoTypes.get(Vars.content.item(spec.ammo));
        if(t instanceof LiquidTurret)return ((LiquidTurret)t).ammoTypes.get(Vars.content.liquid(spec.ammo));
        if(t instanceof ContinuousLiquidTurret)return ((ContinuousLiquidTurret)t).ammoTypes.get(Vars.content.liquid(spec.ammo));
        if(t instanceof PowerTurret)return ((PowerTurret)t).shootType;
        if(t instanceof ContinuousTurret)return ((ContinuousTurret)t).shootType;
        throw new IllegalArgumentException("Unsupported fusion source: "+t.name);
    }
    public static BulletType privateCopy(BulletType original){
        BulletType copy=original.copy();Vars.content.getBy(ContentType.bullet).remove(copy,true);copy.id=original.id;return copy;
    }
    private BulletType mix(Seq<BulletType> ingredients,int depth,String color,boolean child){
        BulletType carrier=ingredients.first();
        // A laser-producing fragment must remain a laser, even when mixed after ordinary fragments.
        if(child)for(BulletType b:ingredients)if(b instanceof LaserBulletType || b instanceof ContinuousBulletType){carrier=b;break;}
        BulletType out=privateCopy(carrier);
        Seq<BulletType> frags=new Seq<>(),intervals=new Seq<>();
        Seq<BeaconBulletType> beacons=new Seq<>();Seq<KismetBulletType> links=new Seq<>();
        Seq<StatusEffect> statuses=new Seq<>();Seq<Float> durations=new Seq<>();
        int fragCount=0,intervalCount=0,pierceCap=0;boolean unlimited=false,fragGeometry=false,fragHit=false,fragDespawn=false;float splashLog=0;int splashCount=0;float damageLog=0;int damages=0;
        for(BulletType b:ingredients){
            float damage=effectiveDamage(b);if(damage>0){damageLog+=Math.log(damage);damages++;}
            if(b.splashDamage>0){splashLog+=Math.log(b.splashDamage);splashCount++;}
            out.splashDamageRadius=Math.max(out.splashDamageRadius,b.splashDamageRadius);
            out.pierceDamageFactor=Math.max(out.pierceDamageFactor,b.pierceDamageFactor);
            out.pierce|=b.pierce;out.pierceBuilding|=b.pierceBuilding;out.pierceArmor|=b.pierceArmor;
            if(b.pierce || b.pierceBuilding){if(b.pierceCap<0)unlimited=true;else pierceCap=Math.max(pierceCap,b.pierceCap);}
            if(b.homingPower>out.homingPower)out.homingDelay=b.homingDelay;
            out.homingPower=Math.max(out.homingPower,b.homingPower);out.homingRange=Math.max(out.homingRange,b.homingRange);
            if(Math.abs(b.weaveMag)>Math.abs(out.weaveMag)){out.weaveMag=b.weaveMag;out.weaveScale=b.weaveScale;}
            out.lightning=Math.max(out.lightning,b.lightning);out.lightningLength=Math.max(out.lightningLength,b.lightningLength);
            out.lightningDamage=Math.max(out.lightningDamage,b.lightningDamage);
            if(out.lightningType==null)out.lightningType=b.lightningType;
            if(b.puddles>out.puddles){out.puddles=b.puddles;out.puddleLiquid=b.puddleLiquid;out.puddleAmount=b.puddleAmount;out.puddleRange=b.puddleRange;}
            out.lifesteal=Math.max(out.lifesteal,b.lifesteal);
            out.makeFire|=b.makeFire;out.incendAmount=Math.max(out.incendAmount,b.incendAmount);out.incendChance=Math.max(out.incendChance,b.incendChance);
            out.knockback=Math.max(out.knockback,b.knockback);out.suppressionRange=Math.max(out.suppressionRange,b.suppressionRange);
            out.suppressionDuration=Math.max(out.suppressionDuration,b.suppressionDuration);
            if(b.fragBullet!=null){
                frags.add(b.fragBullet);fragCount=Math.max(fragCount,b.fragBullets);fragHit|=b.fragOnHit;fragDespawn|=b.fragOnDespawn;
                if(!fragGeometry){
                    out.fragRandomSpread=b.fragRandomSpread;out.fragSpread=b.fragSpread;out.fragAngle=b.fragAngle;
                    out.fragVelocityMin=b.fragVelocityMin;out.fragVelocityMax=b.fragVelocityMax;out.fragLifeMin=b.fragLifeMin;out.fragLifeMax=b.fragLifeMax;fragGeometry=true;
                }
            }
            if(b!=carrier && (b instanceof LaserBulletType || b instanceof ContinuousBulletType || b instanceof RailBulletType)){
                intervals.add(b);intervalCount=Math.max(1,intervalCount);out.bulletInterval=Math.max(10f,out.bulletInterval);
            }
            if(b.intervalBullet!=null){intervals.add(b.intervalBullet);intervalCount=Math.max(intervalCount,b.intervalBullets);out.bulletInterval=Math.max(4f,Math.min(out.bulletInterval,b.bulletInterval));}
            if(b.spawnBullets.any()){frags.addAll(b.spawnBullets);fragCount=Math.max(fragCount,b.spawnBullets.size);fragHit=fragDespawn=true;}
            if(b.status!=null && b.status!=mindustry.content.StatusEffects.none){
                int index=statuses.indexOf(b.status,true);
                if(index<0){statuses.add(b.status);durations.add(b.statusDuration);}else durations.set(index,Math.max(durations.get(index),b.statusDuration));
            }
            if(b!=carrier && b instanceof BeaconBulletType){BeaconBulletType copy=(BeaconBulletType)privateCopy(b);beacons.add(copy);}
            if(b!=carrier && b instanceof KismetBulletType)links.add((KismetBulletType)privateCopy(b));
        }
        if(out.pierce || out.pierceBuilding)out.pierceCap=unlimited?-1:Math.max(1,pierceCap);
        float synergy=1f+0.18f*(float)Math.log(ingredients.size);
        out.damage=damages==0?0f:(float)Math.exp(damageLog/damages)*synergy;
        out.splashDamage=splashCount==0?0f:(float)Math.exp(splashLog/splashCount)*synergy;
        if(out instanceof BeaconBulletType)((BeaconBulletType)out).strikeDamage=out.damage;
        for(BeaconBulletType b:beacons)b.strikeDamage=out.damage*0.6f;
        out.spawnBullets=new Seq<>();out.shootPattern=null;
        if(depth<4){
            out.fragBullet=frags.isEmpty()?null:mix(frags,depth+1,color,true);
            out.fragBullets=Math.min(depth==0?16:depth==1?4:2,fragCount);out.fragOnHit=out.fragBullet!=null && fragHit;out.fragOnDespawn=out.fragBullet!=null && fragDespawn;
            out.intervalBullet=intervals.isEmpty()?null:mix(intervals,depth+1,color,true);out.intervalBullets=Math.min(depth==0?4:1,intervalCount);
        }else{out.fragBullet=null;out.intervalBullet=null;}
        if(!color.isEmpty())out=new UnityPalette(color).apply(out);
        UnityHybridBullet hybrid=new UnityHybridBullet(out,statuses,durations,beacons,links);
        wireTypes.add(hybrid);return hybrid;
    }
    private static float effectiveDamage(BulletType b){return b instanceof BeaconBulletType?((BeaconBulletType)b).strikeDamage:b.damage;}
    private static void scaleRange(BulletType b,float range){
        BulletType actual=b instanceof UnityHybridBullet?((UnityHybridBullet)b).carrier:b;
        if(actual instanceof LaserBulletType)((LaserBulletType)actual).length=range;
        else if(actual instanceof ContinuousLaserBulletType)((ContinuousLaserBulletType)actual).length=range;
        else if(actual instanceof ContinuousFlameBulletType)((ContinuousFlameBulletType)actual).length=range;
        else if(actual instanceof RailBulletType)((RailBulletType)actual).length=range;
        else if(actual.speed>0.001f)actual.lifetime=Math.max(actual.lifetime,range/actual.speed);
        actual.range=actual.rangeOverride=range;
    }
    public BulletType remap(BulletType original,float damage){
        BulletType best=null;float distance=Float.MAX_VALUE;
        for(BulletType candidate:wireTypes)if(candidate.id==original.id){
            float d=Math.abs(candidate.damage-damage);if(d<distance){distance=d;best=candidate;}
        }
        return best==null?original:best;
    }
}
