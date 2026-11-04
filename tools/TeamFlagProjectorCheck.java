import arc.Core;
import arc.Events;
import arc.Settings;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.mock.*;
import arc.util.ArcNativesLoader;
import arc.util.Time;
import cryon.Features.NormaTeam;
import cryon.Type.TeamFlagProjector;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.game.Team;
import mindustry.game.EventType.DisposeEvent;
import java.nio.*;

/** Checks owned masks and the original scan-strip rendering without a display. */
public class TeamFlagProjectorCheck{
    static void check(boolean value, String message){ if(!value) throw new AssertionError(message); }
    static class GL extends MockGL20{
        byte[] uploaded;
        int uploads, handle = 1;
        @Override public int glGenTexture(){ return handle++; }
        @Override public void glTexImage2D(int target,int level,int internal,int width,int height,int border,int format,int type,Buffer data){
            uploads++;
            if(data instanceof ByteBuffer){
                ByteBuffer bytes = ((ByteBuffer)data).duplicate();
                uploaded = new byte[bytes.remaining()]; bytes.get(uploaded);
            }
        }
    }
    static class Capture extends Batch{
        Texture symbol;
        int strips;
        float nextV;
        @Override protected void draw(Texture texture,float[] vertices,int offset,int count){}
        @Override protected void draw(TextureRegion region,float x,float y,float ox,float oy,float width,float height,float rotation){
            if(region.texture != symbol || height > 3f) return;
            check(Math.abs(region.v - nextV) < 0.0001f, "Discontinuous scan-strip UVs");
            check(region.u >= 0 && region.u2 <= 1 && region.v2 <= 1.0001f, "Scan strip escaped its mask");
            nextV = region.v2;
            strips++;
        }
        @Override protected void flush(){}
    }
    public static void main(String[] args)throws Exception{
        ArcNativesLoader.load();
        Core.app = new MockApplication(); Core.graphics = new MockGraphics();
        Core.files = new MockFiles(); Core.settings = new Settings();
        GL gl = new GL(); Core.gl = Core.gl20 = gl;
        Vars.headless = true; Vars.content = new mindustry.core.ContentLoader();
        Items.graphite = new mindustry.type.Item("graphite"); Items.silicon = new mindustry.type.Item("silicon");
        cryon.Content.CryonItems.aluminum = new mindustry.type.Item("aluminum");
        NormaTeam.init(); cryon.Features.AllianceTeam.init(); cryon.Features.PioneerTeam.init();
        TeamFlagProjector projector = new TeamFlagProjector("team-flag-projector-check");
        Core.atlas = new TextureAtlas();
        Pixmap page = new Pixmap(16, 16); page.fill(0xff0000ff);
        // Non-white icon, transparent hole, and distinctive alpha inside a larger atlas.
        page.set(4, 5, 0x12345680); page.set(5, 5, 0xabcdef00);
        Texture source = new Texture(page);
        Core.atlas.getPixmaps().put(source, page);
        Core.atlas.addRegion("white", source, 0, 0, 1, 1);
        for(Team team : Team.baseTeams){
            if(cryon.Features.TeamEmblems.bundledPath(team) == null) Core.atlas.addRegion("team-" + team.name, source, 4, 5, 2, 3);
        }
        Vars.headless = false;
        Core.atlas.addRegion("error", source, 0, 0, 1, 1);
        Core.atlas.setErrorRegion("error");
        Core.atlas.addRegion("team-flag-projector-check", source, 0, 0, 2, 2);
        int beforeLoading = gl.uploads;
        projector.load();
        check(gl.uploads == beforeLoading, "Block.load() uploaded a GL texture");
        projector.loadIcon();
        mindustry.graphics.MultiPacker packer = new mindustry.graphics.MultiPacker(){
            @Override public PixmapRegion get(String name){
                return Core.atlas.has(name) ? new PixmapRegion(page, 4, 5, 2, 3) : new PixmapRegion(page, 0, 0, 1, 1);
            }
        };
        int before = gl.uploads;
        Thread worker = new Thread(() -> projector.createIcons(packer));
        worker.start(); worker.join();
        check(gl.uploads == before, "Packing worker uploaded a GL texture");
        projector.loadTeamSymbols();
        var build = projector.new TeamFlagProjectorBuild();
        for(Team team : Team.baseTeams){
            build.team = team;
            TextureRegion symbol = build.teamSymbol();
            check(symbol != null && symbol.texture != source, "Projection still depends on the UI atlas: " + team);
            if(cryon.Features.TeamEmblems.bundledPath(team) != null) check(symbol.width == 62 && symbol.height == 62, "Bundled faction mask missing: " + team);
            else check(symbol.width == 2 && symbol.height == 3 && symbol.u == 0 && symbol.v == 0,
                    "Wrong atlas crop: " + team);
        }
        // Load a late custom icon so the last GPU upload is inspectable.
        Team late = Team.get(172); late.name = "projector-check";
        build.team = late;
        check(build.teamSymbol() == null, "Missing emblem used the atlas error sprite");
        Core.atlas.addRegion("team-" + late.name, source, 4, 5, 2, 3);
        projector.createIcons(packer);
        build.team = late; TextureRegion mask = build.teamSymbol();
        check((gl.uploaded[0] & 255) == 255 && (gl.uploaded[1] & 255) == 255
                && (gl.uploaded[2] & 255) == 255 && (gl.uploaded[3] & 255) == 128
                && (gl.uploaded[7] & 255) == 0, "Mask lost white RGB or original alpha");
        int uploads = gl.uploads;
        projector.loadTeamSymbols();
        check(gl.uploads == uploads, "Repeated loading leaks textures");
        // Simulate font/UI repacking: move the source UVs and dispose its old texture.
        Core.atlas.find("team-" + late.name).set(0, 0, 1, 1);
        source.dispose();
        check(build.teamSymbol() == mask && !mask.texture.isDisposed(), "UI repacking broke the projector");
        Capture capture = new Capture(); Core.batch = capture;
        var draw = build.getClass().getDeclaredMethod("drawHologram"); draw.setAccessible(true);
        build.efficiency = 1f; Time.time = 83f;
        for(Team team : new Team[]{Team.sharded, Team.green, NormaTeam.team, cryon.Features.AllianceTeam.team, cryon.Features.PioneerTeam.team}){
            build.team = team; capture.symbol = build.teamSymbol().texture;
            capture.strips = 0; capture.nextV = 0;
            draw.invoke(build);
            check(capture.strips == TeamFlagProjector.strips && Math.abs(capture.nextV - 1f) < 0.0001f,
                    "Original scan-strip projection missing: " + team);
        }
        Events.fire(new DisposeEvent());
        check(mask.texture.isDisposed(), "Projection texture leaked on shutdown");
        page.dispose();
        System.out.println("PASS: vanilla, Norma, Alliance and Pioneer masks, alpha crop, lazy loading, UI repack isolation, scan strips and texture disposal");
    }
}
