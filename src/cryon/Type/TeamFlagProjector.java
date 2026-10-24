package cryon.Type;

import arc.Core;
import arc.Events;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import cryon.Content.CryonItems;
import cryon.Features.ProjectorSalutes;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.game.EventType.DisposeEvent;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.graphics.*;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;

/** Sci-fi hologram team emblem projector. Glows in the dark. Cosmetic only. */
public class TeamFlagProjector extends Block{
    // 保持原类名和内容 ID,已放置的方块不会丢
    private final TextureRegion[] teamSymbols = new TextureRegion[256];

    public static float emblemSize = 64f;   // 图标大小
    public static float hoverHeight = 58f;  // 悬浮高度
    public static int strips = 28;          // 扫描条数量

    public TeamFlagProjector(String name){
        super(name);
        ProjectorSalutes.init();
        size = 2;
        health = 180;
        solid = true;
        destructible = true;
        update = true;
        hasPower = true;
        consumePower(1f);
        clipSize = 256f;
        alwaysUnlocked = false;
        emitLight = true;
        lightRadius = 90f;
        requirements(Category.effect, BuildVisibility.shown,
                ItemStack.with(CryonItems.aluminum, 25, Items.graphite, 10, Items.silicon, 15));
    }

    @Override public void load(){
        super.load();
        if(Vars.headless) return;
        for(Team team : Team.all){
            String name = "team-" + team.name;
            if(!Core.atlas.has(name)) continue;
            Pixmap mask = Core.atlas.getPixmap(name).crop();
            for(int y = 0; y < mask.height; y++){
                for(int x = 0; x < mask.width; x++){
                    mask.set(x, y, 0xffffff00 | (mask.get(x, y) & 0xff));
                }
            }
            Texture texture = new Texture(mask);
            mask.dispose();
            texture.setFilter(Texture.TextureFilter.linear);
            teamSymbols[team.id] = new TextureRegion(texture);
            Events.on(DisposeEvent.class, event -> texture.dispose());
        }
    }

    public class TeamFlagProjectorBuild extends Building{
        private final TextureRegion tmp = new TextureRegion();
        private float saluteTimer;

        @Override public void updateTile(){
            if(Vars.headless) return;
            saluteTimer += Time.delta;
            if(saluteTimer >= 15f){
                saluteTimer %= 15f;
                ProjectorSalutes.offer(this);
            }
        }


        public TextureRegion teamSymbol(){
            return teamSymbols[team.id];
        }

        /** 黑暗地图中,周围地面被队伍色照亮 */
        @Override public void drawLight(){
            if(!enabled || efficiency <= 0.001f) return;
            float pulse = 0.9f + 0.1f * Mathf.sin(Time.time / 18f);
            Drawf.light(x, y + hoverHeight * 0.6f, 130f * pulse, team.color, 0.28f * efficiency);
            Drawf.light(x, y, 40f, team.color, 0.34f * efficiency);
        }

        @Override public void draw(){
            super.draw();
            float z = Draw.z(), stroke = Lines.getStroke();
            try{
                drawBase();
                if(!enabled || efficiency <= 0.001f) return;
                Draw.z(Layer.light + 1f);
                Draw.blend(Blending.additive);
                drawHologram();
            }finally{
                Draw.blend();
                Draw.reset();
                Lines.stroke(stroke);
                Draw.z(z);
            }
        }

        private void drawBase(){
            Draw.z(Layer.blockOver + 0.1f);
            Draw.color(Pal.darkestGray);
            Fill.circle(x, y, 4.2f);
            Draw.color(team.color, enabled ? efficiency : 0f);
            Lines.stroke(0.8f);
            Lines.circle(x, y, 3.4f);
            Fill.circle(x, y, 1.4f);
        }

        private void drawHologram(){
            float t = Time.time;
            Color c = team.color;
            float cy = y + hoverHeight + Mathf.sin(t / 40f) * 2.5f;
            float half = emblemSize / 2f;

            float flick = Mathf.randomSeed((long)(t / 3f) + id, 0f, 1f) > 0.94f ? 0.6f : 1f;
            float base = (0.51f + 0.09f * Mathf.sin(t / 7f)) * flick * efficiency;

            // ---- 底座旋转能量环 ----
            Draw.color(c, 0.8f * base);
            Lines.stroke(1.1f);
            for(int i = 0; i < 3; i++){
                Lines.arc(x, y, 7f + i * 2.5f, 0.28f, t * (i % 2 == 0 ? 2f : -2.5f) + i * 120f);
            }
            Lines.stroke(0.6f);
            Draw.color(c, 0.4f * base);
            Lines.circle(x, y, 13f + Mathf.absin(t, 20f, 1.5f));

            // ---- 渐变光锥 ----
            float cLow = Tmp.c1.set(c).a(0.22f * base).toFloatBits();
            float cTop = Tmp.c2.set(c).a(0.02f * base).toFloatBits();
            float topW = half * 0.85f;
            Fill.quad(
                    x - 3f, y, cLow,
                    x + 3f, y, cLow,
                    x + topW, cy - half, cTop,
                    x - topW, cy - half, cTop
            );

            // ---- 向上飘的光粒子 ----
            Draw.color(c, 0.7f * base);
            for(int i = 0; i < 14; i++){
                float p = ((t * 0.6f + i * 37f) % 100f) / 100f;
                float ang = i * 53f + p * 40f;
                float r = Mathf.lerp(3f, topW, p) * (0.3f + (i % 5) / 5f);
                float px = x + Mathf.cosDeg(ang) * r;
                float py = Mathf.lerp(y, cy - half, p);
                Fill.square(px, py, 0.8f * (1f - p) + 0.3f, 45f);
            }

            // ---- 图标:横向扫描条 + 故障偏移 ----
            TextureRegion region = teamSymbol();
            if(region != null){
                float sweep = (t * 0.012f) % 1f;
                float glitchOn = Mathf.randomSeed((long)(t / 5f) + id * 7L, 0f, 1f) > 0.9f ? 1f : 0f;
                float h = emblemSize / strips;
                float vh = (region.v2 - region.v) / strips;

                for(int i = 0; i < strips; i++){
                    float f = i / (float)strips;
                    float dy = cy + half - (i + 0.5f) * h;
                    float off = glitchOn * Mathf.randomSeed((long)(t / 5f) * 31L + i, -4f, 4f);

                    tmp.set(region);
                    tmp.v = region.v + vh * i;
                    tmp.v2 = region.v + vh * (i + 1);

                    float line = (i % 2 == 0) ? 1f : 0.6f;
                    float near = Mathf.clamp(1f - Math.abs((1f - f) - sweep) * 7f);
                    float a = Math.min(0.85f, 0.4f * line + near * 0.3f) * base;

                    Draw.color(Tmp.c3.set(c).lerp(Color.white, near * 0.2f), a);
                    Draw.rect(tmp, x + off, dy, emblemSize, h + 0.15f);
                }

                // 底光
                Draw.color(c, 0.07f * base);
                Draw.rect(region, x, cy, emblemSize * 1.08f, emblemSize * 1.08f);

                // 扫描亮线
                float sy = cy - half + sweep * emblemSize;
                Draw.color(Color.white, 0.4f * base);
                Lines.stroke(1f);
                Lines.line(x - half * 0.9f, sy, x + half * 0.9f, sy);
                Draw.color(c, 0.2f * base);
                Lines.stroke(3f);
                Lines.line(x - half * 0.9f, sy, x + half * 0.9f, sy);
            }

            // ---- 图标外围:旋转环与刻度 ----
            float ringR = half * 1.05f;
            Draw.color(c, 0.65f * base);
            Lines.stroke(1f);
            Lines.arc(x, cy, ringR, 0.2f, t * 1.2f);
            Lines.arc(x, cy, ringR, 0.2f, t * 1.2f + 180f);
            Lines.stroke(0.6f);
            Draw.color(c, 0.35f * base);
            Lines.circle(x, cy, ringR + 4f);
            for(int i = 0; i < 24; i++){
                float ang = i * 15f - t * 0.5f;
                float len = i % 6 == 0 ? 4f : 1.8f;
                Lines.lineAngle(x + Mathf.cosDeg(ang) * (ringR + 4f), cy + Mathf.sinDeg(ang) * (ringR + 4f), ang, len);
            }

            // ---- 四角瞄准框 ----
            Draw.color(c, 0.75f * base);
            Lines.stroke(1.1f);
            float b = half + 8f, l = 7f;
            for(int sx = -1; sx <= 1; sx += 2){
                for(int sy2 = -1; sy2 <= 1; sy2 += 2){
                    float cx = x + sx * b, cyy = cy + sy2 * b;
                    Lines.line(cx, cyy, cx - sx * l, cyy);
                    Lines.line(cx, cyy, cx, cyy - sy2 * l);
                }
            }
        }
    }
}
