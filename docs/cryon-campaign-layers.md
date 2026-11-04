# 克莱恩双层战役

地下当前仅展示第一关“冰下前哨”。后续五关暂不显示在星图或科技树中，通关第一关也不会继续开放下一关；地图文件和原有存档数据保留。

战役星图列表只显示一个 **克莱恩** 入口，顶部使用 **地表 / 地下** 按钮切换视图。两层共用同一星图坐标、轨道与自转，每次显示当前层的球体。地表使用现有克莱恩战役，地下显示冰层下的海洋世界。原版星图和随模组附带的「单位发射台」库星图均支持切换；选择运输目标时遵循该界面原有的区块限制。

两层共用克莱恩科技树与研究解锁，研究资源按游戏原有机制汇总。区块、地图和存档分别保存：地表继续使用 `cryon-cryon`，地下使用 `cryon-cryon-depths`。每层记住切换前选中的区块。

地下采用默认的固定路线：

| 顺序 | 关卡 | ID | 目标 |
| --- | --- | --- | --- |
| 1 | 冰下前哨 | `cryon-depths-outpost` | 生存 12 波 |
| 2 | 潮汐海峡 | `cryon-depths-strait` | 生存 18 波 |
| 3 | 断裂群岛 | `cryon-depths-archipelago` | 摧毁敌方核心 |
| 4 | 深蓝回廊 | `cryon-depths-corridor` | 生存 30 波 |
| 5 | 深渊热泉 | `cryon-depths-vents` | 摧毁敌方核心 |
| 6 | 海底枢纽 | `cryon-depths-nexus` | 摧毁敌方核心 |

占领前一关后自动开放下一关。上述路线和地图文件沿用已有版本，本次不重新生成或调整地图；资源、敌人波次、基地和电源由制图者布置。旧关卡标题“深渊热泉”保留兼容ID，环境已取消地热喷口。

地下现在具有寒冷与深海环境，敌人为Crux。仅允许生产潜艇和核心机。潜艇沿用克莱恩的单位投影场、二级重构与三级至五级通用装配厂，科技树从共用根节点分出独立的潜艇路线：Minimus → Danae → Pammelas → Schmidti → Sloani。深海船坞已删除，六张预置战役地图不再放置该建筑。

删除船坞时仅清除其建筑与失效电力连线，未重新生成六张地图的地形。`tools/ShipyardRemovalCheck.java` 对照删除前的地形、矿脉、其他建筑与库存摘要（`tools/shipyard-removal-baseline.properties`），检查打包后的地图及保存重载结果。

生产配方、深潜指令、重型鱼雷、水雷、动力泵及编辑器地块见[深海内容说明](deepsea-submarines.md)。

桌面版构建：`bash ./gradlew jar --offline`。

地图生成工具 `tools/GenerateCryonDepthsMaps.java` 为历史工具，本次未运行。地图文件由制图者维护。

验证工具：`tools/DeepSeaContentCheck.java` 验证生产消耗、环境限制、动力泵、深潜、鱼雷和水雷；`tools/DeepSeaRenderCheck.java` 在临时测试场景检查贴图、深潜按钮和存档，不写入战役地图。`tools/CryonLayersRenderCheck.java` 用于星图切换检查。

地下保留坦特罗斯风格的红蓝海床、海藻和珊瑚，新增30种专属地块、植物与地质块，包括富钛矿床、富镍矿床及镍质结核层。所有矿物沿用克莱恩已有资源。环境只有悬浮颗粒与气泡，无地热喷口。

载入已有地下战役时保留建筑、库存、矿层和进度；旧暗沙替换为不产沙的深海淤泥，旧热泉替换为无地热属性的玄武岩兼容地块。地表继续使用自己的环境。

环境贴图工具：`tools/generate_cryon_depths_sprites.py`；新增地块与潜艇换色工具：`tools/generate_deepsea_content_assets.py`。
