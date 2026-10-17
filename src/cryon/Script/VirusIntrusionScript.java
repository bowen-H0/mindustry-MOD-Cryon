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
import mindustry.type.UnitType;
import mindustry.content.UnitTypes;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;

import static mindustry.Vars.*;

/** Native player/Boss combat controlled by the isolation map processor. */
public final class VirusIntrusionScript extends MapScript {
    public static final float introTicks = 720f, combatSeconds = 120f, attackModeTicks = 600f;
    public static final double gammaFlag = 730001d, virusFlag = 730002d;
    public static final float playerHitSize = 6f, recoveryPerSecond = 6f, upgradedHealth = 360f, upgradedRecoveryPerSecond = 2f;
    private static final mindustry.entities.abilities.Ability[] noAbilities = new mindustry.entities.abilities.Ability[0];
    public static final float rageModeTicks = 360f, transformTicks = 360f;
    private static final String transformKey = "cryon-intrusion-transform-time";
    private static final String transformXKey = "cryon-intrusion-transform-x", transformYKey = "cryon-intrusion-transform-y";
    private static final String rageKey = "cryon-intrusion-rage-start";
    private static final String aimKey = "cryon-intrusion-warning-angle";
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
    public boolean transforming(){ return state.rules.tags.containsKey(transformKey); }
    public float transformSeconds(){ return Float.parseFloat(state.rules.tags.get(transformKey, "0")) / 60f; }
    public boolean enraged(){ return state.rules.tags.containsKey(rageKey); }
    public int bossStage(){ return enraged() ? 2 : 1; }
    public int phase(){
        float start = enraged() ? Float.parseFloat(state.rules.tags.get(rageKey)) : introTicks;
        return (int)(Math.max(0f, time - start) / (enraged() ? rageModeTicks : attackModeTicks)) % (enraged() ? 4 : 3);
    }
    public static UnitType playerUnitType(){
        UnitType type = content.unit("cryon-transliminal");
        if(type == null) throw new IllegalStateException("Isolation player unit cryon-transliminal is missing");
        return type;
    }
    public Unit controlledPlayer(){ return gamma(); }
    /** Retained for existing scripts and tooling; now returns the controlled Transliminal. */
    public Unit controlledGamma(){ return controlledPlayer(); }
    public Unit virusNode(){ return node(); }
    public boolean recoveryActive(){ return state.rules.tags.getBool(recoveryKey); }
    private void updateSecondStage(){
        // Remove the former support processor from older isolation-space saves too.
        if(world.build(56, 30) instanceof cryon.Type.JavaProcessor.JavaProcessorBuild
                && ((cryon.Type.JavaProcessor.JavaProcessorBuild)world.build(56, 30)).script instanceof OrbitalStrikeScript){
            world.tile(56, 30).remove();
        }
        if(!enraged() && !transforming() && nodeHealth() <= 0.5f){
            state.rules.tags.put(transformKey, "0");
            state.rules.tags.put(transformXKey, Float.toString(gamma().x));
            state.rules.tags.put(transformYKey, Float.toString(gamma().y));
            state.rules.tags.remove(aimKey);
            Groups.bullet.clear();
            volley = 0;
            shootTimer = 0f;
        }
    }

    private void updateTransformation(Unit pilot, Unit boss){
        // Keep the simulation running so the processor/UI can advance, while combat is frozen.
        Groups.bullet.clear();
        pilot.set(Float.parseFloat(state.rules.tags.get(transformXKey, Float.toString(pilot.x))),
                Float.parseFloat(state.rules.tags.get(transformYKey, Float.toString(pilot.y))));
        pilot.vel.setZero();
        pilot.controlWeapons(false, false);
        pilot.plans.clear();
        pilot.mineTile = null;
        boss.vel.setZero();
        for(Unit unit : new Unit[]{pilot, boss}){
            unit.apply(StatusEffects.unmoving, 2f);
            unit.apply(StatusEffects.disarmed, 2f);
            unit.apply(StatusEffects.invincible, 2f);
        }
        if(player != null && player.unit() != pilot){
            player.team(state.rules.defaultTeam);
            player.unit(pilot);
        }
        float elapsed = Float.parseFloat(state.rules.tags.get(transformKey, "0")) + delta();
        state.rules.tags.put(transformKey, Float.toString(elapsed));
        if(elapsed >= transformTicks){
            state.rules.tags.remove(transformKey);
            state.rules.tags.remove(transformXKey);
            state.rules.tags.remove(transformYKey);
            state.rules.tags.put(rageKey, Float.toString(time));
            for(Unit unit : new Unit[]{pilot, boss}){
                unit.unapply(StatusEffects.unmoving);
                unit.unapply(StatusEffects.disarmed);
                unit.unapply(StatusEffects.invincible);
            }
            pilot.apply(StatusEffects.invincible, 60f);
            arc.util.Log.info("[CryonIntrusion] Transformation dialogue complete; entering stage 2.");
        }
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
        if(node.maxHealth != IntrusionContent.virus.health){
            node.health = IntrusionContent.virus.health * Mathf.clamp(node.health / Math.max(1f, node.maxHealth));
            node.maxHealth = IntrusionContent.virus.health;
        }
        if(time >= introTicks) updateSecondStage();
        if(transforming()){
            updateTransformation(gamma, node);
            return;
        }
        if(enraged() && gamma.type != playerUnitType()){
            Unit upgraded = playerUnitType().create(state.rules.defaultTeam);
            upgraded.set(gamma.x, gamma.y);
            upgraded.rotation = gamma.rotation;
            upgraded.flag = gammaFlag;
            limitUpgradedPlayer(upgraded);
            upgraded.health = upgraded.maxHealth * Mathf.clamp(gamma.health / Math.max(1f, gamma.maxHealth));
            // Preserve temporary protection/disarm across migration of older saves.
            if(gamma.hasEffect(StatusEffects.invincible)) upgraded.apply(StatusEffects.invincible, 60f);
            if(gamma.hasEffect(StatusEffects.disarmed)) upgraded.apply(StatusEffects.disarmed, 2f);
            gamma.remove();
            upgraded.add();
            gammaId = upgraded.id;
            gamma = upgraded;
            lastHealth = gamma.health;
        }
        if(gamma.type == UnitTypes.gamma){
            gamma.maxHealth = 300f;
            gamma.health = Math.min(300f, gamma.health);
            gamma.armor = 0f;
        }
        if(gamma.type == playerUnitType()) limitUpgradedPlayer(gamma);
        gamma.hitSize = playerHitSize;
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
            arc.util.Log.info("[CryonIntrusion] Intro complete; player combat started.");
        }
        if(phase() != previousMode){
            if(enraged()) clearEnemyBullets();
            state.rules.tags.remove(aimKey);
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
            gamma.heal((enraged() ? upgradedRecoveryPerSecond : recoveryPerSecond) * delta() / 60f);
            lastHealth = gamma.health;
        }
        shootTimer -= delta();
        if(shootTimer <= 0f){
            if(enraged()){
                shootTimer = Math.max(0f, shootTimer) + (phase() == 0 ? 12f : phase() == 1 ? 60f
                        : phase() == 2 ? (volley % 4 == 0 ? 42f : volley % 4 == 3 ? 84f : 32f)
                        : (volley % 2 == 0 ? 42f : 84f));
            }else{
                shootTimer += phase() == 0 ? (volley % 3 == 2 ? 96f : 12f) : phase() == 1 ? 90f : 108f;
            }
            emit(gamma);
            volley++;
        }
    }

    private void limitUpgradedPlayer(Unit unit){
        // Apply only to this encounter's entity; keep the campaign UnitType untouched.
        if(unit.maxHealth != upgradedHealth){
            unit.health = upgradedHealth * Mathf.clamp(unit.health / Math.max(1f, unit.maxHealth));
            unit.maxHealth = upgradedHealth;
        }
        unit.hitSize = playerHitSize;
        unit.armor = 0f;
        unit.shield = 0f;
        unit.abilities = noAbilities;
    }

    private void spawn(){
        spawned = true;
        Unit gamma = (enraged() ? playerUnitType() : UnitTypes.gamma).create(state.rules.defaultTeam);
        if(gamma.type == UnitTypes.gamma) gamma.maxHealth = gamma.health = 300f;
        else limitUpgradedPlayer(gamma);
        gamma.set(IntrusionMap.playerX, IntrusionMap.playerY);
        gamma.hitSize = playerHitSize;
        gamma.flag = gammaFlag;
        lastHealth = gamma.health;
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
        state.rules.teams.get(state.rules.defaultTeam).unitHealthMultiplier = 1f;
        state.rules.teams.get(state.rules.defaultTeam).unitDamageMultiplier = enraged() ? 0.8f : 1.5f;
        state.rules.possessionAllowed = state.rules.schematicsAllowed = false;
        state.rules.logicUnitControl = false;
    }

    private void emit(Unit gamma){
        if(Groups.bullet.size() > 500) return;
        float aim = Angles.angle(IntrusionMap.nodeX, IntrusionMap.nodeY, gamma.x, gamma.y);
        if(enraged()){
            emitRage(aim);
            return;
        }
        if(phase() == 0){
            // Three consecutive aimed shots; each shot reacquires the player with small spread.
            float spread = Mathf.randomSeedRange(0x43525558L + volley, 3f);
            packet(IntrusionMap.nodeX, IntrusionMap.nodeY, aim + spread, 2.2f);
        }else if(phase() == 1){
            // Seven projectiles across exactly the forward-facing 180 degrees.
            int count = 7;
            for(int i = 0; i < count; i++){
                packet(IntrusionMap.nodeX, IntrusionMap.nodeY, aim - 90f + i * 180f / (count - 1), 1.8f);
            }
        }else{
            // Tracks the controlled player specifically, never a friendly core or the processor.
            int count = 1;
            for(int i = 0; i < count; i++){
                mindustry.gen.Bullet bullet = IntrusionContent.seeker.create(build, state.rules.waveTeam,
                        IntrusionMap.nodeX, IntrusionMap.nodeY, aim + (i - (count - 1) / 2f) * 18f);
            }
        }
    }

    private void clearEnemyBullets(){
        Groups.bullet.each(bullet -> { if(bullet.team == state.rules.waveTeam) bullet.remove(); });
    }

    private void emitRage(float aim){
        if(phase() == 0){
            // Three rotating arms: advance sixteen degrees per step, leaving broad lanes.
            for(int arm = 0; arm < 3; arm++) ragePacket(volley * 16f + arm * 120f, 2.3f);
        }else if(phase() == 1){
            // A ring with a 96-degree safe opening that sways around the player's direction.
            float gap = aim + Mathf.sin(volley * 0.65f) * 35f;
            for(int i = 2; i <= 13; i++) ragePacket(gap + i * 24f, 1.9f);
        }else if(phase() == 2){
            if(volley % 4 == 0){
                float gap = aim + 60f;
                state.rules.tags.put(aimKey, Float.toString(gap));
                // Native Lancer charge effects mark the actual segment origins.
                for(int wave = 0; wave < 3; wave++){
                    float radius = 140f + wave * 100f;
                    for(int i = 5; i < 31; i++){
                        float angle = gap + i * 10f;
                        mindustry.content.Fx.lancerLaserCharge.at(IntrusionMap.nodeX + Angles.trnsx(angle, radius),
                                IntrusionMap.nodeY + Angles.trnsy(angle, radius), angle + 95f, Color.valueOf("be78ed"));
                    }
                }
            }else{
                int wave = volley % 4 - 1;
                float gap = Float.parseFloat(state.rules.tags.get(aimKey, Float.toString(aim + 60f)));
                float radius = 140f + wave * 100f;
                for(int i = 5; i < 31; i++){
                    float angle = gap + i * 10f;
                    IntrusionContent.ringLasers[wave].create(build, state.rules.waveTeam,
                            IntrusionMap.nodeX + Angles.trnsx(angle, radius),
                            IntrusionMap.nodeY + Angles.trnsy(angle, radius), angle + 95f);
                }
                if(wave == 2) state.rules.tags.remove(aimKey);
            }
        }else if(volley % 2 == 0){
            state.rules.tags.put(aimKey, Float.toString(aim));
            mindustry.content.Fx.lightningCharge.at(IntrusionMap.nodeX, IntrusionMap.nodeY, aim, Color.valueOf("be78ed"));
        }else{
            float lockedAim = Float.parseFloat(state.rules.tags.get(aimKey, Float.toString(aim)));
            IntrusionContent.chainLightning.create(build, state.rules.waveTeam,
                    IntrusionMap.nodeX, IntrusionMap.nodeY, lockedAim);
            state.rules.tags.remove(aimKey);
        }
    }

    private void ragePacket(float angle, float speed){
        mindustry.gen.Bullet bullet = IntrusionContent.packet.create(build, state.rules.waveTeam,
                IntrusionMap.nodeX, IntrusionMap.nodeY, angle, -1f, speed / 1.8f, 1f, null);
        bullet.lifetime = 260f;
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
        Draw.draw(Layer.blockOver, () -> {
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
