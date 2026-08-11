package example;

import arc.*;
import arc.graphics.gl.*;
import arc.math.*;
import arc.scene.ui.layout.Scl;
import arc.util.Time;
import mindustry.graphics.*;

import static mindustry.Vars.*;

public class FluxShieldShader extends Shader {

    public FluxShieldShader() {
        super(
                Shaders.getShaderFi("screenspace.vert"),
                Shaders.getShaderFi("flux_shield.frag")
        );
    }

    @Override
    public void apply() {
        setUniformf("u_dp", Scl.scl(1f));
        setUniformf("u_time", Time.time / Scl.scl(1f));
        setUniformf("u_offset",
                Core.camera.position.x - Core.camera.width / 2,
                Core.camera.position.y - Core.camera.height / 2
        );
        setUniformf("u_texsize", Core.camera.width, Core.camera.height);
        setUniformf("u_invsize", 1f / Core.camera.width, 1f / Core.camera.height);

        // You can add custom uniforms
        // setUniformf("u_fluxIntensity", 1.5f);
        // setUniformf("u_heat", paramEntity.shieldHeat / paramEntity.maxShieldHeat);
    }
}