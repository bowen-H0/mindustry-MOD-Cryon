package cryon.Script;

import arc.graphics.Color;
import arc.math.Interp;
import arc.math.Mathf;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.struct.FloatSeq;
import arc.struct.IntSet;
import arc.struct.Seq;
import arc.util.io.*;
import cryon.Fx.NukeFx;
import mindustry.entities.Effect;
import mindustry.game.Team;
import mindustry.game.Teams.TeamData;
import mindustry.gen.*;
import mindustry.ui.Styles;

import static mindustry.Vars.*;

/**
 * 核弹打击（一次性，战略级）。
 *
 * 流程：IDLE(待机) -> COUNT(倒计时) -> LOCK(提前 20 秒选定落点并标记) -> FALL(导弹坠落)
 *       -> BLAST(冲击波逐层推进并结算伤害) -> DONE(已用完，脚本不再运行)
 *
 * 落点：在所有敌方建筑位置里做候选，评分 = 覆盖的敌方建筑数 - 友军惩罚 * 覆盖的友军建筑数，
 *       再向覆盖区质心迭代微调。敌方 = 非处理器队伍且非 derelict；友军 = 处理器所在队伍。
 * 威力：半径 = 240 * power（世界单位）。0.6 倍半径内直接蒸发，
 *       外圈按距离衰减伤害（边缘轻微）。targetable = false 的建筑不会受伤。
 * 伤害只在服务端/单机结算。
 */
public class NukeStrikeScript extends MapScript {
    public static final int IDLE = 0, COUNT = 1, LOCK = 2, FALL = 3, BLAST = 4, DONE = 5;

    // ---- 可设置 ----
    public float delayMinutes = 5f;     // 几分钟后打击
    public float power = 5f;            // 威力 1~10：半径 = 240 * power
    public boolean friendlyFire = true; // 爆炸是否波及友军
    public float friendlyPenalty = 6f;  // 选点时每个友军建筑相当于扣多少个敌军建筑

    // ---- 固定参数 ----
    public float lockLead = 1200f;      // 落点提前多久锁定（20 秒）
    public float fallTime = 150f;       // 导弹坠落时长
    public float waveTime = 300f;       // 冲击波推进到最大半径的时长
    public float blastTotal = 720f;     // 爆炸画面总时长
    static final float KILL_ZONE = 0.6f;   // 致死区占半径比例
    static final int MAX_HITS_PER_TICK = 200;

    // ---- 状态 / 供特效读取 ----
    public boolean alive;
    public int hudEpoch;
    public int phase = IDLE;
    public float remaining, totalTicks;
    public float tx, ty, blastRadius;
    public float fallTimer, blastTimer, waveR;
    public int coveredEnemy, coveredFriendly;
    private boolean noTarget;
    private float retryTimer, hudTimer = 1e6f;

    // ---- 选点 / 爆炸缓存 ----
    private final FloatSeq ex = new FloatSeq(), ey = new FloatSeq(), fx = new FloatSeq(), fy = new FloatSeq();
    private int cEnemy, cFriend;
    private final Seq<Building> blastBuildings = new Seq<>();
    private int buildPtr;
    private final IntSet hitUnits = new IntSet();

    @Override
    protected void init() {
        alive = true;
    }

    public static float radiusFor(float power) {
        return 240f * power;
    }

    // ================= 选点 =================

    private float count(float cx, float cy, float R) {
        float r2 = R * R;
        int e = 0, f = 0;
        for(int i = 0; i < ex.size; i++) if(Mathf.dst2(ex.get(i), ey.get(i), cx, cy) <= r2) e++;
        for(int i = 0; i < fx.size; i++) if(Mathf.dst2(fx.get(i), fy.get(i), cx, cy) <= r2) f++;
        cEnemy = e;
        cFriend = f;
        return e - friendlyPenalty * f;
    }

    /** 选出覆盖最多敌方建筑、友军尽量少的落点，找不到敌方建筑返回 false */
    private boolean chooseTarget() {
        float R = radiusFor(power);
        ex.clear(); ey.clear(); fx.clear(); fy.clear();
        Team own = build.team;

        for(TeamData data : state.teams.present){
            Team t = data.team;
            if(t == Team.derelict) continue;
            boolean friendly = t == own;
            if(friendly && !friendlyFire) continue;
            for(Building b : data.buildings){
                if(!b.isValid() || !b.block.targetable) continue;
                if(friendly){ fx.add(b.x); fy.add(b.y); }
                else{ ex.add(b.x); ey.add(b.y); }
            }
        }
        if(ex.size == 0) return false;

        // 候选点：敌方建筑位置（过多时等距抽样到 ~500 个）
        int step = Math.max(1, ex.size / 500);
        float best = -Float.MAX_VALUE, bx = ex.get(0), by = ey.get(0);
        for(int i = 0; i < ex.size; i += step){
            float s = count(ex.get(i), ey.get(i), R);
            if(s > best){ best = s; bx = ex.get(i); by = ey.get(i); }
        }

        // 向被覆盖敌方建筑的质心微调几轮
        float r2 = R * R;
        for(int it = 0; it < 4; it++){
            float sx = 0f, sy = 0f;
            int n = 0;
            for(int i = 0; i < ex.size; i++){
                if(Mathf.dst2(ex.get(i), ey.get(i), bx, by) <= r2){
                    sx += ex.get(i);
                    sy += ey.get(i);
                    n++;
                }
            }
            if(n == 0) break;
            float cx = sx / n, cy = sy / n;
            float s = count(cx, cy, R);
            if(s > best){ best = s; bx = cx; by = cy; } else break;
        }

        count(bx, by, R);
        coveredEnemy = cEnemy;
        coveredFriendly = cFriend;
        tx = bx;
        ty = by;
        blastRadius = R;
        return true;
    }

    // ================= 主循环 =================

    @Override
    public void update() {
        if(phase == DONE){
            alive = false;
            return;
        }
        alive = true;
        float d = delta();

        hudTimer += d;
        if(hudTimer >= 3000f){
            hudTimer = 0f;
            hudEpoch++;
            NukeFx.hud.at(x(), y(), hudEpoch, Color.white, this);
        }

        switch(phase){
            case COUNT -> {
                remaining -= d;
                if(remaining <= lockLead){
                    remaining = lockLead;   // 没找到目标就停在 20 秒，持续重试
                    retryTimer -= d;
                    if(retryTimer <= 0f){
                        retryTimer = 120f;
                        noTarget = !chooseTarget();
                        if(!noTarget){
                            phase = LOCK;
                            Effect.shake(3f, 20f, tx, ty);
                        }
                    }
                }
            }
            case LOCK -> {
                remaining -= d;
                if(remaining <= 0f){
                    remaining = 0f;
                    phase = FALL;
                    fallTimer = 0f;
                }
            }
            case FALL -> {
                fallTimer += d;
                if(fallTimer >= fallTime) startBlast();
            }
            case BLAST -> updateBlast(d);
            default -> {}
        }
    }

    // ================= 爆炸 =================

    private boolean hitTeam(Team t) {
        return t != Team.derelict && (friendlyFire || t != build.team);
    }

    private void startBlast() {
        phase = BLAST;
        blastTimer = 0f;
        waveR = 0f;
        buildPtr = 0;
        hitUnits.clear();
        blastBuildings.clear();

        Effect.shake(25f, 120f, tx, ty);

        // 收集范围内所有会受影响的建筑，按距离排序，随冲击波推进依次结算
        for(TeamData data : state.teams.present){
            if(!hitTeam(data.team)) continue;
            for(Building b : data.buildings){
                if(b.isValid() && b.block.targetable && Mathf.within(b.x, b.y, tx, ty, blastRadius)){
                    blastBuildings.add(b);
                }
            }
        }
        final float fx0 = tx, fy0 = ty;
        blastBuildings.sort(b -> Mathf.dst2(b.x, b.y, fx0, fy0));
    }

    private void updateBlast(float d) {
        blastTimer += d;
        float wp = Mathf.clamp(blastTimer / waveTime);
        waveR = blastRadius * Interp.pow2Out.apply(wp);

        if(blastTimer < waveTime && ((int)blastTimer) % 15 == 0){
            Effect.shake(14f * (1f - wp) + 3f, 20f, tx, ty);
        }

        if(!net.client()){
            int hits = 0;
            while(buildPtr < blastBuildings.size && hits < MAX_HITS_PER_TICK){
                Building b = blastBuildings.get(buildPtr);
                float dd = Mathf.dst(b.x, b.y, tx, ty);
                if(dd > waveR) break;
                buildPtr++;
                hits++;
                if(b.isValid()) damageBuilding(b, dd);
            }

            final float curR = waveR;
            Groups.unit.each(u -> {
                if(!u.isValid() || hitUnits.contains(u.id) || !hitTeam(u.team)) return;
                if(!u.targetable(build.team) && u.team != build.team) return;
                float dd = Mathf.dst(u.x, u.y, tx, ty);
                if(dd > curR) return;
                hitUnits.add(u.id);
                damageUnit(u, dd);
            });
        }

        if(blastTimer >= blastTotal){
            phase = DONE;     // 一次性：炸完就没了
            alive = false;
        }
    }

    /** 0..1：致死区内为 1(直接蒸发)，往外线性衰减到边缘 0 */
    private float falloff(float dd) {
        float k = KILL_ZONE * blastRadius;
        if(dd <= k) return -1f;
        return 1f - (dd - k) / (blastRadius - k);
    }

    private void damageBuilding(Building b, float dd) {
        float f = falloff(dd);
        if(f < 0f) b.kill();
        else b.damage(build.team, b.maxHealth() * 2f * f);
    }

    private void damageUnit(Unit u, float dd) {
        float f = falloff(dd);
        if(f < 0f) u.kill();
        else u.damage(u.maxHealth * 2f * f);
    }

    // ================= 界面 / 存档 =================

    private String statusText() {
        switch(phase){
            case IDLE: return "待机";
            case COUNT:
                if(remaining <= lockLead) return noTarget ? "等待目标…" : "即将锁定";
                return "倒计时 " + fmt(remaining);
            case LOCK: return "已锁定 " + fmt(remaining) + "  敌" + coveredEnemy + " / 友" + coveredFriendly;
            case FALL: return "坠落中";
            case BLAST: return "爆炸中";
            default: return "已使用";
        }
    }

    private static String fmt(float ticks) {
        int sec = (int)Math.ceil(ticks / 60f);
        return (sec / 60) + ":" + (sec % 60 < 10 ? "0" : "") + (sec % 60);
    }

    @Override
    public void buildUI(Table table) {
        table.row();
        table.label(() -> "延迟 " + (int)delayMinutes + " 分钟");
        table.row();
        table.slider(1f, 60f, 1f, delayMinutes, v -> {
            if(phase == IDLE) delayMinutes = v;
        }).width(220f);

        table.row();
        table.label(() -> "威力 " + power + "  (半径 " + (int)(radiusFor(power) / tilesize) + " 格)");
        table.row();
        table.slider(1f, 10f, 0.5f, power, v -> {
            if(phase < LOCK) power = v;
        }).width(220f);

        table.row();
        table.label(this::statusText);

        table.row();
        TextButton btn = table.button("启动倒计时", Styles.flatt, () -> {
            if(phase == IDLE){
                totalTicks = delayMinutes * 3600f;
                remaining = totalTicks;
                noTarget = false;
                retryTimer = 0f;
                phase = COUNT;
            }else if(phase == COUNT || phase == LOCK){
                phase = IDLE;   // 导弹落下前可取消
            }
        }).size(160f, 40f).get();
        btn.update(() -> {
            btn.setText(phase == IDLE ? "启动倒计时" : (phase == COUNT || phase == LOCK) ? "取消" : "—");
            btn.setDisabled(phase > LOCK);
        });
    }

    @Override
    public void removed() {
        alive = false;
    }

    @Override
    public void write(Writes write) {
        write.f(delayMinutes);
        write.f(power);
        write.b(phase);
        write.f(remaining);
        write.f(totalTicks);
        write.f(tx);
        write.f(ty);
        write.f(blastRadius);
        write.i(coveredEnemy);
        write.i(coveredFriendly);
    }

    @Override
    public void read(Reads read) {
        delayMinutes = read.f();
        power = read.f();
        phase = read.b();
        remaining = read.f();
        totalTicks = read.f();
        tx = read.f();
        ty = read.f();
        blastRadius = read.f();
        coveredEnemy = read.i();
        coveredFriendly = read.i();
        // 爆炸/坠落的中间态不存档：坠落重来，爆炸中读档视为已完成
        if(phase == FALL) fallTimer = 0f;
        if(phase == BLAST) phase = DONE;
    }
}