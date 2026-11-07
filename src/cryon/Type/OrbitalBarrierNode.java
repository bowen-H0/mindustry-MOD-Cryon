package cryon.Type;

import arc.Core;
import arc.Events;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.struct.*;
import arc.util.Time;
import arc.util.io.*;
import cryon.Features.MapAlliances;
import cryon.Features.OrbitalBarrierRenderer;
import cryon.Content.CryonItems;
import mindustry.content.*;
import mindustry.entities.Damage;
import mindustry.entities.bullet.*;
import mindustry.game.EventType.*;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.Bar;
import mindustry.world.Block;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

/** A finite, linked orbital curtain. Geometry never places invisible blocks in the map. */
public class OrbitalBarrierNode extends Block{
    public float linkRange = 960f, shieldCapacity = 120000f, phaseCapacity = 80000f;
    public float barrierWidth = 64f;
    public float recharge = 250f, breakTime = 600f;
    public int maxLinks = 4;
    private static final Seq<OrbitalBarrierBuild> nodes = new Seq<>();
    private static boolean installed;

    public OrbitalBarrierNode(String name){
        super(name);
        requirements(Category.defense, BuildVisibility.shown, ItemStack.with(
            Items.silicon, 2400, Items.surgeAlloy, 1200, Items.phaseFabric, 800,
            CryonItems.nanoMaterial, 1800, CryonItems.neutronite, 1400, CryonItems.farstarAlloy, 1600));
        size = 9; health = 100000; armor = 40f;
        update = true; solid = true; configurable = true; saveConfig = true; copyConfig = true;
        hasItems = true; acceptsItems = true; itemCapacity = 60; hasLiquids = true; liquidCapacity = 360f;
        hasPower = true; consumePower(600f);
        canOverdrive = false; rebuildable = false;
        clipSize = 16000f;
        buildType = OrbitalBarrierBuild::new;
        config(Integer.class, (OrbitalBarrierBuild build, Integer pos) -> build.toggleLink(pos));
        config(Point2[].class, (OrbitalBarrierBuild build, Point2[] offsets) -> {
            build.clearLinks();
            for(Point2 p : offsets) build.toggleLink(Point2.pack(build.tileX() + p.x, build.tileY() + p.y));
        });
        configClear((OrbitalBarrierBuild build) -> build.clearLinks());
        if(!installed){
            installed = true;
            Events.on(ResetEvent.class, e -> nodes.clear());
            Events.run(Trigger.afterGameUpdate, OrbitalBarrierNode::blockUnits);
        }
    }

    @Override public void setBars(){
        super.setBars();
        addBar("orbital-shield", (OrbitalBarrierBuild b) -> new Bar(
            () -> Core.bundle.format("bar.cryon-orbital-shield", (int)b.shield, (int)b.capacity()),
            () -> Pal.powerLight, () -> b.shield / b.capacity()));
        addLiquidBar(Liquids.cryofluid);
        addBar("orbital-phase", (OrbitalBarrierBuild b) -> new Bar(
            () -> Core.bundle.format("bar.cryon-orbital-phase", b.items.get(Items.phaseFabric)),
            () -> Items.phaseFabric.color, () -> b.phaseTime / 600f));
        addBar("orbital-reboot", (OrbitalBarrierBuild b) -> new Bar(
            () -> Core.bundle.get(b.fuse >= 0f ? "bar.cryon-orbital-unstable" : !b.powered() ? "bar.cryon-orbital-unpowered" : b.broken ? "bar.cryon-orbital-reboot" : "bar.cryon-orbital-ready"),
            () -> b.fuse >= 0f ? Pal.remove : Pal.powerLight,
            () -> b.fuse >= 0f ? b.fuse / 15f : b.broken ? 1f - b.cooldown / breakTime : 1f));
    }

    @Override public void setStats(){
        super.setStats();
        stats.add(Stat.shieldHealth, shieldCapacity, StatUnit.shieldHealth);
        stats.add(Stat.range, linkRange / tilesize, StatUnit.blocks);
        stats.add(Stat.cooldownTime, breakTime / 60f, StatUnit.seconds);
    }

    private static boolean valid(OrbitalBarrierBuild b){
        return b != null && b.isValid() && !b.dead;
    }

    /** Segment intersection fraction, or -1. Used for swept projectiles and fast units. */
    public static float crossing(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy){
        float rx = bx - ax, ry = by - ay, sx = dx - cx, sy = dy - cy;
        float den = rx * sy - ry * sx;
        if(Math.abs(den) < 0.00001f) return -1f;
        float t = ((cx - ax) * sy - (cy - ay) * sx) / den;
        float u = ((cx - ax) * ry - (cy - ay) * rx) / den;
        return t >= 0f && t <= 1f && u >= 0f && u <= 1f ? t : -1f;
    }

    /** Called before native bullet movement, so fast rounds cannot damage behind the curtain. */
    public static float thickCrossing(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy, float radius){
        float length = Mathf.dst(cx, cy, dx, dy);
        float first = circleCrossing(ax, ay, bx, by, cx, cy, radius);
        float last = circleCrossing(ax, ay, bx, by, dx, dy, radius);
        if(first < 0f || last >= 0f && last < first) first = last;
        if(length < 0.001f) return first;
        float ux = (dx - cx) / length, uy = (dy - cy) / length;
        float px = (ax - cx) * ux + (ay - cy) * uy, py = -(ax - cx) * uy + (ay - cy) * ux;
        float vx = (bx - ax) * ux + (by - ay) * uy, vy = -(bx - ax) * uy + (by - ay) * ux;
        float low = 0f, high = 1f;
        if(Math.abs(vx) < 0.00001f){ if(px < 0f || px > length) return first; }
        else{ float a = -px / vx, b = (length - px) / vx; low = Math.max(low, Math.min(a, b)); high = Math.min(high, Math.max(a, b)); }
        if(Math.abs(vy) < 0.00001f){ if(Math.abs(py) > radius) return first; }
        else{ float a = (-radius - py) / vy, b = (radius - py) / vy; low = Math.max(low, Math.min(a, b)); high = Math.min(high, Math.max(a, b)); }
        return low <= high && (first < 0f || low < first) ? low : first;
    }
    private static float circleCrossing(float ax, float ay, float bx, float by, float x, float y, float radius){
        float ox = ax - x, oy = ay - y, vx = bx - ax, vy = by - ay;
        float c = ox * ox + oy * oy - radius * radius;
        if(c <= 0f) return 0f;
        float a = vx * vx + vy * vy, b = ox * vx + oy * vy, discriminant = b * b - a * c;
        if(a < 0.00001f || discriminant < 0f) return -1f;
        float t = (-b - Mathf.sqrt(discriminant)) / a;
        return t >= 0f && t <= 1f ? t : -1f;
    }

    /** Called before native bullet movement, so fast rounds cannot damage behind the curtain. */
    public static boolean intercept(Bullet bullet){
        if(net.client() || bullet.absorbed || bullet.vel.isZero() || beamLength(bullet.type) >= 0f) return false;
        OrbitalBarrierBuild first = null, second = null;
        float best = 2f, ex = bullet.x + bullet.vel.x * Time.delta, ey = bullet.y + bullet.vel.y * Time.delta;
        for(OrbitalBarrierBuild a : nodes){
            if(!a.active() || MapAlliances.allied(a.team, bullet.team)) continue;
            for(int i = 0; i < a.links.size; i++){
                OrbitalBarrierBuild b = a.link(a.links.get(i));
                if(b == null || !b.active() || a.pos() > b.pos()) continue;
                float hit = thickCrossing(bullet.x, bullet.y, ex, ey, a.x, a.y, b.x, b.y, ((OrbitalBarrierNode)a.block).barrierWidth / 2f);
                if(hit >= 0f && hit < best){ best = hit; first = a; second = b; }
            }
        }
        if(first == null) return false;
        first.strain(second, Math.max(1f, bullet.type.shieldDamage(bullet)) + Math.max(0f, bullet.type.splashDamage));
        bullet.set(Mathf.lerp(bullet.x, ex, best), Mathf.lerp(bullet.y, ey, best));
        Fx.absorb.at(bullet.x, bullet.y, Pal.powerLight);
        bullet.absorb();
        return true;
    }

    private static float beamLength(BulletType type){
        if(type instanceof LaserBulletType) return ((LaserBulletType)type).length;
        if(type instanceof ContinuousBulletType) return ((ContinuousBulletType)type).length;
        if(type instanceof RailBulletType) return ((RailBulletType)type).length;
        if(type instanceof ShrapnelBulletType) return ((ShrapnelBulletType)type).length;
        return -1f;
    }

    private static void beamLength(BulletType type, float value){
        if(type instanceof LaserBulletType) ((LaserBulletType)type).length = value;
        else if(type instanceof ContinuousBulletType) ((ContinuousBulletType)type).length = value;
        else if(type instanceof RailBulletType) ((RailBulletType)type).length = value;
        else if(type instanceof ShrapnelBulletType) ((ShrapnelBulletType)type).length = value;
    }

    /** Scope the shared native beam length around init/update/draw; always restore in finally. */
    public static float clipBeam(Bullet bullet, boolean charge){
        float length = beamLength(bullet.type);
        if(length < 0f || nodes.isEmpty()) return -1f;
        float ex = bullet.x + Mathf.cosDeg(bullet.rotation()) * length;
        float ey = bullet.y + Mathf.sinDeg(bullet.rotation()) * length;
        float best = 2f;
        OrbitalBarrierBuild first = null, second = null;
        for(OrbitalBarrierBuild a : nodes){
            if(!a.active() || MapAlliances.allied(a.team, bullet.team)) continue;
            for(int i = 0; i < a.links.size; i++){
                OrbitalBarrierBuild b = a.link(a.links.get(i));
                if(b == null || !b.active() || a.pos() > b.pos()) continue;
                float hit = thickCrossing(bullet.x, bullet.y, ex, ey, a.x, a.y, b.x, b.y, ((OrbitalBarrierNode)a.block).barrierWidth / 2f);
                if(hit >= 0f && hit < best){ best = hit; first = a; second = b; }
            }
        }
        if(first == null) return -1f;
        beamLength(bullet.type, Math.max(0f, length * best - 6f));
        if(charge && !net.client()){
            float damage = Math.max(1f, bullet.type.shieldDamage(bullet));
            if(bullet.type instanceof ContinuousBulletType){
                ContinuousBulletType continuous = (ContinuousBulletType)bullet.type;
                damage *= Time.delta / Math.max(1f, continuous.damageInterval);
            }
            first.strain(second, damage);
        }
        return length;
    }

    public static void restoreBeam(Bullet bullet, float length){ if(length >= 0f) beamLength(bullet.type, length); }

    /** Unit pressure consumes energy; nothing kills or deletes a boss to simulate blocking. */
    public static void blockUnits(){
        if(net.client() || state == null || !state.isGame()) return;
        for(OrbitalBarrierBuild a : nodes){
            if(!a.active()) continue;
            for(int i = 0; i < a.links.size && a.active(); i++){
                OrbitalBarrierBuild b = a.link(a.links.get(i));
                if(b == null || !b.active() || a.pos() > b.pos()) continue;
                float dx = b.x - a.x, dy = b.y - a.y, len = Mathf.len(dx, dy);
                if(len < 1f) continue;
                Groups.unit.each(unit -> {
                    if(!a.active() || !b.active() || unit.dead || MapAlliances.allied(a.team, unit.team)) return;
                    float radius = unit.hitSize / 2f + ((OrbitalBarrierNode)a.block).barrierWidth / 2f;
                    float hit = thickCrossing(unit.lastX, unit.lastY, unit.x, unit.y, a.x, a.y, b.x, b.y, radius);
                    if(hit < 0f) return;
                    float hx = Mathf.lerp(unit.lastX, unit.x, hit), hy = Mathf.lerp(unit.lastY, unit.y, hit);
                    float along = Mathf.clamp(((hx - a.x) * dx + (hy - a.y) * dy) / (len * len));
                    float qx = a.x + dx * along, qy = a.y + dy * along;
                    float distance = Mathf.dst(qx, qy, hx, hy);
                    float nx = distance > 0.001f ? (hx - qx) / distance : -dy / len;
                    float ny = distance > 0.001f ? (hy - qy) / distance : dx / len;
                    unit.set(qx + nx * (radius + 0.1f), qy + ny * (radius + 0.1f));
                    float inward = unit.vel.x * nx + unit.vel.y * ny;
                    if(inward < 0f) unit.vel.sub(nx * inward, ny * inward);
                    float pressure = Math.max(unit.maxHealth * 0.005f, unit.hitSize >= 64f ? 1500f : 0f);
                    a.strain(b, Math.max(80f, Math.min(2000f, pressure)) * Time.delta);
                });
            }
        }
    }

    public class OrbitalBarrierBuild extends Building{
        public final IntSeq links = new IntSeq();
        public float shield = shieldCapacity, phaseTime, quiet, cooldown, fuse = -1f, flash;
        public boolean broken;

        @Override public void add(){ super.add(); if(!nodes.contains(this, true)) nodes.add(this); }
        @Override public void onRemoved(){ nodes.remove(this, true); clearLinks(); super.onRemoved(); }
        public OrbitalBarrierBuild link(int pos){
            Building other = world.build(pos);
            if(!(other instanceof OrbitalBarrierBuild) || other.block != block || other.team != team || other == this) return null;
            OrbitalBarrierBuild target = (OrbitalBarrierBuild)other;
            return valid(target) && within(target, linkRange) && target.links.contains(pos()) ? target : null;
        }
        public void toggleLink(int pos){
            Building other = world.build(pos);
            if(!(other instanceof OrbitalBarrierBuild) || other.block != block || other == this || other.team != team || !within(other, linkRange)) return;
            OrbitalBarrierBuild target = (OrbitalBarrierBuild)other;
            if(links.contains(pos)){ links.removeValue(pos); target.links.removeValue(pos()); }
            else if(links.size < maxLinks && target.links.size < maxLinks){ links.add(pos); target.links.add(pos()); }
        }
        public void clearLinks(){
            for(int i = 0; i < links.size; i++){
                Building b = world.build(links.get(i));
                if(b instanceof OrbitalBarrierBuild) ((OrbitalBarrierBuild)b).links.removeValue(pos());
            }
            links.clear();
        }
        @Override public Object config(){
            Point2[] points = new Point2[links.size];
            for(int i = 0; i < points.length; i++) points[i] = new Point2(Point2.x(links.get(i)) - tileX(), Point2.y(links.get(i)) - tileY());
            return points;
        }
        @Override public boolean onConfigureBuildTapped(Building other){
            if(other == this){ deselect(); return false; }
            if(other instanceof OrbitalBarrierBuild && other.team == team){ configure(other.pos()); return false; }
            return true;
        }
        @Override public void drawConfigure(){
            Drawf.dashCircle(x, y, linkRange, Pal.powerLight);
            for(int i = 0; i < links.size; i++){
                OrbitalBarrierBuild other = link(links.get(i));
                if(other != null) Drawf.square(other.x, other.y, other.block.size * tilesize / 2f + 2f, Pal.powerLight);
            }
        }
        public float capacity(){ return shieldCapacity + (phaseTime > 0f ? phaseCapacity : 0f); }
        public boolean powered(){ return enabled && power != null && power.status >= 0.98f; }
        public boolean active(){ return valid(this) && powered() && !broken && shield > 0f && fuse < 0f; }
        public void strain(OrbitalBarrierBuild other, float damage){
            takeShield(damage / 2f); other.takeShield(damage / 2f);
        }
        public void takeShield(float damage){
            shield = Math.max(0f, shield - damage); quiet = 180f; flash = 1f;
            if(shield <= 0f && !broken){ broken = true; cooldown = breakTime; Fx.shieldBreak.at(x, y, 26f, Pal.powerLight); }
        }
        @Override public boolean acceptItem(Building source, Item item){ return item == Items.phaseFabric && items.get(item) < itemCapacity; }
        @Override public boolean acceptLiquid(Building source, Liquid liquid){ return liquid == Liquids.cryofluid; }
        @Override public void updateTile(){
            flash = Mathf.approachDelta(flash, 0f, 0.05f);
            if(net.client()) return;
            if(fuse >= 0f){ fuse -= Time.delta; if(fuse <= 0f) kill(); return; }
            phaseTime = Math.max(0f, phaseTime - Time.delta);
            shield = Math.min(shield, capacity());
            if(!powered()) return;
            if(phaseTime <= 0f && items.has(Items.phaseFabric)){
                items.remove(Items.phaseFabric, 1); phaseTime = 600f;
                // Added capacity is delivered with each phase activation, not free every tick.
                shield = Math.min(capacity(), shield + phaseCapacity);
            }
            shield = Math.min(shield, capacity());
            boolean cooling = liquids.get(Liquids.cryofluid) >= 0.2f * Time.delta && (broken || shield < capacity());
            if(cooling) liquids.remove(Liquids.cryofluid, 0.2f * Time.delta);
            quiet = Math.max(0f, quiet - Time.delta);
            if(broken){
                cooldown = Math.max(0f, cooldown - Time.delta * (cooling ? 2.5f : 1f));
                if(cooldown <= 0f){ broken = false; shield = capacity() * 0.2f; }
            }else if(quiet <= 0f) shield = Math.min(capacity(), shield + recharge * Time.delta * (cooling ? 2.4f : 1f));
        }
        @Override public void onDestroyed(){
            // Ignite immediate neighbors before removing the links. Fuses propagate on their
            // own destruction, so cycles terminate naturally and saves preserve pending blasts.
            if(!net.client()){
                for(int i = 0; i < links.size; i++){
                    OrbitalBarrierBuild other = link(links.get(i));
                    if(other != null && other.fuse < 0f) other.fuse = 15f;
                }
                Damage.damage(Team.derelict, x, y, 100f, 8000f, true, true);
            }
            Fx.massiveExplosion.at(x, y);
            if(!headless) mindustry.entities.Effect.shake(6f, 20f, x, y);
            super.onDestroyed();
        }

        @Override public void draw(){
            super.draw();
            if(headless) return;
            float z = Draw.z();
            Draw.z(Layer.light + 0.02f);
            if(powered()){
                float height = Math.max(1000f, Core.camera.position.y + Core.camera.height / 2f + 120f - y);
                for(int lane = -1; lane <= 1; lane++){
                    for(int s = 0; s < 36; s++){
                        if((s + lane + (int)(Time.time / 16f)) % 9 == 0) continue;
                        float a = s / 36f, b = (s + 0.85f) / 36f;
                        float ax = x + lane * (3f + a * 14f) + OrbitalPowerNode.parallaxOffset(x, Core.camera.position.x, a, 60f);
                        float bx = x + lane * (3f + b * 14f) + OrbitalPowerNode.parallaxOffset(x, Core.camera.position.x, b, 60f);
                        Draw.color(fuse >= 0f ? Pal.remove : Pal.powerLight, active() ? 0.3f : 0.08f);
                        Lines.stroke(lane == 0 ? 1.5f : 0.7f); Lines.line(ax, y + a * height, bx, y + b * height);
                    }
                }
                Drawf.light(x, y, x, y + height, 130f, Pal.powerLight, active() ? 0.17f : 0.04f);
            }
            for(int i = 0; i < links.size; i++){
                OrbitalBarrierBuild b = link(links.get(i));
                if(b == null || pos() > b.pos()) continue;
                if(active() && b.active()){
                    OrbitalBarrierRenderer.draw(x, y, b.x, b.y, barrierWidth, Math.max(flash, b.flash),
                        Math.min(shield / capacity(), b.shield / b.capacity()), phaseTime > 0f && b.phaseTime > 0f);
                    Drawf.light(x, y, b.x, b.y, 105f, Pal.powerLight, 0.17f);
                }
            }
            Draw.reset(); Draw.z(z);
        }
        @Override public byte version(){ return 1; }
        @Override public void write(Writes w){
            super.write(w); w.i(links.size); for(int i = 0; i < links.size; i++) w.i(links.get(i));
            writeState(w);
        }
        @Override public void read(Reads r, byte revision){
            super.read(r, revision); links.clear(); int count = r.i();
            for(int i = 0; i < count; i++){ int pos = r.i(); if(i < maxLinks) links.add(pos); }
            readState(r);
        }
        private void writeState(Writes w){ w.f(shield); w.f(phaseTime); w.f(quiet); w.f(cooldown); w.f(fuse); w.bool(broken); }
        private void readState(Reads r){ shield = r.f(); phaseTime = r.f(); quiet = r.f(); cooldown = r.f(); fuse = r.f(); broken = r.bool(); }
    }
}
