package cryon.Fx;

import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.util.Time;
import cryon.Type.TeamFlagProjector.TeamFlagProjectorBuild;
import mindustry.entities.Effect;
import mindustry.graphics.Layer;

/** Native team emblem projection, rendered by FX with ordinary alpha blending. */
public class TeamEmblemFx{
    // Six-tick emission interval and complementary fades keep the projection continuous.
    public static final Effect projection = new Effect(12f, 256f, e -> {
        if(!(e.data instanceof TeamFlagProjectorBuild)) return;
        TeamFlagProjectorBuild build = (TeamFlagProjectorBuild)e.data;
        if(!build.isValid()) return;
        TextureRegion symbol = build.teamSymbol();
        if(symbol == null) return;

        float fade = Math.min(e.fin() * 2f, e.fout() * 2f);
        float phase = Time.time * 0.045f + build.id * 0.73f;
        float pulse = 0.9f + 0.1f * Mathf.sin(phase);
        float hoverY = build.y + 66f + Mathf.sin(phase * 0.7f) * 2.5f;
        float scale = 64f / Math.max(symbol.width, symbol.height);
        float width = symbol.width * scale, height = symbol.height * scale;
        float stroke = Lines.getStroke();
        try{
            Draw.color(build.team.color, 0.035f * fade);
            Fill.tri(build.x, build.y + 2f,
                build.x - width * 0.35f, hoverY - height * 0.25f,
                build.x + width * 0.35f, hoverY - height * 0.25f);

            // Soft alpha silhouettes surround a clear team-colored emblem.
            Draw.color(build.team.color, 0.065f * fade * pulse);
            Draw.rect(symbol, build.x, hoverY, width + 3f, height + 3f);
            Draw.color(build.team.color, 0.9f * fade * pulse);
            Draw.rect(symbol, build.x, hoverY, width, height);

            // Rising projection motes and expanding emitter pulses belong to the FX lifecycle.
            for(int i = 0; i < 5; i++){
                float progress = (Time.time / 75f + i / 5f + build.id * 0.013f) % 1f;
                float spread = Mathf.sin(i * 2.4f + build.id) * 9f;
                Draw.color(build.team.color, Mathf.sin(progress * Mathf.pi) * 0.5f * fade);
                Fill.circle(build.x + spread * progress,
                    build.y + 5f + progress * (hoverY - build.y - 8f), 0.7f);
            }
            float ring = (Time.time / 60f + build.id * 0.017f) % 1f;
            Draw.color(build.team.color, (1f - ring) * 0.4f * fade);
            Lines.stroke(0.7f);
            Lines.circle(build.x, build.y, 3f + ring * 6f);
        }finally{
            Draw.reset();
            Lines.stroke(stroke);
        }
    }).layer(Layer.flyingUnit + 1f);
}
