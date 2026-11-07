package cryon.Type;

import arc.util.io.*;
import arc.struct.IntSeq;
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
    @Override public boolean canPlaceOn(mindustry.world.Tile tile,mindustry.game.Team team,int rotation){
        return team==cryon.Features.DisasterFactions.nano&&super.canPlaceOn(tile,team,rotation);
    }
    public class NanoDrillBuild extends DrillBuild{
        public boolean feeder;
        public int supplyTarget=-1;
        public final IntSeq supplyRoute=new IntSeq();

        @Override public Object config(){return feeder;}
        @Override public byte version(){return 3;}
        @Override public void write(Writes w){super.write(w);w.bool(feeder);w.i(supplyTarget);w.s(supplyRoute.size);for(int pos:supplyRoute.toArray())w.i(pos);}
        @Override public void read(Reads r,byte revision){super.read(r,revision);feeder=revision>=2&&r.bool();supplyRoute.clear();supplyTarget=-1;
            if(revision>=3){supplyTarget=r.i();int count=r.us();for(int i=0;i<count;i++)supplyRoute.add(r.i());}}
    }
}
