package cryon.Type;

import arc.Events;
import arc.graphics.Color;
import arc.graphics.Blending;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Time;
import cryon.Features.MapAlliances;
import cryon.Features.MechAudio;
import mindustry.Vars;
import mindustry.entities.*;
import mindustry.entities.units.WeaponMount;
import mindustry.game.Team;
import mindustry.game.EventType.ResetEvent;
import mindustry.gen.*;
import mindustry.graphics.*;
import java.util.IdentityHashMap;

/** All T5 mechanics belong to its weapon: proximity deployment, swept cuts and escalating marks. */
public class SuperluminalBladeWeapon extends EnergyBladeWeapon{
    public static final int maxMarks = 8;
    public static final float markDuration = 480f;
    public static final Color flame = Color.valueOf("aeafff"), hot = Color.valueOf("efffff");
    public final float reach;
    private final IdentityHashMap<Healthc, Mark> marks = new IdentityHashMap<>();
    private float cleanupAt=-Float.MAX_VALUE;
    private final Vec2 originTmp=new Vec2(),ghostShoulder=new Vec2(),ghostWrist=new Vec2(),ghostElbow=new Vec2();
    private static class Mark{
        int strength;
        float expires;
        Team team;
        SlaughterMount owner;
    }
    public static class SlaughterMount extends BladeMount{
        public boolean slaughter;
        public boolean blocked;
        public boolean resolved;
        public float attackAngle;
        public float blockedDistance = Float.POSITIVE_INFINITY;
        public float deployment, scan;
        public int trailHead, trailCount;
        public float trailTimer;
        public final float[] trailX=new float[12], trailY=new float[12], trailRotation=new float[12],
            trailAngle=new float[12], trailLength=new float[12], trailLife=new float[12];
        public final ObjectSet<Healthc> struck = new ObjectSet<>();
        private final ObjectSet<Healthc> markedTargets=new ObjectSet<>();
        private final Seq<Healthc> candidates=new Seq<>();
        public SlaughterMount(mindustry.type.Weapon weapon){ super(weapon); }
    }
    public static final Effect discharge = new Effect(28f, 750f, e -> {
        float strength = Mathf.clamp(e.rotation, 0, maxMarks), size = 22f + strength * 9f;
        Draw.color(flame, hot, e.fout());
        Lines.stroke((2f + strength * 0.3f) * e.fout());
        Lines.circle(e.x, e.y, e.finpow() * size);
        Angles.randLenVectors(e.id, 30 + (int)strength * 8, 4f + e.finpow() * size * 1.7f, (x, y) -> {
            float angle = Mathf.angle(x, y);
            Lines.lineAngle(e.x+x, e.y+y, angle, (3f + strength * 1.3f) * e.fout());
            Drawf.tri(e.x+x, e.y+y, 1.2f * e.fout(), (7f + strength) * e.fout(), angle);
        });
        Draw.color(Color.white, e.fout());
        Drawf.tri(e.x, e.y, 3f * e.fout(), size * 1.4f * e.fout(), 45f);
        Drawf.tri(e.x, e.y, 3f * e.fout(), size * 1.4f * e.fout(), 225f);
        Drawf.light(e.x, e.y, size * 2f, flame, e.fout() * 0.9f);
        Draw.reset();
    });

    public SuperluminalBladeWeapon(String name, float scale, float damage){
        super(name, scale, damage, 8f);
        reach = (11f + 18f * 5f) * scale;
        bullet.rangeOverride = reach;
        // ShrapnelBulletType calculates range from length/maxRange, ignoring rangeOverride.
        // Native AI and Weapon.update must see the same reach as the deployed swept blade.
        bullet.maxRange = reach;
        shootCone = 24f;
        mountType = SlaughterMount::new;
        Events.on(ResetEvent.class, e -> {
            for(Mark mark:marks.values()) if(mark.owner!=null) mark.owner.markedTargets.clear();
            marks.clear();cleanupAt=-Float.MAX_VALUE;
        });
    }

    public int markStrength(Healthc target){
        Mark mark = marks.get(target);
        return mark != null && mark.expires > Time.time && target.isAdded() && !target.dead() ? mark.strength : 0;
    }
    @Override protected float animationRate(Unit unit, BladeMount blade){
        return ((SlaughterMount)blade).slaughter ? 4f : 1f;
    }
    @Override protected float bladeLength(Unit unit, BladeMount blade){
        SlaughterMount state = (SlaughterMount)blade;
        return Math.min(18f * bladeScale * (1f + 4f * state.deployment),
            Math.max(0f, state.blockedDistance - 11f * bladeScale));
    }
    @Override protected void beginSwing(BladeMount blade, boolean defensive){
        super.beginSwing(blade, defensive);
        ((SlaughterMount)blade).struck.clear();
        ((SlaughterMount)blade).blocked = false;
        ((SlaughterMount)blade).resolved = false;
        ((SlaughterMount)blade).blockedDistance = Float.POSITIVE_INFINITY;
    }
    @Override protected void shoot(Unit unit, WeaponMount mount, float x, float y, float rotation){
        // Damage follows the visible swept edge instead of a delayed straight shrapnel beam.
        SlaughterMount blade = (SlaughterMount)mount;
        beginSwing(blade, false);
        blade.attackAngle = Angles.angle(unit.x,unit.y,mount.aimX,mount.aimY);
        mount.totalShots++; mount.barrelCounter++;
        mount.reload = blade.slaughter ? reload : 36f;
    }
    @Override public void update(Unit unit, WeaponMount mount){
        SlaughterMount blade = (SlaughterMount)mount;
        blade.scan -= Time.delta;
        if(blade.scan <= 0){
            blade.scan = 4f;
            blade.slaughter = unit.canShoot() && Units.closestTarget(unit.team, unit.x, unit.y, reach,
                enemy -> !enemy.isFlying() && !MapAlliances.allied(unit.team, enemy.team),
                build -> !MapAlliances.allied(unit.team, build.team)) != null;
        }
        if(!unit.canShoot() || unit.dead) blade.slaughter = false;
        blade.deployment = Mathf.approachDelta(blade.deployment, blade.slaughter ? 1f : 0f, 0.08f);
        float before = blade.age;
        int combo = blade.combo;
        super.update(unit, mount);
        if(combo != blade.combo) before = blade.age;
        if(unit.canShoot() && !unit.dead && !blade.defensive && !blade.resolved && blade.age >= 8f && before < 12f){
            blade.resolved=true;
            strikeLine(unit,blade);
        }
        for(int i=0;i<blade.trailLife.length;i++) blade.trailLife[i]=Math.max(0f,blade.trailLife[i]-Time.delta);
        blade.trailTimer+=Time.delta;
        if(!unit.dead && blade.trailTimer>=1f && (unit.vel.len2()>0.16f || (blade.slaughter && blade.age<26f))){
            blade.trailTimer%=1f;
            int index=blade.trailHead;
            Vec2 origin=poseOrigin(unit,blade,blade.age,originTmp);
            blade.trailX[index]=origin.x;blade.trailY[index]=origin.y;
            blade.trailRotation[index]=unit.rotation+torsoTwist(unit);
            blade.trailAngle[index]=unit.rotation+poseAngle(blade,blade.age);
            blade.trailLength[index]=bladeLength(unit,blade);
            blade.trailLife[index]=14f;
            blade.trailHead=(index+1)%blade.trailX.length;
            blade.trailCount=Math.min(blade.trailCount+1,blade.trailX.length);
        }
        if(Time.time-cleanupAt >= 60f){
            cleanupAt = Time.time;
            marks.entrySet().removeIf(entry -> {
                boolean expired=entry.getValue().expires<=Time.time||!entry.getKey().isAdded()||entry.getKey().dead();
                if(expired&&entry.getValue().owner!=null) entry.getValue().owner.markedTargets.remove(entry.getKey());
                return expired;
            });
        }
    }
    private float circleEntry(float ox, float oy, float dx, float dy, float x, float y, float radius, float end){
        float xrel=x-ox, yrel=y-oy, along=xrel*dx+yrel*dy;
        float side2=xrel*xrel+yrel*yrel-along*along;
        if(side2>radius*radius) return Float.POSITIVE_INFINITY;
        float half=Mathf.sqrt(Math.max(0f,radius*radius-side2)), near=along-half, far=along+half;
        return far>=4f*bladeScale && near<=end ? Math.max(4f*bladeScale,near) : Float.POSITIVE_INFINITY;
    }
    private float boxEntry(float ox,float oy,float dx,float dy,float x,float y,float half,float end){
        float near=4f*bladeScale, far=end;
        if(Math.abs(dx)<0.00001f){ if(ox<x-half||ox>x+half) return Float.POSITIVE_INFINITY; }
        else{
            float a=(x-half-ox)/dx,b=(x+half-ox)/dx;
            near=Math.max(near,Math.min(a,b));far=Math.min(far,Math.max(a,b));
        }
        if(Math.abs(dy)<0.00001f){ if(oy<y-half||oy>y+half) return Float.POSITIVE_INFINITY; }
        else{
            float a=(y-half-oy)/dy,b=(y+half-oy)/dy;
            near=Math.max(near,Math.min(a,b));far=Math.min(far,Math.max(a,b));
        }
        return near<=far ? near : Float.POSITIVE_INFINITY;
    }
    private void strikeLine(Unit unit, SlaughterMount blade){
        if(blade.blocked) return;
        float range=11f*bladeScale+bladeLength(unit,blade)+32f;
        Seq<Healthc> candidates=blade.candidates;candidates.clear();
        float angle = blade.attackAngle;
        Vec2 origin = poseOrigin(unit, blade, 8f,originTmp);
        float end = 11f * bladeScale + bladeLength(unit, blade);
        float dx=Angles.trnsx(angle,1f),dy=Angles.trnsy(angle,1f);
        float sx=origin.x+dx*4f*bladeScale,sy=origin.y+dy*4f*bladeScale;
        float ex=origin.x+dx*end,ey=origin.y+dy*end,pad=2.5f*bladeScale+1f;
        // Quad-tree queries already include target hitbox extents; only scan the blade corridor.
        Units.nearbyEnemies(unit.team,Math.min(sx,ex)-pad,Math.min(sy,ey)-pad,
            Math.abs(ex-sx)+pad*2f,Math.abs(ey-sy)+pad*2f,target -> {
            if(!target.dead&&!target.isFlying()&&!MapAlliances.allied(unit.team,target.team)) candidates.add(target);
        });
        Units.nearbyBuildings(unit.x,unit.y,range,target -> {
            if(!target.dead&&(!MapAlliances.allied(unit.team,target.team)||target.block.solid)) candidates.add(target);
        });
        // Resolve the aimed strike, not whichever side a wide animation crosses first.
        Healthc first=null;float nearest=end+1f;
        for(Healthc target:candidates){
            float distance=target instanceof Building
                ? boxEntry(origin.x,origin.y,dx,dy,target.x(),target.y(),((Building)target).block.size*Vars.tilesize/2f+2.5f*bladeScale,end)
                : circleEntry(origin.x,origin.y,dx,dy,target.x(),target.y(),((Unit)target).hitSize/2f+2.5f*bladeScale,end);
            if(distance<nearest){ nearest=distance;first=target; }
        }
        candidates.clear();
        if(nearest<=end){
            blade.blocked=true;blade.blockedDistance=nearest;
            if(first!=null && !MapAlliances.allied(unit.team,((Teamc)first).team())) strike(unit,blade,first);
            else{
                float x=origin.x+dx*nearest,y=origin.y+dy*nearest;
                discharge.at(x,y,0f,flame);
                MechAudio.play(MechAudio.Cue.slaughter,unit,x,y,bladeScale);
                blade.hold=Math.max(blade.hold,0.6f);
            }
            return;
        }
    }
    private void strike(Unit unit, SlaughterMount blade, Healthc target){
        if(!blade.struck.add(target)) return;
        Mark mark = marks.get(target);
        int strength = mark != null && mark.expires > Time.time && MapAlliances.allied(unit.team,mark.team) ? mark.strength : 0;
        float damage = bullet.damage * Math.min(5f, 1f + strength) * unit.damageMultiplier;
        if(target instanceof Tankc) damage *= trackedDamageMultiplier;
        if(!Vars.net.client()) target.damage(damage);
        if(mark == null){ mark = new Mark(); marks.put(target,mark); }
        if(mark.owner!=blade){
            if(mark.owner!=null) mark.owner.markedTargets.remove(target);
            blade.markedTargets.add(target);mark.owner=blade;
        }
        mark.team = unit.team;
        mark.strength = Math.min(maxMarks,strength+1);
        mark.expires = Time.time+markDuration;
        discharge.at(target.x(),target.y(),strength,flame);
        if(!blade.hitSoundPlayed){
            blade.hitSoundPlayed = true;
            MechAudio.play(MechAudio.Cue.slaughter,unit,target.x(),target.y(),bladeScale);
            if(!Vars.headless) Effect.shake(2f+strength*0.15f,4f,target.x(),target.y());
        }
        blade.hold = Math.max(blade.hold,0.6f);
    }

    @Override public void draw(Unit unit, WeaponMount mount){
        drawAfterimages(unit,(SlaughterMount)mount);
        super.draw(unit,mount);
        SlaughterMount blade = (SlaughterMount)mount;
        float z = Draw.z();
        Draw.z(Layer.effect);
        if(blade.deployment > 0f){
            float angle = unit.rotation+poseAngle(blade,blade.age);
            Vec2 origin = poseOrigin(unit,blade,blade.age,originTmp);
            float length = bladeLength(unit,blade);
            float sx = origin.x+Angles.trnsx(angle,11f*bladeScale), sy = origin.y+Angles.trnsy(angle,11f*bladeScale);
            Draw.blend(Blending.additive);
            for(int i=0;i<9;i++){
                float fraction = i/9f, wave = Mathf.sin(Time.time*0.7f+i*2f)*bladeScale*1.2f;
                float x = sx+Angles.trnsx(angle,length*fraction)+Angles.trnsx(angle+90f,wave);
                float y = sy+Angles.trnsy(angle,length*fraction)+Angles.trnsy(angle+90f,wave);
                Draw.color(i%2==0?flame:energy,0.38f*blade.deployment);
                Drawf.tri(x,y,(4f+2f*Mathf.absin(Time.time+i,2f,1f))*bladeScale,
                    Math.min(length*(1f-fraction),length*0.4f),angle);
            }
            Draw.blend();
            if(blade.age>=6f && blade.age<16f){
                Draw.color(flame,0.2f*blade.deployment);
                Lines.stroke(8f*bladeScale);
                Lines.arc(unit.x,unit.y,length+7f*bladeScale,0.34f,angle-blade.swingSide*90f);
                Draw.color(hot,0.8f*blade.deployment); Lines.stroke(1.5f);
                Lines.arc(unit.x,unit.y,length+7f*bladeScale,0.34f,angle-blade.swingSide*90f);
            }
        }
        // Each target's mark is drawn once, by its latest attacker, rather than by every T5.
        for(Healthc target:blade.markedTargets){
            Mark mark=marks.get(target);
            if(mark==null) continue;
            if(mark.expires<=Time.time||!target.isAdded()||target.dead()||!MapAlliances.allied(unit.team,mark.team)
                || !unit.within(target,reach+120f) || (target instanceof Unit && ((Unit)target).inFogTo(unit.team))) continue;
            float size=7f+mark.strength*1.8f;
            Draw.color(flame,0.8f);Lines.stroke(1f+mark.strength*0.12f);
            Lines.poly(target.x(),target.y(),4,size,45f);
            for(int i=0;i<mark.strength;i++) Lines.lineAngle(target.x(),target.y()+size+4f+i*2f,0f,5f);
        }
        Draw.reset();Draw.z(z);
    }

    private void ghostArm(TextureRegion region,Vec2 from,Vec2 to,int side){
        Draw.rect(region,(from.x+to.x)/2f,(from.y+to.y)/2f,
            region.width*Draw.scl*bladeScale*side,from.dst(to)+2f*bladeScale,from.angleTo(to)-90f);
    }
    private void drawAfterimages(Unit unit,SlaughterMount blade){
        float z=Draw.z();Draw.z(Layer.groundUnit-0.03f);
        for(int age=blade.trailCount-1;age>=0;age--){
            int i=(blade.trailHead-age-1+blade.trailX.length)%blade.trailX.length;
            if(blade.trailLife[i]<=0f) continue;
            float alpha=0.42f*blade.trailLife[i]/14f*(1f-age/(float)(blade.trailX.length+1));
            Color tint=age%2==0?flame:energy;
            float x=blade.trailX[i],y=blade.trailY[i],body=blade.trailRotation[i],angle=blade.trailAngle[i];
            Draw.color(Color.white,alpha);Draw.mixcol(tint,1f);
            Draw.rect(unit.type.baseRegion,x,y,body-90f);
            for(int side=-1;side<=1;side+=2){
                Vec2 shoulder=ghostShoulder.trns(body,0f,side*5f*bladeScale).add(x,y);
                Vec2 wrist=ghostWrist.trns(angle,(side==1?9f:5f)*bladeScale).add(x,y);
                Vec2 elbow=EnergyBladeWeapon.elbow(shoulder,wrist,side,7f*bladeScale,ghostElbow);
                ghostArm(upperArmRegion,shoulder,elbow,side);
                ghostArm(forearmRegion,elbow,wrist,side);
            }
            Draw.rect(unit.type.region,x,y,body-90f);
            Draw.rect(hiltRegion,x+Angles.trnsx(angle,7f*bladeScale),y+Angles.trnsy(angle,7f*bladeScale),
                hiltRegion.width*Draw.scl*bladeScale,hiltRegion.height*Draw.scl*bladeScale,angle-90f);
            Draw.mixcol();Draw.color(tint,alpha*0.8f);Lines.stroke(2.2f*bladeScale);
            Lines.lineAngle(x+Angles.trnsx(angle,11f*bladeScale),y+Angles.trnsy(angle,11f*bladeScale),angle,blade.trailLength[i]);
        }
        Draw.reset();Draw.z(z);
    }
}
