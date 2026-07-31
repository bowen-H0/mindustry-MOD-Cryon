package example;

import arc.Core;
import arc.func.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.game.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.abilities.Ability;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.meta.*;

import static arc.Core.*;
import static mindustry.Vars.*;
// The most useless feature.
public class ElectronicJammerAbility extends Ability {

    public float jamRadius = 160f;

    public float offsetRadius = 100f;

    public float jamChance = 0.75f;

    protected float warmup = 0f;
    protected float pulse  = 0f;

    private static float sJamX, sJamY, sOffsetRadius, sJamChance;
    private static Team  sEnemyTeam;

    private static final Object JAMMED = new Object();

    private static final Cons<Bullet> bulletConsumer = b -> {
        if (b.team == sEnemyTeam) return;
        if (b.data == JAMMED) return;
        if (b.time > 2f) return;
        if (sJamChance < 1f && !Mathf.chance(sJamChance)) return;

        b.data = JAMMED;

        float angle = Mathf.random(360f);
        float dist  = Mathf.random(sOffsetRadius);
        float fakeX = sJamX + Mathf.cosDeg(angle) * dist;
        float fakeY = sJamY + Mathf.sinDeg(angle) * dist;

        float speed = b.vel.len();
        float dx = fakeX - b.x;
        float dy = fakeY - b.y;
        if (speed > 0.001f) {
            float currentAngle = Mathf.angle(b.vel.x, b.vel.y);
            float targetAngle  = Mathf.angle(dx, dy);
            float diff = ((targetAngle - currentAngle + 540f) % 360f) - 180f;
            float maxDeflect = 40f;
            if (Math.abs(diff) > maxDeflect) {
                diff = maxDeflect * Math.signum(diff);
            }
            float newAngle = currentAngle + diff;
            b.vel.set(Mathf.cosDeg(newAngle) * speed, Mathf.sinDeg(newAngle) * speed);
        }
    };

    public ElectronicJammerAbility() {}

    public ElectronicJammerAbility(float jamRadius, float offsetRadius) {
        this.jamRadius    = jamRadius;
        this.offsetRadius = offsetRadius;
    }

    public ElectronicJammerAbility(float jamRadius, float offsetRadius, float jamChance) {
        this.jamRadius    = jamRadius;
        this.offsetRadius = offsetRadius;
        this.jamChance    = jamChance;
    }

    @Override
    public void created(Unit unit) {
        warmup = 0f;
        pulse  = 0f;
    }

    @Override
    public void update(Unit unit) {
        warmup = Mathf.lerpDelta(warmup, 1f, 0.08f);
        pulse += Time.delta / 60f;
        if (pulse > 1f) pulse -= 1f;

        if (warmup < 0.05f) return;

        sJamX         = unit.x;
        sJamY         = unit.y;
        sOffsetRadius = offsetRadius;
        sJamChance    = jamChance;
        sEnemyTeam    = unit.team;

        float r = jamRadius * warmup;
        Groups.bullet.intersect(unit.x - r, unit.y - r, r * 2f, r * 2f, bulletConsumer);
    }

    @Override
    public void death(Unit unit) {
        warmup = 0f;
    }

    @Override
    public void draw(Unit unit) {
    }

    @Override
    public void addStats(Table t) {
        t.add(Core.bundle.format("bullet.range",
                Strings.autoFixed(jamRadius / tilesize, 2))).row();
        t.add("[lightgray]Offset radius: []"
                + Strings.autoFixed(offsetRadius, 2) + "px").row();
        if (jamChance < 1f) {
            t.add("[lightgray]Jam chance: []"
                    + Strings.autoFixed(jamChance * 100f, 0) + "%").row();
        }
    }
}