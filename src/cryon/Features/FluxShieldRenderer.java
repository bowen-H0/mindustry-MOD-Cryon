package cryon.Features;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.gl.FrameBuffer;
import cryon.Type.FluxBarrier;

public class FluxShieldRenderer {

    private static FrameBuffer fluxBuffer;
    private static boolean scheduled;

    public static void drawFluxShields() {
        if(scheduled) return;
        scheduled = true;

        Draw.drawRange(FluxBarrier.layerFluxShield, 1f,
                () -> {
                    if(FluxBarrier.fluxShader == null) FluxBarrier.fluxShader = new FluxShieldShader();
                    if(fluxBuffer == null) fluxBuffer = new FrameBuffer();
                    if(fluxBuffer.getWidth() != Core.graphics.getWidth()
                            || fluxBuffer.getHeight() != Core.graphics.getHeight()){
                        fluxBuffer.resize(Core.graphics.getWidth(), Core.graphics.getHeight());
                    }
                    fluxBuffer.begin(Color.clear);
                },
                () -> {
                    fluxBuffer.end();
                    fluxBuffer.blit(FluxBarrier.fluxShader);
                    scheduled = false;
                }
        );
    }
}