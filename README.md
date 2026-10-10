# 原版精修 (Vanilla Refit)

在原版基础上做建筑扩展与细节打磨的模组：竖半砖、竖楼梯、混合半砖，以及一批更顺手的原版改动。
所有新增形态都由方块状态控制，默认关闭，中途加入模组不会让存档里已有的方块变形。

| 项目 | 值 |
| --- | --- |
| 支持版本 | 1.21.1 / 1.21.4 / 1.21.8 / 1.21.10（NeoForge）；1.20.1（Forge） |
| 模组 id | `btsdhz_original` |
| 运行环境 | 客户端与服务端均可 |

每个游戏版本一份 jar，按自己的游戏版本下载对应那一份即可：

| 游戏版本 | 加载器 | 说明 |
| --- | --- | --- |
| 1.21.1 | NeoForge 21.1.248 及以上 | 开发主线（本仓库 `main`） |
| 1.20.1 | Forge 47.4.26 及以上（Java 17） | 已适配 |
| 1.21.4 | NeoForge 21.4.158 及以上 | 已适配 |
| 1.21.8 | NeoForge 21.8.54 及以上 | 已适配 |
| 1.21.10 | NeoForge 21.10.64 及以上 | 已适配 |

功能口径与配置项在五个版本上保持一致（见下方"兼容范围"）。

## 台阶与楼梯

- 台阶和楼梯都可以竖着放。按 `V`（可在按键设置里改）切换放置逻辑，台阶与楼梯各自记忆，并且会跟随存档保存：
  - **默认**：点方块面靠外区域竖放，点中央区域平放；手持时显示分割辅助线（台阶为对角线、楼梯为十字线，只在完整面上绘制）。
  - **混合**：面中央的正方形区域内平放，其余位置竖放，正方形比例可在配置里调整。
  - **紧贴**：按点击的那个面决定竖放朝向。
  - **原版**：保留原版放置逻辑（台阶的混合模式仍然可用）。
- **混合半砖**：同一格里叠放两种材质的半砖；潜行时只拆掉其中一半，不潜行则是原版式整体拆除。
- 其它模组的台阶与楼梯默认也能竖放（竖形态的模型由客户端按本模组的模板运行时生成），可以在配置里关掉，或用方块名单逐个排除。

## 火把、灯笼、栅栏与墙

- 火把、灵魂火把、红石火把、灯笼、灵魂灯笼可以放在下台阶上，模型、碰撞箱与粒子一起下移半格贴合台阶表面；灯笼也可以挂在上台阶下方。
- 下台阶上的栅栏与墙会整体下移半格，并与周围的栅栏/墙正常连接；墙在方块上方的满高判定也已适配。
- 判定基于原版方块与标签，因此其它模组按原版方式实现的红石火把、灯笼等也能沿用这套贴合。

## 玻璃板与铁栏杆

- 玻璃板与铁栏杆拆成 12 个部件（东西南北各分上下两半，加上四个角的水平面片），大面积铺开时中间的竖直面片会自动隐藏，拼出整块玻璃幕墙的效果。
- 连接规则在原版基础上扩展（上下两半 + 四个角的面片），并且在任何摆放顺序下都会得到相同结果。

## 兼容范围

- **原版方块**：完整适配。
- **楼梯、台阶**：其它模组的默认也生效（模型在客户端运行时按模板生成），可用 `allowModdedSlabs` / `allowModdedStairs` 整体关掉，或用 `blocklist` 逐个排除。
- **墙、栅栏**：只要继承原版类，都适用「贴台阶」的位移与跨台阶连接。
- **玻璃板、铁栏杆**：目前只适配原版的（其它模组的贴图命名不保证，暂不适配）。
- **火把、灯笼**：按标签判定（`btsdhz_original:on_slab_torch` / `btsdhz_original:on_slab_lantern`），基于原版类构造的加进对应标签即可生效。

本模组不给方块加新 id，所有改动都发生在原版方块上：卸载后世界里的方块不会消失，只会退回原版的样子；中途加入模组也不会改变已放好的方块。

## 其它原版改动

- 玻璃、玻璃板等玻璃类方块可以用镐或斧加速挖掘。
- 拴绳可以在两个栅栏之间拉出一条微微下垂的悬链线；点击栅栏上的绳结即可解开并返还拴绳，拆除栅栏同样会解除连接。
- 铁砧：使用时不再逐渐损坏（只保留跌落受损）；用材料修复物品不再累积修复惩罚，修复比例由 25% 提高到 90%。
- 新增两个按键：爬行（默认 `Ctrl` 按住）与坐下（默认 `Z` 切换），都可在按键设置里更改。

## 配置

配置文件为 `config/btsdhz_original-common.toml`，所有开关都带中文注释，默认开启：

- `allowTorchLanternOnSlab`：火把/灯笼在下台阶上的贴合。
- `allowLanternUnderTopSlab`：灯笼悬挂在上台阶下。
- `slabCenterSquareRatio`：台阶放置逻辑「混合」中央正方形的大小。
- `allowModdedSlabs` / `allowModdedStairs`：其它模组的台阶 / 楼梯是否也能竖放（默认开启）。
- `blocklist`：方块名单，决定哪些方块允许加竖形态（`+*` / `-*` / `+minecraft:oak_slab` / `+somemod:*` / `+#minecraft:wooden_slabs`）。它只影响放置行为，不会减少已注册的方块状态。

## 构建

```bash
./gradlew build       # 打包，产物在 build/libs
./gradlew runData     # 重新生成方块状态与模型
./gradlew runClient   # 启动开发环境客户端
```

## 许可

MIT 许可证，详见 [LICENSE](LICENSE)。

---

## English

**Vanilla Refit** is a mod that refines vanilla building and behaviour, with one jar per game version:
Minecraft 1.21.1 / 1.21.4 / 1.21.8 / 1.21.10 on NeoForge, and Minecraft 1.20.1 on Forge. It adds
vertical slabs, vertical stairs and mixed slabs (with switchable placement modes), torches/lanterns/fences/walls
sitting flush on slabs, glass panes and iron bars rebuilt from 12 pieces (extended connection rules),
crawl/sit keybinds, a lead that can be strung between two fences, glass that can be mined faster with pickaxes or
axes, and anvil changes (no wear from use, no repair cost accumulation, 90% material repair).

Mod id: `btsdhz_original`. Client and server are both supported on every supported version.

Licensed under the MIT License, see [LICENSE](LICENSE).
