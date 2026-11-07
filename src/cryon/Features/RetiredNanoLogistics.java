package cryon.Features;

import arc.Events;
import arc.struct.*;
import arc.util.Time;
import cryon.Content.CryonItems;
import cryon.Type.GreyGooPort;
import mindustry.Vars;
import mindustry.entities.units.AIController;
import mindustry.game.Team;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.io.SaveVersion;
import mindustry.io.SaveFileReader.CustomChunk;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import mindustry.world.meta.BuildVisibility;
import java.io.*;

/** Read-only legacy definitions let existing saves retire air freight without losing cargo. */
public final class RetiredNanoLogistics{
    private static UnitType carrier;
    private static Block loader,unload;
    private static final IntIntMap cargo=new IntIntMap();
    private static final Seq<Building> retired=new Seq<>();
    private static float elapsed;
    private static boolean installed;
    public static void load(){
        carrier=new UnitType("grey-goo-carrier");carrier.constructor=UnitEntity::create;
        carrier.hidden=true;carrier.hideDatabase=true;carrier.flying=true;carrier.useUnitCap=false;
        carrier.itemCapacity=120;carrier.health=700;carrier.createWreck=false;
        carrier.playerControllable=carrier.logicControllable=false;
        carrier.controller=u->new AIController();carrier.aiController=AIController::new;
        loader=new GreyGooPort("grey-goo-loader");unload=new GreyGooPort.UnloadPoint("grey-goo-unload-point");
        for(Block b:new Block[]{loader,unload}){b.buildVisibility=BuildVisibility.hidden;b.hideDatabase=true;b.placeablePlayer=false;}
    }
    public static boolean removed(Block b){return b==loader||b==unload;}
    public static void install(){
        if(installed)return;installed=true;
        SaveVersion.addCustomChunk("cryon-retired-logistics-cargo",new CustomChunk(){
            public void write(DataOutput out)throws IOException{
                out.writeInt(cargo.size);for(var e:cargo.entries()){out.writeInt(e.key);out.writeInt(e.value);}
            }
            public void read(DataInput in)throws IOException{
                cargo.clear();int count=in.readInt();if(count<0||count>256)throw new IOException("Invalid retired cargo count");
                for(int i=0;i<count;i++){int team=in.readInt(),amount=in.readInt();if(team<0||team>255||amount<0)throw new IOException("Invalid retired cargo");cargo.put(team,amount);}
            }
        });
        Events.on(ResetEvent.class,e->{cargo.clear();elapsed=0;});
        Events.on(WorldLoadBeginEvent.class,e->{cargo.clear();elapsed=0;});
        // Custom chunks are read after WorldLoadEvent. Do not consume old ports before their
        // cargo-reserve chunk has loaded, or a later chunk read could overwrite the reserve.
        Events.on(WorldLoadEvent.class,e->Time.run(1f,RetiredNanoLogistics::migrate));
        Events.on(SaveLoadEvent.class,e->migrate());
        Events.run(Trigger.update,()->{
            if(Vars.net.client()||!Vars.state.isGame()||Vars.state.isPaused()||cargo.isEmpty())return;
            elapsed+=Time.delta;if(elapsed>=120f){elapsed%=120f;flush();}
        });
    }
    private static void keep(Team team,int amount){if(amount>0)cargo.put(team.id,cargo.get(team.id,0)+amount);}
    public static void migrate(){
        if(Vars.net.client())return;
        for(Unit u:Groups.unit.copy())if(u.type==carrier){
            if(u.stack.item==CryonItems.nanoMaterial)keep(u.team,u.stack.amount);
            Call.unitDespawn(u);
        }
        retired.clear();for(Team team:Team.all)for(Building b:team.data().buildings)if(removed(b.block)&&b.isValid())retired.add(b);
        for(Building b:retired){
            if(b.items!=null)keep(b.team,b.items.get(CryonItems.nanoMaterial));
            // This is a save migration, not colony construction: obsolete ports become ordinary pipe.
            b.tile.setNet(Vars.content.block("cryon-reinforced-vacuum-duct"),b.team,b.rotation);
        }
        retired.clear();flush();
    }
    private static void flush(){
        var entries=cargo.entries();
        while(entries.hasNext()){
            var entry=entries.next();int amount=entry.value;
            for(Building b:Team.get(entry.key).data().buildings){
                if(!b.isValid()||b.items==null||(!(b.block instanceof cryon.Type.NanoWarehouse)&&!(b instanceof CoreBuild)))continue;
                int accepted=Math.min(amount,Math.max(0,b.getMaximumAccepted(CryonItems.nanoMaterial)-b.items.get(CryonItems.nanoMaterial)));
                b.items.add(CryonItems.nanoMaterial,accepted);amount-=accepted;if(amount==0)break;
            }
            if(amount==0)entries.remove();else cargo.put(entry.key,amount);
        }
    }
}
