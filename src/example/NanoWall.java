package example;

import arc.scene.ui.layout.Table;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Fx;
import mindustry.graphics.Pal;
import mindustry.ui.Bar;
import mindustry.world.blocks.defense.Wall;

/**
 * 大型纳米墙
 * - 3000 血量 / 5 护甲 / 每秒回血 50
 * - 3000 护盾 / 每秒回复 100
 * - 护盾以纯 UI 条的形式显示在选中面板里
 * - 每秒耗电 50
 *
 * 用法（在 loadContent 里）:
 *   nanoWallLarge = new NanoWall("nano-wall-large") {{
 *       category = Category.defense;
 *       buildVisibility = BuildVisibility.shown;
 *   }};
 *   nanoWallLarge.requirements(Category.defense, with(
 *       CryonContent.item("nano-material"), 48,
 *       CryonContent.item("giant-wave-alloy"), 24, // TODO: 换成"巨浪合金"真实的 item id
 *       CryonContent.item("nickel"), 32
 *   ));
 */
public class NanoWall extends Wall {

    /** 最大护盾值 */
    public float maxShield = 3000f;
    /** 护盾每秒恢复量 */
    public float shieldRegenPerSec = 100f;
    /** 血量每秒恢复量 */
    public float healthRegenPerSec = 50f;

    public NanoWall(String name) {
        super(name);

        size = 2; // "大型" 墙一般是 2x2
        health = 3000;
        armor = 5f;

        update = true; // Wall 默认不 tick，必须打开才会计算耗电/效率、跑 updateTile
        hasPower = true;
        // consumePower 接收的是"每 tick"的耗电量，1 秒 = 60 tick
        consumePower(50f / 60f);
    }

    public class NanoWallBuild extends WallBuild {

        /** 当前护盾值 */
        public float shield = maxShield;

        /** 每隔多少 tick 治疗一次（60 = 1 秒一次） */
        public float healReload = 60f;
        /** 治疗脉冲计时器 */
        public float healCounter = 0f;

        @Override
        public void updateTile() {
            super.updateTile();

            // 没电就什么都不回，护盾也不会恢复
            if (efficiency <= 0) return;

            if (health < maxHealth) {
                healCounter += Time.delta;

                if (healCounter >= healReload) {
                    healCounter %= healReload;

                    // 每治疗一次就闪一下，而不是按帧持续回血
                    heal(healthRegenPerSec * healReload / 60f);
                    Fx.healBlockFull.at(x, y, 0f, Pal.heal, block);
                }
            }

            if (shield < maxShield) {
                shield = Math.min(maxShield, shield + shieldRegenPerSec * Time.delta / 60f);
            }
        }

        @Override
        public void damage(float damage) {
            // 没电时护盾不生效，伤害全部直接打在血量上
            if (shield > 0 && efficiency > 0) {
                float absorbed = Math.min(shield, damage);
                shield -= absorbed;
                damage -= absorbed;

                // 有护盾时，被击中会有火花特效，这是护盾存在的唯一视觉区分
                Fx.absorb.at(x, y);
            }

            if (damage > 0) {
                super.damage(damage);
            }
        }

        /** 选中方块时，信息面板里额外显示一条护盾条（纯 UI，不画在场景里）。 */
        @Override
        public void displayBars(Table bars) {
            super.displayBars(bars);
            bars.add(new Bar("护盾", Pal.shield, () -> shield / maxShield)).row();
        }

        @Override
        public byte version() {
            return 1; // 加了 shield 字段，版本号从 0 提到 1
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.f(shield);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            // 只有 revision >= 1 的存档才写过 shield，旧存档没有这个字段，跳过即可
            if (revision >= 1) {
                shield = read.f();
            }
        }
    }
}