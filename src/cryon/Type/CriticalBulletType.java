package cryon.Type;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import cryon.Fx.CriticalFx;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.gen.Bullet;
import mindustry.graphics.Drawf;

/** A white-hot spear with counter-rotating containment arcs. No extra gameplay projectiles. */
public class CriticalBulletType extends BasicBulletType{
    public CriticalBulletType(float speed, float damage){ super(speed, damage); }

    @Override
    public void draw(Bullet b){
        super.draw(b);
        float z = Draw.z();
        Draw.z(layer + 0.01f);
        float pulse = 1f + Mathf.absin(b.time, 3f, 0.12f);
        Draw.color(backColor);
        Drawf.tri(b.x, b.y, 15f * pulse, 44f, b.rotation());
        Drawf.tri(b.x, b.y, 12f * pulse, 64f, b.rotation() + 180f);
        Lines.stroke(2.4f);
        for(int i = 0; i < 2; i++){
            float angle = b.time * (i == 0 ? 7f : -5f) + i * 180f;
            Lines.arc(b.x, b.y, 19f + i * 5f, 0.28f, angle);
            float x = b.x + Angles.trnsx(angle, 19f + i * 5f);
            float y = b.y + Angles.trnsy(angle, 19f + i * 5f);
            Fill.square(x, y, 2.5f, angle + 45f);
        }
        Draw.color(Color.white);
        Drawf.tri(b.x, b.y, 6f, 33f, b.rotation());
        Drawf.tri(b.x, b.y, 5f, 44f, b.rotation() + 180f);
        Fill.circle(b.x, b.y, 6f * pulse);
        Draw.reset();
        Draw.z(z);
    }
}
