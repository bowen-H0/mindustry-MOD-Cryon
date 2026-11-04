import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import java.io.*;
import java.lang.reflect.*;
import mindustry.*;
import mindustry.ai.*;
import mindustry.ai.types.*;
import mindustry.content.*;
import mindustry.core.Logic;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.net.Net;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.units.*;
import mindustry.world.blocks.payloads.UnitPayload;
import mindustry.world.consumers.*;

/** Combat, concealment, production, exact pump throughput, editor geology and map-scope checks. */
public class DeepSeaContentCheck{
    static ClassLoader loader;
    static UnitType[] subs;
    static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
    static Object get(Object o,String field)throws Exception{return o.getClass().getField(field).get(o);}
    static void set(Object o,String field,Object value)throws Exception{o.getClass().getField(field).set(o,value);}
    static void invoke(String type,String method)throws Exception{loader.loadClass(type).getMethod(method).invoke(null);}
    static void ticks(Building b,int count){for(int i=0;i<count;i++){Time.delta=1;Time.time++;b.updateConsumption();b.updateTile();}}
    static Building place(String name,int x,int y){Vars.world.tile(x,y).setBlock(Vars.content.block("cryon-"+name),Team.sharded);return Vars.world.tile(x,y).build;}
    static void index(){Vars.state.teams.updateTeamStats();for(Team team:new Team[]{Team.sharded,Team.crux}){team.data().tree().clear();for(Unit unit:Groups.unit)if(unit.team==team)team.data().tree().insert(unit);}}
    static void world(Planet planet){
        Vars.logic.reset();Vars.state.rules=new Rules();Vars.state.rules.unitCap=200;
        Vars.state.rules.sector=planet.getStartSector();Vars.state.rules.planet=planet;
        Vars.world.loadGenerator(180,140,tiles->{for(int x=0;x<180;x++)for(int y=0;y<140;y++)tiles.set(x,y,new Tile(x,y,Vars.content.block("cryon-abyss-blue-bed"),Blocks.air,Blocks.air));});
        Vars.state.rules.env=planet.defaultEnv;
    }
    public static void main(String[] args)throws Exception{
        Core.app=new MockApplication();Core.files=new MockFiles();Core.graphics=new MockGraphics();Core.audio=new MockAudio();
        Core.settings=new Settings();Core.settings.setDataDirectory(new Fi("/tmp/deepsea-content-check"));Core.bundle=I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(Core.settings.getDataDirectory().child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(Core.settings.getDataDirectory().child("mods/unitlanuch.jar"));
        Vars.headless=true;Vars.loadLocales=false;Vars.net=new Net(null);Vars.platform=new mindustry.core.Platform(){};
        mindustry.core.Version.init();Vars.init();Vars.content.createBaseContent();Vars.content.createModContent();Vars.content.init();
        var mod=Vars.mods.locateMod("cryon");check(mod!=null&&!mod.hasContentErrors(),"Content errors: "+mod.erroredContent);
        loader=mod.main.getClass().getClassLoader();
        invoke("cryon.Content.SubmarineContent","configureProduction");invoke("cryon.Features.DeepSeaRules","configure");invoke("cryon.Features.CryonCampaignLayers","configure");
        Planet deep=Vars.content.planet("cryon-cryon-depths"),surface=Vars.content.planet("cryon-cryon");
        var root=new TechTree.TechNode(null,Vars.content.block("cryon-core-pioneer"),ItemStack.empty);surface.techTree=root;
        new TechTree.TechNode(root,Vars.content.block("cryon-unit-projector"),ItemStack.empty);
        invoke("cryon.Features.CryonCampaignLayers","shareResearch");
        String[] names={"minimus","danae","pammelas","schmidti","sloani"};subs=new UnitType[5];
        for(int i=0;i<5;i++){
            subs[i]=Vars.content.unit("cryon-"+names[i]);subs[i].unlock();check(subs[i]!=null&&subs[i].create(Team.sharded) instanceof WaterMovec,"Not a naval submarine: "+names[i]);
            check(subs[i].techNode!=null&&subs[i].weapons.contains(w->w instanceof mindustry.type.weapons.RepairBeamWeapon),"Missing support weapons/research");
            if(i>0)check(subs[i].techNode.parent.content==subs[i-1],"Submarine research line is disconnected");
        }
        check(Vars.content.block("cryon-abyss-titanium-bed").itemDrop==Items.titanium,"Wrong titanium bed resource");
        check(Vars.content.block("cryon-abyss-nickel-bed").itemDrop==Vars.content.item("cryon-nickel"),"Wrong nickel bed resource");
        check(!(Vars.content.block("cryon-abyss-vent") instanceof mindustry.world.blocks.environment.SteamVent),"Geothermal vent remains");
        int plants=0;for(var block:Vars.content.blocks())if(block.name.startsWith("cryon-abyss-")&&(block instanceof mindustry.world.blocks.environment.Seaweed||block instanceof mindustry.world.blocks.environment.SeaBush))plants++;
        check(plants>=6,"Missing dedicated deep-sea plants");
        Vars.logic=new Logic();world(deep);
        loader.loadClass("cryon.Content.CryonDepthsEnvironment").getMethod("applyRules",Rules.class).invoke(null,Vars.state.rules);
        check(Vars.state.rules.waveTeam==Team.crux&&Vars.state.rules.tags.getBool("cryon-environment-cold")&&Vars.state.rules.tags.getBool("cryon-environment-deepsea"),"Deep-sea/cold/Crux rules missing");
        check(UnitTypes.flare.isBanned()&&UnitTypes.risso.isBanned()&&Vars.content.unit("cryon-littorina").isBanned(),"Other combat units remain manufacturable");
        for(UnitType type:subs)check(!type.isBanned(),"Submarine banned: "+type);
        check(!((mindustry.world.blocks.storage.CoreBlock)deep.defaultCore).unitType.isBanned(),"Core builder banned");
        // Exact full-footprint output and power draw.
        var pump=Vars.content.block("cryon-powered-pump");
        check(pump.size==2&&pump.health==180&&pump.liquidCapacity==120,"Pump size/health/storage mismatch");
        check(((ConsumePower)pump.findConsumer(c->c instanceof ConsumePower)).usage==1.5f,"Pump does not consume 90 power/second");
        int[] amounts={90,60,30,20};Item[] materials={Vars.content.item("cryon-aluminum"),Items.silicon,Items.titanium,Items.graphite};
        for(int i=0;i<4;i++){final int n=i;check(Seq.with(pump.requirements).contains(s->s.item==materials[n]&&s.amount==amounts[n]),"Pump cost mismatch");}
        var pb=place("powered-pump",20,20);pb.power.status=1;ticks(pb,60);
        check(Math.abs(pb.liquids.get(Liquids.water)-60)<0.05,"Pump is not 60 liquid/second: "+pb.liquids.get(Liquids.water));
        pb.power.status=0;ticks(pb,60);check(Math.abs(pb.liquids.get(Liquids.water)-60)<0.05,"Unpowered pump runs");
        pb.power.status=1;ticks(pb,180);check(Math.abs(pb.liquids.get(Liquids.water)-120)<0.05,"Pump storage overflows");
        for(int x=27;x<=29;x++)for(int y=19;y<=21;y++)Vars.world.tile(x,y).setFloor(Blocks.deepwater.asFloor());
        var deepPump=place("powered-pump",28,20);deepPump.power.status=1;ticks(deepPump,60);
        check(Math.abs(deepPump.liquids.get(Liquids.water)-60)<0.05,"Deepwater exceeds the rated pump output: "+deepPump.liquids.get(Liquids.water));
        System.out.println("PUMP_PASS: 180 health, 2x2, exact recipe, 90 power/s, 60 liquid/s, 120 capacity");
        currentGenerator();
        // Real tier-one manufacture, blocked without processed inputs.
        var f=(UnitFactory)Vars.content.block("cryon-unit-projector");var fb=(UnitFactory.UnitFactoryBuild)place("unit-projector",40,20);
        fb.currentPlan=f.plans.indexOf(p->p.unit==subs[0]);check(fb.currentPlan>=0,"No Minimus projector plan");
        var recipe=f.plans.get(fb.currentPlan);fb.power.status=1;
        for(var cost:recipe.requirements)if(cost.item!=Items.silicon)fb.items.add(cost.item,cost.amount);
        ticks(fb,(int)recipe.time+5);check(fb.payload==null,"Manufactured without silicon");
        for(var cost:recipe.requirements)if(cost.item==Items.silicon)fb.items.add(cost.item,cost.amount);
        ticks(fb,(int)recipe.time+1);check(fb.payload!=null&&fb.payload.unit.type==subs[0],"Minimus manufacture failed");
        var t2=(Reconstructor)Vars.content.block("cryon-t2factory");var rb=(Reconstructor.ReconstructorBuild)place("t2factory",60,20);
        check(t2.upgrades.contains(p->p[0]==subs[0]&&p[1]==subs[1]),"No T2 submarine upgrade");
        rb.payload=new UnitPayload(subs[0].create(Team.sharded));rb.power.status=1;
        var consume=(ConsumeItems)t2.findConsumer(c->c instanceof ConsumeItems);
        for(var cost:consume.items)rb.items.add(cost.item,cost.amount);
        ticks(rb,(int)t2.constructTime+1);check((rb.payload!=null&&rb.payload.unit.type==subs[1])||Groups.unit.contains(u->u.type==subs[1]),"Danae upgrade failed");
        for(var cost:consume.items)check(rb.items.get(cost.item)==0,"T2 upgrade did not consume materials");
        for(int tier=3;tier<=5;tier++){
            final int level=tier;
            var block=Vars.content.block("cryon-t"+tier+"universal-assembler");var build=place("t"+tier+"universal-assembler",30+(tier-3)*55,80);
            @SuppressWarnings("unchecked") Seq<Object> plans=(Seq<Object>)get(block,"plans");
            int found=-1;for(int i=0;i<plans.size;i++)if(get(plans.get(i),"unit")==subs[tier-1])found=i;
            check(found>=0,"No T"+tier+" assembly plan");Object plan=plans.get(found);set(build,"currentPlan",found);set(build,"constructorSatisfaction",1f);build.power.status=1;
            var payloads=(PayloadSeq)get(build,"blocks");@SuppressWarnings("unchecked") Seq<PayloadStack> req=(Seq<PayloadStack>)get(plan,"requirements");
            check(req.contains(p->p.item==subs[level-2]),"Assembly omits previous submarine tier");
            for(var cost:req)payloads.add(cost.item,cost.amount);
            for(var cost:(LiquidStack[])get(plan,"liquidReq"))build.liquids.add(cost.liquid,cost.amount*(Float)get(plan,"time")+10f);
            for(var consumer:block.consumers)if(consumer instanceof ConsumeLiquid fixed)build.liquids.add(fixed.liquid,fixed.amount*(Float)get(plan,"time")+50f);
            @SuppressWarnings("unchecked") Seq<Unit> drones=(Seq<Unit>)get(build,"units");
            for(int i=0;i<4;i++){Unit drone=UnitTypes.assemblyDrone.create(Team.sharded);drone.controller(new AssemblerAI(){@Override public boolean inPosition(){return true;}});drone.set(build.x,build.y);drone.add();drones.add(drone);}
            ticks(build,30);check((Float)get(build,"progress")==0f,"Assembly ran without processed items");
            for(var cost:(ItemStack[])get(plan,"itemReq"))build.items.add(cost.item,cost.amount);
            int previous=Groups.unit.count(u->u.type==subs[level-1]);ticks(build,(int)(float)(Float)get(plan,"time")+2);
            if(Groups.unit.count(u->u.type==subs[level-1])<=previous)System.out.println("ASSEMBLY_DIAG tier="+tier+" progress="+get(build,"progress")+" efficiency="+build.efficiency+" occupied="+get(build,"wasOccupied")+" drones="+drones.size+" constructor="+get(build,"constructorSatisfaction")+" items="+build.items+" liquids="+build.liquids.currentAmount()+" power="+build.power.status+" canCreate="+mindustry.entities.Units.canCreate(Team.sharded,subs[level-1]));
            check(Groups.unit.count(u->u.type==subs[level-1])>previous,"Assembly did not create tier "+tier);
            check(payloads.total()==0,"Assembly did not consume previous hulls and walls");
            for(var cost:(ItemStack[])get(plan,"itemReq"))check(build.items.get(cost.item)==0,"Assembly did not consume processed resources");
        }
        System.out.println("PRODUCTION_PASS: T1 projector, T2 reconstructor, real T3-T5 Cryon assembly consumes hulls, walls, fluids and processed inputs");
        stealth(deep);combat(deep);
        invoke("cryon.TechTree.CryonTechTree","load");
        var generator=Vars.content.block("cryon-underice-current-generator");
        check(generator.techNode!=null&&generator.techNode.parent.content==Vars.content.block("cryon-aluminum-node"),"Current generator is not a child of the power node");
        System.out.println("DEEPSEA_CONTENT_PASS: dedicated editor terrain, valid mineral beds, cold/deep-sea production restrictions, Crux, pump, stealth, torpedoes and mines");
    }

    static void currentGenerator()throws Exception{
        var type=(mindustry.world.blocks.power.PowerGenerator)Vars.content.block("cryon-underice-current-generator");
        check(type!=null&&type.size==2&&type.health==240&&type.powerProduction==2f,"Current generator rating mismatch");
        check(type.consumers.length==0&&!type.hasItems&&!type.hasLiquids&&!type.canOverdrive,"Current generator requires fuel or can exceed its rating");
        check(type.requirements.length==2&&Seq.with(type.requirements).contains(s->s.item==Vars.content.item("cryon-aluminum")&&s.amount==50)
                &&Seq.with(type.requirements).contains(s->s.item==Items.silicon&&s.amount==30),"Current generator cost mismatch");
        Tile tile=Vars.world.tile(20,35),corner=Vars.world.tile(21,36);
        check(type.canPlaceOn(tile,Team.sharded,0),"Cannot place turbine in deep-sea water");
        Building generator=place("underice-current-generator",20,35);
        Building pump=place("powered-pump",24,35);
        var grid=new mindustry.world.blocks.power.PowerGraph();grid.add(generator);grid.add(pump);
        for(int i=0;i<60;i++){Time.delta=1;generator.updateTile();grid.update();ticks(pump,1);}
        check(Math.abs(generator.getPowerProduction()*60f-120f)<0.01,"Wrong sustained generator output");
        check(Math.abs(pump.liquids.get(Liquids.water)-60f)<0.05,"Turbine could not start and power a 90 power/s pump without fuel or stored power");
        for(Block floor:new Block[]{Blocks.sand,Blocks.slag,Blocks.tar,Blocks.cryofluid,Blocks.bluemat}){
            corner.setFloor(floor.asFloor());
            check(!type.canPlaceOn(tile,Team.sharded,0)&&generator.getPowerProduction()==0,"Turbine operates with a non-water footprint: "+floor);
            generator.updateTile();check(((mindustry.world.blocks.power.PowerGenerator.GeneratorBuild)generator).productionEfficiency==0,"Dry turbine reports active production");
        }
        for(Block floor:new Block[]{Blocks.water,Blocks.deepwater,Vars.content.block("cryon-abyss-shallows"),Vars.content.block("cryon-abyss-blue-bed")}){
            corner.setFloor(floor.asFloor());generator.updateTile();
            check(type.canPlaceOn(tile,Team.sharded,0)&&generator.getPowerProduction()==2,"Turbine rejected water: "+floor);
        }
        generator.enabled=false;check(generator.getPowerProduction()==0,"Disabled turbine generates power");generator.enabled=true;
        int env=Vars.state.rules.env;Vars.state.rules.env=mindustry.world.meta.Env.terrestrial;
        check(!type.supportsEnv(Vars.state.rules.env)&&generator.getPowerProduction()==0,"Turbine operates outside underwater environments");Vars.state.rules.env=env;
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();generator.write(new Writes(new DataOutputStream(bytes)));
        generator=place("underice-current-generator",20,35);generator.read(new Reads(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))),generator.version());
        check(generator.getPowerProduction()==2,"Loaded turbine does not resume generation");
        System.out.println("CURRENT_GENERATOR_PASS: 120 power/s, exact recipe, self-starts a real pump, water-only footprint, underwater rules, disable and save/load");
    }
    static void stealth(Planet deep)throws Exception{
        world(deep);Unit sub=subs[2].create(Team.sharded);sub.set(500,500);sub.add();
        Unit enemy=UnitTypes.dagger.create(Team.crux);enemy.set(900,500);enemy.add();index();
        StatusEffect status=Vars.content.getByName(mindustry.ctype.ContentType.status,"cryon-deep-diving");
        UnitStance stance=Vars.content.unitStances().find(s->s.name.equals("cryon-deep-dive"));
        sub.command().setStance(stance);for(var a:sub.abilities)a.update(sub);
        check(sub.hasEffect(status),"Dive stance failed");check(!sub.targetable(Team.crux)&&sub.inFogTo(Team.crux),"Distant enemy detects a diving submarine");
        check(sub.targetable(Team.sharded)&&!sub.inFogTo(Team.sharded),"Allies cannot see their submarine");
        enemy.set(540,500);index();check(sub.targetable(Team.crux)&&!sub.inFogTo(Team.crux),"Nearby enemy cannot detect submarine");
        enemy.remove();index();Vars.world.tile(65,62).setBlock(Blocks.copperWall,Team.crux);
        check(sub.targetable(Team.crux),"Nearby enemy building does not detect submarine");Vars.world.tile(65,62).remove();
        sub.update();check(sub.speedMultiplier<0.7f,"Diving does not reduce speed");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();sub.write(Writes.get(new DataOutputStream(bytes)));
        Unit copy=subs[2].create(Team.sharded);copy.read(Reads.get(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        copy.afterRead();copy.afterReadAll();
        check(copy.getDuration(status)>0,"Dive status lost on save/load");
        check(copy.isCommandable()&&copy.command().hasStance(stance),"Dive stance lost on save/load");
        copy.update();check(copy.hasEffect(status),"Loaded dive status does not activate");
        sub.command().disableStance(stance);for(var a:sub.abilities)a.update(sub);sub.update();check(!sub.hasEffect(status)&&sub.targetable(Team.crux),"Surface stance did not restore visibility");
        System.out.println("STEALTH_PASS: enemy range/building detection, allied visibility, slower travel, surface toggle and save/load");
    }
    static void combat(Planet deep)throws Exception{
        shoreline(deep);
        world(deep);Unit owner=subs[4].create(Team.sharded);owner.set(400,400);owner.vel.set(0.4f,0);owner.add();
        var heavy=subs[4].weapons.find(w->w.bullet.getClass().getSimpleName().equals("HeavyTorpedoBulletType")).bullet;
        Bullet torpedo=heavy.create(owner,Team.sharded,400,400,0);float speed=torpedo.vel.len();
        check(speed>heavy.speed&&heavy.homingPower==0&&heavy.splashDamage>=1500,"Heavy torpedo lacks inertia or heavy blast");
        for(int i=0;i<90;i++){Time.delta=1;torpedo.update();}check(torpedo.vel.len()<speed&&torpedo.vel.len()>0.4f,"Heavy torpedo does not coast");
        Unit enemy=subs[4].create(Team.crux);enemy.set(650,400);enemy.add();Unit ally=subs[3].create(Team.sharded);ally.set(655,400);ally.add();index();
        float before=enemy.health,friend=ally.health;heavy.hit(torpedo,650,400);check(enemy.health<before-500&&ally.health==friend,"Heavy explosion lacks damage or harms allies");
        Object ability=subs[1].abilities.find(a->a.getClass().getSimpleName().equals("SeaMineLayerAbility"));
        var mine=(mindustry.entities.bullet.BulletType)get(ability,"mine");
        Unit target=subs[2].create(Team.crux);target.set(1000,600);target.apply(Vars.content.getByName(mindustry.ctype.ContentType.status,"cryon-deep-diving"));target.add();index();
        Bullet charge=mine.create(owner,Team.sharded,1000,600,0);charge.time=0;mine.update(charge);check(charge.isAdded(),"Mine exploded before arming");
        float hp=target.health;charge.time=60;mine.update(charge);check(!charge.isAdded()&&target.health<hp,"Mine does not trigger on a hostile diving hull");
        enemy.remove();index();
        Bullet friendly=mine.create(owner,Team.sharded,ally.x,ally.y,0);friendly.time=60;mine.update(friendly);check(friendly.isAdded(),"Mine triggered on friendly ship");
        System.out.println("COMBAT_PASS: slow inertial torpedo with real heavy splash; armed proximity sea mines detect submerged enemies and ignore allies");
    }

    static void shoreline(Planet deep){
        world(deep);
        // A one-tile island must stop even a torpedo that crosses it in one tick.
        Vars.world.tile(60,50).setFloor(Blocks.sand.asFloor());
        for(UnitType sub:subs){
            var type=sub.weapons.find(w->w.bullet.getClass().getSimpleName().equals("TorpedoBulletType")||w.bullet.getClass().getSimpleName().equals("HeavyTorpedoBulletType")).bullet;
            for(int direction:new int[]{-1,1}){
                float start=480-direction*24;
                Bullet b=type.create(null,Team.sharded,start,400,direction>0?0:180);
                b.lastX=start;b.lastY=400;b.set(480+direction*24,400);
                type.update(b);
                check(!b.isAdded()&&b.hit,"Torpedo crossed the shore: "+sub.name);
                check(Math.abs(b.x-(480-direction*4))<0.02f&&Vars.world.tileWorld(b.x,b.y).floor().isLiquid,"Torpedo exploded on land instead of the water edge: "+sub.name);
            }
            for(float[] path:new float[][]{{480,376,480,424},{480,424,480,376},{456,376,504,424}}){
                Bullet b=type.create(null,Team.sharded,path[0],path[1],0);
                b.lastX=path[0];b.lastY=path[1];b.set(path[2],path[3]);type.update(b);
                check(!b.isAdded()&&Vars.world.tileWorld(b.x,b.y).floor().isLiquid,"Vertical/diagonal shore collision failed: "+sub.name);
            }
            Vars.world.tile(55,50).setFloor(Vars.content.block("cryon-abyss-shallows").asFloor());
            Bullet water=type.create(null,Team.sharded,440,400,0);
            type.update(water);check(water.isAdded(),"Torpedo exploded in liquid shallows: "+sub.name);water.remove();
        }
        System.out.println("SHORELINE_PASS: all five submarine torpedoes detonate at the water edge, including fast, reverse and diagonal crossings; liquid shallows remain navigable");
    }
}
