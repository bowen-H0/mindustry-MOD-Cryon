package cryon.Type;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Time;
import mindustry.gen.*;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;

import static mindustry.Vars.*;

/** A high-to-ground column. AI uses a forgiving hover station; possession aims directly at the cursor. */
public class DownwardBurnBeamBulletType extends LockOnBurnBulletType implements OrbitalProjectile{
    public float hoverHeight = 96f, hoverRadius = 64f;
    private final Vec2 hover = new Vec2();

    public DownwardBurnBeamBulletType(){
        targetsOrbit = false;
        collidesAir = false;
        collidesGround = true;
        maxFocusMultiplier = 1f;
        beamWidth = 2.5f;
        beamColor = Color.valueOf("a8f2ed");
    }

    /** The ship's screen-space altitude is represented by an offset from its ground footprint. */
    public Vec2 hoverPoint(Unit owner, Teamc target, Vec2 out){
        float margin = owner.hitSize;
        return out.set(Mathf.clamp(target.getX(), margin, Math.max(margin, world.unitWidth() - margin)),
            Mathf.clamp(target.getY() + hoverHeight, margin, Math.max(margin, world.unitHeight() - margin)));
    }

    @Override public void update(Bullet b){
        if(!(b.data instanceof BeamState state)) return;
        state.active = false;
        if(!(b.owner instanceof Unit owner) || !firing(b) || OrbitalGroundHoverAI.inTransit(owner)){
            state.target = null;
            state.focus = 0f;
            return;
        }
        boolean manual = owner.isPlayer() || owner.controller() instanceof mindustry.ai.types.LogicAI;
        if(manual){
            // Read the controller's aim, never the previous frame's locked beam endpoint.
            if(!Float.isFinite(owner.aimX) || !Float.isFinite(owner.aimY) || owner.aimX < 0f || owner.aimY < 0f){
                state.target = null;
                state.focus = 0f;
                return;
            }
            hover.set(owner.aimX - b.x, owner.aimY - b.y).limit(rangeOverride).add(b.x, b.y);
            b.aimX = hover.x;
            b.aimY = hover.y;
            Teamc selected = targetAt(b, hover.x, hover.y);
            if(selected != state.target) state.focus = 0f;
            state.target = selected;
            state.active = true;
            state.endX = selected == null ? hover.x : selected.getX();
            state.endY = selected == null ? hover.y : selected.getY();
        }else{
            Teamc selected = intendedTarget(b);
            if(selected != state.target) state.focus = 0f;
            state.target = selected;
            if(!valid(b, selected)){ state.target = null; state.focus = 0f; return; }
            hoverPoint(owner, selected, hover);
            if(!b.within(hover, hoverRadius)){ state.focus = 0f; return; }
            state.active = true;
            state.endX = selected.getX();
            state.endY = selected.getY();
        }
        b.aimX = state.endX;
        b.aimY = state.endY;
        if(state.target != null){
            state.focus += Time.delta;
            damageTarget(b, state);
        }
    }

    @Override public void draw(Bullet b){
        if(!(b.data instanceof BeamState state) || !state.active) return;
        float previous = Draw.z(), fade = b.fslope();
        float sx = b.x, sy = b.y, ex = state.endX, ey = state.endY;
        // Vary width and depth within the orbital pass; crossing reserved shield framebuffer layers causes seams.
        for(int i = 0; i < 16; i++){
            float t1 = i / 16f, t2 = (i + 1f) / 16f;
            float x1 = Mathf.lerp(sx, ex, t1), y1 = Mathf.lerp(sy, ey, t1);
            float x2 = Mathf.lerp(sx, ex, t2), y2 = Mathf.lerp(sy, ey, t2);
            float w1 = Mathf.lerp(beamWidth, beamWidth * 3.5f, t1) * fade;
            float w2 = Mathf.lerp(beamWidth, beamWidth * 3.5f, t2) * fade;
            Draw.z(OrbitalUnitType.orbitalLayer - 0.2f - t2 * 0.1f);
            Draw.color(beamColor, 0.12f * fade);
            Fill.quad(x1 - w1, y1, x1 + w1, y1, x2 + w2, y2, x2 - w2, y2);
            Draw.color(beamColor, 0.88f * fade);
            Lines.stroke(beamWidth * fade); Lines.line(x1, y1, x2, y2, false);
            Draw.color(Color.white, 0.95f * fade);
            Lines.stroke(beamWidth * 0.35f * fade); Lines.line(x1, y1, x2, y2, false);
        }
        // Traveling horizontal rings show a downward flow rather than a beam laid on the map plane.
        for(int i = 0; i < 4; i++){
            float t = (Time.time / 42f + i / 4f) % 1f;
            Draw.z(OrbitalUnitType.orbitalLayer - 0.19f - t * 0.1f);
            Draw.color(beamColor, 0.48f * fade);
            Lines.stroke(0.7f * fade);
            Lines.ellipse(24, Mathf.lerp(sx, ex, t), Mathf.lerp(sy, ey, t), Mathf.lerp(3f, 10f, t), 2f, 0f);
        }
        Draw.z(Layer.bullet + 1.2f);
        Draw.color(beamColor, 0.75f * fade);
        Lines.stroke(1.2f * fade);
        Lines.ellipse(36, ex, ey, 12f + Mathf.absin(4f, 2f), 4f, 0f);
        Draw.color(Color.white, fade);
        Fill.circle(ex, ey, 2f * fade);
        Drawf.light(ex, ey, 42f, beamColor, 0.7f * fade);
        Draw.reset();
        Draw.z(previous);
    }
}
