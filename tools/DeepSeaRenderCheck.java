import arc.*;
import arc.files.Fi;
import arc.struct.Seq;
import arc.util.*;
import mindustry.*;
import mindustry.ai.UnitStance;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;

/** An isolated visual test scene, never written into the mod's campaign maps. */
public class DeepSeaRenderCheck{
    static boolean ready, overview=true;
    static Unit[] ships=new Unit[5];
    static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
    static void guarded(Runnable r){try{r.run();}catch(Throwable e){Log.err(e);System.exit(1);}}
    public static void main(String[] args){
        Fi data=new Fi(System.getProperty("mindustry.data.dir"));Fi repo=new Fi(System.getProperty("cryon.repo","."));
        repo.child("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        repo.child("单位发射台library.jar").copyTo(data.child("mods/unitlanuch.jar"));
        Events.on(ClientLoadEvent.class,event->Timer.schedule(()->guarded(()->{
            for(var el:Core.scene.root.getChildren().copy())if(el instanceof arc.scene.ui.Dialog d){d.clearActions();d.hide(null);}
            check(!Vars.mods.locateMod("cryon").hasContentErrors(),"Mod has content load errors");
            var deep=Vars.content.planet("cryon-cryon-depths");Vars.logic.reset();Vars.state.rules=new Rules();
            Vars.state.rules.sector=deep.getStartSector();Vars.state.rules.planet=deep;
            Vars.state.rules.tags.put("cryon-tantros-seabed-v1","true");
            Vars.world.loadGenerator(160,110,tiles->{
                for(int x=0;x<160;x++)for(int y=0;y<110;y++)tiles.set(x,y,new Tile(x,y,Vars.content.block("cryon-abyss-blue-bed"),Blocks.air,Blocks.air));
                String[] floors={"pillow-basalt","pressure-slate","obsidian","limestone","black-silt","white-clay","frozen-silt","silica-crust","ice-crystal-floor","titanium-bed","nickel-bed","metal-nodules"};
                for(int i=0;i<floors.length;i++)for(int x=0;x<4;x++)for(int y=0;y<4;y++)tiles.get(43+i*5+x,35+y).setFloor(Vars.content.block("cryon-abyss-"+floors[i]).asFloor());
                for(int x=8;x<22;x++)for(int y=14;y<28;y++)tiles.get(x,y).setFloor(Vars.content.block("cryon-abyss-pillow-basalt").asFloor());
            });
            deep.ruleSetter.get(Vars.state.rules);
            Vars.state.rules.waves=Vars.state.rules.waveTimer=Vars.state.rules.canGameOver=false;Vars.state.rules.unitCap=200;
            Vars.world.tile(15,20).setBlock(deep.defaultCore,Team.sharded);
            Vars.world.tile(98,53).setBlock(Vars.content.block("cryon-powered-pump"),Team.sharded);
            Vars.world.tile(101,49).setBlock(Vars.content.block("cryon-abyssal-mine"),Team.sharded);
            String[] plants={"glass-kelp","ribbon-kelp","lantern-weed","frost-fan","glow-anemone","lantern-coral","tube-sponge","ice-fern"};
            for(int i=0;i<plants.length;i++)for(int j=0;j<5;j++)Vars.world.tile(44+i*7+j,65+(j%2)*3).setBlock(Vars.content.block("cryon-abyss-"+plants[i]));
            String[] names={"minimus","danae","pammelas","schmidti","sloani"};
            UnitStance dive=Vars.content.unitStances().find(s->s.name.equals("cryon-deep-dive"));
            for(int i=0;i<5;i++){
                UnitType type=Vars.content.unit("cryon-"+names[i]);type.unlock();
                check(type.region.found()&&type.cellRegion.found(),"Missing submarine hull/cell: "+names[i]);
                check(type.techNode!=null,"Missing submarine research");
                ships[i]=type.create(Team.sharded);ships[i].set(380+i*105,450);ships[i].rotation=90;ships[i].add();
                if(i==2||i==3)ships[i].command().setStance(dive);
                Seq<UnitStance> stances=new Seq<>();type.getUnitStances(ships[i],stances);check(stances.contains(dive),"Dive toggle absent from command UI");
            }
            for(var block:Vars.content.blocks())if(block.name.startsWith("cryon-abyss-")&&block.minfo.sourceFile!=null&&block.inEditor){
                if(block.variants>0)for(var r:block.variantRegions)check(r.found(),"Missing terrain variant: "+block.name);
                else check(block.region.found(),"Missing terrain sprite: "+block.name);
            }
            check(Vars.content.block("cryon-powered-pump").region.found(),"Pump texture absent");
            check(Vars.content.block("cryon-abyssal-mine").region.found(),"Mine texture absent");
            Vars.player.team(Team.sharded);Vars.logic.play();ready=true;
            Timer.schedule(()->guarded(()->{
                check(ships[2].inFogTo(Team.crux)&&!ships[2].inFogTo(Team.sharded),"Rendered submarine concealment is wrong");
                ScreenUtils.saveScreenshot(data.child("submarines-and-terrain.png"));
                overview=false;Vars.ui.hudfrag.shown=true;
                Vars.control.input.commandMode=true;Vars.control.input.selectedUnits.clear();Vars.control.input.selectedUnits.add(ships[2]);
                Timer.schedule(()->guarded(()->{
                    ScreenUtils.saveScreenshot(data.child("dive-command.png"));
                    Vars.control.input.commandMode=false;Vars.control.input.selectedUnits.clear();
                    try{mindustry.io.SaveIO.write(data.child("submarine-roundtrip.msav"));Vars.logic.reset();mindustry.io.SaveIO.load(data.child("submarine-roundtrip.msav"));}catch(Exception e){throw new RuntimeException(e);}
                    Vars.logic.play();
                    Timer.schedule(()->guarded(()->{
                        Unit loaded=Groups.unit.find(u->u.type.name.equals("cryon-pammelas"));
                        check(loaded!=null&&loaded.getClass().getName().equals("cryon.Type.SubmarineUnit"),"Save loaded a generic naval entity");
                        check(loaded.inFogTo(Team.crux)&&!loaded.inFogTo(Team.sharded),"Saved diving submarine lost concealment");
                        ScreenUtils.saveScreenshot(data.child("saved-submarines.png"));
                        Log.info("DEEPSEA_RENDER_PASS: five hulls, dedicated terrain/plant sprites, command dive toggle, pump/mine textures and custom submarine world-save roundtrip");
                        Core.app.exit();
                    }),3f);
                }),2f);
            }),4f);
        }),2f));
        Events.run(Trigger.update,()->{if(ready){Core.camera.position.set(590,440);Vars.renderer.setScale(0.85f);if(overview){Vars.ui.hudfrag.shown=false;Vars.ui.hudGroup.visible=false;}}});
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
