package cryon.Script;

import arc.scene.ui.layout.Table;
import arc.util.io.*;
import cryon.Type.JavaProcessor.JavaProcessorBuild;
import mindustry.game.Team;
import arc.audio.Sound;
/**
 * Java 处理器脚本基类。
 * 继承它、重写需要的方法，然后在 ScriptRegistry 里注册，
 * 就能在 Java 处理器的 UI 里选择。
 */
public abstract class MapScript {
    /** 所属的处理器建筑（attach 之后可用） */
    public JavaProcessorBuild build;

    /** 播放定点音效（带空值保护） */
    public static void snd(Sound s, float x, float y, float volume, float pitch) {
        if(s != null) s.at(x, y, pitch, volume);
    }

    /** 由处理器调用：绑定建筑并初始化 */
    public final void attach(JavaProcessorBuild build) {
        this.build = build;
        init();
    }

    /** 初始化（被选中 / 读档后调用一次，在 read() 之前） */
    protected void init() {}

    /** 每帧调用（对应处理器的 updateTile），主要逻辑写这里 */
    public void update() {}

    /** 脚本被切换掉 / 处理器被移除时调用，用来清理 */
    public void removed() {}

    /** 保存脚本自己的状态（存档用） */
    public void write(Writes write) {}

    /** 读取脚本自己的状态；write 写什么，这里按同样顺序读 */
    public void read(Reads read) {}

    /** 可选：在处理器配置面板里追加自定义 UI */
    public void buildUI(Table table) {}

    // ---------- 便捷方法 ----------
    protected Team team() { return build.team; }
    protected float delta() { return build.delta(); }
    protected float x() { return build.x; }
    protected float y() { return build.y; }
}
