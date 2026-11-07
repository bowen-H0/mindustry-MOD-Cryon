package cryon.Type;

import arc.math.geom.Vec2;
import arc.util.Time;
import cryon.Features.MapAlliances;
import mindustry.Vars;
import mindustry.entities.units.AIController;
import mindustry.game.Team;
import mindustry.gen.*;

/** Combat specialists seek enemy infrastructure even beyond their weapon acquisition range. */
public class NanoAssaultAI extends AIController{
    private Building objective;
    private float refresh;
    private final Vec2 goal=new Vec2(),next=new Vec2();
    private final boolean[] noPath={false};
    @Override public void updateMovement(){
        if(Vars.net.client())return;
        if(target!=null&&!invalid(target)&&unit.within(target,unit.range()*0.9f)){
            moveTo(target,unit.range()*0.65f);unit.lookAt(target);return;
        }
        refresh-=Time.delta;
        if(refresh<=0||(objective!=null&&(!objective.isValid()||objective.dead))){
            refresh=120f+unit.id%41;objective=null;float score=Float.MAX_VALUE;
            for(Team team:Team.all)if(team!=Team.derelict&&!MapAlliances.allied(unit.team,team)){
                for(Building b:Vars.state.teams.get(team).buildings){
                    if(!b.isValid()||b.dead||!b.block.targetable)continue;
                    float candidate=unit.dst2(b);
                    if(candidate<score){objective=b;score=candidate;}
                }
            }
        }
        if(objective==null)return;
        goal.set(objective.x,objective.y);
        if(Vars.controlPath!=null&&Vars.controlPath.getPathPosition(unit,goal,next,noPath))moveTo(next,0f);
        else if(Vars.controlPath==null)moveTo(objective,unit.range()*0.7f);
        faceMovement();
    }
}
