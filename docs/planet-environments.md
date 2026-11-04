# 星球环境属性

- 克莱恩：冷；本土单位适配冷，不适配热。
- 阿拉维斯：热；单位默认适配热，克莱恩本土单位除外。
- 波菲拉：原版炽热（Env.scorching）和重结晶；18 种本土单位显式适配。
- 单位默认耐寒；埃里克尔单位不耐寒。克莱恩与波菲拉本土单位耐寒。
- 适配原版炽热的单位同时免疫普通热环境。克莱恩单位不默认免疫热。
- 重结晶默认不适配，波菲拉本土单位显式免疫。

炽热仍使用原版环境准入规则，不兼容的单位可能被引擎直接清除。热、冷状态不使用这个硬性规则。

热应激：移动70%、伤害65%、射速65%、建造60%、最大生命75%。
冷失效：移动60%、伤害80%、射速60%、建造60%、最大生命80%。
离开对应环境后，热/冷状态最多约1秒消退。重结晶会移除不适配单位并释放单位名额，在附近空地留下保留单位外形和朝向的水晶雕塑方块。雕塑为可破坏的中立建筑，按1～5格大小生成并保存到地图。若原位置被占用会寻找附近空地，不覆盖建筑。未适配原版炽热的单位仍受原版环境判定约束。

## 后续配置

`PlanetTraits.set(planet, Trait.heat / Trait.cold / Trait.recrystallization)` 配置星球属性。

地图 Rules.tags 可单独覆盖：

- `cryon-environment-heat`: `true` / `false`
- `cryon-environment-cold`: `true` / `false`
- `cryon-environment-recrystallization`: `true` / `false`

原版炽热由 Rules.env / Planet.defaultEnv 的 Env.scorching 位控制。

单位对热、冷、重结晶的适配使用对应状态的 immunities：heatStress、coldStress、crystallized；追加免疫即可显式适配。波菲拉单位同时取消 Env.scorching 禁用位。
