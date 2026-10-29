# 更新日志 / Changelog

## 1.0.5

以玻璃板 / 铁栏杆连接外观为主的一次更新。功能、方块状态数、存档格式都没有变化。

**改动**

- 玻璃板 / 铁栏杆的连接判定整体重做：判定仍然只读"周围是什么方块"、不读邻居算出来的属性，所以结果与摆放顺序无关；新增了面向"连续形状"的补全——单个面片、某个大面缺一个角、以及整片集中在同一个方向的摆法都会自动补齐，夹角位置的面片改为只在正向条件成立时点亮。
- 玻璃板连接外观继续微调：这是阶段性改进，不是最终定稿，个别摆法的外观会与 1.0.4 不同；碰撞箱、调试棒行为、老存档外观均不变。

**适配**

- 本次五个版本（1.20.1 / 1.21.1 / 1.21.4 / 1.21.8 / 1.21.10）一起更新，连接改动是纯逻辑，各版本内容一致。

---

## 1.0.5 (English)

A glass pane / iron bars connection-appearance focused update. Gameplay, block state counts and save format are unchanged.

**Changes**

- The glass pane / iron bars connection logic has been reworked: it still only reads what the neighbouring blocks are (not the properties they compute), so the result is independent of placement order, and it now fills in the remaining faces of continuous shapes - a single live face, a large face missing one corner, or a set of faces all concentrated in one direction get completed automatically. Corner faces are now lit only when their positive condition is satisfied.
- Further fine-tuning of the glass pane connection appearance; this is an incremental improvement rather than a final version, so a few layouts look different from 1.0.4. Collision boxes, the debug stick behaviour and existing-world appearance are unchanged.

**Ports**

- All five editions (1.20.1 / 1.21.1 / 1.21.4 / 1.21.8 / 1.21.10) are updated together; the connection change is pure logic and identical across editions.

---

## 1.0.4

以两处"进不去游戏"的严重问题为主的一次修复更新：专用服务器启动失败、新建世界卡在进度条；另含一处玻璃板连接外观调整。功能、方块状态数、存档格式都没有变化，**强烈建议所有用户升级**。

**修复**

- **新建世界卡在"正在生成世界"进度条**：世界生成时，原版会把某些结构段（例如废弃矿井的交叉口）的 `mirror` / `rotation` 以 **null** 传进方块状态的镜像/旋转入口，而本模组的对称兼容代码没有做空判断，直接抛 `NullPointerException`，生成线程当场中断、进度条停在某个百分比不再前进。现在这两处把 null 视作"不变"（与原版方块的行为一致）。该问题自 1.0.0 起就存在、所有游戏版本都受影响，且**与种子有关**（碰到这类结构段才会发生），所以此前表现为"有些新世界能建、有些卡住"——卡住过的半成品世界建议直接删除重建。
- **专用服务器无法启动（因此也无法生成新世界）**：模组在构造阶段无条件注册了三个只属于客户端的模型事件监听，服务端为了解析这些方法引用被迫加载客户端类，抛出 `NoClassDefFoundError: net/minecraft/client/resources/model/…` 并让模组加载失败，服务器直接停在启动阶段。现在这三个监听和另一个同类的客户端监听一起放进"仅在客户端注册"的判断里，**客户端行为完全不变**，服务端可正常开服并生成世界。该问题自 1.0.0 起就存在、所有游戏版本都受影响；单机（客户端与内置服务端同进程）不会触发，所以此前只在专用服务器上暴露。

**改动**

- 玻璃板 / 铁栏杆的连接规则去掉了一条"夹角不成立就把面片熄灭"的规则：现在角落的面片只由正向条件点亮、不再被熄灭，所以角落空着时比 1.0.3 更容易长出小面片。属性、方块状态数、存档格式、渲染方式都没有变化，只是部分摆法的连接外观会与 1.0.3 不同。

**适配**

- 本次五个版本（1.20.1 / 1.21.1 / 1.21.4 / 1.21.8 / 1.21.10）一起更新，三项都是纯逻辑改动，各版本内容一致。
- 说明：这两处问题在 1.0.3 打包时尚未定位到，所以没有进 1.0.3，只在本次修复。

## 1.0.4 (English)

A critical update focused on two cases where the game could not be entered: dedicated server startup failure and world creation becoming stuck on the progress screen. It also adjusts part of the glass pane connection appearance. Gameplay, block-state counts, and save format are unchanged. **Updating all users is strongly recommended.**

**Fixes**

- **New worlds stuck on the "Generating world" progress screen**: during world generation, vanilla can pass `null` for `mirror` or `rotation` when transforming certain structure pieces, such as abandoned mineshaft crossings. The mod's symmetry compatibility code did not guard against null and threw a `NullPointerException`, stopping the generation thread at an arbitrary percentage. Both paths now treat `null` as "no transformation", matching vanilla block behavior. The issue existed since 1.0.0, affected every game version, and depended on the world seed because it only occurred when one of these structure pieces was generated. Incomplete worlds that were already stuck should be deleted and regenerated.
- **Dedicated servers failing to start and therefore failing to generate worlds**: the mod registered three client-only model event listeners unconditionally during construction. The dedicated server had to resolve those method references and attempted to load client model classes, causing `NoClassDefFoundError: net/minecraft/client/resources/model/...` and preventing the mod from loading. Those three listeners and one similar client listener are now registered only on the client. Client behavior is unchanged, while dedicated servers can now start and generate worlds. This issue also existed since 1.0.0 and affected every game version; integrated single-player uses the client process and did not trigger it.

**Changes**

- The glass pane / iron bars connection rules no longer contain the rule that extinguished a face when a corner condition failed. Corner faces are now lit only by positive conditions. As a result, small corner faces can appear more often than in 1.0.3 when a corner is empty. Properties, block-state counts, save format, and rendering method are unchanged; only some connection appearances differ from 1.0.3.

**Ports**

- All five editions (1.20.1 / 1.21.1 / 1.21.4 / 1.21.8 / 1.21.10) were updated together. All three changes are logic-only and identical across editions.
- These two startup issues were not identified when 1.0.3 was packaged, so they were not included in 1.0.3 and are fixed only in this release.

## 1.0.3

以玻璃板连接规则与日志可读性为主的一次更新，功能与状态数不变，**建议尽快升级**。

**修复**

- 玻璃板 / 铁栏杆的连接规则重做：1.0.2 的规则在多种摆法下会误判——夹角与竖直半片点亮的位置不对。新规则只读"周围是什么方块"、不再读邻居算出来的属性，因此**结果与摆放顺序无关**；玻璃板与铁栏杆共用同一套规则。属性、方块状态数、存档格式、渲染方式都没有变化，只是部分摆法的连接外观会与 1.0.2 不同。

**改动**

- 启动与资源重载的日志改为正式简洁的写法：去掉重复的加载提示、把逐方块的过程行降为调试级、统一 `[原版精修/模型|状态|配置|基准]` 前缀，正常启动只剩少量汇总行与自检结论。

**适配**

- 本次五个版本（1.20.1 / 1.21.1 / 1.21.4 / 1.21.8 / 1.21.10）一起更新，功能内容一致；连接规则是纯逻辑改动，各版本无差异。

## 1.0.3 (English)

An update focused on glass pane connection rules and log readability. Gameplay and block-state counts are unchanged. **Updating is recommended.**

**Fixes**

- Glass pane / iron bars connection rules were reworked. The 1.0.2 rules produced incorrect results in several layouts, particularly for corners and half-height vertical faces. The new rules read only the types of neighbouring blocks and no longer read properties computed by neighbours, so the result is independent of placement order. Glass panes and iron bars share the same rules. Properties, block-state counts, save format, and rendering method are unchanged; only some connection appearances differ from 1.0.2.

**Changes**

- Startup and resource-reload logging now uses formal, concise messages. Repeated loading messages were removed, per-block progress messages were lowered to debug level, and prefixes were standardized as `[原版精修/模型|状态|配置|基准]`. Normal startup now prints only a small number of summaries and self-check results.

**Ports**

- All five editions were updated together with the same feature set. The connection rule change is logic-only and identical across editions.

## 1.0.2

以内存与渲染开销为主的一次更新：玻璃板/铁栏杆与栅栏/墙的模型和状态表做了几处重做，并配套一批拴绳修复与连接规则调整。

**优化**

- 玻璃板与铁栏杆改用"一个方块变体 + 运行时按状态拼装"的渲染方式：方块状态数与属性都没变（每块仍是 8192 个状态），但每个状态不再各存一份模型数据。同机同客户端的两次实测（主菜单、GC 后）总存活量 **1.42 GB → 710 MB（省 782 MB，−52%）**，其中 `KeyValueCondition` 条件对象 10.64M → 322k、`Optional` 10.65M → 329k、`MultiPartBakedModel` 175,562 → 28,106（玻璃板那 147,456 个变成一个材质一个的 18 个模型实例）。碰撞箱、连接规则、调试棒行为、老存档外观全部不变。
- 玻璃板/铁栏杆的运行时拼装按 12 位部件掩码缓存拼装结果（懒加载、无锁）：`getQuads` 不再在每次调用时新建集合，渲染路径每次调用只剩"读 12 个属性 + 取一次缓存"，代价约 0.6 MB。改造前的原版 multipart 每次调用要建约 14~19 个临时对象，现在为零。
- 内存优化：不再给每个方块状态各存一张原版的"邻居表"（`ArrayTable` + 两张索引表 + 每属性一行 `Object[]`，与渲染和连接规则无关），改为按属性下标现算目标状态。**每个新增状态约 2.4 KiB 的固定开销里，那一项占 53%**（21.6 万张表 / 约 230 MiB），现在 `ArrayTable` 计数为 0。同一套 dev 客户端主菜单 GC 后 **678 MiB → 444 MiB（−234 MiB，−34%）**；状态数、属性、外观、连接规则不变，启动时抽样校验跳转与原版逐一对上，检测到 FerriteCore 时自动让路，可用 `-Dbtsdhz_original.compactStateNeighbours=false` 关闭。
- 内存优化：栅栏与墙不再"每个方块状态各烘焙一个拼接结果模型"，改成"一个材质一个模型 + 渲染时按状态挑片段"（片段与剔除归属照抄原版烘焙结果，替换前逐状态比对，结构不同自动保持原版）。干净环境 **420 → 396 MiB**、dev 客户端 **444 → 419 MiB**；`MultiPartBakedModel` 28,106 → 2,691、`ImmutablePair` 144,424 → 35,244。渲染时不再逐条判断拼接规则、不再临时拼列表。
- 上述两项合计：干净环境里本模组的内存开销从 **667 MiB 降到 396 MiB**（相对纯 NeoForge 的净增 +426 → +155 MiB），每个新增方块状态从 2.39 KB 降到 897 B。
- 内存优化：不再为**每一个方块状态**保留一条原版的"顶层模型位置"。原版会为每个状态造一条 `ModelResourceLocation(方块id + 属性名=值...)` 并长期留在两张模型表里（未烘焙表 + 烘焙结果表，共用同一批键），玻璃板一块 8192 个状态、18 块就是 14.7 万条——而这些状态本来就共用同一个模型实例，存的全是同一句话的副本。现在改成每块只留 1 条（另外给"按状态 MRL 查模型"加了按属性串解析回状态的兜底，第三方按老办法查照样拿得到模型）。干净环境实测 **431 MB → 368 MB（−63 MB）**，删掉 203,336 条状态键（玻璃板 147,438 条 47.0 MiB、楼梯 27,303 条 5.8、墙 24,275 条 5.1、台阶 3,180 条 0.6、栅栏 1,140 条 0.2）；方块状态数、属性、外观、碰撞箱、老存档一律不变，每次资源重载后会打一行自检（203,508 个状态的运行期模型全部命中）。安全阀 `-Dbtsdhz_original.pruneModelKeys=false`。
- 渲染优化：玻璃板/铁栏杆的"12 个部件掩码"改为按**方块状态**缓存。原版把方块几何烘焙进区块网格时，会对同一个状态连续问 6 个方向 + 1 次不带方向，等于把一个方块的外观重复算了 7 遍；现在每个状态只算一次（`util/StateIntCache`，无锁直接映射、零常驻开销）。4 万个玻璃板的大场景实测：**取四边形从 1010 ns/方块降到 113 ns（8.9 倍）**，一次全量重建里这一段从约 28 ms 降到约 3 ms；稳态帧率不变（四边形本来就烘焙进区块网格），**方块属性、状态数、外观、碰撞箱、老存档、调试棒行为全部不变**，不占内存。
- 内存优化：台阶/楼梯的"含什么液体"不再把水编两遍。以前原版 `waterlogged` 被恒置为 false、水也存在我们自己的 3 值属性里（6 种组合里只有 3 种有意义）；现在**水用原版 `waterlogged`、除水以外的液体（目前是熔岩）用 2 值属性**，两者互斥。台阶每块 54 → **36** 个状态、楼梯每块 480 → **320**，全游戏注册状态数 216,376 → **206,176**；干净环境实测整机 **367.33 → 358.79 MB（−8.5 MB）**，其中楼梯类 38.91 → 31.33 MB。**顺带的好处：卸掉模组后含水台阶/楼梯仍然是含水的**（以前会变回不含水）。属性名仍是 `btsdhz_fluid`，旧存档/结构里的 `=none|lava` 照旧认，只有 `=water` 需要理解成 `waterlogged=true`。

**修复**

- 拴绳（原版的绳结实体）现在会跟着栅栏的贴台阶位移一起下移/上移半格：以前栅栏下移半格后，绕绳结、它的判定框和拴绳的端点还停在原格中心，看起来就是"拴绳没跟着栅栏走"。动物拴绳（原版）与栅栏之间的拴绳（本模组）都已修正，栅栏上下放/拆台阶后两端的绳结也会自动回位。
- 栅栏/墙/火把/灯笼伸进相邻台阶那一半格也能正常右键了：以前指着这半格右键会被判成点在台阶上（拿拴绳点栅栏伸出去的那半格什么都不会发生），现在和"挖掉/中键选取"一样会改判到贴着的那个方块；这半格里正好有拴绳的绕绳结时，按点到绳结处理（解绳、继续连接）。
- 栅栏之间的拴绳：一头已经挂在栅栏上（绳的另一头在玩家手上）时，点另一根栅栏就能完成连接，不再要求手里还拿着拴绳——空手或拿着别的物品都行。第一次开始连接、点同一根取消，仍然和原版一样要用拴绳。
- 拴绳现在两个方向都能连：除了"先点动物、再点栅栏"，也可以"先点栅栏（绳的一头挂上去、另一头在手上）、再拿拴绳点动物"，动物会被拴到那根栅栏的绳结上（同样消耗这条连接）。
- 修复拴绳连接完成后手上还残留一条绳的问题：那条"挂在手上"的绳由绳结实体上的标记驱动，以前有几处只清了玩家自己的状态、没清绳结上的标记（反向连接、牵着动物点栅栏等路径都会中招）；现在这些路径统一连标记一起清，客户端镜像也同步清掉。
- 玻璃板/铁栏杆的运行时拼装只替换方块状态模型，不再顶掉它们的物品图标（此前会让物品栏里的玻璃板变成 3D 立柱）。
- 其它模组的玻璃板 / 铁栏杆不再被注入本模组的 8 个附加属性：以前凡是继承 `IronBarsBlock` 的模组方块都会带上它们（每块状态数 32 → 8192，内存翻 256 倍），但本模组又不给它们做外观与碰撞，属于"只多占内存、没有功能"。现在第三方玻璃板 / 铁栏杆的状态数、外观、碰撞箱与连接规则完全保持原版；原版 18 个玻璃板 / 铁栏杆不受影响。

**适配**

- 本次五个版本（1.20.1 / 1.21.1 / 1.21.4 / 1.21.8 / 1.21.10）一起更新，功能内容一致。
- "顶层模型表去重"这一项只在 1.21.1 与 1.21.4 生效；1.20.1 的模型表结构与 1.21.8 / 1.21.10 的模型系统不同，这三个版本按前提跳过，其余优化与修复都已同步。

**连接规则**

- 玻璃板 / 铁栏杆的连接规则换成第三套六条：判定只读周围方块的类别，不再互相读"邻居算出来的属性"，因此无论按什么顺序摆放，最终连接结果都唯一。1.0.1 的已知问题"连接结果可能受放置顺序影响"本次解决；个别摆法的外观会与 1.0.1 不同。

## 1.0.2 (English)

An update focused on memory and rendering overhead. The glass pane / iron bars and fence / wall models and state tables were reworked, along with a set of lead fixes and connection-rule adjustments.

**Optimizations**

- Glass panes and iron bars now use one block-state model plus runtime assembly from parts. Block-state counts and properties are unchanged (8,192 states per block), but each state no longer stores a separate model. In two measurements on the same machine and client (main menu, after GC), total live memory went from **1.42 GB to 710 MB (782 MB saved, -52%)**. `KeyValueCondition` objects went from 10.64M to 322k, `Optional` from 10.65M to 329k, and `MultiPartBakedModel` from 175,562 to 28,106. The 147,456 pane states now share 18 model instances, one per texture. Collision shapes, connection rules, debug-stick behavior, and existing-world appearance are unchanged.
- Runtime pane assembly caches its result by a 12-bit parts mask. The lazy, lock-free cache means `getQuads` no longer allocates collections during each call. The render path now reads 12 properties and performs one cache lookup per call, at a cost of about 0.6 MB.
- Block states no longer each store a copy of vanilla's neighbour lookup tables (`ArrayTable` plus two index tables). These tables were unrelated to rendering and connection rules and cost about 2.4 KiB per added state, accounting for 53% of that fixed cost (216k tables, about 230 MiB). Neighbour transitions are now calculated from property indexes. Main-menu memory after GC fell from **678 MiB to 444 MiB (-234 MiB, -34%)** in the same development client. State counts, properties, appearance, and connection rules are unchanged. Startup sampling verifies the transitions, and the optimization automatically yields to FerriteCore. It can be disabled with `-Dbtsdhz_original.compactStateNeighbours=false`.
- Fences and walls no longer bake a separate assembled model for every block state. They use one model per material plus runtime selection of model parts, using the same parts and culling ownership as vanilla baking. Any structural mismatch automatically falls back to vanilla. Clean-environment memory fell from **420 to 396 MiB**, and development-client memory from **444 to 419 MiB**. `MultiPartBakedModel` went from 28,106 to 2,691, and `ImmutablePair` from 144,424 to 35,244.
- Together, the previous two optimizations reduced the mod's overhead in a clean environment from **667 MiB to 396 MiB** (net increase over pure NeoForge from +426 MiB to +155 MiB). The cost per added block state fell from 2.39 KB to 897 B.
- The mod no longer stores one vanilla top-level model entry for every block state. Vanilla creates a `ModelResourceLocation` containing every property for each state and keeps both unbaked and baked copies. A single pane block has 8,192 states, so 18 pane blocks produced 147k entries that all referred to the same model instance. Each pane block now keeps one entry per model, while a fallback parser still resolves old-style state-based model lookups. Clean-environment memory fell from **431 MB to 368 MB (-63 MB)** and 203,336 state keys were removed: glass panes 147,438 keys / 47.0 MiB, stairs 27,303 / 5.8 MiB, walls 24,275 / 5.1 MiB, slabs 3,180 / 0.6 MiB, and fences 1,140 / 0.2 MiB. Block states, properties, appearance, collision, and existing worlds are unchanged. A self-check runs after each resource reload. The optimization can be disabled with `-Dbtsdhz_original.pruneModelKeys=false`.
- The pane 12-part mask is now cached per block state. Vanilla asks for six directional quad sets and one undirected set while baking a state, repeating the same calculation seven times. Each state is now evaluated once through `util/StateIntCache`, with no lock and no persistent allocation. In a scene with 40,000 glass panes, quad lookup fell from **1,010 ns to 113 ns per block (8.9x faster)**, reducing that part of a full rebuild from about 28 ms to about 3 ms. Steady-state frame rate is unchanged because geometry is baked into chunk meshes. Properties, state counts, appearance, collision, existing worlds, and debug-stick behavior are unchanged.
- Slab and stair fluid storage no longer encodes water twice. Vanilla `waterlogged` was previously forced to false while water was also stored in a custom three-value property, leaving only three of six combinations useful. Water now uses vanilla `waterlogged`, while non-water fluids (currently lava) use a two-value custom property. Slabs went from 54 to **36** states and stairs from 480 to **320**. Total registered block states went from 216,376 to **206,176**. Clean-environment memory fell from **367.33 to 358.79 MB (-8.5 MB)**, including stair memory from 38.91 to 31.33 MB. Waterlogged slabs and stairs also remain waterlogged if the mod is removed.

**Fixes**

- Leash knots, their hitboxes, and leash endpoints now follow fences that are shifted by half a block on top or bottom slabs. This covers both vanilla animal leads and the mod's fence-to-fence lead connections.
- Right-click targeting now works in the half-block volume where fences, walls, torches, and lanterns extend into a neighbouring slab block. Previously these clicks were treated as slab clicks. The same redirection already used for breaking and middle-click selection now applies here, including leash-knot interactions.
- If one end of a fence lead is already attached and the other end is held by the player, clicking another fence now completes the connection even if the player is not holding a lead. Starting a new connection or cancelling by clicking the same fence still requires a lead.
- Leads can now be connected in either order. After attaching one end to a fence, the player can hold a lead and click an animal to connect it to that fence knot.
- Fixed a visual lead remaining in the player's hand after completing a connection. Several paths cleared only the player's state without clearing the knot entity marker; all paths now clear both the server state and the client mirror.
- Runtime pane assembly now replaces only the block-state model instead of overriding the item icon. Glass panes no longer appear as 3D posts in inventories.
- Glass panes and iron bars from other mods no longer receive the mod's eight additional properties. Previously every class extending `IronBarsBlock` went from 32 to 8,192 states (256x memory) without receiving the matching appearance or collision behavior. Third-party panes and bars now keep vanilla state counts, appearance, collision, and connection rules.

**Ports**

- All five editions were updated together with the same features.
- Top-level model table pruning is active only on 1.21.1 and 1.21.4. The model table layout differs on 1.20.1, 1.21.8, and 1.21.10, so that optimization is skipped there. All other optimizations and fixes were synchronized.

**Connection rules**

- Glass pane / iron bars connection rules were replaced with a third six-rule set. The rules read only the types of surrounding blocks and no longer read properties computed by neighbours, so placement order cannot produce a different final result. This resolves the 1.0.1 known issue. Some layouts look different from 1.0.1.

## 1.0.1

**新增**

- 竖半砖与竖楼梯改为运行时生成，不再为每个方块预生成模型与方块状态：原版与其它模组的台阶、楼梯现在都能竖放。个别方块可在配置里排除，游戏内 `/btsdhz_original blocklist` 可查看当前名单。
- 竖半砖、竖楼梯支持镜像与旋转：机械动力创造之杖的对称模式、结构方块镜像、WorldEdit 都能正确转向（竖楼梯的连接侧会跟着左右互换）。
- 组合半砖跟随机械动力对称之杖一起搬运两块材质；镜像那一格被拆除、或潜行只拆一半时，也会同步补回保留的半砖与掉落。
- 上下堆叠的玻璃板与铁栏杆会互相延续连接（连接规则新增一条竖向规则）。
- 本次同时提供 Minecraft 1.20.1（Forge 47.4.x）、1.21.1（NeoForge 21.1.x）、1.21.4、1.21.8、1.21.10 的适配包。

**修复**

- 修复位移方块（下台阶上的墙、栅栏、灯笼等）的碰撞箱与射线判定：箭矢不再停在模型上方的空气里，玩家走动与投掷物看到的是同一套碰撞箱。
- 修复台阶与上方墙/栅栏之间缝隙的碰撞箱失效（位移状态改用三值枚举后漏判的那几处）。
- 组合半砖拆除时的碎屑改用两块半砖各自的贴图，两种材质混合出现，不再一律是石头。
- 组合半砖被爆炸或 `/setblock destroy` 破坏时也会正常掉落两块半砖。
- 组合半砖的破坏会正常发出破坏事件，其它模组（如机械动力）基于该事件的联动不再失效。
- 补回被误删的平滑砂岩楼梯"上平连接"模型，启动时不再报缺失模型，竖楼梯接平楼梯能正常显示。

**优化**

- 方块状态数量大幅减少：竖楼梯的连接形态并入原版 `shape`（每块楼梯 2880 → 480），墙/栅栏/灯笼的贴台阶状态由两个布尔合并成三值枚举（墙 1296 → 972，栅栏 128 → 96）。
- 位移方块的形状合并按状态缓存，准星与碰撞查询不再重复计算。
- 模型包裹日志从 INFO 降为 DEBUG，日志更干净。
- 打包产物不再包含混进资源目录的开发脚本与文件名清单。

**已知问题**

- 玻璃板/铁栏杆的连接结果仍可能受放置顺序影响，后续版本再处理。

## 1.0.1 (English)

**New**

- Vertical slabs and vertical stairs are now generated at runtime instead of receiving pre-generated models and block states. Vanilla and modded slabs and stairs can now be placed vertically. Individual blocks can be excluded in the configuration, and `/btsdhz_original blocklist` lists the active rules.
- Vertical slabs and vertical stairs support mirroring and rotation through the Create Symmetry Wand, Structure Blocks, and WorldEdit. Connected vertical stair sides are swapped correctly during mirroring.
- Combined-material slabs are transferred with both materials by the Create Symmetry Wand. Removing the mirrored block, or half-mining while sneaking, preserves and restores the surviving half and its drop.
- Vertically stacked glass panes and iron bars now continue their connection through the vertical rule.
- Editions were provided for Minecraft 1.20.1 (Forge 47.4.x), 1.21.1 (NeoForge 21.1.x), 1.21.4, 1.21.8, and 1.21.10.

**Fixes**

- Fixed collision and ray targeting for offset blocks such as fences, walls, and lanterns on slabs. Arrows no longer stop in the air above the model, and player movement and projectiles use consistent collision shapes.
- Fixed missing collision in the gap between a slab and a wall or fence above it after the three-value offset state was introduced.
- Combined-material slab break particles now use both slab textures instead of always using stone.
- Combined-material slabs now drop both halves correctly when destroyed by explosions or `/setblock destroy`.
- Combined-material slabs now emit normal block-break events, so integrations such as Create continue to work.
- Restored a missing "upper flat connection" model for smooth sandstone stairs. Missing-model errors no longer appear at startup, and vertical-to-flat stair connections render correctly.

**Optimizations**

- Reduced block-state counts substantially by merging vertical stair connection shapes into vanilla `shape` (from 2,880 to 480 states per stair) and combining the two slab-offset booleans for walls, fences, and lanterns into a three-value enum (walls from 1,296 to 972; fences from 128 to 96).
- Cached merged shapes for offset blocks so targeting and collision queries do not recalculate them repeatedly.
- Lowered model-wrapper logs from INFO to DEBUG.
- Removed development scripts and filename lists that had been packaged into resource directories.

**Known issues**

- Glass pane / iron bars connections could still depend on placement order. This was addressed in a later release.

## 1.0.0

首个正式版本。

**台阶与楼梯**

- 竖半砖与竖楼梯，附带分割辅助线。
- 放置逻辑：默认 / 混合 / 紧贴 / 原版，按键切换并按存档记忆，中央正方形比例可配置。
- 混合半砖：一格内叠放两种材质，潜行只拆一半；堆叠的挖掘、音效与掉落已适配。

**贴合与连接**

- 火把、灵魂火把、红石火把、灯笼、灵魂灯笼可放在下台阶上，灯笼可挂在上台阶下（模型、碰撞箱、粒子一起位移）。
- 下台阶上的栅栏与墙整体下移半格并正常连接。
- 玻璃板与铁栏杆改为 12 部件拼装，中间面片自动隐藏；连接结果与放置顺序无关。

**其它原版改动**

- 玻璃类方块可用镐或斧加速挖掘。
- 拴绳可在两个栅栏之间连接成悬链线，点击绳结解开并返还，拆栅栏也会解除。
- 铁砧不再因使用而损坏；材料修复不累积惩罚，修复比例 25% → 90%。
- 新增爬行（`Ctrl`）与坐下（`Z`）按键。

**修复**

- 修复台阶/楼梯变体的 uv 映射、碰撞箱与面剔除问题。
- 修复玻璃板斜角依赖导致的放置顺序问题（需要手动更新才正确）。
- 修复坐姿/爬行状态下的提示与射箭位置等问题。

## 1.0.0 (English)

The first stable release.

**Slabs and stairs**

- Vertical slabs and vertical stairs with placement guide lines.
- Default / Blend / Flush / Original placement modes, switched with a keybind and saved per world. The central-square ratio is configurable.
- Combined-material slabs place two materials in one block. Sneak-mine removes only one half. Mining, sound, and drops for the combined state are adapted.

**Alignment and connections**

- Torches, soul torches, redstone torches, lanterns, and soul lanterns can sit on bottom slabs. Lanterns can hang beneath top slabs. Models, collision shapes, and particles shift together.
- Fences and walls on bottom slabs shift down by half a block and connect normally.
- Glass panes and iron bars are assembled from 12 parts. Inner faces are hidden, and connection results are independent of placement order.

**Other vanilla changes**

- Glass-type blocks can be mined faster with a pickaxe or an axe.
- Leads can form a catenary between two fences. Clicking the knot unties it and returns the lead. Breaking a fence also releases the connection.
- Anvils no longer wear down through use. Material repairs do not accumulate a penalty, and the repair ratio is increased from 25% to 90%.
- Added crawl (`Ctrl`) and sit (`Z`) keybinds.

**Fixes**

- Fixed UV mapping, collision shapes, and face culling for slab and stair variants.
- Fixed glass pane diagonal dependencies causing placement-order errors that required a block update.
- Fixed sit/crawl hints and arrow spawn positions.
