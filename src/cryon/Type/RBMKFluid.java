package cryon.Type;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.layout.Table;
import arc.util.*;
import arc.util.io.*;
import cryon.Type.RBMKReactor.NeutronCore;
import cryon.Type.RBMKReactor.NeutronNode;
import cryon.Type.RBMKReactor.NeutronProps;
import mindustry.content.*;
import mindustry.entities.Damage;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.Block;
import mindustry.world.Edges;
import mindustry.world.Tile;
import mindustry.world.blocks.liquid.Conduit;
import mindustry.world.blocks.power.PowerGenerator;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.meta.BlockGroup;
import static mindustry.Vars.*;

/**
 * 热力回路：
 *   水 / 冷蒸汽 --(HeatPipe 吸热)--> 热蒸汽 --(SteamTurbine)--> 冷蒸汽 --(Condenser + 电)--> 水
 *
 * 使用前必须先调用 RBMKFluid.loadLiquids()（在你定义方块之前），
 * 因为 Condenser / SteamTurbine 的构造函数里要用到 coldSteam / hotSteam。
 * 需要在 bundle 里加 liquid.<modname>-cold-steam.name / hot-steam.name。
 */
public class RBMKFluid {

    public static Liquid coldSteam, hotSteam;
    /** 1 单位水 -> 多少单位蒸汽（膨胀；管道堵塞时这就是压力来源） */
    public static float steamPerWater = 2f;

    public static void loadLiquids() {
        coldSteam = new Liquid("cold-steam", Color.valueOf("b8c8d8")) {{
            gas = true;
            temperature = 0.45f;
            heatCapacity = 0.6f;
            viscosity = 0.3f;
            lightColor = Color.valueOf("b8c8d855");
        }};
        hotSteam = new Liquid("hot-steam", Color.valueOf("ffd9c0")) {{
            gas = true;
            temperature = 1.3f;
            heatCapacity = 0.8f;
            viscosity = 0.3f;
            lightColor = Color.valueOf("ffd9c066");
        }};
    }

    /** 是否是"普通流体管道"（不是导热管道） */
    static boolean isPlainConduit(Block b) {
        return b instanceof Conduit && !(b instanceof HeatPipe);
    }

    // ------------------------------------------------------------------
    //  导热管道
    // ------------------------------------------------------------------
    public static class HeatPipe extends Conduit {
        public NeutronProps np = new NeutronProps();

        /** 压力 = 管内液体总量 / liquidCapacity。超过 burstPressure 持续 burstDelay tick 就爆管 */
        public float burstPressure = 2f;
        public float burstDelay = 20f;
        public float burstRadius = 4f, burstDamage = 150f;

        /** 每 tick 最大转化量 */
        public float convertRate = 0.8f;
        /** 转化 1 单位所需热量 */
        public float waterHeatCost = 3f, coldSteamHeatCost = 1.5f;
        /** 管道温度低于此值不产生蒸汽 */
        public float minSteamHeat = 50f;
        /** 冷蒸汽转热蒸汽需要更高的管温，避免整条管线余热把输出的冷蒸汽又烧成热蒸汽 */
        public float coldSteamMinHeat = 150f;
        /** 矿渣：每 tick 消耗量 / 每单位产生的热量 */
        public float slagRate = 0.3f, slagHeat = 6f;
        /** 堵塞时从旁边输出液体的方块拉取的速度 */
        public float pullRate = 0.3f;

        public HeatPipe(String name) {
            super(name);
            liquidCapacity = 20f;
            np.absorbFast = 0f;
            np.absorbSlow = 0f;
            np.conduct = 0.08f;      // 管道之间少传热，热量留在堆附近
            np.dissipate = 0.01f;
            np.meltHeat = 1500f;
            np.explosionRadius = 2.5f;
            np.explosionDamage = 60f;
            buildType = HeatPipeBuild::new;
        }

        @Override
        public void setBars() {
            super.setBars();
            addBar("heat", (Building e) -> new Bar("热量", Pal.lightOrange,
                    () -> Mathf.clamp(((HeatPipeBuild) e).core.heat / np.meltHeat)));
            for (int i = 0; i < 3; i++) {
                final int idx = i;
                addBar("mix" + i, (Building e) -> {
                    HeatPipeBuild pb = (HeatPipeBuild) e;
                    Liquid l = idx == 0 ? Liquids.water : idx == 1 ? hotSteam : coldSteam;
                    return new Bar(
                            () -> l.localizedName + " " + Strings.fixed(pb.liquids.get(l) / liquidCapacity, 1),
                            () -> l.color,
                            () -> Mathf.clamp(pb.liquids.get(l) / liquidCapacity));
                });
            }
            addBar("pressure", (Building e) -> new Bar("压力", Pal.remove,
                    () -> Mathf.clamp(((HeatPipeBuild) e).pressure() / burstPressure)));
        }

        /** 不与普通流体管道连接（贴图不拼接） */
        @Override
        public boolean blends(Tile tile, int rotation, int otherx, int othery, int otherrot, Block otherblock) {
            if (isPlainConduit(otherblock)) return false;
            return super.blends(tile, rotation, otherx, othery, otherrot, otherblock);
        }

        public class HeatPipeBuild extends ConduitBuild implements NeutronNode {
            public NeutronCore core = new NeutronCore();
            public float overTime = 0f;
            boolean moved = false;

            @Override public NeutronCore neutron() { return core; }
            @Override public NeutronProps props() { return np; }

            /** 允许的最大存量（压力可以超过 1，直到爆管阈值） */
            float limit() { return liquidCapacity * burstPressure * 1.25f; }

            /** LiquidModule 没有 total()，自己累加所有液体 */
            public float total() { return liquids.sum((l, a) -> a); }

            public float pressure() { return total() / liquidCapacity; }

            /** 可混合的液体：水 / 热蒸汽 / 冷蒸汽 */
            boolean isMix(Liquid l) {
                return l == Liquids.water || l == hotSteam || l == coldSteam;
            }

            float mixTotal() {
                return liquids.get(Liquids.water) + liquids.get(hotSteam) + liquids.get(coldSteam);
            }

            /** 让 current 指向量最多的那种液体（影响贴图颜色） */
            void fixCurrent() {
                if (mixTotal() < 0.0001f) return;
                liquids.set(Liquids.water, liquids.get(Liquids.water));
                liquids.set(coldSteam, liquids.get(coldSteam));
                liquids.set(hotSteam, liquids.get(hotSteam));
            }

            Liquid[] flowOrder;

            @Override
            public boolean acceptLiquid(Building source, Liquid liquid) {
                if (isPlainConduit(source.block)) return false;
                noSleep();
                if (tile != null && (source.relativeTo(tile.x, tile.y) + 2) % 4 == rotation) return false;
                // 管内只有 水/蒸汽 时，水和蒸汽可以混合；其他液体仍然是单一液体规则
                boolean mixOk = isMix(liquid) && total() - mixTotal() < 0.2f;
                if (!mixOk && liquids.current() != liquid && liquids.currentAmount() >= 0.2f) return false;
                return total() < limit();
            }

            @Override
            public void onDestroyed() {
                super.onDestroyed();
                core.explode(this, np, false);
            }

            @Override
            public void updateTile() {
                smoothLiquid = Mathf.lerpDelta(smoothLiquid, Mathf.clamp(total() / liquidCapacity), 0.05f);

                thermal();
                flow();
                fixCurrent();
                pressureCheck();

                core.visuals(this, np);
                if (core.begin(this, np)) core.end(this, np);
            }

            /** 吸热转化 / 矿渣加热 */
            void thermal() {
                float d = delta();
                convert(Liquids.water, hotSteam, waterHeatCost, steamPerWater, minSteamHeat, d);
                convert(coldSteam, hotSteam, coldSteamHeatCost, 1f, coldSteamMinHeat, d);

                float slag = liquids.get(Liquids.slag);
                if (slag > 0.001f) {
                    float c = Math.min(slag, slagRate * d);
                    liquids.remove(Liquids.slag, c);
                    core.heat += c * slagHeat;
                }
            }

            void convert(Liquid from, Liquid to, float cost, float ratio, float minHeat, float d) {
                float a = liquids.get(from);
                if (a < 0.001f || core.heat < minHeat) return;
                float avail = core.heat - minHeat * 0.5f;   // 保留一部分热量，避免被抽干后停转
                if (avail <= 0f) return;
                float c = Math.min(Math.min(a, convertRate * d), avail / cost);
                if (c <= 0f) return;
                liquids.remove(from, c);
                core.heat -= c * cost;
                liquids.add(to, c * ratio);   // 最后 add，使 current 变为蒸汽
            }

            /** 向前输送；输出优先级：热蒸汽 > 冷蒸汽 > 水 > 其他。管道->管道允许压力把对方撑过容量 */
            void flow() {
                moved = false;
                if (total() < 0.0001f) return;

                Building next = front();
                if (next != null && next.team == team) {
                    if (flowOrder == null) flowOrder = new Liquid[]{hotSteam, coldSteam, Liquids.water};
                    for (Liquid l : flowOrder) {
                        if (holds(l)) continue;
                        moved |= push(next, l);
                    }
                    Liquid cur = liquids.current();
                    if (!isMix(cur)) moved |= push(next, cur);
                }

                // 堵住了还满着：旁边仍在"试图输入"的液体源会继续把液体挤进来 -> 压力上升
                if (!moved && total() >= liquidCapacity * 0.95f) pullSources(liquids.current(), next);
            }

            /**
             * 管道足够热时，水/冷蒸汽留在管内慢慢转化成热蒸汽，不往下游冲走；
             * 只有存量快满（>90% 容量）时才溢出给下游，防止死锁。
             */
            boolean holds(Liquid l) {
                if (l == Liquids.water) return core.heat >= minSteamHeat && liquids.get(l) < liquidCapacity * 0.9f;
                if (l == coldSteam) return core.heat >= coldSteamMinHeat && liquids.get(l) < liquidCapacity * 0.9f;
                return false;
            }

            /** 管内各液体按量加权混合出的颜色 */
            Color mixColor() {
                float w = 0f, r = 0f, g = 0f, b = 0f;
                for (int i = 0; i < 4; i++) {
                    Liquid l = i == 0 ? Liquids.water : i == 1 ? hotSteam : i == 2 ? coldSteam : liquids.current();
                    if (i == 3 && isMix(l)) break;
                    float a = liquids.get(l);
                    if (a <= 0.0001f) continue;
                    w += a;
                    r += l.color.r * a;
                    g += l.color.g * a;
                    b += l.color.b * a;
                }
                if (w <= 0f) return Tmp.c1.set(liquids.current().color);
                return Tmp.c1.set(r / w, g / w, b / w, 1f);
            }

            /** 把 liq 往 next 送一点，返回是否送出了液体 */
            boolean push(Building next, Liquid liq) {
                if (liquids.get(liq) < 0.0001f) return false;
                if (next instanceof HeatPipeBuild hp) {
                    if (!hp.acceptLiquid(this, liq)) return false;
                    float p = liquids.get(liq) / liquidCapacity;
                    float op = hp.liquids.get(liq) / liquidCapacity;
                    float f = Math.min(Mathf.clamp(p - op) * liquidCapacity, liquids.get(liq));
                    f = Math.min(f, hp.limit() - hp.total());
                    if (f <= 0.0001f) return false;
                    hp.handleLiquid(this, liq, f);
                    liquids.remove(liq, f);
                    return true;
                }
                if (isPlainConduit(next.block)) return false;
                return moveLiquid(next, liq) > 0.0001f;
            }

            void pullSources(Liquid liq, Building next) {
                for (Building o : proximity) {
                    if (o == next || o.liquids == null || isPlainConduit(o.block)) continue;
                    if (o instanceof HeatPipeBuild) continue; // 管道上游由它们自己推
                    if (!o.block.outputsLiquid || o.block.consumesLiquid(liq)) continue;
                    if (!o.canDumpLiquid(this, liq)) continue;
                    float have = o.liquids.get(liq);
                    if (have < o.block.liquidCapacity * 0.9f) continue;
                    float pull = Math.min(have, pullRate * delta());
                    if (total() + pull < limit() && acceptLiquid(o, liq)) {
                        o.liquids.remove(liq, pull);
                        handleLiquid(o, liq, pull);
                    }
                }
            }

            void pressureCheck() {
                float pr = pressure();
                if (pr > burstPressure) {
                    overTime += delta();
                    if (overTime > burstDelay) burst(pr);
                } else {
                    overTime = Math.max(0f, overTime - delta() * 2f);
                }
                if (!headless && pr > burstPressure * 0.7f && Mathf.chanceDelta(0.1)) {
                    Fx.smoke.at(x + Mathf.range(3f), y + Mathf.range(3f));
                }
            }

            void burst(float pr) {
                core.exploded = true; // 自己处理，不再触发普通爆炸
                float heatMul = 1f + core.heat / np.meltHeat;
                float scale = pr / burstPressure;
                Damage.damage(x, y, tilesize * burstRadius * scale, burstDamage * scale * heatMul);
                Fx.explosion.at(x, y);
                Sounds.explosion.at(x, y);
                kill();
            }

            @Override
            public void draw() {
                super.draw();

                // 水+蒸汽混合：在管内叠一层按量加权的混合色
                float la = Mathf.clamp(smoothLiquid) * 0.9f;
                if (la > 0.02f) {
                    Draw.z(Layer.blockUnder + 0.1f);
                    Draw.color(mixColor(), la);
                    Fill.rect(x, y, tilesize * 0.6f, tilesize * 0.6f);
                    Draw.reset();
                }

                float a = Mathf.clamp(core.heat / np.meltHeat) * 0.55f;
                if (a > 0.01f) {
                    Draw.z(Layer.blockOver);
                    Draw.color(Pal.lightOrange, a);
                    Fill.rect(x, y, tilesize, tilesize);
                    Draw.reset();
                }

                float pr = pressure() / burstPressure;
                if (pr > 0.7f) {
                    Draw.z(Layer.blockOver + 0.05f);
                    Draw.color(Pal.remove, Mathf.clamp((pr - 0.7f) * 2f) * (0.4f + 0.6f * Mathf.absin(Time.time, 5f, 1f)));
                    Fill.rect(x, y, tilesize, tilesize);
                    Draw.reset();
                }

                core.drawGlow(this, np);
            }

            @Override
            public void write(Writes write) {
                super.write(write);
                core.write(write);
                write.f(overTime);
            }

            @Override
            public void read(Reads read, byte revision) {
                super.read(read, revision);
                core.read(read);
                overTime = read.f();
            }
        }
    }

    // ------------------------------------------------------------------
    //  导热管道路由器：向左右分流，可设置分类液体
    //   有方向：只接受后方的输入，向前方和两侧输出；前方/侧面不接受输入
    //   - 未设置：接受任何液体
    //   - 设置了液体：只允许该液体进入（例如只放热蒸汽）
    //   输出：前方 + 两侧均分
    //   不与普通流体管道连接
    // ------------------------------------------------------------------
    public static class HeatPipeRouter extends Block {
        public HeatPipeRouter(String name) {
            super(name);
            update = true;
            rotate = true;
            hasLiquids = true;
            outputsLiquid = true;
            liquidCapacity = 20f;
            configurable = true;
            saveConfig = true;
            clearOnDoubleTap = true;
            unloadable = false;
            group = BlockGroup.liquids;
            buildType = HeatPipeRouterBuild::new;

            config(Liquid.class, (HeatPipeRouterBuild b, Liquid l) -> b.sortLiquid = l);
            configClear((HeatPipeRouterBuild b) -> b.sortLiquid = null);
        }

        @Override
        public void setBars() {
            super.setBars();
            addBar("liquid", (Building e) -> new Bar(
                    () -> ((HeatPipeRouterBuild) e).liquids.current().localizedName,
                    () -> ((HeatPipeRouterBuild) e).liquids.current().color,
                    () -> ((HeatPipeRouterBuild) e).liquids.currentAmount() / liquidCapacity));
        }

        public class HeatPipeRouterBuild extends Building {
            public @Nullable Liquid sortLiquid;
            int rr = 0;

            @Override
            public boolean acceptLiquid(Building source, Liquid liquid) {
                if (isPlainConduit(source.block)) return false;
                // 只接受后方：来源->本方块 的方向必须等于本方块朝向
                if (source.relativeTo(tile.x, tile.y) != rotation) return false;
                // 分类：只允许选中的液体进入
                if (sortLiquid != null && liquid != sortLiquid) return false;
                if (liquids.current() != liquid && liquids.currentAmount() >= 0.2f) return false;
                return liquids.get(liquid) < liquidCapacity - 0.001f;
            }

            @Override
            public void handleLiquid(Building source, Liquid liquid, float amount) {
                if (liquid != liquids.current()) liquids.clear(); // 丢弃 <0.2 的残留
                liquids.add(liquid, amount);
            }

            /** to 这个方向上是否允许输出 l */
            boolean allowed(Building to, Liquid l) {
                if (to == null || isPlainConduit(to.block)) return false;
                int d = relativeTo(Edges.getFacingEdge(to.tile, tile));
                return d == rotation || d == (rotation + 1) % 4 || d == (rotation + 3) % 4;
            }

            /** 旁边的导热管道堵塞时会来拉液体，同样要遵守分类规则 */
            @Override
            public boolean canDumpLiquid(Building to, Liquid liquid) {
                return allowed(to, liquid);
            }

            @Override
            public void updateTile() {
                Liquid liq = liquids.current();
                if (liq == null || liquids.get(liq) < 0.001f) return;

                // 前方/左/右三个出口轮流优先，保证均分
                rr = (rr + 1) % 3;
                for (int i = 0; i < 3; i++) {
                    int idx = (rr + i) % 3;
                    int d = idx == 0 ? rotation : idx == 1 ? (rotation + 1) % 4 : (rotation + 3) % 4;
                    Building o = nearby(d);
                    if (o != null && o.team == team && allowed(o, liq)) moveLiquid(o, liq);
                }
            }

            @Override
            public void buildConfiguration(Table table) {
                table.table(Styles.black6, t -> {
                    t.defaults().pad(2f);
                    int i = 0;
                    for (Liquid l : content.liquids()) {
                        if (l.isHidden()) continue;
                        t.button(new TextureRegionDrawable(l.uiIcon), Styles.clearTogglei, 32f, () -> configure(l))
                                .size(40f).update(b -> b.setChecked(sortLiquid == l));
                        if (++i % 5 == 0) t.row();
                    }
                    t.row();
                    t.button("清除分类", () -> configure(null)).colspan(5).growX().height(30f);
                });
            }

            @Override
            public Object config() {
                return sortLiquid;
            }

            @Override
            public void draw() {
                super.draw();
                Liquid liq = liquids.current();
                float a = liq == null ? 0f : liquids.currentAmount() / liquidCapacity;
                if (liq != null && a > 0.01f) {
                    Draw.z(Layer.blockOver);
                    Draw.color(liq.color, Mathf.clamp(a) * 0.8f);
                    Fill.rect(x, y, tilesize * 0.6f, tilesize * 0.6f);
                }
                if (sortLiquid != null) {
                    Draw.z(Layer.blockOver + 0.1f);
                    Draw.color(sortLiquid.color);
                    Fill.square(x, y, 2f);
                }
                Draw.reset();
            }

            @Override
            public void write(Writes write) {
                super.write(write);
                write.s(sortLiquid == null ? -1 : sortLiquid.id);
                write.b(0); // 保留字节，兼容旧存档
            }

            @Override
            public void read(Reads read, byte revision) {
                super.read(read, revision);
                int id = read.s();
                sortLiquid = id < 0 ? null : content.liquid(id);
                read.b();
            }
        }
    }

    // ------------------------------------------------------------------
    //  冷凝器：电力 + 冷蒸汽 -> 水
    // ------------------------------------------------------------------
    public static class Condenser extends GenericCrafter {
        /** 每 tick 消耗的冷蒸汽 */
        public float steamUse = 0.6f;

        public Condenser(String name) {
            super(name);
            hasLiquids = true;
            hasPower = true;
            outputsLiquid = true;
            liquidCapacity = 40f;
            craftTime = 60f;
            consumeLiquid(coldSteam, steamUse);
            consumePower(1.5f);
            outputLiquid = new LiquidStack(Liquids.water, steamUse / steamPerWater);
            buildType = CondenserBuild::new;
        }

        public class CondenserBuild extends GenericCrafterBuild {
            /** 只吃导热管道里的冷蒸汽，不接普通管道 */
            @Override
            public boolean acceptLiquid(Building source, Liquid liquid) {
                return !isPlainConduit(source.block) && super.acceptLiquid(source, liquid);
            }

            /** 水只输出到导热管道体系（导热管道 / 导热管道路由器） */
            @Override
            public boolean canDumpLiquid(Building to, Liquid liquid) {
                return to.block instanceof HeatPipe || to.block instanceof HeatPipeRouter;
            }
        }
    }

    // ------------------------------------------------------------------
    //  蒸汽轮机：热蒸汽 -> 冷蒸汽 + 电
    // ------------------------------------------------------------------
    public static class SteamTurbine extends PowerGenerator {
        /** 每 tick 消耗的热蒸汽（0.25 = 15/秒，缓慢消耗；想更快就调大） */
        public float steamUse = 0.25f;

        public SteamTurbine(String name) {
            super(name);
            hasLiquids = true;
            outputsLiquid = true;
            liquidCapacity = 60f;
            // 每 tick 发电量（原版单位，60 = 3600/s）。请保证高于斯特林的 powerProduction
            powerProduction = 20f;
            consumeLiquid(hotSteam, steamUse);
            buildType = SteamTurbineBuild::new;
        }

        @Override
        public void setBars() {
            super.setBars();
            addLiquidBar(coldSteam);   // 显示冷蒸汽输出缓存
        }

        /** 贴图：name（底座）、name-rotator（转子，随效率旋转）、name-top（盖在最上层） */
        public TextureRegion rotatorRegion, topRegion;
        public boolean hasRotator, hasTop;
        /** 满效率时转子转速（度/tick） */
        public float maxRotateSpeed = 12f;

        @Override
        public void load() {
            super.load();
            hasRotator = Core.atlas.has(name + "-rotator");
            hasTop = Core.atlas.has(name + "-top");
            rotatorRegion = Core.atlas.find(name + "-rotator");
            topRegion = Core.atlas.find(name + "-top");
        }

        @Override
        public TextureRegion[] icons() {
            Seq<TextureRegion> list = new Seq<>();
            list.add(region);
            if (hasRotator) list.add(rotatorRegion);
            if (hasTop) list.add(topRegion);
            return list.toArray(TextureRegion.class);
        }

        public class SteamTurbineBuild extends GeneratorBuild {
            @Override
            public boolean shouldConsume() {
                return liquids.get(coldSteam) < liquidCapacity - 0.5f;
            }

            /** 转子角度 / 转速（仅用于绘制） */
            public float rotAngle = 0f, rotSpeed = 0f;

            @Override
            public void draw() {
                super.draw();   // 底座
                if (hasRotator) Draw.rect(rotatorRegion, x, y, rotAngle);
                if (hasTop) Draw.rect(topRegion, x, y);
            }

            @Override
            public void updateTile() {
                productionEfficiency = efficiency;

                // 转子平滑加速/减速
                rotSpeed = Mathf.lerpDelta(rotSpeed, productionEfficiency * maxRotateSpeed, 0.04f);
                rotAngle = (rotAngle + rotSpeed * Time.delta) % 360f;

                if (efficiency > 0.001f) {
                    // 与 ConsumeLiquid 本 tick 消耗量一致：消耗多少热蒸汽就产生多少冷蒸汽
                    float used = steamUse * edelta();
                    liquids.add(coldSteam, Math.min(used, liquidCapacity - liquids.get(coldSteam)));
                }

                if (liquids.get(coldSteam) > 0.01f) dumpLiquid(coldSteam, 2f);
            }

            /** 冷蒸汽只送到导热管道 / 冷凝器，不进普通管道 */
            @Override
            public boolean canDumpLiquid(Building to, Liquid liquid) {
                return !isPlainConduit(to.block);
            }

            @Override
            public boolean acceptLiquid(Building source, Liquid liquid) {
                return !isPlainConduit(source.block) && super.acceptLiquid(source, liquid);
            }
        }
    }
}