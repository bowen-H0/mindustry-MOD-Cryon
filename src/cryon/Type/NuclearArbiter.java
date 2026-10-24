package cryon.Type;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.io.*;
import cryon.Content.CryonItems;
import cryon.Script.NukeStrikeScript;
import cryon.Script.NuclearCleansingScript;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.Bar;
import mindustry.world.Block;
import mindustry.world.meta.*;

/** Strategic anti-nuclear silo, deliberately not a Turret or a point-defense weapon. */
public class NuclearArbiter extends Block{
    public float range = 640f, chargeTime = 1800f;
    public ItemStack[] ammunition;
    private static final Color hatchDark = Color.valueOf("18212b"), hatchPanel = Color.valueOf("667581");
    private static boolean networkInitialized;

    public NuclearArbiter(String name){
        super(name);
        size = 5;
        health = 4800;
        solid = update = hasPower = hasItems = true;
        itemCapacity = 320;
        consumePower(60f);
        ammunition = ItemStack.with(Items.silicon, 160, Items.surgeAlloy, 80,
            Items.phaseFabric, 40, CryonItems.neutronite, 60);
        consumeItems(ammunition);
        requirements(Category.effect, BuildVisibility.shown, ItemStack.with(
            CryonItems.aluminum, 500, CryonItems.nickel, 400, Items.surgeAlloy, 250,
            Items.phaseFabric, 150, CryonItems.nanoMaterial, 100));
        if(!networkInitialized){
            networkInitialized = true;
            Events.on(ClientLoadEvent.class, event -> Vars.netClient.addPacketHandler("cryon-anti-nuke", data -> {
                String[] args = data.split("\\|");
                if((args.length != 4 && args.length != 5) || Vars.world == null) return;
                try{
                    Building b = Vars.world.build(Integer.parseInt(args[1]));
                    if(!(b instanceof JavaProcessor.JavaProcessorBuild)) return;
                    Object script = ((JavaProcessor.JavaProcessorBuild)b).script;
                    int id = args.length == 5 ? Integer.parseInt(args[4]) : 0;
                    NukeStrikeScript nuke = script instanceof NukeStrikeScript ? (NukeStrikeScript)script
                        : script instanceof NuclearCleansingScript ? ((NuclearCleansingScript)script).findStrike(id) : null;
                    if(nuke == null) return;
                    float x = Float.parseFloat(args[2]), y = Float.parseFloat(args[3]);
                    if(args[0].equals("launch")) nuke.beginInterception(x, y);
                    if(args[0].equals("hit")) nuke.intercepted(x, y);
                }catch(NumberFormatException ignored){
                    // Ignore malformed cosmetic state packets.
                }
            }));
        }
    }

    @Override public void setStats(){
        super.setStats();
        stats.add(Stat.range, range / Vars.tilesize, StatUnit.blocks);
        stats.add(Stat.reload, chargeTime / 60f, StatUnit.seconds);
    }

    @Override public void setBars(){
        super.setBars();
        addBar("charge", (NuclearArbiterBuild b) -> new Bar(
            () -> Core.bundle.format("bar.cryon-anti-nuke-charge", (int)(100f * b.charge / chargeTime)),
            () -> b.team.color, () -> b.charge / chargeTime));
    }

    /** Called before the script advances the falling missile, so a successful hit cannot explode. */
    public static void tryLaunch(NukeStrikeScript nuke){
        if(Vars.net.client() || nuke.phase != NukeStrikeScript.FALL || nuke.intercepting) return;
        NuclearArbiterBuild[] best = {null};
        Groups.build.each(b -> {
            if(!(b instanceof NuclearArbiterBuild) || b.team == nuke.build.team) return;
            NuclearArbiterBuild silo = (NuclearArbiterBuild)b;
            NuclearArbiter type = (NuclearArbiter)silo.block;
            if(!silo.isValid() || !silo.enabled || silo.efficiency < 0.99f
                || !silo.items.has(type.ammunition)
                || silo.charge < type.chargeTime || !silo.within(nuke.tx, nuke.ty, type.range)) return;
            if(best[0] == null || silo.dst2(nuke.tx, nuke.ty) < best[0].dst2(nuke.tx, nuke.ty)) best[0] = silo;
        });
        NuclearArbiterBuild silo = best[0];
        if(silo == null) return;
        silo.prepareLaunch();
        // Open while tracking, then launch only after the threat has completed half its descent.
        if(nuke.fallTimer < nuke.fallTime * Mathf.sqrt(0.5f) || silo.door < 0.95f) return;
        silo.consume();
        silo.charge = 0f;
        nuke.beginInterception(silo.x, silo.y);
        nuke.broadcastInterception("launch", silo.x, silo.y);
    }

    public class NuclearArbiterBuild extends Building{
        public float charge, door, launchHold;

        public void prepareLaunch(){
            launchHold = 120f;
        }

        @Override public void updateTile(){
            if(enabled && efficiency > 0f) charge = Math.min(chargeTime, charge + edelta());
            launchHold = Math.max(0f, launchHold - Time.delta);
            door = Mathf.approachDelta(door, launchHold > 0f ? 1f : 0f,
                launchHold > 0f ? 1f / 18f : 1f / 36f);
        }

        @Override public boolean acceptItem(Building source, Item item){
            return consumesItem(item) && items.get(item) < itemCapacity;
        }

        @Override public void drawSelect(){
            super.drawSelect();
            Drawf.dashCircle(x, y, range, team.color);
        }

        @Override public void draw(){
            super.draw();
            float z = Draw.z();
            try{
                Draw.z(Layer.blockOver);
                Draw.color(hatchDark);
                Fill.square(x, y, 11f);
                Draw.color(team.color, door * 0.45f);
                Lines.stroke(0.8f);
                Lines.square(x, y, 8.5f);
                Draw.color(hatchPanel);
                Fill.rect(x - 5.5f - door * 12f, y, 10.5f, 21f);
                Fill.rect(x + 5.5f + door * 12f, y, 10.5f, 21f);
                Draw.color(team.color, enabled && efficiency > 0f ? 0.85f : 0.15f);
                Lines.stroke(1f);
                Lines.square(x, y, 14f);
                int ready = (int)(charge / chargeTime * 8f);
                for(int i = 0; i < ready; i++) Fill.rect(x - 12f + i * 3.4f, y - 17f, 2f, 1.4f);
            }finally{
                Draw.reset();
                Lines.stroke(1f);
                Draw.z(z);
            }
        }

        @Override public void write(Writes write){
            super.write(write);
            write.f(charge);
        }

        @Override public void read(Reads read, byte revision){
            super.read(read, revision);
            charge = Mathf.clamp(read.f(), 0f, chargeTime);
        }
    }
}
