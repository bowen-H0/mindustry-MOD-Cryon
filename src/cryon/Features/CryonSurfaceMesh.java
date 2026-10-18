package cryon.Features;

import arc.graphics.Color;
import arc.math.geom.Vec3;
import arc.util.noise.Simplex;
import mindustry.graphics.Shaders;
import mindustry.graphics.g3d.HexMesh;
import mindustry.graphics.g3d.HexMesher;
import mindustry.graphics.g3d.MultiMesh;
import mindustry.graphics.g3d.NoiseMesh;
import mindustry.graphics.g3d.PlanetParams;
import mindustry.type.Planet;

/** Preserve the HJSON terrain profile, but supply the native emissive vertex channel. */
public final class CryonSurfaceMesh extends HexMesh{
    public static void install(Planet planet){
        planet.meshLoader = () -> new MultiMesh(
                new CryonSurfaceMesh(planet),
                new NoiseMesh(planet, 12, 3, 0.99f, 3, 0.5f, 0.6f, 0.4f,
                        Color.valueOf("1c6b5f"), Color.valueOf("2f8f7a"), 1, 0.5f, 1f, 0.5f){
                    @Override public void preRender(PlanetParams params){
                        super.preRender(params);
                        Shaders.planet.emissive = false;
                    }
                },
                new NoiseMesh(planet, 14, 3, 0.975f, 2, 0.5f, 0.8f, 0.2f,
                        Color.valueOf("6fb0a850"), Color.valueOf("79c1b730"), 1, 0.5f, 1f, 0.5f){
                    @Override public void preRender(PlanetParams params){
                        super.preRender(params);
                        Shaders.planet.emissive = false;
                    }
                });
        planet.reloadMesh();
    }

    public CryonSurfaceMesh(Planet planet){
        super(planet, new HexMesher(){
            final Color low = Color.valueOf("0a2e2c"), high = Color.valueOf("0f4a46");

            @Override public float getHeight(Vec3 p){
                return Simplex.noise3d(18, 3, 0.5, 0.5, 5f + p.x, 5f + p.y, 5f + p.z) * 0.6f;
            }

            @Override public void getColor(Vec3 p, Color out){
                out.set(Simplex.noise3d(19, 1, 0.5, 1, 5f + p.x, 5f + p.y, 5f + p.z) > 0.5f ? high : low);
            }

            @Override public boolean isEmissive(){ return true; }

            @Override public void getEmissiveColor(Vec3 p, Color out){
                planet.generator.getEmissiveColor(p, out);
            }
        }, 6, Shaders.planet);
    }
}
