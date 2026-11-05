package cryon.Type;

import arc.graphics.g2d.*;
import arc.math.Mathf;
import mindustry.gen.Bullet;
import mindustry.entities.Units;

/** Threshold-style drifting mines, with either a persistent gravity trap or a heavy bomb. */
public class EonMineBulletType extends MineBulletType{
    public final boolean gravity;
    public EonMineBulletType(boolean gravity){
        super(8f, 0f, gravity ? 90f : 72f);
        this.gravity = gravity;
        lifetime = 300f;
        rangeOverride = 520f;
        drag = 0.025f;
        armTime = 45f;
        triggerRadius = gravity ? 70f : 40f;
        color = EonPhaseBulletType.phase;
        goldColor = arc.graphics.Color.white;
    }
    @Override public void init(Bullet b){
        super.init(b);
        // With linear drag, total travel is approximately initial speed / drag.
        if(b.aimX >= 0f && b.aimY >= 0f)
            b.vel.setLength(Mathf.clamp(b.dst(b.aimX, b.aimY) * drag, 3f, 13f));
    }
    @Override public void update(Bullet b){
        // Do not run MineBulletType.update: gravity mines remain alive after triggering.
        if(b.time < armTime) return;
        if(b.data instanceof EonPhaseBulletType.GravityWellState){
            EonPhaseBulletType.pull(b, 140f, 0.8f, true);
            if(b.timer(1, 20f)) EonPhaseBulletType.areaDamage(b, 70f, 100f, false);
            return;
        }
        if(Units.closestEnemy(b.team, b.x, b.y, triggerRadius, u -> u.targetable(b.team)) == null) return;
        if(gravity){
            b.data = new EonPhaseBulletType.GravityWellState();
            b.vel.setZero();
            b.lifetime = b.time + 90f;
            EonPhaseBulletType.burst(b, 90f, 500f, true);
        }else{
            EonPhaseBulletType.burst(b, 72f, 6500f, true);
            b.remove();
        }
    }
    @Override public void draw(Bullet b){
        super.draw(b);
        if(!(b.data instanceof EonPhaseBulletType.GravityWellState)) return;
        Draw.color(color);
        Lines.stroke(2f);
        Lines.circle(b.x, b.y, 140f);
        for(int i = 0; i < 3; i++){
            float radius = 30f + Mathf.mod(b.time * 2f + i * 35f, 105f);
            Lines.arc(b.x, b.y, radius, 0.22f, b.time * 2f + i * 120f);
        }
        Draw.reset();
    }
}
