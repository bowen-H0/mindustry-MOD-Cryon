package cryon.Type;

import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Fx;
import mindustry.entities.Units;
import mindustry.gen.*;
import mindustry.world.Block;

/** Buildable single-use water mine; its fuse ignores friendlies and aircraft. */
public class SeaMineBlock extends Block{
    public SeaMineBulletType blast = new SeaMineBulletType(620f, 56f);

    public SeaMineBlock(String name){
        super(name);
        update = true;
        solid = false;
        floating = placeableLiquid = requiresWater = true;
        underBullets = true;
        health = 120;
        targetable = false;
        destroyEffect = Fx.none;
        buildType = SeaMineBuild::new;
    }

    public class SeaMineBuild extends Building{
        public float arming;
        @Override public void draw(){
            super.draw();
            arc.graphics.g2d.Draw.color(team.color);
            arc.graphics.g2d.Fill.circle(x, y, 1.3f);
            arc.graphics.g2d.Draw.reset();
        }

        @Override public void updateTile(){
            arming += delta();
            if(arming < 90f || tile == null || !tile.floor().isLiquid) return;
            boolean[] hit = {false};
            Units.nearbyEnemies(team, x, y, 30f, unit -> {
                if(!unit.dead && unit instanceof WaterMovec && !unit.isFlying() && unit.within(this, 30f)) hit[0] = true;
            });
            if(hit[0]){
                Bullet bullet = blast.create(this, team, x, y, 0f);
                blast.hit(bullet); bullet.remove();
                kill();
            }
        }
        @Override public byte version(){ return 1; }
        @Override public void write(Writes write){ super.write(write); write.f(arming); }
        @Override public void read(Reads read, byte revision){ super.read(read, revision); arming = revision >= 1 ? read.f() : 0f; }
    }
}
