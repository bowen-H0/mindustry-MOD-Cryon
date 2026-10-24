package cryon.Type;

import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.IntMap;
import arc.struct.Seq;
import cryon.Fx.SpaceJumpFx;
import mindustry.Vars;
import mindustry.entities.abilities.Ability;
import mindustry.gen.Groups;
import mindustry.gen.Unit;

/** Live destination reservations are read from abilities: cancellation/death cannot leave stale slots. */
public class SpaceJumpFormation{
    private static final float bucketSize = 128f;

    public static float radius(Unit unit){
        float visual = SpaceJumpFx.digitalHalfSize(unit);
        return Math.max(unit.hitSize * 0.5f + 6f,
            visual * (SpaceJumpAbility.level(unit) >= 4 ? Mathf.sqrt2 : 1f) + 4f);
    }

    public static Vec2 plan(Unit unit, Vec2 desired, float maxDistance, float minDistance, Vec2 out){
        float centerX = desired.x, centerY = desired.y;
        float radius = radius(unit);
        float limit = Math.min(maxDistance * 0.75f, Math.max(Vars.world.unitWidth(), Vars.world.unitHeight()));
        Occupancy occupancy = new Occupancy(centerX, centerY, limit + radius + 4f);
        for(Unit other : Groups.unit){
            if(other == unit || other.dead || !other.isFlying() || other.isMissile()) continue;
            float otherRadius = radius(other);
            occupancy.add(other.x, other.y, otherRadius);
            for(Ability ability : other.abilities){
                if(ability instanceof SpaceJumpAbility){
                    SpaceJumpAbility jump = (SpaceJumpAbility)ability;
                    if(jump.reservesLanding()) occupancy.add(jump.landingX(), jump.landingY(), otherRadius);
                }
            }
        }
        if(valid(unit, centerX, centerY, radius, maxDistance, minDistance, occupancy, out)) return out;
        // Expand around the original command, beginning toward the source when range is capped.
        float spacing = Math.max(12f, radius * 0.75f);
        float back = Angles.angle(centerX, centerY, unit.x, unit.y);
        int attempts = 0;
        for(float distance = spacing; distance <= limit; distance += spacing){
            int slots = Math.max(6, Mathf.ceil(Mathf.PI2 * distance / spacing));
            for(int i = 0; i < slots; i++){
                float angle = back + i * 360f / slots;
                float x = centerX + Angles.trnsx(angle, distance);
                float y = centerY + Angles.trnsy(angle, distance);
                if(valid(unit, x, y, radius, maxDistance, minDistance, occupancy, out)) return out;
                if(++attempts >= 4096) return null;
            }
        }
        return null; // Do not squeeze into occupied space when the formation cannot fit.
    }

    private static boolean valid(Unit unit, float x, float y, float radius, float maxDistance, float minDistance,
                                 Occupancy occupancy, Vec2 out){
        if(x < radius || y < radius || x > Vars.world.unitWidth() - radius || y > Vars.world.unitHeight() - radius
            || unit.dst2(x, y) < minDistance * minDistance || unit.dst2(x, y) > maxDistance * maxDistance + 0.01f || occupancy.overlaps(x, y, radius)) return false;
        out.set(x, y);
        Vec2 safe = SpaceJumpLanding.outsideEnemyFlux(unit, out);
        return safe != null && Mathf.dst2(x, y, safe.x, safe.y) < 0.01f;
    }

    private static class Disc{
        final float x, y, radius;
        Disc(float x, float y, float radius){this.x = x; this.y = y; this.radius = radius;}
    }

    private static class Occupancy{
        final IntMap<Seq<Disc>> cells = new IntMap<>();
        final float centerX, centerY, reach;
        Occupancy(float x, float y, float reach){centerX = x; centerY = y; this.reach = reach;}
        int key(int x, int y){return (x & 0xffff) | (y << 16);}
        int cell(float coordinate){return Mathf.floor(coordinate / bucketSize);}
        void add(float x, float y, float radius){
            if(Math.abs(x-centerX)>reach+radius || Math.abs(y-centerY)>reach+radius) return;
            Disc disc = new Disc(x,y,radius);
            for(int cx=cell(x-radius);cx<=cell(x+radius);cx++) for(int cy=cell(y-radius);cy<=cell(y+radius);cy++){
                int key=key(cx,cy);
                Seq<Disc> bucket=cells.get(key);
                if(bucket==null){bucket=new Seq<>();cells.put(key,bucket);}
                bucket.add(disc);
            }
        }
        boolean overlaps(float x,float y,float radius){
            for(int cx=cell(x-radius-4f);cx<=cell(x+radius+4f);cx++) for(int cy=cell(y-radius-4f);cy<=cell(y+radius+4f);cy++){
                Seq<Disc> bucket=cells.get(key(cx,cy));
                if(bucket==null) continue;
                for(Disc disc:bucket){
                    float clearance=radius+disc.radius+4f;
                    if(Mathf.dst2(x,y,disc.x,disc.y)<clearance*clearance) return true;
                }
            }
            return false;
        }
    }
}
