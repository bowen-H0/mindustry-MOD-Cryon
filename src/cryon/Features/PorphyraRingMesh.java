package cryon.Features;

import arc.func.Prov;
import arc.graphics.Color;
import arc.graphics.Gl;
import arc.graphics.Mesh;
import arc.graphics.VertexAttribute;
import arc.graphics.gl.Shader;
import arc.math.Mathf;
import arc.math.geom.Mat3D;
import arc.math.geom.Vec3;
import arc.struct.FloatSeq;
import mindustry.graphics.g3d.GenericMesh;
import mindustry.graphics.g3d.MultiMesh;
import mindustry.graphics.g3d.PlanetParams;
import mindustry.type.Planet;

/** Three thin, tilted orbital bands around Porphyra. */
public class PorphyraRingMesh implements GenericMesh{
    private final Planet planet;
    private final Mesh mesh;
    private final Shader shader;
    private final Mat3D transform = new Mat3D();

    public static void install(Planet planet){
        Prov<GenericMesh> original = planet.meshLoader;
        planet.meshLoader = () -> new MultiMesh(original.get(), new PorphyraRingMesh(planet));
        if(planet.mesh != null) planet.mesh = new MultiMesh(planet.mesh, new PorphyraRingMesh(planet));
        planet.clipRadius = Math.max(planet.clipRadius, planet.radius * 1.5f);
    }

    public PorphyraRingMesh(Planet planet){
        this.planet = planet;
        FloatSeq vertices = new FloatSeq();
        band(vertices, 1.17f, 1.20f, Color.valueOf("5d426d"));
        band(vertices, 1.25f, 1.31f, Color.valueOf("a487bc"));
        band(vertices, 1.38f, 1.43f, Color.valueOf("d5c0e1"));
        mesh = new Mesh(true, vertices.size / 7, 0,
                VertexAttribute.position3, VertexAttribute.normal, VertexAttribute.color);
        mesh.setVertices(vertices.toArray());
        shader = new Shader(
                "attribute vec4 a_position;\nattribute vec3 a_normal;\nattribute vec4 a_color;\n"
                + "uniform mat4 u_proj;\nuniform mat4 u_trans;\nvarying vec4 v_color;\n"
                + "void main(){ v_color = a_color; gl_Position = u_proj * u_trans * a_position; }",
                "varying vec4 v_color;\nvoid main(){ gl_FragColor = v_color; }"
        );
    }

    private static void band(FloatSeq out, float inner, float outer, Color color){
        for(int i = 0; i < 96; i++){
            float a = i * 360f / 96f, b = (i + 1) * 360f / 96f;
            add(out, inner, a, color); add(out, outer, a, color); add(out, outer, b, color);
            add(out, inner, a, color); add(out, outer, b, color); add(out, inner, b, color);
            add(out, outer, b, color); add(out, outer, a, color); add(out, inner, a, color);
            add(out, inner, b, color); add(out, outer, b, color); add(out, inner, a, color);
        }
    }

    private static void add(FloatSeq out, float r, float angle, Color color){
        out.add(Mathf.cosDeg(angle) * r, 0f, Mathf.sinDeg(angle) * r);
        out.add(0f, 1f, 0f);
        out.add(color.toFloatBits());
    }

    @Override public void render(PlanetParams params, Mat3D projection, Mat3D planetTransform){
        if(params.planet == planet && params.drawUi && params.uiAlpha > 0.02f) return;
        transform.set(planetTransform).rotate(Vec3.Z, 27f)
                .scale(planet.radius, planet.radius, planet.radius);
        shader.bind();
        shader.setUniformMatrix4("u_proj", projection.val);
        shader.setUniformMatrix4("u_trans", transform.val);
        mesh.render(shader, Gl.triangles);
    }

    @Override public void dispose(){ mesh.dispose(); shader.dispose(); }
}
