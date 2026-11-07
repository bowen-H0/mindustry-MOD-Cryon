package cryon.Content;

import arc.Events;
import arc.struct.IntQueue;
import arc.struct.IntSet;
import arc.struct.Seq;
import arc.util.Time;
import cryon.Type.NanoSuppressor.NanoSuppressorBuild;
import cryon.Features.DisasterFactions;
import cryon.Features.NanoColonies;
import cryon.Fx.NanoFeedbackFx;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.EventType.*;
import mindustry.gen.Groups;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.*;

/** Terrain itself is the saved infection state; only the active edge is kept in memory. */
public final class NanoEnvironment{
    public static Floor floor;
    public static StaticWall wall;
    public static OreBlock ore;
    private static final IntQueue edge = new IntQueue();
    private static final IntSet queued = new IntSet();
    private static final Seq<NanoSuppressorBuild> suppressors = new Seq<>();
    private static final Seq<mindustry.gen.Building> damageBuildings = new Seq<>();
    private static float elapsed;
    private static float planningElapsed=-30f;
    private static boolean installed;

    public static void load(){
        floor = new Floor("nano-floor", 2);
        wall = new StaticWall("nano-terrain-wall");
        wall.variants = 2;
        floor.wall = wall;
        ore = new OreBlock("nano-ore", CryonItems.nanoMaterial);
        ore.variants = 3;
        ore.oreDefault = false;
    }

    public static void install(){
        if(installed) return;
        installed = true;
        cryon.Features.NanoConstruction.install();
        cryon.Features.RetiredNanoLogistics.install();
        Events.on(WorldLoadBeginEvent.class, e -> cryon.Features.NanoConstruction.clear());
        Events.on(ResetEvent.class, e -> {clear();cryon.Features.NanoConstruction.clear();});
        Events.on(WorldLoadEvent.class, e -> rebuild());
        Events.on(SaveLoadEvent.class, e -> rebuild());
        Events.run(Trigger.update, () -> {
            if(Vars.state == null || !Vars.state.isGame() || Vars.state.isPaused() || Vars.net.client()) return;
            elapsed += Time.delta;
            if(elapsed >= 60f){
                elapsed %= 60f; spreadStep(); damageTerrain();
            }
            // Keep colony planning off the frame that runs the spread/corrosion pulse.
            planningElapsed+=Time.delta;
            if(planningElapsed>=120f){planningElapsed%=120f;NanoColonies.step();}
        });
    }

    private static void clear(){ edge.clear(); queued.clear(); suppressors.clear(); damageBuildings.clear(); NanoColonies.clear(); elapsed = 0f; planningElapsed = -30f; }
    public static boolean infected(Tile t){ return t != null && (t.floor() == floor || t.block() == wall || t.overlay() == ore); }
    private static boolean convertible(Tile t){ return t != null && t.floor() != Blocks.empty && !infected(t); }
    private static boolean boundary(Tile t){
        for(int d = 0; d < 4; d++) if(convertible(t.nearby(d))) return true;
        return false;
    }
    private static void enqueue(Tile t){
        if(infected(t) && boundary(t) && queued.add(t.pos())) edge.addLast(t.pos());
    }
    public static void rebuild(){
        clear();
        if(Vars.net.client()) return;
        for(Tile t : Vars.world.tiles) enqueue(t);
        NanoColonies.rebuild();
        refreshSuppressors();
    }

    public static void infect(Tile t){
        if(Vars.net.client() || t == null || t.floor() == Blocks.empty) return;
        boolean naturalWall = t.block() instanceof StaticWall;
        boolean wasOre = t.overlay() == ore;
        boolean mineral = wasOre || NanoColonies.mineral(t);
        boolean fresh = !infected(t);
        // Only existing mineral deposits become nano ore; ordinary ground never creates ore.
        t.setFloorNet(floor, mineral ? ore : t.overlay());
        if(mineral && !wasOre) NanoColonies.record(t);
        if(naturalWall && t.block() != wall) t.setNet(wall, mindustry.game.Team.derelict, 0);
        if(fresh || (mineral && !wasOre)) NanoFeedbackFx.event(mineral ? NanoFeedbackFx.oreBloom : NanoFeedbackFx.assimilate, t.worldx(), t.worldy(), 0f, 0);
        enqueue(t);
    }

    /** One wave per second, capped to keep large contaminated maps responsive. */
    public static void spreadStep(){
        if(Vars.net.client()) return;
        refreshSuppressors();
        int count = Math.min(edge.size, 128);
        for(int i = 0; i < count; i++){
            int pos = edge.removeFirst(); queued.remove(pos);
            Tile t = Vars.world.tile(pos);
            if(!infected(t)) continue;
            if(suppressed(t)){
                NanoFeedbackFx.event(NanoFeedbackFx.rejected, t.worldx(), t.worldy(), 0f, 0);
                enqueue(t); continue;
            }
            for(int d = 0; d < 4; d++){
                Tile next = t.nearby(d);
                if(convertible(next) && !suppressed(next)){
                    boolean vein = NanoColonies.mineral(next);
                    infect(next);
                    // Rich seams attract replication: one extra step along contiguous ore.
                    Tile beyond = next.nearby(d);
                    if(vein && NanoColonies.mineral(beyond) && !suppressed(beyond)) infect(beyond);
                }
            }
            enqueue(t);
        }
    }

    public static void registerSuppressor(NanoSuppressorBuild b){ suppressors.addUnique(b); }
    private static void refreshSuppressors(){
        suppressors.clear();
        Groups.build.each(b -> {
            if(b instanceof NanoSuppressorBuild){
                suppressors.add((NanoSuppressorBuild)b);
            }
        });
    }

    public static boolean suppressed(Tile t){
        if(t == null) return false;
        for(NanoSuppressorBuild b : suppressors){
            if(b.operating() && b.contains(t.worldx(), t.worldy())) return true;
        }
        return false;
    }

    public static void spreadByUnit(mindustry.gen.Unit unit){
        if(Vars.net.client() || unit.dead || unit.team != DisasterFactions.nano || suppressed(unit.tileOn())) return;
        for(int dx = -1; dx <= 1; dx++) for(int dy = -1; dy <= 1; dy++){
            Tile t = Vars.world.tile(unit.tileX() + dx, unit.tileY() + dy);
            if(t != null && !suppressed(t)) infect(t);
        }
    }

    /** Corrosion affects ground occupants and entire building footprints, regardless of diplomacy. */
    public static void damageTerrain(){
        if(Vars.net.client()) return;
        for(mindustry.gen.Unit u : Groups.unit.copy()){
            if(!u.dead && u.team != DisasterFactions.nano && u.isGrounded() && infected(u.tileOn())){
                NanoFeedbackFx.event(NanoFeedbackFx.corrosion, u.x, u.y, u.hitSize * 0.6f, 4);
                u.damagePierce(60f);
            }
        }
        boolean[] exposed = {false};
        // Groups.build omits sleeping/static buildings, including ordinary walls and vaults.
        damageBuildings.clear();
        for(mindustry.game.Team team : mindustry.game.Team.all){
            if(team != DisasterFactions.nano) damageBuildings.addAll(Vars.state.teams.get(team).buildings);
        }
        for(mindustry.gen.Building b : damageBuildings){
            if(b.dead || !b.isValid() || b.team == DisasterFactions.nano) continue;
            exposed[0] = false;
            b.tile.getLinkedTiles(t -> { if(infected(t)) exposed[0] = true; });
            if(exposed[0]){
                NanoFeedbackFx.event(NanoFeedbackFx.corrosion, b.x, b.y, b.block.size * 4f, 4);
                b.damagePierce(40f);
            }
        }
    }

    public static void seed(float x, float y, int radius){
        if(Vars.net.client()) return;
        int cx = mindustry.core.World.toTile(x), cy = mindustry.core.World.toTile(y);
        for(int dx = -radius; dx <= radius; dx++) for(int dy = -radius; dy <= radius; dy++){
            if(dx * dx + dy * dy <= radius * radius) infect(Vars.world.tile(cx + dx, cy + dy));
        }
    }

    /** EMP converts only actual nano terrain; ordinary floors, ores and buildings survive. */
    public static boolean neutralize(Tile t){
        if(Vars.net.client() || !infected(t)) return false;
        boolean nanoWall = t.block() == wall;
        t.setFloorNet(Blocks.metalFloorDamaged, t.overlay() == ore ? Blocks.oreScrap : t.overlay());
        if(nanoWall) t.setNet(Blocks.darkMetal, mindustry.game.Team.derelict, 0);
        return true;
    }
}
