package cryon.Fx;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import mindustry.entities.Effect;
import mindustry.gen.Building;
import mindustry.graphics.*;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.draw.DrawBlock;

/** Contained process visuals: crystallization, lattice scanning, phase vortex and compression. */
public final class FactoryProductionFx{
    private FactoryProductionFx(){}

    public static Effect activity(int kind, int size, Color color){
        final float radius = size * 2.5f;
        return new Effect(32f, size * 16f, e -> {
            Draw.color(color, Color.white, e.fout() * 0.4f);
            Draw.alpha(e.fout() * 0.8f);
            Lines.stroke(0.8f * e.fout());
            for(int i = 0; i < 4; i++){
                float angle = i * 90f + Mathf.randomSeed(e.id, 35f);
                float r = kind >= 2 ? radius * e.fout() : radius * e.fin();
                float x = e.x + Angles.trnsx(angle, r), y = e.y + Angles.trnsy(angle, r);
                if(kind == 0) Lines.square(x, y, 1f + e.fout(), 45f);
                else if(kind == 1) Fill.square(x, y, 0.8f * e.fout());
                else Lines.lineAngle(x, y, angle + (kind == 2 ? 65f : 0f), 3f * e.fout());
            }
            Draw.reset();
        }).layer(Layer.blockOver + 0.2f);
    }

    public static Effect complete(int kind, int size, Color color){
        final float radius = size * 3f;
        return new Effect(kind == 2 ? 32f : 55f, size * 20f, e -> {
            Draw.color(color);
            Draw.alpha(e.fout() * (kind == 2 ? 0.5f : 0.9f));
            Lines.stroke((kind == 3 ? 2.4f : 1.6f) * e.fout());
            if(kind == 1){
                Lines.square(e.x, e.y, radius * (0.4f + 0.6f * e.fin()));
                Lines.square(e.x, e.y, radius * (0.25f + 0.45f * e.fin()), 45f);
            }else if(kind == 3){
                Lines.poly(e.x, e.y, 6, radius * (0.35f + 0.65f * e.fin()), 30f);
                Lines.poly(e.x, e.y, 6, radius * (0.2f + 0.4f * e.fin()), -30f);
            }else{
                Lines.circle(e.x, e.y, radius * (0.3f + 0.7f * e.fin()));
            }
            Draw.color(color, Color.white, e.fout());
            for(int i = 0; i < 4; i++){
                float angle = 45f + i * 90f;
                float r = radius * e.fin() * 0.7f;
                float x = e.x + Angles.trnsx(angle, r), y = e.y + Angles.trnsy(angle, r);
                if(kind <= 1) Lines.square(x, y, (kind == 0 ? 2f : 1.4f) * e.fout(), 45f);
                else Lines.lineAngle(x, y, angle, 5f * e.fout());
            }
            if(kind == 3){
                Draw.alpha(e.fout());
                Fill.square(e.x, e.y, 3f * e.fout(), 45f);
            }
            Drawf.light(e.x, e.y, radius * 3f, color, (kind == 2 ? 0.3f : 0.65f) * e.fout());
            Draw.reset();
        }).layer(Layer.blockOver + 0.25f);
    }

    public static class ProcessDraw extends DrawBlock{
        public final int kind;
        public final Color color;
        public ProcessDraw(int kind, Color color){this.kind = kind;this.color = color;}

        private float activity(Building b){
            return b.enabled ? b.warmup() * Mathf.clamp(b.efficiency) : 0f;
        }

        @Override public void draw(Building b){
            float active = activity(b);
            if(active <= 0.001f) return;
            GenericCrafter.GenericCrafterBuild build = (GenericCrafter.GenericCrafterBuild)b;
            float radius = b.block.size * 2.4f, time = build.totalProgress;
            float z = Draw.z();
            Draw.z(Layer.blockOver + 0.15f);
            Draw.color(color);
            Draw.alpha(active * 0.85f);
            Lines.stroke(0.7f + active * 0.4f);
            if(kind == 0){
                Lines.square(b.x, b.y, radius * (0.42f + Mathf.absin(time, 14f, 0.08f)), 45f);
                for(int i = 0; i < 3; i++){
                    float angle = time * 1.1f + i * 120f;
                    float x = b.x + Angles.trnsx(angle, radius * 0.8f), y = b.y + Angles.trnsy(angle, radius * 0.8f);
                    Lines.line(b.x, b.y, x, y);
                    Fill.square(x, y, 1.1f, 45f);
                }
            }else if(kind == 1){
                for(int i = -2; i <= 2; i++){
                    float offset = i * radius * 0.4f;
                    Draw.alpha(active * 0.28f);
                    Lines.line(b.x - radius, b.y + offset, b.x + radius, b.y + offset);
                    Lines.line(b.x + offset, b.y - radius, b.x + offset, b.y + radius);
                }
                Draw.alpha(active);
                float scan = ((time / 80f) % 1f * 2f - 1f) * radius;
                Lines.line(b.x - radius, b.y + scan, b.x + radius, b.y + scan);
                for(int i = -2; i <= 2; i++) Fill.square(b.x + i * radius * 0.4f, b.y + scan, 0.9f);
            }else if(kind == 2){
                for(int i = 0; i < 3; i++){
                    float angle = time * 1.7f + i * 120f;
                    Lines.arc(b.x, b.y, radius * (0.5f + i * 0.18f), 0.19f, angle);
                    Fill.circle(b.x + Angles.trnsx(angle, radius * 0.75f), b.y + Angles.trnsy(angle, radius * 0.75f), 1.1f);
                }
                Draw.color(color, Color.white, Mathf.absin(time, 7f, 0.6f));
                Fill.poly(b.x, b.y, 4, 2.2f + Mathf.absin(time, 6f, 0.8f), time * 0.9f);
            }else{
                for(int i = 0; i < 3; i++){
                    float phase = (time / 110f + i / 3f) % 1f;
                    Draw.alpha(active * phase * 0.7f);
                    Lines.poly(b.x, b.y, 6, radius * (1.15f - phase * 0.8f), 30f + time * 0.25f);
                }
                Draw.alpha(active);
                Draw.color(color, Color.white, build.progress * 0.65f);
                Lines.square(b.x, b.y, 2f + build.progress * 3f, 45f);
            }
            Draw.reset();
            Draw.z(z);
        }
        @Override public void drawLight(Building b){
            float active = activity(b);
            if(active > 0.001f) Drawf.light(b.x, b.y, b.block.size * 12f, color, active * 0.35f);
        }
    }
}
