package cryon.Features;

import arc.math.geom.Rect;
import arc.struct.ObjectMap;
import cryon.Type.OrbitalUnitType;
import mindustry.Vars;
import mindustry.async.AsyncProcess;
import mindustry.async.PhysicsProcess.PhysicsWorld;
import mindustry.async.PhysicsProcess.PhysicsWorld.PhysicsBody;
import mindustry.gen.Groups;
import mindustry.gen.Unit;

/** Native separation in an independent world, shared only by orbital units of every team. */
public final class OrbitalPhysics implements AsyncProcess{
    private static boolean installed;
    private final ObjectMap<Unit, Body> bodies = new ObjectMap<>();
    private PhysicsWorld world;

    private static final class Body{
        final PhysicsBody physics = new PhysicsBody();
        float startX, startY;
    }

    public static void install(){
        if(installed) return;
        installed = true;
        Vars.asyncCore.processes.add(new OrbitalPhysics());
    }

    @Override public void init(){
        reset();
        world = new PhysicsWorld(Vars.world.getQuadBounds(new Rect()));
    }

    @Override public void reset(){ bodies.clear(); world = null; }

    @Override public void begin(){
        if(world == null) init();
        var entries = bodies.entries();
        while(entries.hasNext()){
            var entry = entries.next();
            if(!active(entry.key)){
                world.remove(entry.value.physics);
                entries.remove();
            }
        }
        for(Unit unit : Groups.unit){
            if(!active(unit)) continue;
            Body body = bodies.get(unit);
            if(body == null){
                body = new Body();
                bodies.put(unit, body);
                world.add(body.physics);
            }
            body.startX = body.physics.x = unit.x;
            body.startY = body.physics.y = unit.y;
            body.physics.radius = unit.hitSize * 0.6f;
            body.physics.mass = Math.max(0.001f, unit.mass());
            body.physics.local = !Vars.net.client() || unit.isLocal();
        }
    }

    private static boolean active(Unit unit){
        return unit.isAdded() && !unit.dead && unit.type instanceof OrbitalUnitType;
    }

    @Override public boolean shouldProcess(){ return world != null && bodies.size > 0; }

    // Only independent physics bodies are accessed by the worker thread.
    @Override public void process(){ if(world != null) world.update(); }

    @Override public void end(){
        for(var entry : bodies){
            Unit unit = entry.key;
            Body body = entry.value;
            if(active(unit) && body.physics.local){
                unit.move(body.physics.x - body.startX, body.physics.y - body.startY);
            }
        }
    }
}
