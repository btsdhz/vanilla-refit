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
 * | 2 | 启用 | 本块上方和下方都没有玻璃板时：某两个方向以及它们之间的斜角都有“原版会和玻璃板连接的方块”（玻璃板/铁栏杆、墙，或朝本块那一面是完整实心面）→ 对应角为真（例：北 + 西 + 西北都有这种方块 → 本块西北为真；东南/东北/西南同理） |
 * | 3 | 启用 | 某个方向两侧的角同时为真 → 该方向的上半与下半为假（东北 + 西北 → 北上下；东南 + 西南 → 南；东北 + 东南 → 东；西北 + 西南 → 西） |
 * | 4 | 启用 | 相邻玻璃板的斜对角面片传播到本角（北邻的南西 + 本块西边是玻璃板 → 本块西北为真；北邻的南东 + 东边是玻璃板 → 本块东北为真；南/东/西同理） |
 * | 5 | 启用 | 某个水平方向有方块（判定与原版 IronBarsBlock 完全一致）、且通过邻居玻璃板限制时：上方有方块 → 该方向上半置真，下方有方块 → 该方向下半置真；只置真，不会把没方块的那一半置假 |
 * | 6 | 启用 | 任意一个角为真 → 8 个竖直面片全为假 |
 * | 7 | 启用 | 某个水平方向有方块（判定与原版一致）、且通过邻居玻璃板限制 → 该方向的上半与下半都为真 |
 *
 * 规则 5、7 的邻居玻璃板限制（限制的是“对应方向上的那块玻璃板”，不是当前这块）：
 *  - 邻居不是玻璃板（墙、完整方块等）：不检测，直接通过；
 *  - 邻居是玻璃板但它四个角（东北/东南/西北/西南）全是假：不检测，直接通过；
 *  - 邻居是玻璃板且四个角有任意一个为真：才去检测它朝向本块的那一侧
 *    （本方向的反方向）上半或下半是否为真，为真才通过。
 *
 * 起点（不属于上面任何一条）：12 个属性先全部置为假，周围什么都没有时就是原版那根棍。
 *
 * 实现说明：规则 3、5、6 都要读四角属性，所以代码里先算完四角（规则 4 → 规则 2 → 规则 1 的角部分），
 * 再算竖直面片（规则 7 → 6 → 5 → 3 → 规则 1 的竖直部分）。优先级只决定“写同一个属性时谁赢”，
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
        boolean paneNorthEast = isPaneAt(level, pos, 1, -1);
        boolean paneSouthEast = isPaneAt(level, pos, 1, 1);
        boolean paneNorthWest = isPaneAt(level, pos, -1, -1);
        boolean paneSouthWest = isPaneAt(level, pos, -1, 1);

        // 规则 2 用的四个斜角：是不是“原版玻璃板会连接的方块”
        boolean linkNorthEast = diagonalConnects(pane, level, pos, 1, -1);
        boolean linkSouthEast = diagonalConnects(pane, level, pos, 1, 1);
        boolean linkNorthWest = diagonalConnects(pane, level, pos, -1, -1);
        boolean linkSouthWest = diagonalConnects(pane, level, pos, -1, 1);

        boolean hasAbove = hasBlock(level, pos.above());
        boolean hasBelow = hasBlock(level, pos.below());
        // 规则 2 的前提：本块上方和下方都没有玻璃板
        boolean noPaneAboveOrBelow = !isPaneAt(level, pos.above()) && !isPaneAt(level, pos.below());
        // 规则 1 的判定：水平面 8 个方向全是玻璃板
        boolean surroundedByPanes = paneNorth && paneEast && paneSouth && paneWest
                && paneNorthEast && paneSouthEast && paneNorthWest && paneSouthWest;

        // 规则 5 / 规则 7 都要用：某个水平方向有没有方块（判定与原版一致）
        boolean connectNorth = connectsTo(pane, level, pos, Direction.NORTH);
        boolean connectEast = connectsTo(pane, level, pos, Direction.EAST);
        boolean connectSouth = connectsTo(pane, level, pos, Direction.SOUTH);
        boolean connectWest = connectsTo(pane, level, pos, Direction.WEST);
        // 规则 5 / 规则 7 的邻居玻璃板限制
        boolean faceNorth = connectNorth && neighbourFacesBack(level, pos, Direction.NORTH);
        boolean faceEast = connectEast && neighbourFacesBack(level, pos, Direction.EAST);
        boolean faceSouth = connectSouth && neighbourFacesBack(level, pos, Direction.SOUTH);
        boolean faceWest = connectWest && neighbourFacesBack(level, pos, Direction.WEST);

        // ==================== 第一组：四角属性 ====================
        boolean northEast = false;
        boolean southEast = false;
        boolean southWest = false;
        boolean northWest = false;

        // ----- 规则 4：相邻玻璃板的斜对角面片传播到本角 -----
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

        // ----- 规则 2：上下都没有玻璃板时，两个方向 + 它们之间的斜角都有会连接的方块 → 该角为真 -----
        if (noPaneAboveOrBelow) {
            if (connectNorth && connectEast && linkNorthEast) {
                northEast = true;
            }
            if (connectSouth && connectEast && linkSouthEast) {
                southEast = true;
            }
            if (connectNorth && connectWest && linkNorthWest) {
                northWest = true;
            }
            if (connectSouth && connectWest && linkSouthWest) {
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

        // ----- 规则 7（最低优先级）：该方向有方块 → 上下两半都为真 -----
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

        // ----- 规则 6：任意一个角为真 → 8 个竖直面片全为假 -----
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

        // ----- 规则 5：该方向有方块时，上方有方块则该方向上安置真、下方有方块则下安置真（只加真）-----
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

        // ----- 规则 3：某个方向两侧的角同时为真 → 该方向上下两半为假 -----
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

    /**
     * 斜角（dx, 0, dz）位置的方块是不是“原版玻璃板会连接的方块”：
     * 玻璃板/铁栏杆、墙，或者它朝本块那一侧的两个面之一是完整实心面（口径与原版 attachsTo 一致）。
     */
    private static boolean diagonalConnects(IronBarsBlock pane, BlockGetter level, BlockPos pos, int dx, int dz) {
        BlockPos diagonalPos = pos.offset(dx, 0, dz);
        BlockState diagonal = level.getBlockState(diagonalPos);
        Direction faceOnX = dx > 0 ? Direction.WEST : Direction.EAST;
        Direction faceOnZ = dz > 0 ? Direction.NORTH : Direction.SOUTH;
        boolean solid = diagonal.isFaceSturdy(level, diagonalPos, faceOnX)
                || diagonal.isFaceSturdy(level, diagonalPos, faceOnZ);
        return pane.attachsTo(diagonal, solid);
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
     * 规则 5、7 的邻居玻璃板限制：限制的是“对应方向上的那块玻璃板”。
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
}
