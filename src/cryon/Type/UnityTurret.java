package cryon.Type;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.struct.*;
import arc.util.Time;
import arc.util.io.*;
import cryon.Content.CryonItems;
import cryon.Constructor.ConstructorConsumer;
import cryon.Unity.*;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.logic.LAccess;
import mindustry.type.*;
import mindustry.ui.Bar;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.ConstructBlock;
import mindustry.world.blocks.heat.HeatConsumer;
import cryon.Injector.HeatBoostTurret.HeatBoostTurretBuild;
import mindustry.entities.units.BuildPlan;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.meta.*;

/** A bare fusion platform. Native turret builds supply their original weapon behavior. */
public class UnityTurret extends Block{
    public final boolean finished;
    public UnityTurret platform, finishedBlock;
    public UnityConstruction construction;
    private static final IntSet coloredPositions=new IntSet();
    // Newer game builds cache this flag; v157 computes it from logicShooting instead.
    private static final java.lang.reflect.Field shootingField=findShootingField();
    private static java.lang.reflect.Field findShootingField(){
        try{return Turret.TurretBuild.class.getField("isShooting");}catch(NoSuchFieldException ignored){return null;}
    }
    private arc.graphics.g2d.TextureRegion head;

    public UnityTurret(String name){this(name,false);}
    private UnityTurret(String name,boolean finished){
        super(name);this.finished=finished;
        size=5;health=6400;
        if(!finished){
            platform=this;
            finishedBlock=new UnityTurret(name+"-finished",true);
            finishedBlock.platform=this;finishedBlock.finishedBlock=finishedBlock;
            construction=new UnityConstruction(name+"-construction",this);
            finishedBlock.construction=construction;
            // Retain the old content name so existing saved unit plans can be discarded safely.
            Block legacy=new Block(name+"-work");legacy.buildVisibility=BuildVisibility.hidden;legacy.inEditor=false;
        }
        attacks=true;priority=mindustry.entities.TargetPriority.turret;
        group=BlockGroup.turrets;flags=arc.struct.EnumSet.of(BlockFlag.turret);
        solid=update=hasItems=hasLiquids=hasPower=true;configurable=!finished;
        sync=true;
        if(!finished){
        arc.Events.on(mindustry.game.EventType.ResetEvent.class,event->{coloredPositions.clear();});
        arc.Events.run(mindustry.game.EventType.Trigger.afterGameUpdate,()->{
            if(!Vars.net.client() || coloredPositions.isEmpty())return;
            Groups.bullet.each(b->{
                if(!(b.owner instanceof Building))return;
                Building owner=(Building)b.owner;
                if(owner.tile!=null && coloredPositions.contains(owner.pos()) && owner.tile.build instanceof UnityBuild){
                    UnityBuild platform=(UnityBuild)owner.tile.build;
                    b.type=platform.fusion==null?platform.palette.apply(b.type):platform.fusion.remap(b.type,b.damage);
                }
            });
        });
        }
        if(!finished){
            arc.Events.on(mindustry.game.EventType.ClientLoadEvent.class,event->{
                Vars.netClient.addPacketHandler("cryon-unity-migrated",data->{
                    try{
                        String[] parts=data.split("\\|",2);Building b=Vars.world.build(Integer.parseInt(parts[0]));
                        if(b instanceof UnityBuild && ((UnityTurret)b.block).finished)((UnityBuild)b).install(UnityBlueprint.decode(parts[1]));
                    }catch(Exception ignored){}
                });
                Vars.netClient.addPacketHandler("cryon-unity-site",data->{
                    try{
                        String[] parts=data.split("\\|",3);
                        Building b=Vars.world.build(Integer.parseInt(parts[0]));
                        if(b instanceof UnityConstruction.Site)((UnityConstruction.Site)b).initialize(UnityBlueprint.decode(parts[2]),parts[1].equals("break"));
                    }catch(Exception ignored){}
                });
            });
            arc.Events.on(mindustry.game.EventType.BlockBuildBeginEvent.class,event->{
                if(Vars.net.client() || !event.breaking || !(event.tile.build instanceof ConstructBlock.ConstructBuild))return;
                ConstructBlock.ConstructBuild site=(ConstructBlock.ConstructBuild)event.tile.build;
                if(site.prevBuild==null)return;
                for(Building old:site.prevBuild)if(old instanceof UnityBuild && old.block==finishedBlock){
                    construction.replace(site,((UnityBuild)old).blueprint,true);break;
                }
            });
        }
        itemCapacity=2000;liquidCapacity=2000f;
        saveConfig=false;copyConfig=false;
        buildType=UnityBuild::new;
        if(finished){
            requirements(Category.turret,BuildVisibility.hidden,ItemStack.empty);
            alwaysUnlocked=true;inEditor=false;buildTime=600f;
        }else requirements(Category.turret,BuildVisibility.shown,ItemStack.with(
            CryonItems.aluminum,350,Items.silicon,250,CryonItems.quartz,200,CryonItems.nanoMaterial,60));
        consumePowerDynamic((UnityBuild b)->b.powerUse());
        config(String.class,(UnityBuild b,String value)->{
            if(!finished && value.startsWith("build:")){
                try{b.begin(UnityBlueprint.decode(value.substring(6)));}catch(Exception ignored){}
            }
        });
    }
    @Override public boolean isPlaceable(){return finished ? supportsEnv(Vars.state.rules.env) : super.isPlaceable();}
    @Override public boolean canReplace(Block other){return finished ? other==platform : super.canReplace(other);}
    @Override public boolean canPlaceOn(Tile tile,Team team,int rotation){
        return !finished || (tile.build instanceof UnityBuild && tile.build.block==platform
            && tile.team()==team && ((UnityBuild)tile.build).pending!=null);
    }
    @Override public void placeBegan(Tile tile,Block previous,Unit builder){
        super.placeBegan(tile,previous,builder);
        if(!finished || Vars.net.client() || !(tile.build instanceof ConstructBlock.ConstructBuild))return;
        ConstructBlock.ConstructBuild site=(ConstructBlock.ConstructBuild)tile.build;
        if(site.prevBuild!=null)for(Building old:site.prevBuild)if(old instanceof UnityBuild && old.block==platform){
            UnityBlueprint recipe=((UnityBuild)old).pending;
            if(recipe!=null){
                UnityBuild oldPlatform=(UnityBuild)old;oldPlatform.refundLegacyPayment();
                construction.replace(site,recipe,false);
                ((UnityConstruction.Site)tile.build).progress=oldPlatform.progress;
            }
            break;
        }
    }
    @Override public void load(){super.load();if(finished)head=Core.atlas.find(name+"-top");}
    @Override public void setBars(){
        super.setBars();
        addBar("fusion",(UnityBuild b)->new Bar(
            ()->finished ? b.blueprint.name+" · "+b.blueprint.modules.size+" 个模块" : b.pending==null?"团结 · 空平台":"已加入原生建造队列",
            ()->Pal.accent,()->b.pending==null?1f:b.progress));
        addBar("fusion-heat",(UnityBuild b)->new Bar(
            ()->"热量 "+arc.util.Strings.autoFixed(b.fusionHeat,1)+" / "+arc.util.Strings.autoFixed(b.heatRequirement(),1),
            ()->Pal.lightOrange,()->b.heatRequirement()>0?Mathf.clamp(b.fusionHeat/b.heatRequirement()):0f));
        addBar("fusion-constructor",(UnityBuild b)->new Bar(
            ()->"构能 "+(int)(b.constructorSatisfaction*100f)+"%",
            ()->Pal.accent,()->b.constructorUse()>0?Mathf.clamp(b.constructorSatisfaction):1f));
    }

    public class UnityBuild extends Building implements ConstructorConsumer,HeatConsumer{
        public final float[] fusionSideHeat=new float[4];
        public float fusionHeat;
        @Override public float[] sideHeat(){return fusionSideHeat;}
        @Override public float heatRequirement(){
            float total=0f;
            for(Mount m:mounts)total+=m.heatDemand();
            return total;
        }
        public float constructorSatisfaction(){return constructorSatisfaction;}
        public UnityBlueprint blueprint=new UnityBlueprint(),pending;
        public float progress;
        @Override public String getDisplayName(){return finished && !blueprint.modules.isEmpty()?blueprint.name:UnityTurret.this.localizedName;}
        @Override public void configured(Unit builder,Object value){
            if(finished){
                if(blueprint.modules.isEmpty() && value instanceof String && ((String)value).startsWith("install:")){
                    try{install(UnityBlueprint.decode(((String)value).substring(8)));}catch(Exception ignored){}
                }
                return;
            }
            super.configured(builder,value);
            if(pending!=null && value instanceof String && ((String)value).startsWith("build:"))assignBuilder(builder);
        }
        public void assignBuilder(Unit builder){
            if(!finished && pending!=null && builder!=null && builder.team==team && builder.canBuild())
                builder.addBuild(new BuildPlan(tile.x,tile.y,rotation,finishedBlock,"install:"+pending.encode()),false);
        }
        @Override public void tapped(){
            if(!finished && !Vars.headless && Vars.player!=null && Vars.player.team()==team)assignBuilder(Vars.player.unit());
        }
        public final Seq<Mount> mounts=new Seq<>();
        private float constructorSatisfaction;
        private ItemStack[] legacyRefund=ItemStack.empty;
        private UnityPalette palette=new UnityPalette("");
        public UnityFusion fusion;
        public float fusionReload, fusionRotation=90f;
        public int totalFusedShots;
        public String fusionStatus="等待目标";
        private float retarget, charge=-1f, beamTime;
        private Teamc fusionTarget;
        private Bullet beam;

        public float powerUse(){
            float total=0f;
            for(Mount m:mounts)if(m.source.consPower!=null)total+=m.source.consPower.requestedPower(m.build);
            return total;
        }
        public boolean affordable(UnityBlueprint recipe){
            return Vars.state.rules.infiniteResources || (core()!=null && core().items.has(recipe.cost()));
        }
        public void begin(UnityBlueprint recipe){
            if(finished || recipe.modules.isEmpty() || !recipe.researched())return;
            pending=recipe;progress=0;
        }
        public void install(UnityBlueprint recipe){
            for(Mount m:mounts)m.dispose();
            mounts.clear();blueprint=recipe;palette=new UnityPalette(recipe.color);
            if(tile!=null){if(recipe.color.isEmpty())coloredPositions.remove(pos());else coloredPositions.add(pos());}
            for(UnityBlueprint.Module module:recipe.modules)mounts.add(new Mount(module));
            fusion=new UnityFusion(recipe);totalFusedShots=0;fusionReload=0f;charge=-1f;fusionTarget=null;
            beam=null;beamTime=0;
        }
        private void refundLegacyPayment(){
            if(!Vars.net.client() && legacyRefund.length>0 && core()!=null){for(ItemStack stack:legacyRefund)core().items.add(stack.item,stack.amount);legacyRefund=ItemStack.empty;}
        }
        private void migrateLegacyTurret(){
            try{
                java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
                write(Writes.get(new java.io.DataOutputStream(bytes)));
                mindustry.world.modules.ItemModule inventory=items;
                mindustry.world.modules.LiquidModule fluids=liquids;
                float healthFraction=healthf();int facing=rotation;
                Tile location=tile;
                Call.setTile(location,finishedBlock,team,facing);
                UnityBuild turret=(UnityBuild)location.build;
                turret.read(Reads.get(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()))),version());
                turret.pending=null;turret.progress=0;turret.items=inventory;turret.liquids=fluids;
                turret.health=turret.maxHealth*healthFraction;
                if(Vars.net.server())Call.clientPacketReliable("cryon-unity-migrated",location.pos()+"|"+turret.blueprint.encode());
            }catch(Exception e){throw new IllegalStateException("Cannot migrate legacy Unity turret",e);}
        }
        @Override public void updateTile(){
            refundLegacyPayment();
            if(!finished && blueprint.modules.any() && !Vars.net.client()){migrateLegacyTurret();return;}
            if(mounts.size!=blueprint.modules.size)install(blueprint);
            if(mounts.any())updateFusion();
        }
        private void updateFusion(){
            Turret primary=mounts.first().source;
            fusionHeat=heatRequirement()>0f?calculateHeat(fusionSideHeat):0f;
            retarget-=Time.delta;
            if(retarget<=0f || fusionTarget==null || Units.invalidateTarget(fusionTarget,team,x,y,fusion.range)){
                retarget=15f;int mode=blueprint.targeting;
                Units.Sortf sort=mode==2?(u,tx,ty)->-u.maxHealth:mode==3?(u,tx,ty)->u.health:mode==0?primary.unitSort:(u,tx,ty)->u.dst2(tx,ty);
                fusionTarget=Units.bestTarget(team,x,y,fusion.range,
                    u->mode!=5 && u.checkTarget(fusion.targetAir,fusion.targetGround) && (mode!=4 || u.isFlying()),
                    b->mode!=4 && fusion.targetGround,sort);
            }
            boolean tracking=enabled && fusionTarget!=null;
            if(tracking)fusionRotation=arc.math.Angles.moveToward(fusionRotation,angleTo(fusionTarget),fusion.rotateSpeed*delta());
            boolean shooting=tracking && arc.math.Angles.angleDist(fusionRotation,angleTo(fusionTarget))<=Math.max(1f,primary.shootCone);
            float efficiency=Float.MAX_VALUE,heatBoost=0f;
            for(Mount m:mounts){
                m.prepare(fusionTarget,shooting);
                if(!m.build.hasAmmo() || !m.build.canConsume())efficiency=0f;
                else efficiency=Math.min(efficiency,m.build.efficiency);
                if(m.build instanceof HeatBoostTurretBuild)heatBoost+=((HeatBoostTurretBuild)m.build).fusionHeatBoost();
            }
            efficiency*=1f+heatBoost/mounts.size;
            if(constructorUse()>0f)efficiency*=Mathf.clamp(constructorSatisfaction);
            fusionStatus=!enabled?"已禁用":!tracking?"等待可攻击目标":!shooting?"正在瞄准":efficiency<=0f?"供给不足或效率为零":beam!=null?"持续攻击中":fusionReload<fusion.reload?"正在装填":"准备发射";
            float cooling=0;
            if(beam==null && fusionReload<fusion.reload && efficiency>0)for(Mount m:mounts){
                if(m.source.coolant!=null && m.source.coolant.efficiency(m.build)>0f){
                    float heat=m.build.liquids==null?0f:m.build.liquids.current().heatCapacity;
                    cooling+=m.source.coolant.amount*m.source.coolant.efficiency(m.build)*heat*m.source.coolantMultiplier;
                    m.source.coolant.update(m.build);
                }
            }
            if(beam==null)fusionReload=Math.min(fusion.reload,fusionReload+delta()*efficiency*(1f+cooling/mounts.size));
            if(beam!=null){
                if(beam.isAdded() && beamTime>0f && shooting && efficiency>0f){
                    beam.set(x,y);beam.rotation(fusionRotation);beam.time=beam.lifetime*0.5f;beam.keepAlive=true;beamTime-=delta();
                }else{beam=null;beamTime=0;}
            }
            if(shooting && efficiency>0f && fusionReload>=fusion.reload && beam==null){
                if(charge<0f && primary.shoot.firstShotDelay>0f){charge=primary.shoot.firstShotDelay;fusion.bullet.chargeEffect.at(x,y,fusionRotation,fusion.bullet.hitColor);}
                if(charge>0f){
                    charge=Math.max(0f,charge-delta()*efficiency);
                    if(charge>0f){fusionStatus="正在蓄力 · "+arc.util.Strings.autoFixed(charge/60f,1)+" 秒";return;}
                }
                float lifeScale=fusion.bullet.scaleLife?Mathf.clamp(dst(fusionTarget)/fusion.range,0.05f,1f):1f;
                Bullet fired=fusion.bullet.create(this,team,x,y,fusionRotation,-1f,1f,lifeScale,null,null,fusionTarget.x(),fusionTarget.y());
                for(Mount m:mounts)m.build.useAmmo();
                totalFusedShots++;fusionReload=0;charge=-1;
                fusion.bullet.shootEffect.at(x,y,fusionRotation,fusion.bullet.hitColor);
                primary.shootSound.at(x,y,1f,primary.shootSoundVolume);
                if(((UnityHybridBullet)fusion.bullet).carrier instanceof mindustry.entities.bullet.ContinuousBulletType){
                    beam=fired;beamTime=primary instanceof LaserTurret?((LaserTurret)primary).shootDuration:60f;
                }
            }else if(!shooting || efficiency<=0f)charge=-1f;
        }
        @Override public boolean acceptItem(Building source,Item item){
            if(items.get(item)>=itemCapacity)return false;
            for(Mount m:mounts)if(m.accepts(item))return true;
            return false;
        }
        @Override public boolean acceptLiquid(Building source,Liquid liquid){
            if(liquids.get(liquid)>=liquidCapacity)return false;
            for(Mount m:mounts)if(m.accepts(liquid))return true;
            return false;
        }
        @Override public boolean shouldConsume(){return enabled && mounts.any();}
        @Override public float constructorUse(){
            float sum=0;for(Mount m:mounts)if(m.build instanceof ConstructorConsumer)sum+=((ConstructorConsumer)m.build).constructorUse();
            return sum;
        }
        @Override public void constructorStatus(float status){constructorSatisfaction=status;}
        @Override public boolean constructorValid(){return enabled && !dead;}
        @Override public void buildConfiguration(Table table){
            if(!finished)table.button("编辑 / 炮塔库",Icon.pencil,()->new UnityEditor(this).show()).size(220f,60f);
        }
        @Override public void draw(){
            Draw.rect(finished?platform.region:region,x,y);
            if(finished && head!=null)Draw.rect(head,x,y,mounts.isEmpty()?0f:fusionRotation-90f);
        }
        @Override public void displayConsumption(Table table){
            if(finished)UnityConsumption.display(table,blueprint,this);
        }
        @Override public float fogRadius(){
            float range=0f;
            if(fusion!=null)range=fusion.range/Vars.tilesize;
            return range;
        }
        @Override public void drawSelect(){
            float range=fusion==null?0f:fusion.range;
            if(range>0)Drawf.dashCircle(x,y,range,team.color);
        }
        @Override public void onRemoved(){
            for(Mount m:mounts)m.dispose();
            coloredPositions.remove(pos());
            super.onRemoved();
        }
        @Override public byte version(){return 3;}
        @Override public void write(Writes w){
            w.str(blueprint.encode());w.bool(pending!=null);
            if(pending!=null)w.str(pending.encode());w.f(progress);
            w.i(mounts.size);
            for(Mount m:mounts){
                w.b(m.build.version());m.build.write(w);
                w.bool(m.build.items!=null);if(m.build.items!=null)m.build.items.write(w);
                w.bool(m.build.liquids!=null);if(m.build.liquids!=null)m.build.liquids.write(w);
            }
            w.i(legacyRefund.length);for(ItemStack stack:legacyRefund){w.str(stack.item.name);w.i(stack.amount);}
            w.f(fusionReload);w.f(fusionRotation);w.i(totalFusedShots);
        }
        @Override public void read(Reads r,byte revision){
            UnityBlueprint saved=UnityBlueprint.decodeStored(r.str());
            pending=r.bool()?UnityBlueprint.decodeStored(r.str()):null;progress=Mathf.clamp(r.f());
            install(saved);
            int count=r.i();if(count!=mounts.size)throw new IllegalArgumentException("Unity module save mismatch");
            for(Mount m:mounts){
                m.build.read(r,r.b());
                if(r.bool())m.build.items.read(r);
                if(r.bool())m.build.liquids.read(r);
            }
            if(revision>=2){
                int n=r.i();legacyRefund=new ItemStack[n];
                for(int i=0;i<n;i++)legacyRefund[i]=new ItemStack(Vars.content.item(r.str()),r.i());
            }else if(pending!=null && !Vars.state.rules.infiniteResources)legacyRefund=pending.cost();
            if(revision>=3){fusionReload=r.f();fusionRotation=r.f();totalFusedShots=r.i();}
        }

        public class Mount{
            public final UnityBlueprint.Module spec;
            public final Turret source;
            public final Turret.TurretBuild build;
            public final UnityPalette palette;
            private final Seq<Item> supplyItems=new Seq<>();
            private final Seq<Liquid> supplyLiquids=new Seq<>();
            public Mount(UnityBlueprint.Module spec){
                this.spec=spec;source=spec.source();palette=UnityBuild.this.palette;
                build=(Turret.TurretBuild)source.buildType.get();
                build.create(source,team);
                // These builds never enter entity groups, tile ownership, or power graphs.
                build.tile=tile;build.set(x,y);build.power=power;build.rotation=90f;
                for(Item item:Vars.content.items())if(accepts(item))supplyItems.add(item);
                for(Liquid liquid:Vars.content.liquids())if(accepts(liquid))supplyLiquids.add(liquid);

            }
            public boolean accepts(Item i){
                if(source instanceof ItemTurret && ((ItemTurret)source).ammoTypes.containsKey(i))return spec.ammo.equals(i.name);
                return source.hasItems && source.consumesItem(i);
            }
            public float heatDemand(){
                return build instanceof HeatBoostTurretBuild?((HeatBoostTurretBuild)build).heatRequirement():Math.max(0f,source.heatRequirement);
            }
            public boolean accepts(Liquid l){
                if(source instanceof LiquidTurret && ((LiquidTurret)source).ammoTypes.containsKey(l))return spec.ammo.equals(l.name);
                if(source instanceof ContinuousLiquidTurret && ((ContinuousLiquidTurret)source).ammoTypes.containsKey(l))return spec.ammo.equals(l.name);
                return source.hasLiquids && source.consumesLiquid(l);
            }
            private void supply(){
                for(Item item:supplyItems)if(items.get(item)>0){
                    if(source instanceof ItemTurret && spec.ammo.equals(item.name)){
                        if(build.acceptItem(UnityBuild.this,item)){build.handleItem(UnityBuild.this,item);items.remove(item,1);}
                    }else if(build.items!=null){
                        int n=Math.min(items.get(item),Math.max(0,source.itemCapacity-build.items.get(item)));
                        if(n>0){items.remove(item,n);build.items.add(item,n);}
                    }
                }
                if(build.liquids!=null)for(Liquid liquid:supplyLiquids){
                    float n=Math.min(liquids.get(liquid),Math.max(0f,source.liquidCapacity-build.liquids.get(liquid)));
                    if(n>0f){liquids.remove(liquid,n);build.handleLiquid(UnityBuild.this,liquid,n);}
                }
            }
            public void prepare(Teamc target,boolean shooting){
                build.team=team;build.tile=tile;build.set(x,y);build.power=power;build.enabled=enabled;
                build.applyBoost(timeScale,60f);build.applySlowdown(timeScale,60f);
                if(build instanceof ConstructorConsumer)((ConstructorConsumer)build).constructorStatus(constructorSatisfaction);
                float totalHeat=heatRequirement();
                float allocated=totalHeat>0f?fusionHeat*heatDemand()/totalHeat:0f;
                build.heatReq=allocated;
                if(build instanceof HeatBoostTurretBuild)((HeatBoostTurretBuild)build).boostHeat=allocated;
                supply();
                if(source instanceof ContinuousLiquidTurret){
                    // Only update its liquid activation latch; independent firing remains disabled.
                    build.logicControlTime=60f;build.logicShooting=false;build.target=null;build.updateTile();
                }
                build.target=target instanceof Posc?(Posc)target:null;
                build.logicControlTime=60f;build.logicShooting=shooting;build.wasShooting=shooting;
                if(shootingField!=null)try{shootingField.setBoolean(build,shooting);}catch(IllegalAccessException e){throw new IllegalStateException(e);}
                build.rotation=fusionRotation;
                if(target!=null)build.targetPos.set(target.x(),target.y());
                build.updateConsumption();
            }
            public void dispose(){
                build.dead=true;
                if(build.soundLoop!=null)build.soundLoop.stop();
            }
        }
    }
}
