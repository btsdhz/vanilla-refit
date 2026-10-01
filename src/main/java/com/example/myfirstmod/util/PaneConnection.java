package com.example.myfirstmod.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 玻璃板的自动连接逻辑（直接覆盖原版那套“只设置东西南北”的逻辑）。
 *
 * 规则按优先级从高到低如下（编号沿用需求里的表述顺序，编号越小优先级越高，
 * 也就是靠前的规则会覆盖靠后的规则）：
 *  1. 周围什么都没有 → 12 个属性全为 false（原版那根棍）。这是所有规则的起点，不需要特判；
 *  2. 某个水平方向有方块（判定与原版 IronBarsBlock 完全一致）→ 该方向的上半与下半都为真；
 *  3. 下方有方块 → 东南西北四个方向的下半部分全为真（下半就是原版的东西南北属性）；
 *  4. 上方有方块 → 东南西北四个方向的上半部分全为真；
 *  5. 上方和下方都有方块 → 四个角（东南/西南/东北/西北）一律为假；
 *  6. 相邻两个方向都是玻璃板 → 对应那个角为真
 *     （东+南=东南、南+西=西南、北+东=东北、北+西=西北），优先级最低。
 *
 * 所以代码里按 6 → 5 → 4 → 3 → 2 的顺序套用：先按最低优先级的角，再让高优先级的规则覆盖它。
 */
public final class PaneConnection {

    private PaneConnection() {
    }

    /** 按当前周围环境重算玻璃板的 12 个部件属性；不是本模组支持的玻璃板时原样返回。 */
    public static BlockState update(BlockState state, BlockGetter level, BlockPos pos) {
        if (!PaneCornerSupport.isSupportedPane(state)
                || !state.hasProperty(ModBlockStateProperties.PANE_NORTH_EAST)
                || !(state.getBlock() instanceof IronBarsBlock pane)) {
            return state;
        }

        // ===== 规则 6（优先级最低）：相邻两个方向都是玻璃板 → 角为真 =====
        boolean paneNorth = isPane(level, pos, Direction.NORTH);
        boolean paneEast = isPane(level, pos, Direction.EAST);
        boolean paneSouth = isPane(level, pos, Direction.SOUTH);
        boolean paneWest = isPane(level, pos, Direction.WEST);
        boolean northEast = paneNorth && paneEast;
        boolean southEast = paneSouth && paneEast;
        boolean southWest = paneSouth && paneWest;
        boolean northWest = paneNorth && paneWest;

        boolean hasAbove = hasBlock(level, pos.above());
        boolean hasBelow = hasBlock(level, pos.below());

        // ===== 规则 5：上下都有方块 → 四个角一律为假（覆盖规则 6）=====
        if (hasAbove && hasBelow) {
            northEast = false;
            southEast = false;
            southWest = false;
            northWest = false;
        }

        // ===== 规则 3 / 4：下方有方块 → 下半全真；上方有方块 → 上半全真 =====
        boolean northLower = hasBelow;
        boolean eastLower = hasBelow;
        boolean southLower = hasBelow;
        boolean westLower = hasBelow;
        boolean northUpper = hasAbove;
        boolean eastUpper = hasAbove;
        boolean southUpper = hasAbove;
        boolean westUpper = hasAbove;

        // ===== 规则 2（优先级最高）：该方向有方块（判定与原版一致）→ 该方向上下两半都为真 =====
        if (connectsTo(pane, level, pos, Direction.NORTH)) {
            northLower = true;
            northUpper = true;
        }
        if (connectsTo(pane, level, pos, Direction.EAST)) {
            eastLower = true;
            eastUpper = true;
        }
        if (connectsTo(pane, level, pos, Direction.SOUTH)) {
            southLower = true;
            southUpper = true;
        }
        if (connectsTo(pane, level, pos, Direction.WEST)) {
            westLower = true;
            westUpper = true;
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
        return level.getBlockState(pos.relative(direction)).getBlock() instanceof IronBarsBlock;
    }

    /** 该位置是不是“有方块”（非空气就算，含流体）。 */
    private static boolean hasBlock(BlockGetter level, BlockPos pos) {
        return !level.getBlockState(pos).isAir();
    }

    /** 与原版 IronBarsBlock 相同的连接判定：邻居是玻璃板/铁栏杆、墙，或该面是完整实心面。 */
    private static boolean connectsTo(IronBarsBlock pane, BlockGetter level, BlockPos pos, Direction direction) {
        BlockPos neighborPos = pos.relative(direction);
        BlockState neighbor = level.getBlockState(neighborPos);
        return pane.attachsTo(neighbor, neighbor.isFaceSturdy(level, neighborPos, direction.getOpposite()));
    }
}
