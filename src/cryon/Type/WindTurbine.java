package cryon.Type;

import arc.Core;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.ui.layout.Table;
import arc.util.*;
import arc.util.io.*;
import mindustry.entities.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.world.*;
import mindustry.world.blocks.power.PowerGenerator;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

/**
 * WindTurbine
 *
 * 风力发电机：
 * - 需要周围有一片足够大的空地才能放置（不能被其他建筑/地形阻挡气流）。
 * - 发电功率不是瞬间达到上限，而是随着运行时间缓慢爬升到 powerProduction，
 *   模拟风机从静止到转速拉满的过程；一旦被阻挡或失效，也会缓慢回落而不是瞬间归零。
 */
public class WindTurbine extends PowerGenerator {

    /** 周围空地检测的边长（以格为单位，以本体为中心） */
    public int clearAreaSize = 7;

    /** 从0爬升到满功率所需的大致时间（帧），数值越大爬升越慢 */
    public float spinUpTime = 60f * 8f;

    /** 功率回落速度相对爬升速度的倍率，越大回落越快 */
    public float spinDownMultiplier = 2.5f;

    public WindTurbine(String name){
        super(name);
        update = true;
        solid = true;
        outputsPower = true;
        hasPower = true;
        envEnabled = Env.any;
    }

    /** 检测以该方块为中心、clearAreaSize大小的区域内是否有阻挡物 */
    public boolean areaClear(Tile tile){
        if(tile == null) return false;

        int half = clearAreaSize / 2;
        int cx = tile.x, cy = tile.y;

        for(int dx = -half; dx <= half; dx++){
            for(int dy = -half; dy <= half; dy++){
                Tile other = world.tile(cx + dx, cy + dy);
                if(other == null) return false; //超出地图边界也视为不合法

                //跳过自己占用的格子
                if(other.build != null && other.build.tile == tile) continue;

                //区域内存在其他实体建筑，视为被阻挡
                if(other.build != null && other.build.block.solid && other.build.tile != tile){
                    return false;
                }

                //地形本身是高聚合物/悬崖等障碍也视为不合法，具体按你的地图内容调整
                if(other.solid()){
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean canPlaceOn(Tile tile, Team team, int rotation){
        return areaClear(tile) && super.canPlaceOn(tile, team, rotation);
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        super.drawPlace(x, y, rotation, valid);

        Tile tile = world.tile(x, y);
        boolean clear = tile != null && areaClear(tile);

        float wx = x * tilesize + offset;
        float wy = y * tilesize + offset;
        float half = clearAreaSize * tilesize / 2f;

        Drawf.dashRect(clear ? Pal.accent : Pal.remove,
                Tmp.r1.setSize(clearAreaSize * tilesize).setCenter(wx, wy));
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.range, clearAreaSize, StatUnit.blocks);
    }

    public class WindTurbineBuild extends GeneratorBuild {
        public float spinup = 0f;
        public float rotorAngle = 0f;

        @Override
        public void updateTile(){
            boolean canRun = areaClear(tile) && enabled;

            float target = canRun ? 1f : 0f;
            float speed = 1f / spinUpTime;

            if(spinup < target){
                spinup = Mathf.approachDelta(spinup, target, speed);
            }else{
                spinup = Mathf.approachDelta(spinup, target, speed * spinDownMultiplier);
            }

            productionEfficiency = spinup;

            rotorAngle += spinup * 6f * delta();
        }

        @Override
        public float getPowerProduction(){
            return canConsume() ? powerProduction * spinup : 0f;
        }

        @Override
        public void draw(){
            Draw.rect(region, x, y);

            if(spinup > 0.001f){
                Draw.rect(Core.atlas.find(block.name + "-rotor", region), x, y, rotorAngle);
            }
        }


        @Override
        public void write(Writes write){
            super.write(write);
            write.f(spinup);
            write.f(rotorAngle);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);

            if(revision >= 1){
                spinup = read.f();
                rotorAngle = read.f();
            }
        }
    }
}