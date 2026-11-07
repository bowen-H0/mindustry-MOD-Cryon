package cryon.Fx;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.Time;
import mindustry.Vars;
import mindustry.entities.Effect;
import mindustry.gen.Call;
import mindustry.graphics.Layer;

/** Native, texture-free effects. Event budgets also bound dedicated-server network traffic. */
public final class NanoFeedbackFx{
    public static final Color green = Color.valueOf("4ce68c"), cyan = Color.valueOf("89dcec");
    private static final Color acid = Color.valueOf("b7f57e");
    public static final Effect assimilate = new Effect(28f, e -> {
        Draw.color(green, Color.white, e.fout() * 0.4f);
        Lines.stroke(e.fout()); Lines.square(e.x, e.y, 2f + e.fin() * 5f, 45f);
        motes(e, 5, 9f, false); Draw.reset();
    }).layer(Layer.blockOver);
    public static final Effect oreBloom = new Effect(42f, e -> {
        Draw.color(green, Color.white, e.fout() * 0.6f);
        Lines.stroke(1.5f * e.fout()); Lines.circle(e.x, e.y, 2f + e.fin() * 12f);
        motes(e, 10, 15f, false); Draw.reset();
    }).layer(Layer.blockOver);
    public static final Effect grow = new Effect(48f, 160f, e -> {
        float radius = Math.max(6f, e.rotation * 4f);
        Draw.color(green, Color.white, e.fout() * 0.45f);
        Lines.stroke(1.6f * e.fout()); Lines.square(e.x, e.y, radius * (0.5f + e.fin() * 0.65f));
        motes(e, 12, radius + 9f, true); Draw.reset();
    }).layer(Layer.blockOver);
    public static final Effect corrosion = new Effect(32f, e -> {
        Draw.color(green, acid, e.fin());
        motes(e, 5, Mathf.clamp(e.rotation, 5f, 22f), false); Draw.reset();
    }).layer(Layer.flyingUnitLow + 0.1f);
    public static final Effect repair = new Effect(32f, e -> {
        Draw.color(green, Color.white, 0.4f);
        motes(e, 5, 9f, true); Draw.reset();
    }).layer(Layer.blockOver);
    public static final Effect containment = new Effect(65f, 400f, e -> {
        float radius = Math.max(16f, e.rotation);
        Draw.color(cyan); Draw.alpha(e.fslope() * 0.4f);
        Lines.stroke(0.8f * e.fout()); Lines.circle(e.x, e.y, radius * (0.8f + e.fin() * 0.2f));
        for(int i = 0; i < 12; i++){
            float angle = i * 30f + e.fin() * 12f;
            float px = e.x + Angles.trnsx(angle, radius), py = e.y + Angles.trnsy(angle, radius);
            Fill.square(px, py, 1.8f * e.fslope(), angle + 45f);
        }
        Draw.reset();
    }).layer(Layer.blockOver);
    public static final Effect rejected = new Effect(25f, e -> {
        Draw.color(green, cyan, e.fin());
        Lines.stroke(1.2f * e.fout()); Lines.circle(e.x, e.y, 2f + e.fin() * 7f);
        motes(e, 5, 8f, true); Draw.reset();
    }).layer(Layer.blockOver);
    public static final Effect charge = new Effect(30f, e -> {
        Draw.color(green, Color.white, 0.3f);
        motes(e, 6, 12f, true); Draw.reset();
    }).layer(Layer.blockOver);
    public static final Effect warning = new Effect(24f, e -> {
        Draw.color(green, Color.orange, e.fin());
        Lines.stroke(1.5f * e.fout()); Lines.square(e.x, e.y, 5f + e.fin() * 12f, 45f);
        motes(e, 7, 16f, false); Draw.reset();
    }).layer(Layer.blockOver);
    public static final Effect pulseShot = new Effect(20f, e -> {
        Draw.color(green, Color.white, e.fout());
        Lines.stroke(2f * e.fout()); Lines.circle(e.x, e.y, 1f + e.fin() * 9f);
        for(int i = 0; i < 5; i++){
            float angle = e.rotation + Mathf.randomSeed(e.id * 31L + i, -28f, 28f);
            float radius = 3f + e.fin() * (8f + i * 3f);
            Fill.square(e.x + Angles.trnsx(angle, radius), e.y + Angles.trnsy(angle, radius), 1.8f * e.fout(), 45f);
        }
        Draw.reset();
    }).layer(Layer.bullet + 0.1f);
    public static final Effect laserHit = new Effect(28f, e -> {
        Draw.color(green, Color.white, e.fout() * 0.7f);
        motes(e, 9, 17f, false); Draw.reset();
    }).layer(Layer.bullet + 0.1f);
    public static final Effect explosion = new Effect(95f, 400f, e -> {
        float radius = Math.max(40f, e.rotation);
        Draw.color(green, Color.white, e.fout() * 0.6f);
        Draw.alpha(e.fout()); Lines.stroke(4f * e.fout());
        Lines.circle(e.x, e.y, radius * e.finpow());
        Lines.stroke(1.8f * e.fout()); Lines.circle(e.x, e.y, radius * e.finpow() * 0.7f);
        motes(e, 32, radius, false); Draw.reset();
    }).layer(Layer.bullet + 0.2f);

    private static void motes(Effect.EffectContainer e, int count, float radius, boolean inward){
        Draw.alpha(e.fslope());
        for(int i = 0; i < count; i++){
            float angle = Mathf.randomSeed(e.id * 91L + i, 360f) + e.fin() * (inward ? 65f : 15f);
            float distance = radius * Mathf.randomSeed(e.id * 43L + i, 0.25f, 1f) * (inward ? e.fout() : e.finpow());
            float px = e.x + Angles.trnsx(angle, distance), py = e.y + Angles.trnsy(angle, distance);
            Fill.square(px, py, (0.8f + Mathf.randomSeed(e.id * 17L + i, 1.2f)) * e.fout(), angle + 45f);
            Lines.stroke(0.7f * e.fout());
            Lines.lineAngle(px, py, angle + (inward ? 180f : 0f), 2f + e.fout() * 3f);
        }
    }

    private static long eventWindow = Long.MIN_VALUE, localWindow = Long.MIN_VALUE;
    private static final int[] events = new int[5];
    private static int localCount;
    /** Separate quotas keep corrosion and major explosions visible during large terrain waves. */
    public static void event(Effect effect, float x, float y, float rotation, int group){
        if(Vars.net == null || Vars.net.client() || (Vars.headless && !Vars.net.server())) return;
        long window = (long)(Vars.state.tick / 60d);
        if(window != eventWindow){ eventWindow = window; java.util.Arrays.fill(events, 0); }
        int limit = group == 0 ? 24 : group == 3 ? 8 : group == 4 ? 16 : 20;
        if(events[group]++ >= limit) return;
        // The native call displays locally in single player and also reaches network clients.
        Call.effect(effect, x, y, rotation, green);
    }
    /** Ambient feedback is created by each visible peer; no per-frame network messages. */
    public static void ambient(Effect effect, float x, float y, float rotation, float period){
        if(Vars.headless || Vars.state == null || !Vars.state.isGame() || Vars.state.isPaused()) return;
        if(!Mathf.chanceDelta(1f / period)) return;
        long window = (long)(Time.time / 60f);
        if(window != localWindow){ localWindow = window; localCount = 0; }
        if(localCount++ < 90) effect.at(x, y, rotation, green);
    }
}
