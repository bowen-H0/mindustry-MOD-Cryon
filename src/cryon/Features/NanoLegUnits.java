package cryon.Features;

import arc.Events;
import cryon.Content.NanoColonyContent;
import mindustry.Vars;
import mindustry.game.EventType.*;
import mindustry.gen.*;

/** Old saves encode the former UnitEntity class ID; upgrade existing bodies without killing them. */
public final class NanoLegUnits{
    private static boolean installed;
    public static void install(){
        if(installed)return;
        installed=true;
        Events.on(WorldLoadEvent.class,e->migrate());
        Events.on(SaveLoadEvent.class,e->migrate());
    }
    public static void migrate(){
        if(Vars.net==null||Vars.net.client())return;
        for(Unit old:Groups.unit.copy()){
            if(old.dead||old instanceof Legsc||!(old.type==NanoColonyContent.eroder||old.type==NanoColonyContent.armorBreaker||old.type==NanoColonyContent.annihilator))continue;
            Unit next=old.type.create(old.team);
            next.set(old.x,old.y);next.rotation=old.rotation;next.vel.set(old.vel);
            next.maxHealth=old.maxHealth;next.health=old.health;next.shield=old.shield;next.flag=old.flag;
            // Some newer desktop builds removed the v157 unit-ammo field entirely.
            try{var ammo=Unit.class.getField("ammo");ammo.setFloat(next,ammo.getFloat(old));}
            catch(NoSuchFieldException ignored){}
            catch(IllegalAccessException error){throw new IllegalStateException("Cannot preserve unit ammo",error);}
            next.stack.item=old.stack.item;next.stack.amount=old.stack.amount;
            for(var effect:Vars.content.statusEffects()){
                float duration=old.getDuration(effect);if(duration>0f)next.apply(effect,duration);
            }
            for(int i=0;i<Math.min(old.mounts.length,next.mounts.length);i++){
                next.mounts[i].reload=old.mounts[i].reload;next.mounts[i].heat=old.mounts[i].heat;next.mounts[i].warmup=old.mounts[i].warmup;
            }
            Call.unitDespawn(old);next.add();
        }
    }
}
