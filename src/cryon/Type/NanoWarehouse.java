package cryon.Type;

import arc.struct.IntSeq;
import arc.util.io.*;
import cryon.Content.CryonItems;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.blocks.storage.StorageBlock;
import mindustry.world.meta.*;

/** Independent storage, never a core or victory objective. Bootstrap belt plan is saved. */
public class NanoWarehouse extends StorageBlock{
    public NanoWarehouse(String name){
        super(name);requirements(Category.effect,BuildVisibility.sandboxOnly,ItemStack.empty);
        size=5;health=12000;armor=10;itemCapacity=12000;coreMerge=false;
        buildType=NanoWarehouseBuild::new;
    }
    public class NanoWarehouseBuild extends StorageBuild{
        public int supplyPos=-1;
        public final IntSeq supplyRoute=new IntSeq();
        @Override public boolean acceptItem(Building source,Item item){
            return source.team==team&&item==CryonItems.nanoMaterial&&super.acceptItem(source,item);
        }
        @Override public int acceptStack(Item item,int amount,Teamc source){
            return item==CryonItems.nanoMaterial&&(source==null||source.team()==team)?super.acceptStack(item,amount,source):0;
        }
        @Override public byte version(){return 1;}
        @Override public void write(Writes w){super.write(w);w.i(supplyPos);w.s(supplyRoute.size);for(int i=0;i<supplyRoute.size;i++)w.i(supplyRoute.get(i));}
        @Override public void read(Reads r,byte revision){
            super.read(r,revision);supplyRoute.clear();
            if(revision>=1){supplyPos=r.i();int count=r.us();for(int i=0;i<count;i++)supplyRoute.add(r.i());}
        }
    }
}
