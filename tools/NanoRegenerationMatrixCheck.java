import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.*;
import java.nio.file.Files;
import mindustry.*;
import mindustry.content.Blocks;
import mindustry.core.Logic;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.io.SaveIO;
import mindustry.net.Net;
import mindustry.world.*;
import mindustry.world.meta.BuildVisibility;

/** Real packaged matrix: fuel accounting, native power, allied healing and persistence. */
public final class NanoRegenerationMatrixCheck{
    static void check(boolean ok, String message){ if(!ok) throw new AssertionError(message); }
    public static void main(String[] args){
        try{ run(); System.exit(0); }
        catch(Throwable error){ error.printStackTrace(); System.exit(1); }
    }
    static void run() throws Exception{
        Core.app = new MockApplication(); Core.files = new MockFiles(); Core.graphics = new MockGraphics(); Core.audio = new MockAudio();
        Core.settings = new Settings(); Fi data = new Fi(Files.createTempDirectory("cryon-orbital-check-").toFile());
        Core.settings.setDataDirectory(data); Core.bundle = I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(data.child("mods/cryon.jar"));
        new Fi("单位发射台library.jar").copyTo(data.child("mods/unitlanuch.jar"));
        Vars.headless = true; Vars.loadLocales = false; Vars.net = new Net(null); Vars.platform = new mindustry.core.Platform(){};
        mindustry.core.Version.init(); Vars.init(); Core.settings.setJson(mindustry.io.JsonIO.json);
        Vars.content.createBaseContent(); Vars.content.createModContent(); Vars.content.init();
        var mod = Vars.mods.locateMod("cryon"); check(mod != null && !mod.hasContentErrors(), "packaged content errors");
        mod.main.getClass().getClassLoader().loadClass("cryon.Features.MapAlliances").getMethod("install").invoke(null);
        Block node=Vars.content.block("cryon-nano-regeneration-matrix");
        check(node!=null && node.size==7 && node.buildVisibility==BuildVisibility.shown,"matrix content missing");
        var nano=Vars.content.item("cryon-nano-material");
        Vars.logic=new Logic(); Vars.logic.reset();
        Vars.world.loadGenerator(200,200,tiles->{
            for(int x=0;x<200;x++) for(int y=0;y<200;y++) tiles.set(x,y,new Tile(x,y,Blocks.stone,Blocks.air,Blocks.air));
            tiles.get(20,20).setBlock(Blocks.coreShard,Team.sharded);
            tiles.get(90,90).setBlock(node,Team.sharded);
            tiles.get(80,90).setBlock(Vars.content.block("cryon-orbital-power-node"),Team.sharded);
            tiles.get(100,90).setBlock(Blocks.thoriumWallLarge,Team.sharded);
            tiles.get(105,90).setBlock(Blocks.thoriumWallLarge,Team.green);
            tiles.get(110,90).setBlock(Blocks.thoriumWallLarge,Team.crux);
            tiles.get(100,100).setBlock(Blocks.mendProjector,Team.sharded);
            tiles.get(160,90).setBlock(Blocks.thoriumWallLarge,Team.sharded);
        });
        Vars.state.set(mindustry.core.GameState.State.playing); Vars.state.rules.unitCap=100; Time.delta=1f;
        Vars.state.rules.tags.put("cryon-allied-teams",Integer.toString(Team.green.id));
        Building matrix=Vars.world.build(90,90), source=Vars.world.build(80,90);
        Building friend=Vars.world.build(100,90), ally=Vars.world.build(105,90), enemy=Vars.world.build(110,90), suppressed=Vars.world.build(100,100), outside=Vars.world.build(160,90);
        for(Building b:new Building[]{friend,ally,enemy,suppressed,outside}) b.health=b.maxHealth/2f;
        suppressed.applyHealSuppression(600f);
        Unit air=spawn(mindustry.content.UnitTypes.eclipse,Team.sharded,760,780);
        Unit ground=spawn(mindustry.content.UnitTypes.reign,Team.sharded,780,780);
        Unit naval=spawn(mindustry.content.UnitTypes.omura,Team.sharded,800,780);
        Unit allied=spawn(mindustry.content.UnitTypes.eclipse,Team.green,820,780);
        Unit hostile=spawn(mindustry.content.UnitTypes.eclipse,Team.crux,840,780);
        Unit far=spawn(mindustry.content.UnitTypes.eclipse,Team.sharded,1300,780);
        Vars.state.teams.updateTeamStats();
        Groups.unit.tree().clear(); for(Unit u:Groups.unit) Groups.unit.tree().insert(u);
        for(var td:Vars.state.teams.active){ td.tree().clear(); for(Unit u:Groups.unit) if(u.team==td.team) td.tree().insert(u); }
        check(matrix.acceptItem(matrix,nano) && !matrix.acceptItem(matrix,mindustry.content.Items.copper),"input filter");
        source.configureAny(matrix.pos()); matrix.shouldConsumePower=true;
        matrix.items.add(nano,600); source.power.graph.update();
        check(node.consPower.usage==300f && matrix.power.status>0.99f,"native 18000/s supply");
        var previous=(mindustry.world.blocks.defense.MendProjector)Vars.content.block("cryon-nano-projector");
        float previousPercentPerSecond=previous.healPercent/100f*60f/previous.reload;
        check(node.getClass().getField("buildingHealing").getFloat(node)>previousPercentPerSecond*2f,"matrix must repair buildings over twice as fast as the nano projector");
        float friendHp=friend.health, enemyHp=enemy.health, suppressHp=suppressed.health, farHp=outside.health;
        matrix.updateTile();
        check(matrix.items.get(nano)==599 && Math.abs(get(matrix,"fuelTime")-29f)<0.001f,"fuel not paid upfront");
        check(Math.abs(friend.health-friendHp-(1200f+friend.maxHealth*.30f)/60f)<0.01f && ally.health>ally.maxHealth/2f,"building/ally healing rate");
        for(Unit u:new Unit[]{air,ground,naval,allied}) check(u.health>u.maxHealth/2f,"friendly unit not healed: "+u.type.name);
        check(enemy.health==enemyHp && hostile.health==hostile.maxHealth/2f && outside.health==farHp && far.health==far.maxHealth/2f && suppressed.health==suppressHp,"enemy, distant or suppressed target healed");
        for(int i=1;i<60;i++) matrix.updateTile();
        check(matrix.items.get(nano)==598 && get(matrix,"fuelTime")==0f,"not exactly one nano every half second");
        matrix.updateTile(); check(matrix.items.get(nano)==597 && get(matrix,"fuelTime")==29f,"next paid batch missing");
        source.enabled=false; source.power.graph.update();
        float fuel=get(matrix,"fuelTime"); friend.health=friendHp; matrix.updateTile();
        check(friend.health==friendHp && get(matrix,"fuelTime")==fuel,"blackout spent fuel or healed");
        source.enabled=true; source.power.graph.update(); matrix.enabled=false; matrix.updateTile();
        check(friend.health==friendHp && get(matrix,"fuelTime")==fuel,"disabled matrix running"); matrix.enabled=true;
        matrix.applyHealSuppression(600f); matrix.updateTile();
        check(friend.health==friendHp && get(matrix,"fuelTime")==fuel,"suppressed matrix running"); matrix.healSuppressionTime=-1f;
        matrix.items.clear(); set(matrix,"fuelTime",0f); matrix.updateTile();
        check(friend.health==friendHp,"empty matrix healed");
        matrix.items.add(nano,1); Time.delta=90f; matrix.updateTile();
        check(matrix.items.get(nano)==0 && get(matrix,"fuelTime")==0f,"large delta overspent or gave unearned fuel");
        Time.delta=1f; matrix.items.add(nano,60); matrix.updateTile();
        Fi save=data.child("nano.msav"); float paid=get(matrix,"fuelTime"); SaveIO.write(save); SaveIO.load(save);
        matrix=Vars.world.build(90,90); source=Vars.world.build(80,90);
        check(matrix.items.get(nano)==59 && get(matrix,"fuelTime")==paid && matrix.power.graph==source.power.graph,"save lost paid fuel, inventory or power links");
        var buffer=new java.io.ByteArrayOutputStream();
        set(matrix,"fuelTime",20f); matrix.write(arc.util.io.Writes.get(new java.io.DataOutputStream(buffer)));
        matrix.read(arc.util.io.Reads.get(new java.io.DataInputStream(new java.io.ByteArrayInputStream(buffer.toByteArray()))),(byte)1);
        check(get(matrix,"fuelTime")==300f,"legacy paid nano reserve was discarded");
        System.out.println("NANO_MATRIX_CHECK_PASS: 7x7, native 18000/s power, exact 1 nano/0.5s, building/ground/air/naval/allied healing, enemies/range/suppression, disable/blackout/starvation, large delta, save/load");
    }
    static Unit spawn(mindustry.type.UnitType type,Team team,float x,float y){ Unit u=type.create(team); u.set(x,y); u.add(); u.health=u.maxHealth/2f; return u; }
    static float get(Building b,String field)throws Exception{return b.getClass().getField(field).getFloat(b);}
    static void set(Building b,String field,float value)throws Exception{b.getClass().getField(field).setFloat(b,value);}
}
