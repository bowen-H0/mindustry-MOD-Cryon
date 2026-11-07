package cryon.Type;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.Items;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.type.*;
import mindustry.world.Block;
import mindustry.world.meta.*;
import static mindustry.Vars.*;

/** Powered containment of terrain replication; leaves the contaminated terrain in place. */
public class NanoSuppressor extends Block{
    public float range = 160f;
    public static final Color fieldColor = Color.valueOf("89dcec");

    public NanoSuppressor(String name){
        super(name);
        requirements(Category.effect, ItemStack.with(Items.silicon, 240, Items.titanium, 200,
            Items.phaseFabric, 60, Items.surgeAlloy, 80));
        size = 3; health = 1800;
        update = solid = hasPower = true;
        canOverdrive = false;
        consumePower(30f);
        buildType = NanoSuppressorBuild::new;
    }

    @Override public void setStats(){
        super.setStats();
        stats.add(Stat.range, range / tilesize, StatUnit.blocks);
    }

    public class NanoSuppressorBuild extends Building{
        @Override public void created(){ super.created(); cryon.Content.NanoEnvironment.registerSuppressor(this); }
        public boolean operating(){
            return enabled && !dead && isValid() && power != null && power.status >= 0.98f;
        }
        public boolean contains(float tx, float ty){ return Mathf.dst2(x, y, tx, ty) <= range * range; }
        @Override public void drawSelect(){
            super.drawSelect();
            Drawf.dashCircle(x, y, range, fieldColor);
        }
        @Override public void draw(){
            super.draw();
            if(!operating()) return;
            Draw.color(fieldColor);
            Draw.alpha(0.5f + Mathf.absin(Time.time, 12f, 0.3f));
            Lines.stroke(1f);
            Lines.circle(x, y, 6f + Mathf.absin(Time.time, 25f, 2f));
            Draw.reset();
            cryon.Fx.NanoFeedbackFx.ambient(cryon.Fx.NanoFeedbackFx.containment, x, y, range, 65f);
        }
    }
}
