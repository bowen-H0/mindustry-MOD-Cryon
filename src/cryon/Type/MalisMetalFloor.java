package cryon.Type;

import arc.graphics.Color;
import arc.graphics.Pixmap;
import arc.graphics.g2d.PixmapRegion;
import mindustry.game.Team;
import mindustry.graphics.MultiPacker;
import mindustry.graphics.MultiPacker.PageType;
import mindustry.world.blocks.environment.Floor;

/** Vanilla metal tiles with a separate Malis accent palette and intact autotile masks. */
public class MalisMetalFloor extends Floor{
    public final Floor source;

    public MalisMetalFloor(Floor source){
        super("malis-" + source.name, 0);
        this.source = source;
        autotile = source.autotile;
        autotileVariants = source.autotileVariants;
        autotileMidVariants = source.autotileMidVariants;
        drawEdgeOut = source.drawEdgeOut;
        drawEdgeIn = source.drawEdgeIn;
        emitLight = source.emitLight;
        lightRadius = source.lightRadius;
        lightColor = Team.malis.color.cpy().a(source.lightColor.a);
        mapColor.set(Team.malis.color).mul(0.4f);
    }

    @Override public void createIcons(MultiPacker packer){
        String base = source.name;
        // Vanilla atlases contain the 47 already-split cells, not the source autotile sheet.
        for(int i = 0; i < 47; i++) copy(packer, base + "-" + i, name + "-" + i);
        if(autotileVariants > 1){
            for(int variant = 1; variant <= autotileVariants; variant++){
                for(int i = 0; i < 47; i++){
                    String suffix = "-" + variant + "-" + i;
                    copy(packer, base + suffix, name + suffix);
                }
            }
        }
        for(int i = 2; i <= autotileMidVariants; i++) copy(packer, base + "-mid-" + i, name + "-mid-" + i);
        String icon = packer.has(base) ? base : base + (autotileVariants > 1 ? "-1-13" : "-13");
        copy(packer, icon, name);
        // Refresh the worker atlas references after registering the generated regions.
        load();
        loadIcon();
        super.createIcons(packer);
    }

    private void copy(MultiPacker packer, String from, String to){
        // A supplied custom sprite always takes precedence over generated palette variants.
        if(packer.has(to)) return;
        PixmapRegion original = packer.get(from);
        if(original == null || original == packer.get("error")) return;
        Pixmap result = original.crop();
        Color pixel = new Color();
        float[] hsv = new float[3], crux = new float[3], malis = new float[3];
        Team.crux.color.toHsv(crux);
        Team.malis.color.toHsv(malis);
        for(int y = 0; y < result.height; y++){
            for(int x = 0; x < result.width; x++){
                int rgba = result.get(x, y);
                int red = rgba >>> 24, green = rgba >>> 16 & 255, blue = rgba >>> 8 & 255;
                // Preserve neutral metal, seams, highlights and transparent pixels exactly.
                if((rgba & 255) == 0 || red <= green + 8 || red <= blue + 8) continue;
                pixel.set(rgba).toHsv(hsv);
                float alpha = pixel.a;
                pixel.fromHsv(malis[0], Math.min(1f, hsv[1] * malis[1] / crux[1]),
                    Math.min(1f, hsv[2] * malis[2] / crux[2]));
                pixel.a = alpha;
                result.set(x, y, pixel.rgba());
            }
        }
        packer.add(PageType.environment, to, result);
        result.dispose();
    }
}
