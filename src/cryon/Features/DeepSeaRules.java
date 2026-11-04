package cryon.Features;

import arc.struct.ObjectSet;
import cryon.Content.SubmarineContent;
import mindustry.Vars;
import mindustry.content.UnitTypes;
import mindustry.game.Rules;
import mindustry.type.UnitType;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.meta.Env;

/** Restrict deep-sea combat production without disabling Cryon industry or core builders. */
public final class DeepSeaRules{
    private static final ObjectSet<UnitType> coreUnits = new ObjectSet<>();
    private DeepSeaRules(){}

    public static void configure(){
        coreUnits.clear();
        for(var block : Vars.content.blocks()){
            if(block instanceof CoreBlock && ((CoreBlock)block).unitType != null) coreUnits.add(((CoreBlock)block).unitType);
            if(block.minfo.mod != null && block.minfo.mod.name.equals("cryon")) block.envDisabled &= ~Env.underwater;
        }
    }

    public static boolean allowed(UnitType type){
        // Assembly drones are internal machinery, not an additional producible combat line.
        return SubmarineContent.submarine(type) || coreUnits.contains(type) || type == UnitTypes.assemblyDrone;
    }

    public static void apply(Rules rules){
        for(UnitType unit : Vars.content.units()) if(!allowed(unit)) rules.bannedUnits.add(unit);
        rules.waveTeam = mindustry.game.Team.crux;
        rules.tags.put("cryon-environment-cold", "true");
        rules.tags.put("cryon-environment-deepsea", "true");
    }
}
