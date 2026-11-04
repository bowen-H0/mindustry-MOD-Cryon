package cryon.Features;

import arc.files.Fi;
import arc.freetype.FreeTypeFontGenerator;
import arc.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import arc.graphics.Color;
import arc.graphics.Texture;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.GlyphLayout;
import arc.scene.ui.layout.Scl;
import arc.util.Log;
import mindustry.Vars;
import mindustry.core.UI;
import mindustry.ui.Fonts;

import java.io.IOException;
import java.io.InputStream;

/** Adds the single CJK glyph missing from Mindustry's bundled fonts. */
public final class PorphyraFont{
    private static final char og = '鿫';
    private static Font normalFallback, outlineFallback;

    private PorphyraFont(){}

    public static void install(){
        if(Vars.headless || Fonts.def == null || Fonts.outline == null || UI.packer == null) return;
        if(normalFallback != null) return;
        Fi source = Fi.tempFile("cryon-og-font");
        try(InputStream stream = PorphyraFont.class.getResourceAsStream("/fonts/cryon-og.ttf")){
            if(stream == null) throw new IllegalStateException("Bundled 鿫 font resource missing");
            source.write(stream, false);
        }catch(IOException error){
            throw new IllegalStateException("Could not read bundled 鿫 font", error);
        }
        try{
            normalFallback = generate(source, false);
            outlineFallback = generate(source, true);
        }finally{
            source.delete();
        }
        Fonts.def.addFallback(normalFallback);
        Fonts.outline.addFallback(outlineFallback);

        Font.Glyph normal = Fonts.def.getData().getGlyph(og);
        Font.Glyph outlined = Fonts.outline.getData().getGlyph(og);
        if(normal == null || outlined == null || normal.id != og || outlined.id != og){
            throw new IllegalStateException("鿫 glyph was not installed in both Mindustry fonts");
        }
        GlyphLayout layout = new GlyphLayout();
        layout.setText(Fonts.def, String.valueOf(og));
        if(layout.width <= 0f) throw new IllegalStateException("鿫 glyph has zero display width");
        Log.info("[Cryon] Installed 鿫 glyph from bundled Noto CJK subset");
    }

    private static Font generate(Fi source, boolean outline){
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(source);
        try{
            FreeTypeFontParameter parameter = new FreeTypeFontParameter();
            // FreeType uses x and H to measure the font before rendering 鿫.
            parameter.characters = "鿫xH ";
            parameter.size = Math.max(1, (int)Scl.scl(18f));
            parameter.packer = UI.packer;
            parameter.incremental = false;
            parameter.minFilter = Texture.TextureFilter.linear;
            parameter.magFilter = Texture.TextureFilter.linear;
            if(outline){
                parameter.borderWidth = Scl.scl(2f);
                parameter.borderColor = Color.darkGray;
                parameter.spaceX = -(int)parameter.borderWidth;
            }else{
                parameter.shadowColor = Color.darkGray;
                parameter.shadowOffsetY = 2;
            }
            Font font = generator.generateFont(parameter);
            font.setOwnsTexture(false); // Its glyph shares Mindustry's UI.packer texture.
            return font;
        }finally{
            generator.dispose();
        }
    }
}
