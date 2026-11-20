package cryon.Type;

import mindustry.game.Team;
import mindustry.world.Tile;
import mindustry.world.blocks.production.HeatCrafter;
import mindustry.world.meta.BuildVisibility;

/** Existing and saved furnaces retain their native heat recipe; new placement is forbidden. */
public class LegacyFusionCrafter extends HeatCrafter{
    public LegacyFusionCrafter(String name){
        super(name);
        buildVisibility = BuildVisibility.hidden;
        placeablePlayer = false;
    }
    @Override public void init(){
        super.init();
        // HJSON requirements can restore shown visibility while content is parsed.
        buildVisibility = BuildVisibility.hidden;
        placeablePlayer = false;
    }
    @Override public boolean canPlaceOn(Tile tile, Team team, int rotation){ return false; }
}
