package cryon.Content;

import java.util.ArrayDeque;
import arc.math.Mathf;
import mindustry.content.Blocks;
import mindustry.type.Liquid;
import mindustry.world.*;
import mindustry.world.blocks.environment.Floor;

/** Small pumpable deposits, shared by generated sectors and existing campaign maps. */
public final class AravisLiquidResources{
    private static final int radius = 4, margin = 6;

    public static void ensure(Tiles tiles, Tile core){
        if(core == null) return;
        boolean[] reachable = reachable(tiles, core);
        addPool(tiles, core, reachable, Blocks.tar.asFloor(), -30);
        // Recompute after placing oil so the second deposit remains reachable.
        addPool(tiles, core, reachable(tiles, core), Blocks.slag.asFloor(), 30);
    }

    private static boolean[] reachable(Tiles tiles, Tile start){
        boolean[] visited = new boolean[tiles.width * tiles.height];
        ArrayDeque<Tile> queue = new ArrayDeque<>();
        queue.add(start); visited[start.array()] = true;
        while(!queue.isEmpty()){
            Tile tile = queue.remove();
            for(int d = 0; d < 4; d++){
                Tile next = tile.nearby(d);
                // The starting core is solid; allow traversal out through its footprint.
                if(next != null && !visited[next.array()] && !next.floor().isLiquid
                        && (!next.solid() || next.build == start.build && start.build != null)){
                    visited[next.array()] = true;
                    queue.add(next);
                }
            }
        }
        return visited;
    }

    private static void addPool(Tiles tiles, Tile core, boolean[] reachable, Floor floor, int offset){
        if(hasDeposit(tiles, reachable, floor.liquidDrop)) return;
        Tile best = null;
        float score = Float.MAX_VALUE;
        for(int x = margin; x < tiles.width - margin; x++){
            for(int y = margin; y < tiles.height - margin; y++){
                Tile tile = tiles.getn(x, y);
                if(!reachable[tile.array()] || Mathf.dst2(x, y, core.x, core.y) < 20f * 20f) continue;
                float distance = Mathf.dst2(x, y, core.x + offset, core.y + 12);
                if(distance < score && clearSite(tiles, x, y)){
                    best = tile;
                    score = distance;
                }
            }
        }
        if(best == null) return; // Never erase player work to force a deposit into a full map.
        for(int dx = -radius; dx <= radius; dx++){
            for(int dy = -radius; dy <= radius; dy++){
                if(dx * dx + dy * dy <= radius * radius){
                    tiles.getn(best.x + dx, best.y + dy).setFloor(floor);
                }
            }
        }
    }

    private static boolean clearSite(Tiles tiles, int x, int y){
        // A dry two-tile shore leaves a route around the pool for ground units.
        for(int dx = -margin; dx <= margin; dx++){
            for(int dy = -margin; dy <= margin; dy++){
                Tile tile = tiles.get(x + dx, y + dy);
                if(tile == null || tile.block() != Blocks.air || tile.floor().isLiquid
                        || tile.floor().liquidDrop != null || tile.overlay() == Blocks.spawn) return false;
                if(dx * dx + dy * dy <= radius * radius && tile.overlay() != Blocks.air) return false;
            }
        }
        return true;
    }

    private static boolean hasDeposit(Tiles tiles, boolean[] reachable, Liquid liquid){
        for(Tile tile : tiles){
            if(tile.floor().liquidDrop != liquid) continue;
            boolean pumpable = true, shore = false;
            for(int dx = -1; dx <= 1; dx++) for(int dy = -1; dy <= 1; dy++){
                Tile part = tiles.get(tile.x + dx, tile.y + dy);
                if(part == null || part.floor().liquidDrop != liquid) pumpable = false;
            }
            if(!pumpable) continue;
            for(int dx = -margin; dx <= margin && !shore; dx++){
                for(int dy = -margin; dy <= margin; dy++){
                    Tile nearby = tiles.get(tile.x + dx, tile.y + dy);
                    if(nearby != null && reachable[nearby.array()]){ shore = true; break; }
                }
            }
            if(shore) return true;
        }
        return false;
    }
}
