# 地图编辑器一键水下化

加载新版模组后，在地图编辑器打开 **菜单 → 一键水下化**，即可把当前地图的所有地板转换成铺有水面的海底地形，并启用水下环境规则。转换后仍需正常保存地图。

石质、泥沙、冰雪等地板分别转换为对应的海底材质；已有的12种海底实体地板使用原纹理与水面合成水下版本，保留其矿物掉落和地形属性。水域、红蓝菌毯分别转换为现有海底水域。岩浆、石油等液体也会转换为水域。

建筑、建筑配置和库存、墙体、矿脉覆盖层及出生点保持原样。不自动添加植物、矿脉、波次或深海战役的生产限制。

整次转换占用一个编辑器历史步骤：使用编辑器的撤销／重做按钮或快捷键，可以同时撤销／重做全图地板与环境规则。重复点击不会反复改动已经转换的地板，也不会增加空的撤销步骤。操作仅对当前编辑地图生效，不批量改写其他地图文件。

## 验证

`tools/UnderwaterEditorCheck.java` 加载打包后的模组，使用原生编辑器地块、撤销栈与地图保存格式，仅替换需要图形上下文的渲染器。覆盖全图水覆盖、材质与矿床属性、建筑与库存、矿脉与出生点、撤销／重做、重复点击、仅规则变更、保存重载和游戏状态保护。

水下地板的纹理生成在 `load()` 阶段解析水面和海底材质，并在合成前加载双方的实际纹理，不能等到 `init()` 才设置。桌面游戏先生成模组图集，再初始化地块；原先依赖 `init()` 会跳过合成，导致转换后显示缺失贴图。地块名称保持不变，已经转换并保存的地图无需重新转换。

`tools/UnderwaterTextureCheck.java` 按照桌面启动顺序，在内容初始化之前调用原生贴图生成器，使用游戏自带水面与打包后的海底 PNG，逐像素验证 12 种材质的全部 36 个变体，再打包图集并检查地板与编辑器图标。仅模拟 GPU 上传，不需要显示器；生成的左右对比图保存在 `/tmp/cryon-underwater-textures.png`。

```bash
mkdir -p /tmp/cryon-editor-test-classes
javac --release 17 -sourcepath '' \
  -cp /path/to/mindustry-assets/mindustry-linux-64-bit/jre/desktop.jar \
  -d /tmp/cryon-editor-test-classes tools/UnderwaterEditorCheck.java
java -cp /tmp/cryon-editor-test-classes:/path/to/mindustry-assets/mindustry-linux-64-bit/jre/desktop.jar \
  mindustry.editor.UnderwaterEditorCheck
javac --release 17 -sourcepath '' \
  -cp /path/to/mindustry-assets/mindustry-linux-64-bit/jre/desktop.jar \
  -d /tmp/cryon-editor-test-classes tools/UnderwaterTextureCheck.java
java -Xmx768m -cp /tmp/cryon-editor-test-classes:/path/to/mindustry-assets/mindustry-linux-64-bit/jre/desktop.jar \
  UnderwaterTextureCheck
```

验证在独立临时数据目录运行，不修改已安装模组、玩家存档或项目中的地图文件。上述纹理测试不覆盖实际 GPU 绘制、游戏内水面着色器或图形界面的按钮位置。
