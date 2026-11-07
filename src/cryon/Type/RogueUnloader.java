package cryon.Type;

import arc.util.Time;
import cryon.Content.CryonItems;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.Block;
import mindustry.world.meta.*;

/** Directional warehouse extraction; never steals from factories or returns ammo to storage. */
public class RogueUnloader extends Block{
    public RogueUnloader(String name){
        super(name);requirements(Category.distribution,BuildVisibility.editorOnly,ItemStack.empty);
        update=solid=rotate=true;health=1200;unloadable=false;buildType=RogueUnloaderBuild::new;
    }
    @Override public boolean canPlaceOn(mindustry.world.Tile tile,mindustry.game.Team team,int rotation){
        return team==cryon.Features.DisasterFactions.nano&&super.canPlaceOn(tile,team,rotation);
    }
    public class RogueUnloaderBuild extends Building{
        private float clock;
        @Override public void updateTile(){
            if(mindustry.Vars.net.client()||!enabled||team!=cryon.Features.DisasterFactions.nano)return;
            clock+=Time.delta;if(clock<4f)return;clock%=4f;
            Building source=back(),sink=front();
            if(source==null||sink==null||source.team!=team||sink.team!=team||!(source.block instanceof NanoWarehouse))return;
            for(int i=0;i<4&&source.items.has(CryonItems.nanoMaterial)&&sink.acceptItem(this,CryonItems.nanoMaterial);i++){
                source.items.remove(CryonItems.nanoMaterial,1);sink.handleItem(this,CryonItems.nanoMaterial);
            }
        }
    }
}
