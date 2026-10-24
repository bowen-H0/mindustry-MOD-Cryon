package cryon.Features;

import arc.Core;
import arc.audio.Sound;
import cryon.Type.SpaceJumpAbility;
import mindustry.Vars;
import mindustry.entities.abilities.Ability;
import mindustry.gen.Groups;
import mindustry.gen.Unit;

/** One spatial charge bed for the most audible ship prevents fleet-sized sound stacking. */
public class SpaceJumpAudio{
    private static Sound charge, fold;
    private static int voice = -1, owner = -1;

    public static void load(){
        if(Vars.headless) return;
        charge = Vars.tree.loadSound("cryon-fold-charge");
        fold = Vars.tree.loadSound("cryon-fold-release");
        fold.setMinInterval(100);
    }

    public static void update(){
        if(Vars.headless || charge == null) return;
        if(!Vars.state.isGame() || Vars.state.isPaused()){
            stop();
            return;
        }
        Unit best = null;
        float volume = 0f, progress = 0f;
        int level = 1;
        for(Unit unit : Groups.unit){
            if(unit.dead || (Vars.player != null && unit.inFogTo(Vars.player.team()))) continue;
            for(Ability ability : unit.abilities){
                if(!(ability instanceof SpaceJumpAbility)) continue;
                SpaceJumpAbility jump = (SpaceJumpAbility)ability;
                if(!jump.active() || jump.chargeProgress() <= 0f) continue;
                float p = jump.chargeProgress();
                int tier = SpaceJumpAbility.level(unit);
                float v = charge.calcVolume(unit.x, unit.y) * (0.12f + 0.3f * p) * (0.7f + tier * 0.09f);
                if(v > volume){ best = unit; volume = v; progress = p; level = tier; }
            }
        }
        if(best == null || volume < 0.01f){ stop(); return; }
        float pitch = 0.8f + progress * 0.4f - level * 0.025f;
        float pan = charge.calcPan(best.x, best.y);
        if(voice < 0 || !Core.audio.isPlaying(voice)) voice = charge.loop(volume, pitch, pan);
        else{
            Core.audio.set(voice, pan, volume);
            Core.audio.setPitch(voice, pitch);
        }
        owner = best.id;
    }

    public static void fold(Unit unit, float ox, float oy, float x, float y, int level){
        if(Vars.headless || fold == null) return;
        if(owner == unit.id) stop();
        if(Vars.player != null && unit.inFogTo(Vars.player.team())) return;
        // One release at the nearer endpoint, emitted by the same event that draws the fold.
        boolean origin = fold.calcVolume(ox, oy) > fold.calcVolume(x, y);
        fold.at(origin ? ox : x, origin ? oy : y, 1f, 0.35f + level * 0.07f);
    }

    public static void stop(){
        if(voice >= 0 && Core.audio != null) Core.audio.stop(voice);
        voice = -1; owner = -1;
    }
}
