package cryon.Type;

import cryon.Content.SubmarineContent;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.UnitWaterMove;

/** Standard naval physics with team-aware concealment, including minimap and network fog. */
public class SubmarineUnit extends UnitWaterMove{
    public static int entityId;

    @Override public int classId(){ return entityId; }

    @Override public boolean inFogTo(Team viewer){
        if(type instanceof SubmarineUnitType && SubmarineContent.diving(this)
                && viewer != team && !((SubmarineUnitType)type).detected(this, viewer)) return true;
        return super.inFogTo(viewer);
    }

    @Override public void draw(){
        if(Vars.player != null && inFogTo(Vars.player.team())) return;
        super.draw();
    }
}
