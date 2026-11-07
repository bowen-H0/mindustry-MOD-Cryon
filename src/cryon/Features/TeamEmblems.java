package cryon.Features;

import arc.Core;
import arc.graphics.Pixmap;
import arc.graphics.Texture;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.TextureRegion;
import arc.math.geom.Rect;
import arc.struct.ObjectMap;
import arc.util.Log;
import mindustry.Vars;
import mindustry.core.UI;
import mindustry.game.Team;
import mindustry.ui.Fonts;

/** Shared font-packer registration for Cryon's bundled faction emblems. */
public final class TeamEmblems{
    private static final ObjectMap<Team, TextureRegion> emblems = new ObjectMap<>();

    private TeamEmblems(){}

    public static String bundledPath(Team team){
        return team == NormaTeam.team || team == AllianceTeam.team || team == PioneerTeam.team || team == DisasterFactions.nano
                ? "/sprites/team-" + team.name + ".png" : null;
    }

    public static void loadIcon(Team team){
        if(Vars.headless || Core.atlas == null || UI.packer == null
                || Fonts.def == null || Fonts.outline == null) return;
        String name = "team-" + team.name;
        String packName = "cryon-" + team.name + "-emoji";
        TextureRegion region = emblems.get(team);
        if(region == null){
            try(java.io.InputStream stream = TeamEmblems.class.getResourceAsStream(bundledPath(team))){
                if(stream == null) throw new java.io.IOException("Missing bundled " + team.name + " PNG");
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int count;
                while((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
                Pixmap pixmap = new Pixmap(bytes.toByteArray());
                try{
                    // Font pages belong to this packer. An unrelated texture appended to a
                    // font's regions is overwritten when incremental CJK glyphs add pages.
                    Rect rect = UI.packer.pack(packName, pixmap);
                    UI.packer.updateTextureRegions(Fonts.def.getRegions(),
                            Texture.TextureFilter.linear, Texture.TextureFilter.linear, false);
                    UI.packer.updateTextureRegions(Fonts.outline.getRegions(),
                            Texture.TextureFilter.linear, Texture.TextureFilter.linear, false);
                    Texture texture = UI.packer.getPage(packName).getTexture();
                    region = new TextureRegion(texture, (int)rect.x, (int)rect.y,
                            (int)rect.width, (int)rect.height);
                    emblems.put(team, region);
                }finally{
                    pixmap.dispose();
                }
            }catch(java.io.IOException error){
                Log.err("[Cryon] Could not load " + team.name + " UI emblem", error);
                return;
            }
        }
        Core.atlas.addRegion(name, region);
        int code = Fonts.getUnicode(name);
        if(code == 0){
            code = 0xe800;
            while(code <= 0xf8ff && (Fonts.def.getData().hasGlyph((char)code)
                    || Fonts.outline.getData().hasGlyph((char)code)
                    || Fonts.unicodeToName(code) != null)) code++;
            if(code > 0xf8ff){
                Log.err("[Cryon] No free private-use character for " + team.name + " emblem");
                return;
            }
        }
        Fonts.registerIcon(name, name, code, region);
        Font.Glyph registered = Fonts.def.getData().getGlyph((char)code);
        bindIconPage(Fonts.def, code, registered, region);
        bindIconPage(Fonts.outline, code, registered, region);
        team.emoji = Fonts.getUnicodeStr(name);
    }

    private static void bindIconPage(Font font, int code, Font.Glyph source, TextureRegion region){
        int page = -1;
        for(int i = 0; i < font.getRegions().size; i++){
            if(font.getRegion(i).texture == region.texture){ page = i; break; }
        }
        if(page < 0) throw new IllegalStateException("Team emblem is outside the shared font packer");
        // Fonts may store the same texture at different page indices; never share this glyph.
        Font.Glyph glyph = new Font.Glyph();
        glyph.id = code;
        glyph.width = source.width;
        glyph.height = source.height;
        glyph.xoffset = source.xoffset;
        glyph.yoffset = source.yoffset;
        glyph.xadvance = source.xadvance;
        glyph.fixedWidth = source.fixedWidth;
        glyph.u = source.u;
        glyph.v = source.v;
        glyph.u2 = source.u2;
        glyph.v2 = source.v2;
        glyph.page = page;
        font.getData().setGlyph(code, glyph);
    }

}
