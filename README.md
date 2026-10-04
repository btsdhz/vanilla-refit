# 原版精修 (Vanilla Refit)

在原版基础上做建筑扩展与细节打磨的 NeoForge 模组：竖半砖、竖楼梯、混合半砖，以及一批更顺手的原版改动。
所有新增形态都由方块状态控制，默认关闭，**中途加入模组不会让存档里已有的方块变形**。

| 项目 | 值 |
| --- | --- |
| 游戏版本 | Minecraft 1.21.1 |
| 加载器 | NeoForge 21.1.248 及以上 |
| 模组 id | `btsdhz_original` |
| 运行环境 | 客户端与服务端均可 |

## 台阶与楼梯

- 台阶和楼梯都可以竖着放。按 `V`（可在按键设置里改）切换放置逻辑，台阶与楼梯各自记忆，并且会跟随存档保存：
  - **默认**：点方块面靠外区域竖放，点中央区域平放；手持时显示分割辅助线（台阶为对角线、楼梯为十字线，只在完整面上绘制）。
  - **混合**：面中央的正方形区域内平放，其余位置竖放，正方形比例可在配置里调整。
  - **紧贴**：按点击的那个面决定竖放朝向。
  - **原版**：保留原版放置逻辑（台阶的混合模式仍然可用）。
- **混合半砖**：同一格里叠放两种材质的半砖；潜行时只拆掉其中一半，不潜行则是原版式整体拆除。

## 火把、灯笼、栅栏与墙

- 火把、灵魂火把、红石火把、灯笼、灵魂灯笼可以放在下台阶上，模型、碰撞箱与粒子一起下移半格贴合台阶表面；灯笼也可以挂在上台阶下方。
- 下台阶上的栅栏与墙会整体下移半格，并与周围的栅栏/墙正常连接；墙在方块上方的满高判定也已适配。
- 判定基于原版方块与标签，因此其它模组按原版方式实现的红石火把、灯笼等也能沿用这套贴合。

## 玻璃板与铁栏杆

- 玻璃板与铁栏杆拆成 12 个部件（东西南北各分上下两半，加上四个角的水平面片），大面积铺开时中间的竖直面片会自动隐藏，拼出整块玻璃幕墙的效果。
- 连接规则沿袭原版，且**与放置顺序无关**，不会出现需要手动更新才正确的情况。

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

## 构建

```bash
./gradlew build       # 打包，产物在 build/libs
./gradlew runData     # 重新生成方块状态与模型
./gradlew runClient   # 启动开发环境客户端
```

## 许可

All Rights Reserved。

---

## English

**Vanilla Refit** is a NeoForge mod for Minecraft 1.21.1 that refines vanilla building and behaviour:
vertical slabs, vertical stairs and mixed slabs (with switchable placement modes), torches/lanterns/fences/walls
sitting flush on slabs, glass panes and iron bars rebuilt from 12 pieces with order-independent connections,
crawl/sit keybinds, a lead that can be strung between two fences, glass that can be mined faster with pickaxes or
axes, and anvil changes (no wear from use, no repair cost accumulation, 90% material repair).

Mod id: `btsdhz_original`. Requires NeoForge 21.1.248+ for Minecraft 1.21.1. Client and server are both supported.
