package cryon.Script;

import arc.Core;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import arc.util.io.*;
import cryon.Content.SacredRemains;
import cryon.Story.DialogueSequence;
import cryon.UI.RadioDialogue;
import mindustry.Vars;
import mindustry.content.StatusEffects;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.world.Tile;

/** Place at the desired breach location. All encounter state belongs to this Java processor. */
public final class AegisSacredRemainsScript extends MapScript{
    public static final String id = "aegis-sacred-remains", onceTag = "cryon-aegis-sacred-remains-started";
    public static final double bossFlag = 584103d;
    // 0: waiting for first Malis core loss; 1: channel; 2: breach; 3: boss; 4: finished.
    private int stage, initialCores = -1, cursor;
    private float remaining = -1f, spawnX, spawnY, entryAngle, heartbeat;
    private boolean sent, holding, oldAttack, oldWaves;
    private DialogueSequence dialogue;
    @Override protected void init(){dialogue = DialogueSequence.parse(DialogueSequence.source("aegis-sacred-remains"));}
    public int stage(){return stage;}
    public float remaining(){return remaining;}
    @Override public void update(){
        if(Vars.net.client() || Vars.state.isEditor() || !Vars.state.isGame() || Vars.state.isPaused() || Vars.state.gameOver) return;
        if(stage == 0){
            if(Vars.state.rules.tags.getBool(onceTag)){stage = 4;return;}
            int count = Team.malis.cores().size;
            if(initialCores < 0){if(count > 0) initialCores = count;return;}
            if(count >= initialCores){initialCores = count;return;}
            Tile entry = entry();
            if(entry == null) return;
            spawnX = entry.worldx();spawnY = entry.worldy();
            var core = Vars.state.rules.defaultTeam.core();
            entryAngle = core == null ? 270f : arc.math.Angles.angle(spawnX,spawnY,core.x,core.y);
            oldAttack = Vars.state.rules.attackMode;oldWaves = Vars.state.rules.waves;
            holding = true;stage = 1;
            Vars.state.rules.tags.put(onceTag, "true");
            holdVictory();syncRules();
        }
        if(stage >= 1 && stage <= 3) holdVictory();
        if(stage == 1){
            DialogueSequence.Step step = dialogue.steps.get(cursor);
            if(remaining < 0f) remaining = step.duration();
            if(!sent){
                RadioDialogue.broadcastPage(step.team, step.speaker, step.text, step.mode, step.hold,
                    Math.max(0f, step.duration() - remaining), step.textKey, step.speakerKey, step.duration());
                sent = true;
            }
            remaining -= Time.delta / 60f;
            if(remaining <= 0f){
                cursor++;remaining = -1f;sent = false;
                if(cursor >= dialogue.steps.size){stage = 2;remaining = SacredRemains.riftDuration;showRift();}
            }
        }else if(stage == 2){
            heartbeat += Time.delta;
            if(!sent || heartbeat >= 60f){showRift();heartbeat = 0f;}
            remaining -= Time.delta;
            float elapsed = SacredRemains.riftDuration - remaining;
            Unit arriving = boss();
            if(elapsed >= 120f && arriving == null && Vars.state.rules.tags.getBool(onceTag+"-spawned")){
                stage = 4;restoreRules();cryon.Features.SacredRiftRenderer.cancel();return;
            }
            if(elapsed >= 120f && arriving == null){
                Vars.state.rules.tags.put(onceTag+"-spawned","true");
                arriving = SacredRemains.sacredRemains.create(Team.malis);
                arriving.flag = bossFlag;
                arriving.set(spawnX - Mathf.cosDeg(entryAngle)*70f, spawnY - Mathf.sinDeg(entryAngle)*70f);
                arriving.rotation = entryAngle;
                arriving.apply(StatusEffects.boss,999999f);
                arriving.add();
            }
            if(arriving != null){
                if(!(arriving.controller() instanceof cryon.Type.SacredRemainsArrivalAI))
                    arriving.controller(new cryon.Type.SacredRemainsArrivalAI(spawnX,spawnY,entryAngle));
                arriving.apply(StatusEffects.disarmed,10f);
                arriving.apply(StatusEffects.invincible,10f);
            }
            if(remaining <= 0f){
                if(arriving != null){
                    arriving.unapply(StatusEffects.disarmed);arriving.unapply(StatusEffects.invincible);
                    arriving.controller(new mindustry.ai.types.GroundAI());
                }
                stage = 3;remaining = -1f;sent = false;
            }
        }else if(stage == 3 && boss() == null){stage = 4;restoreRules();}
    }
    private void showRift(){
        cryon.Features.SacredRiftRenderer.broadcast(spawnX,spawnY,entryAngle,SacredRemains.riftDuration - remaining);
        sent = true;
    }
    private Unit boss(){
        for(Unit unit : Groups.unit) if(!unit.dead && unit.type == SacredRemains.sacredRemains && unit.flag == bossFlag) return unit;
        return null;
    }
    private Tile entry(){
        Tile best = null;float closest = Float.MAX_VALUE;
        for(int tx = build.tileX() - 24; tx <= build.tileX() + 24; tx++) for(int ty = build.tileY() - 24; ty <= build.tileY() + 24; ty++){
            Tile tile = Vars.world.tile(tx, ty);
            if(tile == null || tile.floor().isDeep() || tile.block().solid) continue;
            boolean open = true;
            for(int dx = -3; dx <= 3 && open; dx++) for(int dy = -3; dy <= 3; dy++){
                Tile nearby = Vars.world.tile(tx + dx, ty + dy);
                if(nearby == null || nearby.block().solid || nearby.floor().isDeep()){open = false;break;}
            }
            if(!open) continue;
            float distance = Mathf.dst2(build.x, build.y, tile.worldx(), tile.worldy());
            if(distance < closest){closest = distance;best = tile;}
        }
        return best;
    }
    private void holdVictory(){
        // Keep normal player defeat enabled, but do not capture the sector while the breach is pending.
        Vars.state.rules.attackMode = false;Vars.state.rules.waves = false;
    }
    private void restoreRules(){
        if(!holding) return;
        Vars.state.rules.attackMode = oldAttack;Vars.state.rules.waves = oldWaves;
        holding = false;syncRules();
    }
    private void syncRules(){if(Vars.net.server()) Call.setRules(Vars.state.rules);}
    @Override public void removed(){
        if(!Vars.net.client()){
            if(stage == 2){
                Unit unit = boss();
                if(unit != null){unit.unapply(StatusEffects.disarmed);unit.unapply(StatusEffects.invincible);unit.controller(new mindustry.ai.types.GroundAI());}
                cryon.Features.SacredRiftRenderer.cancel();
            }
            restoreRules();
        }
    }
    @Override public void write(Writes write){
        write.i(stage);write.i(initialCores);write.i(cursor);write.f(remaining);
        write.f(spawnX);write.f(spawnY);write.bool(holding);write.bool(oldAttack);write.bool(oldWaves);write.b(1);write.f(entryAngle);
    }
    @Override public void read(Reads read){
        stage = Mathf.clamp(read.i(), 0, 4);initialCores = Math.max(-1, read.i());
        cursor = Mathf.clamp(read.i(), 0, dialogue.steps.size - 1);remaining = read.f();
        spawnX = read.f();spawnY = read.f();holding = read.bool();oldAttack = read.bool();oldWaves = read.bool();
        try{read.b();entryAngle = read.f();}
        catch(RuntimeException legacy){
            var core = Vars.state.rules.defaultTeam.core();
            entryAngle = core == null ? 270f : arc.math.Angles.angle(spawnX,spawnY,core.x,core.y);
            if(stage == 2) remaining = remaining / 240f * SacredRemains.riftDuration;
        }
        if(!Float.isFinite(entryAngle))entryAngle = 270f;
        if(!Float.isFinite(remaining)) remaining = -1f;
        sent = false;
    }
    @Override public void buildUI(Table table){
        table.row();table.add("@script.aegis-sacred-remains.hint").wrap().width(320f).pad(8f).row();
        table.label(() -> Core.bundle.format("script.aegis-sacred-remains.stage", stage)).row();
    }
}
