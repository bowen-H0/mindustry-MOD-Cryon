package cryon.Features;

import arc.Events;
import arc.util.Time;
import cryon.Content.OrbitalUnits;
import cryon.Type.BeaconBulletType;
import cryon.Type.BeaconBulletType.Marker;
import cryon.Type.SkywatchAI;
import mindustry.Vars;
import mindustry.game.EventType.*;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Unit;

/** Position requests stay pending until a real Skywatch fires its own weapon. */
public final class OrbitalSupport{
    public static final float requestLifetime = 1200f;
    private static boolean installed;
    private OrbitalSupport(){}

    public static void install(){
        if(installed) return;
        installed = true;
        Events.run(Trigger.update, OrbitalSupport::update);
        Events.on(ResetEvent.class, event -> BeaconBulletType.markers.clear());
        Events.on(WorldLoadEvent.class, event -> BeaconBulletType.markers.clear());
    }

    public static boolean playerTeam(Team team){
        if(Vars.state != null && Vars.state.rules.defaultTeam == team) return true;
        return Groups.player != null && Groups.player.contains(player -> player.team() == team);
    }

    public static Marker request(BeaconBulletType source, Team team, float x, float y, float attenuation){
        // Repeated designations of the same unclaimed position refresh one request.
        for(Marker marker : BeaconBulletType.markers){
            if(marker.supportRequired && marker.team == team && marker.claimedBy < 0
                    && arc.math.Mathf.within(marker.x, marker.y, x, y, 16f)){
                marker.startTime = Time.time;
                marker.x = x; marker.y = y; marker.attenuation = attenuation;
                return marker;
            }
        }
        Marker marker = new Marker(x, y, source.strikeRadius, requestLifetime, source.markColor, team);
        marker.supportRequired = true;
        marker.attenuation = attenuation;
        BeaconBulletType.markers.add(marker);
        return marker;
    }

    public static boolean pending(Marker marker, Unit unit){
        return marker != null && marker.supportRequired && marker.team == unit.team
                && BeaconBulletType.markers.contains(marker, true)
                && Time.time - marker.startTime < marker.delay
                && (marker.claimedBy < 0 || marker.claimedBy == unit.id);
    }

    public static Marker claim(Unit unit){
        Marker best = null;
        float distance = Float.MAX_VALUE;
        for(Marker marker : BeaconBulletType.markers){
            if(!pending(marker, unit)) continue;
            if(marker.claimedBy == unit.id) return marker;
            float dst = unit.dst2(marker.x, marker.y);
            if(dst < distance){ best = marker; distance = dst; }
        }
        if(best != null) best.claimedBy = unit.id;
        return best;
    }

    /** Called by the actual unit projectile, never by the ground beacon. */
    public static float consume(Unit unit, float x, float y){
        for(int i = 0; i < BeaconBulletType.markers.size; i++){
            Marker marker = BeaconBulletType.markers.get(i);
            if(marker.supportRequired && marker.team == unit.team
                    && (marker.claimedBy < 0 || marker.claimedBy == unit.id)
                    && arc.math.Mathf.within(marker.x, marker.y, x, y, 4f)){
                BeaconBulletType.markers.remove(i);
                return marker.attenuation;
            }
        }
        return 0f;
    }

    private static void update(){
        for(int i = BeaconBulletType.markers.size - 1; i >= 0; i--){
            Marker marker = BeaconBulletType.markers.get(i);
            if(!marker.supportRequired) continue;
            if(Time.time - marker.startTime >= marker.delay){ BeaconBulletType.markers.remove(i); continue; }
            if(marker.claimedBy >= 0){
                Unit owner = Groups.unit.getByID(marker.claimedBy);
                if(owner == null || owner.dead || owner.type != OrbitalUnits.skywatch || owner.team != marker.team
                        || !(owner.controller() instanceof SkywatchAI)){
                    marker.claimedBy = -1;
                }
            }
        }
    }
}
