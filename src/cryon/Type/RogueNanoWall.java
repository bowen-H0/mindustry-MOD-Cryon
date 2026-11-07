package cryon.Type;

import arc.util.Time;
import mindustry.type.*;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.meta.*;

/** Living colony defense, independent of power and campaign cores. */
public class RogueNanoWall extends Wall{
    public RogueNanoWall(String name){
        super(name);
        requirements(Category.defense, BuildVisibility.editorOnly, ItemStack.empty);
        health = 1800; armor = 12f; update = true; absorbLasers = true;
        buildType = RogueWallBuild::new;
    }
    @Override public boolean canPlaceOn(mindustry.world.Tile tile,mindustry.game.Team team,int rotation){
        return team==cryon.Features.DisasterFactions.nano&&super.canPlaceOn(tile,team,rotation);
    }
    public class RogueWallBuild extends WallBuild{
        @Override public void updateTile(){ if(!mindustry.Vars.net.client() && enabled && team==cryon.Features.DisasterFactions.nano) heal(45f * Time.delta / 60f); }
        @Override public void draw(){
            super.draw();
            if(enabled && damaged()) cryon.Fx.NanoFeedbackFx.ambient(cryon.Fx.NanoFeedbackFx.repair, x, y, 0f, 25f);
        }
    }
}
