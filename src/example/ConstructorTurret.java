package example;

import arc.*;
import arc.math.*;
import mindustry.gen.*;
import mindustry.graphics.Pal;
import mindustry.ui.Bar;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatCat;
import mindustry.world.meta.StatUnit;
import mindustry.world.blocks.defense.turrets.PowerTurret;

public class ConstructorTurret extends PowerTurret {

    public float constructorUse = 0f;

    public ConstructorTurret(String name) {
        super(name);
    }

    public void consumeConstructor(float amount) {
        this.constructorUse = amount;
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(
                new Stat("constructoruse", StatCat.power),
                constructorUse * 60f,
                StatUnit.perSecond
        );
    }

    @Override
    public void setBars() {
        super.setBars();
        addBar("constructor", (ConstructorTurretBuild b) -> new Bar(
                () -> "Constructor " + (int) (b.constructorSatisfaction * 100) + "%",
                () -> Pal.accent,
                () -> Mathf.clamp(b.constructorSatisfaction)
        ));
    }

    public class ConstructorTurretBuild extends PowerTurretBuild implements ConstructorConsumer {
        public float constructorSatisfaction = 0f;

        @Override
        public float constructorUse() {
            return constructorUse;
        }

        @Override
        public void constructorStatus(float status) {
            this.constructorSatisfaction = status;
        }

        @Override
        public boolean constructorValid() {
            return enabled && !dead();
        }

        @Override
        public boolean hasAmmo() {
            return constructorSatisfaction > 0.0001f;
        }
    }
}