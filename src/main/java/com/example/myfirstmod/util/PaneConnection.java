package com.example.myfirstmod.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * 玻璃板的自动连接逻辑（直接覆盖原版那套“只设置东西南北”的逻辑）。
 *
 * ===== 连接规则（四条；数值小的覆盖数值大的）=====
 * 改动规则时，这张表要同步更新。
 *
 * | 编号 | 状态 | 规则 |
 * | --- | --- | --- |
 * | 1 | 启用 | 任意两个相邻方向都是“原版会连接的方块”，且其中**至少一个是可连接但不是玻璃板 / 铁栏杆**的方块（实心方块、墙）→ 它们的夹角为真（上/下 × 水平 = 那个方向的上下半；水平 × 水平 = 那个斜角的面片） |
 * | 2 | 启用 | 任意两个相邻方向都有**玻璃板 / 铁栏杆**、且它们夹角上的斜角位置也是玻璃板 / 铁栏杆 → 夹角为真（同样分上/下 × 水平与水平 × 水平两种） |
 * | 3 | 启用 | 上下**至少有一格有“方块且非板类”**、且东西南北里某个方向是**方块** → 那个方向的上下两半都为真 |
 * | 4 | 兜底（最低） | **上面和下面都是空气**、且**规则 1~3 都没有命中** → 12 个面全真 |
 *
 * <b>夹角面片的熄灭</b>：四个夹角面片只被规则 1、2 点亮，**没有任何规则再把它们熄灭**，
 * 因此“角落空着却长出小面片”的情况会比 1.0.3 略多一些，这是有意的取舍。
 *
 * <b>“方块”的判定</b>：全部指“原版玻璃板会连接的方块”，与原版 {@code IronBarsBlock#attachsTo} 一致——
 * 玻璃板 / 铁栏杆、墙（{@code BlockTags.WALLS}）、或者朝本块那一面是完整实心面的方块。
 * 斜角位置（同一水平面里两个相邻方向之间那一格）没有单一朝向，那里取“它朝本块的两个侧面里
 * 任意一个满足上面这条”即算有方块——现在只有规则 2 会看水平斜角（要求那里是板类）。
 * 本文件里的“板类”专指**玻璃板与铁栅栏**（{@link IronBarsBlock} 一族，含染色玻璃板）。
 *
 * <b>优先级怎么落地</b>：代码按“优先级从低到高”写，靠后的覆盖靠前的：
 * 先算规则 1、2（都只置真、互不否决）→ 规则 3（同样只置真）
 * → 规则 4 兜底（只有它自己的“上下都是空气”条件满足时才会执行）。现在没有任何“置假”规则。
 *
 * <b>起点</b>：12 个属性先全部置假；规则 1~3 都没命中时就是全假，也就是原版那根中心十字，
 * 只有规则 4 的兜底（上下都是空气、且规则 1~3 都没命中）才会变成 12 面全真。
 * 排除规则 4 的依据是“规则 1~3 有没有命中”，不是“结果是否全假”。
 *
 * <b>为什么与放置顺序无关</b>：这套规则**不读邻居算出来的部件属性**，只读邻居是什么方块，
 * 所以结果是周围环境的纯函数（不存在“先摆一片、再在下面放玻璃板就不会重新连接”那类问题）。
 *
 * <b>更新范围</b>：本块要读 6 个正交方向 + 12 个“夹角”斜角（同一水平面 4 个 + 上下 × 水平 8 个），
 * 共 18 格，见 {@link #NEIGHBOUR_OFFSETS} / {@link #DIAGONAL_OFFSETS}。原版的邻居级联只通知 6 个
 * 正交邻居，斜角那一路由 PaneIndirectNeighbourMixin 补（见 {@link #refreshDiagonals}，照原版红石线
 * 走 updateIndirectNeighbourShapes）。世界生成那一路仍然走 refreshChunk 的队列，但那只是保险——
 * 现在的规则一次 update 就到位（幂等），队列不会算出不同的结果。
 */
public final class PaneConnection {

    /**
     * 是否正处在区块后处理这一次调用里（LevelChunkPostProcessMixin 会在方法前后开关）。
     * 后处理的时机是“区块被提升为 tick 中”，从磁盘加载的区块也会走，所以不能用它来当
     * “这块区块是新生成的”判据；这里改成记录“本次后处理有没有真的碰到我们的方块”，
     * 只有碰到了才需要做后面的收敛，普通加载就完全不额外做事。
     */
    private static boolean inPostProcess;
    private static boolean touchedPane;

    private PaneConnection() {
    }

    /**
     * 会与本块互相影响的偏移：6 个正交方向 + 12 个“夹角”斜角（同一水平面 4 个 + 上下 × 水平 8 个），共 18 个。
     * 规则 1、2 读的全部位置都在这里面，改动规则牵到新位置时必须同步这张表。
     */
    private static final int[][] NEIGHBOUR_OFFSETS = {
            {0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0},
            {1, 0, -1}, {1, 0, 1}, {-1, 0, 1}, {-1, 0, -1},
            {0, 1, -1}, {1, 1, 0}, {0, 1, 1}, {-1, 1, 0},
            {0, -1, -1}, {1, -1, 0}, {0, -1, 1}, {-1, -1, 0}
    };

    /** 12 个“夹角”斜角偏移（与 {@link #NEIGHBOUR_OFFSETS} 的后 12 项一致，这里单独一张便于遍历）。 */
    private static final int[][] DIAGONAL_OFFSETS = {
            {1, 0, -1}, {1, 0, 1}, {-1, 0, 1}, {-1, 0, -1},
            {0, 1, -1}, {1, 1, 0}, {0, 1, 1}, {-1, 1, 0},
            {0, -1, -1}, {1, -1, 0}, {0, -1, 1}, {-1, -1, 0}
    };

    /** 区块后处理开始（由 LevelChunkPostProcessMixin 调用）。 */
    public static void beginPostProcess() {
        inPostProcess = true;
        touchedPane = false;
    }

    /** 区块后处理结束；返回本次是否处理过玻璃板/铁栏杆（由 LevelChunkPostProcessMixin 调用）。 */
    public static boolean endPostProcess() {
        boolean touched = touchedPane;
        inPostProcess = false;
        touchedPane = false;
        return touched;
    }

    /** 按当前周围环境重算玻璃板的 12 个部件属性；不是本模组支持的玻璃板时原样返回。 */
    public static BlockState update(BlockState state, BlockGetter level, BlockPos pos) {
        if (!PaneCornerSupport.isSupportedPane(state)
                || !state.hasProperty(ModBlockStateProperties.PANE_NORTH_EAST)
                || !(state.getBlock() instanceof IronBarsBlock pane)) {
            return state;   // 第三方玻璃板/铁栏杆等：连“碰到过玻璃板”的标记都不打，免得白跑一次收敛
        }
        if (inPostProcess) {
            touchedPane = true;
        }

        // ===== 六个方向：是不是“原版玻璃板会连接的方块”，以及是不是玻璃板/铁栏杆 =====
        boolean north = connectsTo(pane, level, pos, Direction.NORTH);
        boolean east = connectsTo(pane, level, pos, Direction.EAST);
        boolean south = connectsTo(pane, level, pos, Direction.SOUTH);
        boolean west = connectsTo(pane, level, pos, Direction.WEST);
        boolean up = connectsTo(pane, level, pos, Direction.UP);
        boolean down = connectsTo(pane, level, pos, Direction.DOWN);
        boolean paneNorth = isPaneBlock(level, pos.north());
        boolean paneEast = isPaneBlock(level, pos.east());
        boolean paneSouth = isPaneBlock(level, pos.south());
        boolean paneWest = isPaneBlock(level, pos.west());
        BlockState aboveState = level.getBlockState(pos.above());
        BlockState belowState = level.getBlockState(pos.below());
        boolean paneUp = aboveState.getBlock() instanceof IronBarsBlock;
        boolean paneDown = belowState.getBlock() instanceof IronBarsBlock;
        // 兜底（规则 4）的限制条件用：上下“没有方块”= 两格都是空气
        boolean airAbove = aboveState.isAir();
        boolean airBelow = belowState.isAir();
        // “其余可连接方块”= 可连接但不是玻璃板/铁栏杆（实心方块、墙）
        boolean solidNorth = north && !paneNorth;
        boolean solidEast = east && !paneEast;
        boolean solidSouth = south && !paneSouth;
        boolean solidWest = west && !paneWest;
        boolean solidUp = up && !paneUp;
        boolean solidDown = down && !paneDown;

        // ===== 规则 2 的 12 组条件：两个相邻方向都是“原版会连接的方块”，且其中至少一个是
        // “可连接但不是玻璃板/铁栏杆”的方块（实心方块、墙）=====
        // 条件是“一边是非板类方块、另一边只要是方块即可”，所以“实心方块 × 玻璃板”这种组合也会点亮夹角；
        // 板类 × 板类 不归这条管。
        boolean solidPairNorthEast = north && east && (solidNorth || solidEast);
        boolean solidPairSouthEast = south && east && (solidSouth || solidEast);
        boolean solidPairSouthWest = south && west && (solidSouth || solidWest);
        boolean solidPairNorthWest = north && west && (solidNorth || solidWest);
        boolean solidPairDownNorth = down && north && (solidDown || solidNorth);
        boolean solidPairDownEast = down && east && (solidDown || solidEast);
        boolean solidPairDownSouth = down && south && (solidDown || solidSouth);
        boolean solidPairDownWest = down && west && (solidDown || solidWest);
        boolean solidPairUpNorth = up && north && (solidUp || solidNorth);
        boolean solidPairUpEast = up && east && (solidUp || solidEast);
        boolean solidPairUpSouth = up && south && (solidUp || solidSouth);
        boolean solidPairUpWest = up && west && (solidUp || solidWest);
        boolean rule2Hit = solidPairNorthEast || solidPairSouthEast || solidPairSouthWest || solidPairNorthWest
                || solidPairDownNorth || solidPairDownEast || solidPairDownSouth || solidPairDownWest
                || solidPairUpNorth || solidPairUpEast || solidPairUpSouth || solidPairUpWest;

        // ===== 规则 3 的 12 组条件：两个相邻方向都是玻璃板/铁栏杆、且它们夹角上的斜角位置也是玻璃板/铁栏杆 =====
        // 水平 × 水平 → 同一水平面里的那个斜角面片；上/下 × 水平 → 那个水平方向的上半/下半片
        boolean panePairNorthEast = paneNorth && paneEast && isPaneAt(level, pos, 1, 0, -1);
        boolean panePairSouthEast = paneSouth && paneEast && isPaneAt(level, pos, 1, 0, 1);
        boolean panePairSouthWest = paneSouth && paneWest && isPaneAt(level, pos, -1, 0, 1);
        boolean panePairNorthWest = paneNorth && paneWest && isPaneAt(level, pos, -1, 0, -1);
        boolean panePairDownNorth = paneDown && paneNorth && isPaneAt(level, pos, 0, -1, -1);
        boolean panePairDownEast = paneDown && paneEast && isPaneAt(level, pos, 1, -1, 0);
        boolean panePairDownSouth = paneDown && paneSouth && isPaneAt(level, pos, 0, -1, 1);
        boolean panePairDownWest = paneDown && paneWest && isPaneAt(level, pos, -1, -1, 0);
        boolean panePairUpNorth = paneUp && paneNorth && isPaneAt(level, pos, 0, 1, -1);
        boolean panePairUpEast = paneUp && paneEast && isPaneAt(level, pos, 1, 1, 0);
        boolean panePairUpSouth = paneUp && paneSouth && isPaneAt(level, pos, 0, 1, 1);
        boolean panePairUpWest = paneUp && paneWest && isPaneAt(level, pos, -1, 1, 0);
        boolean rule3Hit = panePairNorthEast || panePairSouthEast || panePairSouthWest || panePairNorthWest
                || panePairDownNorth || panePairDownEast || panePairDownSouth || panePairDownWest
                || panePairUpNorth || panePairUpEast || panePairUpSouth || panePairUpWest;

        // ----- 规则 2、3：命中就把对应夹角置真（两条都只置真、互不否决，谁命中结果一样） -----
        boolean northEast = solidPairNorthEast || panePairNorthEast;
        boolean southEast = solidPairSouthEast || panePairSouthEast;
        boolean southWest = solidPairSouthWest || panePairSouthWest;
        boolean northWest = solidPairNorthWest || panePairNorthWest;
        boolean northLower = solidPairDownNorth || panePairDownNorth;
        boolean eastLower = solidPairDownEast || panePairDownEast;
        boolean southLower = solidPairDownSouth || panePairDownSouth;
        boolean westLower = solidPairDownWest || panePairDownWest;
        boolean northUpper = solidPairUpNorth || panePairUpNorth;
        boolean eastUpper = solidPairUpEast || panePairUpEast;
        boolean southUpper = solidPairUpSouth || panePairUpSouth;
        boolean westUpper = solidPairUpWest || panePairUpWest;

        // ----- 规则 4：上下至少有一格有“方块且非板类”，且东西南北里某个方向是“方块”
        // → 那个方向的上下两半都为真 -----
        // “方块”仍是本文通用口径＝原版会连接的方块（玻璃板/铁栏杆、墙、或朝本块那一面是完整实心面的方块）；
        // “非板类”＝那一格不是玻璃板/铁栏杆，所以上下条件是 solidUp || solidDown。
        // 水平那一侧只要是方块就行（板类也算）。它只置真，不会否掉规则 2、3 点亮的夹角面片。
        // 例：下面放了个实心方块、北面是玻璃板 → 北方向的上下两半都真。
        boolean touchedAboveOrBelow = solidUp || solidDown;
        boolean rule4Hit = touchedAboveOrBelow && (north || east || south || west);
        if (touchedAboveOrBelow) {
            if (north) {
                northLower = true;
                northUpper = true;
            }
            if (east) {
                eastLower = true;
                eastUpper = true;
            }
            if (south) {
                southLower = true;
                southUpper = true;
            }
            if (west) {
                westLower = true;
                westUpper = true;
            }
        }

        // ----- 规则 4（兜底，最低优先级）：上面和下面都是空气、且规则 1~3 都没有命中 → 12 个面全真 -----
        // 限制“上下都是空气”是为了把兜底只留给悬空的孤立玻璃板：上下只要有一格是方块
        // （含实心方块、墙、玻璃板），就保持全假＝原版那根中心十字。
        if (airAbove && airBelow && !(rule2Hit || rule3Hit || rule4Hit)) {
            northLower = true;
            eastLower = true;
            southLower = true;
            westLower = true;
            northUpper = true;
            eastUpper = true;
            southUpper = true;
            westUpper = true;
            northEast = true;
            southEast = true;
            southWest = true;
            northWest = true;
        }

        return state
                .setValue(CrossCollisionBlock.NORTH, northLower)
                .setValue(CrossCollisionBlock.EAST, eastLower)
                .setValue(CrossCollisionBlock.SOUTH, southLower)
                .setValue(CrossCollisionBlock.WEST, westLower)
                .setValue(ModBlockStateProperties.PANE_NORTH_UP, northUpper)
                .setValue(ModBlockStateProperties.PANE_EAST_UP, eastUpper)
                .setValue(ModBlockStateProperties.PANE_SOUTH_UP, southUpper)
                .setValue(ModBlockStateProperties.PANE_WEST_UP, westUpper)
                .setValue(ModBlockStateProperties.PANE_NORTH_EAST, northEast)
                .setValue(ModBlockStateProperties.PANE_SOUTH_EAST, southEast)
                .setValue(ModBlockStateProperties.PANE_SOUTH_WEST, southWest)
                .setValue(ModBlockStateProperties.PANE_NORTH_WEST, northWest);
    }

    /** 与原版 IronBarsBlock 相同的连接判定：邻居是玻璃板/铁栏杆、墙，或朝本块那一面是完整实心面。 */
    private static boolean connectsTo(IronBarsBlock pane, BlockGetter level, BlockPos pos, Direction direction) {
        return attachesTo(pane, level, pos.relative(direction), direction.getOpposite());
    }

    /**
     * 站在 {@code neighborPos} 看“它会不会和本块连上”，判定与原版 {@code IronBarsBlock#attachsTo} 完全一致：
     * 玻璃板 / 铁栏杆、墙（{@code BlockTags.WALLS}）无条件算连接，其余方块看朝本块那一面是不是完整实心面。
     *
     * @param faceTowardUs 邻居朝本块的那一面
     */
    private static boolean attachesTo(IronBarsBlock pane, BlockGetter level, BlockPos neighborPos,
                                      Direction faceTowardUs) {
        BlockState neighbor = level.getBlockState(neighborPos);
        return pane.attachsTo(neighbor, neighbor.isFaceSturdy(level, neighborPos, faceTowardUs));
    }

    /** 该位置是不是玻璃板 / 铁栏杆（也就是本模组做部件化的那一族方块）。 */
    private static boolean isPaneBlock(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof IronBarsBlock;
    }

    /** 按偏移 (dx, dy, dz) 看那一格是不是玻璃板 / 铁栏杆（规则 2 的“夹角的斜角”用）。 */
    private static boolean isPaneAt(BlockGetter level, BlockPos pos, int dx, int dy, int dz) {
        return isPaneBlock(level, pos.offset(dx, dy, dz));
    }

    /** 从斜角位置指回本块的方向，只用来给原版那一步一个合法的朝向（本模组重算时直接读世界）。 */
    private static Direction directionBackToOrigin(int dx, int dy, int dz) {
        if (dx != 0) {
            return dx > 0 ? Direction.WEST : Direction.EAST;
        }
        if (dy != 0) {
            return dy > 0 ? Direction.DOWN : Direction.UP;
        }
        return dz > 0 ? Direction.NORTH : Direction.SOUTH;
    }

    /**
     * 世界生成结束后，把一个区块里参与部件化的玻璃板/铁栏杆刷到稳定状态。
     *
     * 原版的后处理（LevelChunk.postProcessGeneration）只对每个标记过的位置按任意顺序调用一次
     * updateFromNeighbourShapes。旧规则会读邻居的属性，顺序不对时先算的方块会停在旧结果上
     * （表现就是部分铁栏杆没被更新，要手动在旁边放个方块才刷新），当时才需要这里的队列收敛。
     *
     * 现在的规则只读“邻居是什么方块”，一次 update 就到位，所以这个队列是**保险**性质：
     * update 幂等，收敛不会算出不同的结果。留着它比留一堆“以后可能不需要”的判断更安全；
     * 如果确认长期稳定，可以连同 LevelChunkPostProcessMixin 一起删掉。
     */
    public static void refreshChunk(LevelChunk chunk, Level level) {
        List<BlockPos> seeds = collectPanesInChunk(chunk);
        if (seeds.isEmpty()) {
            return;
        }
        // inQueue 只用来避免同一个位置在队列里重复排队；出队后会移除，
        // 这样某个方块在它之后又被邻居改动影响时还能再算一次，做到真正的收敛。
        Set<BlockPos> inQueue = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos pos : seeds) {
            if (inQueue.add(pos)) {
                queue.add(pos);
            }
        }
        // 安全上限，避免极端形状下反复互相触发
        int budget = seeds.size() * 8 + 64;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty() && budget-- > 0) {
            BlockPos pos = queue.poll();
            inQueue.remove(pos);
            BlockState state = level.getBlockState(pos);
            if (!PaneCornerSupport.isSupportedPane(state)) {
                continue;
            }
            BlockState next = update(state, level, pos);
            if (next == state) {
                continue;
            }
            // 16 = UPDATE_KNOWN_SHAPE：跳过原版的邻居形状级联，改由本方法的队列收敛
            level.setBlock(pos, next, 2 | 16);
            // 这里必须连 4 个水平斜角一起入队：原版级联覆盖不到斜角，而规则 2 会读斜角有没有方块
            for (int[] offset : NEIGHBOUR_OFFSETS) {
                BlockPos neighbor = mutable.setWithOffset(pos, offset[0], offset[1], offset[2]).immutable();
                if (level.getBlockState(neighbor).getBlock() instanceof IronBarsBlock && inQueue.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
    }

    /**
     * 补上“夹角斜角”这条原版邻居级联覆盖不到的依赖：规则 2、5 会读斜角位置有没有方块/玻璃板，
     * 而 BlockStateBase#updateNeighbourShapes 只通知 6 个正交邻居，所以斜角上的方块放置或拆除后
     * 本块不会被重算（表现就是要手动在边上放个方块才更新）。
     *
     * 一共 12 个斜角位置（同一水平面 4 个 + 上下 × 水平 8 个，见 {@link #DIAGONAL_OFFSETS}）：
     * 前 4 个是规则 2 的“水平夹角”（同一水平面里的斜角），后 8 个是规则 2 的“上/下 × 水平”夹角。
     *
     * 这里照原版红石线（RedStoneWireBlock#updateIndirectNeighbourShapes）的做法，在“间接邻居”这一步
     * 把变化位置周围的 12 个斜角各走一遍 neighborShapeChanged，让它们重算 12 个部件属性；
     * 斜角那块要是真的变了，它自己又会照常级联下去，所以整条链能收敛。
     *
     * @param state 发生变化的方块状态（放置时是新状态、拆除时是旧状态，两种情况都能补到）
     * @param pos   发生变化的位置
     */
    public static void refreshDiagonals(LevelAccessor level, BlockState state, BlockPos pos,
                                        int flags, int recursionLeft) {
        for (int[] offset : DIAGONAL_OFFSETS) {
            BlockPos diagonal = pos.offset(offset[0], offset[1], offset[2]);
            if (!PaneCornerSupport.isSupportedPane(level.getBlockState(diagonal))) {
                continue;
            }
            // 斜角与变化位置之间没有单一轴向，这里给的朝向只是让原版那一步的计算拿到一个合法邻居方向；
            // 本模组重算时直接读世界，不受这个朝向影响。
            level.neighborShapeChanged(directionBackToOrigin(offset[0], offset[1], offset[2]), state, diagonal, pos,
                    flags, recursionLeft);
        }
    }

    /** 收集区块里所有参与部件化的玻璃板/铁栏杆；先用 section 的调色板过滤，避免整块扫描。 */
    private static List<BlockPos> collectPanesInChunk(LevelChunk chunk) {
        List<BlockPos> result = new ArrayList<>();
        LevelChunkSection[] sections = chunk.getSections();
        ChunkPos chunkPos = chunk.getPos();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section == null || section.hasOnlyAir()
                    || !section.getStates().maybeHas(state -> state.getBlock() instanceof IronBarsBlock)) {
                continue;
            }
            int baseY = SectionPos.sectionToBlockCoord(chunk.getMinSection() + index);
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (PaneCornerSupport.isSupportedPane(state)) {
                            result.add(new BlockPos(chunkPos.getMinBlockX() + x, baseY + y,
                                    chunkPos.getMinBlockZ() + z));
                        }
                    }
                }
            }
        }
        return result;
    }

}
