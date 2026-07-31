package example;

import arc.*;
import arc.graphics.Color;
import arc.util.*;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.ctype.ContentType;
import mindustry.ctype.UnlockableContent;
import mindustry.game.EventType.*;
import mindustry.content.TechTree;
import mindustry.content.TechTree.TechNode;
import mindustry.mod.*;
import mindustry.type.*;
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

import static mindustry.type.ItemStack.*;
//This project was developed using AI.
public class ExampleJavaMod extends Mod {


    public ExampleJavaMod() {}

    public static FluxBarrier    fluxBarrier;
    public static AgitatorBlock  agitatorTower;
    public static UniversalUnitAssembler t3universalAssembler;
    public static ConstructorSource constructorSource;
    public static ConstructorSink   constructorSink;
    public static ConstructorNode   constructorNode;
    public static GenericConstructorSource waveEmitter; // 提升为字段，方便在 init() 里设置依赖 cryon- 内容的字段
    public static ConstructorDrill constructorDrill;

    public static PowerTurret abyss;

    // ══════════════════════════════════════════════════════════════
    //  loadContent
    // ══════════════════════════════════════════════════════════════
    @Override
    public void loadContent() {

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
        constructorDrill = new ConstructorDrill("constructor-drill") {{
            requirements(Category.production, new ItemStack[]{
                    new ItemStack(Items.phaseFabric, 20),
                    new ItemStack(Items.graphite,    20),
                    new ItemStack(Items.silicon,     30),
            });
            tier        = 3;
            drillTime   = 150;
            size        = 3;
            constructorConsumption = 1f;
        }};

        //Abyss
        abyss = new PowerTurret("abyss"){{


            size = 5;

            var arrayProgress = PartProgress.warmup;
            Color arrayColor = Color.valueOf("6ea8ff"), heatCol = Color.valueOf("aee3ff");
            float arrayY = -18f, arrayRotSpeed = 1.2f;

            shootSound = Sounds.shootArc;
            loopSound = Sounds.loopSmelter;
            loopSoundVolume = 1.4f;

            shootType = new BasicBulletType(4f, 550f){{
                sprite = "cryon-java-dependency-tesla-orb";
                width = 40f;
                height = 40f;
                lifetime = 65f;
                hitEffect = despawnEffect = Fx.scatheExplosion;
                hitSound = Sounds.explosion;

                hitColor = backColor = trailColor = Color.valueOf("00d8d8");
                lightning = 6;
                lightningLength = 28;
                lightningLengthRand = 20;
                lightningDamage = 120;
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
            health = 2500;
            inaccuracy = 2f;
            rotateSpeed = 10f;
            coolant = consumeCoolant(0.1f);
            coolantMultiplier = 10f;
            researchCostMultiplier = 0.05f;
            depositCooldown = 2.0f;

            limitRange(5f);
        }};
        OblivionUnit.load();
    }

    // ══════════════════════════════════════════════════════════════
    //  init
    // ══════════════════════════════════════════════════════════════
    @Override
    public void init() {

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
        Planet cryonPlanet = Vars.content.planet("cryon-cryon");
        // generator
        if(cryonPlanet != null) {
            cryonPlanet.defaultEnv = Env.terrestrial;
            CryonPlanetGenerator generator = new CryonPlanetGenerator();

            generator.baseSeed = 12;

            cryonPlanet.generator = generator;

        }


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
        abyss.shownPlanets.add(cryonPlanet);
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
                    "cryon-unit-projector",
                    "cryon-cryon-sector-1",
                    "cryon-magnesium"
            });

            TechNode parentNode = null;
            for (TechNode node : TechTree.all) {
                if (node.content != null && node.content.name.equals("cryon-t2factory")) {
                    parentNode = node;
                    break;
                }
            }

            if (parentNode == null) {
            } else {
                SectorPreset neutronFluxZone = Vars.content.getByName(
                        ContentType.sector,
                        "cryon-cryon-neutron-flux-zone"
                );

                Seq<Objective> objectives = Seq.with();
                if (neutronFluxZone != null) {
                    objectives.add(new Objectives.SectorComplete(neutronFluxZone));
                }

                TechNode assemblerNode = new TechNode(parentNode, t3universalAssembler, new ItemStack[]{
                        new ItemStack(farstarAlloy, 500),
                        new ItemStack(Items.phaseFabric, 150),
                        new ItemStack(Items.graphite, 80),
                        new ItemStack(Items.silicon, 650),
                });
                assemblerNode.objectives = objectives;

                if (!parentNode.children.contains(assemblerNode)) {
                    parentNode.children.add(assemblerNode);
                }

                //peak, umbra, murex
                String[] unitNames = {"cryon-peak", "cryon-umbra", "cryon-murex"};
                for (String unitName : unitNames) {
                    UnitType unitType = Vars.content.unit(unitName);
                    if (unitType == null) {
                        continue;
                    }

                    TechNode unitNode = TechTree.all.find(n -> n.content == unitType);
                    if (unitNode == null) {
                        continue;
                    }

                    Seq<Objective> existingObjectives = unitNode.objectives;
                    if (existingObjectives == null) {
                        existingObjectives = new Seq<>();
                    }

                    boolean alreadyHasResearch = existingObjectives.contains(
                            obj -> obj instanceof Objectives.Research &&
                                    ((Objectives.Research) obj).content == t3universalAssembler
                    );

                    if (!alreadyHasResearch) {
                        existingObjectives.add(new Objectives.Research(t3universalAssembler));
                    }

                    unitNode.objectives = existingObjectives;
                }
            }

            // Add a tech tree chain

            TechNode phaseReactorNode = null;
            for (TechNode node : TechTree.all) {
                if (node.content != null && node.content.name.equals("cryon-phase-reactor")) {
                    phaseReactorNode = node;
                    break;
                }
            }

            if (phaseReactorNode == null) {
            } else {
                TechNode constructorNodeTech = new TechNode(phaseReactorNode, constructorNode, new ItemStack[]{
                        new ItemStack(Items.phaseFabric, 10),
                        new ItemStack(farstarAlloy, 6)
                });

                if (!phaseReactorNode.children.contains(constructorNodeTech)) {
                    phaseReactorNode.children.add(constructorNodeTech);
                }

                TechNode waveEmitterNode = new TechNode(constructorNodeTech, waveEmitter, new ItemStack[]{
                        new ItemStack(Items.phaseFabric, 60),
                        new ItemStack(farstarAlloy, 50),
                        new ItemStack(Items.silicon, 40)
                });

                if (!constructorNodeTech.children.contains(waveEmitterNode)) {
                    constructorNodeTech.children.add(waveEmitterNode);
                }
                TechNode constructorDrillNode = new TechNode(waveEmitterNode, constructorDrill, new ItemStack[]{
                        new ItemStack(Items.phaseFabric, 20),
                        new ItemStack(Items.graphite, 20),
                        new ItemStack(Items.silicon, 30)
                });

                if (!waveEmitterNode.children.contains(constructorDrillNode)) {
                    waveEmitterNode.children.add(constructorDrillNode);
                }
            }
        });
        HeatBoostTurret.inject("cryon-spiral", 4f, 1.0f);
        HeatBoostTurret.inject("cryon-torrent", 4f, 1.0f);

        MeltingDrillInjector.inject("cryon-melting-drill",5f,0.05f, 60f, 150f);
        DeuteriumReactorInjector.inject("cryon-deuterium-reactor", "cryon-tritium", 0.125f);


        //SectorIdDebug.install();
        //DebugUnlock.enabled = true;
        //DebugUnlock.apply();
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