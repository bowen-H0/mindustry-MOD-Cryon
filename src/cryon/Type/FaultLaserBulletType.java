package cryon.Type;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import cryon.Fx.FaultFx;
import mindustry.content.Fx;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.gen.Bullet;
import mindustry.graphics.Drawf;

/** Native instantaneous laser collision with a recessed V-shaped beam origin. */
public final class FaultLaserBulletType extends LaserBulletType{
    public FaultLaserBulletType(){
        super(320f);
        length = 160f;
        width = 12f;
        lifetime = 20f;
        colors = new Color[]{FaultFx.orange.cpy().a(0.35f), FaultFx.orange, FaultFx.pale, Color.white};
        chargeEffect = FaultFx.charge;
        shootEffect = FaultFx.shoot;
        hitEffect = FaultFx.hit;
        hitColor = FaultFx.orange;
        smokeEffect = laserEffect = despawnEffect = Fx.none;
        collidesAir = false;
        ammoMultiplier = 1f;
        pierceCap = 3;
    }
    private float px(Bullet b, float forward, float side){ return b.x + Angles.trnsx(b.rotation(), forward, side); }
    private float py(Bullet b, float forward, float side){ return b.y + Angles.trnsy(b.rotation(), forward, side); }
    @Override public void draw(Bullet b){
        float length = b.fdata * Mathf.curve(b.fin(), 0f, 0.12f);
        if(length < 0.1f) return;
        for(int layer = 0; layer < colors.length; layer++){
            Draw.color(colors[layer]);
            float half = width * 0.5f * Mathf.pow(0.66f, layer) * b.fout();
            // The center starts ahead of the shoulders: a true concave cutout, not a flat line cap.
            float notch = Math.min(length * 0.4f, 11f * b.fout());
            float tip = Math.max(notch, length - 6f * b.fout());
            Fill.quad(px(b, 0f, half), py(b, 0f, half), px(b, tip, half * 0.55f), py(b, tip, half * 0.55f),
                    px(b, length, 0f), py(b, length, 0f), px(b, notch, 0f), py(b, notch, 0f));
            Fill.quad(px(b, notch, 0f), py(b, notch, 0f), px(b, length, 0f), py(b, length, 0f),
                    px(b, tip, -half * 0.55f), py(b, tip, -half * 0.55f), px(b, 0f, -half), py(b, 0f, -half));
        }
        Drawf.light(b.x, b.y, px(b, length, 0f), py(b, length, 0f), width * 2f * b.fout(), FaultFx.orange, 0.7f);
        Draw.reset();
    }
}
