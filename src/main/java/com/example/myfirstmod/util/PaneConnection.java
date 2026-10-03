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
 * ===== 当前规则（按优先级从高到低，靠前的会覆盖靠后的同名属性）=====
 *
 * | 编号 | 状态 | 规则 |
 * | --- | --- | --- |
 * | 1 | 启用（最高） | 水平面 8 个方向（东南西北 + 东北/东南/西北/西南）全是玻璃板 → 四个角全为真，8 个竖直面片全为假 |
 * | 2 | 启用 | 某个方向两侧的角同时为真 → 该方向的上半与下半为假（东北 + 西北 → 北上下为假；东南 + 西南 → 南；东北 + 东南 → 东；西北 + 西南 → 西） |
 * | 3 | 启用 | 相邻玻璃板的斜对角面片传播到本角（北邻的南西 + 本块西边是玻璃板 → 本块西北为真；北邻的南东 + 东边是玻璃板 → 本块东北为真；南/东/西同理） |
 * | 4 | 启用 | 某个水平方向有方块（判定与原版 IronBarsBlock 完全一致）时：上方有方块 → 该方向上半为真，下方有方块 → 该方向下半为真（没有方块的那一半保持假） |
 * | 5 | 启用 | 任意一个角为真 → 8 个竖直面片全为假 |
 * | 6 | 启用 | 某个水平方向有方块（判定与原版一致）→ 该方向的上半与下半都为真 |
 *
 * 起点（不属于上面任何一条）：12 个属性先全部置为假，周围什么都没有时就是原版那根棍。
 *
 * 实现说明：规则 2、4、5 都要读四角属性，所以代码里先算完四角（规则 3 与规则 1 的角部分），
 * 再算竖直面片（规则 6 → 5 → 4 → 2 → 规则 1 的竖直部分）。优先级只决定“写同一个属性时谁赢”，
 * 角属性与竖直面片是两组不同的属性，先算角不改变优先级结果。
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
        boolean hasAbove = hasBlock(level, pos.above());
        boolean hasBelow = hasBlock(level, pos.below());

        // 规则 1 的判定：水平面 8 个方向全是玻璃板
        boolean surroundedByPanes = paneNorth && paneEast && paneSouth && paneWest
                && isPaneAt(level, pos, 1, -1)
                && isPaneAt(level, pos, 1, 1)
                && isPaneAt(level, pos, -1, -1)
                && isPaneAt(level, pos, -1, 1);

        // 规则 6 / 规则 4 都要用：某个水平方向有没有方块（判定与原版一致）
        boolean connectNorth = connectsTo(pane, level, pos, Direction.NORTH);
        boolean connectEast = connectsTo(pane, level, pos, Direction.EAST);
        boolean connectSouth = connectsTo(pane, level, pos, Direction.SOUTH);
        boolean connectWest = connectsTo(pane, level, pos, Direction.WEST);

        // ==================== 第一组：四角属性 ====================
        boolean northEast = false;
        boolean southEast = false;
        boolean southWest = false;
        boolean northWest = false;

        // ----- 规则 3：相邻玻璃板的斜对角面片传播到本角 -----
        if (paneNorth) {
            BlockState north = level.getBlockState(pos.north());
            if (paneWest && isOn(north, ModBlockStateProperties.PANE_SOUTH_WEST)) {
                northWest = true;
            }
            if (paneEast && isOn(north, ModBlockStateProperties.PANE_SOUTH_EAST)) {
                northEast = true;
            }
        }
        if (paneSouth) {
            BlockState south = level.getBlockState(pos.south());
            if (paneWest && isOn(south, ModBlockStateProperties.PANE_NORTH_WEST)) {
                southWest = true;
            }
            if (paneEast && isOn(south, ModBlockStateProperties.PANE_NORTH_EAST)) {
                southEast = true;
            }
        }
        if (paneEast) {
            BlockState east = level.getBlockState(pos.east());
            if (paneNorth && isOn(east, ModBlockStateProperties.PANE_NORTH_WEST)) {
                northEast = true;
            }
            if (paneSouth && isOn(east, ModBlockStateProperties.PANE_SOUTH_WEST)) {
                southEast = true;
            }
        }
        if (paneWest) {
            BlockState west = level.getBlockState(pos.west());
            if (paneNorth && isOn(west, ModBlockStateProperties.PANE_NORTH_EAST)) {
                northWest = true;
            }
            if (paneSouth && isOn(west, ModBlockStateProperties.PANE_SOUTH_EAST)) {
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

        // ----- 规则 6（最低优先级）：该方向有方块 → 上下两半都为真 -----
        if (connectNorth) {
            northLower = true;
            northUpper = true;
        }
        if (connectEast) {
            eastLower = true;
            eastUpper = true;
        }
        if (connectSouth) {
            southLower = true;
            southUpper = true;
        }
        if (connectWest) {
            westLower = true;
            westUpper = true;
        }

        // ----- 规则 5：任意一个角为真 → 8 个竖直面片全为假 -----
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

        // ----- 规则 4：该方向有方块时，只有上方/下方真有方块的那一半才为真 -----
        if (connectNorth) {
            northLower = hasBelow;
            northUpper = hasAbove;
        }
        if (connectEast) {
            eastLower = hasBelow;
            eastUpper = hasAbove;
        }
        if (connectSouth) {
            southLower = hasBelow;
            southUpper = hasAbove;
        }
        if (connectWest) {
            westLower = hasBelow;
            westUpper = hasAbove;
        }

        // ----- 规则 2：某个方向两侧的角同时为真 → 该方向上下两半为假 -----
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
        return level.getBlockState(pos.relative(direction)).getBlock() instanceof IronBarsBlock;
    }

    /** 水平方向上偏移 (dx, 0, dz) 的位置是不是玻璃板（用于四个斜角）。 */
    private static boolean isPaneAt(BlockGetter level, BlockPos pos, int dx, int dz) {
        return level.getBlockState(pos.offset(dx, 0, dz)).getBlock() instanceof IronBarsBlock;
    }

    /** 该位置是不是“有方块”（非空气就算，含流体）。 */
    private static boolean hasBlock(BlockGetter level, BlockPos pos) {
        return !level.getBlockState(pos).isAir();
    }

    private static boolean isOn(BlockState state, Property<Boolean> property) {
        return state.hasProperty(property) && state.getValue(property);
    }

    /** 与原版 IronBarsBlock 相同的连接判定：邻居是玻璃板/铁栏杆、墙，或该面是完整实心面。 */
    private static boolean connectsTo(IronBarsBlock pane, BlockGetter level, BlockPos pos, Direction direction) {
        BlockPos neighborPos = pos.relative(direction);
        BlockState neighbor = level.getBlockState(neighborPos);
        return pane.attachsTo(neighbor, neighbor.isFaceSturdy(level, neighborPos, direction.getOpposite()));
    }
}
