package com.example.myfirstmod.util;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.function.UnaryOperator;

/**
 * 给本模组注入的“方向性属性”补上原版 {@code BlockState#mirror} / {@code BlockState#rotate} 语义。
 *
 * <p>本模组的竖半砖、竖楼梯是往原版方块里加属性做出来的，原版方块自己的 mirror/rotate 只认识
 * 原版属性：竖半砖用 {@code btsdhz_mode} + {@code type} 表示朝向，竖楼梯用 {@code facing} +
 * {@code btsdhz_connection} 表示朝向与连接侧，这两组属性都落在原版实现之外，于是出现
 * “对称/旋转之后形态没跟着变”的现象（例如机械动力创造之杖的对称模式、结构方块的镜像/旋转、
 * WorldEdit 之类）。
 *
 * <p>这里只做“原版算完之后再补一刀”的纯状态换算，不碰任何原版自己会处理的属性
 * （竖半砖的 type 只在竖放形态下才当作朝向看待，平放台阶的上下半不会被误翻）。
 */
public final class SymmetrySupport {

    private static final Direction[] HORIZONTALS =
            { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };

    private SymmetrySupport() {
    }

    /**
     * 原版 {@code BlockState#mirror} 的结果再补上本模组属性的镜像。
     *
     * <p>必须同时拿到镜像前（{@code original}）与镜像后（{@code mirrored}）两份状态：
     * 原版 {@code StairBlock#mirror} 会自己把 facing 转 180°（东西朝向时），它算出来的 facing
     * 是按“原版楼梯”语义给的，对竖楼梯的象限语义不成立，拿它当输入再换算一次会连错两个方向。
     */
    public static BlockState mirror(BlockState original, BlockState mirrored, Mirror mirror) {
        if (mirror == Mirror.NONE) {
            return mirrored;
        }
        BlockState result = mirrored;

        Direction slabSide = verticalSlabSide(original);
        if (slabSide != null) {
            result = withVerticalSlabSide(result, mirror.mirror(slabSide));
        }

        if (isVerticalStair(original)) {
            // 竖楼梯的 facing 是“L 形中心所在象限”的对角方向，镜像后落在相邻象限，
            // 这跟原版楼梯（沿 Z 轴朝向时左右对称）的规则不同，必须单独换算。
            result = result
                    .setValue(StairBlock.FACING,
                            mirroredStairFacing(original.getValue(StairBlock.FACING), mirror))
                    // 连接形态存在 shape 载体里：原版那份镜像只把 INNER_LEFT↔INNER_RIGHT（左右对，
                    // 正好是需要的），OUTER_LEFT↔OUTER_RIGHT 会把拐角与双面连反对，所以这里按
                    // 镜像前的状态重算一遍。
                    .setValue(StairBlock.SHAPE,
                            mirroredConnection(StairConnection.of(original)).toShape());
        }
        result = transformPane(result, original, mirror::mirror);
        return result;
    }

    /** 原版 {@code BlockState#rotate} 的结果再补上本模组属性的旋转。 */
    public static BlockState rotate(BlockState original, BlockState rotated, Rotation rotation) {
        if (rotation == Rotation.NONE) {
            return rotated;
        }
        Direction slabSide = verticalSlabSide(original);
        BlockState result = rotated;
        // 竖楼梯的连接形态是相对 facing 的（左/右），旋转不改手性，原版转 facing 已经够了。
        if (slabSide != null) {
            result = withVerticalSlabSide(result, rotation.rotate(slabSide));
        }
        return transformPane(result, original, rotation::rotate);
    }

    // ===== 玻璃板 / 铁栅栏：本模组加的东西南北上半、四个角的面片 =====

    // 原版那 4 个东西南北横片由 CrossCollisionBlock 自己的 mirror/rotate 处理，这里只搬本模组加的属性。

    /**
     * @param move 老方向 → 变换后的方向（镜像用 {@link Mirror#mirror(Direction)}，旋转用
     *             {@link Rotation#rotate(Direction)}），与原版 CrossCollisionBlock 的方向搬运方向一致
     */
    private static BlockState transformPane(BlockState result, BlockState original,
                                            UnaryOperator<Direction> move) {
        if (!original.hasProperty(ModBlockStateProperties.PANE_NORTH_UP)) {
            return result;
        }
        BlockState out = result;
        for (Direction dir : HORIZONTALS) {
            Property<Boolean> to = paneUpProperty(move.apply(dir));
            Property<Boolean> from = paneUpProperty(dir);
            if (to != null && from != null) {
                out = out.setValue(to, original.getValue(from));
            }
        }
        out = out.setValue(paneCornerProperty(move.apply(Direction.NORTH), move.apply(Direction.EAST)),
                original.getValue(ModBlockStateProperties.PANE_NORTH_EAST));
        out = out.setValue(paneCornerProperty(move.apply(Direction.NORTH), move.apply(Direction.WEST)),
                original.getValue(ModBlockStateProperties.PANE_NORTH_WEST));
        out = out.setValue(paneCornerProperty(move.apply(Direction.SOUTH), move.apply(Direction.EAST)),
                original.getValue(ModBlockStateProperties.PANE_SOUTH_EAST));
        out = out.setValue(paneCornerProperty(move.apply(Direction.SOUTH), move.apply(Direction.WEST)),
                original.getValue(ModBlockStateProperties.PANE_SOUTH_WEST));
        return out;
    }

    private static Property<Boolean> paneUpProperty(Direction dir) {
        return switch (dir) {
            case NORTH -> ModBlockStateProperties.PANE_NORTH_UP;
            case EAST -> ModBlockStateProperties.PANE_EAST_UP;
            case SOUTH -> ModBlockStateProperties.PANE_SOUTH_UP;
            case WEST -> ModBlockStateProperties.PANE_WEST_UP;
            default -> null;
        };
    }

    /** 由两个互相垂直的水平方向取出对应的角落属性（属性只按“北/南 + 东/西”命名，与顺序无关）。 */
    private static Property<Boolean> paneCornerProperty(Direction a, Direction b) {
        boolean north = a == Direction.NORTH || b == Direction.NORTH;
        boolean south = a == Direction.SOUTH || b == Direction.SOUTH;
        boolean east = a == Direction.EAST || b == Direction.EAST;
        boolean west = a == Direction.WEST || b == Direction.WEST;
        if (north && east) {
            return ModBlockStateProperties.PANE_NORTH_EAST;
        }
        if (north && west) {
            return ModBlockStateProperties.PANE_NORTH_WEST;
        }
        if (south && east) {
            return ModBlockStateProperties.PANE_SOUTH_EAST;
        }
        if (south && west) {
            return ModBlockStateProperties.PANE_SOUTH_WEST;
        }
        return ModBlockStateProperties.PANE_NORTH_EAST;   // 理论上到不了，兜底避免 NPE
    }

    // ===== 竖半砖：朝向 = (mode 轴, type 侧) =====

    /**
     * 竖半砖贴住的那一侧；平放台阶（含 type=double 的异常状态）返回 null，表示不需要换算。
     * type 在这里被当成“南北 / 东西里的哪一侧”用，与 {@code SlabBlockMixin#getShape} 一致。
     */
    private static Direction verticalSlabSide(BlockState state) {
        if (!state.hasProperty(ModBlockStateProperties.MODE)
                || !state.hasProperty(SlabBlock.TYPE)) {
            return null;
        }
        VerticalSlabMode mode = state.getValue(ModBlockStateProperties.MODE);
        if (mode == VerticalSlabMode.SLAB) {
            return null;
        }
        SlabType type = state.getValue(SlabBlock.TYPE);
        if (type == SlabType.DOUBLE) {
            return null;
        }
        boolean bottom = type == SlabType.BOTTOM;
        if (mode == VerticalSlabMode.VERTICAL_NS) {
            return bottom ? Direction.NORTH : Direction.SOUTH;
        }
        return bottom ? Direction.WEST : Direction.EAST;
    }

    /** 把竖半砖换算成“贴住 side 这一侧”的状态。 */
    private static BlockState withVerticalSlabSide(BlockState state, Direction side) {
        if (side.getAxis() == Direction.Axis.Y) {
            return state;   // 水平镜像/旋转不会把竖半砖变成平放台阶
        }
        VerticalSlabMode mode = side.getAxis() == Direction.Axis.Z
                ? VerticalSlabMode.VERTICAL_NS
                : VerticalSlabMode.VERTICAL_EW;
        SlabType type = side == Direction.NORTH || side == Direction.WEST
                ? SlabType.BOTTOM
                : SlabType.TOP;
        return state
                .setValue(ModBlockStateProperties.MODE, mode)
                .setValue(SlabBlock.TYPE, type);
    }

    // ===== 竖楼梯：facing（L 中心象限的对角）+ 连接侧 =====

    private static boolean isVerticalStair(BlockState state) {
        return state.hasProperty(ModBlockStateProperties.VERTICAL)
                && state.hasProperty(StairBlock.FACING)
                && state.hasProperty(StairBlock.SHAPE)
                && state.getValue(ModBlockStateProperties.VERTICAL);
    }

    /**
     * 竖楼梯 facing 的镜像：facing 指向 L 形中心所在象限的对角方向
     * （北 = 西北象限、东 = 东北象限……，见 {@code StairBlockMixin#getFacingFromQuadrant}），
     * 所以镜像后换到相邻的象限，而不是像原版那样只翻南北或只翻东西。
     */
    private static Direction mirroredStairFacing(Direction facing, Mirror mirror) {
        return switch (mirror) {
            // 镜像 X 轴：西北象限 ↔ 东北象限、西南 ↔ 东南
            case FRONT_BACK -> switch (facing) {
                case NORTH -> Direction.EAST;
                case EAST -> Direction.NORTH;
                case SOUTH -> Direction.WEST;
                case WEST -> Direction.SOUTH;
                default -> facing;
            };
            // 镜像 Z 轴：西北 ↔ 西南、东北 ↔ 东南
            case LEFT_RIGHT -> switch (facing) {
                case NORTH -> Direction.WEST;
                case WEST -> Direction.NORTH;
                case EAST -> Direction.SOUTH;
                case SOUTH -> Direction.EAST;
                default -> facing;
            };
            default -> facing;
        };
    }

    /** 连接侧是相对 facing 的左右手性，镜像一律左右互换；拐角的上下与双面连接都不换。 */
    private static StairConnection mirroredConnection(StairConnection conn) {
        return switch (conn) {
            case CONN_LEFT -> StairConnection.CONN_RIGHT;
            case CONN_RIGHT -> StairConnection.CONN_LEFT;
            default -> conn;
        };
    }
}
