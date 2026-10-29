# 原版精修 (Vanilla Refit)

在原版基础上做建筑扩展与细节打磨的模组：竖半砖、竖楼梯、混合半砖，以及一批更顺手的原版改动。
所有新增形态都由方块状态控制，默认关闭，中途加入模组不会让存档里已有的方块变形。

## 支持版本

| 游戏版本 | 加载器 | 说明 |
| --- | --- | --- |
| 1.21.1 | NeoForge 21.1.248 及以上 | 主要维护版本 |
| 1.20.1 | Forge 47.4.26 及以上（Java 17） | 已适配 |
| 1.21.4 | NeoForge 21.4.158 及以上 | 已适配 |
| 1.21.8 | NeoForge 21.8.54 及以上 | 已适配 |
| 1.21.10 | NeoForge 21.10.64 及以上 | 已适配 |

每个游戏版本使用一份独立 jar。客户端与服务端都需要安装。

模组 id 为 `btsdhz_original`。功能与配置项在五个版本上保持一致。

## 台阶与楼梯

- 台阶和楼梯都可以竖着放。按 `V`（可在按键设置中修改）切换放置逻辑；台阶与楼梯分别记忆模式，并随存档保存。
- **默认**：点击方块顶面或底面时竖放，点击水平位置决定竖半砖的朝向；点击侧面使用原版放置行为。
- **混合**：只适用于台阶。在“默认”的基础上，将顶面或底面中央的正方形区域改为平放，正方形比例可在配置中调整。
- **紧贴**：按点击的那个面决定放置位置与朝向。台阶可以从顶面、底面和四个侧面放置；目前楼梯没有这一档。
- **原版**：恢复原版放置逻辑；台阶仍保留混合半砖功能。
- 楼梯目前只有“默认”和“原版”两档。竖楼梯的侧向放置不属于现有模式，需要从顶面或底面放置。
- 手持台阶或楼梯时，会在完整且紧贴的可用表面上显示分割辅助线：台阶为对角线 X，楼梯为十字。原版模式不绘制额外辅助线。
- **混合半砖**：同一格内可叠放两种材质的半砖。潜行时只拆除一半，不潜行时按原版逻辑整体拆除。
- 其它模组的台阶与楼梯默认也能竖放。竖形态在客户端运行时生成，可在配置中整体关闭，或用方块名单逐个排除。

## 火把、灯笼、栅栏与墙

- 火把、灵魂火把、红石火把、灯笼和灵魂灯笼可以放在下半台阶上；模型、碰撞箱与粒子会一起下移半格，贴合台阶上表面。
- 灯笼与灵魂灯笼也可以挂在上半台阶下方，并整体上移半格贴合底面。
- 下半台阶上的栅栏与墙会整体下移半格；上半台阶下方的栅栏与墙会整体上移半格。
- 栅栏和墙的模型、碰撞箱、准星判定、跨台阶连接与拴绳连接均已适配。
- 火把和灯笼按标签判定；基于原版类实现并加入相应标签的模组方块也能使用这套贴合。

## 玻璃板与铁栏杆

- 原版玻璃板与铁栏杆由 12 个部件组成：东西南北各分上下两半，并额外包含四个角的水平面片。
- 大面积铺开时会隐藏内部面片，拼出连续玻璃幕墙或栏杆的效果。
- 连接规则在原版基础上扩展，并保证最终结果不受摆放顺序影响。
- 玻璃板和铁栏杆目前不参与火把、灯笼、栅栏、墙所使用的“贴台阶位移”。放在台阶附近时仍保持原版整格高度。

## 兼容范围

- **原版方块**：完整适配。
- **楼梯、台阶**：其它模组中继承原版 `StairBlock` / `SlabBlock` 的方块默认可以竖放。可用 `allowModdedSlabs` / `allowModdedStairs` 整体关闭，或用 `blocklist` 逐个排除。
- **墙、栅栏**：继承原版类的方块适用贴台阶位移与跨台阶连接。
- **玻璃板、铁栏杆**：目前只适配原版方块；第三方玻璃板和铁栏杆保持原版行为。
- **火把、灯笼**：按标签判定。对应标签为 `btsdhz_original:on_slab_torch` 与 `btsdhz_original:on_slab_lantern`；基于原版类构造的模组方块加入标签后即可生效。

### 与其它模组的联动

- **机械动力（Create）对称之杖**：竖半砖、竖楼梯和组合半砖支持镜像与旋转。组合半砖会连同两种材质一起搬运；拆除或潜行半拆时，镜像格也会同步处理。
- **机械动力破坏事件**：组合半砖会正常发出方块破坏事件，依赖该事件的模组联动不会失效。
- **结构方块与 WorldEdit**：竖半砖、竖楼梯及其连接方向会通过标准的方块状态镜像、旋转入口正确转换。

这些联动不需要额外配置。对应模组不存在或接口不可用时，相关兼容会静默跳过，不影响其它功能。

本模组不给方块增加新 id，所有改动都发生在已有方块上：卸载后世界里的方块不会消失，只会退回原版外观；中途加入模组也不会改变已经放好的方块。

## 其它原版改动

- 玻璃、玻璃板等玻璃类方块可以用镐或斧加速挖掘。
- 拴绳可以在两个栅栏之间拉出一条微微下垂的悬链线；点击栅栏上的绳结可以解开并返还拴绳，拆除栅栏也会解除连接。
- 铁砧使用时不再逐渐损坏，只保留跌落造成的损伤；用材料修复物品不再累积惩罚，修复比例由 25% 提高到 90%。
- 新增两个按键：爬行（默认 `Ctrl` 按住）与坐下（默认 `Z` 切换），都可以在按键设置中更改。

## 配置

配置文件为 `config/btsdhz_original-common.toml`，所有注释均为中文。

- `allowTorchLanternOnSlab`：火把与灯笼在下半台阶上的贴合，默认开启。
- `allowLanternUnderTopSlab`：灯笼悬挂在上半台阶下方，默认开启。
- `slabCenterSquareRatio`：“混合”放置模式中央正方形边长占方块面边长的比例，默认 `0.5`。
- `allowModdedSlabs` / `allowModdedStairs`：其它模组的台阶 / 楼梯是否也能竖放，默认开启。
- `blocklist`：方块名单，决定哪些方块允许生成本模组形态。支持 `+*`、`-*`、单个方块 id、整个命名空间和方块标签。它只影响放置行为，不会减少已经注册的方块状态。

游戏内可使用 `/btsdhz_original blocklist` 查看当前名单与生效统计。

## 构建

```bash
./gradlew build       # 打包，产物在 build/libs
./gradlew runData     # 重新生成方块状态与模型
./gradlew runClient   # 启动开发环境客户端
```

## 许可

MIT 许可证，详见 [LICENSE](LICENSE)。

---

# Vanilla Refit

A building-focused expansion and quality-of-life mod for vanilla Minecraft. It adds vertical slabs, vertical stairs, combined-material slabs, and a set of smaller changes that make building and block interaction feel more complete.

Every new form is represented through block-state properties whose vanilla-compatible default is disabled. Adding the mod to an existing world does not change blocks that are already placed.

## Supported versions

| Minecraft | Loader | Notes |
| --- | --- | --- |
| 1.21.1 | NeoForge 21.1.248 or newer | Primary target |
| 1.20.1 | Forge 47.4.26 or newer (Java 17) | Supported |
| 1.21.4 | NeoForge 21.4.158 or newer | Supported |
| 1.21.8 | NeoForge 21.8.54 or newer | Supported |
| 1.21.10 | NeoForge 21.10.64 or newer | Supported |

Each game version uses its own jar. Install the mod on both the client and the server.

Mod id: `btsdhz_original`. Features and configuration options are kept consistent across all supported versions.

## Slabs and stairs

- Slabs and stairs can be placed vertically. Press `V` (rebindable) to switch placement modes. Slabs and stairs remember their modes separately, and the selection is saved with the world.
- **Default**: clicking the top or bottom face places a piece vertically. The horizontal click position determines the vertical slab orientation. Side clicks use vanilla placement.
- **Blend**: slabs only. In addition to Default behavior, the central square on a top or bottom face places a flat slab. Its size is configurable.
- **Flush**: placement follows the clicked face. Slabs can be placed from the top, bottom, or any side face. Stairs do not currently have this mode.
- **Original**: restore vanilla placement. Combined-material slabs remain available for slabs.
- Stairs currently provide only Default and Original modes. Vertical stairs cannot currently be selected through a side click; place them from a top or bottom face.
- While holding a slab or stair, guide lines are shown on complete, flush placement surfaces: an X for slabs and a cross for stairs. Original mode adds no extra guides.
- **Combined-material slabs**: place two slab materials in one block. Sneak-mine to remove only one half; mining normally removes the combined block using vanilla-style behavior.
- Slabs and stairs from other mods are supported by default. Vertical forms are generated at runtime and can be disabled globally or excluded per block.

## Torches, lanterns, fences, and walls

- Torches, soul torches, redstone torches, lanterns, and soul lanterns sit flush on bottom slabs. Their models, collision shapes, and particles all shift down by half a block.
- Lanterns and soul lanterns can also hang beneath top slabs. Their models and collision shapes shift up by half a block to meet the slab's lower face.
- Fences and walls shift down by half a block on bottom slabs and shift up by half a block beneath top slabs.
- Their models, collision shapes, targeting, cross-slab connections, and lead connections are adapted together.
- Torches and lanterns are detected through tags. Modded blocks based on the vanilla classes can opt in by adding the corresponding tag.

## Glass panes and iron bars

- Vanilla glass panes and iron bars are assembled from 12 parts: the four horizontal directions split into upper and lower halves, plus four horizontal corner pieces.
- Inner faces are hidden in large surfaces, producing continuous glass walls and bar structures.
- Connection rules extend the vanilla behavior and produce the same result regardless of placement order.
- Glass panes and iron bars do not currently use the half-block slab alignment applied to torches, lanterns, fences, and walls. They retain vanilla full-block height near slabs.

## Compatibility

- **Vanilla blocks**: fully supported.
- **Slabs and stairs**: blocks from other mods that extend vanilla `SlabBlock` or `StairBlock` support vertical placement by default. Use `allowModdedSlabs` / `allowModdedStairs` to disable this globally, or `blocklist` to exclude individual blocks.
- **Walls and fences**: blocks extending the vanilla classes support slab alignment and cross-slab connections.
- **Glass panes and iron bars**: vanilla blocks only. Modded panes and bars retain vanilla behavior.
- **Torches and lanterns**: detected through `btsdhz_original:on_slab_torch` and `btsdhz_original:on_slab_lantern`. Modded blocks based on the vanilla classes can opt in through these tags.

### Mod integrations

- **Create Symmetry Wand**: vertical slabs, vertical stairs, and combined-material slabs support mirroring and rotation. Both materials of a combined slab are transferred, and mirrored blocks are handled correctly when broken or half-mined while sneaking.
- **Create block-break events**: combined-material slabs emit normal block-break events, so integrations based on those events continue to work.
- **Structure Blocks and WorldEdit**: vertical forms and their connected directions follow the standard block-state mirror and rotation paths.

These integrations require no extra configuration. If the corresponding mod is absent or an integration point is unavailable, that compatibility path is skipped silently and the rest of the mod is unaffected.

The mod does not add new block IDs. All changes are applied to existing blocks, so removing the mod will not delete blocks from a world; they simply return to their vanilla appearance. Adding the mod does not retroactively change blocks that are already placed.

## Other vanilla changes

- Glass blocks and glass panes can be mined faster with a pickaxe or an axe.
- Leads can be stretched between two fences as a gently sagging catenary. Click a knot to untie it and recover the lead. Breaking a fence also releases the connection.
- Anvils no longer wear down through use; fall damage still applies. Material repairs no longer accumulate an increasing penalty and restore 90% durability instead of 25%.
- Two keybinds are available: crawl (hold `Ctrl` by default) and sit (`Z` to toggle by default). Both are rebindable.

## Configuration

The configuration file is `config/btsdhz_original-common.toml`. Comments in the generated file are currently written in Chinese.

- `allowTorchLanternOnSlab`: aligns torches and lanterns on bottom slabs. Enabled by default.
- `allowLanternUnderTopSlab`: allows lanterns to hang beneath top slabs. Enabled by default.
- `slabCenterSquareRatio`: the side length of the central Blend square as a ratio of the block face. Default: `0.5`.
- `allowModdedSlabs` / `allowModdedStairs`: enables vertical placement for slabs and stairs from other mods. Enabled by default.
- `blocklist`: controls which blocks may receive this mod's forms. It accepts `+*`, `-*`, individual block IDs, whole namespaces, and block tags. It affects placement only and does not reduce the number of registered block states.

Run `/btsdhz_original blocklist` in game to inspect the active list and statistics.

## Building

```bash
./gradlew build       # Build the jar into build/libs
./gradlew runData     # Regenerate blockstates and models
./gradlew runClient   # Launch the development client
```

## License

Licensed under the MIT License. See [LICENSE](LICENSE).
