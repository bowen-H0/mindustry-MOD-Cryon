import arc.Core;
import arc.graphics.Pixmap;
import arc.graphics.Texture;
import arc.graphics.g2d.*;
import arc.mock.*;
import arc.util.ArcNativesLoader;
import mindustry.Vars;
import mindustry.core.UI;
import mindustry.ui.Fonts;
import cryon.Features.NormaTeam;

/** Exercises icon registration across real packer page growth without a display. */
public class NormaIconCheck{
    static void check(boolean valid, String message){
        if(!valid) throw new AssertionError(message);
    }
    static void update(){
        UI.packer.updateTextureRegions(Fonts.def.getRegions(), Texture.TextureFilter.linear, Texture.TextureFilter.linear, false);
        UI.packer.updateTextureRegions(Fonts.outline.getRegions(), Texture.TextureFilter.linear, Texture.TextureFilter.linear, false);
    }
    static Font font(){
        Font.FontData data = new Font.FontData();
        data.lineHeight = 18;
        Font font = new Font(data, new TextureRegion(UI.packer.getPages().first().getTexture()), false);
        font.setOwnsTexture(false);
        return font;
    }
    public static void main(String[] args){
        ArcNativesLoader.load();
        Core.app = new MockApplication();
        Core.graphics = new MockGraphics();
        Core.gl = Core.gl20 = new MockGL20();
        Vars.headless = false;
        Core.atlas = new TextureAtlas();
        UI.packer = new PixmapPacker(128, 128, 2, true);
        Pixmap filler = new Pixmap(100, 100);
        UI.packer.pack("existing-ui", filler);
        UI.packer.updatePageTextures(Texture.TextureFilter.linear, Texture.TextureFilter.linear, false);
        Fonts.def = font(); Fonts.outline = font();
        // An outline-only glyph must not be overwritten by the custom emoji.
        Font.Glyph occupied = new Font.Glyph(); occupied.id = 0xe800;
        Fonts.outline.data.setGlyph(0xe800, occupied);
        NormaTeam.init(); NormaTeam.loadIcon();
        cryon.Features.AllianceTeam.init(); cryon.Features.AllianceTeam.loadIcon();
        cryon.Features.PioneerTeam.init(); cryon.Features.PioneerTeam.loadIcon();
        int allianceCode = Fonts.getUnicode("team-alliance");
        check(allianceCode != 0 && allianceCode != Fonts.getUnicode("team-norma"), "Alliance emoji missing or overwrites Norma");
        int pioneerCode = Fonts.getUnicode("team-pioneer");
        check(pioneerCode != 0 && pioneerCode != allianceCode && pioneerCode != Fonts.getUnicode("team-norma"),
            "Pioneer emoji missing or overwrites another faction");
        int code = Fonts.getUnicode("team-norma");
        check(code != 0 && code != 0xe800, "Emoji overwrote an existing glyph");
        TextureRegion emblem = Core.atlas.find("team-norma");
        check(UI.packer.getPages().size > 1, "Test did not force a new icon page");
        Texture originalUi = Fonts.def.getRegion(0).texture;
        int pages = UI.packer.getPages().size;
        for(int i = 0; i < 5; i++) UI.packer.pack("later-cjk-" + i, filler);
        update();
        check(UI.packer.getPages().size > pages, "Test did not grow the font atlas");
        for(Font font : new Font[]{Fonts.def, Fonts.outline}){
            Font.Glyph glyph = font.data.getGlyph((char)code);
            check(font.getRegion(glyph.page).texture == emblem.texture, "Emoji samples the wrong texture after font page growth");
            check(glyph.u == emblem.u && glyph.v == emblem.v2 && glyph.u2 == emblem.u2 && glyph.v2 == emblem.v, "Emoji UVs changed");
            check(font.getRegion(0).texture == originalUi, "Existing UI texture was replaced");
        }
        TextureRegion alliance = Core.atlas.find("team-alliance");
        for(Font font : new Font[]{Fonts.def, Fonts.outline}){
            Font.Glyph glyph = font.data.getGlyph((char)allianceCode);
            check(font.getRegion(glyph.page).texture == alliance.texture, "Alliance emoji samples wrong texture after page growth");
            check(glyph.u == alliance.u && glyph.v == alliance.v2 && glyph.u2 == alliance.u2 && glyph.v2 == alliance.v, "Alliance emoji UV mismatch");
        }
        TextureRegion pioneer = Core.atlas.find("team-pioneer");
        check(pioneer.found() && pioneer.width == 62 && pioneer.height == 62, "Missing bundled Pioneer emblem");
        for(Font font : new Font[]{Fonts.def, Fonts.outline}){
            Font.Glyph glyph = font.data.getGlyph((char)pioneerCode);
            check(font.getRegion(glyph.page).texture == pioneer.texture, "Pioneer emoji samples wrong texture after page growth");
            check(glyph.u == pioneer.u && glyph.v == pioneer.v2 && glyph.u2 == pioneer.u2 && glyph.v2 == pioneer.v, "Pioneer emoji UV mismatch");
        }
        int regions = Fonts.def.getRegions().size;
        NormaTeam.loadIcon();
        cryon.Features.AllianceTeam.loadIcon();
        cryon.Features.PioneerTeam.loadIcon();
        check(Fonts.getUnicode("team-norma") == code && Fonts.def.getRegions().size == regions, "Repeated registration leaked pages or codes");
        check(Fonts.getUnicode("team-pioneer") == pioneerCode, "Repeated Pioneer registration leaked a code");
        check(Fonts.outline.data.getGlyph((char)0xe800) == occupied, "Outline glyph was overwritten");
        check(NormaTeam.team.id == 169 && NormaTeam.team != mindustry.game.Team.green, "Norma is not independent");
        check(mindustry.game.Team.green.name.equals("green") && mindustry.game.Team.green.color.equals(arc.graphics.Color.valueOf("54d67d")), "Vanilla green changed");
        int matches = 0;
        for(var candidate : mindustry.game.Team.baseTeams) if(candidate == NormaTeam.team) matches++;
        NormaTeam.init();
        check(matches == 1, "Norma missing or duplicated in editor teams");
        var faction = cryon.Features.PioneerTeam.team;
        check(faction.id == 171 && faction.name.equals("pioneer") && faction.color.equals(arc.graphics.Color.valueOf("f28c28")),
            "Pioneer identity or orange color changed");
        matches = 0;
        cryon.Features.PioneerTeam.init();
        for(var candidate : mindustry.game.Team.baseTeams) if(candidate == faction) matches++;
        check(matches == 1 && faction.hasPalette && faction.palette[0].equals(faction.color), "Pioneer editor team or palette missing/duplicated");
        filler.dispose(); UI.packer.forceDispose();
        System.out.println("PASS: Norma, Alliance and Pioneer emblems survive shared font page growth; occupied glyphs, repeated registration, orange palette and independent editor teams verified");
    }
}
