package cryon.Story;

import arc.Events;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import cryon.Type.ShardedReceptorRaidAI;
import mindustry.Vars;
import mindustry.game.EventType.*;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.type.UnitType;
import mindustry.world.Tile;

/** One real diversion per map, invoked by a dialogue event. No terrain or buildings are replaced. */
public final class ShardedReceptorRaid{
    public static final String tag = "cryon-sharded-receptor-raid";
    public static final double unitFlag = 741306d;
    private static boolean installed;

    private ShardedReceptorRaid(){}

    public static void install(){
        if(installed) return;
        installed = true;
        Events.on(WorldLoadEvent.class, event -> restore());
        Events.on(SaveLoadEvent.class, event -> restore());
    }

    private static void restore(){
        if(Vars.net.client()) return;
        for(Unit unit : Groups.unit){
            if(unit.flag == unitFlag && unit.team == Team.sharded && unit.type.name.equals("cryon-receptor"))
                unit.controller(new ShardedReceptorRaidAI());
        }
    }

    public static void start(Building origin, int count){
        if(Vars.net.client() || Vars.state.isEditor() || Vars.state.rules.tags.getBool(tag)) return;
        UnitType type = Vars.content.unit("cryon-receptor");
        if(type == null || origin == null || count < 1 || count > 12) return;
        Teamc objective = objective(origin);
        Vec2 landing = new Vec2(origin.x, origin.y);
        Building core = cryon.Features.AllianceTeam.team.core();
        if(core != null) landing.set(core.x, core.y);
        if(objective == null) return;
        Unit scout = type.create(Team.sharded);
        boolean[] reachable = reachable(scout, landing);
        Tile entry = entry(reachable, landing);
        if(entry == null) return;
        Vec2 spawn = new Vec2(entry.worldx(), entry.worldy());
        Vec2 direction = spawn.cpy().sub(landing);
        Vec2 side = direction.cpy().rotate90(1).setLength(18f);
        int spawned = 0;
        for(int i = 0; i < count; i++){
            float offset = i - (count - 1) / 2f;
            Unit unit = spawn(type, Team.sharded, spawn.x + side.x * offset, spawn.y + side.y * offset,
                direction.angle() + 180f, reachable);
            if(unit == null) continue;
            unit.flag = unitFlag;
            unit.controller(new ShardedReceptorRaidAI());
            spawned++;
        }
        if(spawned == 0) return;
        Vars.state.rules.tags.put(tag, "true");
        if(Vars.net.server()) Call.setRules(Vars.state.rules);
    }

    private static Teamc objective(Building origin){
        Teamc best = null;
        float distance = Float.MAX_VALUE;
        // Attack the player's landing force; do not create extra opposing factions.
        for(Team team : new Team[]{cryon.Features.AllianceTeam.team}){
            if(team == Team.sharded) continue;
            for(Building building : Vars.state.teams.get(team).buildings){
                if(!building.isValid() || !building.block.targetable
                    || building instanceof cryon.Type.JavaProcessor.JavaProcessorBuild) continue;
                float score = origin.dst2(building);
                if(score < distance){ best = building; distance = score; }
            }
            for(Unit unit : Groups.unit){
                if(unit.team != team || unit.dead || !unit.type.targetable) continue;
                float score = origin.dst2(unit);
                if(score < distance){ best = unit; distance = score; }
            }
            if(best != null) return best;
        }
        return null;
    }

    /** Flood-fill open hover terrain so a distant entry point still has a route to the landing. */
    private static boolean[] reachable(Unit unit, Vec2 landing){
        int width = Vars.world.width(), height = Vars.world.height();
        boolean[] open = new boolean[width * height], visited = new boolean[open.length];
        int seed = -1;
        float nearest = Float.MAX_VALUE;
        for(Tile tile : Vars.world.tiles){
            int index = tile.x + tile.y * width;
            open[index] = !tile.block().solid && !tile.floor().isDeep() && unit.canPass(tile.x, tile.y);
            if(open[index]){
                float distance = landing.dst2(tile.worldx(), tile.worldy());
                if(distance < nearest){ seed = index; nearest = distance; }
            }
        }
        if(seed < 0) return visited;
        int[] queue = new int[open.length];
        int start = 0, end = 0;
        queue[end++] = seed; visited[seed] = true;
        while(start < end){
            int index = queue[start++], x = index % width, y = index / width;
            for(int direction = 0; direction < 4; direction++){
                int nx = x + (direction == 0 ? 1 : direction == 1 ? -1 : 0);
                int ny = y + (direction == 2 ? 1 : direction == 3 ? -1 : 0);
                if(nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                int next = nx + ny * width;
                if(!visited[next] && open[next]){ visited[next] = true; queue[end++] = next; }
            }
        }
        return visited;
    }

    private static Tile entry(boolean[] reachable, Vec2 landing){
        float shortSide = Math.min(Vars.world.unitWidth(), Vars.world.unitHeight());
        float desiredDistance = Math.min(1200f, Math.max(640f, shortSide * 0.35f));
        float minimum = Math.min(640f, shortSide * 0.55f);
        Vec2 direction = new Vec2(Vars.world.unitWidth() / 2f, Vars.world.unitHeight() / 2f).sub(landing);
        if(direction.isZero()) direction.set(1f, 0f);
        Vec2 desired = direction.setLength(desiredDistance).add(landing);
        Tile best = null, fallback = null;
        float score = Float.MAX_VALUE, farthest = -1f;
        for(Tile tile : Vars.world.tiles){
            if(!reachable[tile.x + tile.y * Vars.world.width()]) continue;
            float distance = landing.dst2(tile.worldx(), tile.worldy());
            if(distance > farthest){ farthest = distance; fallback = tile; }
            if(distance < minimum * minimum) continue;
            boolean nearBase = false;
            for(Building building : cryon.Features.AllianceTeam.team.data().buildings){
                if(building.within(tile.worldx(), tile.worldy(), minimum * 0.5f)){ nearBase = true; break; }
            }
            if(nearBase) continue;
            float candidate = desired.dst2(tile.worldx(), tile.worldy());
            if(candidate < score){ best = tile; score = candidate; }
        }
        return best == null ? fallback : best;
    }

    private static Unit spawn(UnitType type, Team team, float x, float y, float rotation, boolean[] reachable){
        Unit unit = type.create(team);
        Tile best = null;
        float distance = Float.MAX_VALUE;
        int centerX = Math.round(x / Vars.tilesize), centerY = Math.round(y / Vars.tilesize);
        for(int tx = centerX - 12; tx <= centerX + 12; tx++) for(int ty = centerY - 12; ty <= centerY + 12; ty++){
            Tile tile = Vars.world.tile(tx, ty);
            if(tile == null || !reachable[tx + ty * Vars.world.width()]) continue;
            float score = Mathf.dst2(tile.worldx(), tile.worldy(), x, y);
            if(score < distance){ best = tile; distance = score; }
        }
        if(best == null) return null;
        Vars.state.rules.unitCap = Math.max(Vars.state.rules.unitCap, team.data().countType(type) + 1);
        unit.set(best.worldx(), best.worldy());
        unit.rotation = rotation;
        unit.add();
        return unit;
    }
}
