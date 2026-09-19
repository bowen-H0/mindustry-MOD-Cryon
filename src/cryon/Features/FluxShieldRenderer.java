package cryon.Features;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.gl.FrameBuffer;
import cryon.Type.FluxBarrier;

public class FluxShieldRenderer {

    private static FrameBuffer fluxBuffer;

    public static void drawFluxShields() {
        if (FluxBarrier.fluxShader == null) return;

        if (fluxBuffer == null) {
            fluxBuffer = new FrameBuffer();
        }

        if (fluxBuffer.getWidth() != Core.graphics.getWidth() || fluxBuffer.getHeight() != Core.graphics.getHeight()) {
            fluxBuffer.resize(Core.graphics.getWidth(), Core.graphics.getHeight());
        }

        Draw.drawRange(FluxBarrier.layerFluxShield, 1f,
                () -> fluxBuffer.begin(Color.clear),
                () -> {
                    fluxBuffer.end();
                    fluxBuffer.blit(FluxBarrier.fluxShader);
                }
        );
    }
}