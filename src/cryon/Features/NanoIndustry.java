package cryon.Features;

import arc.math.geom.Geometry;
import arc.struct.*;
import cryon.Content.*;
import cryon.Fx.NanoFeedbackFx;
import cryon.Type.*;
import cryon.Type.NanoWarehouse.NanoWarehouseBuild;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.world.*;

/** Compact, reconstructible warehouse districts with physical bootstrap belts and air freight. */
public final class NanoIndustry{
    private static final Seq<NanoWarehouseBuild> hubs=new Seq<>();
    private static Seq<Module> modules;
    private static class Module{
        Block block;int x,y,rotation;
        Module(Block b,int x,int y,int rotation){block=b;this.x=x;this.y=y;this.rotation=rotation;}
    }
    private static Seq<Module> modules(){
        if(modules==null)modules=Seq.with(
            new Module(NanoColonyContent.unloadPoint,-4,0,0),
            new Module(NanoColonyContent.largeFactory,0,6,1),new Module(NanoColonyContent.unloader,0,3,1),
            new Module(NanoColonyContent.combatFactory,6,0,0),new Module(NanoColonyContent.unloader,3,0,0),
            new Module(NanoColonyContent.ultimateFactory,0,-7,3),new Module(NanoColonyContent.unloader,0,-3,3),
            new Module(NanoColonyContent.cleaver,-6,-6,0),new Module(NanoColonyContent.unloader,-3,-2,2),
            new Module(belt(),-4,-2,3),new Module(belt(),-4,-3,3),new Module(belt(),-4,-4,3),new Module(belt(),-4,-5,2));
        return modules;
    }
    private static Block belt(){return Vars.content.block("cryon-reinforced-vacuum-duct");}
    public static void clear(){hubs.clear();}
    public static boolean hasHub(){return hubs.any();}
    public static boolean feeder(Building b){return b instanceof NanoColonyDrill.NanoDrillBuild&&((NanoColonyDrill.NanoDrillBuild)b).feeder;}
    public static void step(Seq<Building> drills){
        hubs.clear();
        for(Building b:DisasterFactions.nano.data().buildings)if(b instanceof NanoWarehouseBuild&&b.isValid())hubs.add((NanoWarehouseBuild)b);
        for(NanoWarehouseBuild hub:hubs){
            if(hub.supplyRoute.isEmpty()&&!hasCarriers())for(Building drill:drills){
                IntSeq bootstrap=route(drill.tile,hub.tile);
                if(bootstrap!=null){hub.supplyPos=drill.pos();hub.supplyRoute.addAll(bootstrap);break;}
            }
            growRoute(hub);growDistrict(hub);
        }
        for(Building drill:drills)if(feeder(drill))growFeeder(drill);
        if(hubs.isEmpty()){
            for(Building drill:drills)if(!feeder(drill)){
                for(int radius:new int[]{14,18,22,26})for(int dir=0;dir<8;dir++){
                    int dx=new int[]{1,1,0,-1,-1,-1,0,1}[dir],dy=new int[]{0,1,1,1,0,-1,-1,-1}[dir];
                    Tile center=Vars.world.tile(drill.tile.x+dx*radius,drill.tile.y+dy*radius);
                    if(tryHub(drill,center))return;
                }
            }
        }else if(hubs.size<4&&hubs.first().items.get(CryonItems.nanoMaterial)>9000){
            NanoWarehouseBuild root=hubs.first();
            for(int d=0;d<4;d++){
                Tile center=Vars.world.tile(root.tile.x+Geometry.d4x(d)*24,root.tile.y+Geometry.d4y(d)*24);
                if(drills.any()&&tryHub(drills.first(),center))return;
            }
        }
    }
    private static boolean tryHub(Building source,Tile center){
        if(center==null||!fits(center,NanoColonyContent.warehouse))return false;
        for(int x=-2;x<=2;x++)for(int y=-2;y<=2;y++)if(reservedExit(Vars.world.tile(center.x+x,center.y+y))||starterReserved(Vars.world.tile(center.x+x,center.y+y)))return false;
        for(Module m:modules()){
            Tile target=Vars.world.tile(center.x+m.x,center.y+m.y);
            if(!fits(target,m.block)||!frontClear(target,m.block,m.rotation))return false;
            int offset=-(m.block.size-1)/2;
            for(int x=0;x<m.block.size;x++)for(int y=0;y<m.block.size;y++){
                Tile cell=Vars.world.tile(target.x+offset+x,target.y+offset+y);
                if(starterReserved(cell)||reservedExit(cell))return false;
            }
            if(m.block instanceof NanoMachineFactory){
                NanoMachineFactory factory=(NanoMachineFactory)m.block;int front=(m.block.size+1)/2;
                for(int f=front;f<front+factory.clearanceDepth();f++)for(int side=-factory.clearanceHalfWidth();side<=factory.clearanceHalfWidth();side++){
                    Tile exit=Vars.world.tile(target.x+Geometry.d4x(m.rotation)*f-Geometry.d4y(m.rotation)*side,target.y+Geometry.d4y(m.rotation)*f+Geometry.d4x(m.rotation)*side);
                    if(starterReserved(exit))return false;
                }
            }
        }
        IntSeq route=route(source.tile,center);
        if(route==null){if(!hasCarriers())return false;route=new IntSeq();}
        place(center,NanoColonyContent.warehouse,0);
        NanoWarehouseBuild hub=(NanoWarehouseBuild)center.build;
        hub.supplyPos=source.pos();hub.supplyRoute.addAll(route);hubs.add(hub);return true;
    }
    private static boolean hasCarriers(){return Groups.unit.contains(u->!u.dead&&u.team==DisasterFactions.nano&&u.type==NanoColonyContent.carrier);}
    private static void growRoute(NanoWarehouseBuild hub){
        for(int i=0;i<hub.supplyRoute.size;i++){
            Tile t=Vars.world.tile(hub.supplyRoute.get(i));if(t==null)return;
            Tile next=i+1<hub.supplyRoute.size?Vars.world.tile(hub.supplyRoute.get(i+1)):hub.tile;
            int rotation=i+1<hub.supplyRoute.size?t.relativeTo(next.x,next.y):t.absoluteRelativeTo(next.x,next.y);
            if(t.block()==belt()&&t.team()==hub.team&&t.build.rotation==rotation)continue;
            if(available(t))place(t,belt(),rotation);
            return;
        }
    }
    private static void growDistrict(NanoWarehouseBuild hub){
        for(Module m:modules()){
            Tile t=Vars.world.tile(hub.tile.x+m.x,hub.tile.y+m.y);
            if(t!=null&&t.block()==m.block&&t.team()==hub.team)continue;
            if(fits(t,m.block)&&frontClear(t,m.block,m.rotation)){place(t,m.block,m.rotation);return;}
        }
        // Leave exits and the bootstrap route open while growing a loose defensive perimeter.
        for(int x=-12;x<=12;x+=3)for(int y:new int[]{-13,12}){
            Tile t=Vars.world.tile(hub.tile.x+x,hub.tile.y+y);
            if(available(t)&&!reservedExit(t)&&!hub.supplyRoute.contains(t.pos())){place(t,NanoColonyContent.wall,0);return;}
        }
    }
    public static boolean feederFits(Tile origin,int rotation){
        for(int f=0;f<=4;f++)if(!available(at(origin,rotation,f))||reservedSite(at(origin,rotation,f)))return false;
        return true;
    }
    private static void growFeeder(Building drill){
        if(!drill.isValid()||NanoEnvironment.suppressed(drill.tile))return;
        for(int f=1;f<=4;f++){
            Tile t=at(drill.tile,drill.rotation,f);Block desired=f==4?NanoColonyContent.loader:belt();
            if(t!=null&&t.block()==desired&&t.team()==drill.team)continue;
            if(available(t)&&!reservedExit(t))place(t,desired,f==4?0:drill.rotation);
            return;
        }
    }
    private static Tile at(Tile origin,int rotation,int forward){return Vars.world.tile(origin.x+Geometry.d4x(rotation)*forward,origin.y+Geometry.d4y(rotation)*forward);}
    private static boolean available(Tile t){return t!=null&&t.block()==Blocks.air&&t.floor()==NanoEnvironment.floor&&t.floor().hasSurface()&&!NanoEnvironment.suppressed(t);}
    private static boolean fits(Tile center,Block block){
        if(center==null)return false;
        int offset=-(block.size-1)/2;
        for(int x=0;x<block.size;x++)for(int y=0;y<block.size;y++)if(!available(Vars.world.tile(center.x+offset+x,center.y+offset+y)))return false;
        return true;
    }
    private static boolean frontClear(Tile center,Block block,int rotation){
        if(!(block instanceof NanoMachineFactory))return true;
        NanoMachineFactory factory=(NanoMachineFactory)block;
        int front=(block.size+1)/2;
        for(int f=front;f<front+factory.clearanceDepth();f++)for(int s=-factory.clearanceHalfWidth();s<=factory.clearanceHalfWidth();s++){
            Tile t=Vars.world.tile(center.x+Geometry.d4x(rotation)*f-Geometry.d4y(rotation)*s,center.y+Geometry.d4y(rotation)*f+Geometry.d4x(rotation)*s);
            if(t==null||t.block()!=Blocks.air||!t.floor().hasSurface())return false;
        }
        return true;
    }
    public static boolean reservedExit(Tile t){
        if(t==null)return true;
        for(NanoWarehouseBuild hub:hubs)for(Module m:modules())if(m.block instanceof NanoMachineFactory){
            int dx=t.x-hub.tile.x-m.x,dy=t.y-hub.tile.y-m.y;
            int forward=dx*Geometry.d4x(m.rotation)+dy*Geometry.d4y(m.rotation);
            int side=-dx*Geometry.d4y(m.rotation)+dy*Geometry.d4x(m.rotation);
            NanoMachineFactory factory=(NanoMachineFactory)m.block;int front=(m.block.size+1)/2;
            if(forward>=front&&forward<front+factory.clearanceDepth()&&Math.abs(side)<=factory.clearanceHalfWidth())return true;
        }
        for(Building b:DisasterFactions.nano.data().buildings)if(b.block instanceof NanoMachineFactory){
            int dx=t.x-b.tile.x,dy=t.y-b.tile.y;
            NanoMachineFactory factory=(NanoMachineFactory)b.block;int front=(b.block.size+1)/2,f=dx*Geometry.d4x(b.rotation)+dy*Geometry.d4y(b.rotation);
            if(f>=front&&f<front+factory.clearanceDepth()&&Math.abs(-dx*Geometry.d4y(b.rotation)+dy*Geometry.d4x(b.rotation))<=factory.clearanceHalfWidth())return true;
        }
        return false;
    }
    public static boolean reservedSite(Tile t){
        if(t==null)return true;
        for(NanoWarehouseBuild hub:hubs)if(planned(t,hub.tile)||hub.supplyRoute.contains(t.pos()))return true;
        return reservedExit(t)||starterReserved(t);
    }
    private static boolean starterReserved(Tile t){
        if(t==null)return true;
        for(Building b:DisasterFactions.nano.data().buildings)if(b.block==NanoColonyContent.drill){
            int dx=t.x-b.tile.x,dy=t.y-b.tile.y;
            int f=dx*Geometry.d4x(b.rotation)+dy*Geometry.d4y(b.rotation),s=-dx*Geometry.d4y(b.rotation)+dy*Geometry.d4x(b.rotation);
            if(feeder(b)){if(f>=0&&f<=4&&s==0)return true;continue;}
            if(f>=0&&f<=5&&s==0)return true;
            if(f>=6&&f<=11&&Math.abs(s)<=1)return true;
            if(f>=-2&&f<=8&&(f+2)%2==0&&Math.abs(s)==3)return true;
        }
        return false;
    }
    private static boolean planned(Tile t,Tile root){
        if(Math.abs(t.x-root.x)<=2&&Math.abs(t.y-root.y)<=2)return true;
        for(Module m:modules()){
            int x=t.x-root.x-m.x,y=t.y-root.y-m.y,offset=-(m.block.size-1)/2;
            if(x>=offset&&x<offset+m.block.size&&y>=offset&&y<offset+m.block.size)return true;
            if(m.block instanceof NanoMachineFactory){
                NanoMachineFactory factory=(NanoMachineFactory)m.block;int front=(m.block.size+1)/2,f=x*Geometry.d4x(m.rotation)+y*Geometry.d4y(m.rotation);
                if(f>=front&&f<front+factory.clearanceDepth()&&Math.abs(-x*Geometry.d4y(m.rotation)+y*Geometry.d4x(m.rotation))<=factory.clearanceHalfWidth())return true;
            }
        }
        return false;
    }
    private static IntSeq route(Tile source,Tile root){
        IntQueue queue=new IntQueue();IntIntMap parents=new IntIntMap();IntSet seen=new IntSet();
        queue.addLast(source.pos());seen.add(source.pos());
        Tile end=null;
        while(queue.size>0&&seen.size<2048){
            Tile current=Vars.world.tile(queue.removeFirst());
            int ax=Math.abs(current.x-root.x),ay=Math.abs(current.y-root.y);
            if(current!=source&&((ax==3&&ay<=2)||(ay==3&&ax<=2))){end=current;break;}
            for(int d=0;d<4;d++){
                Tile next=current.nearby(d);
                if(!available(next)||planned(next,root)||reservedExit(next)||starterReserved(next)||!seen.add(next.pos()))continue;
                parents.put(next.pos(),current.pos());queue.addLast(next.pos());
            }
        }
        if(end==null)return null;
        IntSeq result=new IntSeq();int pos=end.pos();
        while(pos!=source.pos()){result.add(pos);pos=parents.get(pos,source.pos());}
        result.reverse();return result;
    }
    private static void place(Tile t,Block block,int rotation){
        t.setNet(block,DisasterFactions.nano,rotation);
        NanoFeedbackFx.event(NanoFeedbackFx.grow,t.drawx(),t.drawy(),block.size,1);
    }
}
