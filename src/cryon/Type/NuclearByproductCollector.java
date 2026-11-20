package cryon.Type;

import arc.struct.Seq;
import cryon.Content.CryonItems;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.gen.Building;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.Block;
import mindustry.world.meta.*;
import static mindustry.type.ItemStack.with;

/** Automatically pulls only spent-fuel byproducts from nearby allied RBMK fuel rods. */
public class NuclearByproductCollector extends Block{
    public float range = 12f * Vars.tilesize, transportTime = 5f, scanInterval = 30f;

    public NuclearByproductCollector(String name){
        super(name);
        size = 2;
        health = 420;
        update = solid = hasItems = hasPower = true;
        itemCapacity = 40;
        allowConfigInventory = false;
        group = BlockGroup.transportation;
        envEnabled |= Env.space;
        consumePower(0.5f);
        requirements(Category.power, with(CryonItems.aluminum, 60, CryonItems.nickel, 25, mindustry.content.Items.silicon, 40));
        buildType = NuclearByproductCollectorBuild::new;
    }

    @Override public boolean outputsItems(){ return true; }
    @Override public void setStats(){
        super.setStats();
        stats.add(Stat.range, range / Vars.tilesize, StatUnit.blocks);
        stats.add(Stat.itemsMoved, 60f / transportTime, StatUnit.itemsSecond);
    }

    public class NuclearByproductCollectorBuild extends Building{
        public final Seq<RBMKReactor.ReactorPart.ReactorPartBuild> sources = new Seq<>();
        private float scanTimer, transportCounter;
        private int sourceIndex;

        @Override public boolean acceptItem(Building source, Item item){ return false; }

        public boolean sourceValid(RBMKReactor.ReactorPart.ReactorPartBuild source){
            return source != null && source.isValid() && source.team == team && within(source, range)
                    && source.block instanceof RBMKReactor.ReactorPart
                    && ((RBMKReactor.ReactorPart)source.block).kind == RBMKReactor.Kind.fuel
                    && source.props().byproductItem == CryonItems.nuclearByproduct;
        }

        public void scanSources(){
            sources.clear();
            int radius = (int)Math.ceil(range / Vars.tilesize) + 1;
            for(int dx = -radius; dx <= radius; dx++){
                for(int dy = -radius; dy <= radius; dy++){
                    Building b = Vars.world.build(tileX() + dx, tileY() + dy);
                    if(b instanceof RBMKReactor.ReactorPart.ReactorPartBuild){
                        RBMKReactor.ReactorPart.ReactorPartBuild source = (RBMKReactor.ReactorPart.ReactorPartBuild)b;
                        if(sourceValid(source)) sources.addUnique(source);
                    }
                }
            }
            sourceIndex = sources.isEmpty() ? 0 : sourceIndex % sources.size;
        }

        @Override public void updateTile(){
            dump(CryonItems.nuclearByproduct);
            if(!enabled || efficiency <= 0f) return;
            scanTimer -= delta();
            if(scanTimer <= 0f){
                scanSources();
                scanTimer = scanInterval;
            }
            transportCounter = Math.min(transportTime, transportCounter + edelta());
            if(transportCounter < transportTime || items.total() >= itemCapacity || sources.isEmpty()) return;
            for(int i = 0; i < sources.size; i++){
                RBMKReactor.ReactorPart.ReactorPartBuild source = sources.get(sourceIndex);
                sourceIndex = (sourceIndex + 1) % sources.size;
                if(sourceValid(source) && source.items.get(CryonItems.nuclearByproduct) > 0){
                    source.items.remove(CryonItems.nuclearByproduct, 1);
                    items.add(CryonItems.nuclearByproduct, 1);
                    transportCounter = 0f;
                    if(wasVisible) Fx.itemTransfer.at(source.x, source.y, 0f, CryonItems.nuclearByproduct.color, this);
                    break;
                }
            }
        }

        @Override public void drawSelect(){
            super.drawSelect();
            Drawf.dashCircle(x, y, range, Pal.accent);
        }
    }
}
