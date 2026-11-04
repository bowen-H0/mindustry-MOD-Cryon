# 先驱阵营

新增独立阵营 **先驱 / Pioneer**，固定 ID 为 `171`，主色为橙色 `#f28c28`，并提供对应的建筑、单位阵营调色板。地图编辑器可直接选择先驱作为放置阵营或地图玩家／敌人阵营；底部的“盟友阵营”设置也会列出先驱。

先驱默认沿用原生阵营关系。将其勾选为盟友后，与玩家互不攻击、保持独立控制权，并使用盟友系统的自主 AI；不会自动改写现有地图的玩家或敌人阵营。

独立箭锋徽标使用透明的 `sprites/team-pioneer.png`（62×62），同时用于阵营界面、文字图标和阵营投影器。可编辑源文件为 `tools/team-pioneer.svg`，通过 `python3 tools/generate_team_pioneer_icon.py` 重新导出 PNG。

验证沿用并扩展现有检查：`NormaIconCheck` 覆盖字体图集扩容后的徽标、独立图标编码及橙色调色板；`TeamFlagProjectorCheck` 覆盖投影纹理及扫描条绘制；`MapAlliancesCheck` 覆盖先驱盟友配置、独立所有权、地图和存档重载，以及将先驱设为玩家阵营。
