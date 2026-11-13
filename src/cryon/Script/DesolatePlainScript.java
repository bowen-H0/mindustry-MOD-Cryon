package cryon.Script;

import arc.Core;
import arc.Events;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.ui.layout.Table;
import arc.util.io.Reads;
import arc.util.io.Writes;
import cryon.Features.MapAlliances;
import cryon.Story.DesolateFleetCutscene;
import cryon.Story.DesolateBattlefield;
import cryon.UI.RadioDialogue;
import cryon.Type.JavaProcessor.JavaProcessorBuild;
import mindustry.content.StatusEffects;
import mindustry.game.EventType.*;
import mindustry.game.SpawnGroup;
import mindustry.gen.*;
import mindustry.type.UnitType;
import mindustry.world.Tile;

import static mindustry.Vars.*;

/** The sector's persistent director; inherits nuclear strike networking and serialization. */
public final class DesolatePlainScript extends NuclearCleansingScript{
    public static final String id = "desolate_plain", mapTag = "cryon-desolate-director";
    private static final String finalWaveTag = "cryon-desolate-final-wave";
    private static final String packet = "cryon-desolate-fleet";
    public static final int waiting = 0, intro = 1, battle = 2, complete = 3;
    public static final float fleetArrival = 15f;
    public static final float returnStart = 300f, introDuration = 360f;
    public static final double fleetFlag = 840100d;
    private static final float[] lateral = {-390f, -130f, 130f, 390f, -550f, 550f, -220f, 220f};
    private static final float[] forward = {-230f, -230f, -230f, -230f, 50f, 50f, 330f, 330f};
    private static DesolatePlainScript active;
    private static boolean installed;
    public int phase, spawnedMask;
    public float elapsed, centerX, centerY, heading;
    public boolean cleansingStarted;
    public DesolateBattlefield battlefield;

    public static void install(){
        if(installed) return;
        installed = true;
        DesolateFleetCutscene.install();
        Events.on(ResetEvent.class, event -> active = null);
        Events.run(Trigger.afterGameUpdate, () -> {
            DesolatePlainScript script = current();
            if(script != null && script.phase == intro) script.poseFleet();
        });
        Events.on(ClientLoadEvent.class, event -> {
            if(headless) return;
            netClient.addPacketHandler(packet, data -> {
                String[] args = data.split("\\|");
                if(args.length != 6) return;
                try{
                    Building b = world.build(Integer.parseInt(args[0]));
                    if(!(b instanceof JavaProcessorBuild)) return;
                    MapScript script = ((JavaProcessorBuild)b).script;
                    if(!(script instanceof DesolatePlainScript)) return;
                    DesolatePlainScript director = (DesolatePlainScript)script;
                    director.phase = Integer.parseInt(args[1]);
                    director.elapsed = Float.parseFloat(args[2]);
                    director.centerX = Float.parseFloat(args[3]);
                    director.centerY = Float.parseFloat(args[4]);
                    director.heading = Float.parseFloat(args[5]);
                }catch(NumberFormatException ignored){}
            });
        });
    }

    @Override protected void init(){
        active = this;
        intervalSeconds = 60f;
        missilesPerSalvo = 3;
        power = 3f;
        friendlyFire = false;
        prepareRules();
        battlefield = new DesolateBattlefield(this);
    }

    public static DesolatePlainScript current(){
        return active != null && active.build != null && active.build.isAdded()
            && active.build.script == active ? active : null;
    }

    public void prepareRules(){
        configureRules(phase);
    }

    private static void configureRules(int phase){
        if(net.client()) return;
        if(!state.rules.tags.containsKey(finalWaveTag)){
            state.rules.tags.put(finalWaveTag, Integer.toString(state.rules.winWave > 0 ? state.rules.winWave : 100));
        }
        state.rules.tags.put(mapTag, "true");
        // Enemy builders must also work on survival maps with no enemy core.
        state.rules.teams.get(state.rules.waveTeam).infiniteResources = true;
        // Retire the old intrusion gate so its load handler cannot undo this director's gate.
        state.rules.tags.remove("cryon-intrusion-win-wave");
        state.rules.tags.remove("cryon-intrusion-wave-timer");
        state.rules.spawns = state.rules.spawns.copy();
        state.rules.spawns.removeAll(g -> fleetType(g.type));
        for(int i = 0; i < state.rules.spawns.size; i++){
            SpawnGroup group = state.rules.spawns.get(i).copy();
            group.end = Math.min(group.end, finalWave() - 2);
            state.rules.spawns.set(i, group);
        }
        state.rules.winWave = phase == complete ? finalWave() : 0;
        if(phase != waiting || state.wave >= finalWave()) state.rules.waveTimer = false;
    }

    public static int finalWave(){ return state.rules.tags.getInt(finalWaveTag, 100); }
    private static boolean fleetType(UnitType type){
        return type != null && (type.name.equals("cryon-roche") || type.name.equals("cryon-threshold") || type.name.equals("cryon-eon"));
    }

    private boolean enemiesRemain(){
        return Groups.unit.contains(u -> !u.dead && u.health > 0f && u.isEnemy()
            && !MapAlliances.allied(u.team, state.rules.defaultTeam));
    }

    private void dialogueOnce(String cue, boolean play){
        String tag = "cryon-desolate-radio-" + cue;
        if(state.rules.tags.getBool(tag)) return;
        state.rules.tags.put(tag, "true");
        if(play) RadioDialogue.broadcastKey(mindustry.game.Team.crux, "Crux", "cryon.desolate.radio." + cue);
    }

    @Override public void update(){
        active = this;
        if(state.isEditor() || state.gameOver) return;
        if(net.client()){
            super.update();
            if(phase == intro && !state.isPaused()) elapsed = Math.min(introDuration, elapsed + delta());
            if(phase == intro) poseFleet();
            return;
        }
        // Tags survive saves without changing the processor's binary save format.
        dialogueOnce("opening", phase == waiting && !cleansingStarted && state.wave <= 2);
        battlefield.update();
        // state.wave is the NEXT wave; wave 21 means wave 20 has been dispatched.
        if(!cleansingStarted && (state.wave > 21
            || state.wave == 21 && !enemiesRemain() && !spawner.isSpawning())){
            cleansingStarted = true;
            running = true;
            timer = 0f;
            dialogueOnce("cleansing", true);
            if(net.server()) Call.announce(Core.bundle.get("cryon.desolate.cleansing"));
            else if(!headless && ui != null) ui.announce(Core.bundle.get("cryon.desolate.cleansing"), 5f);
        }
        // state.wave == finalWave() means the preceding (normally 99th) wave just began.
        if(phase == waiting && state.wave >= finalWave()) dialogueOnce("fleet", true);
        if(phase == waiting && state.wave >= finalWave()){
            state.rules.waveTimer = false;
            state.wavetime = state.rules.waveSpacing;
            if(!enemiesRemain() && !spawner.isSpawning()) startIntro();
        }
        if(phase == intro){
            elapsed += delta();
            for(int slot = 0; slot < 8; slot++){
                if(elapsed >= fleetArrival && (spawnedMask & (1 << slot)) == 0) spawnSlot(slot);
                Unit unit = fleetUnit(slot);
                if(unit != null){
                    Vec2 pos = entryPosition(slot);
                    unit.set(pos);
                    unit.rotation = heading;
                    unit.vel.setZero();
                    unit.apply(StatusEffects.unmoving, 2f);
                    unit.apply(StatusEffects.disarmed, 2f);
                    unit.apply(StatusEffects.invincible, 2f);
                }
            }
            if(elapsed >= introDuration){
                phase = battle;
                for(int i = 0; i < 8; i++){
                    Unit unit = fleetUnit(i);
                    if(unit == null) continue;
                    unit.unapply(StatusEffects.unmoving);
                    unit.unapply(StatusEffects.disarmed);
                    unit.unapply(StatusEffects.invincible);
                }
                syncIntro();
            }
        }else if(phase == battle && !enemiesRemain() && !spawner.isSpawning()){
            phase = complete;
            running = false;
            state.rules.winWave = finalWave();
            state.wave = Math.max(state.wave, finalWave() + 1);
            if(state.isCampaign()) Call.sectorCapture();
            syncIntro();
        }
        // Pause future salvos during the cinematic; committed missiles still resolve normally.
        boolean resume = running;
        if(phase == intro) running = false;
        super.update();
        running = resume;
    }

    private void startIntro(){
        UnitType[] types = {content.unit("cryon-roche"), content.unit("cryon-threshold"), content.unit("cryon-eon")};
        for(UnitType type : types) if(type == null) throw new IllegalStateException("Missing desolate fleet unit");
        Tile spawn = spawner.getFirstSpawn();
        centerX = spawn == null ? world.unitWidth() / 2f : spawn.worldx();
        centerY = spawn == null ? world.unitHeight() - 500f : spawn.worldy();
        Building core = state.rules.defaultTeam.core();
        heading = core == null ? 270f : Angles.angle(centerX, centerY, core.x, core.y);
        // Keep the complete formation, including large hulls, inside the map.
        float marginX = Math.min(720f, world.unitWidth() * 0.45f);
        float marginY = Math.min(720f, world.unitHeight() * 0.45f);
        centerX = Mathf.clamp(centerX, marginX, world.unitWidth() - marginX);
        centerY = Mathf.clamp(centerY, marginY, world.unitHeight() - marginY);
        phase = intro;
        elapsed = 0f;
        syncIntro();
    }

    public Vec2 formation(int slot){
        float scale = Math.min(1f, Math.min(world.unitWidth(), world.unitHeight()) / 1600f);
        return new Vec2().trns(heading, forward[slot] * scale, lateral[slot] * scale).add(centerX, centerY);
    }

    public float entryProgress(){
        return Mathf.clamp((elapsed - fleetArrival) / (returnStart - fleetArrival));
    }

    public float entryOffset(){
        float scale = Math.min(1f, Math.min(world.unitWidth(), world.unitHeight()) / 1600f);
        float distance = 480f * scale;
        float dx = -Mathf.cosDeg(heading), dy = -Mathf.sinDeg(heading);
        float margin = Math.min(120f, Math.min(world.unitWidth(), world.unitHeight()) * 0.08f);
        for(int i = 0; i < 8; i++){
            Vec2 position = formation(i);
            if(dx > 0.001f) distance = Math.min(distance, (world.unitWidth() - margin - position.x) / dx);
            if(dx < -0.001f) distance = Math.min(distance, (margin - position.x) / dx);
            if(dy > 0.001f) distance = Math.min(distance, (world.unitHeight() - margin - position.y) / dy);
            if(dy < -0.001f) distance = Math.min(distance, (margin - position.y) / dy);
        }
        float remaining = 1f - entryProgress();
        return Math.max(0f, distance) * remaining * remaining;
    }

    public Vec2 entryPosition(int slot){
        return formation(slot).add(new Vec2().trns(heading + 180f, entryOffset()));
    }

    private void poseFleet(){
        for(int i = 0; i < 8; i++){
            Unit unit = fleetUnit(i);
            if(unit == null) continue;
            unit.set(entryPosition(i));
            unit.rotation = heading;
            unit.vel.setZero();
        }
    }

    public static boolean entering(Unit unit){
        DesolatePlainScript script = current();
        return script != null && script.phase == intro && unit.team == state.rules.waveTeam
            && unit.flag >= fleetFlag && unit.flag < fleetFlag + 8d;
    }

    public Unit fleetUnit(int slot){
        return Groups.unit.find(u -> u.team == state.rules.waveTeam && u.flag == fleetFlag + slot && !u.dead);
    }

    private void spawnSlot(int slot){
        if(fleetUnit(slot) == null){
            UnitType type = content.unit(slot < 4 ? "cryon-roche" : slot < 6 ? "cryon-threshold" : "cryon-eon");
            Vec2 pos = entryPosition(slot);
            Unit unit = type.create(state.rules.waveTeam);
            unit.set(pos);
            unit.rotation = heading;
            unit.flag = fleetFlag + slot;
            unit.apply(StatusEffects.boss, Float.MAX_VALUE);
            unit.apply(StatusEffects.unmoving, 2f);
            unit.apply(StatusEffects.disarmed, 2f);
            unit.apply(StatusEffects.invincible, 2f);
            unit.add();
            Call.effect(mindustry.content.Fx.unitSpawn, unit.x, unit.y, heading, unit.team.color);
        }
        spawnedMask |= 1 << slot;
    }

    private void syncIntro(){
        if(net.server()){
            Call.setRules(state.rules);
            Call.clientPacketReliable(packet, build.pos() + "|" + phase + "|" + elapsed + "|" + centerX + "|" + centerY + "|" + heading);
        }
    }

    @Override public void configure(String data){} // The sector owns the nuclear schedule.
    @Override public void buildUI(Table table){
        table.row();
        table.add(Core.bundle.get("cryon.desolate.processor-help")).wrap().width(300f);
    }
    @Override public void removed(){
        super.removed();
        if(active == this) active = null;
    }
    @Override public void write(Writes write){
        super.write(write);
        write.i(phase); write.i(spawnedMask); write.f(elapsed);
        write.f(centerX); write.f(centerY); write.f(heading); write.bool(cleansingStarted);
    }
    @Override public void read(Reads read){
        super.read(read);
        phase = read.i(); spawnedMask = read.i(); elapsed = read.f();
        centerX = read.f(); centerY = read.f(); heading = read.f(); cleansingStarted = read.bool();
        prepareRules();
    }
}
