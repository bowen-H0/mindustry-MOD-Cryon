package cryon.Type;

import arc.Core;
import mindustry.Vars;
import mindustry.content.Liquids;
import mindustry.game.Team;
import mindustry.world.Tile;
import mindustry.world.blocks.power.PowerGenerator;
import mindustry.world.meta.Env;

/** A self-starting underwater turbine powered by water beneath its entire footprint. */
public class WaterCurrentGenerator extends PowerGenerator{
    public WaterCurrentGenerator(String name){
        super(name);
        floating = true;
        placeableLiquid = true;
        envEnabled |= Env.underwater;
        envDisabled &= ~Env.underwater;
        envRequired = Env.underwater;
        canOverdrive = false;
        buildType = WaterCurrentGeneratorBuild::new;
    }

    public boolean waterSupported(Tile tile){
        if(tile == null) return false;
        int start = -(size - 1) / 2;
        for(int dx = start; dx < start + size; dx++){
            for(int dy = start; dy < start + size; dy++){
                Tile other = Vars.world.tile(tile.x + dx, tile.y + dy);
                if(other == null || !other.floor().isLiquid || other.floor().liquidDrop != Liquids.water) return false;
            }
        }
        return true;
    }

    @Override
    public boolean canPlaceOn(Tile tile, Team team, int rotation){
        return super.canPlaceOn(tile, team, rotation) && waterSupported(tile);
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        super.drawPlace(x, y, rotation, valid);
        if(!waterSupported(Vars.world.tile(x, y))){
            drawPlaceText(Core.bundle.get("cryon.generator.requires-water"), x, y, false);
        }
    }

    public class WaterCurrentGeneratorBuild extends GeneratorBuild{
        private float turbineRotation;

        private boolean active(){
            return enabled && supportsEnv(Vars.state.rules.env) && waterSupported(tile);
        }

        @Override
        public void updateTile(){
            productionEfficiency = active() ? 1f : 0f;
            turbineRotation += delta() * productionEfficiency;
        }

        @Override
        public float getPowerProduction(){
            // Also check here: the power graph can update before this building's tick.
            return active() ? powerProduction : 0f;
        }

        @Override
        public float totalProgress(){
            return turbineRotation;
        }
    }
}
