package cryon.Type;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.world.Block;

public class AgitatorBlock extends Block {

    public float range = 200f;
    public float healthThreshold = 0.3f;
    public float convertCooldown = 120f;
    public float targetSearchInterval = 15f;
    public Color effectColor = Color.valueOf("f4ba6e");

    public AgitatorBlock(String name) {
        super(name);
        update = true;
        solid = true;
        hasPower = true;
        canOverdrive = true;
        configurable = false;
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid) {
        super.drawPlace(x, y, rotation, valid);
        Drawf.dashCircle(x * 8f, y * 8f, range, effectColor);
    }

    public class AgitatorBuild extends Building {

        public float cooldownTimer = 0f;
        public float chargeProgress = 0f;
        private float searchTimer;
        private Unit searchResult;
        private final arc.func.Cons<Unit> targetFinder = u -> {
            if(u.team != team && u.isValid() && u.within(x, y, range)
                    && u.health <= u.maxHealth * healthThreshold
                    && (searchResult == null || u.id < searchResult.id)){
                searchResult = u;
            }
        };

        @Override
        public void updateTile() {
            if (cooldownTimer > 0f) {
                cooldownTimer -= Time.delta;
            }

            if (!hasPower || power.status < 0.5f) return;

            chargeProgress = Mathf.lerpDelta(chargeProgress, 1f, 0.05f);

            if (cooldownTimer > 0f) return;

            searchTimer -= Time.delta;
            if(searchTimer > 0f) return;
            searchTimer = targetSearchInterval;
            Unit target = findTarget();
            if (target != null) {
                convertUnit(target);
                cooldownTimer = convertCooldown;
                chargeProgress = 0f;
            }
        }

        protected Unit findTarget() {
            searchResult = null;
            Groups.unit.intersect(x - range, y - range, range * 2f, range * 2f, targetFinder);
            return searchResult;
        }

        protected void convertUnit(Unit target) {
            spawnConvertEffect(target);
            target.team(team);
            target.controller(target.type.createController(target));
        }

        protected void spawnConvertEffect(Unit target) {
            mindustry.content.Fx.unitSpawn.at(target.x, target.y, 0f, effectColor, target);
            mindustry.content.Fx.heal.at(target.x, target.y);
            mindustry.content.Fx.lightningShoot.at(
                    x, y,
                    Mathf.angle(target.x - x, target.y - y),
                    effectColor
            );
        }

        @Override
        public void draw() {
            super.draw();

            if (power != null && power.status > 0.5f) {
                float pulse = Mathf.sin(Time.time, 40f, 1f) * 0.15f + 0.85f;

                Draw.z(71f);

                Lines.stroke(1.5f, effectColor);
                Draw.alpha(0.18f * pulse);
                Lines.dashCircle(x, y, range);

                Draw.alpha(chargeProgress * 0.35f * pulse);
                Fill.circle(x, y, 12f * chargeProgress);

                if (cooldownTimer > 0f) {
                    float progress = 1f - (cooldownTimer / convertCooldown);
                    Draw.alpha(0.7f);
                    Lines.stroke(2f, effectColor);
                    Lines.arc(x, y, 10f, progress, -90f);
                }

                Draw.reset();
            }
        }

        public float range() {
            return range;
        }

        @Override
        public void drawSelect() {
            Drawf.dashCircle(x, y, range, effectColor);
        }
    }
}