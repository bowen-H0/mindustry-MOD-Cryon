import arc.*;
import arc.files.Fi;
import arc.graphics.Color;
import arc.scene.ui.layout.Table;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.world.*;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.blocks.defense.turrets.PointDefenseTurret;
import mindustry.world.consumers.ConsumeItems;

/** Actual desktop GL and configuration smoke test using an isolated data directory. */
public class PorphyraIndustryRenderCheck{
    static boolean ready;
    static Building trade;
    static PointDefenseTurret.PointDefenseBuild laser;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        Fi dir=new Fi(System.getProperty("mindustry.data.dir"));
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(dir.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(dir.child("mods/unitlanuch.jar"));
        Events.on(ClientLoadEvent.class,event->Core.app.post(()->{
            try{
                Core.graphics.setFullscreen(false);Core.graphics.setWindowSize(1300,820);
                for(var element:Core.scene.root.getChildren().copy())if(element instanceof arc.scene.ui.Dialog dialog)dialog.hide();
                Vars.logic.reset();Rules rules=new Rules();rules.waves=rules.waveTimer=rules.canGameOver=false;Vars.state.rules=rules;
                Vars.world.loadGenerator(120,80,tiles->{for(int x=0;x<120;x++)for(int y=0;y<80;y++)
                    tiles.set(x,y,new Tile(x,y,Blocks.basalt,Blocks.air,Blocks.air));});
                String[] names={"protocol-encoder","trade-tower","rhenium-press","crystal-stabilizer","alloy-forge","plasma-condenser","laser-defense"};
                for(int i=0;i<names.length;i++){
                    Block block=Vars.content.block("cryon-porphyra-"+names[i]);
                    check(block.region!=null&&block.region.found()&&block.uiIcon!=null&&block.uiIcon.found(),"Missing sprite: "+block.name);
                    int x=25+i*11,y=34;
                    Vars.world.tile(x,y).setBlock(block,Team.sharded);
                    Building build=Vars.world.tile(x,y).build;
                    Vars.world.tile(x,y-(block.size+1)/2).setBlock(Blocks.powerSource,Team.sharded);
                    if(build instanceof GenericCrafter.GenericCrafterBuild){
                        ConsumeItems inputs=block.findConsumer(c->c instanceof ConsumeItems);
                        for(var input:inputs.items)build.items.add(input.item,input.amount*3);
                        if(names[i].equals("alloy-forge"))build.liquids.add(Vars.content.liquid("cryon-porphyra-plasma"),45);
                    }
                    if(names[i].equals("trade-tower")){
                        trade=build;trade.items.add(Vars.content.item("cryon-protocol-point"),24);
                        // Verify the native network configuration path, then show the actual order buttons.
                        trade.configureAny(1);
                        check(((Integer)trade.config())==1,"Trade config did not update through native engine handler");
                        Table menu=new Table();menu.setFillParent(true);menu.top().left().margin(80f);
                        menu.table(t->{t.setBackground(mindustry.gen.Tex.pane);trade.buildConfiguration(t);});
                        Core.scene.add(menu);
                    }
                    if(build instanceof PointDefenseTurret.PointDefenseBuild pd)laser=pd;
                }
                Timer.schedule(()->{
                    if(laser==null)return;
                    UnitTypes.dagger.weapons.first().bullet.create((Entityc)null,Team.crux,laser.x+115f,laser.y+25f,180f);
                },1f,0.4f);
                Vars.player.team(Team.sharded);Vars.logic.play();ready=true;
                Timer.schedule(()->{
                    try{
                        check(trade.items.get(Items.graphite)>=10,"Powered trade did not produce graphite during live simulation");
                        check(laser.efficiency>0 || laser.target==null,"Laser defense is unpowered");
                        ScreenUtils.saveScreenshot(dir.child("industry.png"));
                        Log.info("PORPHYRA_INDUSTRY_RENDER_PASS: seven valid sprites, native order configuration and live powered graphite transaction");
                        Core.app.exit();
                    }catch(Throwable error){Log.err(error);System.exit(1);}
                },7f);
            }catch(Throwable error){Log.err(error);System.exit(1);}
        }));
        Events.run(Trigger.update,()->{if(ready){Core.camera.position.set(450,280);Vars.renderer.setScale(2.3f);}});
        mindustry.desktop.DesktopLauncher.main(args);
    }
}
