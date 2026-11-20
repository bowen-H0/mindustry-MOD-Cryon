import arc.*;
import arc.files.Fi;
import arc.scene.Element;
import arc.scene.ui.Dialog;
import arc.scene.ui.layout.Table;
import arc.graphics.g2d.Draw;
import arc.math.Mat;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.type.UnitType;
import mindustry.entities.abilities.MoveEffectAbility;
import mindustry.entities.units.AIController;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Tile;
import mindustry.world.blocks.units.UnitFactory;

/** Actual atlas packing, unit/tread/rotor rendering, headquarters and production UI. */
public class AravisRtsRenderCheck{
    static Fi data;
    static boolean running;
    static Unit movingBoat,movingTank;
    static boolean waterWake,landWake;
    static float started;
    static final String[] names={"violet-falcon","ash-kite","needlewing","shorekeeper","field-artillery","hover-tank","light-tank","flak-car"};
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void guarded(Runnable action){try{action.run();}catch(Throwable error){Log.err(error);System.exit(1);}}
    static UnitType unit(String name){return Vars.content.unit("cryon-aravis-"+name);}
    static void screenshot(String name){Core.scene.act();Core.scene.draw();ScreenUtils.saveScreenshot(data.child(name));}
    static void test() throws Exception{
        check(!Vars.mods.locateMod("cryon").hasContentErrors(),"Packaged content errors");
        var aravis=Vars.content.planet("cryon-aravis");
        Vars.logic.reset();Vars.state.rules=new Rules();Vars.state.rules.canGameOver=false;
        Vars.state.rules.unitCap=100;Vars.state.rules.waves=false;Vars.state.rules.lighting=false;
        Vars.world.loadGenerator(100,90,tiles->{
            for(int x=0;x<100;x++)for(int y=0;y<90;y++)tiles.set(x,y,new Tile(x,y,
                x>=60&&x<=78&&y>=30&&y<=45?Blocks.deepwater:Blocks.stone,Blocks.air,Blocks.air));
        });
        for(int i=0;i<names.length;i++){
            UnitType type=unit(names[i]);
            check(type.region.found()&&type.uiIcon.found()&&type.fullIcon.found(),"Missing unit art: "+type.name);
            check(type.techNode!=null&&type.shownPlanets.contains(aravis),"Unit missing from Aravis research: "+type.name);
            for(var weapon:type.weapons)check(weapon.region.found(),"Missing weapon art: "+weapon.name);
            if(type.constructor.get() instanceof Tankc){
                check(type.treadRegions!=null&&type.treadRegions.length==1,"Missing tank treads");
                for(var frame:type.treadRegions[0])check(frame.found(),"Missing tread animation frame");
            }
            Unit unit=type.spawn(Team.sharded,300+(i%4)*56,440-(i/4)*100);
            unit.rotation=90f;unit.apply(StatusEffects.unmoving,100000f);unit.apply(StatusEffects.disarmed,100000f);
            for(var mount:unit.mounts)mount.rotation=0f;
        }
        check(Core.atlas.has("cryon-aravis-ash-kite-rotor"),"Missing helicopter rotor");
        Unit boat=unit("shorekeeper").spawn(Team.sharded,510,300);
        movingBoat=boat;
        boat.rotation=0f;boat.controller(new AIController(){@Override public void updateMovement(){}});
        ((Payloadc)boat).pickup(unit("light-tank").create(Team.sharded));
        movingTank=unit("hover-tank").spawn(Team.sharded,300,270);
        movingTank.rotation=0f;movingTank.apply(StatusEffects.disarmed,100000f);
        movingTank.controller(new AIController(){@Override public void updateMovement(){}});
        for(String branch:new String[]{"army","air"}){
            UnitFactory factory=(UnitFactory)Vars.content.block("cryon-aravis-"+branch+"-headquarters");
            check(factory.region.found()&&factory.outRegion.found()&&factory.topRegion.found()&&factory.fullIcon.found(),"Missing headquarters art");
            check(factory.techNode!=null&&factory.shownPlanets.contains(aravis),"Wrong headquarters planet");
            var root=factory.techNode;while(root.parent!=null)root=root.parent;
            check(root.planet==aravis,"Headquarters outside Aravis research tree");
            Vars.world.tile(branch.equals("army")?38:51,27).setBlock(factory,Team.sharded);
            UnitFactory.UnitFactoryBuild build=(UnitFactory.UnitFactoryBuild)Vars.world.build((branch.equals("army")?38:51),27);
            build.currentPlan=0;build.power.status=1f;
        }
        Vars.player.team(Team.sharded);Vars.player.unit(UnitTypes.alpha.spawn(Team.sharded,370,300));
        Vars.logic.play();Vars.ui.hudfrag.shown=false;started=Time.time;running=true;
        Timer.schedule(()->guarded(()->{
            check(waterWake&&landWake,"Moving hover units did not emit distinct water/land wakes");
            Mat projection=new Mat(Draw.proj());
            Draw.proj(Core.camera);
            try{
                for(Unit hover:new Unit[]{movingBoat,movingTank}){
                    float x=hover.x,y=hover.y,elevation=hover.elevation;
                    Mat transform=new Mat(Draw.trans());
                    hover.type.draw(hover);
                    check(hover.x==x&&hover.y==y&&hover.elevation==elevation,"Visual hover changes gameplay position/elevation");
                    check(java.util.Arrays.equals(transform.val,Draw.trans().val),"Hover draw leaks the batch transform");
                }
            }finally{
                Draw.flush();
                Draw.proj(projection);
            }
            screenshot("aravis-rts-world.png");
            BaseDialog catalog=new BaseDialog("阿拉维斯 · 新增部队");
            int index=0;
            for(String name:names){
                UnitType type=unit(name);
                catalog.cont.table(card->{
                    card.image(type.fullIcon).size(76f).row();
                    card.add(type.localizedName).padTop(5f);
                }).size(180f,120f).pad(6f);
                if(++index%4==0)catalog.cont.row();
            }
            catalog.cont.row();
            catalog.cont.table(row->{
                for(String branch:new String[]{"army","air"}){
                    UnitFactory factory=(UnitFactory)Vars.content.block("cryon-aravis-"+branch+"-headquarters");
                    row.table(card->{card.image(factory.fullIcon).size(94f).row();card.add(factory.localizedName+" · "+factory.plans.size);}).pad(20f);
                }
            }).colspan(4);
            catalog.addCloseButton();catalog.show();catalog.clearActions();catalog.color.a=1f;
            Timer.schedule(()->guarded(()->{
                screenshot("aravis-rts-catalog.png");
                Log.info("ARAVIS_RTS_RENDER_PASS: 8 bodies/icons/weapons, treads/rotor, idle hover jets, moving land/water wakes, render-only lift and restored transforms, payload transport, headquarters and research");
                Core.app.exit();
            }),1f);
        }),3f);
    }
    public static void main(String[] args){
        data=Fi.get(System.getProperty("mindustry.data.dir"));Fi repo=Fi.get(System.getProperty("cryon.repo","."));
        repo.child("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        repo.child("单位发射台library.jar").copyTo(data.child("mods/unitlaunch.jar"));
        Events.on(ClientLoadEvent.class,event->Timer.schedule(()->guarded(()->{
            for(Element element:Core.scene.root.getChildren().copy())if(element instanceof Dialog dialog){dialog.clearActions();dialog.hide(null);}
            Core.graphics.setFullscreen(false);
            arc.backend.sdl.jni.SDL.SDL_RestoreWindow(((arc.backend.sdl.SdlApplication)Core.app).getWindow());
            Core.graphics.setWindowSize(1200,850);
            Timer.schedule(()->guarded(()->{try{test();}catch(Exception error){throw new RuntimeException(error);}}),1f);
        }),3f));
        Events.run(Trigger.update,()->{
            if(!running)return;
            Core.camera.position.set(400,360);Vars.player.set(400,360);Vars.renderer.setScale(3f);
            movingBoat.vel.set(0.32f,0f);movingTank.vel.set(0.32f,0f);
            if(Time.time-started>30f){
                for(var draw:Groups.draw)if(draw instanceof EffectState effect){
                    for(var ability:movingBoat.abilities)if(ability instanceof MoveEffectAbility jet&&effect.effect==jet.effect)waterWake=true;
                    for(var ability:movingTank.abilities)if(ability instanceof MoveEffectAbility jet&&effect.effect==jet.effect)landWake=true;
                }
            }
        });
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
