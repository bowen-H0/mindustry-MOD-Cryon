package cryon.Type;

import arc.Core;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.struct.*;
import arc.util.Time;
import arc.util.io.*;
import cryon.Features.MapAlliances;
import cryon.Fx.*;
import mindustry.content.*;
import mindustry.entities.Effect;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.graphics.Drawf;
import mindustry.type.*;
import mindustry.ui.Bar;
import mindustry.world.*;
import mindustry.world.meta.*;
import static mindustry.Vars.*;

/** One-shot timed EMP launching physical electric projectiles and native lightning. */
public class EmpBomb extends Block{
    public float fuseTime = 180f, range = 160f, arcDamage = 480f;
    public Effect blastEffect = EmpFx.burst; // Register effects on every peer during content loading.
    public EmpArcBulletType arcBullet = new EmpArcBulletType();
    public EmpBomb(String name){
        super(name);
        requirements(Category.effect, ItemStack.with(Items.silicon, 60, Items.titanium, 40, Items.surgeAlloy, 20));
        size = 2; health = 200; solid = update = true;
        canOverdrive = false; createRubble = false; destroyEffect = Fx.none;
        buildType = EmpBombBuild::new;
    }
    @Override public void setStats(){
        super.setStats();
        stats.add(Stat.range, range / tilesize, StatUnit.blocks);
        stats.add(Stat.damage, arcDamage);
    }
    @Override public void setBars(){
        super.setBars();
        addBar("emp-fuse", (EmpBombBuild b) -> new Bar(
            () -> Core.bundle.format("bar.cryon-emp-fuse", Math.max(0, Mathf.ceil((fuseTime - b.age) / 60f))),
            () -> EmpFx.blue, () -> Mathf.clamp(b.age / fuseTime)));
    }
    public class EmpBombBuild extends Building{
        public float age;
        private boolean detonated;
        @Override public void updateTile(){
            if(detonated) return;
            age = Math.min(fuseTime, age + Time.delta);
            if(!net.client() && age >= fuseTime) detonate();
        }
        public void detonate(){
            if(net.client() || detonated || !isValid()) return;
            detonated = true;
            Seq<Unit> enemies = Groups.unit.copy().select(u -> !u.dead && !MapAlliances.allied(team, u.team) && u.within(this, range));
            enemies.sort(u -> dst2(u));
            Seq<Building> buildings = new Seq<>();
            for(Team other : Team.all) if(!MapAlliances.allied(team, other)){
                for(Building b : state.teams.get(other).buildings){
                    if(b.isValid() && !b.dead && b.within(this, range)) buildings.add(b);
                }
            }
            buildings.sort(b -> dst2(b));
            for(int i = 0; i < 24; i++) discharge(i * 15f);
            // Reserve direct arcs for nearby opponents as well as the radial area coverage.
            int targeted = 0;
            for(Unit u : enemies){ if(targeted++ >= 4) break; discharge(angleTo(u)); }
            targeted = 0;
            for(Building b : buildings){ if(targeted++ >= 4) break; discharge(angleTo(b)); }
            Call.effect(blastEffect, x, y, range, EmpFx.blue);
            tile.removeNet();
        }
        private void discharge(float angle){
            arcBullet.createNet(team,x,y,angle,arcDamage,1f,range/(arcBullet.speed*arcBullet.lifetime));
        }
        @Override public void draw(){
            super.draw();
            float progress = Mathf.clamp(age / fuseTime);
            Draw.color(EmpFx.blue); Draw.alpha(0.45f + Mathf.absin(Time.time, 6f - progress * 4f, 0.5f));
            Fill.circle(x, y, 2f + progress * 3f); Draw.reset();
            NanoFeedbackFx.ambient(EmpFx.charge, x, y, 0f, 20f);
        }
        @Override public void drawSelect(){ super.drawSelect(); Drawf.dashCircle(x, y, range, EmpFx.blue); }
        @Override public byte version(){ return 1; }
        @Override public void write(Writes w){ super.write(w); w.f(age); }
        @Override public void read(Reads r, byte revision){ super.read(r, revision); age = revision >= 1 ? r.f() : 0f; }
    }
}
