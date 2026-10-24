package cryon.Script;

import arc.Events;
import arc.Core;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.io.*;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.gen.*;
import mindustry.ui.Styles;
import cryon.Type.JavaProcessor.JavaProcessorBuild;
import java.io.*;
import static mindustry.Vars.*;

/** Repeated salvos composed of the existing one-shot nuclear strike implementation. */
public class NuclearCleansingScript extends MapScript{
    public static final int maxActive = 48;
    public float intervalSeconds = 60f, power = 3f, minPower = 1f, maxPower = 5f;
    public int missilesPerSalvo = 3;
    public boolean randomPower, friendlyFire = true, running;
    public float timer;
    public int nextStrikeId = 1;
    public final Seq<NukeStrikeScript> strikes = new Seq<>();
    private static boolean networkInitialized;

    public static void initNetworking(){
        if(networkInitialized) return;
        networkInitialized = true;
        Events.on(ClientLoadEvent.class, event -> netClient.addPacketHandler("cryon-nuclear-cleansing", data -> {
            String[] args = data.split("\\|");
            if(args.length != 8 || world == null) return;
            try{
                Building b = world.build(Integer.parseInt(args[0]));
                if(!(b instanceof JavaProcessorBuild)
                    || !(((JavaProcessorBuild)b).script instanceof NuclearCleansingScript)) return;
                NuclearCleansingScript script = (NuclearCleansingScript)((JavaProcessorBuild)b).script;
                int id = Integer.parseInt(args[1]);
                if(script.findStrike(id) != null) return;
                NukeStrikeScript strike = script.newStrike(id);
                strike.power = Float.parseFloat(args[2]);
                strike.tx = Float.parseFloat(args[3]);
                strike.ty = Float.parseFloat(args[4]);
                strike.blastRadius = Float.parseFloat(args[5]);
                strike.friendlyFire = Boolean.parseBoolean(args[6]);
                strike.remaining = strike.totalTicks = Float.parseFloat(args[7]);
                strike.phase = NukeStrikeScript.LOCK;
                script.strikes.add(strike);
                script.nextStrikeId = Math.max(script.nextStrikeId, id + 1);
                script.timer = script.intervalSeconds * 60f;
            }catch(NumberFormatException ignored){
                // Only server-issued spawn state is accepted here.
            }
        }));
    }

    private NukeStrikeScript newStrike(int id){
        NukeStrikeScript strike = new NukeStrikeScript();
        strike.attach(build);
        strike.strikeId = id;
        strike.showStation = false;
        strike.simpleWarning = true;
        strike.friendlyFire = friendlyFire;
        return strike;
    }

    public NukeStrikeScript findStrike(int id){
        return strikes.find(strike -> strike.strikeId == id);
    }

    public int launchSalvo(){
        if(net.client()) return 0;
        if(maxActive - strikes.size < missilesPerSalvo) return 0;
        int count = missilesPerSalvo;
        int launched = 0;
        for(int i = 0; i < count; i++){
            int id = nextStrikeId;
            float strength = randomPower
                ? Mathf.randomSeed(((long)build.pos() << 32) ^ id, minPower, maxPower) : power;
            NukeStrikeScript strike = newStrike(id);
            if(!strike.arm(strength, 1200f, strikes)) break;
            nextStrikeId++;
            strikes.add(strike);
            launched++;
            if(net.server()) Call.clientPacketReliable("cryon-nuclear-cleansing",
                build.pos() + "|" + id + "|" + strike.power + "|" + strike.tx + "|" + strike.ty
                    + "|" + strike.blastRadius + "|" + strike.friendlyFire + "|" + strike.remaining);
        }
        return launched;
    }

    @Override public void update(){
        int incoming = 0;
        // Stop only prevents future salvos; already committed strikes continue independently.
        for(int i = strikes.size - 1; i >= 0; i--){
            NukeStrikeScript strike = strikes.get(i);
            // Cross the threshold once per missile, grouping an entire salvo into one message.
            if(strike.phase == NukeStrikeScript.LOCK && strike.remaining > 180f
                && strike.remaining - delta() <= 180f) incoming++;
            strike.update();
            if(strike.phase == NukeStrikeScript.DONE) strikes.remove(i);
        }
        if(incoming > 0 && !headless && ui != null){
            ui.announce(Core.bundle.format("warning.cryon-nuclear-cleansing", incoming), 3f);
        }
        if(!running) return;
        timer = Math.max(0f, timer - delta());
        if(timer <= 0f && !net.client()){
            // Empty maps and the concurrency limit retry without creating unbounded waiting strikes.
            timer = launchSalvo() > 0 ? intervalSeconds * 60f : 60f;
        }
    }

    /** Called through the processor's normal network-synchronized building configuration. */
    public void configure(String data){
        String[] args = data.split(",");
        if(args.length != 8) return;
        try{
            float interval = Float.parseFloat(args[0]), strength = Float.parseFloat(args[2]);
            float low = Float.parseFloat(args[4]), high = Float.parseFloat(args[5]);
            int count = Integer.parseInt(args[1]);
            if(!Float.isFinite(interval) || !Float.isFinite(strength)
                || !Float.isFinite(low) || !Float.isFinite(high)) return;
            intervalSeconds = Mathf.clamp(interval, 5f, 600f);
            missilesPerSalvo = Mathf.clamp(count, 1, 12);
            power = Mathf.clamp(strength, 0.5f, 10f);
            randomPower = Boolean.parseBoolean(args[3]);
            minPower = Mathf.clamp(Math.min(low, high), 0.5f, 10f);
            maxPower = Mathf.clamp(Math.max(low, high), minPower, 10f);
            friendlyFire = Boolean.parseBoolean(args[6]);
            boolean start = Boolean.parseBoolean(args[7]);
            if(start && !running) timer = intervalSeconds * 60f;
            else timer = Math.min(timer, intervalSeconds * 60f);
            running = start;
        }catch(NumberFormatException ignored){
            // Preserve the current settings if a malformed configuration is received.
        }
    }

    private void sync(boolean start){
        build.configure("nuclear-cleansing:" + intervalSeconds + "," + missilesPerSalvo + "," + power
            + "," + randomPower + "," + minPower + "," + maxPower + "," + friendlyFire + "," + start);
    }

    @Override public void buildUI(Table table){
        table.row();
        table.label(() -> Core.bundle.format("script.nuclear-cleansing.schedule", (int)intervalSeconds, missilesPerSalvo));
        table.row();
        table.slider(5f, 600f, 5f, intervalSeconds, value -> {intervalSeconds = value; sync(running);}).width(260f);
        table.row();
        table.add(Core.bundle.get("script.nuclear-cleansing.salvo-count"));
        table.row();
        table.slider(1f, 12f, 1f, missilesPerSalvo, value -> {missilesPerSalvo = (int)value; sync(running);}).width(260f);
        table.row();
        table.check(Core.bundle.get("script.nuclear-cleansing.random-power"), randomPower, value -> {randomPower = value; sync(running);});
        table.row();
        table.label(() -> Core.bundle.format("script.nuclear-cleansing.fixed-power", power));
        table.row();
        table.slider(0.5f, 10f, 0.5f, power, value -> {power = value; sync(running);}).width(260f);
        table.row();
        table.label(() -> Core.bundle.format("script.nuclear-cleansing.power-range", minPower, maxPower));
        table.row();
        table.slider(0.5f, 10f, 0.5f, minPower, value -> {
            minPower = value; maxPower = Math.max(maxPower, minPower); sync(running);
        }).width(260f);
        table.row();
        table.slider(0.5f, 10f, 0.5f, maxPower, value -> {
            maxPower = value; minPower = Math.min(minPower, maxPower); sync(running);
        }).width(260f);
        table.row();
        table.check(Core.bundle.get("script.nuclear-cleansing.friendly-fire"), friendlyFire, value -> {friendlyFire = value; sync(running);});
        table.row();
        table.label(() -> Core.bundle.format("script.nuclear-cleansing.status", (running ? Core.bundle.format("script.nuclear-cleansing.next-salvo", (int)Math.ceil(timer / 60f)) : Core.bundle.get("script.nuclear-cleansing.stopped")), strikes.size));
        table.row();
        table.add(Core.bundle.format("script.nuclear-cleansing.help", 20, maxActive)).wrap().width(300f);
        table.row();
        table.button(Core.bundle.get("script.nuclear-cleansing.toggle"), Styles.flatt, () -> sync(!running)).size(220f, 40f);
    }

    @Override public void removed(){
        running = false;
        for(NukeStrikeScript strike : strikes) strike.removed();
        strikes.clear();
    }

    @Override public void write(Writes write){
        write.b(1);
        write.f(intervalSeconds);write.i(missilesPerSalvo);write.f(power);write.bool(randomPower);
        write.f(minPower);write.f(maxPower);write.bool(friendlyFire);write.bool(running);
        write.f(timer);write.i(nextStrikeId);write.i(strikes.size);
        for(NukeStrikeScript strike : strikes){
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            strike.write(new Writes(new DataOutputStream(data)));
            write.i(strike.strikeId);write.i(data.size());write.b(data.toByteArray());
        }
    }

    @Override public void read(Reads read){
        read.b();
        intervalSeconds = Mathf.clamp(read.f(), 5f, 600f);
        missilesPerSalvo = Mathf.clamp(read.i(), 1, 12);
        power = Mathf.clamp(read.f(), 0.5f, 10f);randomPower = read.bool();
        minPower = Mathf.clamp(read.f(), 0.5f, 10f);maxPower = Mathf.clamp(read.f(), minPower, 10f);
        friendlyFire = read.bool();running = read.bool();timer = Math.max(0f, read.f());nextStrikeId = read.i();
        int count = read.i();
        if(count < 0 || count > maxActive) throw new IllegalArgumentException("Invalid nuclear salvo count");
        strikes.clear();
        for(int i = 0; i < count; i++){
            int id = read.i(), length = read.i();
            if(length < 0 || length > 4096) throw new IllegalArgumentException("Invalid nuclear strike data length");
            NukeStrikeScript strike = newStrike(id);
            strike.read(new Reads(new DataInputStream(new ByteArrayInputStream(read.b(length)))));
            if(strike.phase != NukeStrikeScript.DONE) strikes.add(strike);
        }
    }
}
