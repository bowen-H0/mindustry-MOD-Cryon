package cryon.Type;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mat;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.Vars;
import mindustry.entities.Effect;
import mindustry.entities.abilities.MoveEffectAbility;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;

/** Render-only lift and low-density hover jets; combat coordinates remain on the ground. */
public class AravisHoverUnitType extends UnitType{
    public float hoverHeight = 1.5f, bobAmplitude = 0.35f;
    private static final Color glow = Color.valueOf("a27ce5");
    private final Mat previous = new Mat(), raised = new Mat();
    private boolean renderingRaised;

    private static final Effect exhaust = new Effect(22f, e -> {
        Draw.color(e.color, e.fout() * 0.23f);
        Fill.circle(e.x, e.y, e.rotation * (0.5f + e.fin() * 0.8f));
        Draw.color(Color.white, e.fout() * 0.18f);
        Fill.circle(e.x, e.y, e.rotation * 0.25f * e.fout());
        Draw.reset();
    }).layer(Layer.groundUnit - 1f);

    private static final Effect wake = new Effect(30f, e -> {
        Draw.color(e.color, Color.white, 0.4f);
        Draw.alpha(e.fout() * 0.45f);
        Lines.stroke(0.5f * e.fout());
        Lines.ellipse(e.x, e.y, e.rotation * (0.6f + e.fin() * 1.8f), 1f, 0.65f, 0f);
        Draw.reset();
    }).layer(Layer.groundUnit - 1f);

    public AravisHoverUnitType(String name){
        super(name);
        shadowElevation = 0f;
    }

    public void addHoverJets(){
        for(int side = -1; side <= 1; side += 2){
            MoveEffectAbility ability = new HoverWake(side * hitSize * 0.34f, -hitSize * 0.38f);
            ability.effectParam = hitSize * 0.1f;
            ability.minVelocity = 0.12f;
            abilities.add(ability);
        }
    }

    public float visualLift(Unit unit){
        return hoverHeight + Mathf.sin(Time.time + unit.id * 11f, 18f, bobAmplitude);
    }

    @Override public void draw(Unit unit){
        // Stored units do not run their own hover engines inside another payload.
        if(!unit.isAdded()){
            super.draw(unit);
            return;
        }
        if(unit.inFogTo(Vars.player.team())) return;
        float z = Draw.z();
        Blending blend = Draw.getBlend();
        previous.set(Draw.trans());
        raised.set(previous).translate(0f, visualLift(unit));
        try{
            drawHoverJets(unit);
            Draw.trans(raised);
            renderingRaised = true;
            super.draw(unit);
        }finally{
            renderingRaised = false;
            Draw.trans(previous);
            Draw.blend(blend);
            Draw.z(z);
            Draw.reset();
        }
    }

    @Override public void drawSoftShadow(Unit unit){
        if(!renderingRaised){
            super.drawSoftShadow(unit);
            return;
        }
        Draw.trans(previous);
        try{super.drawSoftShadow(unit, 0.7f);}finally{Draw.trans(raised);}
    }

    private void drawHoverJets(Unit unit){
        float motion = Mathf.clamp(unit.vel.len() / speed);
        float pulse = 1f + Mathf.absin(Time.time + unit.id, 7f, 0.08f);
        Draw.z(groundLayer - 0.04f);
        Draw.blend(Blending.additive);
        for(int side = -1; side <= 1; side += 2){
            for(int end = -1; end <= 1; end += 2){
                float localX = side * hitSize * 0.34f, localY = end * hitSize * 0.24f;
                float x = unit.x + Angles.trnsx(unit.rotation - 90f, localX, localY);
                float y = unit.y + Angles.trnsy(unit.rotation - 90f, localX, localY);
                float radius = hitSize * 0.105f * pulse * (1f + motion * 0.25f);
                Draw.color(glow, 0.14f + motion * 0.1f);
                Fill.circle(x, y, radius * 1.8f);
                Draw.color(glow, 0.45f);
                Fill.circle(x, y, radius);
                Draw.color(Color.white, 0.7f);
                Fill.circle(x, y, radius * 0.35f);
                Drawf.light(x, y, radius * 7f, glow, 0.3f);
            }
        }
        Draw.blend();
        Draw.reset();
    }

    private static class HoverWake extends MoveEffectAbility{
        HoverWake(float x, float y){super(x, y, glow, exhaust, 8f);}
        @Override public void update(Unit unit){
            if(Vars.headless) return;
            effect = unit.floorOn().isLiquid ? wake : exhaust;
            super.update(unit);
        }
    }
}
