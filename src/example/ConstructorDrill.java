package example;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.util.Time;
import arc.scene.ui.layout.Table;
import arc.util.Scaling;
import mindustry.ui.Bar;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.world.blocks.production.Drill;

public class ConstructorDrill extends Drill {

    public float constructorConsumption = 80f;
    public Color auraColor    = Color.valueOf("ffd37f");
    public float auraRadius   = -1f;
    public float auraStroke   = 1.5f;
    public float auraRotSpeed = 1.2f;
    public float auraPulseMag = 0.08f;
    public float auraPulseScl = 5f;

    public ConstructorDrill(String name) {
        super(name);
        hasPower = false;
    }

    @Override
    public void init() {
        super.init();
        if (auraRadius < 0f) auraRadius = size * 4f + 3f;
    }

    @Override
    public void setStats() {
        super.setStats();
        stats.add(
                new mindustry.world.meta.Stat("constructoruse", mindustry.world.meta.StatCat.power),
                constructorConsumption,
                StatUnit.perSecond
        );
    }


    @Override
    public void setBars() {
        super.setBars();
        addBar("constructor-status", (ConstructorDrillBuild b) ->
                new Bar(
                        () -> "Constructor " + (int)(b._constructorStatus * 100) + "%",
                        () -> auraColor,
                        () -> b._constructorStatus
                )
        );
    }


    public class ConstructorDrillBuild extends DrillBuild implements ConstructorConsumer {

        float _constructorStatus = 0f;
        private float auraRot = 0f;

        // ── ConstructorConsumer ───────────────────────────────────

        @Override
        public float constructorUse() {
            if (dominantItem == null || !enabled) return 0f;
            return constructorConsumption * warmup;
        }

        @Override
        public void constructorStatus(float status) {
            _constructorStatus = status;
        }

        @Override
        public boolean constructorValid() {
            return enabled && dominantItem != null;
        }



        @Override
        public boolean shouldConsume() {
            return _constructorStatus > 0.001f && super.shouldConsume();
        }

        @Override
        public void updateTile() {
            auraRot += auraRotSpeed * delta();
            if (_constructorStatus <= 0.001f) {
                warmup = Mathf.approachDelta(warmup, 0f, warmupSpeed);
                lastDrillSpeed = 0f;
                return;
            }
            super.updateTile();
        }


        @Override
        public void draw() {
            super.draw();
            drawAura();
        }

        private void drawAura() {
            float alpha = warmup * _constructorStatus;
            if (alpha <= 0.01f) return;

            Draw.z(Layer.effect);

            float pulse  = 1f + Mathf.absin(Time.time, auraPulseScl, auraPulseMag);
            float radius = auraRadius * pulse;

            Lines.stroke(auraStroke, auraColor);
            Draw.alpha(alpha * 0.7f);
            int segs = 16;
            for (int i = 0; i < segs; i += 2) {
                float a0 = (i      / (float)segs) * 360f + auraRot;
                float a1 = ((i+1f) / (float)segs) * 360f + auraRot;
                Lines.line(
                        x + Mathf.cosDeg(a0) * radius,
                        y + Mathf.sinDeg(a0) * radius,
                        x + Mathf.cosDeg(a1) * radius,
                        y + Mathf.sinDeg(a1) * radius
                );
            }

            Lines.stroke(auraStroke * 0.5f);
            Draw.alpha(alpha * 0.35f);
            Lines.circle(x, y, radius * 0.75f);

            Draw.alpha(alpha * 0.6f);
            Drawf.tri(x, y + radius, auraStroke * 2f, radius * 0.18f, 90f  + auraRot * 0.5f);
            Drawf.tri(x, y - radius, auraStroke * 2f, radius * 0.18f, 270f + auraRot * 0.5f);
            Drawf.tri(x + radius, y, auraStroke * 2f, radius * 0.18f, 0f   + auraRot * 0.5f);
            Drawf.tri(x - radius, y, auraStroke * 2f, radius * 0.18f, 180f + auraRot * 0.5f);

            Draw.reset();
        }
    }
}