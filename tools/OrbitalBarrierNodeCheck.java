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

/** Native engine integration: links, swept interception, beams, reinforcement and chain reactions. */
public final class OrbitalBarrierNodeCheck{
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
        Block node = Vars.content.block("cryon-orbital-barrier-node");
        check(node != null && node.buildVisibility == BuildVisibility.shown && node.size == 9 && node.requirements.length >= 6 && !node.alwaysUnlocked, "top-tier survival node missing");
        Vars.logic = new Logic(); Vars.logic.reset();
        Vars.world.loadGenerator(220, 100, tiles -> {
            for(int x = 0; x < 220; x++) for(int y = 0; y < 100; y++) tiles.set(x, y, new Tile(x, y, Blocks.stone, Blocks.air, Blocks.air));
            tiles.get(10, 10).setBlock(Blocks.coreShard, Team.sharded);
            tiles.get(45, 30).setBlock(node, Team.sharded);
            tiles.get(45, 60).setBlock(node, Team.sharded);
            tiles.get(75, 60).setBlock(node, Team.sharded);
            tiles.get(75, 30).setBlock(node, Team.sharded);
            tiles.get(80, 45).setBlock(Blocks.thoriumWallLarge, Team.sharded);
            tiles.get(90, 80).setBlock(node, Team.crux);
            tiles.get(150, 30).setBlock(node, Team.sharded);
            tiles.get(180, 30).setBlock(node, Team.sharded);
            tiles.get(60, 45).setBlock(Vars.content.block("cryon-orbital-power-node"), Team.sharded);
        });
        Time.delta = 1f; Vars.state.set(mindustry.core.GameState.State.playing);
        Building a = Vars.world.build(45,30), b = Vars.world.build(45,60), c = Vars.world.build(75,60), d = Vars.world.build(75,30);
        Building wall = Vars.world.build(80,45);
        a.configureAny(b.pos()); b.configureAny(c.pos()); c.configureAny(d.pos()); d.configureAny(a.pos());
        check(links(a).size == 2 && links(b).contains(a.pos()), "bidirectional links failed");
        a.configureAny(Vars.world.build(90,80).pos());
        check(links(a).size == 2, "enemy link accepted");
        a.configureAny(Vars.world.build(180,30).pos()); check(links(a).size == 2, "out-of-range link accepted");
        a.configureAny(Vars.world.build(150,30).pos()); check(links(a).size == 3, "105-tile link refused");
        a.configureAny(Vars.world.build(150,30).pos());
        Building source=Vars.world.build(60,45);
        for(Building n:new Building[]{a,b,c,d}){ source.configureAny(n.pos()); n.shouldConsumePower=true; }
        source.power.graph.update();
        check(node.consPower.usage == 600f && a.power.status > 0.99f, "native 36000/s power demand not supplied");
        source.enabled=false; source.power.graph.update();
        check(a.power.status < 0.01f && !(boolean)a.getClass().getMethod("active").invoke(a), "unpowered shield remains active");
        set(a,"shield",100000f); a.items.add(mindustry.content.Items.phaseFabric,2); a.liquids.add(mindustry.content.Liquids.cryofluid,60f);
        a.updateTile();
        check(get(a,"shield")==100000f && a.items.get(mindustry.content.Items.phaseFabric)==2 && a.liquids.get(mindustry.content.Liquids.cryofluid)==60f, "unpowered node recharges or consumes boosters");
        var blackoutType = new mindustry.entities.bullet.BasicBulletType(400f,1000f); blackoutType.init();
        Bullet blackout=blackoutType.create(null,Team.crux,200f,360f,0f); blackout.update();
        check(!blackout.absorbed,"blackout did not open barrier"); blackout.remove();
        source.enabled=true; source.power.graph.update();
        check((boolean)a.getClass().getMethod("active").invoke(a), "shield did not recover after power restoration");
        a.power.status=0.5f;
        check(!(boolean)a.getClass().getMethod("active").invoke(a), "brownout left the shield active");
        a.power.status=1f;
        a.items.clear(); a.liquids.clear(); set(a,"shield",120000f);
        check(a.acceptItem(a, mindustry.content.Items.phaseFabric) && !a.acceptItem(a, mindustry.content.Items.copper), "phase input filter");
        check(a.acceptLiquid(a, mindustry.content.Liquids.cryofluid) && !a.acceptLiquid(a, mindustry.content.Liquids.water), "coolant input filter");
        var basic = new mindustry.entities.bullet.BasicBulletType(400f, 1000f); basic.lifetime = 20f; basic.absorbable=false; basic.init();
        Bullet fast = basic.create(null, Team.crux, 200f, 360f, 0f);
        float before = get(a,"shield"); fast.update();
        check(fast.absorbed && get(a,"shield") < before, "fast enemy projectile tunneled through");
        Bullet friendly = basic.create(null, Team.sharded, 200f, 360f, 0f); friendly.update();
        check(!friendly.absorbed, "friendly projectile blocked"); friendly.remove();
        Vars.state.rules.tags.put("cryon-allied-teams", Integer.toString(Team.green.id));
        Bullet ally = basic.create(null, Team.green, 200f, 360f, 0f); ally.update();
        check(!ally.absorbed, "map ally projectile blocked"); ally.remove();
        var edgeType=new mindustry.entities.bullet.BasicBulletType(25f,100f);edgeType.init();
        Bullet edgeShot=edgeType.create(null,Team.crux,310f,360f,0f);edgeShot.update();
        check(edgeShot.absorbed,"bullet entered visible thickness without crossing centerline");
        var geometry=node.getClass().getMethod("thickCrossing",float.class,float.class,float.class,float.class,float.class,float.class,float.class,float.class,float.class);
        float edgeHit=(float)geometry.invoke(null,310f,360f,335f,360f,360f,240f,360f,480f,32f);
        check(Math.abs(edgeHit-.72f)<.001f,"wrong outer-face interception");
        float capHit=(float)geometry.invoke(null,360f,190f,360f,230f,360f,240f,360f,480f,32f);
        check(Math.abs(capHit-.45f)<.001f,"rounded endpoint thickness ignored");
        float hp = wall.health;
        var laser = new mindustry.entities.bullet.LaserBulletType(1000f); laser.length = 650f; laser.init();
        laser.create(null, Team.crux, 200f, 360f, 0f);
        check(wall.health == hp && laser.length == 650f, "laser bypassed curtain or shared type was not restored");
        var rail = new mindustry.entities.bullet.RailBulletType(); rail.damage = 1000f; rail.length = 650f; rail.init();
        rail.create(null, Team.crux, 200f, 360f, 0f);
        check(wall.health == hp && rail.length == 650f, "rail bypassed curtain");
        var continuous = new mindustry.entities.bullet.ContinuousLaserBulletType(1000f); continuous.length = 650f; continuous.init();
        Bullet beam = continuous.create(null, Team.crux, 200f, 360f, 0f);
        for(int i=0;i<20;i++) beam.update();
        check(wall.health == hp && continuous.length == 650f, "continuous beam bypassed curtain"); beam.remove();
        var unit = mindustry.content.UnitTypes.flare.create(Team.crux); unit.set(370f,360f); unit.add(); unit.lastX = 300f; unit.lastY = 360f;
        Class<?> type = node.getClass();
        type.getMethod("blockUnits").invoke(null);
        check(unit.x < 360f && !unit.dead, "fast air unit not stopped"); unit.remove();
        var allyUnit = mindustry.content.UnitTypes.flare.create(Team.green); allyUnit.set(370f,360f); allyUnit.add(); allyUnit.lastX=300f; allyUnit.lastY=360f;
        type.getMethod("blockUnits").invoke(null);
        check(allyUnit.x == 370f, "allied unit blocked"); allyUnit.remove();
        for(Building n : new Building[]{a,b,c,d}){ set(n,"shield",120000f); set(n,"quiet",0f); n.items.add(mindustry.content.Items.phaseFabric,2); n.liquids.add(mindustry.content.Liquids.cryofluid,100f); n.updateTile(); }
        check(get(a,"phaseTime") > 0 && get(a,"shield") == 200000f && a.items.get(mindustry.content.Items.phaseFabric) == 1, "phase fabric not consumed or capacity not boosted");
        set(a,"shield",100000f); float liquid = a.liquids.get(mindustry.content.Liquids.cryofluid); a.updateTile();
        check(get(a,"shield") == 100600f && a.liquids.get(mindustry.content.Liquids.cryofluid) < liquid, "coolant did not boost regeneration");
        // A real boss repeatedly presses the same segment; it must survive and eventually break through.
        var bossType = Vars.content.unit("cryon-eon"); check(bossType != null,"missing boss fixture");
        var boss = bossType.create(Team.crux); boss.add(); float bossHp=boss.health; int ticks=0;
        while(!(boolean)a.getClass().getField("broken").get(a) && ticks < 600){
            boss.set(370f,360f); boss.lastX=250f; boss.lastY=360f;
            type.getMethod("blockUnits").invoke(null); a.updateTile(); b.updateTile(); ticks++;
        }
        check(ticks > 60 && ticks < 360 && boss.health == bossHp && !boss.dead, "boss blockade must last seconds and never kill boss: ticks="+ticks);
        boss.remove();
        check(a.isValid() && (boolean)a.getClass().getField("broken").get(a), "overload destroyed node");
        for(int i=0;i<250;i++) a.updateTile();
        check(!(boolean)a.getClass().getField("broken").get(a), "cooled shield did not reboot");
        set(c,"fuse",9f);
        Fi save=data.child("barrier.msav"); SaveIO.write(save); SaveIO.load(save);
        a=Vars.world.build(45,30); b=Vars.world.build(45,60); c=Vars.world.build(75,60); d=Vars.world.build(75,30);
        check(links(a).contains(b.pos()) && links(b).contains(a.pos()) && get(c,"fuse")==9f && get(a,"phaseTime")>0, "save/load lost links, boost or pending chain");
        set(c,"fuse",-1f);
        a.kill();
        check(get(b,"fuse")>=0f && get(d,"fuse")>=0f, "destruction did not ignite both neighbors");
        for(int i=0;i<40;i++) for(Building n : new Building[]{b,c,d}) if(n.isValid()) n.updateTile();
        check(!b.isValid() && !c.isValid() && !d.isValid(), "cyclic chain did not propagate/terminate");
        System.out.println("ORBITAL_BARRIER_CHECK_PASS: 9x9 survival node, 105-tile links, native power, blackout/brownout gates, input filters, thick outer-face/rounded-end interception, fast bullets/units, allied passage, laser/rail/continuous fire, phase/coolant, boss pressure "+ticks+" ticks, reboot, save/load, cyclic chain");
    }
    static arc.struct.IntSeq links(Building b) throws Exception{ return (arc.struct.IntSeq)b.getClass().getField("links").get(b); }
    static float get(Building b,String field) throws Exception{ return b.getClass().getField(field).getFloat(b); }
    static void set(Building b,String field,float value) throws Exception{ b.getClass().getField(field).setFloat(b,value); }
}
