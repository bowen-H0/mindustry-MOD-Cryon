package cryon.Type;

import cryon.Content.CryonItems;
import arc.util.io.Reads;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.Block;
import mindustry.world.blocks.units.UnitCargoUnloadPoint;
import mindustry.world.meta.*;

public class GreyGooPort extends Block{
    public GreyGooPort(String name){
        super(name);requirements(Category.distribution,BuildVisibility.sandboxOnly,ItemStack.empty);
        solid=update=hasItems=acceptsItems=true;health=1500;itemCapacity=480;
        buildType=LoaderBuild::new;
    }
    public class LoaderBuild extends Building{
        @Override public boolean acceptItem(Building source,Item item){return enabled&&source.team==team&&item==CryonItems.nanoMaterial&&items.get(item)<itemCapacity;}
        @Override public boolean canUnload(){return false;}
    }
    public static class UnloadPoint extends UnitCargoUnloadPoint{
        public UnloadPoint(String name){
            super(name);requirements(Category.distribution,BuildVisibility.sandboxOnly,ItemStack.empty);
            size=2;health=2500;itemCapacity=480;configurable=false;clearOnDoubleTap=false;buildType=GooUnloadBuild::new;
        }
        public class GooUnloadBuild extends UnitCargoUnloadPointBuild{
            @Override public void created(){super.created();item=CryonItems.nanoMaterial;}
            @Override public void read(Reads r,byte revision){super.read(r,revision);item=CryonItems.nanoMaterial;}
            @Override public int acceptStack(Item item,int amount,Teamc source){
                return enabled&&(source==null||source.team()==team)&&item==CryonItems.nanoMaterial?super.acceptStack(item,amount,source):0;
            }
        }
    }
}
