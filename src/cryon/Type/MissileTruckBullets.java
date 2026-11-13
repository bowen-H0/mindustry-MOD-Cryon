package cryon.Type;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.Vec2;
import arc.util.Time;
import cryon.Features.MapAlliances;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.graphics.Drawf;

/** Per-shot state machines; no global salvo counters or delayed callbacks survive a world reset. */
public final class MissileTruckBullets{
    public static final Color gold = Color.valueOf("ffd470"), dark = Color.valueOf("272638");
    public static final Effect blast = new Effect(55f, 1200f, e -> {
        Draw.color(e.color, Color.white, e.fin());
        Lines.stroke(5f * e.fout()); Lines.circle(e.x, e.y, e.rotation * e.fin());
        Draw.alpha(0.18f * e.fout()); Fill.circle(e.x, e.y, e.rotation * e.fin()); Draw.reset();
    });
    public static final Effect bombBlast = new Effect(48f, 500f, e -> {
        float radius = 12 + 70 * Interp.pow3Out.apply(e.fin());
        Draw.color(Color.valueOf("d89454"), gold, e.fout());
        Lines.stroke(3.5f * e.fout()); Lines.circle(e.x, e.y, radius);
        Angles.randLenVectors(e.id, Vars.mobile ? 6 : 10, radius * 0.9f, (x, y) -> {
            Draw.color(Color.valueOf("55545b")); Draw.alpha(0.45f * e.fout());
            Fill.circle(e.x + x, e.y + y, (8 + e.fin() * 13) * e.fout());
            Draw.color(gold); Draw.alpha(e.fout());
            Drawf.tri(e.x + x, e.y + y, 2f * e.fout(), 12f * e.fout(), Angles.angle(x, y));
        });
        e.scaled(7f, flash -> {
            Draw.color(Color.white); Draw.alpha(flash.fout());
            Fill.circle(e.x, e.y, 18 * flash.fout());
        });
        Drawf.light(e.x, e.y, 95 * e.fout(), gold, 0.35f * e.fout()); Draw.reset();
    });
    public static final Effect sonicCloud = new Effect(24f, 350f, e -> {
        Draw.color(Color.white); Draw.alpha(0.35f * e.fout());
        Lines.stroke(3f * e.fout());
        float width = 12 + e.fin() * 46;
        for(int side : new int[]{-1, 1}){
            float angle = e.rotation + 180 + side * 65;
            Lines.lineAngle(e.x, e.y, angle, width);
        }
        Draw.reset();
    });
    public static final Effect heavyBlast = new Effect(85f, 1200f, e -> {
        float radius = 190 * Interp.pow3Out.apply(e.fin());
        Draw.color(gold); Lines.stroke(8 * e.fout()); Lines.circle(e.x, e.y, radius);
        Angles.randLenVectors(e.id, Vars.mobile ? 16 : 28, radius * 0.8f, (x, y) -> {
            Draw.color(Color.gray, dark, e.fin()); Draw.alpha(0.7f * e.fout());
            Fill.circle(e.x + x, e.y + y, (14 + 25 * e.fin()) * e.fout());
            Draw.color(gold); Draw.alpha(e.fout());
            Drawf.tri(e.x + x, e.y + y, 3 * e.fout(), 32 * e.fout(), Angles.angle(x, y));
        });
        e.scaled(10, flash -> {
            Draw.color(Color.white, gold, flash.fin()); Fill.circle(e.x, e.y, 45 * flash.fout());
        });
        Drawf.light(e.x, e.y, 260 * e.fout(), gold, 0.7f * e.fout()); Draw.reset();
    });

    public static BulletType create(int tier, float range){
        if(tier == 0) return new RewindMissile(range);
        if(tier == 1) return new FlankingMissile(range);
        if(tier == 2) return new NodeSalvo(range);
        if(tier == 3) return new SkyStrike(range);
        return new HypersonicMissile(range);
    }

    private static void area(Bullet b, float x, float y, float radius, float amount){
        if(Vars.net.client()) return;
        MapAlliances.Scope scope = MapAlliances.enterTeam(b.team);
        try{
            Damage.damage(b.team, x, y, radius, amount * b.damageMultiplier(), true, true);
        }finally{ MapAlliances.exit(scope); }
    }
    private static float aimX(Bullet b, float range){ return b.aimX >= 0 ? b.aimX : b.x + Angles.trnsx(b.rotation(), range); }
    private static float aimY(Bullet b, float range){ return b.aimY >= 0 ? b.aimY : b.y + Angles.trnsy(b.rotation(), range); }
    private static void shot(BulletType type, Bullet parent, float x, float y, float angle, Object data, float ax, float ay){
        type.create(parent.owner, parent.team, x, y, angle, -1, 1, 1, data, null, ax, ay);
    }

    public static class BeamState{
        public float length;
        public boolean visible = true;
        public BeamState(float length){ this.length = length; }
        public BeamState(float length, boolean visible){ this.length = length; this.visible = visible; }
    }
    public static class NetworkBeam extends BulletType{
        public NetworkBeam(float damage){
            super(0, damage); lifetime = 24; collides = collidesTiles = false;
            collidesAir = true; pierce = pierceBuilding = true;
            hittable = absorbable = reflectable = false; keepVelocity = false;
            hitEffect = Fx.hitLancer; despawnEffect = Fx.none; drawSize = 1600;
        }
        @Override public void init(Bullet b){
            super.init(b);
            if(Vars.net.client() || !(b.data instanceof BeamState)) return;
            MapAlliances.Scope scope = MapAlliances.enterTeam(b.team);
            try{ Damage.collideLine(b, b.team, b.x, b.y, b.rotation(), ((BeamState)b.data).length, false, false); }
            finally{ MapAlliances.exit(scope); }
        }
        @Override public void draw(Bullet b){
            if(!(b.data instanceof BeamState)) return;
            Draw.color(hitColor); Lines.stroke(4f * b.fout());
            Lines.lineAngle(b.x, b.y, b.rotation(), ((BeamState)b.data).length);
            Draw.color(Color.white); Lines.stroke(1.4f * b.fout());
            Lines.lineAngle(b.x, b.y, b.rotation(), ((BeamState)b.data).length); Draw.reset();
        }
    }
    public static class TrapBeam extends NetworkBeam{
        public TrapBeam(){ super(120); lifetime = 38; hitColor = gold; }
        @Override public void draw(Bullet b){
            if(!(b.data instanceof BeamState)) return;
            if(!((BeamState)b.data).visible) return;
            float length = ((BeamState)b.data).length, angle = b.rotation();
            float x = b.x + Angles.trnsx(angle, length), y = b.y + Angles.trnsy(angle, length);
            float fade = b.fout();
            Draw.color(gold); Draw.alpha(0.14f * fade); Lines.stroke(7f * fade);
            Lines.line(b.x, b.y, x, y);
            Draw.alpha(fade); Lines.stroke(2.8f * fade); Lines.line(b.x, b.y, x, y);
            Drawf.tri(x, y, 8f * fade, 42f * fade, angle);
            Drawf.tri(b.x, b.y, 6f * fade, 28f * fade, angle + 180);
            Draw.color(Color.white); Lines.stroke(1.1f * fade); Lines.line(b.x, b.y, x, y);
            Drawf.tri(x, y, 3f * fade, 29f * fade, angle);
            Draw.reset();
        }
    }
    private static void beam(NetworkBeam type, Bullet parent, Vec2 from, Vec2 to){
        shot(type, parent, from.x, from.y, from.angleTo(to), new BeamState(from.dst(to)), to.x, to.y);
    }
    private static void trapBeam(NetworkBeam type, Bullet parent, Vec2 from, Vec2 to, boolean visible){
        shot(type, parent, from.x, from.y, from.angleTo(to), new BeamState(from.dst(to), visible), to.x, to.y);
    }
    private static String edgeKey(Vec2 from, Vec2 to){
        int ax = Mathf.round(from.x / 8), ay = Mathf.round(from.y / 8);
        int bx = Mathf.round(to.x / 8), by = Mathf.round(to.y / 8);
        if(ax > bx || (ax == bx && ay > by)){ int t = ax; ax = bx; bx = t; t = ay; ay = by; by = t; }
        return ax + "," + ay + ":" + bx + "," + by;
    }
    public static class Controller extends BulletType{
        public Controller(float range, float life){
            super(0, 1); rangeOverride = range; lifetime = life; keepVelocity = false;
            collides = collidesTiles = false; hittable = absorbable = reflectable = false;
            despawnHit = false; hitEffect = despawnEffect = shootEffect = smokeEffect = Fx.none;
            drawSize = 2000;
        }
        @Override public void draw(Bullet b){}
    }

    public static class RewindState{
        public final Vec2 origin;
        public boolean recalled;
        public RewindState(float x, float y){ origin = new Vec2(x, y); }
    }
    public static class RecallState{
        public final Vec2 origin, end;
        public final int steps;
        public int pulses;
        public RecallState(Vec2 origin, float x, float y){
            this.origin = origin.cpy(); end = new Vec2(x, y);
            steps = Mathf.clamp(Mathf.ceil(origin.dst(end) / 18f), 2, 12);
        }
    }
    public static class RewindMissile extends MissileBulletType{
        public final BasicBulletType fragment;
        public final Controller echo;
        public RewindMissile(float range){
            super(5f, 32f); lifetime = range / speed; rangeOverride = range;
            homingPower = 0; weaveMag = 0; collidesAir = false;
            pierce = pierceBuilding = true; pierceCap = 4;
            width = 7; height = 13; trailColor = backColor = hitColor = gold;
            frontColor = Color.white; splashDamage = 0; trailLength = 10; trailWidth = 1.4f;
            // Native fragments, paired curling trails like Critical's satellites.
            fragment = new BasicBulletType(3.2f, 10f);
            fragment.width = 4; fragment.height = 7; fragment.lifetime = 21;
            fragment.backColor = fragment.hitColor = fragment.trailColor = gold;
            fragment.frontColor = Color.white; fragment.trailWidth = 1; fragment.trailLength = 5;
            fragment.weaveMag = 2; fragment.weaveScale = 4; fragment.weaveRandom = false;
            fragment.splashDamage = 7; fragment.splashDamageRadius = 12; fragment.collidesAir = false;
            fragment.hitEffect = fragment.despawnEffect = cryon.Fx.CriticalFx.satelliteHit;
            echo = new Controller(range, 52){
                { fragBullet = fragment; fragBullets = 6; fragRandomSpread = 360; fragVelocityMin = 0.75f; fragVelocityMax = 1.15f; }
                @Override public void update(Bullet b){
                    if(!(b.data instanceof RecallState)) return;
                    RecallState state = (RecallState)b.data;
                    while(state.pulses < state.steps && b.time >= state.pulses * 4f){
                        float f = state.pulses / (float)(state.steps - 1);
                        float x = Mathf.lerp(state.end.x, state.origin.x, f);
                        float y = Mathf.lerp(state.end.y, state.origin.y, f);
                        area(b, x, y, 15, 12);
                        cryon.Fx.CriticalFx.satelliteHit.at(x, y, 0, gold);
                        createFrags(b, x, y);
                        state.pulses++;
                    }
                }
                @Override public void despawned(Bullet b){}
            };
        }
        @Override public void init(Bullet b){ super.init(b); b.data = new RewindState(b.x, b.y); }
        private void recall(Bullet b, float x, float y){
            RewindState state = (RewindState)b.data;
            if(state.recalled) return;
            state.recalled = true;
            shot(echo, b, x, y, b.rotation() + 180, new RecallState(state.origin, x, y), state.origin.x, state.origin.y);
        }
        @Override public void hitEntity(Bullet b, Hitboxc entity, float health){
            if(entity instanceof Teamc && MapAlliances.allied(b.team, ((Teamc)entity).team())) return;
            super.hitEntity(b, entity, health);
            recall(b, b.x, b.y);
        }
        @Override public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct){
            if(MapAlliances.allied(b.team, build.team)) return;
            super.hitTile(b, build, x, y, initialHealth, direct);
            if(direct) recall(b, x, y);
        }
    }

    public static class FlankState{
        public final Vec2 center, rear, target;
        public final Vec2[] route = new Vec2[3];
        public final float heading;
        public final int side;
        public int waypoint;
        public boolean cruising;
        public FlankState(float x, float y, float heading, float radius, int side, float tx, float ty){
            center = new Vec2(x, y); this.heading = heading; this.side = side;
            target = new Vec2(tx, ty);
            rear = center.cpy().add(Angles.trnsx(heading + 180, radius), Angles.trnsy(heading + 180, radius));
            for(int i = 0; i < route.length; i++){
                float angle = heading + side * (135 - i * 45);
                route[i] = center.cpy().add(Angles.trnsx(angle, radius), Angles.trnsy(angle, radius));
            }
        }
    }
    public static class FlankingMissile extends MissileBulletType{
        public FlankingMissile(float range){
            super(3.6f, 90); rangeOverride = range; lifetime = range / speed + 65;
            width = 8; height = 13; homingPower = 0.13f; homingRange = 160;
            weaveMag = 0; keepVelocity = false;
            splashDamage = 50; splashDamageRadius = 34;
            trailColor = backColor = hitColor = gold; frontColor = Color.white;
            trailLength = 15; trailWidth = 1.6f;
        }
        @Override public void init(Bullet b){
            super.init(b);
            float tx = aimX(b, rangeOverride), ty = aimY(b, rangeOverride);
            Unit owner = b.owner instanceof Unit ? (Unit)b.owner : null;
            float heading = owner == null ? b.rotation() : owner.rotation;
            FlankState state = new FlankState(owner == null ? b.x : owner.x, owner == null ? b.y : owner.y,
                    heading, (owner == null ? 28 : owner.hitSize * 0.8f + 14) + (b.id % 3) * 6,
                    (b.id & 1) == 0 ? 1 : -1, tx, ty);
            b.data = state;
            b.set(state.rear); b.vel.trns(b.angleTo(state.route[0]), speed);
        }
        @Override public void updateHoming(Bullet b){
            FlankState state = (FlankState)b.data;
            if(state.waypoint < state.route.length){
                Vec2 point = state.route[state.waypoint];
                if(b.within(point, speed * Time.delta + 1)) state.waypoint++;
                if(state.waypoint < state.route.length){
                    b.vel.trns(b.angleTo(state.route[state.waypoint]), speed);
                    return;
                }
                state.cruising = true;
            }
            // Lock enemies only after the missile has rounded the rear and flank.
            Teamc target = Units.closestTarget(b.team, state.target.x, state.target.y, homingRange,
                    u -> !MapAlliances.allied(b.team, u.team) && u.checkTarget(collidesAir, collidesGround),
                    build -> collidesGround && !MapAlliances.allied(b.team, build.team));
            if(target != null) state.target.set(target);
            b.vel.setAngle(Angles.moveToward(b.rotation(), b.angleTo(state.target), homingPower * Time.delta * 50));
        }
    }

    public static class SalvoState{
        public final Vec2 center;
        public final Vec2 sourceOffset = new Vec2();
        public final arc.struct.ObjectSet<String> visibleEdges = new arc.struct.ObjectSet<>();
        public final Vec2[] nodes = new Vec2[12], planned = new Vec2[12];
        public int launched, landed, resolved;
        public float armedAt = -1;
        public boolean detonated;
        public SalvoState(float x, float y){ center = new Vec2(x, y); }
    }
    public static class NodeFlight{
        public final SalvoState salvo;
        public final int index;
        public final Vec2 target;
        public boolean arrived, finished;
        public NodeFlight(SalvoState salvo, int index, Vec2 target){
            this.salvo = salvo; this.index = index; this.target = target.cpy();
        }
    }
    private static class SalvoTarget{
        final Vec2 point;
        final float radius, health;
        final boolean unit;
        float covered;
        SalvoTarget(float x, float y, float radius, float health, boolean unit){
            point = new Vec2(x, y); this.radius = radius; this.health = Math.max(1, health); this.unit = unit;
        }
    }
    public static class NodeMissile extends MissileBulletType{
        public NodeMissile(float range){
            super(7, 80); rangeOverride = range; lifetime = range / speed + 30;
            homingPower = weaveMag = 0;
            collides = collidesTiles = collidesAir = collidesGround = true;
            collideTerrain = collideFloor = false; pierce = pierceBuilding = false; keepVelocity = false;
            width = 7; height = 14; trailColor = backColor = hitColor = gold;
            frontColor = Color.white; trailLength = 10; trailWidth = 1.3f;
        }
        private void finish(Bullet b, float x, float y){
            NodeFlight f = (NodeFlight)b.data;
            if(f.finished) return;
            f.finished = true; f.salvo.resolved++; f.salvo.planned[f.index] = null;
            if(b.absorbed) return;
            f.arrived = true; f.salvo.nodes[f.index] = new Vec2(x, y); f.salvo.landed++;
            area(b, x, y, 24, 70); blast.at(x, y, 30, gold);
        }
        @Override public void update(Bullet b){
            super.update(b);
            NodeFlight f = (NodeFlight)b.data;
            if(f.finished) return;
            if(b.within(f.target, speed * Time.delta + 1)){
                // Let native swept tile collision run before an endpoint explosion.
                b.tileRaycast(mindustry.core.World.toTile(b.lastX), mindustry.core.World.toTile(b.lastY), b.tileX(), b.tileY());
                if(b.isAdded()){
                    finish(b, b.x, b.y); b.hit = true; b.remove();
                }
            }
        }
        @Override public void hitEntity(Bullet b, Hitboxc entity, float health){
            if(entity instanceof Teamc && MapAlliances.allied(b.team, ((Teamc)entity).team())) return;
            super.hitEntity(b, entity, health); finish(b, b.x, b.y);
        }
        @Override public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct){
            if(MapAlliances.allied(b.team, build.team)) return;
            super.hitTile(b, build, x, y, initialHealth, direct);
            if(direct) finish(b, x, y);
        }
        @Override public void despawned(Bullet b){ finish(b, b.x, b.y); }
        @Override public void removed(Bullet b){ super.removed(b); finish(b, b.x, b.y); }
    }
    public static class NodeSalvo extends Controller{
        public final BulletType missile;
        public final NetworkBeam laser = new TrapBeam();
        public NodeSalvo(float range){
            super(range, 280); laser.hitColor = gold; missile = new NodeMissile(range);
        }
        @Override public void init(Bullet b){
            super.init(b);
            SalvoState state = new SalvoState(aimX(b, rangeOverride), aimY(b, rangeOverride));
            if(b.owner instanceof Unit) state.sourceOffset.set(b.x - ((Unit)b.owner).x, b.y - ((Unit)b.owner).y);
            b.data = state;
        }

        private static float distanceToLine(Vec2 point, Vec2 from, Vec2 to){
            float dx = to.x - from.x, dy = to.y - from.y;
            float length2 = dx * dx + dy * dy;
            float t = length2 < 0.01f ? 0 : Mathf.clamp(((point.x - from.x) * dx + (point.y - from.y) * dy) / length2);
            return Mathf.dst(point.x, point.y, from.x + dx * t, from.y + dy * t);
        }
        private Vec2 expectedImpact(Bullet b, Vec2 target){
            Vec2 impact = target.cpy();
            Vars.world.raycastEach(mindustry.core.World.toTile(b.x), mindustry.core.World.toTile(b.y),
                    mindustry.core.World.toTile(target.x), mindustry.core.World.toTile(target.y), (x, y) -> {
                mindustry.world.Tile tile = Vars.world.tile(x, y);
                if(tile == null) return false;
                boolean hostile = tile.build != null && tile.block().solid && tile.block().targetable
                        && !MapAlliances.allied(b.team, tile.team());
                if(hostile){ impact.set(tile.worldx(), tile.worldy()); return true; }
                return false;
            });
            return impact;
        }
        private Vec2 expectedImpact(Bullet b, Vec2 target, arc.struct.Seq<SalvoTarget> blockers){
            Vec2 impact = expectedImpact(b, target);
            float dx = target.x - b.x, dy = target.y - b.y, length = Mathf.sqrt(dx * dx + dy * dy);
            if(length < 0.01f) return impact;
            dx /= length; dy /= length;
            float nearest = b.dst(impact);
            for(SalvoTarget enemy : blockers) if(enemy.unit){
                float ex = enemy.point.x - b.x, ey = enemy.point.y - b.y;
                float along = ex * dx + ey * dy;
                float perpendicular2 = Math.max(0, ex * ex + ey * ey - along * along);
                float radius = enemy.radius + missile.hitSize / 2;
                if(along < 0 || perpendicular2 > radius * radius) continue;
                float entry = Math.max(0, along - Mathf.sqrt(radius * radius - perpendicular2));
                if(entry < nearest){ nearest = entry; impact.set(b.x + dx * entry, b.y + dy * entry); }
            }
            return impact;
        }
        /** Bounded greedy coverage: direct blast plus new network edges, with diminishing overlap. */
        private float score(SalvoState state, Vec2 point, arc.struct.Seq<SalvoTarget> enemies){
            float score = 0;
            for(SalvoTarget enemy : enemies){
                float previous = enemy.covered, added = 0;
                float reach = 24 + enemy.radius;
                if(point.within(enemy.point, reach)) added += 70 * (1 - point.dst(enemy.point) / (reach * 1.3f));
                for(int i = 0; i < state.launched; i++){
                    Vec2 old = state.nodes[i] == null ? state.planned[i] : state.nodes[i];
                    if(old == null) continue;
                    if(distanceToLine(enemy.point, old, point) <= enemy.radius + 3) added += 120;
                }
                if(added <= 0) continue;
                float useful = Math.min(added, Math.max(0, enemy.health - previous));
                score += useful / (1 + previous / 160f);
                if(previous < 1) score += 55; // Prefer bringing another enemy into the attack.
            }
            for(int i = 0; i < state.launched; i++){
                Vec2 old = state.nodes[i] == null ? state.planned[i] : state.nodes[i];
                if(old != null && point.within(old, 8)) score -= 18;
            }
            return score;
        }
        private Vec2 chooseTarget(Bullet b, SalvoState state){
            arc.struct.Seq<SalvoTarget> enemies = new arc.struct.Seq<>();
            Units.nearbyEnemies(b.team, state.center.x, state.center.y, 180, u -> {
                if(enemies.size < 24 && u.targetable(b.team) && !MapAlliances.allied(b.team, u.team) && u.within(b, rangeOverride + 24))
                    enemies.add(new SalvoTarget(u.x, u.y, u.hitSize / 2, u.health, true));
            });
            Units.nearbyBuildings(state.center.x, state.center.y, 180, build -> {
                if(enemies.size < 24 && build.block.targetable && !MapAlliances.allied(b.team, build.team) && build.within(b, rangeOverride + 24))
                    enemies.add(new SalvoTarget(build.x, build.y, build.block.size * 4, build.health, false));
            });
            // Compute the existing network's coverage once, outside candidate scoring.
            for(SalvoTarget enemy : enemies) for(int i = 0; i < state.launched; i++){
                Vec2 first = state.nodes[i] == null ? state.planned[i] : state.nodes[i];
                if(first == null) continue;
                if(first.within(enemy.point, 24 + enemy.radius)) enemy.covered += 70;
                for(int j = 0; j < i; j++){
                    Vec2 second = state.nodes[j] == null ? state.planned[j] : state.nodes[j];
                    if(second != null && distanceToLine(enemy.point, first, second) <= enemy.radius + 3) enemy.covered += 120;
                }
            }
            int seed = b.id * 31 + state.launched * 131;
            Vec2 best = null, bestImpact = null; float bestScore = -Float.MAX_VALUE;
            for(int i = 0; i < enemies.size; i++) for(int variant = 0; variant < 3; variant++){
                SalvoTarget enemy = enemies.get(i);
                Vec2 candidate = enemy.point.cpy();
                if(variant > 0){
                    float angle = Mathf.randomSeed(seed + i * 53 + variant * 7, 360f);
                    float radius = Mathf.randomSeed(seed + i * 17 + variant, 12f, 24f + enemy.radius * 0.65f);
                    candidate.add(Angles.trnsx(angle, radius), Angles.trnsy(angle, radius));
                }
                candidate.sub(b.x, b.y).limit(rangeOverride).add(b.x, b.y);
                Vec2 impact = expectedImpact(b, candidate, enemies);
                float value = score(state, impact, enemies);
                if(value > bestScore){ bestScore = value; best = candidate; bestImpact = impact; }
            }
            if(best == null){
                float angle = Mathf.randomSeed(seed, 360f), radius = Mathf.randomSeed(seed + 1, 8f, 70f);
                best = state.center.cpy().add(Angles.trnsx(angle, radius), Angles.trnsy(angle, radius));
                best.sub(b.x, b.y).limit(rangeOverride).add(b.x, b.y);
            }
            state.planned[state.launched] = bestImpact == null ? expectedImpact(b, best) : bestImpact;
            return best;
        }
        @Override public void update(Bullet b){
            SalvoState state = (SalvoState)b.data;
            if(state.launched < 12 && b.time >= state.launched * 5){
                if(b.owner instanceof Unit && ((Unit)b.owner).isValid()){
                    Unit owner = (Unit)b.owner;
                    b.set(owner.x + state.sourceOffset.x, owner.y + state.sourceOffset.y);
                }
                int index = state.launched;
                Vec2 target = chooseTarget(b, state);
                NodeFlight flight = new NodeFlight(state, index, target);
                state.launched++;
                shot(missile, b, b.x, b.y, b.angleTo(flight.target), flight, flight.target.x, flight.target.y);
            }
            if(state.resolved == 12 && !state.detonated){
                if(state.armedAt < 0) state.armedAt = b.time;
                if(b.time - state.armedAt < 18) return;
                state.detonated = true;
                state.visibleEdges.clear();
                for(int i = 0; i < 12; i++) if(state.nodes[i] != null)
                    for(int j = i + 1; j < 12; j++) if(state.nodes[j] != null){
                        Vec2 from = state.nodes[i], to = state.nodes[j];
                        boolean visible = from.dst(to) > 8 && state.visibleEdges.add(edgeKey(from, to));
                        trapBeam(laser, b, from, to, visible);
                    }
                blast.at(state.center.x, state.center.y, 110, gold);
                cryon.Fx.CriticalFx.impact.at(state.center.x, state.center.y, 0, gold);
                Effect.shake(5, 12, state.center.x, state.center.y);
                b.lifetime = b.time + 42;
            }
        }
        @Override public void draw(Bullet b){
            SalvoState state = (SalvoState)b.data;
            float charge = state.armedAt < 0 ? 0 : Mathf.clamp((b.time - state.armedAt) / 18f);
            state.visibleEdges.clear();
            Draw.color(gold); Draw.alpha(state.detonated ? b.fout() : 0.3f + charge * 0.65f);
            Lines.stroke(state.detonated ? 1.5f : 0.65f + charge * 1.2f);
            for(int i = 0; i < 12; i++) if(state.nodes[i] != null){
                Vec2 p = state.nodes[i];
                Lines.circle(p.x, p.y, 5 + charge * 3);
                if(!state.detonated){
                    Drawf.tri(p.x, p.y, 3 + charge * 3, 10 + charge * 16, p.angleTo(state.center));
                    Drawf.light(p.x, p.y, 22 + charge * 24, gold, 0.3f + charge * 0.4f);
                }
                for(int j = 0; j < i; j++) if(state.nodes[j] != null && p.dst(state.nodes[j]) > 8
                        && state.visibleEdges.add(edgeKey(p, state.nodes[j]))) Lines.line(p.x, p.y, state.nodes[j].x, state.nodes[j].y);
            }
            Draw.reset();
        }
    }

    public static class SkyState{
        public final Vec2 center, sourceOffset = new Vec2();
        public final Vec2[] points = new Vec2[8];
        public final arc.struct.ObjectFloatMap<Teamc> assigned = new arc.struct.ObjectFloatMap<>();
        public int launched, faded, bombsReleased, impacts;
        public boolean detonated;
        public SkyState(float x, float y){ center = new Vec2(x, y); }
    }
    private static class StrikeTarget{
        final Teamc entity;
        final Vec2 point;
        final float health, radius, priority;
        StrikeTarget(Teamc entity, float health, float radius, float priority){
            this.entity = entity; point = new Vec2(entity.getX(), entity.getY());
            this.health = health; this.radius = radius; this.priority = priority;
        }
    }
    public static class FlareFlight{
        public final SkyState strike;
        public final Vec2 target;
        public boolean arrived, released;
        public FlareFlight(SkyState strike, Vec2 target){ this.strike = strike; this.target = target.cpy(); }
    }
    public static class BombFlight{
        public final SkyState strike;
        public final float delay;
        public boolean detonated;
        public BombFlight(SkyState strike, float delay){ this.strike = strike; this.delay = delay; }
    }
    public static class FallingBomb extends Controller{
        public final float fallTime = 40;
        public FallingBomb(float range){ super(range, 78); damage = 900; hitColor = gold; }
        @Override public void update(Bullet b){
            BombFlight flight = (BombFlight)b.data;
            if(flight.detonated || b.time < flight.delay + fallTime) return;
            flight.detonated = true; flight.strike.impacts++;
            area(b, b.x, b.y, 64, damage);
            bombBlast.at(b.x, b.y, 0, gold);
            Effect.shake(5, 12, b.x, b.y);
            b.lifetime = b.time + 8;
            if(flight.strike.impacts == 32) flight.strike.detonated = true;
        }
        @Override public void draw(Bullet b){
            BombFlight flight = (BombFlight)b.data;
            if(flight.detonated || b.time < flight.delay) return;
            float progress = Mathf.clamp((b.time - flight.delay) / fallTime);
            float height = 260 * (1 - progress * progress);
            Draw.color(dark); Draw.alpha(0.4f); Fill.circle(b.x, b.y, 5 + progress * 5);
            Draw.color(gold); Draw.alpha(0.6f); Lines.stroke(3);
            Lines.line(b.x, b.y + height + 8, b.x, b.y + height + 55);
            Draw.alpha(1); Fill.rect(b.x, b.y + height + 6, 8, 16);
            Drawf.tri(b.x, b.y + height, 8, 16, 270);
            Draw.color(Color.white); Fill.rect(b.x, b.y + height + 8, 2, 12);
            Drawf.light(b.x, b.y + height, 34, gold, 0.5f); Draw.reset();
        }
    }
    public static class SignalFlare extends Controller{
        public final FallingBomb bomb;
        public SignalFlare(float range, FallingBomb bomb){
            super(range, 145); speed = 8; damage = 0; this.bomb = bomb;
        }
        @Override public void update(Bullet b){
            FlareFlight flight = (FlareFlight)b.data;
            if(flight.arrived){ b.vel.setZero(); return; }
            b.vel.trns(b.angleTo(flight.target), speed);
            if(b.within(flight.target, speed * Time.delta + 1)){
                flight.arrived = true; b.set(flight.target); b.vel.setZero();
                b.lifetime = b.time + 50;
            }
        }
        @Override public void despawned(Bullet b){
            FlareFlight flight = (FlareFlight)b.data;
            if(!flight.arrived || flight.released || b.time < b.lifetime - 1) return;
            flight.released = true; flight.strike.faded++;
            for(int i = 0; i < 4; i++){
                float angle = Mathf.randomSeed(b.id * 31L + i * 7, 360f);
                float radius = i == 0 ? 0 : Mathf.randomSeed(b.id * 17L + i, 10f, 32f);
                float x = b.x + Angles.trnsx(angle, radius), y = b.y + Angles.trnsy(angle, radius);
                shot(bomb, b, x, y, 270, new BombFlight(flight.strike, i * 7), x, y);
                flight.strike.bombsReleased++;
            }
        }
        @Override public void draw(Bullet b){
            FlareFlight flight = (FlareFlight)b.data;
            float fade = flight.arrived ? Mathf.clamp((b.lifetime - b.time) / 24) : 1;
            Draw.color(gold); Draw.alpha(fade);
            Fill.circle(b.x, b.y, 3.5f + Mathf.absin(b.time, 4, 1));
            Lines.stroke(1.5f); Lines.lineAngle(b.x, b.y, flight.arrived ? 90 : b.rotation() + 180, 16 * fade);
            if(flight.arrived){
                Draw.alpha(0.25f * fade); Fill.circle(b.x, b.y + 12, 8 + Mathf.absin(b.time, 5, 3));
                Drawf.light(b.x, b.y, 52 * fade, gold, 0.55f * fade);
            }
            Draw.color(Color.white); Draw.alpha(fade); Fill.circle(b.x, b.y, 1.5f); Draw.reset();
        }
    }
    public static class SkyStrike extends Controller{
        public final FallingBomb bomb;
        public final SignalFlare flare;
        public SkyStrike(float range){
            super(range, 360); bomb = new FallingBomb(range); flare = new SignalFlare(range, bomb);
        }
        @Override public void init(Bullet b){
            super.init(b); SkyState state = new SkyState(aimX(b, rangeOverride), aimY(b, rangeOverride));
            if(b.owner instanceof Unit) state.sourceOffset.set(b.x - ((Unit)b.owner).x, b.y - ((Unit)b.owner).y);
            b.data = state;
        }
        private float buildingPriority(Building build){
            if(build.block.flags.contains(mindustry.world.meta.BlockFlag.core)) return 7;
            if(build.block.flags.contains(mindustry.world.meta.BlockFlag.turret)) return 4.5f;
            if(build.block.flags.contains(mindustry.world.meta.BlockFlag.factory)
                    || build.block.flags.contains(mindustry.world.meta.BlockFlag.unitAssembler)) return 4;
            if(build.block.flags.contains(mindustry.world.meta.BlockFlag.generator)) return 3;
            return 1;
        }
        private float expectedDamage(Vec2 point, StrikeTarget target){
            float reach = 88 + target.radius;
            float distance = point.dst(target.point);
            return distance > reach ? 0 : 3600 * Mathf.lerp(1, 0.25f, distance / reach);
        }
        private Vec2 chooseTarget(Bullet b, SkyState state){
            arc.struct.Seq<StrikeTarget> targets = new arc.struct.Seq<>();
            Units.nearbyEnemies(b.team, b.x, b.y, rangeOverride, u -> {
                if(targets.size < 80 && u.targetable(b.team) && !MapAlliances.allied(b.team, u.team)){
                    float priority = 1.5f + Mathf.sqrt(u.maxHealth) / 100f + u.armor * 0.025f + u.type.weapons.size * 0.1f;
                    targets.add(new StrikeTarget(u, u.health, u.hitSize / 2, priority));
                }
            });
            Units.nearbyBuildings(b.x, b.y, rangeOverride, build -> {
                if(targets.size < 160 && build.block.targetable && !MapAlliances.allied(b.team, build.team))
                    targets.add(new StrikeTarget(build, build.health, build.block.size * 4, buildingPriority(build)));
            });
            targets.sort((first, second) -> Float.compare(second.priority, first.priority));
            targets.truncate(48);
            Vec2 best = null; float bestScore = -Float.MAX_VALUE;
            int seed = b.id * 31 + state.launched * 53;
            for(int i = 0; i < targets.size; i++) for(int variant = 0; variant < 3; variant++){
                Vec2 candidate = targets.get(i).point.cpy();
                if(variant > 0){
                    float angle = Mathf.randomSeed(seed + i * 7 + variant, 360f);
                    float radius = Mathf.randomSeed(seed + i * 13 + variant, 8f, 28f);
                    candidate.add(Angles.trnsx(angle, radius), Angles.trnsy(angle, radius));
                }
                candidate.sub(b.x, b.y).limit(rangeOverride).add(b.x, b.y);
                float score = 0;
                for(StrikeTarget target : targets){
                    float pending = state.assigned.get(target.entity, 0);
                    float remaining = Math.max(0, target.health - pending);
                    float added = Math.min(remaining, expectedDamage(candidate, target));
                    if(added > 0) score += target.priority * (added + (pending < 1 ? 500 : 0))
                            / (1 + pending / 3600f);
                }
                score -= candidate.dst(state.center) * 0.05f;
                if(score > bestScore){ bestScore = score; best = candidate; }
            }
            if(best == null){
                float angle = Mathf.randomSeed(seed, 360f), radius = Mathf.randomSeed(seed + 1, 12f, 90f);
                best = state.center.cpy().add(Angles.trnsx(angle, radius), Angles.trnsy(angle, radius));
                best.sub(b.x, b.y).limit(rangeOverride).add(b.x, b.y);
            }
            for(StrikeTarget target : targets){
                float amount = expectedDamage(best, target);
                if(amount > 0) state.assigned.put(target.entity, state.assigned.get(target.entity, 0) + amount);
            }
            return best;
        }
        @Override public void update(Bullet b){
            SkyState state = (SkyState)b.data;
            if(state.launched < 8 && b.time >= state.launched * 3){
                if(b.owner instanceof Unit && ((Unit)b.owner).isValid()){
                    Unit owner = (Unit)b.owner; b.set(owner.x + state.sourceOffset.x, owner.y + state.sourceOffset.y);
                }
                Vec2 target = chooseTarget(b, state);
                state.points[state.launched++] = target.cpy();
                shot(flare, b, b.x, b.y, b.angleTo(target), new FlareFlight(state, target), target.x, target.y);
            }
            if(state.detonated) b.lifetime = Math.min(b.lifetime, b.time + 1);
        }
    }

    public static class HypersonicState{
        public final Vec2 target;
        public boolean detonated;
        public HypersonicState(float x, float y){ target = new Vec2(x, y); }
    }
    public static class HypersonicMissile extends MissileBulletType{
        public HypersonicMissile(float range){
            super(48, 960);
            rangeOverride = range; lifetime = range / speed;
            homingPower = weaveMag = 0; keepVelocity = false;
            hittable = absorbable = reflectable = false;
            collideTerrain = collideFloor = collidesTeam = false;
            pierce = pierceBuilding = false;
            width = 12; height = 30; hitSize = 10;
            backColor = trailColor = gold; frontColor = Color.white;
            trailLength = 28; trailWidth = 3;
            hitEffect = despawnEffect = Fx.none; despawnHit = false;
            drawSize = 600;
        }
        @Override public void init(Bullet b){
            super.init(b);
            Vec2 target = new Vec2(aimX(b, rangeOverride), aimY(b, rangeOverride));
            target.sub(b.x, b.y).limit(rangeOverride).add(b.x, b.y);
            b.data = new HypersonicState(target.x, target.y);
            b.vel.setAngle(b.angleTo(target)); b.rotation(b.vel.angle());
        }
        @Override public boolean testCollision(Bullet b, Building build){
            return build.team != Team.derelict && !MapAlliances.allied(b.team, build.team) && super.testCollision(b, build);
        }
        private void detonate(Bullet b){
            if(!(b.data instanceof HypersonicState state) || state.detonated || b.absorbed) return;
            state.detonated = true;
            area(b, b.x, b.y, 180, 12800);
            heavyBlast.at(b.x, b.y, b.rotation(), gold);
            Effect.shake(14, 30, b.x, b.y);
        }
        @Override public void update(Bullet b){
            super.update(b);
            if(!(b.data instanceof HypersonicState state) || state.detonated) return;
            if(b.timer(1, 3)) sonicCloud.at(b.x, b.y, b.rotation());
            float dx = b.x - b.lastX, dy = b.y - b.lastY;
            float length2 = dx * dx + dy * dy;
            float along = length2 == 0 ? -1 : ((state.target.x - b.lastX) * dx + (state.target.y - b.lastY) * dy) / length2;
            if(along >= 0 && along <= 1 && Mathf.dst(b.lastX + dx * along, b.lastY + dy * along, state.target.x, state.target.y) < 8){
                // Check for enemy structures before detonating at the aim point.
                b.tileRaycast(mindustry.core.World.toTile(b.lastX), mindustry.core.World.toTile(b.lastY),
                        mindustry.core.World.toTile(state.target.x), mindustry.core.World.toTile(state.target.y));
                if(!b.isAdded()) return;
                b.set(state.target); detonate(b); b.remove();
            }
        }
        @Override public void hitEntity(Bullet b, Hitboxc entity, float health){
            super.hitEntity(b, entity, health); detonate(b);
        }
        @Override public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct){
            super.hitTile(b, build, x, y, initialHealth, direct); detonate(b);
        }
        @Override public void despawned(Bullet b){ if(b.time >= b.lifetime - 1) detonate(b); }
        @Override public void draw(Bullet b){
            super.draw(b);
            float heading = b.rotation();
            // A narrow Mach cone and torn white slipstream follow the actual moving missile.
            for(int side : new int[]{-1, 1}){
                Draw.color(Color.white); Draw.alpha(0.42f); Lines.stroke(2);
                Lines.lineAngle(b.x, b.y, heading + 180 + side * 18, 105);
                Draw.alpha(0.16f); Lines.stroke(5);
                Lines.lineAngle(b.x - Angles.trnsx(heading, 20), b.y - Angles.trnsy(heading, 20), heading + 180 + side * 28, 60);
            }
            Draw.color(Color.white); Draw.alpha(0.7f); Lines.stroke(1.4f);
            Lines.lineAngle(b.x, b.y, heading + 180, 155);
            Drawf.light(b.x, b.y, 65, gold, 0.6f); Draw.reset();
        }
    }
}
