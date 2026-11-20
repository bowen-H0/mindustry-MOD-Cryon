package cryon.Type;

import mindustry.content.Blocks;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.defense.turrets.Turret;

/** Uses the same charge and firing cues as the vanilla Lancer. */
public final class FaultTurret extends ItemTurret{
    public FaultTurret(String name){
        super(name);
        Turret lancer = (Turret)Blocks.lancer;
        chargeSound = lancer.chargeSound;
        shootSound = lancer.shootSound;
        shootSoundVolume = lancer.shootSoundVolume;
        soundPitchMin = lancer.soundPitchMin;
        soundPitchMax = lancer.soundPitchMax;
    }
}
