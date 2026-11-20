package cryon.Type;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Time;
import cryon.Features.SacredRiftRenderer;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.UnitType;

/** Existing art stays intact: articulated layers provide camera parallax and actual contact shadows. */
public class SacredRemainsUnitType extends UnitType{
    private static final Color sideDark=Color.valueOf("352733"),sideLight=Color.valueOf("805569"),rim=Color.valueOf("cfb4e2"),legDark=Color.valueOf("463040");
    private final TextureRegion organic=new TextureRegion(),casing=new TextureRegion();
    @Override public void load(){
        super.load();organic.set(region);casing.set(region);
        organic.u2=region.u+(region.u2-region.u)*.56f;casing.u=organic.u2;
    }
    public SacredRemainsUnitType(String name){super(name);}
    @Override public void draw(Unit unit){SacredRiftRenderer.drawUnit(unit, () -> super.draw(unit));}
    public static float parallaxX(float x, float height){return Mathf.clamp((x - Core.camera.position.x) / Core.camera.width, -0.7f, 0.7f) * height * 0.85f;}
    public static float parallaxY(float y, float height){return height * 0.30f + Mathf.clamp((y - Core.camera.position.y) / Core.camera.height, -0.7f, 0.7f) * height * 0.85f;}
    @Override public void drawBody(Unit unit){
        float height = 22f + Mathf.sin(Time.time + unit.id, 26f, 1.2f);
        float px = parallaxX(unit.x, height), py = parallaxY(unit.y, height);
        // Solid side walls below the original silhouette, followed by its unchanged top surface.
        for(int i = 0; i < 5; i++){
            float f = i / 5f;
            Draw.color(sideDark,sideLight,f);
            Draw.rect(region, unit.x + px * f + (1f-f)*2f, unit.y + py * f - (1f-f)*4f, unit.rotation - 90f);
        }
        float rotation=unit.rotation-90f,w=region.width*region.scl(),h=region.height*region.scl();
        Draw.color(Color.white);
        float lx=Angles.trnsx(rotation,-w*.22f,0f),ly=Angles.trnsy(rotation,-w*.22f,0f);
        Draw.rect(organic,unit.x+lx+px,unit.y+ly+py,w*.56f,h,rotation);
        float mx=Angles.trnsx(rotation,w*.28f,0f),my=Angles.trnsy(rotation,w*.28f,0f);
        for(int i=0;i<4;i++){
            float raised=height+i*2f;
            Draw.color(i==3?Color.white:sideDark);
            Draw.rect(casing,unit.x+mx+parallaxX(unit.x,raised),unit.y+my+parallaxY(unit.y,raised),w*.44f,h,rotation);
        }
        Draw.color(rim,0.10f+Mathf.absin(Time.time,26f,.04f));
        Draw.rect(organic,unit.x+lx+px-.6f,unit.y+ly+py+.8f,w*.56f,h,rotation);
        Draw.reset();
    }
    @Override public void drawWeapons(Unit unit){
        // A local matrix raises the weapon with the thorax, never changing gameplay coordinates.
        arc.math.Mat saved = new arc.math.Mat(Draw.trans());
        arc.math.Mat raised = new arc.math.Mat(saved).translate(parallaxX(unit.x,22f), parallaxY(unit.y,22f));
        Draw.flush();Draw.trans(raised);
        try{super.drawWeapons(unit);}finally{Draw.flush();Draw.trans(saved);}
    }
    @Override public <T extends Unit & Legsc> void drawLegs(T unit){
        var legs = unit.legs();
        if(legs.length != 3){super.drawLegs(unit);return;}
        // Back-to-front by foot position, not by a symmetric left/right pair assumption.
        int[] order = {0,1,2};
        for(int a=0;a<3;a++)for(int b=a+1;b<3;b++)if(legs[order[a]].base.y<legs[order[b]].base.y){int t=order[a];order[a]=order[b];order[b]=t;}
        for(int index : order){
            var leg = legs[index];
            float lift = leg.moving ? Mathf.sin(leg.stage * Mathf.PI) * 9f : 0f;
            float bx = unit.x + Angles.trnsx(unit.baseRotation() + 120f * index + 60f, legBaseOffset);
            float by = unit.y + Angles.trnsy(unit.baseRotation() + 120f * index + 60f, legBaseOffset);
            float jx = leg.joint.x + parallaxX(leg.joint.x,14f + lift);
            float jy = leg.joint.y + parallaxY(leg.joint.y,14f + lift);
            float fx = leg.base.x + parallaxX(leg.base.x,lift), fy = leg.base.y + parallaxY(leg.base.y,lift);
            float ox = bx + parallaxX(bx,22f), oy = by + parallaxY(by,22f);
            Draw.color(Pal.shadow,0.55f);
            Lines.stroke(8f);Lines.line(bx+4,by-5,leg.joint.x+4,leg.joint.y-5);
            Lines.line(leg.joint.x+4,leg.joint.y-5,leg.base.x+3,leg.base.y-3);
            Drawf.shadow(leg.base.x,leg.base.y,18f + lift * .3f,0.65f);
            Draw.color(legDark);
            Lines.stroke(legRegion.height * legRegion.scl() + 2f);
            Lines.line(legRegion,ox+1,oy-2,jx+1,jy-2,false);
            Lines.line(legBaseRegion,jx+1,jy-2,fx+1,fy-2,false);
            Draw.color(Color.white);
            Lines.stroke(legRegion.height * legRegion.scl());
            Lines.line(legRegion,ox,oy,jx,jy,false);
            Lines.line(legBaseRegion,jx,jy,fx,fy,false);
            Draw.rect(footRegion,fx,fy,unit.angleTo(fx,fy));
            Draw.rect(jointRegion,jx,jy);
            Draw.rect(baseJointRegion,ox,oy);
        }
        Draw.reset();
    }
}
