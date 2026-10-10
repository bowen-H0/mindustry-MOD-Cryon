package cryon.Script;

import arc.scene.ui.layout.Table;
import arc.util.Log;
import arc.util.io.*;
import mindustry.gen.Call;

import static mindustry.Vars.state;

/** 示例：继承 MapScript 写自己的逻辑 */
public class ExampleScript extends MapScript {
    private float timer;
    private int count;

    @Override
    protected void init() {
        Log.info("[ExampleScript] 初始化 @", build.tile.pos());
    }

    @Override
    public void update() {
        timer += delta();
        if (timer >= 60f * 5f) {   // 每 5 秒
            timer = 0f;
            count++;
            Call.sendMessage("[accent]示例脚本触发第 " + count + " 次, 当前波数 " + state.wave);

            // 这里可以控制地图: state.rules.xxx、生成单位、改地形、设置规则等
        }
    }

    @Override
    public void buildUI(Table table) {
        table.row();
        table.label(() -> "触发次数: " + count);
    }

    @Override
    public void write(Writes write) {
        write.f(timer);
        write.i(count);
    }

    @Override
    public void read(Reads read) {
        timer = read.f();
        count = read.i();
    }
}