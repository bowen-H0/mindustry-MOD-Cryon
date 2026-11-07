package cryon.Features;

import arc.math.geom.Geometry;
import arc.math.geom.Point2;
import arc.struct.*;
import arc.util.Time;
import cryon.Content.*;
import cryon.Type.*;
import cryon.Type.NanoWarehouse.NanoWarehouseBuild;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.gen.Building;
import mindustry.world.blocks.distribution.ItemBridge.ItemBridgeBuild;
import mindustry.world.*;

/** Distributed vein districts supplied entirely by native pipes and linked item bridges. */
public final class NanoIndustry{
    private static final Seq<NanoWarehouseBuild> hubs=new Seq<>();
    private static Seq<Module> modules;
    private static final IntSet exits=new IntSet(),starters=new IntSet(),districts=new IntSet(),supplyCells=new IntSet();
    private static final IntMap<IntSet> districtMasks=new IntMap<>();
    private static final IntIntMap directions=new IntIntMap(),links=new IntIntMap();
    private static long reservationRevision=-1;
    private static boolean reservationsDirty=true;
    private static int routeSearches,hubCandidateCursor;
    private static final int maxRouteSearches=4;
    private static final IntFloatMap routeRetry=new IntFloatMap(),hubRetry=new IntFloatMap();

    private static class Module{
        Block block;int x,y,rotation;
        Module(Block b,int x,int y,int rotation){block=b;this.x=x;this.y=y;this.rotation=rotation;}
    }
    private static Seq<Module> modules(){
        if(modules==null)modules=Seq.with(
            new Module(NanoColonyContent.largeFactory,0,6,1),new Module(NanoColonyContent.unloader,0,3,1),
            new Module(NanoColonyContent.combatFactory,6,0,0),new Module(NanoColonyContent.unloader,3,0,0),
            new Module(NanoColonyContent.ultimateFactory,0,-7,3),new Module(NanoColonyContent.unloader,0,-3,3),
            new Module(NanoColonyContent.cleaver,-6,-6,0),new Module(NanoColonyContent.unloader,-3,-2,2),
            new Module(belt(),-4,-2,3),new Module(belt(),-4,-3,3),new Module(belt(),-4,-4,3),new Module(belt(),-4,-5,2));
        return modules;
    }
    private static Block belt(){return Vars.content.block("cryon-reinforced-vacuum-duct");}
    public static void clear(){hubs.clear();districtMasks.clear();reservationsDirty=true;reservationRevision=-1;routeRetry.clear();hubRetry.clear();hubCandidateCursor=0;}
    public static boolean nearHub(Tile t,float radius){return NanoConstruction.sites(NanoColonyContent.warehouse).contains(h->h.within(t,radius*Vars.tilesize));}
    public static boolean feeder(Building b){return b instanceof NanoColonyDrill.NanoDrillBuild&&((NanoColonyDrill.NanoDrillBuild)b).feeder;}
    public static void step(Seq<Building> drills){
        reservationsDirty=true;routeSearches=0;
        hubs.clear();
        for(Building b:DisasterFactions.nano.data().buildings)if(b instanceof NanoWarehouseBuild&&b.isValid())hubs.add((NanoWarehouseBuild)b);
        for(NanoWarehouseBuild hub:hubs){
            if(!hub.supplyRoute.isEmpty()&&(!consistent(hub.supplyRoute,hub.tile)||obstructed(hub.supplyRoute))){
                IntSeq old=new IntSeq(hub.supplyRoute);hub.supplyRoute.clear();reservationsDirty=true;hubRetry.remove(hub.pos(),0f);
                for(int i=0;i<old.size;i++)if(!sharedRoute(old.get(i),hub))NanoConstruction.withdraw(Vars.world.tile(old.get(i)));
            }
            if(hub.supplyRoute.isEmpty()&&Time.time>=hubRetry.get(hub.pos(),-Float.MAX_VALUE)){
                Seq<Building> nearby=new Seq<>(drills);nearby.sort((a,b)->Float.compare(a.dst2(hub),b.dst2(hub)));
                for(Building drill:nearby){
                    if(routeSearches>=maxRouteSearches)break;
                    if(!drill.within(hub,128*Vars.tilesize))continue;
                    IntSeq bootstrap=route(drill.tile,hub.tile);
                    if(bootstrap!=null){hub.supplyPos=drill.pos();hub.supplyRoute.addAll(bootstrap);reservationsDirty=true;break;}
                }
                if(hub.supplyRoute.isEmpty())hubRetry.put(hub.pos(),Time.time+300f+Math.floorMod(hub.pos(),120));
            }
            growRoute(hub);
            // Establish a funded supply line before spending the founders on heavy industry.
            if(supplyReady(hub.supplyRoute)||hub.items.get(CryonItems.nanoMaterial)>0)growDistrict(hub);
        }
        discoverDistrict(drills);
        for(Building drill:drills)if(feeder(drill))growFeeder(drill);
    }
    private static void discoverDistrict(Seq<Building> drills){
        int initialSearches=routeSearches;
        // Each remote vein grows its own district, independent of the first warehouse's wealth.
        if(NanoConstruction.sites(NanoColonyContent.warehouse).size>=8)return;
        for(Building drill:drills)if(!feeder(drill)&&!nearHub(drill.tile,44f)){
            int start=hubCandidateCursor;
            for(int attempt=0;attempt<32;attempt++){
                if(routeSearches>=maxRouteSearches||routeSearches>initialSearches){hubCandidateCursor=(start+attempt)%32;return;}
                int candidate=(start+attempt)%32,dir=candidate%8,radius=14+4*(candidate/8);
                int dx=arc.math.Mathf.round(arc.math.Mathf.cosDeg(dir*45f)),dy=arc.math.Mathf.round(arc.math.Mathf.sinDeg(dir*45f));
                Tile center=Vars.world.tile(drill.tile.x+dx*radius,drill.tile.y+dy*radius);
                if(tryHub(drill,center)){hubCandidateCursor=(candidate+1)%32;return;}
            }

        }
    }

    private static boolean tryHub(Building source,Tile center){
        if(center==null||nearHub(center,40f)||!fits(center,NanoColonyContent.warehouse))return false;
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
        if(route==null)return false;
        place(center,NanoColonyContent.warehouse,0);
        return true;
    }
    private static void growRoute(NanoWarehouseBuild hub){growSupply(hub.supplyRoute,hub.tile,hub);}
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
        // Only reserve the drill and its first output: the actual route is terrain-aware.
        return available(origin)&&!reservedSite(origin)&&available(at(origin,rotation,1))&&!reservedSite(at(origin,rotation,1));
    }
    private static void growFeeder(Building building){
        if(!building.isValid()||NanoEnvironment.suppressed(building.tile))return;
        NanoColonyDrill.NanoDrillBuild drill=(NanoColonyDrill.NanoDrillBuild)building;
        NanoWarehouseBuild hub=hubs.min(h->h.dst2(drill));if(hub==null)return;
        if(!drill.supplyRoute.isEmpty()&&!consistent(drill.supplyRoute,hub.tile))abandon(drill);
        boolean blocked=obstructed(drill.supplyRoute);
        if((drill.supplyTarget!=hub.pos()||drill.supplyRoute.isEmpty()||blocked)&&routeSearches<maxRouteSearches&&Time.time>=routeRetry.get(drill.pos(),-Float.MAX_VALUE)){
            IntSeq path=route(drill.tile,hub.tile,drill.supplyRoute);
            if(path!=null&&path.size<=256){
                routeRetry.remove(drill.pos(),0f);
                IntSeq old=new IntSeq(drill.supplyRoute);drill.supplyRoute.clear();drill.supplyRoute.addAll(path);drill.supplyTarget=hub.pos();reservationsDirty=true;
                for(int i=0;i<old.size;i++){int pos=old.get(i);if(!path.contains(pos)&&!sharedRoute(pos,drill))NanoConstruction.withdraw(Vars.world.tile(pos));}
            }else{
                routeRetry.put(drill.pos(),Time.time+300f+Math.floorMod(drill.pos(),120));
                if(blocked||drill.supplyTarget!=hub.pos())abandon(drill);
            }
        }
        if(!drill.supplyRoute.isEmpty()&&drill.supplyTarget==hub.pos())growSupply(drill.supplyRoute,hub.tile,drill);
    }
    private static void abandon(NanoColonyDrill.NanoDrillBuild drill){
        for(int pos:drill.supplyRoute.toArray())if(!sharedRoute(pos,drill))NanoConstruction.withdraw(Vars.world.tile(pos));
        drill.supplyRoute.clear();drill.supplyTarget=-1;reservationsDirty=true;
    }
    private static boolean sharedRoute(int pos,Building except){
        for(NanoWarehouseBuild hub:hubs)if(hub!=except&&hub.supplyRoute.contains(pos))return true;
        for(Building b:DisasterFactions.nano.data().buildings)if(b!=except&&b instanceof NanoColonyDrill.NanoDrillBuild&&((NanoColonyDrill.NanoDrillBuild)b).supplyRoute.contains(pos))return true;
        return false;
    }
    private static boolean adjacent(Tile a,Tile b){return a!=null&&b!=null&&Math.abs(a.x-b.x)+Math.abs(a.y-b.y)==1;}
    private static boolean transport(Tile t){return t!=null&&t.team()==DisasterFactions.nano&&(t.block()==belt()||t.block()==NanoColonyContent.bridge);}
    private static boolean bridgeAt(IntSeq path,int index){
        Tile t=Vars.world.tile(path.get(index));
        return t!=null&&(t.block()==NanoColonyContent.bridge||links.containsKey(t.pos())||(index>0&&!adjacent(Vars.world.tile(path.get(index-1)),t))||(index+1<path.size&&!adjacent(t,Vars.world.tile(path.get(index+1)))));
    }
    private static boolean obstructed(IntSeq path){
        for(int i=0;i<path.size;i++){
            Tile t=Vars.world.tile(path.get(i));
            if(t==null||t.floor()!=NanoEnvironment.floor||NanoEnvironment.suppressed(t)||(!transport(t)&&t.block()!=Blocks.air&&!(t.build instanceof mindustry.world.blocks.ConstructBlock.ConstructBuild)))return true;
        }
        return false;
    }
    private static void rememberRoute(IntSeq path,Tile destination){
        for(int i=0;i<path.size;i++){
            Tile t=Vars.world.tile(path.get(i)),next=i+1<path.size?Vars.world.tile(path.get(i+1)):destination;
            if(t==null||next==null)continue;supplyCells.add(t.pos());
            if(directions.containsKey(t.pos())||links.containsKey(t.pos()))continue;
            if(bridgeAt(path,i))links.put(t.pos(),i+1<path.size&&bridgeAt(path,i+1)?next.pos():-1);
            if(adjacent(t,next)||i+1==path.size)directions.put(t.pos(),i+1<path.size?t.relativeTo(next.x,next.y):t.absoluteRelativeTo(next.x,next.y));
        }
    }
    private static boolean consistent(IntSeq path,Tile destination){
        ensureReservations();
        for(int i=0;i<path.size;i++){
            Tile t=Vars.world.tile(path.get(i)),next=i+1<path.size?Vars.world.tile(path.get(i+1)):destination;
            if(t==null||next==null)return false;
            if(links.containsKey(t.pos())&&links.get(t.pos(),-1)>=0){if(i+1==path.size||links.get(t.pos(),-1)!=next.pos())return false;}
            else if(directions.containsKey(t.pos())){
                if(i+1<path.size&&!adjacent(t,next))return false;
                int rotation=i+1<path.size?t.relativeTo(next.x,next.y):t.absoluteRelativeTo(next.x,next.y);
                if(rotation!=directions.get(t.pos(),-1))return false;
            }
        }
        return true;
    }
    private static boolean supplyReady(IntSeq path){
        if(path.isEmpty())return false;
        for(int i=0;i<path.size;i++){
            Tile t=Vars.world.tile(path.get(i));if(!transport(t))return false;
            if(t.block()==NanoColonyContent.bridge&&i+1<path.size&&bridgeAt(path,i+1)&&((ItemBridgeBuild)t.build).link!=path.get(i+1))return false;
        }
        return true;
    }
    private static boolean growSupply(IntSeq path,Tile destination,Building owner){
        for(int i=path.size-1;i>=0;i--){
            Tile t=Vars.world.tile(path.get(i)),next=i+1<path.size?Vars.world.tile(path.get(i+1)):destination;
            if(t==null||next==null)return false;
            boolean bridge=bridgeAt(path,i);Block wanted=bridge?NanoColonyContent.bridge:belt();
            int rotation=bridge?0:i+1<path.size?t.relativeTo(next.x,next.y):t.absoluteRelativeTo(next.x,next.y);
            Integer link=bridge?(i+1<path.size&&bridgeAt(path,i+1)?next.pos():-1):null;
            Block planned=NanoConstruction.intended(t);
            if((t.block()==Blocks.air||t.block()==wanted)&&planned!=null&&planned!=wanted&&!sharedRoute(t.pos(),owner))NanoConstruction.withdraw(t);
            if(t.block()==wanted&&t.team()==DisasterFactions.nano&&(!wanted.rotate||t.build.rotation==rotation)){
                if(bridge&&((ItemBridgeBuild)t.build).link!=link)t.build.configureAny(link);
                NanoConstruction.request(t,wanted,rotation,link);continue;
            }
            if(transport(t)&&t.block()!=wanted){if(!sharedRoute(t.pos(),owner))NanoConstruction.withdraw(t);return false;}
            if(available(t)||(t.block()==wanted&&t.team()==DisasterFactions.nano))NanoConstruction.request(t,wanted,rotation,link);
            return false;
        }
        return true;
    }
    private static Tile at(Tile origin,int rotation,int forward){return Vars.world.tile(origin.x+Geometry.d4x(rotation)*forward,origin.y+Geometry.d4y(rotation)*forward);}
    private static boolean available(Tile t){return t!=null&&t.block()==Blocks.air&&!NanoConstruction.reserved(t)&&t.floor()==NanoEnvironment.floor&&t.floor().hasSurface()&&!NanoEnvironment.suppressed(t);}
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
    private static void footprint(IntSet cells,int cx,int cy,int size){
        int offset=-(size-1)/2;
        for(int x=0;x<size;x++)for(int y=0;y<size;y++)cells.add(Point2.pack(cx+offset+x,cy+offset+y));
    }
    private static void corridor(IntSet cells,int x,int y,NanoMachineFactory factory,int rotation){
        int front=(factory.size+1)/2;
        for(int f=front;f<front+factory.clearanceDepth();f++)for(int side=-factory.clearanceHalfWidth();side<=factory.clearanceHalfWidth();side++)
            cells.add(Point2.pack(x+Geometry.d4x(rotation)*f-Geometry.d4y(rotation)*side,y+Geometry.d4y(rotation)*f+Geometry.d4x(rotation)*side));
    }
    private static IntSet districtMask(Tile root){
        IntSet cached=districtMasks.get(root.pos());if(cached!=null)return cached;
        IntSet cells=new IntSet();footprint(cells,root.x,root.y,5);
        for(Module m:modules()){
            footprint(cells,root.x+m.x,root.y+m.y,m.block.size);
            if(m.block instanceof NanoMachineFactory)corridor(cells,root.x+m.x,root.y+m.y,(NanoMachineFactory)m.block,m.rotation);
        }
        districtMasks.put(root.pos(),cells);return cells;
    }
    private static void ensureReservations(){
        long revision=NanoConstruction.revision();
        if(!reservationsDirty&&reservationRevision==revision)return;
        reservationsDirty=false;reservationRevision=revision;
        exits.clear();starters.clear();districts.clear();supplyCells.clear();districtMasks.clear();directions.clear();links.clear();
        for(Tile root:NanoConstruction.sites(NanoColonyContent.warehouse)){
            districts.addAll(districtMask(root));
            for(Module m:modules())if(m.block instanceof NanoMachineFactory)corridor(exits,root.x+m.x,root.y+m.y,(NanoMachineFactory)m.block,m.rotation);
        }
        for(Building b:DisasterFactions.nano.data().buildings)if(b instanceof NanoWarehouseBuild&&b.isValid())rememberRoute(((NanoWarehouseBuild)b).supplyRoute,b.tile);
        for(Building b:DisasterFactions.nano.data().buildings){
            if(!b.isValid())continue;
            if(b.block instanceof NanoMachineFactory)corridor(exits,b.tile.x,b.tile.y,(NanoMachineFactory)b.block,b.rotation);
            if(b instanceof NanoWarehouseBuild){
                IntSeq route=((NanoWarehouseBuild)b).supplyRoute;for(int i=0;i<route.size;i++)supplyCells.add(route.get(i));
            }
            if(b.block!=NanoColonyContent.drill)continue;
            NanoColonyDrill.NanoDrillBuild drill=(NanoColonyDrill.NanoDrillBuild)b;
            Tile destination=Vars.world.tile(drill.supplyTarget);if(destination!=null)rememberRoute(drill.supplyRoute,destination);
            int r=b.rotation,dx=Geometry.d4x(r),dy=Geometry.d4y(r),x=b.tile.x,y=b.tile.y;
            for(int f=0;f<=(feeder(b)?0:5);f++)starters.add(Point2.pack(x+dx*f,y+dy*f));
            if(feeder(b))continue;
            for(int f=6;f<=11;f++)for(int side=-1;side<=1;side++)starters.add(Point2.pack(x+dx*f-dy*side,y+dy*f+dx*side));
            for(int f=-2;f<=8;f+=2)for(int side:new int[]{-3,3})starters.add(Point2.pack(x+dx*f-dy*side,y+dy*f+dx*side));
        }
    }
    public static boolean reservedExit(Tile t){if(t==null)return true;ensureReservations();return exits.contains(t.pos());}
    public static boolean reservedSite(Tile t){
        if(t==null)return true;ensureReservations();int pos=t.pos();
        return NanoConstruction.reserved(t)||districts.contains(pos)||supplyCells.contains(pos)||exits.contains(pos)||starters.contains(pos);
    }
    private static boolean starterReserved(Tile t){if(t==null)return true;ensureReservations();return starters.contains(t.pos());}
    private static boolean planned(Tile t,Tile root){ensureReservations();return districtMask(root).contains(t.pos());}
    private static IntSeq route(Tile source,Tile root){return route(source,root,new IntSeq());}
    private static class RouteNode implements Comparable<RouteNode>{
        final int pos,cost,remaining;
        RouteNode(int pos,int cost,int remaining){this.pos=pos;this.cost=cost;this.remaining=remaining;}
        @Override public int compareTo(RouteNode other){
            int order=Integer.compare(cost+remaining,other.cost+other.remaining);
            if(order==0)order=Integer.compare(remaining,other.remaining);
            return order==0?Integer.compare(pos,other.pos):order;
        }
    }
    private static int remaining(Tile t,Tile root){return Math.max(0,Math.abs(t.x-root.x)-3)+Math.max(0,Math.abs(t.y-root.y)-3);}
    private static boolean routingCell(Tile next,Tile source,Tile root,IntSeq reusable){
        if(next==null||next.floor()!=NanoEnvironment.floor||!next.floor().hasSurface()||NanoEnvironment.suppressed(next)||planned(next,root)||reservedExit(next))return false;
        boolean own=reusable.contains(next.pos()),existing=transport(next);
        boolean building=next.team()==DisasterFactions.nano&&next.build instanceof mindustry.world.blocks.ConstructBlock.ConstructBuild&&(((mindustry.world.blocks.ConstructBlock.ConstructBuild)next.build).current==belt()||((mindustry.world.blocks.ConstructBlock.ConstructBuild)next.build).current==NanoColonyContent.bridge);
        return (available(next)||own||existing)&&(next.block()==Blocks.air||existing||building)&&(own||!starterReserved(next)||ownFeeder(next,source));
    }
    private static void offer(Tile next,Tile current,Tile root,int cost,IntIntMap costs,IntIntMap parents,java.util.PriorityQueue<RouteNode> queue){
        if(cost>=costs.get(next.pos(),Integer.MAX_VALUE))return;
        costs.put(next.pos(),cost);parents.put(next.pos(),current.pos());queue.add(new RouteNode(next.pos(),cost,remaining(next,root)));
    }
    private static IntSeq route(Tile source,Tile root,IntSeq reusable){
        if(routeSearches>=maxRouteSearches)return null;
        routeSearches++;
        java.util.PriorityQueue<RouteNode> queue=new java.util.PriorityQueue<>();
        IntIntMap parents=new IntIntMap(),costs=new IntIntMap();IntSet seen=new IntSet();
        queue.add(new RouteNode(source.pos(),0,remaining(source,root)));costs.put(source.pos(),0);
        Tile end=null;
        while(!queue.isEmpty()&&seen.size<2048){
            RouteNode node=queue.remove();
            if(node.cost!=costs.get(node.pos,Integer.MAX_VALUE)||!seen.add(node.pos))continue;
            Tile current=Vars.world.tile(node.pos);boolean own=reusable.contains(current.pos());
            ItemBridgeBuild existing=current.block()==NanoColonyContent.bridge&&current.team()==DisasterFactions.nano?(ItemBridgeBuild)current.build:null;
            Tile linked=existing==null?null:Vars.world.tile(existing.link);
            int fixedLink=links.get(current.pos(),-1);
            if(fixedLink>=0)linked=Vars.world.tile(fixedLink);
            boolean fixed=fixedLink>=0||(existing!=null&&!own&&NanoColonyContent.bridge.linkValid(current,linked));
            int ax=Math.abs(current.x-root.x),ay=Math.abs(current.y-root.y);
            if(current!=source&&!fixed&&((ax==3&&ay<=2)||(ay==3&&ax<=2))){
                int output=current.absoluteRelativeTo(root.x,root.y);
                if((!directions.containsKey(current.pos())||directions.get(current.pos(),-1)==output)&&(current.block()!=belt()||own||directions.containsKey(current.pos())||current.build.rotation==output)){end=current;break;}
            }
            if(fixed){
                if(routingCell(linked,source,root,reusable)&&!seen.contains(linked.pos()))offer(linked,current,root,node.cost+Math.abs(linked.x-current.x)+Math.abs(linked.y-current.y)+3,costs,parents,queue);
                continue;
            }
            for(int d=0;d<4;d++){
                if(current!=source&&directions.containsKey(current.pos())&&d!=directions.get(current.pos(),-1))continue;
                if(current!=source&&!directions.containsKey(current.pos())&&current.block()==belt()&&current.team()==DisasterFactions.nano&&d!=current.build.rotation&&!own)continue;
                Tile next=current.nearby(d);
                if(routingCell(next,source,root,reusable)&&!seen.contains(next.pos()))offer(next,current,root,node.cost+1,costs,parents,queue);
                // Bridges only launch from their own land endpoint, never from a drill or an existing belt.
                if(current==source||(current.block()!=Blocks.air&&existing==null)||((NanoConstruction.reserved(current)||supplyCells.contains(current.pos()))&&existing==null))continue;
                boolean gap=next==null||next.floor()!=NanoEnvironment.floor||!next.floor().hasSurface()||next.block()!=Blocks.air;
                for(int distance=2;distance<=NanoColonyContent.bridge.range;distance++){
                    Tile landing=Vars.world.tile(current.x+Geometry.d4x(d)*distance,current.y+Geometry.d4y(d)*distance);
                    if(landing==null)break;
                    if(gap&&landing.block()!=belt()&&(!supplyCells.contains(landing.pos())||landing.block()==NanoColonyContent.bridge)&&routingCell(landing,source,root,reusable)&&!seen.contains(landing.pos()))offer(landing,current,root,node.cost+distance+3,costs,parents,queue);
                    if(landing.floor()!=NanoEnvironment.floor||!landing.floor().hasSurface()||landing.block()!=Blocks.air)gap=true;
                }
            }
        }
        if(end==null)return null;
        IntSeq result=new IntSeq();int pos=end.pos();
        while(pos!=source.pos()){result.add(pos);pos=parents.get(pos,source.pos());}
        result.reverse();return result;
    }
    private static boolean ownFeeder(Tile t,Tile source){
        if(source.build==null||!feeder(source.build))return false;
        int dx=t.x-source.x,dy=t.y-source.y,r=source.build.rotation;
        int f=dx*Geometry.d4x(r)+dy*Geometry.d4y(r),side=-dx*Geometry.d4y(r)+dy*Geometry.d4x(r);
        return f==1&&side==0;
    }
    private static void place(Tile t,Block block,int rotation){
        NanoConstruction.request(t,block,rotation);
    }
}
