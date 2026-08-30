package example;

import arc.*;
import arc.files.Fi;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.util.*;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.ctype.ContentType;
import mindustry.ctype.UnlockableContent;
import mindustry.game.EventType.*;
import mindustry.content.TechTree;
import mindustry.content.TechTree.TechNode;
import mindustry.mod.*;
import mindustry.type.*;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.meta.*;
import arc.graphics.*;
import arc.math.*;
import arc.struct.*;
import mindustry.*;
import mindustry.entities.*;
import mindustry.entities.abilities.*;
import mindustry.entities.bullet.*;
import mindustry.entities.effect.*;
import mindustry.entities.part.DrawPart.*;
import mindustry.entities.part.*;
import mindustry.entities.pattern.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.type.unit.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.blocks.campaign.*;
import mindustry.world.blocks.defense.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.blocks.distribution.*;
import mindustry.world.blocks.environment.*;
import mindustry.world.blocks.heat.*;
import mindustry.world.blocks.legacy.*;
import mindustry.world.blocks.liquid.*;
import mindustry.world.blocks.logic.*;
import mindustry.world.blocks.payloads.*;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.production.*;
import mindustry.world.blocks.sandbox.*;
import mindustry.world.blocks.storage.*;
import mindustry.world.blocks.units.*;
import mindustry.world.consumers.*;
import mindustry.world.draw.*;
import mindustry.world.meta.*;
import mindustry.game.Objectives.Objective;

import java.io.IOException;

import static mindustry.type.ItemStack.*;
import static mindustry.world.meta.StatValues.ammo;

//This project was developed using AI.
public class ExampleJavaMod extends Mod {


    public ExampleJavaMod() {}

    public static FluxBarrier    fluxBarrier;
    public static AgitatorBlock  agitatorTower;
    public static UniversalUnitAssembler t3universalAssembler;
    public static UniversalUnitAssembler t4universalAssembler;
    public static ConstructorSource constructorSource;
    public static ConstructorSink   constructorSink;
    public static ConstructorNode   constructorNode;
    public static GenericConstructorSource waveEmitter;
    public static GenericCrafter cryoElectrolyzer;


    public static ConstructorDrill constructorDrill;

    public static PowerTurret abyss;
    public static ConstructorTurret kismet;
    public static PowerTurret beacon;
    public static ConstructorTurret aurora;
    public static ConstructorTurret quantum;


    // ══════════════════════════════════════════════════════════════
    //  loadContent
    // ══════════════════════════════════════════════════════════════
    @Override
    public void loadContent() {
        new Drill("melting-drill") {{ }};
        new Drill("shattering-drill") {{ }};
        fluxBarrier = new FluxBarrier("flux-barrier") {{
            size             = 3;
            category         = Category.effect;
            buildVisibility  = BuildVisibility.shown;
        }};

        agitatorTower = new AgitatorBlock("agitator-tower") {{
            size             = 2;
            category         = Category.effect;
            buildVisibility  = BuildVisibility.shown;
            range            = 100f;
            healthThreshold  = 0.30f;
            convertCooldown  = 120f;
            effectColor      = Color.valueOf("f4ba6e");
            hasPower         = true;
            consumePower(3f);
            health           = 600;
            armor            = 4f;
        }};

        t3universalAssembler = new UniversalUnitAssembler("t3universal-assembler") {{
            size             = 5;
            areaSize         = 13;
            category         = Category.units;
            buildVisibility  = BuildVisibility.shown;

            hasPower = true;
            consumePower(2.5f);

            researchCostMultiplier = 0.4f;
        }};
        t4universalAssembler=new UniversalUnitAssembler("t4universal-assembler") {{
            size             = 5;
            areaSize         = 13;
            category         = Category.units;
            buildVisibility  = BuildVisibility.shown;
            constructorUse  = 50;
            hasPower = true;
            consumePower(5.5f);
            researchCostMultiplier = 0.4f;
        }};
        constructorSource = new ConstructorSource("constructor-source") {{
            size            = 1;
            production      = 1000000f / 60f;
            category        = Category.power;
            buildVisibility = BuildVisibility.sandboxOnly;
            health          = 200;
        }};

        constructorSink = new ConstructorSink("constructor-sink") {{
            size            = 1;
            consumption     = 1000000f / 60f;
            category        = Category.power;
            buildVisibility = BuildVisibility.sandboxOnly;
            health          = 200;
        }};

        constructorNode = new ConstructorNode("constructor-node") {{
            size            = 1;
            range           = 16;
            category        = Category.power;
            buildVisibility = BuildVisibility.shown;
            health          = 80;
        }};

        waveEmitter = new GenericConstructorSource("construct-wave-emitter") {{
            size = 2;
            health = 300;
            production = 600f;
            craftTime = 90f;
            consumeItem(Items.phaseFabric, 1);

            consumePower(3f);
        }};
        cryoElectrolyzer = new GenericCrafter("cryo-electrolyzer"){{


            size = 3;
            health = 240;
            craftTime = 60f;
            rotate = true;
            invertFlip = true;
            group = BlockGroup.liquids;

            itemCapacity = 0;
            liquidCapacity = 30f;

            consumeLiquid(Liquids.water, 2f);
            consumePower(1.5f);

            outputLiquids = LiquidStack.with(
                    Liquids.hydrogen, 2f,
                    Liquids.ozone, 1.2f
            );
            liquidOutputDirections = new int[]{1, 3};

            ambientSound = Sounds.loopElectricHum;
            ambientSoundVolume = 0.08f;

            regionRotated1 = 3;

            drawer = new DrawMulti(
                    new DrawRegion("-bottom"),
                    new DrawLiquidTile(Liquids.water, 2f),
                    new DrawBubbles(Color.valueOf("7693e3")){{
                        sides = 10;
                        recurrence = 3f;
                        spread = 6;
                        radius = 1.5f;
                        amount = 20;
                    }},
                    new DrawRegion(),
                    new DrawLiquidOutputs(),
                    new DrawGlowRegion(){{
                        alpha = 0.7f;
                        color = Color.valueOf("c4bdf3");
                        glowIntensity = 0.3f;
                        glowScale = 6f;
                    }}
            );

        }
            @Override
            public void init() {
                super.init();
                requirements(Category.crafting, with(CryonContent.item("aluminum"), 60, Items.silicon, 40, Items.graphite, 40));
            }
        };
        constructorDrill = new ConstructorDrill("constructor-drill") {{
            requirements(Category.production, new ItemStack[]{
                    new ItemStack(Items.phaseFabric, 20),
                    new ItemStack(Items.graphite,    20),
                    new ItemStack(Items.silicon,     30),
            });
            tier        = 3;
            drillTime   = 150;
            size        = 3;
            constructorConsumption = 4f;
        }};
        new AttributeCrafter("titanium-drill") {{ }};
        new AttributeCrafter("nickel-drill") {{ }};
        new Pump("aluminum-pump") {{ }};

        new SolidPump("cryon-water-extractor") {{ }};
        new AttributeCrafter("slag-extractor") {{ }};






        //Abyss
        abyss = new PowerTurret("abyss"){{


            size = 5;

            var arrayProgress = PartProgress.warmup;
            Color arrayColor = Color.valueOf("6ea8ff"), heatCol = Color.valueOf("aee3ff");
            float arrayY = -18f, arrayRotSpeed = 1.2f;

            shootSound = Sounds.shootArc;
            loopSound = Sounds.loopSmelter;
            loopSoundVolume = 1.4f;
            shoot = new ShootPattern(){{
                shots = 1;
                firstShotDelay = 350f;
            }};
            shootWarmupSpeed = 0.015f;
            shootType = new BasicBulletType(4f, 120f){{
                sprite = "cryon-tesla-orb";
                width = 40f;
                height = 40f;
                lifetime = 65f;
                hitEffect = despawnEffect = Fx.scatheExplosion;
                hitSound = Sounds.explosion;

                hitColor = backColor = trailColor = Color.valueOf("00d8d8");
                lightning = 6;
                lightningLength = 28;
                lightningLengthRand = 20;
                lightningDamage = 60;
                lightningColor=Color.valueOf("00d8d8");;
                despawnShake = 6f;
            }};

            drawer = new DrawTurret("reinforced-"){{
                parts.addAll(
                        new ShapePart(){{
                            progress = arrayProgress;
                            color = arrayColor;
                            sides = 8; hollow = true;
                            stroke = 0f; strokeTo = 2.8f;
                            radius = 32f;
                            layer = Layer.effect;
                            y = arrayY;
                        }},

                        new ShapePart(){{
                            progress = arrayProgress;
                            rotateSpeed = arrayRotSpeed;
                            color = arrayColor;
                            sides = 8; hollow = true;
                            stroke = 0f; strokeTo = 2.0f;
                            radius = 28f;
                            layer = Layer.effect;
                            y = arrayY;
                        }},

                        new ShapePart(){{
                            progress = arrayProgress;
                            rotateSpeed = -arrayRotSpeed * 1.5f;
                            color = arrayColor;
                            sides = 6; hollow = true;
                            stroke = 0f; strokeTo = 2.4f;
                            radius = 20f;
                            layer = Layer.effect;
                            y = arrayY;
                        }},

                        new ShapePart(){{
                            progress = arrayProgress;
                            color = arrayColor;
                            circle = true; hollow = true;
                            stroke = 0f; strokeTo = 2.4f;
                            radius = 14f;
                            layer = Layer.effect;
                            y = arrayY;
                        }},

                        new HaloPart(){{
                            progress = arrayProgress;
                            color = arrayColor;
                            tri = true; shapes = 8;
                            triLength = 0f; triLengthTo = 14f;
                            radius = 10f;
                            haloRadius = 32f;
                            haloRotateSpeed = arrayRotSpeed * 0.8f;
                            layer = Layer.effect;
                            y = arrayY;
                        }},

                        new HaloPart(){{
                            progress = arrayProgress;
                            color = arrayColor;
                            tri = true; shapes = 6;
                            triLength = 0f; triLengthTo = 8f;
                            radius = 7f;
                            haloRadius = 18f;
                            haloRotateSpeed = -arrayRotSpeed * 1.2f;
                            shapeRotation = 180f;
                            layer = Layer.effect;
                            y = arrayY;
                        }},

                        new HaloPart(){{
                            progress = arrayProgress;
                            color = arrayColor;
                            sides = 4; hollow = true;
                            stroke = 0f; strokeTo = 2.0f;
                            radius = 4f;
                            haloRadius = 10f;
                            haloRotateSpeed = arrayRotSpeed * 2f;
                            layer = Layer.effect;
                            y = arrayY;
                        }},

                        new RegionPart("-mouth"){{
                            heatColor = heatCol;
                            heatProgress = PartProgress.warmup;
                            moveY = -10f;
                        }},
                        new RegionPart("-end"){{ moveY = 0f; }},
                        new RegionPart("-front"){{
                            heatColor = heatCol;
                            heatProgress = PartProgress.warmup;
                            mirror = true; moveRot = 30f; moveY = -5f; moveX = 12f;
                        }},
                        new RegionPart("-back"){{
                            heatColor = heatCol;
                            heatProgress = PartProgress.warmup;
                            mirror = true; moveRot = 12f; moveX = 3f; moveY = 6f;
                        }},
                        new RegionPart("-mid"){{
                            heatColor = heatCol;
                            heatProgress = PartProgress.recoil;
                            moveY = -11f;
                        }}
                );

                Color heatCol2 = heatCol.cpy().add(0.1f, 0.1f, 0.1f).mul(1.2f);
                for(int i = 1; i < 4; i++){
                    int fi = i;
                    parts.add(new RegionPart("-spine"){{
                        outline = false;
                        progress = PartProgress.warmup.delay(fi / 5f);
                        heatProgress = PartProgress.warmup.add(p -> (Mathf.absin(3f, 0.2f) - 0.2f) * p.warmup);
                        mirror = true; under = true;
                        layerOffset = -0.3f;
                        turretHeatLayer = Layer.turret - 0.2f;
                        moveY = 10f;
                        moveX = 1f + fi * 4.5f;
                        moveRot = fi * 55f - 120f;
                        color = Color.valueOf("6ea8ff");
                        heatColor = heatCol2;
                        moves.add(new PartMove(PartProgress.recoil.delay(fi / 5f), 1f, 0f, 3f));
                    }});
                }
            }};

            heatRequirement = 8f;
            maxHeatEfficiency = 1f;
            consumePower(10f);
            unitSort = UnitSorts.strongest;
            recoil = 0.5f;
            shootY = 3f;
            reload = 300f;
            range = 260;
            shootCone = 15f;
            ammoUseEffect = Fx.casing1;
            health = 12500;
            inaccuracy = 2f;
            rotateSpeed = 10f;
            researchCostMultiplier = 0.05f;
            depositCooldown = 2.0f;

            limitRange(5f);
        }};

        kismet = new ConstructorTurret("kismet") {{
            size = 3;
            shootSound = Sounds.shootForeshadow;
            loopSound = Sounds.none;
            shoot = new ShootPattern() {{
                shots = 1;
                firstShotDelay = 0f;
            }};
            shootWarmupSpeed = 0.03f;
            shootType = new KismetBulletType(4.5f, 4f) {{
                width = 10f;
                height = 14f;
                lifetime = 60f;
                linkRange = 110f;
                shareFraction = 1.0f;
                linkColor = Color.valueOf("ff2b2b");
            }};
            drawer = new DrawTurret("reinforced-"){{
                var arrayProgress = PartProgress.recoil;
                Color arrayColor = Color.valueOf("ffb3b3");
                parts.addAll(
                        new HaloPart(){{
                            progress = arrayProgress;
                            color = arrayColor;
                            sides = 4; hollow = true;
                            stroke = 0f; strokeTo = 2.2f;
                            radius = 6f;
                            shapes = 1;
                            haloRadius = 0f;
                            haloRotateSpeed = 2f; // 方块本身自转
                            layer = Layer.effect;
                        }},
                        new HaloPart(){{
                            progress = arrayProgress;
                            color = arrayColor;
                            tri = false;
                            shapes = 10;
                            radius = 1.6f;
                            haloRadius = 14f;
                            haloRotateSpeed = -1.2f;
                            layer = Layer.effect;
                        }}
                );
            }};
            reload = 90f;
            range = 220f;
            inaccuracy = 1f;
            rotateSpeed = 6f;
            recoil = 2f;
            shootCone = 20f;
            health = 900;
            armor = 6f;
            hasPower = true;
            consumePower(4f);
            consumeConstructor(4f);
            unitSort = (unit, x, y) -> {
                if (KismetBulletType.unitGroup.containsKey(unit)) {
                    return 10000f;
                }
                if (KismetBulletType.markedUnits.contains(unit)) {
                    return 5000f;
                }
                return unit.dst(x, y);
            };
            targetGround = true;
            targetAir = true;
            category = Category.turret;
            buildVisibility = BuildVisibility.shown;
            researchCostMultiplier = 0.3f;
        }
            @Override
            public void init() {
                super.init();
                requirements(Category.turret, with(
                        Items.graphite, 250,
                        Items.phaseFabric, 200,
                        CryonContent.item("neutronite"), 200

                ));
            }
        };
        beacon = new PowerTurret("beacon") {{
            size = 3;

            shootSound = Sounds.none;
            loopSound  = Sounds.none;
            shootEffect = Fx.none;
            smokeEffect = Fx.none;

            shoot = new ShootPattern() {{
                shots = 1;
                firstShotDelay = 0f;
            }};

            shootType = new BeaconBulletType(5f, 0f) {{
                lifetime     = 20f;   // 落点确定前的极短过渡,弹体不可见
                strikeDelay  = 90f;   // 标记后 1.5 秒光束落下
                strikeRadius = 80f;
                strikeDamage = 220f;
                markColor = Color.valueOf("57c2ff");
                beamColor = Color.valueOf("bfe9ff");
            }};

            targetGround = true;  // 直接选定地面坐标点,而不是跟踪单位
            targetAir    = false;
            shootCone    = 360f;

            range      = 260f;
            reload     = 180f;
            inaccuracy = 0f;
            rotateSpeed = 6f;

            hasPower = true;
            consumePower(6f);

            health = 700;
            armor  = 5f;
            recoil=0;
            category        = Category.turret;
            buildVisibility = BuildVisibility.shown;

            researchCostMultiplier = 0.3f;

        }
            @Override
            public void init() {
                super.init();
                requirements(Category.turret, with(
                        Items.silicon, 150,
                        Items.phaseFabric, 150,
                        CryonContent.item("farstar-alloy"), 200

                ));
            }};
        Effect auroraTrail = new Effect(28f, e -> {
            // 多层光带叠加，制造极光的层次感
            for(int i = 0; i < 3; i++){
                float offset = i * 6f - 6f; // 三层轨迹左右微微错开
                float widthMul = 1f - i * 0.25f;

                // 用噪声让颜色随时间、随位置流动，制造"飘动"的极光感
                float colorShift = Mathf.absin(e.time + i * 30f, 12f, 1f);
                Draw.color(
                        Color.valueOf("ffffff"),
                        Color.valueOf("d94dff"),
                        Mathf.clamp(e.fin() + colorShift * 0.3f)
                );
                Draw.alpha(e.fout() * (0.9f - i * 0.25f));

                Lines.stroke(3.2f * widthMul * e.fout());
                Tmp.v1.trns(e.rotation + 90f, offset);
                Lines.lineAngle(
                        e.x + Tmp.v1.x, e.y + Tmp.v1.y,
                        e.rotation, 9f + i * 2f
                );
            }

            // 中心一条更亮更细的白紫核心线，增加"华丽感"
            Draw.color(Color.white);
            Draw.alpha(e.fout());
            Lines.stroke(1.4f * e.fout());
            Lines.lineAngle(e.x, e.y, e.rotation, 10f);

            // 随机小光点点缀，像极光边缘的星芒闪烁
            Draw.color(Color.valueOf("d94dff"));
            Draw.alpha(e.fout() * 0.8f);
            Angles.randLenVectors(e.id, 2, 6f * e.fin(), e.rotation, 40f, (x, y) -> {
                Fill.circle(e.x + x, e.y + y, 1.1f * e.fout());
            });

            Drawf.light(e.x, e.y, 30f * e.fout(), Color.valueOf("d94dff"), 0.6f * e.fout());

            Draw.reset();
        });

        Effect auroraShoot = new Effect(18f, e -> {
            Draw.color(Color.white, Color.valueOf("d94dff"), e.fin());
            Draw.alpha(e.fout());

            // 中心闪光
            Fill.circle(e.x, e.y, 5f * e.fout());

            // 环形波纹向外扩散
            Lines.stroke(2f * e.fout());
            Lines.circle(e.x, e.y, 4f + 10f * e.fin());

            // 几道放射状光刺，模拟蓄能炮口的华丽感
            Angles.randLenVectors(e.id, 5, 14f * e.fin(), e.rotation, 50f, (x, y) -> {
                Lines.lineAngle(e.x, e.y, Mathf.angle(x, y), 3f * e.fout());
            });

            Drawf.light(e.x, e.y, 70f * e.fout(), Color.valueOf("d94dff"), 0.8f * e.fout());

            Draw.reset();
        });

        Effect auroraHit = new Effect(30f, e -> {
            // 白紫爆闪核心
            Draw.color(Color.white, Color.valueOf("d94dff"), e.fin());
            Draw.alpha(e.fout());
            Fill.circle(e.x, e.y, 6f * e.fout());

            // 多层扩散冲击环
            for(int i = 0; i < 2; i++){
                float delay = i * 0.15f;
                float fin = Mathf.clamp(e.fin() - delay);
                Draw.alpha(e.fout() * (1f - i * 0.4f));
                Lines.stroke(2.2f * (1f - i * 0.3f));
                Lines.circle(e.x, e.y, 3f + 22f * fin);
            }

            // 破碎光屑向外飞溅
            Draw.color(Color.valueOf("d94dff"));
            Angles.randLenVectors(e.id, 8, 4f + 20f * e.fin(), (x, y) -> {
                Draw.alpha(e.fout());
                Fill.circle(e.x + x, e.y + y, 1.3f * e.fout());
            });

            Drawf.light(e.x, e.y, 60f * e.fout(), Color.valueOf("d94dff"), 0.9f * e.fout());

            Draw.reset();
        });

        Effect auroraPierce = new Effect(16f, e -> {
            Draw.color(Color.white, Color.valueOf("d94dff"), e.fin());
            Draw.alpha(e.fout());

            // 较小的穿透闪光，不如命中特效那么夸张
            Fill.circle(e.x, e.y, 3f * e.fout());
            Lines.stroke(1.4f * e.fout());
            Lines.circle(e.x, e.y, 2f + 8f * e.fin());

            // 少量碎光点，表示能量泄漏
            Angles.randLenVectors(e.id, 3, 3f + 6f * e.fin(), e.rotation, 30f, (x, y) -> {
                Fill.circle(e.x + x, e.y + y, 0.9f * e.fout());
            });

            Drawf.light(e.x, e.y, 25f * e.fout(), Color.valueOf("d94dff"), 0.5f * e.fout());

            Draw.reset();
        });

        Effect auroraDespawn = new Effect(22f, e -> {
            Draw.color(Color.valueOf("d94dff"), Color.white, e.fout());
            Draw.alpha(e.fout());

            // 光束末端收缩成一点后消散
            Fill.circle(e.x, e.y, 4f * e.fout());
            Lines.stroke(1.6f * e.fout());
            Lines.circle(e.x, e.y, 6f * e.fin());

            Drawf.light(e.x, e.y, 20f * e.fout(), Color.valueOf("d94dff"), 0.4f * e.fout());

            Draw.reset();
        });
        aurora = new ConstructorTurret("aurora"){
            RailBulletType auroraBullet;

            {
                float brange = range = 380f;


                auroraBullet = new RailBulletType(){{
                    shootEffect   = auroraShoot;
                    hitEffect     = auroraHit;
                    pierceEffect  = auroraPierce;
                    despawnEffect = auroraDespawn;
                    pointEffect   = auroraTrail;
                    hitColor      = Color.valueOf("ffffff");
                    pointEffectSpace = 20f;
                    damage = 1200;
                    pierceDamageFactor = 1f;
                    length = brange;
                    hitShake = 5f;
                    ammoMultiplier = 1f;
                    statusDuration = 360f;
                }};

                ammo(ObjectMap.of(Items.surgeAlloy, auroraBullet));
                shootType = auroraBullet;

                maxAmmo = 40;
                ammoPerShot = 5;
                rotateSpeed = 2f;
                reload = 160f;
                ammoUseEffect = Fx.casing3Double;
                recoil = 5f;
                cooldownTime = reload;
                shake = 4f;
                size = 4;
                shootCone = 2f;
                shootSound = Sounds.shootForeshadow;
                unitSort = UnitSorts.strongest;
                envEnabled |= Env.space;

                coolantMultiplier = 0.4f;
                liquidCapacity = 60f;
                scaledHealth = 150;

                coolant = consumeCoolant(1f);
                depositCooldown = 2.0f;

                hasPower = true;
                consumePower(28f);
                consumeConstructor(20f);
            }

            @Override
            public void init() {
                super.init();
                auroraBullet.status = Vars.content.statusEffect("cryon-imbalance");
                requirements(Category.turret, with(
                        Items.silicon, 700,
                        Items.surgeAlloy, 150,
                        Items.phaseFabric, 600,
                        CryonContent.item("aluminum"), 550

                ));
            }
        };
        quantum = new ConstructorTurret("quantum"){
            BasicBulletType quantumBullet;
            {
                float brange = range = 220f;

                quantumBullet = new BasicBulletType(11f, 58){{
                    width = 11f;
                    height = 16f;
                    lifetime = brange / speed;
                    hitEffect = Fx.hitBulletColor;
                    despawnEffect = Fx.hitBulletColor;
                    hitColor = Color.valueOf("ffffff");
                    backColor = Color.valueOf("fc7272");
                    frontColor = Color.valueOf("ffb7b7");

                    trailColor = Color.valueOf("fc7272");
                    trailWidth = 2.4f;
                    trailLength = 12;

                    ammoMultiplier = 4f;
                    pierceCap = 1;
                    reloadMultiplier = 1f;
                    knockback = 1.2f;
                    inaccuracy = 2f;

                    pierceArmor = true;
                    armorMultiplier = 0.3f;

                    splashDamage = 25f;
                    splashDamageRadius = 15f;

                    hitShake = 2f;
                    statusDuration = 30f;
                }};
                ammo(ObjectMap.of(Items.thorium, quantumBullet));
                shootType = quantumBullet;
                maxAmmo = 100;
                ammoPerShot = 1;
                rotateSpeed = 5f;
                reload = 5f;
                shootCone = 10f;
                recoil = 0.5f;
                cooldownTime = reload;
                shake = 3f;
                size = 4;
                shootSound = Sounds.shootAlpha;
                unitSort = UnitSorts.strongest;
                coolantMultiplier = 0.5f;
                liquidCapacity = 60f;
                scaledHealth = 180;
                coolant = consumeCoolant(1.2f);
                depositCooldown = 1.5f;
                hasPower = true;
                consumePower(24f);
                consumeConstructor(28f);
            }
            @Override
            public void init() {
                super.init();
                quantumBullet.status = Vars.content.statusEffect("cryon-decay");
                requirements(Category.turret, with(
                        Items.silicon, 550,
                        Items.graphite, 550,
                        Items.surgeAlloy, 300,
                        Items.phaseFabric, 300,
                        CryonContent.item("farstar-alloy"), 500

                ));
            }
        };
        OblivionUnit.load();
        DestroyerUnit.load();
    }

    // ══════════════════════════════════════════════════════════════
    //  init
    // ══════════════════════════════════════════════════════════════
    @Override
    public void init() {
        Mods.LoadedMod exist = Vars.mods.locateMod("unitlanuch");
        if(exist == null){
            Mods.LoadedMod self = Vars.mods.locateMod("cryon");
            if(self == null){
                Log.err("找不到 mod 'cryon',无法定位库文件所在目录");
                return;
            }

            Fi jar = self.root.child("单位发射台library.jar");
            if(!jar.exists()){
                Log.err("找不到jar文件: @", jar.absolutePath());
                return;
            }

            try{
                Vars.mods.importMod(jar);
                Log.info("成功导入 unitlanuch 库,强制重启生效");

                Events.on(ClientLoadEvent.class, e -> {
                    BaseDialog dialog = new BaseDialog("@cryon.unitlanuch.restart.title");
                    dialog.cont.add(Core.bundle.get("cryon.unitlanuch.restart")).pad(10f).wrap().width(400f);
                    dialog.buttons.button("@ok", Icon.ok, () -> {
                        dialog.hide();
                        Core.app.exit();
                    }).size(180f, 54f);
                    dialog.show();
                });

                return;
            }catch(IOException e){
                Log.err("导入 unitlanuch 失败: @", e.getMessage());
                e.printStackTrace();
                return;
            }
        }
        Block launchBlock = Vars.content.block("unitlanuch-发射台");
        if (launchBlock != null) launchBlock.buildVisibility = BuildVisibility.sandboxOnly;

        Block receiveBlock = Vars.content.block("unitlanuch-接收台");
        if (receiveBlock != null) receiveBlock.buildVisibility = BuildVisibility.sandboxOnly;

        UnitType peak  = Vars.content.unit("cryon-peak");
        UnitType umbra = Vars.content.unit("cryon-umbra");
        UnitType murex = Vars.content.unit("cryon-murex");
        UnitType buffer   = Vars.content.unit("cryon-buffer");
        UnitType guardian = Vars.content.unit("cryon-guardian");
        Block wall     = Vars.content.block("cryon-composite-wall-large");
        UnitType bolide = Vars.content.unit("cryon-bolide");
        UnitType littorina = Vars.content.unit("cryon-littorina");
        UnitType natica = Vars.content.unit("cryon-natica");
        Item farstarAlloy = Vars.content.item("cryon-farstar-alloy");
        Item magnesium = CryonContent.item("magnesium");
        Item aluminum = CryonContent.item("aluminum");
        Planet cryonPlanet = Vars.content.planet("cryon-cryon");
        // generator
        if(cryonPlanet != null) {
            cryonPlanet.defaultEnv = Env.terrestrial;
            CryonPlanetGenerator generator = new CryonPlanetGenerator();

            generator.baseSeed = 12;
            cryonPlanet.updateLighting = false;
            cryonPlanet.generator = generator;
            cryonPlanet.allowWaves = true;
            cryonPlanet.allowSectorInvasion = true;
            cryonPlanet.enemyCoreSpawnReplace = true;
            cryonPlanet.ruleSetter = r -> {
                r.lighting = true;  // 启用光照
                r.ambientLight = Color.valueOf("03030dd9");  // 暗环境光
                r.fire=false;



            };

        }
        fluxBarrier.requirements(Category.effect, BuildVisibility.shown, new ItemStack[]{
                new ItemStack(Items.graphite, 80),
                new ItemStack(magnesium, 60),
                new ItemStack(aluminum, 100),
        });
        agitatorTower.requirements(Category.effect, BuildVisibility.shown, new ItemStack[]{
                new ItemStack(farstarAlloy, 60),
                new ItemStack(Items.phaseFabric, 40),
                new ItemStack(Items.silicon, 80),
        });


        if (cryonPlanet == null) Log.warn("[CryonCore] Cryon is not enabled.");

        t3universalAssembler.requirements(Category.units, BuildVisibility.shown, new ItemStack[]{
                new ItemStack(farstarAlloy, 500),
                new ItemStack(Items.phaseFabric, 150),
                new ItemStack(Items.graphite, 80),
                new ItemStack(Items.silicon, 650),
        });
        if (cryonPlanet != null) t3universalAssembler.shownPlanets.add(cryonPlanet);

        if (cryonPlanet != null) {
            constructorSource.shownPlanets.add(cryonPlanet);
            constructorSink.shownPlanets.add(cryonPlanet);
            constructorDrill.shownPlanets.add(cryonPlanet);
        }
        abyss.requirements(Category.turret, with(
                farstarAlloy, 1000,
                Items.silicon, 800,
                Items.graphite, 600,
                Items.phaseFabric, 400
        ));

        constructorNode.requirements(Category.power, BuildVisibility.shown, new ItemStack[]{
                new ItemStack(Items.phaseFabric,  10),
                new ItemStack(farstarAlloy, 6),
        });
        if (cryonPlanet != null) constructorNode.shownPlanets.add(cryonPlanet);

        waveEmitter.requirements(Category.power, BuildVisibility.shown, new ItemStack[]{
                new ItemStack(Items.phaseFabric, 60),
                new ItemStack(farstarAlloy, 50),
                new ItemStack(Items.silicon, 40)
        });
        if (cryonPlanet != null) waveEmitter.shownPlanets.add(cryonPlanet);
        // 添加生产计划
        if (peak != null) {
            t3universalAssembler.plans.add(new UniversalUnitAssembler.AssemblerUnitPlan() {{
                unit = peak;
                time = 2000f;
                requirements = Seq.with(
                        new PayloadStack(guardian, 2),
                        new PayloadStack(wall, 4)
                );
                liquidReq = new LiquidStack[]{
                        new LiquidStack(Vars.content.liquid("hydrogen"), (15f/60f))
                };
            }});
        }

        if (umbra != null) {
            t3universalAssembler.plans.add(new UniversalUnitAssembler.AssemblerUnitPlan() {{
                unit = umbra;
                time = 2000f;
                requirements = Seq.with(
                        new PayloadStack(bolide, 2),
                        new PayloadStack(wall, 5)
                );
                liquidReq = new LiquidStack[]{
                        new LiquidStack(Vars.content.liquid("hydrogen"), (15f/60f))
                };
            }});
        }

        if (murex != null) {
            t3universalAssembler.plans.add(new UniversalUnitAssembler.AssemblerUnitPlan() {{
                unit = murex;
                time = 2000f;
                requirements = Seq.with(
                        new PayloadStack(natica, 2),
                        new PayloadStack(wall, 4)
                );
                liquidReq = new LiquidStack[]{
                        new LiquidStack(Vars.content.liquid("hydrogen"), (15f/60f))
                };
            }});
        }
        t3universalAssembler.initCapacities();

        t4universalAssembler.requirements(Category.units, BuildVisibility.shown, new ItemStack[]{
                new ItemStack(farstarAlloy, 1500),
                new ItemStack(Items.phaseFabric, 300),
                new ItemStack(Items.graphite, 150),
                new ItemStack(Items.silicon, 1200),
        });
        if (cryonPlanet != null) t4universalAssembler.shownPlanets.add(cryonPlanet);

        // T4
        UnitType sagitta = Vars.content.unit("cryon-sagitta");
        UnitType blaze = Vars.content.unit("cryon-blaze");
        UnitType charonia = Vars.content.unit("cryon-charonia");
        Block chargedWall = Vars.content.block("cryon-charged-surge-wall-large");
        UnitType umbra_prev = umbra;
        UnitType peak_prev = peak;

        if (sagitta != null && umbra_prev != null) {
            t4universalAssembler.plans.add(new UniversalUnitAssembler.AssemblerUnitPlan() {{
                unit = sagitta;
                time = 4000f;
                requirements = Seq.with(
                        new PayloadStack(umbra_prev, 2),
                        new PayloadStack(chargedWall, 5)
                );
                liquidReq = new LiquidStack[]{
                        new LiquidStack(Vars.content.liquid("nitrogen"), (20f/60f))
                };
            }});
        }

        if (blaze != null && peak_prev != null) {
            t4universalAssembler.plans.add(new UniversalUnitAssembler.AssemblerUnitPlan() {{
                unit = blaze;
                time = 4000f;
                requirements = Seq.with(
                        new PayloadStack(peak_prev, 2),
                        new PayloadStack(chargedWall, 5)
                );
                liquidReq = new LiquidStack[]{
                        new LiquidStack(Vars.content.liquid("nitrogen"), (20f/60f))
                };
            }});
        }

        if (charonia != null) {
            t4universalAssembler.plans.add(new UniversalUnitAssembler.AssemblerUnitPlan() {{
                unit = charonia;
                time = 4000f;
                requirements = Seq.with(
                        new PayloadStack(murex, 2),
                        new PayloadStack(chargedWall, 5)
                );
                liquidReq = new LiquidStack[]{
                        new LiquidStack(Vars.content.liquid("nitrogen"), (20f/60f))
                };
            }});
        }

        t4universalAssembler.initCapacities();

        //abilities
        UnitType comet = Vars.content.units().find(u -> u.name.equals("cryon-comet"));
        if (comet != null) {
            comet.abilities.add(new FluxBarrierAbility(30f, 100f, 20f));
        }

        if (bolide != null) {
            bolide.abilities.add(new FluxBarrierAbility(50f, 300f, 50f));
        }

        if (natica != null) {
            natica.abilities.add(new ElectronicJammerAbility(120f, 60f, 0.65f));
        }
        if (murex != null) {
            murex.abilities.add(new ElectronicJammerAbility(120f, 60f, 0.65f));
        }
        if (umbra != null) {
            umbra.abilities.add(new SafeFluxBarrierAbility(50f, 100f, 20f,4,45f,600));
        }
        // TechTree
        Events.on(ClientLoadEvent.class, e -> {
            CryonTechTree.load();

             TechNode root = TechTree.roots.find(
                    n -> n.content != null && n.content.name.equals("cryon-core-pioneer"));
            if (root == null) {
                Log.warn("[CryonCore] Cryon-core-pioneer not found");
                return;
            }
            reorderChildren(root, new String[]{
                    "cryon-vacuum-conduit",
                    "cryon-cryo-conduit",
                    "cryon-shattering-drill",
                    "cryon-spiral",
                    "core-outpost",
                    "cryon-unit-projector",
                    "cryon-cryon-sector-1",
                    "cryon-magnesium"
            });
        });

        HeatBoostTurret.inject("cryon-spiral", 4f, 1.0f);
        HeatBoostTurret.inject("cryon-torrent", 4f, 1.0f);

        MeltingDrillInjector.inject("cryon-melting-drill",5f,0.05f, 60f, 150f);
        DeuteriumReactorInjector.inject("cryon-deuterium-reactor", "cryon-tritium", 0.125f);
        Events.on(ClientLoadEvent.class, e -> {
            if (fluxBarrier.fluxShader == null) {
                fluxBarrier.fluxShader = new FluxShieldShader();
            }
        });
        Events.run(Trigger.draw, () -> {
            FluxShieldRenderer.drawFluxShields();
        });
        // 添加渲染钩子 - 绘制链接
        Events.run(Trigger.draw, () -> {
            KismetBulletType.drawLinks();
        });

        // 添加更新钩子 - 清理无效链接
        Events.run(Trigger.update, () -> {
            KismetBulletType.cleanup();
        });
        Events.run(Trigger.draw, () -> {
            BeaconBulletType.drawMarkers();
        });


        //SectorIdDebug.install();
    }


    // ══════════════════════════════════════════════════════════════
    //  reorderChildren
    // ══════════════════════════════════════════════════════════════
    private void reorderChildren(TechNode parent, String[] orderedNames) {
        Seq<TechNode> original = new Seq<>(parent.children);
        parent.children.clear();

        for (String name : orderedNames) {
            for (TechNode child : original) {
                if (child.content != null && child.content.name.equals(name)) {
                    parent.children.add(child);
                    break;
                }
            }
        }

        // 把未在 orderedNames 中出现的节点追加到末尾
        for (TechNode child : original) {
            if (!parent.children.contains(child)) {
                parent.children.add(child);
            }
        }
    }
    private void debugTechNode(String contentName){
        UnlockableContent c0 = Vars.content.getByName(mindustry.ctype.ContentType.unit, contentName);
        if(c0 == null) c0 = Vars.content.getByName(mindustry.ctype.ContentType.block, contentName);

        if(c0 == null){
            return;
        }

        final UnlockableContent c = c0;
        TechNode node = TechTree.all.find(n -> n.content == c);
        if(node == null){
            return;
        }

        Log.info("[TechDebug] '@' -> node found. parent=@, planet=@, children=@",
                contentName,
                node.parent == null ? "null" : node.parent.content.name,
                node.planet == null ? "null" : node.planet.name,
                node.children.size);

        TechNode cur = node;
        Seq<String> chain = new Seq<>();
        chain.add(cur.content.name);
        int guard = 0;
        while(cur.parent != null && guard++ < 20){
            cur = cur.parent;
            chain.add(cur.content.name);
        }
        boolean isRoot = TechTree.roots.contains(cur);
        Log.info("[TechDebug] '@' -> chain to top: @ | reaches a registered root: @",
                contentName, chain.toString(" <- "), isRoot);
    }
}
