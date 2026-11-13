package cryon.Type;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Time;
import cryon.Fx.BeaconFx;
import cryon.Features.OrbitalSupport;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.ArtilleryBulletType;
import mindustry.game.Team;
import mindustry.gen.Bullet;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.world.blocks.defense.turrets.Turret.TurretBuild;

/**
 * BeaconBulletType —— 轨道打击标记弹。
 * 基于 ArtilleryBulletType:弹道会精确落在开火瞬间选定的目标点上,
 * 弹体本身完全不绘制,玩家看到落点警告标记,延迟后蓝色光束落下造成范围伤害。
 */
public class BeaconBulletType extends ArtilleryBulletType {
    public boolean fusedTargeting;
    public boolean requiresSkywatch = true;

    public float strikeDelay = 90f;
    public float strikeRadius = 80f;
    public float strikeDamage = 220f;
    public Color markColor = Color.valueOf("57c2ff");
    public Color beamColor = Color.valueOf("bfe9ff");

    public static class Marker {
        public float x, y, radius, delay, startTime;
        public Color color;
        public Team team;
        public boolean supportRequired;
        public int claimedBy = -1;
        public float attenuation = 1f;

        public Marker(float x, float y, float radius, float delay, Color color, Team team) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.delay = delay;
            this.color = color;
            this.team = team;
            this.startTime = Time.time;
        }

        public float progress() {
            return Mathf.clamp((Time.time - startTime) / delay);
        }
    }

    public static final Seq<Marker> markers = new Seq<>();

    public BeaconBulletType(float speed, float unusedDamage) {
        super(speed, 0f);
        damage = 0f;
        splashDamage = 0f;
        collides = false;
        hittable = true;
        absorbable = false;
        pierce = false;
        despawnEffect = Fx.none;
        hitEffect = Fx.none;
        if (lifetime <= 0f) lifetime = 20f;
    }

    @Override
    public void draw(Bullet b) {
    }

    @Override
    public void update(Bullet b) {
        super.update(b);

        if (b.time < 1f) {
            markFrom(b, b.aimX, b.aimY);
            b.remove();
        }
    }

    @Override
    public void hit(Bullet b, float x, float y) {
        markFrom(b, b.aimX, b.aimY);
    }

    public void fuseAt(Bullet source, float x, float y){ markFrom(source, x, y); }

    private void markFrom(Bullet source, float x, float y){
        if(!Float.isFinite(x) || !Float.isFinite(y) || x < 0f || y < 0f) return;
        float originX = source.x, originY = source.y, reach = range;
        if(source.owner instanceof TurretBuild){
            TurretBuild turret = (TurretBuild)source.owner;
            originX = turret.x; originY = turret.y; reach = turret.range();
        }else if(source.owner instanceof Unit){
            Unit unit = (Unit)source.owner;
            originX = unit.x; originY = unit.y; reach = unit.range();
        }
        if(!Float.isFinite(originX) || !Float.isFinite(originY) || !Float.isFinite(reach) || reach < 0f
                || Mathf.dst2(originX, originY, x, y) > reach * reach) return;
        mark(source.team, x, y);
    }

    protected void mark(Team team, float x, float y) {
        float attenuation = cryon.Features.ProjectileTags.current != null && cryon.Features.ProjectileTags.current.realityWeakened ? 0.5f : 1f;
        if(requiresSkywatch && !fusedTargeting && OrbitalSupport.playerTeam(team)){
            OrbitalSupport.request(this, team, x, y, attenuation);
            Fx.circleColorSpark.at(x, y, markColor);
            return;
        }
        markDirect(team, x, y, attenuation);
    }

    protected void markDirect(Team team, float x, float y, float attenuation){
        Marker m = new Marker(x, y, strikeRadius, strikeDelay, markColor, team);
        markers.add(m);
        Fx.circleColorSpark.at(x, y, markColor);

        Time.run(strikeDelay, () -> {
            if(!markers.remove(m, true) || Vars.state == null || !Vars.state.isGame()) return;
            strike(team, x, y, attenuation);
        });
    }

    protected void strike(Team team, float x, float y) {
        strike(team, x, y, 1f);
    }

    protected void strike(Team team, float x, float y, float attenuation) {
        BeaconFx.beam.at(x, y, 0f, beamColor);
        BeaconFx.impact.at(x, y, 0f, beamColor);

        if(Vars.net != null && Vars.net.client()) return;

        Units.nearbyEnemies(team, x, y, strikeRadius, unit -> {
            if (!cryon.Features.MapAlliances.allied(unit.team, team) && (fusedTargeting?unit.checkTarget(collidesAir,collidesGround):unit.isGrounded())) {
                unit.damage(strikeDamage * attenuation);
            }
        });

        if(!fusedTargeting || collidesGround)Vars.indexer.eachBlock(null, x, y, strikeRadius,
                building -> !cryon.Features.MapAlliances.allied(building.team, team),
                building -> building.damage(team, strikeDamage * attenuation)
        );

        Fx.hitBulletColor.at(x, y, 0f, beamColor);

        Effect.shake(4f, 4f, x, y);
    }

    public static void drawMarkers() {
        for (Marker m : markers) {
            float progress = m.supportRequired ? 0f : m.progress();

            Draw.z(Layer.flyingUnit + 1f);
            Draw.color(m.color);

            Lines.stroke(2f);
            Draw.alpha(0.35f + 0.45f * Mathf.absin(Time.time, 4f - progress * 2.5f, 1f));
            Lines.circle(m.x, m.y, m.radius * (1.15f - progress * 0.15f));

            float cross = m.radius * (1f - progress) + 4f;
            Draw.alpha(0.6f);
            Lines.line(m.x - cross, m.y, m.x + cross, m.y);
            Lines.line(m.x, m.y - cross, m.x, m.y + cross);

            Draw.reset();
        }
    }
}
