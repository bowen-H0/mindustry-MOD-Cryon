package cryon.Features;

import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.struct.*;
import cryon.Content.*;
import cryon.Fx.NanoFeedbackFx;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.gen.*;
import mindustry.world.*;

/** Reconstructible colony plans: real drills feed native ducts, then factories. No core or free factory fuel. */
public final class NanoColonies{
    private static final IntSeq deposits = new IntSeq();
    private static final Seq<Tile> veins = new Seq<>();
    private static int cursor;
    private static final Seq<Building> drills = new Seq<>();
    public static void clear(){ deposits.clear(); veins.clear(); cursor = 0; NanoIndustry.clear(); }
    public static void record(Tile t){ if(t.overlay() == NanoEnvironment.ore) deposits.add(t.pos()); }
    public static boolean mineral(Tile t){
        return t != null && t.overlay() != NanoEnvironment.ore && t.overlay().itemDrop != null;
    }
    public static void rebuild(){
        clear();
        for(Tile t : Vars.world.tiles){
            if(t.overlay() == NanoEnvironment.ore) record(t);
            else if(mineral(t)) veins.add(t);
        }
    }

    public static void step(){
        if(Vars.net.client() || Vars.state.isEditor()) return;
        Block belt = Vars.content.block("cryon-reinforced-vacuum-duct");
        if(belt == null) return;
        drills.clear();
        Groups.build.each(b -> { if(b.team == DisasterFactions.nano && b.block == NanoColonyContent.drill) drills.add(b); });
        // Grow at most one part per existing colony per pulse, avoiding single-frame bases.
        for(Building drill : drills) if(!NanoIndustry.feeder(drill)) grow(drill, belt);
        NanoIndustry.step(drills);
        if(drills.size >= 64 || (!NanoIndustry.hasHub()&&drills.size>=4) || deposits.size == 0) return;
        for(int i = 0, limit = Math.min(deposits.size, 128); i < limit; i++){
            Tile t = Vars.world.tile(deposits.get(cursor++ % deposits.size));
            if(t == null || t.overlay() != NanoEnvironment.ore || !empty(t) || NanoEnvironment.suppressed(t)) continue;
            if(NanoIndustry.reservedSite(t))continue;
            boolean feeder=NanoIndustry.hasHub();
            if(drills.contains(b -> b.within(t, (feeder?3f:14f) * Vars.tilesize))) continue;
            for(int rotation = 0; rotation < 4; rotation++){
                if(feeder?NanoIndustry.feederFits(t,rotation):planFits(t, rotation)){
                    place(t, NanoColonyContent.drill, rotation);
                    if(feeder)t.build.configureAny(Boolean.TRUE);
                    return;
                }
            }
        }
    }
    private static void place(Tile t, Block block, int rotation){
        t.setNet(block, DisasterFactions.nano, rotation);
        NanoFeedbackFx.event(NanoFeedbackFx.grow, t.drawx(), t.drawy(), block.size, 1);
    }
    private static Tile at(Tile origin, int rotation, int forward, int side){
        int dx = Geometry.d4x(rotation), dy = Geometry.d4y(rotation);
        return Vars.world.tile(origin.x + dx * forward - dy * side, origin.y + dy * forward + dx * side);
    }
    private static boolean empty(Tile t){ return t != null && t.block() == Blocks.air && t.floor().hasSurface(); }
    private static boolean available(Tile t){ return empty(t) && t.floor() == NanoEnvironment.floor && !NanoEnvironment.suppressed(t); }
    private static boolean planFits(Tile origin, int rot){
        for(int f = 0; f <= 8; f++){
            int width = f >= 6 ? 1 : 0;
            for(int side = -width; side <= width; side++) if(!available(at(origin, rot, f, side))) return false;
        }
        for(int side = -1; side <= 1; side++) if(!empty(at(origin, rot, 9, side))) return false;
        return true;
    }
    private static void grow(Building drill, Block belt){
        if(!drill.isValid() || NanoEnvironment.suppressed(drill.tile)) return;
        int rot = drill.rotation;
        // Never repurpose someone else's structures, and leave factory frontage free.
        for(int i = 1; i <= 5; i++){
            Tile t = at(drill.tile, rot, i, 0);
            if(t == null) return;
            if(t.block() == belt && t.team() == DisasterFactions.nano && t.build.rotation == rot) continue;
            if(available(t)) place(t, belt, rot);
            return;
        }
        Tile center = at(drill.tile, rot, 7, 0);
        if(center == null) return;
        if(center.block() != NanoColonyContent.factory || center.team() != DisasterFactions.nano){
            for(int f = 6; f <= 8; f++) for(int side = -1; side <= 1; side++) if(!available(at(drill.tile, rot, f, side))) return;
            for(int side = -1; side <= 1; side++) if(!empty(at(drill.tile, rot, 9, side))) return;
            place(center, NanoColonyContent.factory, rot);
            return;
        }
        // Two defensive flanks protect the vein without blocking the duct or unit exit.
        for(int f = -2; f <= 8; f += 2) for(int side : new int[]{-3, 3}){
            Tile t = at(drill.tile, rot, f, side);
            if(available(t)){ place(t, NanoColonyContent.wall, 0); return; }
        }
    }

    public static Tile destination(Unit unit){
        Tile best = null; float distance = Float.MAX_VALUE;
        for(Tile t : veins){
            if(!mineral(t) || t.block().solid || NanoEnvironment.suppressed(t)) continue;
            float dst = unit.dst2(t);
            if(dst < distance){ best = t; distance = dst; }
        }
        if(best != null) return best;
        // No remaining visible veins: seek an exposed patch at the advancing edge.
        for(int r = 4; r <= 28; r += 4) for(int a = 0; a < 16; a++){
            float angle = a * 22.5f + Mathf.randomSeed(unit.id, 360);
            Tile t = Vars.world.tile(unit.tileX() + (int)(Mathf.cosDeg(angle) * r), unit.tileY() + (int)(Mathf.sinDeg(angle) * r));
            if(empty(t) && !NanoEnvironment.infected(t) && !NanoEnvironment.suppressed(t)) return t;
        }
        return null;
    }
}
