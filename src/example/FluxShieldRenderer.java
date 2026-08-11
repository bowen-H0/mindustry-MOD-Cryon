package example;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.gl.FrameBuffer;

public class FluxShieldRenderer {

    // 用自己独立的 FrameBuffer，不要复用 renderer.effectBuffer
    // (那个是引擎/原版护盾等其他后处理效果共用的，抢用会导致 begin() 冲突崩溃)
    private static FrameBuffer fluxBuffer;

    public static void drawFluxShields() {
        if (FluxBarrier.fluxShader == null) return;

        if (fluxBuffer == null) {
            fluxBuffer = new FrameBuffer();
        }

        // 跟随屏幕分辨率变化调整大小
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