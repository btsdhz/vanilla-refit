package com.example.myfirstmod.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * 玻璃板的自动连接逻辑（直接覆盖原版那套“只设置东西南北”的逻辑）。
 *
 * ===== 当前规则（按优先级从高到低；靠前的会覆盖靠后的，代码里按相反顺序执行）=====
 *  规则 1（启用，优先级最高）：水平面 8 个方向（东南西北 + 东北/东南/西北/西南）全是玻璃板
 *      → 四个角全为真，东南西北各自的上下（8 个竖直面片）全为假。
 *  规则 2（启用）：某个水平方向有方块（判定与原版 IronBarsBlock 完全一致：
 *      玻璃板/铁栏杆、墙，或该面是完整实心面）→ 该方向的上半与下半都为真。
 *  规则 3（启用，但当前不改变任何值）：相邻玻璃板朝向本角的竖片
 *      （上半或下半任意一个）为真时本角为假。规则 5 停用后角初始就是假，这条暂时无效，
 *      等规则 5 恢复后才会起作用。
 *  规则 4（停用，代码保留）：上方和下方都有方块 → 四个角一律为假。
 *  规则 5（停用，代码保留）：相邻两个方向都是玻璃板 → 对应角为真。
 *  起点（不属于上面任何一条）：12 个属性先全部置为假，周围什么都没有时就是原版那根棍。
 *
 * 每次改规则都要同步更新这份清单，并在回复里列出当前规则。
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

        boolean paneNorth = isPane(level, pos, Direction.NORTH);
        boolean paneEast = isPane(level, pos, Direction.EAST);
        boolean paneSouth = isPane(level, pos, Direction.SOUTH);
        boolean paneWest = isPane(level, pos, Direction.WEST);

        // ===== 规则 5（停用，代码保留）：相邻两个方向都是玻璃板 → 角为真 =====
        // boolean northEast = paneNorth && paneEast;
        // boolean southEast = paneSouth && paneEast;
        // boolean southWest = paneSouth && paneWest;
        // boolean northWest = paneNorth && paneWest;
        boolean northEast = false;
        boolean southEast = false;
        boolean southWest = false;
        boolean northWest = false;

        // ===== 规则 3：相邻玻璃板朝本角的竖片为真 → 本角为假 =====
        if (paneNorth) {
            // 北边那块玻璃板朝东（上或下）为真 → 本块东北为假；朝西为真 → 本块西北为假
            if (hasSidePiece(level, pos.north(), Direction.EAST)) {
                northEast = false;
            }
            if (hasSidePiece(level, pos.north(), Direction.WEST)) {
                northWest = false;
            }
        }
        if (paneSouth) {
            if (hasSidePiece(level, pos.south(), Direction.EAST)) {
                southEast = false;
            }
            if (hasSidePiece(level, pos.south(), Direction.WEST)) {
                southWest = false;
            }
        }
        if (paneEast) {
            if (hasSidePiece(level, pos.east(), Direction.NORTH)) {
                northEast = false;
            }
            if (hasSidePiece(level, pos.east(), Direction.SOUTH)) {
                southEast = false;
            }
        }
        if (paneWest) {
            if (hasSidePiece(level, pos.west(), Direction.NORTH)) {
                northWest = false;
            }
            if (hasSidePiece(level, pos.west(), Direction.SOUTH)) {
                southWest = false;
            }
        }

        // ===== 规则 4（停用，代码保留）：上下都有方块 → 四个角一律为假 =====
        // boolean hasAbove = hasBlock(level, pos.above());
        // boolean hasBelow = hasBlock(level, pos.below());
        // if (hasAbove && hasBelow) {
        //     northEast = false;
        //     southEast = false;
        //     southWest = false;
        //     northWest = false;
        // }

        // ===== 竖直面片：先全假，只有规则 2 会点亮 =====
        boolean northLower = false;
        boolean eastLower = false;
        boolean southLower = false;
        boolean westLower = false;
        boolean northUpper = false;
        boolean eastUpper = false;
        boolean southUpper = false;
        boolean westUpper = false;

        // ===== 规则 2：该方向有方块（判定与原版一致）→ 该方向上下两半都为真 =====
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

        // ===== 规则 1（优先级最高）：水平面 8 个方向全是玻璃板
        //       → 四个角全为真，东南西北的上下（8 个竖直面片）全为假 =====
        if (paneNorth && paneEast && paneSouth && paneWest
                && isPaneAt(level, pos, 1, -1)
                && isPaneAt(level, pos, 1, 1)
                && isPaneAt(level, pos, -1, -1)
                && isPaneAt(level, pos, -1, 1)) {
            northLower = false;
            eastLower = false;
            southLower = false;
            westLower = false;
            northUpper = false;
            eastUpper = false;
            southUpper = false;
            westUpper = false;
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

    /** 该方向是不是玻璃板（玻璃板/染色玻璃板/铁栏杆，与原版 attachsTo 的口径一致）。 */
    private static boolean isPane(BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockState(pos.relative(direction)).getBlock() instanceof IronBarsBlock;
    }

    /** 水平方向上偏移 (dx, 0, dz) 的位置是不是玻璃板（用于四个斜角）。 */
    private static boolean isPaneAt(BlockGetter level, BlockPos pos, int dx, int dz) {
        return level.getBlockState(pos.offset(dx, 0, dz)).getBlock() instanceof IronBarsBlock;
    }

    /** 该位置是不是“有方块”（非空气就算，含流体）。规则 4 使用，随规则 4 一起停用。 */
    @SuppressWarnings("unused")
    private static boolean hasBlock(BlockGetter level, BlockPos pos) {
        return !level.getBlockState(pos).isAir();
    }

    /**
     * 指定位置那块玻璃板在 direction 方向的竖片是否启用（上半或下半任意一个为真）。
     * 例如 direction=EAST 时看的就是它的 east 与 btsdhz_east_up。
     */
    private static boolean hasSidePiece(BlockGetter level, BlockPos panePos, Direction direction) {
        BlockState state = level.getBlockState(panePos);
        return switch (direction) {
            case NORTH -> isOn(state, CrossCollisionBlock.NORTH, ModBlockStateProperties.PANE_NORTH_UP);
            case EAST -> isOn(state, CrossCollisionBlock.EAST, ModBlockStateProperties.PANE_EAST_UP);
            case SOUTH -> isOn(state, CrossCollisionBlock.SOUTH, ModBlockStateProperties.PANE_SOUTH_UP);
            case WEST -> isOn(state, CrossCollisionBlock.WEST, ModBlockStateProperties.PANE_WEST_UP);
            default -> false;
        };
    }

    private static boolean isOn(BlockState state, Property<Boolean> lower, Property<Boolean> upper) {
        return (state.hasProperty(lower) && state.getValue(lower))
                || (state.hasProperty(upper) && state.getValue(upper));
    }

    /** 与原版 IronBarsBlock 相同的连接判定：邻居是玻璃板/铁栏杆、墙，或该面是完整实心面。 */
    private static boolean connectsTo(IronBarsBlock pane, BlockGetter level, BlockPos pos, Direction direction) {
        BlockPos neighborPos = pos.relative(direction);
        BlockState neighbor = level.getBlockState(neighborPos);
        return pane.attachsTo(neighbor, neighbor.isFaceSturdy(level, neighborPos, direction.getOpposite()));
    }
}
