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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * 玻璃板的自动连接逻辑（直接覆盖原版那套“只设置东西南北”的逻辑）。
 *
 * ===== 当前规则（按优先级从高到低，靠前的会覆盖靠后的同名属性）=====
 *
 * | 编号 | 状态 | 规则 |
 * | --- | --- | --- |
 * | 1 | 启用（最高） | 水平面 8 个方向（东南西北 + 东北/东南/西北/西南）全是玻璃板 → 四个角全为真，8 个竖直面片全为假 |
 * | 2 | 启用 | 本块上方和下方都没有方块时：某两个方向以及它们之间的斜角**都有玻璃板** → 对应角为真 |
 * | 3 | 启用 | 本块上方和下方都没有方块时：某两个方向**都有“原版会和玻璃板连接的方块”**（玻璃板/铁栏杆、墙，或朝本块那一面是完整实心面）→ 对应角为真；只看东西南北，不看四个斜角 |
 * | 4 | 启用 | 某个方向两侧的角同时为真 → 该方向的上半与下半为假 |
 * | 5 | 启用 | 相邻玻璃板的斜对角面片传播到本角：某方向的邻居是玻璃板、它的斜对角面片（朝向本块那一侧的角）为真，并且**本块侧边那个方向是“原版会和玻璃板连接的方块”**时，本块对应的角为真 |
 * | 6 | 启用 | 某个水平方向有方块（原版判定）、且通过邻居玻璃板限制时：上方有方块 → 该方向上半置真，下方有方块 → 该方向下半置真；只置真，不会把没方块的那一半置假 |
 * | 7 | 启用 | 任意一个角为真 → 8 个竖直面片全为假 |
 * | 8 | 启用 | 某个水平方向有方块（原版判定）、且通过邻居玻璃板限制 → 该方向的上半与下半都为真 |
 *
 * 规则 6、8 的邻居玻璃板限制（对象是“对应方向上的那块玻璃板”，不是当前这块）：
 *  - 邻居不是玻璃板（墙、完整方块等）：不检测，直接通过；
 *  - 邻居是玻璃板但它四个角（东北/东南/西北/西南）全是假：不检测，直接通过；
 *  - 邻居是玻璃板且四个角有任意一个为真：才去检测它朝向本块的那一侧
 *    （本方向的反方向）上半或下半是否为真，为真才通过。
 *
 * 起点（不属于上面任何一条）：12 个属性先全部置为假，周围什么都没有时就是原版那根棍。
 *
 * 实现说明：规则 4、6、7 都要读四角属性，所以代码里先算完四角（规则 5 → 3 → 2 → 规则 1 的角部分），
 * 再算竖直面片（规则 8 → 7 → 6 → 4 → 规则 1 的竖直部分）。优先级只决定“写同一个属性时谁赢”，
 * 角属性与竖直面片是两组不同的属性，先算角不改变优先级结果。
 *
 * 更新方式：本块要读邻居的属性（规则 5~8），规则 1、2 还要读水平斜对角位置是不是玻璃板。
 * 原版的邻居级联只通知 6 个正交邻居，斜角那一路由 PaneIndirectNeighbourMixin 补
 * （见 refreshDiagonals，照原版红石线走 updateIndirectNeighbourShapes），
 * 世界生成那一路由 refreshChunk 的队列收敛负责，所以放置顺序不影响最终结果。
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

    /** 会与本块互相影响的偏移：8 个水平方向（含 4 个斜角）+ 上下两格，共 10 个。 */
    private static final int[][] NEIGHBOUR_OFFSETS = {
            {-1, 0, -1}, {0, 0, -1}, {1, 0, -1},
            {-1, 0, 0}, {1, 0, 0},
            {-1, 0, 1}, {0, 0, 1}, {1, 0, 1},
            {0, 1, 0}, {0, -1, 0}
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
        if (inPostProcess) {
            touchedPane = true;
        }
        if (!PaneCornerSupport.isSupportedPane(state)
                || !state.hasProperty(ModBlockStateProperties.PANE_NORTH_EAST)
                || !(state.getBlock() instanceof IronBarsBlock pane)) {
            return state;
        }

        boolean paneNorth = isPane(level, pos, Direction.NORTH);
        boolean paneEast = isPane(level, pos, Direction.EAST);
        boolean paneSouth = isPane(level, pos, Direction.SOUTH);
        boolean paneWest = isPane(level, pos, Direction.WEST);
        boolean paneNorthEast = isPaneAt(level, pos, 1, -1);
        boolean paneSouthEast = isPaneAt(level, pos, 1, 1);
        boolean paneNorthWest = isPaneAt(level, pos, -1, -1);
        boolean paneSouthWest = isPaneAt(level, pos, -1, 1);

        boolean hasAbove = hasBlock(level, pos.above());
        boolean hasBelow = hasBlock(level, pos.below());
        // 规则 2 / 规则 3 的前提：本块上方和下方都没有方块（都是空气）
        boolean noBlockAboveOrBelow = !hasBlock(level, pos.above()) && !hasBlock(level, pos.below());
        // 规则 1 的判定：水平面 8 个方向全是玻璃板
        boolean surroundedByPanes = paneNorth && paneEast && paneSouth && paneWest
                && paneNorthEast && paneSouthEast && paneNorthWest && paneSouthWest;

        // 规则 3 / 6 / 8 都要用：某个水平方向有没有“原版会连接的方块”（判定与原版 attachsTo 一致）
        boolean connectNorth = connectsTo(pane, level, pos, Direction.NORTH);
        boolean connectEast = connectsTo(pane, level, pos, Direction.EAST);
        boolean connectSouth = connectsTo(pane, level, pos, Direction.SOUTH);
        boolean connectWest = connectsTo(pane, level, pos, Direction.WEST);
        // 规则 6 / 规则 8 的邻居玻璃板限制
        boolean faceNorth = connectNorth && neighbourFacesBack(level, pos, Direction.NORTH);
        boolean faceEast = connectEast && neighbourFacesBack(level, pos, Direction.EAST);
        boolean faceSouth = connectSouth && neighbourFacesBack(level, pos, Direction.SOUTH);
        boolean faceWest = connectWest && neighbourFacesBack(level, pos, Direction.WEST);

        // ==================== 第一组：四角属性 ====================
        boolean northEast = false;
        boolean southEast = false;
        boolean southWest = false;
        boolean northWest = false;

        // ----- 规则 5：相邻玻璃板的斜对角面片传播到本角 -----
        // 侧边那一侧用的是原版连接判定（玻璃板/铁栏杆、墙，或朝本块那一面是完整实心面）
        if (paneNorth) {
            BlockState north = level.getBlockState(pos.north());
            if (connectWest && isOn(north, ModBlockStateProperties.PANE_SOUTH_WEST)) {
                northWest = true;
            }
            if (connectEast && isOn(north, ModBlockStateProperties.PANE_SOUTH_EAST)) {
                northEast = true;
            }
        }
        if (paneSouth) {
            BlockState south = level.getBlockState(pos.south());
            if (connectWest && isOn(south, ModBlockStateProperties.PANE_NORTH_WEST)) {
                southWest = true;
            }
            if (connectEast && isOn(south, ModBlockStateProperties.PANE_NORTH_EAST)) {
                southEast = true;
            }
        }
        if (paneEast) {
            BlockState east = level.getBlockState(pos.east());
            if (connectNorth && isOn(east, ModBlockStateProperties.PANE_NORTH_WEST)) {
                northEast = true;
            }
            if (connectSouth && isOn(east, ModBlockStateProperties.PANE_SOUTH_WEST)) {
                southEast = true;
            }
        }
        if (paneWest) {
            BlockState west = level.getBlockState(pos.west());
            if (connectNorth && isOn(west, ModBlockStateProperties.PANE_NORTH_EAST)) {
                northWest = true;
            }
            if (connectSouth && isOn(west, ModBlockStateProperties.PANE_SOUTH_EAST)) {
                southWest = true;
            }
        }

        // ----- 规则 3：上下都没有方块时，两个方向都有会连接的方块 → 该角为真（不看斜角）-----
        if (noBlockAboveOrBelow) {
            if (connectNorth && connectEast) {
                northEast = true;
            }
            if (connectSouth && connectEast) {
                southEast = true;
            }
            if (connectNorth && connectWest) {
                northWest = true;
            }
            if (connectSouth && connectWest) {
                southWest = true;
            }
        }

        // ----- 规则 2：上下都没有方块时，两个方向 + 它们之间的斜角都有玻璃板 → 该角为真 -----
        if (noBlockAboveOrBelow) {
            if (paneNorth && paneEast && paneNorthEast) {
                northEast = true;
            }
            if (paneSouth && paneEast && paneSouthEast) {
                southEast = true;
            }
            if (paneNorth && paneWest && paneNorthWest) {
                northWest = true;
            }
            if (paneSouth && paneWest && paneSouthWest) {
                southWest = true;
            }
        }

        // ----- 规则 1（角部分，最高优先级）：8 个方向全是玻璃板 → 四角全真 -----
        if (surroundedByPanes) {
            northEast = true;
            southEast = true;
            southWest = true;
            northWest = true;
        }

        // ==================== 第二组：竖直面片 ====================
        boolean northLower = false;
        boolean eastLower = false;
        boolean southLower = false;
        boolean westLower = false;
        boolean northUpper = false;
        boolean eastUpper = false;
        boolean southUpper = false;
        boolean westUpper = false;

        // ----- 规则 8（最低优先级）：该方向有方块且通过邻居限制 → 上下两半都为真 -----
        if (faceNorth) {
            northLower = true;
            northUpper = true;
        }
        if (faceEast) {
            eastLower = true;
            eastUpper = true;
        }
        if (faceSouth) {
            southLower = true;
            southUpper = true;
        }
        if (faceWest) {
            westLower = true;
            westUpper = true;
        }

        // ----- 规则 7：任意一个角为真 → 8 个竖直面片全为假 -----
        if (northEast || southEast || southWest || northWest) {
            northLower = false;
            eastLower = false;
            southLower = false;
            westLower = false;
            northUpper = false;
            eastUpper = false;
            southUpper = false;
            westUpper = false;
        }

        // ----- 规则 6：该方向有方块时，上方有方块则该方向上安置真、下方有方块则下安置真（只加真）-----
        if (faceNorth) {
            if (hasAbove) {
                northUpper = true;
            }
            if (hasBelow) {
                northLower = true;
            }
        }
        if (faceEast) {
            if (hasAbove) {
                eastUpper = true;
            }
            if (hasBelow) {
                eastLower = true;
            }
        }
        if (faceSouth) {
            if (hasAbove) {
                southUpper = true;
            }
            if (hasBelow) {
                southLower = true;
            }
        }
        if (faceWest) {
            if (hasAbove) {
                westUpper = true;
            }
            if (hasBelow) {
                westLower = true;
            }
        }

        // ----- 规则 4：某个方向两侧的角同时为真 → 该方向上下两半为假 -----
        if (northEast && northWest) {
            northLower = false;
            northUpper = false;
        }
        if (southEast && southWest) {
            southLower = false;
            southUpper = false;
        }
        if (northEast && southEast) {
            eastLower = false;
            eastUpper = false;
        }
        if (northWest && southWest) {
            westLower = false;
            westUpper = false;
        }

        // ----- 规则 1（竖直部分，最高优先级）：8 个方向全是玻璃板 → 8 个竖直面片全为假 -----
        if (surroundedByPanes) {
            northLower = false;
            eastLower = false;
            southLower = false;
            westLower = false;
            northUpper = false;
            eastUpper = false;
            southUpper = false;
            westUpper = false;
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

    /** 该方向是不是玻璃板（玻璃板/染色玻璃板/铁栏杆，与原版 attachsTo 的口径一致）。 */
    private static boolean isPane(BlockGetter level, BlockPos pos, Direction direction) {
        return isPaneAt(level, pos.relative(direction));
    }

    /** 水平方向上偏移 (dx, 0, dz) 的位置是不是玻璃板（用于四个斜角）。 */
    private static boolean isPaneAt(BlockGetter level, BlockPos pos, int dx, int dz) {
        return isPaneAt(level, pos.offset(dx, 0, dz));
    }

    /** 该位置是不是玻璃板。 */
    private static boolean isPaneAt(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof IronBarsBlock;
    }

    /** 该位置是不是“有方块”（非空气就算，含流体）。 */
    private static boolean hasBlock(BlockGetter level, BlockPos pos) {
        return !level.getBlockState(pos).isAir();
    }

    private static boolean isOn(BlockState state, Property<Boolean> property) {
        return state.hasProperty(property) && state.getValue(property);
    }

    /**
     * 规则 6、8 的邻居玻璃板限制：限制的是“对应方向上的那块玻璃板”。
     * 邻居不是玻璃板时不检测；邻居是玻璃板但四个角全是假时也不检测；
     * 只有邻居玻璃板有任意一个角为真时，才去检测它朝向本块的那一侧
     * （本方向的反方向）上半或下半是否为真。
     */
    private static boolean neighbourFacesBack(BlockGetter level, BlockPos pos, Direction direction) {
        BlockState neighbour = level.getBlockState(pos.relative(direction));
        if (!(neighbour.getBlock() instanceof IronBarsBlock)) {
            return true;
        }
        boolean neighbourHasCorner = isOn(neighbour, ModBlockStateProperties.PANE_NORTH_EAST)
                || isOn(neighbour, ModBlockStateProperties.PANE_SOUTH_EAST)
                || isOn(neighbour, ModBlockStateProperties.PANE_NORTH_WEST)
                || isOn(neighbour, ModBlockStateProperties.PANE_SOUTH_WEST);
        if (!neighbourHasCorner) {
            return true;
        }
        Direction facing = direction.getOpposite();
        return isOn(neighbour, lowerProperty(facing)) || isOn(neighbour, upperProperty(facing));
    }

    /** 某个水平方向的“下半”属性（原版属性）。 */
    private static Property<Boolean> lowerProperty(Direction direction) {
        return switch (direction) {
            case NORTH -> CrossCollisionBlock.NORTH;
            case EAST -> CrossCollisionBlock.EAST;
            case SOUTH -> CrossCollisionBlock.SOUTH;
            case WEST -> CrossCollisionBlock.WEST;
            default -> throw new IllegalArgumentException("不是水平方向: " + direction);
        };
    }

    /** 某个水平方向的“上半”属性（本模组属性）。 */
    private static Property<Boolean> upperProperty(Direction direction) {
        return switch (direction) {
            case NORTH -> ModBlockStateProperties.PANE_NORTH_UP;
            case EAST -> ModBlockStateProperties.PANE_EAST_UP;
            case SOUTH -> ModBlockStateProperties.PANE_SOUTH_UP;
            case WEST -> ModBlockStateProperties.PANE_WEST_UP;
            default -> throw new IllegalArgumentException("不是水平方向: " + direction);
        };
    }

    /** 与原版 IronBarsBlock 相同的连接判定：邻居是玻璃板/铁栏杆、墙，或该面是完整实心面。 */
    private static boolean connectsTo(IronBarsBlock pane, BlockGetter level, BlockPos pos, Direction direction) {
        BlockPos neighborPos = pos.relative(direction);
        BlockState neighbor = level.getBlockState(neighborPos);
        return pane.attachsTo(neighbor, neighbor.isFaceSturdy(level, neighborPos, direction.getOpposite()));
    }

    /**
     * 世界生成结束后，把一个区块里参与部件化的玻璃板/铁栏杆刷到稳定状态。
     *
     * 原版的后处理（LevelChunk.postProcessGeneration）只对每个标记过的位置按任意顺序调用一次
     * updateFromNeighbourShapes，而本模组的规则会读邻居的属性，所以顺序不对时，先算的方块是在
     * “邻居还没修正”的基础上算出来的，之后就再也不会更新（表现就是部分铁栏杆没被更新，要手动
     * 在旁边放个方块才刷新）。这里按队列做一次局部收敛：谁的状态变了，就把它的邻居再算一遍。
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
            // 这里必须连 4 个水平斜角一起入队：原版级联覆盖不到斜角，而规则 1、2 会读斜角是不是玻璃板
            for (int[] offset : NEIGHBOUR_OFFSETS) {
                BlockPos neighbor = mutable.setWithOffset(pos, offset[0], offset[1], offset[2]).immutable();
                if (level.getBlockState(neighbor).getBlock() instanceof IronBarsBlock && inQueue.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
    }

    /**
     * 补上“水平斜对角”这条原版邻居级联覆盖不到的依赖：本模组的规则 1、2 会读斜角位置是不是玻璃板，
     * 而 BlockStateBase#updateNeighbourShapes 只通知 6 个正交邻居，所以斜角上的玻璃板放置或拆除后
     * 本块不会被重算（表现就是要手动在边上放个方块才更新）。
     *
     * 这里照原版红石线（RedStoneWireBlock#updateIndirectNeighbourShapes）的做法，在“间接邻居”这一步
     * 把变化位置周围的 4 个水平斜角各走一遍 neighborShapeChanged，让它们重算 12 个部件属性；
     * 斜角那块要是真的变了，它自己又会照常级联下去，所以整条链能收敛。
     *
     * @param state 发生变化的方块状态（放置时是新状态、拆除时是旧状态，两种情况都能补到）
     * @param pos   发生变化的位置
     */
    public static void refreshDiagonals(LevelAccessor level, BlockState state, BlockPos pos,
                                        int flags, int recursionLeft) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Direction side = facing.getClockWise();
            BlockPos diagonal = new BlockPos(x + facing.getStepX() + side.getStepX(), y,
                    z + facing.getStepZ() + side.getStepZ());
            if (!PaneCornerSupport.isSupportedPane(level.getBlockState(diagonal))) {
                continue;
            }
            // 斜角与变化位置之间没有单一轴向，这里给的朝向只是让原版那一步的计算拿到一个合法邻居方向；
            // 本模组重算时直接读世界，不受这个朝向影响。
            level.neighborShapeChanged(side.getOpposite(), state, diagonal, pos, flags, recursionLeft);
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
