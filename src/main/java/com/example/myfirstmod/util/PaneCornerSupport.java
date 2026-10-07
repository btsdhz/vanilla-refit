package com.example.myfirstmod.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 玻璃板“部件化”形状的公共代码。
 *
 * 一块玻璃板由 12 个部件拼成（都靠方块状态控制，默认都不开，外观与原版一致）：
 *  - 4 个水平面片：方块四个角上 1/4 面大小、位于中间高度 8 像素处的水平玻璃，
 *    对应 btsdhz_ne / btsdhz_se / btsdhz_nw / btsdhz_sw；
 *  - 8 个竖直面片：原版的东西南北四个方向各自分成上下两半，每半是 8 高 × 8 长 × 2 厚，
 *    下半用原版属性 north / east / south / west（名字不变），
 *    上半用本模组的 btsdhz_north_up / east_up / south_up / west_up。
 * 所有部件的厚度都是 2 像素，与原版玻璃板自身板厚一致，模型与碰撞箱因此完全重合。
 *
 * 12 个属性全为 false 时保持原版形状与外观（中间那根立柱），所以老存档、没用调试棒改过的
 * 玻璃板不会发生任何变化。
 */
public final class PaneCornerSupport {

    /** 水平面片的上下表面（7~9 像素，中心正好在第 8 像素）。 */
    public static final double CORNER_MIN_Y = 7.0;
    public static final double CORNER_MAX_Y = 9.0;

    /** 东北角水平面片（+X / -Z）。 */
    private static final VoxelShape NORTH_EAST =
            Block.box(8.0, CORNER_MIN_Y, 0.0, 16.0, CORNER_MAX_Y, 8.0);
    /** 东南角水平面片（+X / +Z）。 */
    private static final VoxelShape SOUTH_EAST =
            Block.box(8.0, CORNER_MIN_Y, 8.0, 16.0, CORNER_MAX_Y, 16.0);
    /** 西北角水平面片（-X / -Z）。 */
    private static final VoxelShape NORTH_WEST =
            Block.box(0.0, CORNER_MIN_Y, 0.0, 8.0, CORNER_MAX_Y, 8.0);
    /** 西南角水平面片（-X / +Z）。 */
    private static final VoxelShape SOUTH_WEST =
            Block.box(0.0, CORNER_MIN_Y, 8.0, 8.0, CORNER_MAX_Y, 16.0);

    // 竖直面片：每个方向的下半（Y 0~8）与上半（Y 8~16），厚度 2 像素、长度 8 像素（伸到方块中线）。
    private static final VoxelShape NORTH_LOWER = Block.box(7.0, 0.0, 0.0, 9.0, 8.0, 8.0);
    private static final VoxelShape NORTH_UPPER = Block.box(7.0, 8.0, 0.0, 9.0, 16.0, 8.0);
    private static final VoxelShape EAST_LOWER = Block.box(8.0, 0.0, 7.0, 16.0, 8.0, 9.0);
    private static final VoxelShape EAST_UPPER = Block.box(8.0, 8.0, 7.0, 16.0, 16.0, 9.0);
    private static final VoxelShape SOUTH_LOWER = Block.box(7.0, 0.0, 8.0, 9.0, 8.0, 16.0);
    private static final VoxelShape SOUTH_UPPER = Block.box(7.0, 8.0, 8.0, 9.0, 16.0, 16.0);
    private static final VoxelShape WEST_LOWER = Block.box(0.0, 0.0, 7.0, 8.0, 8.0, 9.0);
    private static final VoxelShape WEST_UPPER = Block.box(0.0, 8.0, 7.0, 8.0, 16.0, 9.0);

    /**
     * 12 个部件合并出来的形状只与方块状态有关（与位置无关），按状态缓存一份，
     * 避免每 tick 的碰撞查询反复做形状布尔运算；空形状表示“保持原版形状”。
     */
    private static final Map<BlockState, VoxelShape> PIECE_SHAPES = new ConcurrentHashMap<>();

    private PaneCornerSupport() {
    }

    /**
     * 该方块状态是不是“参与部件化”的方块：原版命名空间的玻璃板、染色玻璃板与铁栏杆。
     *
     * 铁栏杆和玻璃板同属 IronBarsBlock、柱与横杆的尺寸也完全一致，所以共用同一套 12 个部件、
     * 形状与连接规则；区别只在“什么都没连”时用铁栏杆自己的原版外观（post_ends + post）与贴图。
     * 其它模组的玻璃板贴图命名不一定遵循原版规则，先不处理（避免出现只有碰撞箱、没有模型的隐形碰撞）。
     */
    public static boolean isSupportedPane(BlockState state) {
        Block block = state.getBlock();
        if (!(block instanceof IronBarsBlock)) {
            return false;
        }
        return BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft");
    }

    /** 该状态是否启用了任意一个角上的水平面片（栅栏式的上下面剔除规则要用）。 */
    public static boolean hasCorner(BlockState state) {
        if (!isSupportedPane(state) || !state.hasProperty(ModBlockStateProperties.PANE_NORTH_EAST)) {
            return false;
        }
        return state.getValue(ModBlockStateProperties.PANE_NORTH_EAST)
                || state.getValue(ModBlockStateProperties.PANE_SOUTH_EAST)
                || state.getValue(ModBlockStateProperties.PANE_NORTH_WEST)
                || state.getValue(ModBlockStateProperties.PANE_SOUTH_WEST);
    }

    /**
     * 玻璃板最终的拾取形状 / 碰撞形状（两者规则相同，只是传入的原版形状不同）。
     *
     * 12 个部件属性全为 false 时保持原版形状（中间那根立柱 + 已连接方向的横杆）；
     * 有一个为 true 时模型里就不再渲染立柱，形状改为由 12 个部件拼出来，和模型保持一致。
     *
     * @param currentShape 当前返回的形状（可能已被台阶舒适框叠加过与位置有关的形状）
     * @param vanillaShape 该状态在原版形状表里的形状，用来把叠加进来的那部分分离出来
     */
    public static VoxelShape adjustShape(VoxelShape currentShape, VoxelShape vanillaShape, BlockState state) {
        if (!state.hasProperty(ModBlockStateProperties.PANE_NORTH_EAST)) {
            // 栅栏、铁栏杆等没有这套属性，直接保持原样
            return currentShape;
        }
        VoxelShape pieces = PIECE_SHAPES.computeIfAbsent(state, PaneCornerSupport::buildPieceShape);
        if (pieces.isEmpty()) {
            return currentShape;
        }
        if (currentShape != vanillaShape) {
            // 邻格方块伸进本格的那部分（台阶舒适框）也要保留，否则准星会跳过它
            pieces = Shapes.or(pieces, Shapes.join(currentShape, vanillaShape, BooleanOp.ONLY_FIRST));
        }
        return pieces;
    }

    /** 把该状态下启用的部件逐个并起来；一个都没启用时返回空形状。 */
    private static VoxelShape buildPieceShape(BlockState state) {
        if (!isSupportedPane(state)) {
            return Shapes.empty();
        }
        VoxelShape shape = Shapes.empty();
        if (state.getValue(CrossCollisionBlock.NORTH)) {
            shape = Shapes.or(shape, NORTH_LOWER);
        }
        if (state.getValue(ModBlockStateProperties.PANE_NORTH_UP)) {
            shape = Shapes.or(shape, NORTH_UPPER);
        }
        if (state.getValue(CrossCollisionBlock.EAST)) {
            shape = Shapes.or(shape, EAST_LOWER);
        }
        if (state.getValue(ModBlockStateProperties.PANE_EAST_UP)) {
            shape = Shapes.or(shape, EAST_UPPER);
        }
        if (state.getValue(CrossCollisionBlock.SOUTH)) {
            shape = Shapes.or(shape, SOUTH_LOWER);
        }
        if (state.getValue(ModBlockStateProperties.PANE_SOUTH_UP)) {
            shape = Shapes.or(shape, SOUTH_UPPER);
        }
        if (state.getValue(CrossCollisionBlock.WEST)) {
            shape = Shapes.or(shape, WEST_LOWER);
        }
        if (state.getValue(ModBlockStateProperties.PANE_WEST_UP)) {
            shape = Shapes.or(shape, WEST_UPPER);
        }
        if (state.getValue(ModBlockStateProperties.PANE_NORTH_EAST)) {
            shape = Shapes.or(shape, NORTH_EAST);
        }
        if (state.getValue(ModBlockStateProperties.PANE_SOUTH_EAST)) {
            shape = Shapes.or(shape, SOUTH_EAST);
        }
        if (state.getValue(ModBlockStateProperties.PANE_NORTH_WEST)) {
            shape = Shapes.or(shape, NORTH_WEST);
        }
        if (state.getValue(ModBlockStateProperties.PANE_SOUTH_WEST)) {
            shape = Shapes.or(shape, SOUTH_WEST);
        }
        return shape;
    }
}
