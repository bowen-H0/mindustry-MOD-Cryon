package cryon.Type;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.blocks.sandbox.PowerSource;
import mindustry.world.meta.BuildVisibility;
import mindustry.world.meta.Stat;

import static mindustry.Vars.*;

/** Sandbox orbital receiver with original fractured energy ribbons and falling packets. */
public final class OrbitalPowerNode extends PowerSource{
    private static final Color gold = Pal.powerLight;
    private static final Color warmCore = Pal.powerLight.cpy().lerp(Color.white, 0.24f);

    public OrbitalPowerNode(String name){
        super(name);
        requirements(Category.power, BuildVisibility.sandboxOnly, ItemStack.empty);
        size = 3;
        health = 3200;
        laserRange = 36f;
        maxNodes = 64;
        powerProduction = 1000000f;
        placeablePlayer = true;
        rebuildable = true;
        alwaysUnlocked = true;
        canOverdrive = false;
        // The beam submits a continuous light segment; do not add a duplicate base point light.
        emitLight = false;
        clipSize = 16000f;
        buildType = OrbitalPowerNodeBuild::new;
    }

    @Override public void setStats(){
        super.setStats();
        stats.remove(Stat.basePowerGeneration);
        stats.add(Stat.basePowerGeneration, "∞");
    }

    public class OrbitalPowerNodeBuild extends PowerSourceBuild{
        @Override public float getPowerProduction(){
            if(!enabled || power == null) return 0f;
            // Feed the actual graph demand and battery charge in this tick. Remain finite:
            // Infinity in PowerGraph arithmetic would turn stored power/satisfaction into NaN.
            double demand = Math.max(0f, power.graph.getPowerNeeded());
            double storage = Math.max(0f, power.graph.getBatteryCapacity());
            double output = Math.max(powerProduction, (demand + storage) / Math.max(delta(), 0.0001f) * 1.05d);
            return (float)Math.min(Float.MAX_VALUE / 4096d, output);
        }

        @Override public void draw(){
            super.draw();
            if(!enabled || headless || Core.camera == null) return;
            float previous = Draw.z();
            Blending blend = Draw.getBlend();
            try{
                float height = Math.max(1100f, Core.camera.position.y + Core.camera.height / 2f + 180f - y);
                float topX = x + parallaxOffset(x, Core.camera.position.x, 1f, 85f);
                float topY = y + height;
                Draw.z(Layer.effect + 0.1f);
                Draw.blend(Blending.normal);
                Fill.light(x, y, 48, 95f, Tmp.c1.set(gold).a(0.1f), Tmp.c2.set(gold).a(0f));
                Draw.color(gold, 0.42f);
                Lines.stroke(1.3f);
                // Four short receiver brackets, with no floating emblem or circular display.
                for(int corner = 0; corner < 4; corner++){
                    float sx = corner % 2 == 0 ? -1f : 1f, sy = corner < 2 ? -1f : 1f;
                    Lines.line(x + sx * 26f, y + sy * 17f, x + sx * 26f, y + sy * 26f);
                    Lines.line(x + sx * 26f, y + sy * 26f, x + sx * 17f, y + sy * 26f);
                }

                Draw.z(Layer.light + 0.01f);
                // Independent broken sheets: irregular gaps and offsets create depth without
                // a solid cylinder, a projected badge, or another mod's ring animation.
                for(int lane = 0; lane < 5; lane++){
                    for(int segment = 0; segment < 52; segment++){
                        if((segment + lane * 3 + (int)(Time.time / 14f)) % 13 < 2) continue;
                        float a = segment / 52f, b = (segment + 0.9f) / 52f;
                        float ax = ribbonX(lane, a), bx = ribbonX(lane, b);
                        float aw = (lane % 2 == 0 ? 1.9f : 1.2f) * (0.8f + a);
                        float bw = (lane % 2 == 0 ? 1.9f : 1.2f) * (0.8f + b);
                        float energy = 0.5f + 0.5f * Mathf.sin(Time.time * 0.045f - segment * 0.8f + lane * 1.9f);
                        Draw.color(lane % 2 == 0 ? gold : warmCore, 0.13f + energy * 0.16f);
                        Fill.quad(ax - aw, y + a * height, ax + aw, y + a * height,
                            bx + bw, y + b * height, bx - bw, y + b * height);
                        Draw.color(warmCore, 0.25f + energy * 0.2f);
                        Lines.stroke(0.65f);
                        Lines.line(ax, y + a * height, bx, y + b * height);
                    }
                    for(int packet = 0; packet < 8; packet++){
                        float fall = (Time.time * (4.2f + lane * 0.37f) + packet * 151f + id * 11f + lane * 31f) % height;
                        float fraction = 1f - fall / height;
                        float next = Math.min(1f, fraction + (16f + packet * 4f) / height);
                        float px = ribbonX(lane, fraction), py = y + fraction * height;
                        Draw.color(warmCore, 0.38f);
                        Lines.stroke(1.1f);
                        Lines.line(px, py, ribbonX(lane, next), y + next * height);
                        if(packet % 3 == 0){
                            Draw.color(gold, 0.36f);
                            Fill.quad(px - 2.3f, py, px, py + 7f, px + 2.3f, py, px, py - 7f);
                        }
                    }
                }
                Draw.color(warmCore, 0.55f);
                Fill.circle(x, y, 4.5f);
                Fill.light(x, y, 32, 25f, Tmp.c1.set(gold).a(0.15f), Tmp.c2.set(gold).a(0f));
                Drawf.light(x, y, topX, topY, 210f, gold, 0.28f);
                Drawf.light(x, y, 125f, gold, 0.16f);
            }finally{
                Draw.blend(blend);
                Draw.reset();
                Draw.z(previous);
            }
        }

        private float ribbonX(int lane, float fraction){
            float spread = 4f + fraction * 37f;
            float drift = Mathf.sin(fraction * 8f + lane * 1.7f + Time.time * 0.008f) * (2f + fraction * 6f);
            return x + (lane - 2f) * spread * 0.7f + drift
                + parallaxOffset(x, Core.camera.position.x, fraction, 85f);
        }
    }

    /** A bounded linear visual offset, independent of perspective-plane projection formulas. */
    public static float parallaxOffset(float ground, float camera, float altitude, float limit){
        return Mathf.clamp((ground - camera) * 0.22f, -Math.abs(limit), Math.abs(limit)) * Mathf.clamp(altitude);
    }
}
