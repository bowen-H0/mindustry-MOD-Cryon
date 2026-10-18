package cryon.Features;

import arc.func.Prov;
import arc.graphics.Color;
import arc.graphics.Gl;
import arc.graphics.Mesh;
import arc.graphics.VertexAttribute;
import arc.graphics.gl.Shader;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Mat3D;
import arc.math.geom.Vec3;
import arc.struct.FloatSeq;
import arc.util.Time;
import mindustry.graphics.g3d.GenericMesh;
import mindustry.graphics.g3d.MultiMesh;
import mindustry.graphics.g3d.PlanetParams;
import mindustry.type.Planet;

/** One static mesh, one draw call; no textures, particles or frame buffers. */
public final class CruxStationMesh implements GenericMesh{
    private final Planet planet;
    private final Mesh mesh;
    private final Shader shader;
    private final Mat3D transform = new Mat3D();
    private final Vec3 light = new Vec3();

    public static void install(Planet planet){
        Prov<GenericMesh> original = planet.meshLoader;
        planet.meshLoader = () -> new MultiMesh(original.get(), new CruxStationMesh(planet));
        // Mod.init runs after content loading on desktop; also preserve future reloads.
        if(planet.mesh != null){
            planet.mesh = new MultiMesh(planet.mesh, new CruxStationMesh(planet));
        }
        planet.clipRadius = Math.max(planet.clipRadius, planet.radius * 2.1f);
    }

    public CruxStationMesh(Planet planet){
        this.planet = planet;
        FloatSeq vertices = new FloatSeq();
        Color hull = Color.valueOf("687780"), armor = Color.valueOf("a5afb3");
        Color dark = Color.valueOf("303b48"), red = Color.valueOf("ed5559");
        // Ring lies in the XZ plane. Twenty-four rectangular sections form a closed dock.
        for(int i = 0; i < 24; i++){
            float a = i * 15f, b = (i + 1) * 15f;
            Vec3[] p = new Vec3[8];
            for(int j = 0; j < 8; j++){
                float angle = (j & 1) == 0 ? a : b;
                float radius = (j & 2) == 0 ? 0.24f : 0.29f;
                p[j] = new Vec3(Mathf.cosDeg(angle) * radius,
                        (j & 4) == 0 ? -0.025f : 0.025f, Mathf.sinDeg(angle) * radius);
            }
            quad(vertices, p[0], p[2], p[3], p[1], dark);
            quad(vertices, p[4], p[5], p[7], p[6], armor);
            quad(vertices, p[0], p[1], p[5], p[4], hull);
            quad(vertices, p[2], p[6], p[7], p[3], hull);
        }
        box(vertices, 0, 0, 0, 0.10f, 0.25f, 0.10f, hull);
        box(vertices, 0, 0.145f, 0, 0.15f, 0.055f, 0.15f, armor);
        box(vertices, 0, 0.19f, 0, 0.025f, 0.055f, 0.025f, dark);
        box(vertices, 0, 0, 0, 0.55f, 0.035f, 0.035f, hull);
        box(vertices, 0, 0, 0, 0.035f, 0.035f, 0.55f, hull);
        for(int side : new int[]{-1, 1}){
            // Paired docks and equipment banks give the station a military silhouette.
            box(vertices, side * 0.30f, -0.04f, 0, 0.12f, 0.08f, 0.13f, dark);
            box(vertices, side * 0.30f, 0.005f, 0, 0.12f, 0.012f, 0.10f, red);
            box(vertices, 0, 0.015f, side * 0.285f, 0.10f, 0.055f, 0.08f, armor);
            box(vertices, 0, 0.047f, side * 0.285f, 0.065f, 0.009f, 0.035f, red);
            box(vertices, side * 0.14f, -0.015f, 0.14f, 0.13f, 0.018f, 0.15f, dark);
            box(vertices, side * 0.14f, -0.015f, -0.14f, 0.13f, 0.018f, 0.15f, dark);
        }
        // Reduce the station to 24% of its original dimensions and bake its orbit offset.
        // All orbital objects share one vertex buffer and one draw call.
        for(int i = 0; i < vertices.size; i += 7){
            vertices.items[i] = vertices.items[i] * 0.24f + 1.65f;
            vertices.items[i + 1] *= 0.24f;
            vertices.items[i + 2] *= 0.24f;
        }
        Rand random = new Rand(88132);
        Color debris = Color.valueOf("64706e"), panels = Color.valueOf("3b536c");
        for(int i = 0; i < 64; i++){
            float a = i * 360f / 64f + random.range(2f);
            float r = random.random(1.30f, 1.47f);
            float size = random.random(0.009f, 0.023f);
            box(vertices, Mathf.cosDeg(a) * r, random.range(0.022f), Mathf.sinDeg(a) * r,
                    size, size * random.random(0.4f, 0.9f), size * 1.4f, debris);
        }
        // Twelve small operational satellites form the outer orbital belt.
        for(int i = 0; i < 12; i++){
            float a = i * 30f + 12f;
            float x = Mathf.cosDeg(a) * 1.85f, z = Mathf.sinDeg(a) * 1.85f;
            box(vertices, x, 0f, z, 0.026f, 0.025f, 0.026f, armor);
            box(vertices, x, 0f, z, 0.10f, 0.006f, 0.035f, panels);
            box(vertices, x, 0.016f, z, 0.012f, 0.008f, 0.012f, red);
        }
        mesh = new Mesh(true, vertices.size / 7, 0,
                VertexAttribute.position3, VertexAttribute.normal, VertexAttribute.color);
        mesh.setVertices(vertices.toArray());
        shader = new Shader(
                "attribute vec4 a_position;\nattribute vec3 a_normal;\nattribute vec4 a_color;\n"
                + "uniform mat4 u_proj;\nuniform mat4 u_trans;\nuniform vec3 u_light;\n"
                + "varying vec4 v_color;\nvoid main(){\n"
                + "vec3 normal = normalize(mat3(u_trans) * a_normal);\n"
                + "float brightness = 0.42 + 0.58 * max(dot(normal, u_light), 0.0);\n"
                + "float lamp = step(a_color.g * 1.5, a_color.r);\n"
                + "v_color = vec4(a_color.rgb * mix(brightness, 1.0, lamp), 1.0);\n"
                + "gl_Position = u_proj * u_trans * a_position;\n}",
                "varying vec4 v_color;\nvoid main(){ gl_FragColor = v_color; }"
        );
    }

    @Override
    public void render(PlanetParams params, Mat3D projection, Mat3D planetTransform){
        // Match PlanetRenderer.renderSectors: hide while the sector map UI is visible,
        // including its fade animation, independently of camera zoom.
        if(params.planet == planet && planet.hasGrid() && params.drawUi && params.uiAlpha > 0.02f) return;
        // Reuse all matrices. The orbit completes in about six minutes at normal speed.
        float angle = (Time.globalTime / 60f) % 360f + 35f;
        transform.set(planetTransform).rotate(Vec3.Z, 22f).rotate(Vec3.Y, angle)
                .scale(planet.radius, planet.radius, planet.radius);
        light.set(planet.solarSystem.position).sub(planet.position).nor();
        shader.bind();
        shader.setUniformMatrix4("u_proj", projection.val);
        shader.setUniformMatrix4("u_trans", transform.val);
        shader.setUniformf("u_light", light);
        mesh.render(shader, Gl.triangles);
    }

    @Override
    public void dispose(){
        mesh.dispose();
        shader.dispose();
    }

    private static void box(FloatSeq out, float x, float y, float z,
                            float width, float height, float depth, Color color){
        Vec3[] p = new Vec3[8];
        for(int i = 0; i < 8; i++) p[i] = new Vec3(
                x + ((i & 1) == 0 ? -width : width) / 2f,
                y + ((i & 2) == 0 ? -height : height) / 2f,
                z + ((i & 4) == 0 ? -depth : depth) / 2f);
        quad(out, p[0], p[4], p[6], p[2], color);
        quad(out, p[1], p[3], p[7], p[5], color);
        quad(out, p[0], p[1], p[5], p[4], color);
        quad(out, p[2], p[6], p[7], p[3], color);
        quad(out, p[0], p[2], p[3], p[1], color);
        quad(out, p[4], p[5], p[7], p[6], color);
    }

    private static void quad(FloatSeq out, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Color color){
        Vec3 normal = b.cpy().sub(a).crs(c.cpy().sub(a)).nor();
        for(Vec3 p : new Vec3[]{a, b, c, a, c, d}){
            out.add(p.x, p.y, p.z);
            out.add(normal.x, normal.y, normal.z);
            out.add(color.toFloatBits());
        }
    }
}
