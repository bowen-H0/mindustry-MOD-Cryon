package cryon.Script;

import arc.graphics.Color;
import arc.math.Angles;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.io.*;
import cryon.Fx.AstroFx;
import mindustry.ui.Styles;

/**
 * 天文观测：在处理器所在位置持续播放浑天仪观测阵，
 * 随机划过彗星，并周期性触发超新星脉冲。
 * 配置面板里的按钮可切换色调（存档保存）。
 */
public class AstroObservatoryScript extends MapScript {
    static final Color[] palette = {
            Color.valueOf("6fa8ff"), Color.valueOf("c18bff"),
            Color.valueOf("ffcf6b"), Color.valueOf("7dffd0")
    };
    static final String[] paletteNames = {"深空蓝", "星云紫", "恒星金", "极光绿"};

    private float observeTimer = 1e6f;   // 首帧立即触发
    private float cometTimer = 60f;
    private float novaTimer = 600f;
    private int colorIndex = 0;

    @Override
    public void update() {
        Color c = palette[colorIndex];
        float d = delta();

        // 观测阵：寿命 240，每 200 帧续一次，重叠部分靠淡入淡出无缝衔接
        observeTimer += d;
        if (observeTimer >= 200f) {
            observeTimer = 0f;
            AstroFx.observatory.at(x(), y(), 0f, c);
        }

        // 彗星：随机方位切入，穿过方块附近
        cometTimer -= d;
        if (cometTimer <= 0f) {
            cometTimer = Mathf.random(120f, 300f);
            float a = Mathf.random(360f);
            float sx = x() + Angles.trnsx(a, 300f), sy = y() + Angles.trnsy(a, 300f);
            AstroFx.comet.at(sx, sy, a + 180f + Mathf.range(25f), c);
        }

        // 超新星脉冲：约每 15 秒
        novaTimer -= d;
        if (novaTimer <= 0f) {
            novaTimer = 900f;
            AstroFx.nova.at(x(), y(), 0f, c);
        }
    }

    @Override
    public void buildUI(Table table) {
        table.button("色调", Styles.flatt, () -> colorIndex = (colorIndex + 1) % palette.length)
                .size(80f, 40f);
        table.label(() -> paletteNames[colorIndex]);
    }

    @Override
    public void write(Writes write) {
        write.b(colorIndex);
    }

    @Override
    public void read(Reads read) {
        colorIndex = Mathf.clamp(read.b(), 0, palette.length - 1);
    }
}