package cryon.Story;

import arc.files.Fi;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Time;
import cryon.Content.CryonItems;
import cryon.Script.DesolatePlainScript;
import cryon.Type.NuclearKamikazeAI;
import mindustry.ai.types.BuilderAI;
import mindustry.content.Blocks;
import mindustry.content.StatusEffects;
import mindustry.entities.units.AIController;
import mindustry.entities.units.BuildPlan;
import mindustry.game.Schematic;
import mindustry.game.Schematics;
import mindustry.gen.*;
import mindustry.type.UnitType;
import mindustry.world.Build;

import static mindustry.Vars.*;

/** Battlefield reinforcements and native builder jobs. State is kept in map tags and unit flags. */
public final class DesolateBattlefield{
    public static final double builderFlag = 841000d, assistantFlag = 842000d, repairFlag = 843000d;
    public static final double checkedFlag = 844001d, armedFlag = 844002d;
    public static final int maxBuilders = 3, maxAssistants = 6, maxRepairers = 6;
    public static final int maxBombers = 16, maxStations = 18;
    private static final String jobPrefix = "cryon-desolate-job-", timerKey = "cryon-desolate-support-time";
    private static final String waveKey = "cryon-desolate-support-wave", countKey = "cryon-desolate-station-count";
    private static final String deployedKey = "cryon-desolate-support-deployed";
    private static final String[] blueprintNames = {"大型治疗塔", "治疗塔", "小防御塔", "掩体", "防御"};
    private static final int[] blueprintCycle = {0, 1, 0, 2, 1, 3, 0, 4};
    private final DesolatePlainScript director;
    private final Schematic[] blueprints = new Schematic[blueprintNames.length];
    private final Schematics planner = new Schematics();
    private float scanTime;
    private boolean firstScan = true;
    private final Vec2 cachedCenter = new Vec2();
    private float centerAge = 60f;

    public DesolateBattlefield(DesolatePlainScript director){ this.director = director; }

    public void update(){
        if(net.client() || state.isEditor() || state.gameOver || director.phase == DesolatePlainScript.complete) return;
        if(director.phase == DesolatePlainScript.intro) return;
        centerAge += director.build.delta();
        scanTime += director.build.delta();
        if(scanTime < 60f && !firstScan) return;
        float elapsed = scanTime;
        scanTime = 0f;
        firstScan = false;
        int bombers = Groups.unit.count(u -> !u.dead && u.team == state.rules.waveTeam && u.flag == armedFlag);
        for(Unit unit : Groups.unit){
            if(unit.dead || unit.team != state.rules.waveTeam) continue;
            if(role(unit, builderFlag, maxBuilders)){
                if(!(unit.controller() instanceof StationBuilderAI)) unit.controller(new StationBuilderAI(this));
            }else if(role(unit, assistantFlag, maxAssistants)){
                if(!(unit.controller() instanceof StationAssistAI)) unit.controller(new StationAssistAI());
            }else if(role(unit, repairFlag, maxRepairers)){
                if(!(unit.controller() instanceof RepairEscortAI)) unit.controller(new RepairEscortAI(this));
            }else if(unit.stack.item == CryonItems.nuclearExplosive && unit.stack.amount > 0){
                if(!(unit.controller() instanceof NuclearKamikazeAI)) unit.controller(new NuclearKamikazeAI());
            }else if(eligibleBomber(unit)){
                unit.flag = checkedFlag;
                if(bombers < maxBombers && Mathf.randomSeed((long)unit.id * 11731L + state.wave * 31L) < nuclearChance(state.wave)){
                    armBomber(unit);
                    bombers++;
                }
            }
        }
        float timer = state.rules.tags.getFloat(timerKey, 0f) + elapsed;
        state.rules.tags.put(timerKey, Float.toString(timer));
        int lastWave = state.rules.tags.getInt(waveKey, 1);
        if(hasCombatUnits() && (state.wave >= lastWave + 5 || timer >= (state.rules.tags.getBool(deployedKey) ? 5400f : 1800f))){
            deploySupport();
            state.rules.tags.put(waveKey, Integer.toString(state.wave));
            state.rules.tags.put(timerKey, "0");
        }
    }

    public static float nuclearChance(int wave){ return 0.15f + Math.min(0.2f, wave * 0.002f); }
    private static boolean role(Unit unit, double flag, int count){ return unit.flag >= flag && unit.flag < flag + count; }
    public static boolean support(Unit unit){
        return role(unit, builderFlag, maxBuilders) || role(unit, assistantFlag, maxAssistants) || role(unit, repairFlag, maxRepairers);
    }
    private boolean eligibleBomber(Unit unit){
        return unit.flag == 0d && unit.isFlying() && !unit.isMissile() && unit.itemCapacity() > 0
            && !unit.isPlayer() && !unit.hasEffect(StatusEffects.boss)
            && !unit.type.name.equals("cryon-libration") && !unit.type.name.equals("cryon-nadir") && !unit.type.name.equals("cryon-exotic");
    }
    public void armBomber(Unit unit){
        if(net.client() || unit.team != state.rules.waveTeam || unit.itemCapacity() <= 0) return;
        unit.stack.set(CryonItems.nuclearExplosive, Math.min(unit.itemCapacity(), Mathf.clamp(1 + (int)(unit.hitSize / 12f), 1, 6)));
        unit.flag = armedFlag;
        unit.controller(new NuclearKamikazeAI());
    }

    private boolean hasCombatUnits(){
        return Groups.unit.contains(u -> u.team == state.rules.waveTeam && !u.dead && !support(u));
    }

    public Vec2 denseCenter(){
        if(centerAge < 60f) return cachedCenter;
        centerAge = 0f;
        Unit best = null;
        int bestCount = -1;
        Seq<Unit> friends = Groups.unit.copy().select(u -> u.team == state.rules.waveTeam && !u.dead && !support(u));
        int step = Math.max(1, friends.size / 64);
        for(int i = 0; i < friends.size; i += step){
            Unit candidate = friends.get(i);
            int count = friends.count(u -> u.within(candidate, 480f));
            if(count > bestCount){ best = candidate; bestCount = count; }
        }
        if(best == null) return cachedCenter.set(director.build.x, director.build.y);
        Vec2 center = new Vec2();
        int count = 0;
        for(Unit friend : friends) if(friend.within(best, 480f)){ center.add(friend.x, friend.y); count++; }
        return cachedCenter.set(center.scl(1f / Math.max(1, count)));
    }

    public void deploySupport(){
        if(net.client()) return;
        state.rules.tags.put(deployedKey, "true");
        Vec2 center = denseCenter();
        spawnRole("cryon-libration", builderFlag, maxBuilders, 1, center);
        spawnRole("cryon-nadir", assistantFlag, maxAssistants, 2, center);
        spawnRole("cryon-exotic", repairFlag, maxRepairers, 2, center);
    }
    private void spawnRole(String name, double flag, int max, int amount, Vec2 center){
        UnitType type = content.unit(name);
        if(type == null) throw new IllegalStateException("Missing desolate support unit " + name);
        for(int slot = 0; slot < max && amount > 0; slot++){
            final double value = flag + slot;
            if(Groups.unit.contains(u -> !u.dead && u.team == state.rules.waveTeam && u.flag == value)) continue;
            Unit unit = type.create(state.rules.waveTeam);
            unit.flag = value;
            unit.set(Mathf.clamp(center.x + Mathf.random(-70f, 70f), 32f, world.unitWidth() - 32f),
                Mathf.clamp(center.y + Mathf.random(-70f, 70f), 32f, world.unitHeight() - 32f));
            if(flag == builderFlag) unit.controller(new StationBuilderAI(this));
            else if(flag == assistantFlag) unit.controller(new StationAssistAI());
            else unit.controller(new RepairEscortAI(this));
            unit.add();
            Call.effect(mindustry.content.Fx.unitSpawn, unit.x, unit.y, 0f, unit.team.color);
            amount--;
        }
    }

    private Schematic blueprint(int index){
        if(blueprints[index] != null) return blueprints[index];
        try{
            var mod = mods.locateMod("cryon");
            Fi file = mod == null ? tree.get("schematics/" + blueprintNames[index] + ".msch")
                : mod.root.child("schematics").child(blueprintNames[index] + ".msch");
            blueprints[index] = Schematics.read(file);
            return blueprints[index];
        }catch(Exception error){ throw new IllegalStateException("Cannot read station blueprint " + blueprintNames[index], error); }
    }

    private String jobKey(Unit builder){ return jobPrefix + (long)builder.flag; }
    private boolean reserved(int x, int y, int radius, String own){
        for(int i = 0; i < maxBuilders; i++){
            String key = jobPrefix + (long)(builderFlag + i);
            if(key.equals(own)) continue;
            String value = state.rules.tags.get(key, "");
            if(value.isEmpty()) continue;
            String[] args = value.split(",");
            if(args.length != 3) continue;
            int dx = Integer.parseInt(args[1]) - x, dy = Integer.parseInt(args[2]) - y;
            if(Math.abs(dx) < radius + 10 && Math.abs(dy) < radius + 10) return true;
        }
        return false;
    }
    private Seq<BuildPlan> plans(int index, int x, int y){ return planner.toPlans(blueprint(index), x, y); }
    public boolean assignStation(Unit builder){
        if(net.client() || !builder.plans.isEmpty()) return false;
        String key = jobKey(builder), saved = state.rules.tags.get(key, "");
        if(!saved.isEmpty()){
            String[] args = saved.split(",");
            if(args.length == 3){
                Seq<BuildPlan> restore = plans(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
                for(BuildPlan plan : restore){
                    if(plan.tile() == null) continue;
                    if(plan.tile().block() == plan.block && plan.tile().team() == builder.team) continue;
                    if(Build.validPlace(plan.block, builder.team, plan.x, plan.y, plan.rotation)
                        || plan.tile().build instanceof mindustry.world.blocks.ConstructBlock.ConstructBuild) builder.addBuild(plan, false);
                }
                if(!builder.plans.isEmpty()) return true;
            }
            state.rules.tags.remove(key);
        }
        Vec2 center = denseCenter();
        int sequence = state.rules.tags.getInt(countKey, 0);
        if(sequence >= maxStations) return false;
        int index = blueprintCycle[sequence % blueprintCycle.length];
        Schematic schematic = blueprint(index);
        int start = Math.max(schematic.width, schematic.height) / 2 + 2;
        for(int radius = start; radius <= start + 35; radius += 3){
            for(int direction = 0; direction < 12; direction++){
                Vec2 offset = new Vec2().trns(direction * 30f, radius);
                int x = Math.round(center.x / tilesize + offset.x), y = Math.round(center.y / tilesize + offset.y);
                if(reserved(x, y, start, key)) continue;
                Seq<BuildPlan> plans = plans(index, x, y);
                boolean valid = !plans.isEmpty();
                for(BuildPlan plan : plans){
                    if(plan.tile() == null || plan.tile().block() != Blocks.air
                        || !Build.validPlace(plan.block, builder.team, plan.x, plan.y, plan.rotation)){ valid = false; break; }
                }
                if(!valid) continue;
                for(BuildPlan plan : plans) builder.addBuild(plan, false);
                state.rules.tags.put(key, index + "," + x + "," + y);
                state.rules.tags.put(countKey, Integer.toString(sequence + 1));
                return true;
            }
        }
        return false;
    }

    public static final class StationBuilderAI extends AIController{
        private final DesolateBattlefield field;
        private float retry;
        public StationBuilderAI(DesolateBattlefield field){ this.field = field; }
        @Override public void updateUnit(){
            if(net.client() || unit.dead) return;
            unit.updateBuilding = true;
            unit.controlWeapons(false, false);
            if(unit.plans.isEmpty() && (retry -= Time.delta) <= 0f){
                field.assignStation(unit);
                retry = 180f;
            }
            BuildPlan plan = unit.buildPlan();
            if(plan != null){ unit.lookAt(plan); moveTo(plan, unit.type.buildRange * 0.65f, 20f); }
            else moveTo(field.denseCenter(), 100f, 20f);
        }
    }
    public static final class StationAssistAI extends BuilderAI{
        public StationAssistAI(){ onlyAssist = true; alwaysFlee = false; }
        @Override public void updateMovement(){
            if(assistFollowing == null || assistFollowing.dead || !assistFollowing.isAdded() || !assistFollowing.activelyBuilding()){
                assistFollowing = Groups.unit.copy().select(u -> u.team == unit.team && !u.dead
                    && role(u, builderFlag, maxBuilders) && !u.plans.isEmpty()).min(u -> u.dst2(unit));
            }
            super.updateMovement();
        }
    }
    public static final class RepairEscortAI extends AIController{
        private final DesolateBattlefield field;
        public Unit repairTarget;
        private float search;
        public RepairEscortAI(DesolateBattlefield field){ this.field = field; }
        @Override public void updateUnit(){
            if(net.client() || unit.dead) return;
            if(repairTarget == null || repairTarget.dead || !repairTarget.isAdded() || repairTarget.health >= repairTarget.maxHealth
                || (search -= Time.delta) <= 0f){
                repairTarget = Groups.unit.copy().select(u -> u != unit && u.team == unit.team && !u.dead && u.health < u.maxHealth)
                    .min(u -> u.dst(unit) * (0.3f + u.health / u.maxHealth));
                search = 30f;
            }
            if(repairTarget != null){
                moveTo(repairTarget, 55f, 12f);
                unit.lookAt(repairTarget);
                unit.aim(repairTarget);
                unit.controlWeapons(true, true);
            }else{
                unit.controlWeapons(false, false);
                moveTo(field.denseCenter(), 80f, 20f);
            }
        }
    }
}
