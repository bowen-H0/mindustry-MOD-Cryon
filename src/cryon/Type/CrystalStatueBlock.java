package cryon.Type;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.io.Reads;
import arc.util.io.Writes;
import cryon.Content.PlanetEnvironmentStatuses;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Unit;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.meta.BuildVisibility;
import mindustry.world.meta.Env;

/** A persistent, destructible crystal remnant; no unit entity is retained. */
public class CrystalStatueBlock extends Block{
    public static final CrystalStatueBlock[] statues = new CrystalStatueBlock[5];
    private static final Color crystal = Color.valueOf("b190dc");
    private static final Color pale = Color.valueOf("e9daff");

    public static void loadContent(){
        for(int i = 0; i < statues.length; i++)
            statues[i] = new CrystalStatueBlock(i == 0 ? "crystal-statue" : "crystal-statue-" + (i + 1), i + 1);
    }

    public CrystalStatueBlock(String name, int footprint){
        super(name);
        size = footprint;
        health = 250 * footprint * footprint;
        solid = true;
        destructible = true;
        breakable = true;
        rebuildable = false;
        update = false;
        canOverdrive = false;
        envEnabled = Env.any;
        envDisabled = Env.none;
        clipSize = 320f;
        hideDatabase = true;
        requirements(Category.effect, BuildVisibility.hidden, ItemStack.empty);
        buildType = CrystalStatueBuild::new;
        config(String.class, (CrystalStatueBuild build, String value) -> build.setSource(value));
    }

    @Override public void load(){
        super.load();
        region = Blocks.crystalCluster.region;
    }

    @Override public void loadIcon(){
        uiIcon = fullIcon = Blocks.crystalCluster.uiIcon;
    }

    /** Search a small neighborhood without overwriting any terrain walls or buildings. */
    private static Tile findSite(Unit unit, CrystalStatueBlock block){
        Tile origin = Vars.world.tileWorld(unit.x, unit.y);
        if(origin == null) return null;
        Tile best = null;
        float distance = Float.MAX_VALUE;
        for(int dx = -8; dx <= 8; dx++) for(int dy = -8; dy <= 8; dy++){
            Tile tile = Vars.world.tile(origin.x + dx, origin.y + dy);
            if(tile == null) continue;
            float d = Mathf.dst2(unit.x, unit.y, tile.worldx() + block.offset, tile.worldy() + block.offset);
            if(d >= distance || !empty(tile, block.size)) continue;
            best = tile;
            distance = d;
        }
        return best;
    }

    private static boolean empty(Tile center, int size){
        int lower = -(size - 1) / 2;
        for(int x = 0; x < size; x++) for(int y = 0; y < size; y++){
            Tile tile = Vars.world.tile(center.x + lower + x, center.y + lower + y);
            if(tile == null || tile.block() != Blocks.air || tile.build != null) return false;
        }
        return true;
    }

    /** Server-only conversion, after unit iteration has completed. */
    public static boolean convert(Unit unit){
        if(Vars.net != null && Vars.net.client()) return false;
        if(unit == null || unit.dead || !unit.isAdded()) return false;
        int preferred = Mathf.clamp(Mathf.ceil(unit.hitSize / Vars.tilesize), 1, statues.length);
        for(int size = preferred; size >= 1; size--){
            CrystalStatueBlock block = statues[size - 1];
            Tile site = findSite(unit, block);
            if(site == null) continue;
            String source = unit.type.name + "|" + unit.rotation;
            site.setNet(block, Team.derelict, 0);
            if(!(site.build instanceof CrystalStatueBuild)) return false;
            site.build.configureAny(source);
            PlanetEnvironmentStatuses.crystallized.forget(unit);
            Call.unitDespawn(unit);
            return true;
        }
        // Keep the unit frozen and retry next frame if the entire neighborhood is occupied.
        return false;
    }

    public class CrystalStatueBuild extends Building{
        public String sourceUnit = "";
        public float sourceRotation;

        public void setSource(String value){
            int separator = value.lastIndexOf('|');
            if(separator < 1) return;
            String name = value.substring(0, separator);
            if(Vars.content.unit(name) == null) return;
            try{
                float angle = Float.parseFloat(value.substring(separator + 1));
                if(Float.isNaN(angle) || Float.isInfinite(angle)) return;
                sourceUnit = name;
                sourceRotation = angle;
            }catch(NumberFormatException ignored){}
        }

        @Override public Object config(){ return sourceUnit + "|" + sourceRotation; }

        @Override public void draw(){
            UnitType source = Vars.content.unit(sourceUnit);
            TextureRegion body = source == null ? region
                    : source.fullIcon != null && source.fullIcon.found() ? source.fullIcon : source.region;
            if(body == null || !body.found()) body = region;
            try{
                Draw.color(crystal);
                Draw.mixcol(pale, 0.35f);
                float max = size * Vars.tilesize * 1.2f;
                float scale = Math.min(1f, max / Math.max(body.width / 4f, body.height / 4f));
                Draw.rect(body, x, y, body.width / 4f * scale, body.height / 4f * scale,
                        sourceRotation - 90f);
            }finally{ Draw.reset(); }
        }

        @Override public byte version(){ return 1; }

        @Override public void write(Writes write){
            super.write(write);
            write.str(sourceUnit);
            write.f(sourceRotation);
        }

        @Override public void read(Reads read, byte revision){
            super.read(read, revision);
            sourceUnit = read.str();
            sourceRotation = read.f();
        }
    }
}
