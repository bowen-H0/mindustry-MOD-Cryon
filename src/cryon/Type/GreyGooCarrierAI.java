package cryon.Type;

import arc.util.Time;
import cryon.Content.CryonItems;
import mindustry.Vars;
import mindustry.entities.units.AIController;
import mindustry.gen.*;

/** Untethered factory-built transport. Keeps its cargo if a destination is full or destroyed. */
public class GreyGooCarrierAI extends AIController{
    public Building source,destination;
    private float search,transfer;
    @Override public void updateUnit(){
        if(Vars.net.client()||unit.dead)return;
        unit.controlWeapons(false,false);search-=Time.delta;transfer+=Time.delta;
        if(unit.hasItem()){
            if(!valid(destination)||destination.acceptStack(unit.item(),unit.stack.amount,unit)<=0||search<=0f){destination=find(false);search=30f;}
            if(destination==null)return;
            moveTo(destination,8f,12f);unit.lookAt(destination);
            if(unit.within(destination,18f)&&transfer>=8f){
                int amount=destination.acceptStack(unit.item(),unit.stack.amount,unit);
                if(amount>0)Call.transferItemTo(unit,unit.item(),amount,unit.x,unit.y,destination);
                transfer=0f;
            }
        }else{
            if(!valid(source)||source.items.get(CryonItems.nanoMaterial)==0||search<=0f){source=find(true);search=30f;}
            if(source==null)return;
            // Do not collect until a receiver can accept it, preventing stranded supply flights.
            if(find(false)==null)return;
            moveTo(source,8f,12f);unit.lookAt(source);
            if(unit.within(source,18f)&&transfer>=8f){
                Call.takeItems(source,CryonItems.nanoMaterial,Math.min(unit.type.itemCapacity,source.items.get(CryonItems.nanoMaterial)),unit);transfer=0f;
                destination=null;
            }
        }
    }
    private boolean valid(Building b){return b!=null&&b.isValid()&&!b.dead&&b.enabled&&b.team==unit.team;}
    private Building find(boolean loading){
        Building best=null;float score=Float.MAX_VALUE;
        for(Building b:unit.team.data().buildings){
            if(!valid(b))continue;
            if(loading){if(!(b.block instanceof GreyGooPort)||b.items.get(CryonItems.nanoMaterial)==0)continue;}
            else if(!(b instanceof GreyGooPort.UnloadPoint.GooUnloadBuild)||b.acceptStack(CryonItems.nanoMaterial,1,unit)<=0)continue;
            float candidate=loading?unit.dst2(b)/Math.max(1f,Math.min(unit.type.itemCapacity,b.items.get(CryonItems.nanoMaterial)))
                :unit.dst2(b)+b.items.total()*100f;
            if(candidate<score){best=b;score=candidate;}
        }
        return best;
    }
}
