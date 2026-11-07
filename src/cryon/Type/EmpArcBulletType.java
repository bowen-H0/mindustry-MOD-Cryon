package cryon.Type;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import cryon.Content.NanoEnvironment;
import cryon.Features.MapAlliances;
import cryon.Fx.EmpFx;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.core.World;
import mindustry.entities.bullet.*;
import mindustry.gen.*;
import mindustry.world.Tile;

/** Moving, piercing electric projectile with native LightningBulletType side discharges. */
public class EmpArcBulletType extends BulletType{
    public final LightningBulletType discharge;
    public EmpArcBulletType(){
        super(4f, 480f);
        lifetime = 40f; hitSize = 8f; keepVelocity = false;
        pierce = pierceBuilding = true; pierceCap = -1;
        status = StatusEffects.shocked; statusDuration = 120f;
        hitColor = lightColor = trailColor = EmpFx.blue;
        hitEffect = Fx.hitLancer; despawnEffect = shootEffect = Fx.none;
        trailLength = 10; trailWidth = 1.5f; drawSize = 120f;
        discharge = new LightningBulletType();
        discharge.damage = 60f; discharge.lightningLength = 4;
        discharge.lightningColor = EmpFx.blue;
        discharge.lightningType = new EmpLightningPoint();
    }
    @Override public void update(Bullet b){
        super.update(b);
        if(Vars.net.client()) return;
        World.raycastEach(World.toTile(b.lastX), World.toTile(b.lastY), b.tileX(), b.tileY(), (x,y) -> {
            for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++){
                Tile t=Vars.world.tile(x+dx,y+dy);
                if(t!=null&&arc.math.geom.Intersector.distanceSegmentPoint(b.lastX,b.lastY,b.x,b.y,t.worldx(),t.worldy())<=6f){
                    NanoEnvironment.neutralize(t);
                }
            }
            return false;
        });
        // Short real lightning branches, while sufficiently far from the nominal range edge.
        if(b.time < lifetime - 8f && b.timer.get(3, 8f)){
            for(int side : Mathf.signs) discharge.createNet(b.team,b.x,b.y,b.rotation()+side*55f,60f,1f,1f);
        }
    }
    @Override public boolean testCollision(Bullet b,Building build){
        return !MapAlliances.allied(b.team,build.team)&&super.testCollision(b,build);
    }
    @Override public void hitEntity(Bullet b,Hitboxc entity,float health){
        if(entity instanceof Teamc&&MapAlliances.allied(b.team,((Teamc)entity).team()))return;
        super.hitEntity(b,entity,health);
    }
    @Override public void draw(Bullet b){
        if(b.trail!=null)b.trail.draw(EmpFx.blue,trailWidth);
        drawBolt(b.x,b.y,b.rotation(),b.id,(int)(b.time/3f));
    }
    public static void drawBolt(float x,float y,float angle,int seed,int phase){
        for(int pass=0;pass<2;pass++){
            Draw.color(pass==0?EmpFx.blue:Color.white);Lines.stroke(pass==0?3f:1.1f);
            float px=x,py=y;
            for(int i=1;i<=6;i++){
                float side=i==6?0f:Mathf.randomSeed(seed*37L+phase*19L+i,-4f,4f);
                float nx=x+Angles.trnsx(angle,-i*4f,side),ny=y+Angles.trnsy(angle,-i*4f,side);
                Lines.line(px,py,nx,ny);px=nx;py=ny;
            }
        }
        Draw.reset();
    }
    /** Native lightning damage nodes can also burn nano terrain into metal ruins. */
    public static class EmpLightningPoint extends BulletType{
        public EmpLightningPoint(){
            super(0.0001f,60f);lifetime=8f;hittable=false;keepVelocity=false;
            hitEffect=Fx.hitLancer;despawnEffect=Fx.none;hitColor=EmpFx.blue;
            status=StatusEffects.shocked;statusDuration=120f;
        }
        @Override public void init(Bullet b){
            super.init(b);
            if(Vars.net.client())return;
            for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)NanoEnvironment.neutralize(Vars.world.tile(b.tileX()+dx,b.tileY()+dy));
        }
        @Override public boolean testCollision(Bullet b,Building build){return !MapAlliances.allied(b.team,build.team)&&super.testCollision(b,build);}
        @Override public void hitEntity(Bullet b,Hitboxc entity,float health){
            if(entity instanceof Teamc&&MapAlliances.allied(b.team,((Teamc)entity).team()))return;
            super.hitEntity(b,entity,health);
        }
        @Override public void draw(Bullet b){} // Native Lightning draws the electrical branch itself.
    }
}
