package example;

import arc.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import arc.graphics.*;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Units;
import mindustry.entities.bullet.*;
import mindustry.entities.units.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.blocks.*;

public class DestroyerUnit{

    public static UnitType destroyerHead, destroyerBody;

    /** 不含头部的体节数量 */
    public static final int CHAIN_LENGTH = 11;
    /** 相邻两节之间的目标弧长间距 - 调小让链条更紧凑 */
    public static final float CHAIN_SPACING = 32f;

    // ============================================================
    // 运行时状态（纯内存态，不需要存档）
    // ============================================================
    static final IntMap<IntSeq> chainOf = new IntMap<>();
    static final IntSet claimed = new IntSet();
    static final IntMap<Seq<Vec2>> trailOf = new IntMap<>();

    public static void load(){

        Log.info("=== DestroyerUnit LOADING ===");

        destroyerBody = new UnitType("destroyer-body"){{
            constructor = LegsUnit::create;
            hitSize = 28f;
            health = 500f;
            armor = 4f;
            speed = 1.1f;
            rotateSpeed = 8f;
            allowedInPayloads = false;
            useUnitCap = false;
            hidden=true;
            playerControllable = false;
            controller = u -> new FollowUnitAI();
            weapons.add(laserWeapon(12f, 0f), laserWeapon(-12f, 0f));
        }};

        destroyerHead = new UnitType("destroyer"){
            {
                constructor = LegsUnit::create;
                speed = 0.65f;
                rotateSpeed = 2f;
                hitSize = 36f;
                health = 1200f;
                armor = 6f;
                targetAir = false;
                canBoost = false;
                playerControllable = true;

                weapons.add(laserWeapon(0f, 10f));
            }

            @Override
            public void update(Unit unit){
                super.update(unit);
                recordTrail(unit);
                ensureChain(unit);
            }

            @Override
            public void draw(Unit unit){
                super.draw(unit);
                drawChainLinks(unit);
            }
        };

        Events.on(EventType.UnitDestroyEvent.class, e -> {
            if(e.unit != null && e.unit.type == destroyerHead){
                IntSeq ids = chainOf.remove(e.unit.id);
                if(ids != null){
                    for(int i = 0; i < ids.size; i++){
                        Unit seg = Groups.unit.getByID(ids.get(i));
                        if(seg != null && seg.isAdded() && !seg.dead()){
                            seg.kill();
                        }
                        claimed.remove(ids.get(i));
                    }
                }
                trailOf.remove(e.unit.id);
            }
        });
    }

    // ============================================================
    // 头部历史轨迹（体节沿这条轨迹取点跟随）
    // ============================================================
    static void recordTrail(Unit head){
        Seq<Vec2> trail = trailOf.get(head.id);
        if(trail == null){
            trail = new Seq<>();
            trailOf.put(head.id, trail);
        }
        trail.insert(0, new Vec2(head.x, head.y));

        float maxLen = CHAIN_SPACING * (CHAIN_LENGTH + 2);
        float acc = 0f;
        int cut = trail.size;
        for(int i = 1; i < trail.size; i++){
            acc += trail.get(i - 1).dst(trail.get(i));
            if(acc > maxLen){
                cut = i + 1;
                break;
            }
        }
        if(cut < trail.size) trail.truncate(cut);
    }

    static Vec2 sampleTrail(int headId, float targetDist){
        Seq<Vec2> trail = trailOf.get(headId);
        if(trail == null || trail.size < 2) return null;

        float acc = 0f;
        for(int i = 1; i < trail.size; i++){
            float segLen = trail.get(i - 1).dst(trail.get(i));
            if(acc + segLen >= targetDist){
                float t = (targetDist - acc) / Math.max(segLen, 0.0001f);
                return new Vec2().set(trail.get(i - 1)).lerp(trail.get(i), t);
            }
            acc += segLen;
        }
        return null;
    }

    // ============================================================
    // 链条连接特效：沿着头->体节1->体节2->...画连线。
    // ============================================================
    static void drawChainLinks(Unit head){
        IntSeq ids = chainOf.get(head.id);
        if(ids == null || ids.size == 0) return;

        Draw.z(Layer.groundUnit - 0.1f);

        // 使用队伍颜色
        Color teamColor = head.team.color;
        Draw.color(teamColor.r, teamColor.g, teamColor.b, 0.7f);
        Lines.stroke(4f);

        Unit prev = head;
        for(int i = 0; i < ids.size; i++){
            Unit seg = Groups.unit.getByID(ids.get(i));
            if(seg == null || !seg.isAdded()) break;
            Lines.line(prev.x, prev.y, seg.x, seg.y);
            prev = seg;
        }

        Draw.reset();
    }

    // ============================================================
    // 确保头部拥有一条完整有效的链条；每帧自检，读档/断裂都能自动修好。
    // ============================================================
    static void ensureChain(Unit head){
        IntSeq ids = chainOf.get(head.id);

        boolean valid = ids != null && ids.size == CHAIN_LENGTH;
        if(valid){
            for(int i = 0; i < ids.size; i++){
                Unit seg = Groups.unit.getByID(ids.get(i));
                if(seg == null || !seg.isAdded() || seg.dead()){
                    valid = false;
                    break;
                }
            }
        }

        if(!valid){
            if(ids != null){
                for(int i = 0; i < ids.size; i++) claimed.remove(ids.get(i));
            }
            ids = rebuildChain(head);
            chainOf.put(head.id, ids);
        }

        for(int i = 0; i < ids.size; i++){
            Unit seg = Groups.unit.getByID(ids.get(i));
            if(seg != null && seg.controller() instanceof FollowUnitAI ai){
                ai.headId = head.id;
                ai.arcDistance = (i + 1) * CHAIN_SPACING;
            }
        }
    }

    static IntSeq rebuildChain(Unit head){
        IntSeq result = new IntSeq();

        Seq<Unit> nearby = new Seq<>();
        Groups.unit.each(u -> {
            if(u.type == destroyerBody
                    && u.team == head.team
                    && !claimed.contains(u.id)
                    && u.within(head, CHAIN_SPACING * (CHAIN_LENGTH + 2))){
                nearby.add(u);
            }
        });
        nearby.sort((a, b) -> Float.compare(a.dst(head), b.dst(head)));

        int adopt = Math.min(CHAIN_LENGTH, nearby.size);
        for(int i = 0; i < adopt; i++){
            Unit u = nearby.get(i);
            result.add(u.id);
            claimed.add(u.id);
        }

        for(int i = result.size; i < CHAIN_LENGTH; i++){
            Unit next = destroyerBody.create(head.team);

            float coilAngle = head.rotation + 180f + i * 30f;
            float coilRadius = CHAIN_SPACING * 0.3f + i * (CHAIN_SPACING * 0.12f);
            next.set(
                    head.x + Angles.trnsx(coilAngle, coilRadius),
                    head.y + Angles.trnsy(coilAngle, coilRadius)
            );
            next.rotation = coilAngle;
            next.add();

            result.add(next.id);
            claimed.add(next.id);
        }

        return result;
    }

    // ============================================================
    // 跟随AI：体节追踪头部轨迹并自动攻击
    // ============================================================
    public static class FollowUnitAI extends AIController {

        public int headId = -1;
        public float arcDistance = 0f;

        @Override
        public void updateUnit(){
            if(unit == null || !unit.isAdded() || headId < 0) return;

            // 更新移动
            Vec2 point = sampleTrail(headId, arcDistance);
            if(point == null){
                unit.moveAt(Vec2.ZERO);
                return;
            }

            float dst = unit.dst(point.x, point.y);
            if(dst > 1f){
                Tmp.v1.set(point.x - unit.x, point.y - unit.y).limit(unit.speed());
                unit.moveAt(Tmp.v1);
                unit.lookAt(point.x, point.y);
            }else{
                unit.moveAt(Vec2.ZERO);
            }

            // 调用父类的武器更新逻辑
            updateTargeting();
        }

        @Override
        public Teamc findTarget(float x, float y, float range, boolean air, boolean ground){
            // 优先搜索建筑 - 使用 closestBuilding
            Building building = Units.closestBuilding(unit.team, x, y, range,
                    b -> b.team != unit.team && b.isValid() && !b.block.underBullets
            );

            if(building != null) return building;

            // 搜索单位 - 调用父类方法
            return super.findTarget(x, y, range, air, ground);
        }

        @Override
        public boolean shouldShoot(){
            // 只要有目标就射击
            return target != null;
        }

        @Override
        public boolean retarget(){
            // 缩短目标检查间隔，反应更灵敏
            return timer.get(timerTarget, target == null ? 20f : 40f);
        }
    }

    private static Weapon laserWeapon(float x, float y){
        return new Weapon("cryon-destroyer-laser-mount"){{
            this.x = x;
            this.y = y;
            reload = 90f;
            shootCone = 25f;
            rotate = true;
            rotateSpeed = 6f;
            recoil = 0f;
            shootSound = Sounds.shootLancer;

            bullet = new LaserBulletType(){{
                damage = 120f;
                length = 250f;
                width = 3.2f;
                colors = new Color[]{Color.valueOf("ff0000aa"), Color.valueOf("ff4444"), Color.white};
                sideAngle = 15f;
                sideWidth = 0.5f;
                sideLength = 20f;
                lifetime = 20f;
                shootEffect = Fx.none;
            }};
        }};
    }
}