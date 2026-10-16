package cryon.Type;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.Vars;
import mindustry.gen.WeatherState;
import mindustry.type.weather.ParticleWeather;

/** World-anchored crystal facets over a thin, slowly moving frost haze. */
public class IceCrystalWeather extends ParticleWeather {
    public Color crystalColor = Color.valueOf("a4dfffff");
    public Color glintColor = Color.valueOf("f1e8ffff");
    public float crystalSpacing = 72f;
    public float crystalSize = 2.8f;
    public float driftSpeed = 0.35f;
    public int maxCrystals = 600;

    public IceCrystalWeather(String name){
        super(name);
        drawParticles = false;
    }

    @Override
    public void drawOver(WeatherState state){
        super.drawOver(state);
        float opacity = state.opacity * Vars.renderer.weatherAlpha;
        if(opacity <= 0.001f || maxCrystals <= 0) return;

        float width = Core.camera.width, height = Core.camera.height;
        float left = Core.camera.position.x - width / 2f;
        float bottom = Core.camera.position.y - height / 2f;
        float spacing = Math.max(Math.max(16f, crystalSpacing),
                Mathf.sqrt(width * height * 2f / maxCrystals));
        float time = Time.time;
        int drawn = 0;

        for(int layer = 0; layer < 2; layer++){
            float speed = driftSpeed * (layer == 0 ? 0.55f : 1f);
            float dx = time * speed * 0.45f, dy = -time * speed;
            int minX = Mathf.floor((left - dx) / spacing) - 1;
            int minY = Mathf.floor((bottom - dy) / spacing) - 1;
            int maxX = Mathf.ceil((left + width - dx) / spacing) + 1;
            int maxY = Mathf.ceil((bottom + height - dy) / spacing) + 1;

            for(int x = minX; x <= maxX; x++){
                for(int y = minY; y <= maxY; y++){
                    long seed = x * 73856093L ^ y * 19349663L ^ layer * 83492791L;
                    float phase = Mathf.randomSeed(seed, 0f, 360f);
                    float px = (x + Mathf.randomSeed(seed + 1)) * spacing + dx;
                    float py = (y + Mathf.randomSeed(seed + 2)) * spacing + dy;
                    px += Mathf.sin(time + phase, 90f, 5f);
                    if(px < left - 12f || px > left + width + 12f
                            || py < bottom - 12f || py > bottom + height + 12f) continue;
                    if(drawn++ >= maxCrystals) break;

                    float size = crystalSize * Mathf.randomSeed(seed + 3, 0.65f, 1.3f)
                            * (layer == 0 ? 0.6f : 1f);
                    float angle = phase + time * Mathf.randomSeed(seed + 4, -0.12f, 0.12f);
                    float ax = Mathf.cosDeg(angle) * size, ay = Mathf.sinDeg(angle) * size;
                    float bx = -Mathf.sinDeg(angle) * size * 0.45f;
                    float by = Mathf.cosDeg(angle) * size * 0.45f;
                    float pulse = Math.max(0f, Mathf.sin(time + phase * 7f, 24f, 1f));
                    float alpha = opacity * (layer == 0 ? 0.2f : 0.38f);
                    Draw.color(crystalColor, glintColor, pulse * 0.5f);
                    Draw.alpha(alpha);
                    Fill.quad(px + ax, py + ay, px + bx, py + by,
                            px - ax, py - ay, px - bx, py - by);
                    Draw.color(glintColor, alpha * 0.8f);
                    Lines.stroke(0.35f);
                    Lines.line(px + ax, py + ay, px - ax, py - ay);

                    // Only a few facets catch the light at any given moment.
                    if(pulse > 0.94f && layer == 1){
                        float shine = (pulse - 0.94f) / 0.06f;
                        Draw.color(glintColor, opacity * shine * 0.7f);
                        Lines.stroke(0.5f);
                        Lines.line(px - size * 1.8f, py, px + size * 1.8f, py);
                        Lines.line(px, py - size * 1.8f, px, py + size * 1.8f);
                    }
                }
                if(drawn >= maxCrystals) break;
            }
            if(drawn >= maxCrystals) break;
        }
        Draw.reset();
        Lines.stroke(1f);
    }
}
