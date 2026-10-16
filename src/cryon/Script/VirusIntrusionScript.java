package cryon.Script;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import cryon.Story.DesolateIntrusion;
import cryon.Story.IntrusionContent;
import cryon.Story.IntrusionMap;
import cryon.Story.IntrusionOverlay;
import mindustry.content.StatusEffects;
import mindustry.content.UnitTypes;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;

import static mindustry.Vars.*;

/** All gameplay runs in the map's locked Java processor, using real Gamma and Bullet entities. */
public final class VirusIntrusionScript extends MapScript {
    public static final float introTicks = 720f, combatSeconds = 120f, attackModeTicks = 600f;
    public static final double gammaFlag = 730001d, virusFlag = 730002d;
    public static final float recoveryPerSecond = 6f;
    private static final String recoveryKey = "cryon-intrusion-recovery";
    private static VirusIntrusionScript active;
    private static boolean drawInstalled;
    private static boolean lifecycleInstalled;
    private int gammaId = -1, virusId = -1, volley;
    private float time, shootTimer, lastHealth = 300f, hurtCooldown, ending;
    private boolean spawned, finished, won, requested;

    @Override protected void init(){
        if(!lifecycleInstalled){
            lifecycleInstalled = true;
            Events.on(mindustry.game.EventType.ResetEvent.class, event -> resetPresentation());
            Events.on(mindustry.game.EventType.WorldLoadEvent.class, event -> resetPresentation());
        }
        if(!drawInstalled){
            drawInstalled = true;
            Events.run(Trigger.draw, () -> {
                VirusIntrusionScript current = active;
                if(current != null && current.valid()) current.drawWorld();
            });
        }
    }

    private static void resetPresentation(){
        active = null;
        IntrusionOverlay.hide();
    }

    public boolean valid(){ return IntrusionMap.active() && build != null && build.isAdded() && build.script == this; }
    public static VirusIntrusionScript current(){ return active != null && active.valid() ? active : null; }
    public float seconds(){ return time / 60f; }
    public boolean disarmed(){ Unit unit = gamma(); return !finished && (time < introTicks || unit != null && unit.hasEffect(StatusEffects.disarmed)); }
    public float remainingSeconds(){ return Math.max(0f, combatSeconds - Math.max(0f, time - introTicks) / 60f); }
    public float endingSeconds(){ return ending / 60f; }
    public boolean finished(){ return finished; }
    public boolean won(){ return won; }
    public float health(){ Unit unit = gamma(); return unit == null ? 0f : unit.health; }
    public float nodeMaxHealth(){ Unit unit = node(); return unit == null ? IntrusionContent.virus.health : unit.maxHealth; }
    public float nodeHealthValue(){ Unit unit = node(); return unit == null || unit.dead ? 0f : Math.max(0f, unit.health); }
    public float nodeHealth(){ return Mathf.clamp(nodeHealthValue() / Math.max(1f, nodeMaxHealth())); }
    public int phase(){ return (int)(Math.max(0f, time - introTicks) / attackModeTicks) % 3; }
    public Unit controlledGamma(){ return gamma(); }
    public Unit virusNode(){ return node(); }
    public boolean recoveryActive(){ return state.rules.tags.getBool(recoveryKey); }
    public boolean orbitalSupportActive(){
        return world.build(56, 30) instanceof cryon.Type.JavaProcessor.JavaProcessorBuild
                && ((cryon.Type.JavaProcessor.JavaProcessorBuild)world.build(56, 30)).script instanceof OrbitalStrikeScript;
    }

    private void updateOrbitalSupport(){
        if(!orbitalSupportActive()){
            if(nodeHealth() > 0.5f) return;
            world.tile(56, 30).setBlock(cryon.CryonJavaMod.javaProcessor, state.rules.defaultTeam);
            ((cryon.Type.JavaProcessor.JavaProcessorBuild)world.build(56, 30)).setScript("orbital_strike");
        }
        if(!orbitalSupportActive()) return;
        OrbitalStrikeScript support = (OrbitalStrikeScript)((cryon.Type.JavaProcessor.JavaProcessorBuild)world.build(56, 30)).script;
        support.range = 1000f;
        support.radius = 64f;
        support.chargeTime = 120f;
        support.lockTime = 60f;
        support.strikeTime = 60f;
        support.cooldownTime = 240f;
        support.pulseDamage = 150f;
    }
    private Unit gamma(){ return Groups.unit.getByID(gammaId); }
    private Unit node(){ return Groups.unit.getByID(virusId); }

    @Override public void update(){
        if(!IntrusionMap.active() || net.client()) return;
        if(active != this){
            active = this;
            arc.util.Log.info("[CryonIntrusion] Controller started: state=@, time=@s, delta=@, spawned=@, finished=@",
                    state.getState(), seconds(), delta(), spawned, finished);
        }
        if(!headless) IntrusionOverlay.ensureShown();
        lockRules();
        if(finished){
            if((ending += delta()) >= 180f && !requested){
                requested = true;
                Core.app.post(() -> DesolateIntrusion.finish(won));
            }
            return;
        }
        if(!spawned){ spawn(); }
        Unit gamma = gamma(), node = node();
        if(gamma == null || gamma.dead || gamma.health <= 0f){ end(false); return; }
        if(node == null || node.dead || node.health <= 0f){ end(true); return; }
        gamma.maxHealth = 300f;
        gamma.health = Math.min(300f, gamma.health);
        gamma.armor = 0f;
        gamma.buildSpeedMultiplier = 0f;
        gamma.plans.clear();
        gamma.mineTile = null;
        gamma.spawnedByCore = false;
        if(player != null && player.unit() != gamma){
            player.team(state.rules.defaultTeam);
            player.unit(gamma);
        }
        gamma.set(Mathf.clamp(gamma.x, IntrusionMap.left * 8f, IntrusionMap.right * 8f),
                Mathf.clamp(gamma.y, IntrusionMap.bottom * 8f, IntrusionMap.top * 8f));
        node.set(IntrusionMap.nodeX, IntrusionMap.nodeY);
        node.vel.setZero();
        hurtCooldown = Math.max(0f, hurtCooldown - delta());
        if(gamma.health < lastHealth && hurtCooldown <= 0f){
            gamma.apply(StatusEffects.invincible, 45f);
            hurtCooldown = 45f;
        }
        lastHealth = gamma.health;
        int previousMode = phase();
        float previousTime = time;
        time += delta();
        if(previousTime < introTicks && time >= introTicks){
            arc.util.Log.info("[CryonIntrusion] Intro complete; Gamma combat started.");
        }
        if(phase() != previousMode){
            volley = 0;
            shootTimer = 0f;
        }
        if(time < introTicks){
            gamma.set(IntrusionMap.playerX, IntrusionMap.playerY);
            gamma.vel.setZero();
            gamma.apply(StatusEffects.disarmed, 2f);
            gamma.apply(StatusEffects.invincible, 2f);
            node.apply(StatusEffects.invincible, 2f);
            return;
        }
        if(time >= introTicks + combatSeconds * 60f){ end(false); return; }
        if(!recoveryActive() && nodeHealth() <= 0.8f){
            // Rules tags persist in native saves without changing the processor data format.
            state.rules.tags.put(recoveryKey, "true");
        }
        if(recoveryActive()){
            gamma.heal(recoveryPerSecond * delta() / 60f);
            lastHealth = gamma.health;
        }
        updateOrbitalSupport();
        shootTimer -= delta();
        if(shootTimer <= 0f){
            shootTimer += phase() == 0 ? (volley % 3 == 2 ? 78f : 9f) : phase() == 1 ? 72f : 90f;
            emit(gamma);
            volley++;
        }
    }

    private void spawn(){
        spawned = true;
        Unit gamma = UnitTypes.gamma.create(state.rules.defaultTeam);
        gamma.set(IntrusionMap.playerX, IntrusionMap.playerY);
        gamma.flag = gammaFlag;
        gamma.maxHealth = gamma.health = 300f;
        gamma.add();
        gammaId = gamma.id;
        Unit virus = IntrusionContent.virus.spawn(state.rules.waveTeam, IntrusionMap.nodeX, IntrusionMap.nodeY);
        virus.flag = virusFlag;
        virusId = virus.id;
        if(player != null){
            player.team(state.rules.defaultTeam);
            if(!player.isAdded()) player.add();
            player.unit(gamma);
        }
        if(!headless && Core.camera != null) Core.camera.position.set(gamma.x, gamma.y);
    }

    private void lockRules(){
        state.rules.waves = state.rules.waveTimer = state.rules.attackMode = false;
        state.rules.canGameOver = false;
        state.rules.blockWhitelist = true;
        state.rules.bannedBlocks.clear();
        state.rules.buildSpeedMultiplier = 0f;
        state.rules.teams.get(state.rules.defaultTeam).unitHealthMultiplier = 300f / 220f;
        state.rules.teams.get(state.rules.defaultTeam).unitDamageMultiplier = 1.5f;
        state.rules.possessionAllowed = state.rules.schematicsAllowed = false;
        state.rules.logicUnitControl = false;
    }

    private void emit(Unit gamma){
        if(Groups.bullet.size() > 500) return;
        float aim = Angles.angle(IntrusionMap.nodeX, IntrusionMap.nodeY, gamma.x, gamma.y);
        if(phase() == 0){
            // Three consecutive aimed shots; each shot reacquires the player with small spread.
            float spread = Mathf.randomSeedRange(0x43525558L + volley, 3f);
            packet(IntrusionMap.nodeX, IntrusionMap.nodeY, aim + spread, 2.2f);
        }else if(phase() == 1){
            // Nine projectiles across exactly the forward-facing 180 degrees.
            for(int i = 0; i < 9; i++){
                packet(IntrusionMap.nodeX, IntrusionMap.nodeY, aim - 90f + i * 22.5f, 1.8f);
            }
        }else{
            // Tracks Gamma specifically, never a friendly core or the processor.
            IntrusionContent.seeker.create(build, state.rules.waveTeam,
                    IntrusionMap.nodeX, IntrusionMap.nodeY, aim);
        }
    }

    private void packet(float x, float y, float angle, float speed){
        IntrusionContent.packet.create(build, state.rules.waveTeam, x, y, angle, -1f, speed / 1.8f, 1f, null);
    }

    private void end(boolean success){
        finished = true;
        won = success;
        Groups.bullet.clear();
        Unit gamma = gamma();
        if(gamma != null){ gamma.apply(StatusEffects.invincible, 240f); gamma.apply(StatusEffects.disarmed, 240f); }
    }

    private void drawWorld(){
        Draw.draw(Layer.effect, () -> {
            Draw.color(Color.valueOf("783e40"));
            Draw.alpha(0.4f);
            Lines.stroke(1f);
            for(int x = IntrusionMap.left; x <= IntrusionMap.right; x += 8){
                Lines.line(x * 8f, IntrusionMap.bottom * 8f, x * 8f, IntrusionMap.top * 8f);
            }
            for(int y = IntrusionMap.bottom; y <= IntrusionMap.top; y += 8){
                Lines.line(IntrusionMap.left * 8f, y * 8f, IntrusionMap.right * 8f, y * 8f);
            }
            Draw.alpha(1f);
            Lines.poly(IntrusionMap.nodeX, IntrusionMap.nodeY, 6, 32f, Time.time * 0.5f);
            Draw.reset();
        });
    }

    @Override public void buildUI(Table table){ table.label(() -> Core.bundle.get("cryon.intrusion.processor-locked")).wrap().width(260f); }
    @Override public void removed(){ if(active == this) active = null; }
    @Override public void write(Writes w){
        w.i(gammaId); w.i(virusId); w.i(volley); w.f(time); w.f(shootTimer);
        w.f(lastHealth); w.f(hurtCooldown); w.f(ending); w.bool(spawned); w.bool(finished); w.bool(won);
    }
    @Override public void read(Reads r){
        gammaId = r.i(); virusId = r.i(); volley = r.i(); time = r.f(); shootTimer = r.f();
        lastHealth = r.f(); hurtCooldown = r.f(); ending = r.f(); spawned = r.bool(); finished = r.bool(); won = r.bool();
        requested = false;
    }
}
