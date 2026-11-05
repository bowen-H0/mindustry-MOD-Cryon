package cryon.Features;

import arc.audio.Sound;
import mindustry.Vars;
import mindustry.gen.Sounds;

/** Original spatial sound set. Native unit looping handles distance and pause behavior. */
public final class EonAudio{
    public static Sound engine = Sounds.none, charge = Sounds.chargeCorvus, cannon = Sounds.shootForeshadow,
        impact = Sounds.explosion, detonation = Sounds.explosionReactor;
    public static void load(){
        if(Vars.headless) return;
        engine = Vars.tree.loadSound("cryon-eon-engine");
        charge = Vars.tree.loadSound("cryon-eon-charge");
        cannon = Vars.tree.loadSound("cryon-eon-cannon");
        impact = Vars.tree.loadSound("cryon-eon-impact");
        detonation = Vars.tree.loadSound("cryon-eon-detonation");
    }
}
