package cryon.Type;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.Blending;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.Vec2;
import arc.math.geom.Intersector;
import arc.util.Time;
import cryon.Features.MapAlliances;
import cryon.Features.MechAudio;
import cryon.Fx.SubsonicParryFx;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.bullet.ShrapnelBulletType;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.Weapon;

/** Native delayed melee hit; the same mount owns wind-up, IK pose and impact hold. */
public class EnergyBladeWeapon extends Weapon{
    public TextureRegion upperArmRegion, forearmRegion, handRegion, hiltRegion;
    public final String spritePrefix;
    public final float bladeScale;
    public final int maxParries;
    private final Vec2 poseTmp=new Vec2(), contactTmp=new Vec2(), frontTmp=new Vec2(), rearTmp=new Vec2(),
        shoulderTmp=new Vec2(), elbowTmp=new Vec2(), hiltTmp=new Vec2(), guardTmp=new Vec2(), tipTmp=new Vec2();
    public static final float trackedDamageMultiplier = 0.3f;
    public static final Color energy = Color.valueOf("78f2ed"), steel = Color.valueOf("7895ab"), dark = Color.valueOf("263442");
    public static final Effect sparks = new Effect(19, e -> {
        Draw.color(energy, Color.white, e.fout());
        Lines.stroke(1.4f * e.fout());
        Angles.randLenVectors(e.id, 9, 3 + e.fin() * 20, e.rotation, 85, (x, y) ->
            Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 2 + 5 * e.fout()));
    });

    public static class BladeMount extends WeaponMount{
        public float age = 30, hold;
        public int swingSide = 1;
        public int combo = -1;
        public boolean parryHold, defensive;
        public float parryFlash, parryX, parryY;
        public int parried;
        public boolean swishPlayed, hitSoundPlayed;
        private float[] parryEdges;
        private int parryEdgeCount;
        public BladeMount(Weapon weapon){ super(weapon); }
    }

    public EnergyBladeWeapon(){
        this("subsonic", 1f, 180f, 50f);
    }

    public EnergyBladeWeapon(String unitName, float scale, float strikeDamage, float strikeReload){
        super(unitName + "-blade");
        spritePrefix = "cryon-" + unitName;
        bladeScale = scale;
        maxParries = scale >= 2.7f ? 5 : scale >= 2 ? 4 : scale > 1 ? 3 : 2;
        mirror = false;
        rotate = false;
        x = y = shootY = 0;
        reload = strikeReload;
        shootCone = 24;
        predictTarget = false;
        recoil = shake = 0;
        shootSound = Sounds.none;
        soundPitchMin = 1.5f;
        soundPitchMax = 1.7f;
        shootSoundVolume = 0.35f;
        shoot.firstShotDelay = 8;
        mountType = BladeMount::new;
        bullet = new ShrapnelBulletType(){
            {
                length = 29 * scale;
                width = 9 * scale;
                damage = strikeDamage;
                lifetime = 1;
                hitSize = 6 * scale;
                collidesAir = false;
                shootEffect = smokeEffect = despawnEffect = Fx.none;
                hitEffect = sparks;
                hitColor = energy;
                hitShake = 0;
                serrations = 0;
            }
            @Override public void draw(Bullet b){}
            @Override public void hitEntity(Bullet b, Hitboxc entity, float health){
                if(entity instanceof Teamc && MapAlliances.allied(b.team, ((Teamc)entity).team())) return;
                // Blade strikes excel against light targets, but tracked armour resists them.
                float damage = b.damage;
                try{
                    if(entity instanceof Tankc) b.damage *= trackedDamageMultiplier;
                    super.hitEntity(b, entity, health);
                }finally{
                    b.damage = damage;
                }
                impact(b, entity.x(), entity.y());
            }
            @Override public void hitTile(Bullet b, Building build, float x, float y, float health, boolean direct){
                if(MapAlliances.allied(b.team, build.team)) return;
                super.hitTile(b, build, x, y, health, direct);
                if(direct) impact(b, x, y);
            }
        };
    }

    private static void impact(Bullet bullet, float x, float y){
        if(bullet.owner instanceof Unit){
            Unit unit = (Unit)bullet.owner;
            for(WeaponMount mount : unit.mounts) if(mount instanceof BladeMount){
                BladeMount blade = (BladeMount)mount;
                if(!blade.hitSoundPlayed){
                    blade.hitSoundPlayed = true;
                    MechAudio.play(MechAudio.Cue.armour,
                        unit, x, y, ((EnergyBladeWeapon)blade.weapon).bladeScale);
                }
                if(blade.hold <= 0){
                    blade.hold = 3;
                    blade.age = 8;
                    if(!Vars.headless) Effect.shake(0.8f, 3, x, y);
                }
            }
        }
    }

    @Override protected void shoot(Unit unit, WeaponMount mount, float x, float y, float rotation){
        BladeMount blade = (BladeMount)mount;
        beginSwing(blade, false);
        MechAudio.play(MechAudio.Cue.ready, unit, unit.x, unit.y, bladeScale);
        if(ExtremeOverclockAbility.enhanced(unit)){
            // Match the delayed strike to the accelerated hand animation, per mount.
            mount.charging = true;
            mount.totalShots++;
            mount.barrelCounter++;
            Time.run(shoot.firstShotDelay / ExtremeOverclockAbility.timeScale, () -> {
                mount.charging = false;
                if(unit.isAdded() && !unit.dead && unit.canShoot()) super.bullet(unit, mount, 0, 0, 0, null);
            });
        }else super.shoot(unit, mount, x, y, rotation);
    }

    protected void beginSwing(BladeMount blade, boolean defensive){
        blade.age = defensive ? 6 : 0;
        blade.hold = 0;
        blade.parryHold = false;
        blade.parryFlash = 0;
        blade.parried = 0;
        blade.swishPlayed = blade.hitSoundPlayed = false;
        blade.defensive = defensive;
        blade.combo = (blade.combo + 1) % 4;
        blade.swingSide = -blade.swingSide;
    }

    protected float animationRate(Unit unit, BladeMount blade){
        return ExtremeOverclockAbility.enhanced(unit) ? ExtremeOverclockAbility.timeScale : 1f;
    }

    protected float bladeLength(Unit unit, BladeMount blade){ return 18f * bladeScale; }

    @Override public void update(Unit unit, WeaponMount mount){
        BladeMount blade = (BladeMount)mount;
        float previousAge = blade.age;
        int previousCombo = blade.combo;
        blade.parryFlash = Math.max(0, blade.parryFlash - Time.delta);
        if(blade.hold > 0) blade.hold = Math.max(0, blade.hold - Time.delta);
        else blade.age = Math.min(30, blade.age + Time.delta * animationRate(unit, blade));
        super.update(unit, mount);
        // Like native point defence, remove local projectiles on every peer.
        if(!unit.canShoot()) return;
        if(blade.reload <= 0 && blade.age >= 26 && incoming(unit)){
            // Defensive swings do not create a delayed offensive bullet or spend ammo.
            beginSwing(blade, true);
            blade.reload = reload;
        }
        if(!blade.swishPlayed && blade.age >= 6 && blade.age < 12){
            blade.swishPlayed = true;
            MechAudio.Cue cue = blade.combo >= 2 ? MechAudio.Cue.heavy
                : blade.combo == 1 ? MechAudio.Cue.reverse : MechAudio.Cue.light;
            MechAudio.play(cue, unit, unit.x, unit.y, bladeScale);
        }
        if(previousCombo != blade.combo) previousAge = blade.age;
        if(blade.age >= 6 && previousAge < 12) parry(unit, blade, Math.max(6, previousAge), Math.min(12, blade.age));
    }

    private boolean cuttable(Unit unit, Bullet b){
        return b.isAdded() && !b.absorbed && b.damage > 0 && b.type.hittable
            && !b.vel.isZero() && !MapAlliances.allied(unit.team, b.team);
    }

    private boolean incoming(Unit unit){
        final boolean[] found = {false};
        boolean enhanced = ExtremeOverclockAbility.enhanced(unit);
        float radius = 44 * bladeScale * (enhanced ? 1.6f : 1);
        Groups.bullet.intersect(unit.x - radius, unit.y - radius, radius * 2, radius * 2, b -> {
            if(cuttable(unit, b) && unit.within(b, radius)
                && Angles.within(unit.rotation, unit.angleTo(b), enhanced ? 110 : 75)
                && (!enhanced || unit.dst(b) <= 32 * bladeScale + b.vel.len() * 3)
                && b.vel.dot(unit.x - b.x, unit.y - b.y) > 0) found[0] = true;
        });
        return found[0];
    }

    /** Share the actual blade pose with rendering; sample the swept edge, not a shield circle. */
    private void parry(Unit unit, BladeMount blade, float from, float to){
        if(Groups.bullet.isEmpty()) return;
        float a = poseAngle(blade, from), b = poseAngle(blade, to);
        int steps = Math.max(1, (int)Math.ceil(Math.abs(b - a) / 5f));
        // These edges are identical for every projectile in this update; compute them once.
        blade.parryEdgeCount=0;
        int limit=maxParries+(ExtremeOverclockAbility.enhanced(unit)?2:0);
        float search = 60 * bladeScale;
        Groups.bullet.intersect(unit.x - search, unit.y - search, search * 2, search * 2, shot -> {
            if(blade.parried >= limit || !cuttable(unit, shot)) return;
            if(blade.parryEdgeCount==0){
                if(blade.parryEdges==null) blade.parryEdges=new float[65*5];
                prepareParryEdges(unit,blade,from,to,steps);
            }
            float[] edges=blade.parryEdges;
            float ex = shot.x + shot.vel.x * Time.delta, ey = shot.y + shot.vel.y * Time.delta;
            float radius = 2.5f * bladeScale + Math.min(shot.hitSize * 0.5f, 4f);
            for(int i = 0; i <= steps; i++){
                int p=i*5;
                float sx=edges[p],sy=edges[p+1],tx=edges[p+2],ty=edges[p+3],angle=edges[p+4];
                boolean hit = Intersector.intersectSegments(sx, sy, tx, ty, shot.x, shot.y, ex, ey, null)
                    || Intersector.distanceSegmentPoint(sx, sy, tx, ty, shot.x, shot.y) <= radius
                    || Intersector.distanceSegmentPoint(shot.x, shot.y, ex, ey, sx, sy) <= radius
                    || Intersector.distanceSegmentPoint(shot.x, shot.y, ex, ey, tx, ty) <= radius;
                if(!hit) continue;
                Vec2 contact = contactTmp;
                if(!Intersector.intersectSegments(sx, sy, tx, ty, shot.x, shot.y, ex, ey, contact)){
                    Intersector.nearestSegmentPoint(sx, sy, tx, ty, shot.x, shot.y, contact);
                }
                blade.parryX = contact.x;
                blade.parryY = contact.y;
                blade.parryFlash = 10;
                if(!Vars.headless) SubsonicParryFx.cut.at(contact.x, contact.y, angle, energy,
                    new SubsonicParryFx.Trace(shot.rotation(), Math.min(shot.hitSize, 8)));
                shot.absorb();
                blade.parried++;
                if(!blade.parryHold){
                    blade.parryHold = true;
                    blade.hold = 5;
                    if(!Vars.headless){
                        Effect.shake(1.3f, 5, contact.x, contact.y);
                        MechAudio.play(MechAudio.Cue.parry, unit, contact.x, contact.y, bladeScale);
                    }
                }
                break;
            }
        });
    }

    private void prepareParryEdges(Unit unit,BladeMount blade,float from,float to,int steps){
        float[] edges=blade.parryEdges;
        for(int i=0;i<=steps;i++){
            float t=Mathf.lerp(from,to,i/(float)steps),angle=unit.rotation+poseAngle(blade,t);
            Vec2 origin=poseOrigin(unit,blade,t,poseTmp);
            int p=i*5;
            edges[p]=origin.x+Angles.trnsx(angle,11*bladeScale);
            edges[p+1]=origin.y+Angles.trnsy(angle,11*bladeScale);
            edges[p+2]=origin.x+Angles.trnsx(angle,29*bladeScale);
            edges[p+3]=origin.y+Angles.trnsy(angle,29*bladeScale);
            edges[p+4]=angle;
        }
        blade.parryEdgeCount=steps+1;
    }

    public static float poseAngle(BladeMount blade, float age){
        int style = Math.max(0, blade.combo);
        float windup = style == 2 ? -135 : style == 3 ? -120 : style == 1 ? -75 : -105;
        float finish = style == 2 ? 115 : style == 3 ? 135 : style == 1 ? 65 : 85;
        float angle = -25;
        if(age < 6) angle = Mathf.lerp(-25, windup, Interp.pow2In.apply(age / 6));
        else if(age < 8) angle = Mathf.lerp(windup, 0, Interp.pow2Out.apply((age - 6) / 2));
        else if(age < 12) angle = Mathf.lerp(0, finish, Interp.pow2Out.apply((age - 8) / 4));
        else if(age < 26) angle = Mathf.lerp(finish, -25, Interp.smooth.apply((age - 12) / 14));
        return angle * blade.swingSide;
    }

    public static Vec2 poseOrigin(Unit unit, BladeMount blade, float age){
        return poseOrigin(unit,blade,age,new Vec2());
    }

    public static Vec2 poseOrigin(Unit unit, BladeMount blade, float age, Vec2 out){
        float lean = age < 6 ? -0.8f * age / 6f : age < 12 ? 1.2f : age < 26 ? 1.2f * (26 - age) / 14f : 0;
        return out.trns(unit.rotation, lean * ((EnergyBladeWeapon)blade.weapon).bladeScale).add(unit.x, unit.y);
    }

    public static float torsoTwist(Unit unit){
        BladeMount blade = (BladeMount)unit.mounts[0];
        return blade.age < 26 ? poseAngle(blade, blade.age) * 0.1f : 0;
    }

    // All points use the mech's forward/side axes; a geometric two-link IK solve
    // keeps elbow and wrist attached throughout fast slashes and recovery.
    public static Vec2 elbow(Vec2 shoulder, Vec2 wrist, int side){
        return elbow(shoulder, wrist, side, 7f);
    }

    public static Vec2 elbow(Vec2 shoulder, Vec2 wrist, int side, float length){
        return elbow(shoulder,wrist,side,length,new Vec2());
    }

    public static Vec2 elbow(Vec2 shoulder, Vec2 wrist, int side, float length, Vec2 out){
        float dx=wrist.x-shoulder.x,dy=wrist.y-shoulder.y;
        float distance = Mathf.sqrt(dx*dx+dy*dy);
        if(distance < 0.001f) return out.set(shoulder).add(length * side, 0);
        float along = Math.min(distance * 0.5f, length);
        float bend = Mathf.sqrt(Math.max(0, length * length - along * along));
        dx/=distance;dy/=distance;
        return out.set(shoulder.x+dx*along-dy*bend*side,shoulder.y+dy*along+dx*bend*side);
    }

    private Vec2 point(Unit unit, float forward, float side, Vec2 out){
        BladeMount blade = (BladeMount)unit.mounts[0];
        return out.trns(unit.rotation, forward * bladeScale, side * bladeScale).add(poseOrigin(unit, blade, blade.age,poseTmp));
    }

    @Override public void load(){
        super.load();
        upperArmRegion = Core.atlas.find(spritePrefix + "-upper-arm");
        forearmRegion = Core.atlas.find(spritePrefix + "-forearm");
        handRegion = Core.atlas.find(spritePrefix + "-hand");
        hiltRegion = Core.atlas.find(spritePrefix + "-hilt");
    }

    private void drawArmSegment(TextureRegion region, Vec2 start, Vec2 end, int side){
        Draw.rect(region, (start.x + end.x) / 2f, (start.y + end.y) / 2f,
            region.width * Draw.scl * side * bladeScale, start.dst(end) + 2f * bladeScale, start.angleTo(end) - 90f);
    }

    @Override public void drawOutline(Unit unit, WeaponMount mount){}

    @Override public void draw(Unit unit, WeaponMount mount){
        BladeMount blade = (BladeMount)mount;
        float z = Draw.z();
        Draw.z(Layer.groundUnit + 0.01f);
        boolean active = blade.age < 26;
        float angle = poseAngle(blade, blade.age);
        float direction = unit.rotation + angle;
        // Both hands remain on one rigid hilt: front hand at 9, rear hand at 5.
        Vec2 frontGrip = point(unit, Angles.trnsx(angle, 9), Angles.trnsy(angle, 9),frontTmp);
        Vec2 rearGrip = point(unit, Angles.trnsx(angle, 5), Angles.trnsy(angle, 5),rearTmp);
        for(int side=-1;side<=1;side+=2){
            Vec2 shoulder = shoulderTmp.trns(unit.rotation + torsoTwist(unit), 0, side * 5 * bladeScale)
                .add(poseOrigin(unit, blade, blade.age,poseTmp));
            Vec2 wrist = side == 1 ? frontGrip : rearGrip;
            Vec2 joint = elbow(shoulder, wrist, side, 7 * bladeScale,elbowTmp);
            Draw.color();
            drawArmSegment(upperArmRegion, shoulder, joint, side);
            drawArmSegment(forearmRegion, joint, wrist, side);
        }
        Vec2 hiltCenter = point(unit, Angles.trnsx(angle, 7), Angles.trnsy(angle, 7),hiltTmp);
        Vec2 guard = point(unit, Angles.trnsx(angle, 11), Angles.trnsy(angle, 11),guardTmp);
        Draw.color();
        Draw.rect(hiltRegion, hiltCenter.x, hiltCenter.y, hiltRegion.width * Draw.scl * bladeScale,
            hiltRegion.height * Draw.scl * bladeScale, direction - 90f);
        Draw.rect(handRegion, frontGrip.x, frontGrip.y, handRegion.width * Draw.scl * bladeScale,
            handRegion.height * Draw.scl * bladeScale, direction - 90f);
        Draw.rect(handRegion, rearGrip.x, rearGrip.y,
            -handRegion.width * Draw.scl * bladeScale, handRegion.height * Draw.scl * bladeScale, direction - 90f);
        float length = bladeLength(unit, blade);
        Vec2 tip = tipTmp.trns(direction, length).add(guard);
        // A visible blade halo in daylight, plus actual terrain illumination in dark maps.
        Draw.blend(Blending.additive);
        Draw.color(energy, 0.04f); Lines.stroke(12 * bladeScale); Lines.line(guard.x, guard.y, tip.x, tip.y);
        Draw.color(energy, 0.08f); Lines.stroke(8 * bladeScale); Lines.line(guard.x, guard.y, tip.x, tip.y);
        Draw.blend();
        float chroma = AfterimageDashAbility.chroma(unit);
        if(chroma > 0){
            for(int side=-1;side<=1;side+=2){
                float dx = Angles.trnsx(direction + 90, side * 2.4f * chroma);
                float dy = Angles.trnsy(direction + 90, side * 2.4f * chroma);
                Draw.color(side < 0 ? SubsonicParryFx.pink : SubsonicParryFx.cyan, 0.85f * chroma);
                Lines.stroke(2.5f * bladeScale);
                Lines.line(guard.x + dx, guard.y + dy, tip.x + dx, tip.y + dy);
            }
        }
        if(blade.parryFlash > 0){
            float flash = blade.parryFlash / 10f;
            for(int side=-1;side<=1;side+=2){
                float echo = direction + side * (5 + (1 - flash) * 16);
                Draw.color(side < 0 ? SubsonicParryFx.pink : SubsonicParryFx.cyan, 0.6f * flash);
                Lines.stroke(2.2f * flash);
                Lines.lineAngle(guard.x, guard.y, echo, 21 * bladeScale);
            }
        }
        if(active && blade.age >= 6 && blade.age < 12){
            for(int i = 3; i >= 1; i--){
                float t = Math.max(6, blade.age - i * 0.35f);
                float trailAngle = unit.rotation + poseAngle(blade, t);
                Vec2 origin = poseOrigin(unit, blade, t,poseTmp);
                Draw.color(energy, 0.16f / i); Lines.stroke(2.2f * bladeScale);
                Lines.lineAngle(origin.x + Angles.trnsx(trailAngle, 11 * bladeScale),
                    origin.y + Angles.trnsy(trailAngle, 11 * bladeScale), trailAngle, length);
            }
        }
        Draw.color(energy, 0.24f); Lines.stroke(5 * bladeScale); Lines.line(guard.x, guard.y, tip.x, tip.y);
        Draw.color(energy); Lines.stroke(2.2f * bladeScale); Lines.line(guard.x, guard.y, tip.x, tip.y);
        Draw.color(Color.white); Lines.stroke(0.8f * bladeScale); Lines.line(guard.x, guard.y, tip.x, tip.y);
        if(blade.parryFlash > 5){
            Draw.color(Color.white); Lines.stroke(2.3f * bladeScale);
            Lines.line(guard.x, guard.y, tip.x, tip.y);
        }
        if(active && blade.age >= 6 && blade.age < 12){
            Draw.color(energy, 0.35f); Lines.stroke(2);
            Lines.arc(unit.x, unit.y, 26 * bladeScale, 0.19f, direction - blade.swingSide * 65);
        }
        Drawf.light(guard.x, guard.y, tip.x, tip.y, 32 * bladeScale, energy, 0.85f);
        Drawf.light((guard.x + tip.x) / 2, (guard.y + tip.y) / 2, 40 * bladeScale, energy, 0.65f);
        Drawf.light(tip.x, tip.y, 32 * bladeScale, energy, 0.8f);
        Draw.reset(); Draw.z(z);
    }
}
