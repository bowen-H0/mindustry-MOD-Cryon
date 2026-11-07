package cryon.Features;

import arc.struct.*;
import arc.Events;
import arc.math.geom.Point2;
import cryon.Content.*;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.*;
import mindustry.io.SaveVersion;
import mindustry.io.SaveFileReader.CustomChunk;
import mindustry.world.*;
import mindustry.world.blocks.ConstructBlock.ConstructBuild;
import mindustry.game.EventType.BlockBuildBeginEvent;
import mindustry.game.EventType.BlockBuildEndEvent;
import mindustry.world.meta.BuildVisibility;
import java.io.*;

/** Durable colony blueprints. Native builders perform all construction and dismantling. */
public final class NanoConstruction{
    private static final IntMap<BuildPlan> blueprints=new IntMap<>();
    private static final IntSet founders=new IntSet();
    private static final IntSet occupied=new IntSet(),claimed=new IntSet(),rejected=new IntSet(),reachSeen=new IntSet();
    private static final IntQueue reachQueue=new IntQueue();
    private static final ObjectMap<Block,Seq<Tile>> siteCache=new ObjectMap<>();
    private static boolean spatialDirty=true;
    private static long revision;
    public static long revision(){return revision;}
    private static void changed(){spatialDirty=true;siteCache.clear();revision++;}
    private static boolean installed;
    public static void install(){
        if(installed)return;installed=true;
        Events.on(BlockBuildEndEvent.class,e->{if(e.team==DisasterFactions.nano)changed();});
        Events.on(BlockBuildBeginEvent.class,e->{
            if(e.team==DisasterFactions.nano&&e.tile.build instanceof ConstructBuild){
                ConstructBuild site=(ConstructBuild)e.tile.build;
                site.buildCost=Math.max(site.buildCost,120f*site.current.size);
            }
        });
        SaveVersion.addCustomChunk("cryon-nano-blueprints",new CustomChunk(){
            public void write(DataOutput out)throws IOException{
                out.writeInt(2);out.writeInt(blueprints.size);
                for(BuildPlan p:blueprints.values()){
                    out.writeInt(p.tile().pos());out.writeBoolean(p.breaking);
                    out.writeUTF(p.breaking?"":p.block.name);out.writeByte(p.rotation);
                    if(p.config instanceof Boolean){out.writeByte(1);out.writeBoolean((Boolean)p.config);}
                    else if(p.config instanceof Integer){out.writeByte(2);out.writeInt((Integer)p.config);}
                    else if(p.config instanceof Point2){out.writeByte(3);out.writeInt(((Point2)p.config).pack());}
                    else out.writeByte(0);
                }
                out.writeInt(founders.size);for(int pos:founders.iterator().toArray().toArray())out.writeInt(pos);
            }
            public void read(DataInput in)throws IOException{
                blueprints.clear();founders.clear();
                int version=in.readInt();if(version!=1&&version!=2)throw new IOException("Unknown nano blueprint version");
                int count=in.readInt();if(count<0||count>20000)throw new IOException("Invalid nano blueprint count");
                for(int i=0;i<count;i++){
                    int pos=in.readInt();boolean breaking=in.readBoolean();String name=in.readUTF();
                    int rotation=in.readByte();Object config=null;
                    if(version==1){if(in.readBoolean())config=Boolean.TRUE;}
                    else{
                        int kind=in.readByte();
                        if(kind==1)config=in.readBoolean();else if(kind==2)config=in.readInt();
                        else if(kind==3)config=Point2.unpack(in.readInt());else if(kind!=0)throw new IOException("Invalid nano blueprint config");
                    }
                    Tile t=Vars.world.tile(pos);Block b=Vars.content.block(name);
                    if(t!=null&&!RetiredNanoLogistics.removed(b)&&(breaking||b!=null))blueprints.put(pos,breaking?new BuildPlan(t.x,t.y):new BuildPlan(t.x,t.y,rotation,b,config));
                }
                int seeds=in.readInt();if(seeds<0||seeds>20000)throw new IOException("Invalid nano founder count");
                for(int i=0;i<seeds;i++)founders.add(in.readInt());
                changed();
            }
        });
    }
    public static void clear(){blueprints.clear();founders.clear();occupied.clear();claimed.clear();changed();}
    public static int count(Block block){return sites(block).size;}
    public static Seq<Tile> sites(Block block){
        Seq<Tile> cached=siteCache.get(block);if(cached!=null)return cached;
        Seq<Tile> sites=new Seq<>();
        for(BuildPlan p:blueprints.values())if(!p.breaking&&p.block==block&&p.tile()!=null)sites.add(p.tile());
        for(Building b:DisasterFactions.nano.data().buildings)if(b.block==block&&b.isValid()&&!sites.contains(b.tile))sites.add(b.tile);
        siteCache.put(block,sites);return sites;
    }
    public static boolean reserved(Tile tile){
        if(tile==null)return true;
        if(spatialDirty){
            occupied.clear();
            for(BuildPlan p:blueprints.values())if(!p.breaking){
                int off=-(p.block.size-1)/2;
                for(int x=0;x<p.block.size;x++)for(int y=0;y<p.block.size;y++)occupied.add(Point2.pack(p.x+off+x,p.y+off+y));
            }
            spatialDirty=false;
        }
        return occupied.contains(tile.pos());
    }
    public static void request(Tile t,Block block,int rotation){request(t,block,rotation,null);}
    public static Block intended(Tile t){BuildPlan p=blueprints.get(t.pos());return p==null||p.breaking?null:p.block;}
    public static void request(Tile t,Block block,int rotation,Object config){
        if(t==null||Vars.net.client()||blueprints.size>=12000)return;
        BuildPlan old=blueprints.get(t.pos());
        if(old!=null&&!old.breaking&&old.block!=block)return;
        if(old!=null&&!old.breaking&&old.block==block&&old.rotation==rotation&&java.util.Objects.equals(old.config,config))return;
        blueprints.put(t.pos(),new BuildPlan(t.x,t.y,rotation,block,config));
        changed();
    }
    public static void withdraw(Tile t){
        if(t==null)return;
        BuildPlan old=blueprints.get(t.pos());if(old!=null&&old.breaking&&t.build!=null&&t.team()==DisasterFactions.nano)return;
        blueprints.remove(t.pos());
        if(t.build!=null&&t.team()==DisasterFactions.nano)blueprints.put(t.pos(),new BuildPlan(t.x,t.y));
        changed();
    }
    public static void seed(Tile ore){
        // Only the original founders germinate for free. Replacements consume actual mined material.
        if(ore==null||Groups.unit.count(u->u.team==DisasterFactions.nano&&u.type==NanoColonyContent.weaver)>=24)return;
        for(int pos:founders.iterator().toArray().toArray()){Tile t=Vars.world.tile(pos);if(t!=null&&t.within(ore,48*Vars.tilesize))return;}
        founders.add(ore.pos());spawn(ore,true);spawn(ore,true);
    }
    private static void spawn(Tile tile,boolean founder){
        Unit u=NanoColonyContent.weaver.create(DisasterFactions.nano);
        if(founder){u.stack.item=CryonItems.nanoMaterial;u.stack.amount=60;}
        float angle=u.id*137.5f;
        u.set(tile.worldx()+arc.math.Mathf.cosDeg(angle)*32f,tile.worldy()+arc.math.Mathf.sinDeg(angle)*32f);u.add();
    }
    public static void step(){
        // Also notice manual editor placement/removal, which does not fire construction events.
        siteCache.clear();revision++;
        // Finished demolition has no rebuild intent; do not accumulate years of obsolete tasks.
        boolean pruned=false;
        var entries=blueprints.entries();
        while(entries.hasNext()){
            BuildPlan p=entries.next().value;
            if(p.breaking&&(p.tile()==null||p.tile().block()==Blocks.air)){entries.remove();pruned=true;}
        }
        if(pruned)changed();
        Seq<Building> sources=new Seq<>();
        for(Building b:DisasterFactions.nano.data().buildings){
            if(!b.isValid()||NanoEnvironment.suppressed(b.tile)||b.tile.floor()!=NanoEnvironment.floor)continue;
            if(b.block==NanoColonyContent.drill||b.block==NanoColonyContent.warehouse)sources.add(b);
        }
        for(Building b:sources){
            int nearby=Groups.unit.count(u->u.team==b.team&&u.type==NanoColonyContent.weaver&&!u.dead&&u.within(b,40*Vars.tilesize));
            if(nearby<3&&b.items!=null&&b.items.get(CryonItems.nanoMaterial)>=30&&Groups.unit.count(u->u.team==b.team&&u.type==NanoColonyContent.weaver)<24){
                b.items.remove(CryonItems.nanoMaterial,30);spawn(b.tile,false);break;
            }
        }
        // Adopt older saves without a blueprint chunk; completed structures can also be reconstructed.
        for(Building b:DisasterFactions.nano.data().buildings){
            if(b.isValid()&&!(b instanceof ConstructBuild)&&!blueprints.containsKey(b.pos())&&DatabaseSections.nano.equals(DatabaseSections.category(b.block))&&b.block!=NanoEnvironment.wall)
                request(b.tile,b.block,b.rotation,b.config());
        }
    }
    public static boolean unfinished(BuildPlan p){
        Tile t=p.tile();if(t==null)return false;
        if(p.breaking)return t.build!=null&&t.team()==DisasterFactions.nano;
        return !(t.block()==p.block&&t.team()==DisasterFactions.nano&&(!p.block.rotate||t.build.rotation==p.rotation));
    }
    public static boolean current(BuildPlan p){
        BuildPlan wanted=blueprints.get(p.tile().pos());
        return wanted!=null&&wanted.breaking==p.breaking&&(p.breaking||(wanted.block==p.block&&wanted.rotation==p.rotation&&java.util.Objects.equals(wanted.config,p.config)));
    }
    public static boolean retiring(Building b){
        BuildPlan p=blueprints.get(b.pos());return p!=null&&p.breaking;
    }
    private static boolean walkable(Tile t){return t!=null&&!t.floor().isDeep()&&!t.legSolid();}
    public static boolean reachable(Unit worker,arc.math.geom.Position target,float range){
        if(worker.within(target,range))return true;
        int tx=mindustry.core.World.toTile(target.getX()),ty=mindustry.core.World.toTile(target.getY());
        if(!mindustry.core.World.raycast(worker.tileX(),worker.tileY(),tx,ty,(x,y)->!walkable(Vars.world.tile(x,y))))return true;
        reachQueue.clear();reachSeen.clear();Tile origin=worker.tileOn();if(origin==null)return false;
        reachQueue.addLast(origin.pos());reachSeen.add(origin.pos());
        int visited=0;
        while(reachQueue.size>0&&visited++<512){
            Tile t=Vars.world.tile(reachQueue.removeFirst());
            if(t.within(target,range))return true;
            for(int d=0;d<4;d++){Tile n=t.nearby(d);if(walkable(n)&&reachSeen.add(n.pos()))reachQueue.addLast(n.pos());}
        }
        return false;
    }
    public static BuildPlan next(Unit worker){
        claimed.clear();rejected.clear();
        for(Unit u:Groups.unit)if(u!=worker&&u.team==worker.team&&u.type==worker.type&&!u.dead&&u.buildPlan()!=null){
            BuildPlan p=u.buildPlan();claimed.add(Point2.pack(p.x,p.y));
        }
        for(int attempt=0;attempt<4;attempt++){
          BuildPlan best=null;float score=Float.MAX_VALUE;
          for(BuildPlan p:blueprints.values()){
            Tile t=p.tile();if(!unfinished(p)||t==null||NanoEnvironment.suppressed(t))continue;
            if(!p.breaking&&t.block()!=Blocks.air&&!(t.build instanceof ConstructBuild)&&t.block()!=p.block)continue;
            if(!p.breaking&&t.floor()!=NanoEnvironment.floor)continue;
            if(claimed.contains(t.pos())||rejected.contains(t.pos()))continue;
            float priority=p.breaking?0.8f:p.block==NanoColonyContent.drill?0.45f:p.block==NanoColonyContent.warehouse?0.65f:(p.block.name.contains("duct")||p.block==NanoColonyContent.bridge)?0.3f:1f;
            float cost=worker.dst2(t)*priority;
            if(cost<score){score=cost;best=p;}
          }
          if(best==null)return null;
          if(reachable(worker,best,worker.type.buildRange))return best.breaking?new BuildPlan(best.x,best.y):new BuildPlan(best.x,best.y,best.rotation,best.block,best.config);
          rejected.add(best.tile().pos());
        }
        return null;
    }
    public static float build(Unit worker){
        BuildPlan p=worker.buildPlan();if(p==null||!worker.within(p,worker.type.buildRange))return 0;
        Tile tile=p.tile();
        Block target=p.breaking?(tile.build instanceof ConstructBuild?((ConstructBuild)tile.build).current:tile.block()):p.block;
        float before=tile.build instanceof ConstructBuild?((ConstructBuild)tile.build).progress:p.breaking?1f:0f;
        boolean rotation=!p.breaking&&tile.block()==p.block;
        // Sandbox-only colony structures remain hidden from the player's construction menu.
        // Only this synchronous native builder call can see the faction's construction recipes.
        Block block=p.breaking?null:p.block;
        BuildVisibility old=block==null?null:block.buildVisibility;
        boolean unlocked=block!=null&&block.alwaysUnlocked;
        boolean teamInfinite=worker.team.rules().infiniteResources;
        boolean worldInfinite=Vars.state.rules.infiniteResources;
        try{
            // Bypass only the native mandatory-core check; carried nano pays for the actual work.
            // Sandbox's global infinite-resource rule would otherwise finish a site in one tick.
            worker.team.rules().infiniteResources=true;Vars.state.rules.infiniteResources=false;
            if(block!=null){block.buildVisibility=BuildVisibility.shown;block.alwaysUnlocked=true;}
            worker.updateBuilding(true);worker.updateBuildLogic();
        }finally{
            worker.updateBuilding(false);
            worker.team.rules().infiniteResources=teamInfinite;Vars.state.rules.infiniteResources=worldInfinite;
            if(block!=null){block.buildVisibility=old;block.alwaysUnlocked=unlocked;}
        }
        float after=tile.build instanceof ConstructBuild?((ConstructBuild)tile.build).progress:p.breaking?0f:tile.block()==target?1f:0f;
        return rotation?0f:p.breaking?-(before-after)*target.size*target.size:(after-before)*2f*target.size*target.size;
    }
}
