package example;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.math.geom.Position;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.entities.Units;
import mindustry.entities.abilities.Ability;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.*;
import mindustry.graphics.*;

public class NukeMissileAbility extends Ability {

    public float cooldown = 300f;
    public float range = 800f;
    public BulletType bulletType;

    /** 锁定警告时间（发射前多少ticks开始高亮） */
    public float lockOnTime = 60f;
    /** 高亮脉冲颜色 */
    public Color lockColor = Color.valueOf("ff4400");

    private float timer = 0f;
    private float lockTimer = 0f;
    private Position lockTarget = null;
    private boolean isLockingOn = false;

    @Override
    public void update(Unit unit) {
        if (bulletType == null) return;

        timer += Time.delta;

        // ── 锁定阶段：寻找目标并高亮 ──────────────────────────
        if (!isLockingOn && timer >= cooldown - lockOnTime) {
            // 寻找目标
            Unit target = Units.closestEnemy(unit.team, unit.x, unit.y, range, u -> u.isValid() && !u.isFlying());
            Building buildTarget = null;
            if (target == null) {
                buildTarget = Units.findEnemyTile(unit.team, unit.x, unit.y, range, b -> true);
            }

            if (target != null) {
                lockTarget = target;
            } else if (buildTarget != null) {
                lockTarget = buildTarget;
            }

            if (lockTarget != null) {
                isLockingOn = true;
                lockTimer = lockOnTime;
            }
        }

        // ── 锁定中：高亮目标 ──────────────────────────────────
        if (isLockingOn && lockTarget != null) {
            lockTimer -= Time.delta;

            // 在目标位置画高亮圆圈
            float progress = 1f - (lockTimer / lockOnTime);
            float pulse = Mathf.absin(Time.time, 8f, 1f) * 0.3f + 0.7f;

            Draw.z(Layer.effect + 10f);
            Draw.alpha(0.6f * progress * pulse);

            // 外圈闪烁
            Lines.stroke(3f, Color.white);
            Lines.circle(lockTarget.getX(), lockTarget.getY(),
                    20f + progress * 40f + Mathf.absin(Time.time, 6f, 8f));

            // 内圈红色
            Fill.circle(lockTarget.getX(), lockTarget.getY(),
                    12f + Mathf.absin(Time.time, 10f, 4f));

            // 十字瞄准线
            Lines.stroke(2f, lockColor);
            Lines.line(
                    lockTarget.getX() - 15f, lockTarget.getY(),
                    lockTarget.getX() + 15f, lockTarget.getY()
            );
            Lines.line(
                    lockTarget.getX(), lockTarget.getY() - 15f,
                    lockTarget.getX(), lockTarget.getY() + 15f
            );

            // 锁定线从单位到目标
            Draw.alpha(0.3f * progress * pulse);
            Lines.stroke(1.5f, lockColor);
            Lines.line(unit.x, unit.y, lockTarget.getX(), lockTarget.getY());
            Lines.stroke(1f, Color.white);
            Lines.dashCircle(unit.x, unit.y, Mathf.dst(unit.x, unit.y, lockTarget.getX(), lockTarget.getY()) / 2f);

            Draw.reset();

            // ── 发射时刻 ──────────────────────────────────────
            if (lockTimer <= 0f || timer >= cooldown) {
                // 发射核弹
                float angle = Mathf.angle(
                        lockTarget.getX() - unit.x,
                        lockTarget.getY() - unit.y
                );

                Bullet bullet = bulletType.create(unit, unit.team, unit.x, unit.y, angle);
                if (bullet != null) {
                    bullet.vel.set(
                            lockTarget.getX() - unit.x,
                            lockTarget.getY() - unit.y
                    ).nor().scl(bulletType.speed);
                }

                // 发射特效
                Fx.shootBig.at(unit.x, unit.y, angle, lockColor, unit);

                // 目标处预警特效
                Fx.placeBlock.at(lockTarget.getX(), lockTarget.getY(), 40f);

                // 重置
                timer = 0f;
                lockTimer = 0f;
                lockTarget = null;
                isLockingOn = false;
            }
        }
    }
}