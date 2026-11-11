package cryon.Type;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.util.Time;
import cryon.Fx.PurificationFx;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.world.blocks.defense.turrets.PowerTurret;

/** Tapered signal mast with camera parallax, cross braces and directional antenna panels. */
public class PurificationTurret extends PowerTurret{
    private static final Color frame = Color.valueOf("252d43");
    private static final Color edge = Color.valueOf("8dacca");
    private static final Color panel = Color.valueOf("bacde2");
    private static final Color side = Color.valueOf("495a78");
    private static final Color lamp = Color.valueOf("ff7766");

    public PurificationTurret(String name){
        super(name);
        clipSize = 6000f;
        buildType = PurificationTurretBuild::new;
    }

    public class PurificationTurretBuild extends PowerTurretBuild{
        private float towerX(float height){
            return x + OrbitalPowerNode.parallaxOffset(x, Core.camera.position.x, height / 76f, 14f);
        }

        private float towerY(float height){
            return y + height + OrbitalPowerNode.parallaxOffset(y, Core.camera.position.y, height / 76f, 12f);
        }

        @Override public void draw(){
            super.draw();
            if(Core.camera == null) return;
            float previous = Draw.z();
            Draw.z(Layer.turret + 0.1f);
            Drawf.shadow(x + 14f, y - 18f, 48f);
            for(int level = 0; level < 4; level++){
                float lower = level * 14f, upper = lower + 14f;
                float lx = towerX(lower), ly = towerY(lower);
                float ux = towerX(upper), uy = towerY(upper);
                float lw = 13f - level * 2.2f, uw = lw - 2.2f;
                Draw.color(frame);
                Fill.quad(lx - lw, ly, lx + lw, ly, ux + uw, uy, ux - uw, uy);
                Draw.color(side);
                Fill.quad(lx, ly, lx + lw, ly, ux + uw, uy, ux, uy);
                Draw.color(edge);
                Lines.stroke(1.5f);
                Lines.line(lx - lw, ly, ux - uw, uy);
                Lines.line(lx + lw, ly, ux + uw, uy);
                Lines.line(ux - uw, uy, ux + uw, uy);
                Draw.color(panel);
                Lines.stroke(0.9f);
                Lines.line(lx - lw + 1f, ly + 1f, ux + uw - 1f, uy - 1f);
                Draw.color(edge);
                Lines.line(lx + lw - 1f, ly + 1f, ux - uw + 1f, uy - 1f);
            }

            float ax = towerX(56f), ay = towerY(56f);
            Draw.color(frame);
            Lines.stroke(4f);
            Lines.line(ax, ay - 3f, towerX(76f), towerY(76f));
            Draw.color(panel);
            Lines.stroke(1.3f);
            Lines.line(ax - 0.7f, ay - 3f, towerX(76f) - 0.7f, towerY(76f));

            // Side-facing antenna panels have bright front faces and shaded thickness.
            for(int sign : Mathf.signs){
                float px = ax + sign * 9f, py = ay - 5f + sign * 3f;
                Draw.color(frame);
                Lines.stroke(2.8f);
                Lines.line(ax, py, px, py);
                Draw.color(side);
                Fill.quad(px - 3f, py - 9f, px + 3f, py - 7f,
                    px + 3f, py + 9f, px - 3f, py + 7f);
                Draw.color(panel);
                Fill.quad(px - 2f, py - 8f, px + 1f, py - 7f,
                    px + 1f, py + 8f, px - 2f, py + 7f);
                Draw.color(PurificationFx.blue);
                Lines.stroke(0.8f);
                for(int rib = -1; rib <= 1; rib++)
                    Lines.line(px - 1.5f, py + rib * 4f, px + 0.5f, py + rib * 4f + 0.7f);
            }
            for(int rung = 0; rung < 3; rung++){
                float height = 61f + rung * 4f;
                float width = 8f - rung * 1.8f;
                Draw.color(edge);
                Lines.stroke(1.1f);
                Lines.line(towerX(height) - width, towerY(height),
                    towerX(height) + width, towerY(height));
            }

            float power = enabled ? efficiency : 0f;
            float tipX = towerX(76f), tipY = towerY(76f);
            Draw.z(Layer.effect);
            Draw.color(lamp);
            Draw.alpha(0.2f + power * Mathf.absin(Time.time + id, 7f, 0.8f));
            Fill.circle(tipX, tipY, 1.8f);
            Drawf.light(tipX, tipY, 23f, lamp, power * 0.45f);
            Draw.color(PurificationFx.blue);
            Lines.stroke(1f);
            for(int i = 0; i < 2; i++){
                float phase = (Time.time * 0.009f + i * 0.5f) % 1f;
                Draw.alpha(power * (1f - phase) * 0.55f);
                Lines.arc(tipX, tipY, 5f + phase * 17f, 0.22f, 5f);
                Lines.arc(tipX, tipY, 5f + phase * 17f, 0.22f, 185f);
            }
            Draw.reset();
            Draw.z(previous);
        }
    }
}
