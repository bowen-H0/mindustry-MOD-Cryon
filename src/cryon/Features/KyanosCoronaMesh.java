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
import arc.util.Time;
import mindustry.graphics.g3d.GenericMesh;
import mindustry.graphics.g3d.MultiMesh;
import mindustry.graphics.g3d.PlanetParams;
import mindustry.type.Planet;

/** Broken plasma ribbons rotating around Kyanos in two inclined planes. */
public final class KyanosCoronaMesh implements GenericMesh{
    private final Planet planet;
    private final Mesh inner, outer;
    private final Shader shader;
    private final Mat3D transform = new Mat3D();

    public static void install(Planet planet){
        Prov<GenericMesh> original = planet.meshLoader;
        planet.meshLoader = () -> new MultiMesh(original.get(), new KyanosCoronaMesh(planet));
        if(planet.mesh != null) planet.mesh = new MultiMesh(planet.mesh, new KyanosCoronaMesh(planet));
        planet.clipRadius = Math.max(planet.clipRadius, planet.radius * 1.48f);
    }

    public KyanosCoronaMesh(Planet planet){
        this.planet = planet;
        FloatSeq innerVerts = new FloatSeq(), outerVerts = new FloatSeq();
        Color innerHot = Color.valueOf("f2b8ff"), innerCool = Color.valueOf("9944d9");
        Color outerHot = Color.valueOf("d389ff"), outerCool = Color.valueOf("6d3eb7");
        arc(innerVerts, 12f, 139f, 1.105f, 1.175f, 0f, innerHot, innerCool);
        arc(innerVerts, 165f, 273f, 1.105f, 1.175f, 80f, innerHot, innerCool);
        arc(innerVerts, 295f, 352f, 1.105f, 1.175f, 150f, innerHot, innerCool);
        for(int i = 0; i < 9; i++){
            float angle = i * 40f + 13f;
            flare(innerVerts, angle, 1.205f + 0.055f * Mathf.sinDeg(i * 89f), innerHot);
        }
        arc(outerVerts, 25f, 99f, 1.27f, 1.34f, 30f, outerHot, outerCool);
        arc(outerVerts, 121f, 245f, 1.27f, 1.34f, 100f, outerHot, outerCool);
        arc(outerVerts, 270f, 334f, 1.27f, 1.34f, 205f, outerHot, outerCool);
        inner = mesh(innerVerts);
        outer = mesh(outerVerts);
        shader = new Shader(
                "attribute vec4 a_position;\nattribute vec3 a_normal;\nattribute vec4 a_color;\n"
                + "uniform mat4 u_proj;\nuniform mat4 u_trans;\nuniform float u_time;\n"
                + "varying vec4 v_color;\nvoid main(){\n"
                + "v_color = vec4(a_color.rgb, a_color.a * (0.84 + 0.16 * sin(u_time)));\n"
                + "gl_Position = u_proj * u_trans * a_position;\n}",
                "varying vec4 v_color;\nvoid main(){ gl_FragColor = v_color; }"
        );
    }

    private static Mesh mesh(FloatSeq vertices){
        Mesh mesh = new Mesh(true, vertices.size / 7, 0,
                VertexAttribute.position3, VertexAttribute.normal, VertexAttribute.color);
        mesh.setVertices(vertices.toArray());
        return mesh;
    }

    private static void arc(FloatSeq out, float start, float end, float inner, float outer,
                            float phase, Color hot, Color cool){
        int segments = Math.max(12, (int)((end - start) / 3f));
        for(int i = 0; i < segments; i++){
            float t1 = i / (float)segments, t2 = (i + 1f) / segments;
            float a1 = Mathf.lerp(start, end, t1), a2 = Mathf.lerp(start, end, t2);
            float f1 = Math.min(1f, Math.min(t1, 1f - t1) * 9f);
            float f2 = Math.min(1f, Math.min(t2, 1f - t2) * 9f);
            vertex(out, a1, inner + 0.014f * Mathf.sinDeg(a1 * 5f + phase), hot, 0.62f * f1);
            vertex(out, a1, outer + 0.025f * Mathf.sinDeg(a1 * 3f + phase), cool, 0.12f * f1);
            vertex(out, a2, outer + 0.025f * Mathf.sinDeg(a2 * 3f + phase), cool, 0.12f * f2);
            vertex(out, a1, inner + 0.014f * Mathf.sinDeg(a1 * 5f + phase), hot, 0.62f * f1);
            vertex(out, a2, outer + 0.025f * Mathf.sinDeg(a2 * 3f + phase), cool, 0.12f * f2);
            vertex(out, a2, inner + 0.014f * Mathf.sinDeg(a2 * 5f + phase), hot, 0.62f * f2);
        }
    }

    private static void vertex(FloatSeq out, float angle, float radius, Color color, float alpha){
        out.add(Mathf.cosDeg(angle) * radius, 0f, Mathf.sinDeg(angle) * radius);
        out.add(0f, 1f, 0f);
        out.add(Color.toFloatBits(color.r, color.g, color.b, alpha));
    }

    private static void flare(FloatSeq out, float angle, float tipRadius, Color color){
        vertex(out, angle - 5f, 1.035f, color, 0.36f);
        vertex(out, angle + 5f, 1.035f, color, 0.36f);
        vertex(out, angle, tipRadius, color, 0f);
    }

    @Override public void render(PlanetParams params, Mat3D projection, Mat3D planetTransform){
        boolean wasBlending = Gl.isEnabled(Gl.blend);
        boolean wasCulling = Gl.isEnabled(Gl.cullFace);
        int previousSrc = Gl.getInt(Gl.blendSrcRgb), previousDst = Gl.getInt(Gl.blendDstRgb);
        Gl.enable(Gl.blend);
        Gl.disable(Gl.cullFace);
        Gl.blendFunc(Gl.srcAlpha, Gl.one);
        try{
            shader.bind();
            shader.setUniformMatrix4("u_proj", projection.val);
            shader.setUniformf("u_time", Time.globalTime / 45f);
            draw(inner, planetTransform, 28f, Time.globalTime / 90f);
            draw(outer, planetTransform, -38f, -Time.globalTime / 140f);
        }finally{
            Gl.blendFunc(previousSrc, previousDst);
            if(!wasBlending) Gl.disable(Gl.blend);
            if(wasCulling) Gl.enable(Gl.cullFace);
        }
    }

    private void draw(Mesh mesh, Mat3D planetTransform, float tilt, float rotation){
        transform.set(planetTransform).rotate(Vec3.Z, tilt).rotate(Vec3.Y, rotation)
                .scale(planet.radius, planet.radius, planet.radius);
        shader.setUniformMatrix4("u_trans", transform.val);
        mesh.render(shader, Gl.triangles);
    }

    @Override public void dispose(){ inner.dispose(); outer.dispose(); shader.dispose(); }
}
