package com.example.myfirstmod.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 玻璃板“四角水平面片”属性的公共代码（btsdhz_ne / btsdhz_se / btsdhz_nw / btsdhz_sw）。
 *
 * 属性为真时，在玻璃板中间高度（第 8 像素处）多出一块 1/4 方块面大小的水平玻璃面，
 * 位置靠向属性所表示的方向。面片是水平放置的，所以几何上必须有厚度：
 * 这里用 2 像素（7~9 像素，中心正好在第 8 像素），与原版玻璃板自身的板厚一致，
 * 模型与碰撞箱因此完全重合。
 *
 * 目前只做“模型 + 碰撞箱”，由调试棒手动设置属性；后续的自动连接逻辑也应复用这里的判定。
 */
public final class PaneCornerSupport {

    /** 面片下表面（7/16 格）。 */
    public static final double CORNER_MIN_Y = 7.0;
    /** 面片上表面（9/16 格）。 */
    public static final double CORNER_MAX_Y = 9.0;

    /** 东北（+X / -Z）。 */
    private static final VoxelShape NORTH_EAST =
            Block.box(8.0, CORNER_MIN_Y, 0.0, 16.0, CORNER_MAX_Y, 8.0);
    /** 东南（+X / +Z）。 */
    private static final VoxelShape SOUTH_EAST =
            Block.box(8.0, CORNER_MIN_Y, 8.0, 16.0, CORNER_MAX_Y, 16.0);
    /** 西北（-X / -Z）。 */
    private static final VoxelShape NORTH_WEST =
            Block.box(0.0, CORNER_MIN_Y, 0.0, 8.0, CORNER_MAX_Y, 8.0);
    /** 西南（-X / +Z）。 */
    private static final VoxelShape SOUTH_WEST =
            Block.box(0.0, CORNER_MIN_Y, 8.0, 8.0, CORNER_MAX_Y, 16.0);

    /**
     * 默认那根“棍”的中间立柱（原版 CrossCollisionBlock 的柱体：7~9 像素见方、满高）。
     * 玻璃板只要有一个方向属性为 true，数据生成出来的 blockstate 就不再渲染这根立柱，
     * 所以形状里也要把它减掉，碰撞箱才能和模型一致。
     */
    private static final VoxelShape POST = Block.box(7.0, 0.0, 7.0, 9.0, 16.0, 9.0);

    /**
     * 四角面片的形状只与方块状态有关，按状态缓存一份，避免每 tick 反复合并形状。
     * （“去掉立柱”那一步不能缓存：台阶舒适框会往返回值里叠加与位置有关的形状，
     * 所以那一步按次对当前返回值做差集。）
     */
    private static final Map<BlockState, VoxelShape> CORNER_SHAPES = new ConcurrentHashMap<>();

    private PaneCornerSupport() {
    }

    /**
     * 该方块状态是不是“参与四角面片”的玻璃板。
     *
     * 与数据生成的条件保持一致：原版命名空间、且不是铁栏杆（铁栏杆虽然同为 IronBarsBlock，
     * 但原版 blockstate 结构与贴图命名都不同，本功能不处理）。其它模组的玻璃板同样先不处理，
     * 避免出现只有碰撞箱、没有模型的隐形碰撞。
     */
    public static boolean isSupportedPane(BlockState state) {
        Block block = state.getBlock();
        if (!(block instanceof IronBarsBlock) || block == Blocks.IRON_BARS) {
            return false;
        }
        return BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft");
    }

    /**
     * 返回该状态上启用方向对应的水平面片形状；没有任何方向启用时返回 null。
     * 碰撞箱与拾取形状都用它，保证两者一致。
     */
    @Nullable
    public static VoxelShape cornerShape(BlockState state) {
        return CORNER_SHAPES.computeIfAbsent(state, PaneCornerSupport::buildCornerShape);
    }

    @Nullable
    private static VoxelShape buildCornerShape(BlockState state) {
        if (!hasCorner(state)) {
            return null;
        }
        VoxelShape shape = Shapes.empty();
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
        return shape.isEmpty() ? null : shape;
    }

    /** 该状态是否启用了任意一个方向的面片（未启用时各处都应当保持原版行为）。 */
    public static boolean hasCorner(BlockState state) {
        if (!isSupportedPane(state) || !state.hasProperty(ModBlockStateProperties.PANE_NORTH_EAST)) {
            return false;
        }
        return state.getValue(ModBlockStateProperties.PANE_NORTH_EAST)
                || state.getValue(ModBlockStateProperties.PANE_SOUTH_EAST)
                || state.getValue(ModBlockStateProperties.PANE_NORTH_WEST)
                || state.getValue(ModBlockStateProperties.PANE_SOUTH_WEST);
    }

    /** 八个方向属性（原版东西南北 + 本模组四个角）里是否有一个为 true。 */
    public static boolean hasAnyDirection(BlockState state) {
        if (hasCorner(state)) {
            return true;
        }
        if (!isSupportedPane(state) || !state.hasProperty(ModBlockStateProperties.PANE_NORTH_EAST)) {
            return false;
        }
        return state.getValue(CrossCollisionBlock.NORTH)
                || state.getValue(CrossCollisionBlock.EAST)
                || state.getValue(CrossCollisionBlock.SOUTH)
                || state.getValue(CrossCollisionBlock.WEST);
    }

    /**
     * 玻璃板最终的拾取形状 / 碰撞形状（两者规则相同，只是传入的原版形状不同）。
     *
     * 八个方向属性全为 false 时保持原版形状（中间立柱 + 已连接方向的横杆）；
     * 只要有一个方向属性为 true，模型里就不再渲染那根立柱，这里同步把立柱从形状里减掉，
     * 最后再并上启用的角上面片，保证碰撞箱与模型一致。
     *
     * @param currentShape 当前形状（可能是被台阶舒适框叠加过的返回值）
     */
    public static VoxelShape adjustShape(VoxelShape currentShape, BlockState state) {
        if (!isSupportedPane(state) || !state.hasProperty(ModBlockStateProperties.PANE_NORTH_EAST)) {
            return currentShape;
        }
        VoxelShape shape = hasAnyDirection(state)
                ? Shapes.join(currentShape, POST, BooleanOp.ONLY_FIRST)
                : currentShape;
        VoxelShape corners = cornerShape(state);
        return corners == null ? shape : Shapes.or(shape, corners);
    }
}
