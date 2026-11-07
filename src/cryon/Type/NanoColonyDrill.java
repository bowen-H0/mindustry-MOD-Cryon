package cryon.Type;

import arc.util.io.*;
import cryon.Content.CryonItems;
import mindustry.world.Tile;
import mindustry.world.blocks.production.Drill;

/** Old drills retain their eroder chain; new feeders persist their logistics role. */
public class NanoColonyDrill extends Drill{
    public NanoColonyDrill(String name){
        super(name);
        configurable = true;
        config(Boolean.class,(NanoDrillBuild b,Boolean value)->b.feeder=value);
        buildType = NanoDrillBuild::new;
    }
    @Override public boolean canMine(Tile tile){return tile!=null&&tile.drop()==CryonItems.nanoMaterial&&super.canMine(tile);}
    public class NanoDrillBuild extends DrillBuild{
        public boolean feeder;
        @Override public Object config(){return feeder;}
        @Override public byte version(){return 2;}
        @Override public void write(Writes w){super.write(w);w.bool(feeder);}
        @Override public void read(Reads r,byte revision){super.read(r,revision);feeder=revision>=2&&r.bool();}
    }
}
