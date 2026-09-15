package cryon.Type;

import arc.Core;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class StackRouter extends Block{
    public Effect unloadEffect = Fx.conveyorPoof;

    /** 每次整堆搬运需要的时间（越大越慢） */
    public float recharge = 1.5f;

    public StackRouter(String name){
        super(name);

        solid = false;
        underBullets = true;
        update = true;
        hasItems = true;
        itemCapacity = 10;
        group = BlockGroup.transportation;
        unloadable = false;
        noUpdateDisabled = true;
        squareSprite = false;
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.itemsMoved, Mathf.round(itemCapacity / recharge * 60f), StatUnit.itemsSecond);
    }

    public class StackRouterBuild extends Building{
        public Item lastItem;
        public float cooldown;
        public int rotation;

        @Override
        public void draw(){
            //只画方块本体，不再画物品搬运动画，实际视觉交给传送带承担
            Draw.rect(region, x, y);
        }

        @Override
        public void updateTile(){
            if(cooldown > 0f) cooldown = Mathf.clamp(cooldown - delta(), 0f, recharge);

            if(lastItem == null && items.any()){
                lastItem = items.first();
            }

            if(cooldown > 0f || lastItem == null) return;

            Building target = findTarget(lastItem);
            if(target != null){
                int available = items.get(lastItem);
                int accepted = target.acceptStack(lastItem, available, this);
                if(accepted > 0){
                    target.handleStack(lastItem, accepted, this);
                    items.remove(lastItem, accepted);

                    unloadEffect.at(this);

                    if(items.empty()){
                        lastItem = null;
                    }
                    //只要发出去了,就重新走一次冷却,即使没发完
                    cooldown = recharge;
                }
            }
        }

        /** 全向轮询找下一个能接收整堆的邻居；排除掉朝向自己的建筑，不往回发 */
        public Building findTarget(Item item){
            int counter = rotation;
            for(int i = 0; i < proximity.size; i++){
                Building other = proximity.get((i + counter) % proximity.size);
                rotation = (rotation + 1) % Math.max(proximity.size, 1);

                if(other.front() == this) continue;

                if(other.team == team && other.acceptStack(item, items.get(item), this) > 0){
                    return other;
                }
            }
            return null;
        }

        @Override
        public int acceptStack(Item item, int amount, Teamc source){
            if(items.any() && !items.has(item)) return 0;
            return Math.min(amount, itemCapacity - items.total());
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            //只屏蔽普通传送带，其他来源（载荷传送带、分拣器、其他建筑等）正常接受
            if(source.block instanceof mindustry.world.blocks.distribution.Conveyor) return false;
            return (items.empty() || items.has(item)) && items.total() < itemCapacity;
        }

        @Override
        public void handleItem(Building source, Item item){
            super.handleItem(source, item);
            lastItem = item;
        }

        @Override
        public void handleStack(Item item, int amount, Teamc source){
            if(amount <= 0) return;
            super.handleStack(item, amount, source);
            lastItem = item;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(cooldown);
            write.b(rotation);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            cooldown = read.f();
            rotation = read.b();
            lastItem = items.first();
        }
    }
}