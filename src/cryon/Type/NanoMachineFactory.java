package cryon.Type;

import arc.math.geom.Geometry;
import arc.util.Time;
import arc.util.io.*;
import cryon.Content.*;
import cryon.Features.DisasterFactions;
import cryon.Fx.NanoFeedbackFx;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.meta.*;
import static mindustry.Vars.*;

public class NanoMachineFactory extends Block{
    public float craftTime = 480f;
    public int cost = 12;
    public UnitType outputUnit;
    public int unitLimit = 128;
    public UnitType product(){return outputUnit==null?NanoColonyContent.eroder:outputUnit;}
    public float spawnDistance(){return size*tilesize/2f+Math.max(tilesize/2f,product().hitSize/2f+2f);}
    public int clearanceDepth(){return size<=3?1:Math.max(1,(int)Math.ceil((spawnDistance()+product().hitSize/2f+2f)/tilesize)-(size+1)/2+1);}
    public int clearanceHalfWidth(){return Math.max(size/2,(int)Math.ceil(product().hitSize/2f/tilesize));}
    public NanoMachineFactory(String name){
        super(name);
        requirements(Category.units, BuildVisibility.editorOnly, ItemStack.empty);
        size = 3; health = 2400; update = solid = hasItems = acceptsItems = rotate = true;
        itemCapacity = 120; buildType = NanoFactoryBuild::new;
    }
    @Override public boolean canPlaceOn(Tile tile,mindustry.game.Team team,int rotation){return team==DisasterFactions.nano&&super.canPlaceOn(tile,team,rotation);}
    public class NanoFactoryBuild extends Building{
        public float progress;
        @Override public boolean acceptItem(Building source, Item item){
            return (team == DisasterFactions.nano) && source.team == team && item == CryonItems.nanoMaterial && items.get(item) < itemCapacity;
        }
        public boolean frontClear(){
            int dx = Geometry.d4x(rotation), dy = Geometry.d4y(rotation);
            int front = (size + 1) / 2;
            for(int f=front;f<front+clearanceDepth();f++)for(int side = -clearanceHalfWidth(); side <= clearanceHalfWidth(); side++){
                Tile t = world.tile(tile.x + dx * f - dy * side, tile.y + dy * f + dx * side);
                if(t == null || t.block() != mindustry.content.Blocks.air || !t.floor().hasSurface()) return false;
            }
            return true;
        }
        @Override public void updateTile(){
            if(net.client() || !enabled || team != DisasterFactions.nano || !frontClear() || !items.has(CryonItems.nanoMaterial, cost)) return;
            UnitType product = product();
            if(Groups.unit.count(u -> u.team == team && !u.dead && u.type == product) >= unitLimit) return;
            float sx = x + Geometry.d4x(rotation) * spawnDistance(), sy = y + Geometry.d4y(rotation) * spawnDistance();
            if(Groups.unit.contains(u -> !u.dead && u.isFlying() == product.flying && u.within(sx, sy, product.hitSize * 0.6f + 5f))) return;
            progress += Time.delta;
            if(progress >= craftTime){
                progress %= craftTime; items.remove(CryonItems.nanoMaterial, cost);
                Unit created=product.spawn(team,sx,sy);created.rotation=rotdeg();
                NanoFeedbackFx.event(NanoFeedbackFx.grow, sx, sy, 2f, 1);
            }
        }
        @Override public void draw(){
            super.draw();
            if(enabled && (team == DisasterFactions.nano) && items.has(CryonItems.nanoMaterial, cost)){
                NanoFeedbackFx.ambient(frontClear() ? NanoFeedbackFx.charge : NanoFeedbackFx.warning, x, y, 0f, 22f);
            }
        }
        @Override public byte version(){ return 1; }
        @Override public void write(Writes w){ super.write(w); w.f(progress); }
        @Override public void read(Reads r, byte revision){ super.read(r, revision); progress = r.f(); }
    }
}
