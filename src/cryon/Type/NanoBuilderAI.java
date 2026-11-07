package cryon.Type;

import arc.util.Time;
import arc.math.geom.*;
import cryon.Content.NanoEnvironment;
import cryon.Content.NanoColonyContent;
import cryon.Content.CryonItems;
import cryon.Features.NanoConstruction;
import cryon.Fx.NanoFeedbackFx;
import mindustry.Vars;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.world.blocks.ConstructBlock.ConstructBuild;

/** A visible, moving native builder: repair first, then reconstruct, extend or recycle. */
public class NanoBuilderAI extends AIController{
    private Building repair,supply;
    private float search,feedback,constructionCredit,repairCredit,taskRetry,supplyRetry;
    private final Vec2 goal=new Vec2(),waypoint=new Vec2();
    private final boolean[] noPath={false};
    @Override public void init(){super.init();search=unit.id%30;taskRetry=0;supplyRetry=0;}
    private void approach(Position target,float range){
        if(unit.within(target,range)){unit.vel.scl(0.85f);return;}
        goal.set(target);
        if(Vars.controlPath!=null&&Vars.controlPath.getPathPosition(unit,goal,waypoint,noPath)){
            moveTo(waypoint,0f,24f);faceMovement();
        }else if(Vars.controlPath==null)moveTo(target,range,24f);
    }
    private boolean stock(int minimum){
        if(unit.stack.amount>=minimum)return true;
        if(supply==null||!supply.isValid()||supply.team!=unit.team||supply.items==null||supply.items.get(CryonItems.nanoMaterial)==0){
            if(supplyRetry>0f)return false;
            supplyRetry=15f+unit.id%11;
            supply=null;float best=Float.MAX_VALUE;
            for(Building b:unit.team.data().buildings){
                if(!b.isValid()||b.items==null||(b.block!=NanoColonyContent.drill&&b.block!=NanoColonyContent.warehouse)||b.items.get(CryonItems.nanoMaterial)==0||NanoEnvironment.suppressed(b.tile))continue;
                float score=unit.dst2(b);if(score<best&&NanoConstruction.reachable(unit,b,b.block.size*4f+18f)){best=score;supply=b;}
            }
        }
        if(supply!=null){
            approach(supply,supply.block.size*4f+10f);unit.lookAt(supply);
            if(unit.within(supply,supply.block.size*4f+18f)){
                int amount=Math.min(unit.type.itemCapacity-unit.stack.amount,supply.items.get(CryonItems.nanoMaterial));
                if(amount>0)Call.takeItems(supply,CryonItems.nanoMaterial,amount,unit);
            }
        }
        return unit.stack.amount>=minimum;
    }
    @Override public void updateMovement(){
        if(Vars.net.client())return;
        unit.heal(8f*Time.delta/60f);
        search-=Time.delta;feedback+=Time.delta;taskRetry-=Time.delta;supplyRetry-=Time.delta;
        if(search<=0){
            search=30f;repair=null;float best=Float.MAX_VALUE;
            for(Building b:unit.team.data().buildings){
                if(!b.isValid()||!b.damaged()||b instanceof ConstructBuild||NanoConstruction.retiring(b)||NanoEnvironment.suppressed(b.tile)||!unit.within(b,40*Vars.tilesize))continue;
                float score=unit.dst2(b)*Math.max(0.1f,b.healthf());if(score<best&&NanoConstruction.reachable(unit,b,52f)){best=score;repair=b;}
            }
        }
        if(repair!=null&&repair.isValid()&&repair.damaged()&&!NanoConstruction.retiring(repair)&&!NanoEnvironment.suppressed(repair.tile)){
            unit.updateBuilding(false);approach(repair,36f);unit.lookAt(repair);
            if(repairCredit<=0){if(!stock(1))return;unit.stack.amount--;repairCredit+=180f;}
            if(unit.within(repair,52f)){
                float amount=Math.min(repair.maxHealth-repair.health,Math.min(repairCredit,(180f+repair.maxHealth*0.02f)*Time.delta/60f));
                repair.heal(amount);repairCredit-=amount;
                if(feedback>=12f){feedback=0;NanoFeedbackFx.event(NanoFeedbackFx.repair,repair.x,repair.y,repair.block.size,1);}
            }
            return;
        }
        unit.updateBuilding(false);
        BuildPlan plan=unit.buildPlan();
        if(plan==null||plan.tile()==null||!NanoConstruction.current(plan)||!NanoConstruction.unfinished(plan)||NanoEnvironment.suppressed(plan.tile())||(!plan.breaking&&plan.tile().floor()!=NanoEnvironment.floor)){
            unit.clearBuilding();plan=null;
            if(taskRetry<=0f){
                plan=NanoConstruction.next(unit);
                if(plan!=null){unit.addBuild(plan);taskRetry=0f;}else taskRetry=20f+unit.id%17;
            }
        }
        if(plan!=null){
            if(!plan.breaking){
                float total=2f*plan.block.size*plan.block.size;
                float progress=plan.tile().build instanceof ConstructBuild?((ConstructBuild)plan.tile().build).progress:0f;
                float duration=Math.max(120f*plan.block.size,plan.block.buildTime*Vars.state.rules.buildCostMultiplier);
                float advance=total*10f*unit.type.buildSpeed*unit.buildSpeedMultiplier*Vars.state.rules.buildSpeed(unit.team)/duration;
                int reserve=Math.max(0,(int)Math.ceil(Math.min(total*(1f-progress),advance)-constructionCredit-0.0001f));
                if(!stock(reserve))return;
            }
            approach(plan,unit.type.buildRange*0.65f);unit.lookAt(plan);
            if(plan.tile().build instanceof ConstructBuild){
                // Even tiny ducts grow over multiple frames, including in older saves.
                ConstructBuild site=(ConstructBuild)plan.tile().build;
                site.buildCost=Math.max(site.buildCost,120f*site.current.size);
            }
            float cost=NanoConstruction.build(unit);
            if(cost>0){
                int paid=Math.max(0,(int)Math.ceil(cost-constructionCredit));
                unit.stack.amount=Math.max(0,unit.stack.amount-paid);constructionCredit+=paid-cost;
            }else if(cost<0){
                constructionCredit-=cost;int refund=Math.min((int)constructionCredit,unit.type.itemCapacity-unit.stack.amount);
                if(refund>0){unit.stack.item=CryonItems.nanoMaterial;unit.stack.amount+=refund;constructionCredit-=refund;}
                constructionCredit=Math.min(constructionCredit,1f);
            }
        }else{unit.vel.scl(0.9f);}
    }
}
