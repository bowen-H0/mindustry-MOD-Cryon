package cryon.Type;

import arc.struct.Seq;
import arc.util.io.*;
import cryon.Unity.UnityBlueprint;
import mindustry.Vars;
import mindustry.gen.*;
import mindustry.type.ItemStack;
import mindustry.world.*;
import mindustry.world.blocks.ConstructBlock;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import mindustry.world.meta.BuildVisibility;

/** A persisted native construction site with per-instance requirements. */
public class UnityConstruction extends Block{
    public final UnityTurret platform;
    public UnityConstruction(String name,UnityTurret platform){
        super(name);this.platform=platform;
        size=platform.size;health=platform.health;solid=update=sync=true;
        buildVisibility=BuildVisibility.hidden;inEditor=false;generateIcons=false;
        buildType=Site::new;
    }
    @Override public void load(){super.load();region=platform.region;fullIcon=platform.finishedBlock.fullIcon;uiIcon=fullIcon;}
    public void replace(ConstructBlock.ConstructBuild old,UnityBlueprint recipe,boolean breaking){
        Tile tile=old.tile;Seq<Building> previous=old.prevBuild;float healthFraction=old.healthf();
        Call.setTile(tile,this,old.team,old.rotation);
        Site site=(Site)tile.build;site.initialize(recipe,breaking);site.prevBuild=previous;site.health=site.maxHealth*healthFraction;
        if(Vars.net.server())Call.clientPacketReliable("cryon-unity-site",tile.pos()+"|"+(breaking?"break":"build")+"|"+recipe.encode());
    }
    public class Site extends ConstructBlock.ConstructBuild{
        public UnityBlueprint recipe=new UnityBlueprint();
        public Site(){ConstructBlock.get(5).super();current=platform.finishedBlock;previous=platform;}
        private void withRecipe(Runnable run){
            Block target=platform.finishedBlock;
            ItemStack[] oldRequirements=target.requirements;float oldTime=target.buildTime;
            try{target.requirements=recipe.requirements();target.buildTime=recipe.work();run.run();}
            finally{target.requirements=oldRequirements;target.buildTime=oldTime;}
        }
        public void initialize(UnityBlueprint recipe,boolean breaking){
            this.recipe=recipe;
            withRecipe(()->{if(breaking)setDeconstruct(platform.finishedBlock);else setConstruct(platform,platform.finishedBlock);});
            activeDeconstruct=breaking;wasConstructing=!breaking;lastConfig="install:"+recipe.encode();
        }
        @Override public void displayConsumption(arc.scene.ui.layout.Table table){
            table.left();table.add("施工材料（随建造进度消耗）").left().row();
            for(ItemStack stack:recipe.cost()){
                table.table(row->{
                    if(stack.item.uiIcon!=null)row.image(stack.item.uiIcon).size(22f).padRight(5f);
                    row.add(stack.item.localizedName+" × "+stack.amount).left();
                }).left().row();
            }
        }
        @Override public void construct(Unit builder,Building core,float amount,Object config){
            if(recipe.modules.isEmpty())return;
            withRecipe(()->super.construct(builder,core,amount,"install:"+recipe.encode()));
        }
        @Override public void deconstruct(Unit builder,CoreBuild core,float amount){
            if(recipe.modules.isEmpty())return;
            withRecipe(()->super.deconstruct(builder,core,amount));
        }
        @Override public void write(Writes w){super.write(w);w.str(recipe.encode());w.bool(activeDeconstruct);}
        @Override public void read(Reads r,byte revision){
            super.read(r,revision);recipe=UnityBlueprint.decodeStored(r.str());activeDeconstruct=r.bool();wasConstructing=!activeDeconstruct;
            buildCost=recipe.work()*Vars.state.rules.buildCostMultiplier;lastConfig="install:"+recipe.encode();
        }
        @Override public byte version(){return 1;}
    }
}
