import arc.*;
import arc.files.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.g2d.TextureAtlas.TextureAtlasData;
import arc.mock.*;
import arc.util.*;
import java.nio.file.Files;
import mindustry.*;
import mindustry.content.Blocks;
import mindustry.graphics.MultiPacker;
import mindustry.graphics.MultiPacker.PageType;
import mindustry.net.Net;
import mindustry.world.blocks.environment.ShallowLiquid;

/** Real PNGs, native icon generation and atlas packing; only GPU uploads are mocked. */
public class UnderwaterTextureCheck{
    static final String[] seabeds = {"pillow-basalt", "pressure-slate", "obsidian", "limestone", "black-silt",
        "white-clay", "frozen-silt", "silica-crust", "ice-crystal-floor", "titanium-bed", "nickel-bed", "metal-nodules"};
    static void check(boolean value, String message){ if(!value) throw new AssertionError(message); }
    static void addSprite(String name, Pixmap image){
        Texture texture = new Texture(image);
        Core.atlas.getPixmaps().put(texture, image);
        Core.atlas.addRegion(name, texture, 0, 0, image.width, image.height);
    }
    public static void main(String[] args) throws Exception{
        ArcNativesLoader.load();
        Core.app = new MockApplication(); Core.files = new MockFiles(); Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Core.gl = Core.gl20 = new MockGL20(){
            private int nextTexture = 1;
            @Override public int glGenTexture(){ return nextTexture++; }
        };
        Fi data = new Fi(Files.createTempDirectory("cryon-underwater-textures-").toFile());
        Core.settings = new Settings(); Core.settings.setDataDirectory(data);
        Core.bundle = I18NBundle.createEmptyBundle();
        Fi jar = new Fi(args.length == 0 ? "build/libs/mindustry-MOD-JavaDesktop.jar" : args[0]);
        jar.copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlanuch.jar"));
        Vars.headless = true; Vars.loadLocales = false; Vars.net = new Net(null);
        Vars.platform = new mindustry.core.Platform(){};
        mindustry.core.Version.init(); Vars.init();
        Vars.content.createBaseContent(); Vars.content.createModContent();
        check(!Vars.mods.locateMod("cryon").hasContentErrors(), "Packaged content errors");

        // Match desktop startup: Mods generates textures BEFORE ContentLoader.init().
        Core.atlas = new TextureAtlas();
        TextureAtlasData vanilla = new TextureAtlasData(Core.files.internal("sprites/sprites.aatls"), Core.files.internal("sprites"), false);
        var vanillaNames = arc.struct.Seq.with("error", "white", "edge");
        if(Blocks.water.variants == 0) vanillaNames.add(Blocks.water.name);
        else for(int i = 1; i <= Blocks.water.variants; i++) vanillaNames.add(Blocks.water.name + i);
        for(var page : vanilla.getPages()){
            var regions = vanilla.getRegions().select(r -> r.page == page && vanillaNames.contains(r.name));
            if(regions.isEmpty()) continue;
            Pixmap image = new Pixmap(page.textureFile);
            for(var region : regions){
                check(!region.rotate, "Rotated vanilla source region: " + region.name);
                addSprite(region.name, new PixmapRegion(image, region.left, region.top, region.width, region.height).crop());
            }
            image.dispose();
        }
        for(String name : vanillaNames) check(Core.atlas.has(name), "Missing vanilla source region: " + name);
        Core.atlas.setErrorRegion("error");
        Fi sprites = new ZipFi(jar).child("sprites").child("blocks").child("environment").child("cryon-depths");
        for(Fi file : sprites.list()) if(file.extension().equals("png")){
            addSprite("cryon-" + file.nameWithoutExtension(), new Pixmap(file.readBytes()));
        }
        Vars.maxTextureSize = 1024;
        MultiPacker packer = new MultiPacker();
        for(var region : Core.atlas.getRegions()) packer.add(PageType.environment, region.name, Core.atlas.getPixmap(region));
        Pixmap preview = new Pixmap(6 * 32, seabeds.length * 32);
        int count = 0;
        for(int row = 0; row < seabeds.length; row++){
            String name = seabeds[row];
            ShallowLiquid wet = (ShallowLiquid)Vars.content.block("cryon-abyss-flooded-" + name);
            var dry = Vars.content.block("cryon-abyss-" + name).asFloor();
            check(wet.id < dry.id, "Fixture no longer exercises early Java / late HJSON registration");
            check(dry.variantRegions == null, "Fixture loaded source floor before icon generation");
            check(Core.atlas.has(dry.name + "1"), "Source PNG absent from fixture atlas: " + dry.name + "1");
            // Same per-content calls as Mods.loadAsync, with cold dependencies.
            wet.load(); wet.loadIcon(); wet.createIcons(packer);
            for(int variant = 1; variant <= dry.variants; variant++){
                String regionName = wet.name + variant;
                PixmapRegion actual = packer.getPacked(regionName);
                check(actual != null, "Missing generated underwater texture before content init: " + regionName);
                PixmapRegion base = Core.atlas.getPixmap(dry.name + variant);
                PixmapRegion water = Core.atlas.getPixmap(Blocks.water.region);
                check(actual.width == base.width && actual.height == base.height && actual.width == 32,
                    "Wrong source texture dimensions: " + regionName);
                for(int x = 0; x < actual.width; x++) for(int y = 0; y < actual.height; y++){
                    int expected = Pixmap.blend((water.getRaw(x, y) & 0xffffff00) | (int)(wet.liquidOpacity * 255), base.getRaw(x, y));
                    check(actual.getRaw(x, y) == expected, "Underwater texture has incorrect source pixels: " + regionName);
                }
                preview.draw(base, (variant - 1) * 32, row * 32);
                preview.draw(actual, (variant + 2) * 32, row * 32);
                count++;
            }
        }
        TextureAtlas source = Core.atlas;
        Core.atlas = packer.flush(Texture.TextureFilter.nearest, new TextureAtlas());
        Core.atlas.setErrorRegion("error");
        Vars.content.init();
        for(String name : seabeds){
            var wet = Vars.content.block("cryon-abyss-flooded-" + name).asFloor();
            wet.loadIcon(); wet.load();
            check(wet.fullIcon.found() && wet.uiIcon.found() && wet.region.found(), "Missing loaded underwater icon: " + name);
            check(wet.variantRegions.length == 3, "Incomplete loaded variants: " + name);
            for(var region : wet.variantRegions) check(region.found(), "Missing loaded underwater variant: " + name);
        }
        check(count == 36, "Expected 36 generated seabed variants, got " + count);
        Fi image = new Fi("/tmp/cryon-underwater-textures.png");
        PixmapIO.writePng(image, preview);
        preview.dispose(); source.dispose(); Core.atlas.dispose(); packer.dispose();
        System.out.println("UNDERWATER_TEXTURE_PASS: 36 exact-pixel seabed/water blends generated before content init; all floor regions, variants and editor icons resolve after atlas packing. Preview: " + image);
    }
}
