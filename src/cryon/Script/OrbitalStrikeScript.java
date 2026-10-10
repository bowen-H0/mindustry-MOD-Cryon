package cryon.Script;

import arc.func.Cons;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.math.geom.Position;
import arc.scene.ui.layout.Table;
import cryon.Fx.OrbitalFx;
import mindustry.entities.Effect;
import mindustry.game.Team;
import mindustry.game.Teams.TeamData;
import mindustry.gen.*;

import static mindustry.Vars.*;

/**
 * 轨道打击：
 * 处理器位置显示测量图；瞄准镜始终追踪「其他阵营」的单位（优先）或建筑，
 * 蓄力完成后锁定并标记打击点，稍后毁灭光束落下，范围有限。
 *
 * 目标只会选射程(range)内的；目标与伤害都会跳过：处理器自己的阵营、derelict，以及 targetable 为 false 的单位/建筑。
 * 伤害只在服务端/单机结算；联机时画面由各端本地推进，仅建议单机或主机使用。
 */
public class OrbitalStrikeScript extends MapScript {
    public static final int TRACK = 0, LOCK = 1, STRIKE = 2, COOLDOWN = 3;
    static final String[] phaseNames = {"追踪中", "已锁定", "打击中", "冷却"};
    static final Color chartColor = Color.valueOf("5ad7ff");

    // ---- 可调参数 ----
    public float range = 400f;          // 射程：只会瞄准处理器此距离内的目标（世界单位，8 = 1 格）
    public float radius = 90f;          // 打击半径（世界单位，8 = 1 格）
    public float chargeTime = 420f;     // 蓄力追踪时长
    public float lockTime = 150f;       // 锁定后到落下的预警时长
    public float strikeTime = 100f;     // 光束持续时长
    public float cooldownTime = 240f;   // 冷却时长
    public float pulseDamage = 90f;     // 每次脉冲伤害（每 5 帧一次）

    // ---- 供特效读取的实时状态 ----
    public boolean alive;
    public int hudEpoch;
    public int phase = TRACK;
    public float timer;
    public float rx, ry;
    public float reticleAlpha;
    public boolean hasTarget;

    private Position tgt;
    private boolean needsInit = true;
    private float hudTimer = 1e6f;
    private float pulseTimer;

    // 随机选取目标用（水塘抽样，避免分配）
    private Position picked;
    private int pickCount;
    private final Cons<Unit> unitPicker = u -> {
        if(valid(u)){
            pickCount++;
            if(Mathf.chance(1f / pickCount)) picked = u;
        }
    };

    @Override
    protected void init() {
        alive = true;
    }

    /** 其他阵营：不是处理器自己的队伍，也不是 derelict */
    private boolean isEnemy(Team t) {
        return t != build.team && t != Team.derelict;
    }

    /** 可作为目标/伤害对象：敌方 + 有效 + targetable + 在射程内 */
    private boolean valid(Position p) {
        if(p instanceof Unit){
            Unit u = (Unit)p;
            return u.isValid() && isEnemy(u.team) && u.targetable(build.team) && inRange(p);
        }
        if(p instanceof Building){
            Building b = (Building)p;
            return b.isValid() && isEnemy(b.team) && b.block.targetable && inRange(p);
        }
        return false;
    }

    private boolean inRange(Position p) {
        return Mathf.within(build.x, build.y, p.getX(), p.getY(), range);
    }

    private Position pickTarget() {
        picked = null;
        pickCount = 0;
        Groups.unit.each(unitPicker);
        if(picked != null) return picked;

        for(TeamData data : state.teams.present){
            if(!isEnemy(data.team)) continue;
            for(Building b : data.buildings){
                if(valid(b)){
                    pickCount++;
                    if(Mathf.chance(1f / pickCount)) picked = b;
                }
            }
        }
        return picked;
    }

    @Override
    public void update() {
        float d = delta();
        alive = true;

        // HUD 特效是长寿命的，快到期时换一个新的（旧的靠 epoch 自动停画）
        hudTimer += d;
        if(hudTimer >= 3000f){
            hudTimer = 0f;
            hudEpoch++;
            OrbitalFx.hud.at(x(), y(), hudEpoch, chartColor, this);
        }

        switch(phase){
            case TRACK -> {
                if(!valid(tgt)) tgt = pickTarget();
                if(tgt == null){
                    hasTarget = false;
                    timer = 0f;
                    reticleAlpha = Mathf.approachDelta(reticleAlpha, 0f, 0.04f);
                    needsInit = true;
                    break;
                }
                hasTarget = true;
                if(needsInit){   // 瞄准镜从处理器位置飞向目标
                    rx = x();
                    ry = y();
                    needsInit = false;
                }
                reticleAlpha = Mathf.approachDelta(reticleAlpha, 1f, 0.05f);
                rx = Mathf.lerpDelta(rx, tgt.getX(), 0.045f);
                ry = Mathf.lerpDelta(ry, tgt.getY(), 0.045f);
                timer += d;
                if(timer >= chargeTime){   // 蓄力完成：锁定并标记打击点
                    phase = LOCK;
                    timer = 0f;
                    OrbitalFx.mark.at(rx, ry, radius);
                }
            }
            case LOCK -> {
                reticleAlpha = 1f;
                timer += d;
                if(timer >= lockTime){
                    phase = STRIKE;
                    timer = 0f;
                    pulseTimer = 0f;
                    OrbitalFx.impact.at(rx, ry, radius);
                    Effect.shake(8f, 40f, rx, ry);
                }
            }
            case STRIKE -> {
                reticleAlpha = 1f;
                timer += d;
                pulseTimer += d;
                while(pulseTimer >= 5f){
                    pulseTimer -= 5f;
                    pulse();
                }
                if(timer >= strikeTime){
                    phase = COOLDOWN;
                    timer = 0f;
                }
            }
            default -> {
                timer += d;
                if(timer >= cooldownTime){
                    phase = TRACK;
                    timer = 0f;
                    tgt = null;
                    needsInit = true;
                    reticleAlpha = 0f;
                }
            }
        }
    }

    /** 光束伤害脉冲：只伤害其他阵营中 targetable 的单位和建筑 */
    private void pulse() {
        Effect.shake(2.5f, 5f, rx, ry);
        if(net.client()) return;

        final Team own = build.team;
        final float r = radius;

        Groups.unit.intersect(rx - r, ry - r, r * 2f, r * 2f, u -> {
            if(valid(u) && u.within(rx, ry, r + u.hitSize / 2f)){
                u.damage(pulseDamage);
            }
        });

        for(TeamData data : state.teams.present){
            if(!isEnemy(data.team)) continue;
            indexer.eachBlock(data.team, rx, ry, r, b -> b.block.targetable, b -> b.damage(own, pulseDamage));
        }
    }

    @Override
    public void removed() {
        alive = false;
    }

    @Override
    public void buildUI(Table table) {
        table.row();
        table.label(() -> phaseNames[phase] + (hasTarget ? "" : "  (无目标)"));
    }
}