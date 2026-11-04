package cryon.Type;

import arc.graphics.Color;
import arc.math.geom.Ray;
import arc.math.geom.Vec3;
import mindustry.Vars;
import mindustry.content.Planets;
import mindustry.graphics.g3d.NoiseMesh;
import mindustry.type.Planet;

/** A campaign layer at Cryon's coordinates, without an independent orbit or rotation. */
public class CryonLayerPlanet extends Planet{
    public CryonLayerPlanet(){
        super("cryon-depths", Planets.sun, 1f, 2);
        orbitRadius = 130f;
        accessible = alwaysUnlocked = false;
        defaultEnv = mindustry.world.meta.Env.terrestrial | mindustry.world.meta.Env.underwater;
        visible = false;
        startSector = 0;
        minZoom = 0.5f;
        maxZoom = 2f;
        allowLaunchToNumbered = allowLaunchLoadout = allowLaunchSchematics = allowSectorInvasion = false;
        hasAtmosphere = true;
        atmosphereColor = Color.valueOf("3db899");
        atmosphereRadIn = 0.01f;
        atmosphereRadOut = 0.08f;
        iconColor = Color.valueOf("597be3");
        // Content loads before the custom generator is installed during mod initialization.
        meshLoader = () -> new NoiseMesh(this, 73, 4, Color.valueOf("153753"), 1f, 4, 0.5f, 0.75f, 0.02f);
    }

    private Planet surface(){ return Vars.content.planet("cryon-cryon"); }

    @Override public void init(){
        defaultCore = Vars.content.block("cryon-core-pioneer");
        super.init();
    }

    @Override public float getOrbitAngle(){
        Planet surface = surface();
        return surface == null ? super.getOrbitAngle() : surface.getOrbitAngle();
    }

    @Override public float getRotation(){
        Planet surface = surface();
        return surface == null ? super.getRotation() : surface.getRotation();
    }

    @Override public Vec3 getWorldPosition(Vec3 out){
        Planet surface = surface();
        return surface == null ? super.getWorldPosition(out) : surface.getWorldPosition(out);
    }

    @Override public Vec3 intersect(Ray ray, float radius){
        // The library's picker ignores Planet.visible; hidden layers must not intercept the globe.
        return visible ? super.intersect(ray, radius) : null;
    }
}
