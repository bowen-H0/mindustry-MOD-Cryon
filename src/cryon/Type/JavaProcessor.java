package cryon.Type;

import arc.Events;
import arc.Core;
import arc.scene.ui.layout.Table;
import arc.util.*;
import arc.util.io.*;
import cryon.Script.*;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.type.Category;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;

import java.io.*;

import static mindustry.Vars.*;

/**
 * Java 处理器：类似世界处理器，每帧调用选定的 MapScript。
 * 点击方块 -> 打开选择 UI -> 选 Java 类。
 */
public class JavaProcessor extends Block {
    private boolean sectorCaptured;

    public JavaProcessor(String name) {
        super(name);
        update = true;
        solid = true;
        configurable = true;
        saveConfig = true;
        copyConfig = true;
        group = mindustry.world.meta.BlockGroup.logic;
        category = Category.logic;

        // 配置值 = 脚本 id，空字符串表示不运行任何脚本
        config(String.class, (JavaProcessorBuild b, String id) -> {
            if(b.script instanceof NuclearCleansingScript && id != null && id.startsWith("nuclear-cleansing:")){
                ((NuclearCleansingScript)b.script).configure(id.substring("nuclear-cleansing:".length()));
            }else{
                b.setScript(id);
            }
        });
        configClear((JavaProcessorBuild b) -> b.setScript(""));
        Events.on(EventType.SectorCaptureEvent.class, e -> sectorCaptured = true);
        Events.on(EventType.WorldLoadEvent.class, e -> sectorCaptured = false);

    }

    public class JavaProcessorBuild extends Building {
        public String scriptId = "";
        public MapScript script;
        private boolean failed;

        public void setScript(String id) {
            if (id == null) id = "";
            if (scriptLocked() && !id.equals(scriptId)) return;
            if (id.equals(scriptId) && (script != null || id.isEmpty())) return;
            disposeScript();
            scriptId = id;
            failed = false;
            script = ScriptRegistry.create(id);
            if (script != null) safe(() -> script.attach(this));
        }

        private void disposeScript() {
            if (script != null) {
                MapScript s = script;
                safe(s::removed);
                script = null;
            }
        }

        private void safe(Runnable r) {
            try {
                r.run();
            } catch (Throwable t) {
                failed = true;
                Log.err("[JavaProcessor] 脚本 '" + scriptId + "' 出错, 已停止运行", t);
            }
        }

        @Override
        public void updateTile() {
            if (script != null && !failed) safe(script::update);
        }

        public boolean scriptLocked(){
            return cryon.Story.IntrusionMap.active()
                    && (script instanceof VirusIntrusionScript || script instanceof cryon.Script.OrbitalStrikeScript);
        }

        @Override
        public void buildConfiguration(Table table) {
            if(scriptLocked()){
                script.buildUI(table);
                return;
            }
            table.table(Styles.black6, t -> {
                t.defaults().pad(4f);
                t.button(Icon.list, Styles.cleari, 40f, this::showSelectDialog).size(48f);
                t.label(() -> {
                    ScriptRegistry.Entry e = ScriptRegistry.entries.get(scriptId);
                    String n = e == null ? Core.bundle.get("script.selector.unselected") : e.localizedName();
                    return n + (failed ? Core.bundle.get("script.selector.error-suffix") : "");
                }).padRight(6f);
                if (script != null) script.buildUI(t);
            });
        }

        private void showSelectDialog() {
            BaseDialog d = new BaseDialog(Core.bundle.get("script.selector.title"));
            d.cont.pane(p -> {
                p.defaults().growX().height(50f).pad(3f).minWidth(300f);
                p.button(Core.bundle.get("script.selector.none"), Styles.flatTogglet, () -> {
                    configure("");
                    d.hide();
                }).update(b -> b.setChecked(scriptId.isEmpty()));
                p.row();
                for (var e : ScriptRegistry.entries.values()) {
                    p.button(e.localizedName() + "  [gray](" + e.id + ")", Styles.flatTogglet, () -> {
                        configure(e.id);
                        d.hide();
                    }).update(b -> b.setChecked(e.id.equals(scriptId)));
                    p.row();
                }
            }).grow();
            d.addCloseButton();
            d.show();
        }

        @Override
        public Object config() {
            return scriptId;
        }

        @Override
        public void onRemoved() {
            disposeScript();
            super.onRemoved();
        }

        @Override
        public byte version() {
            return 1;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.str(scriptId);
            // 脚本数据带长度前缀，脚本改动格式时不会破坏整个存档
            byte[] data = new byte[0];
            if (script != null) {
                try {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    script.write(new Writes(new DataOutputStream(bos)));
                    data = bos.toByteArray();
                } catch (Throwable t) {
                    Log.err("[JavaProcessor] 保存脚本状态失败", t);
                }
            }
            write.i(data.length);
            write.b(data);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            if (revision < 1) return;
            String id = read.str();
            int len = read.i();
            byte[] data = read.b(len);

            setScript(id);
            if (script != null && len > 0) {
                try {
                    script.read(new Reads(new DataInputStream(new ByteArrayInputStream(data))));
                } catch (Throwable t) {
                    Log.err("[JavaProcessor] 读取脚本状态失败", t);
                }
            }
        }
        // ===== 无敌：忽略所有伤害 =====
        @Override
        public void damage(float damage) {}

        @Override
        public void damage(Team source, float damage) {}

        @Override
        public void damagePierce(float amount) {}

        @Override
        public void damagePierce(float amount, boolean withEffect) {}
        @Override
        public void update() {
            if (team == Team.derelict || sectorCaptured) {
                if (!net.client()) kill();   // 只在服务端/单机执行，客户端等同步
                return;
            }
            super.update();
        }
    }
}
