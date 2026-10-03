package com.example.myfirstmod.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 判断方块是否为“普通水平下半台阶”的工具。
 *
 * 原版 canSupportCenter 使用 SupportType.CENTER，只把“中心列实心”的方块视为可承载，
 * 因此普通下半台阶（中心列是空的）无法承载火把/灯笼，需要本模组额外放行。
 * 这里的“下半台阶”指：普通水平放置（MODE=SLAB）、且 TYPE=BOTTOM 的台阶；
 * 竖台阶、双台阶都不算，含水的下半台阶仍然算（FLUID_TYPE 不影响）。
 */
public final class SlabSupport {

    private SlabSupport() {
    }

    /**
     * @return 该台阶方块是否属于本模组支持的命名空间（minecraft / btsdhz_original）。
     * 只有这些台阶我们生成了竖台阶模型（btsdhz_original:block/vertical_slab_*_&lt;id&gt;），
     * 其它模组的台阶不应用本模组的竖放/液体/火把贴合等任何逻辑，保持原版行为。
     */
    public static boolean isSupportedSlab(Block block) {
        String namespace = BuiltInRegistries.BLOCK.getKey(block).getNamespace();
        return namespace.equals("minecraft") || namespace.equals("btsdhz_original");
    }

    /**
     * @return pos 处是否为普通水平下半台阶
     */
    public static boolean isBottomSlab(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof SlabBlock
                && isSupportedSlab(state.getBlock())
                && state.hasProperty(ModBlockStateProperties.MODE)
                && state.getValue(ModBlockStateProperties.MODE) == VerticalSlabMode.SLAB
                && state.hasProperty(SlabBlock.TYPE)
                && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
    }

    /**
     * @return pos 处是否为普通水平上半台阶
     */
    public static boolean isTopSlab(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof SlabBlock
                && isSupportedSlab(state.getBlock())
                && state.hasProperty(ModBlockStateProperties.MODE)
                && state.getValue(ModBlockStateProperties.MODE) == VerticalSlabMode.SLAB
                && state.hasProperty(SlabBlock.TYPE)
                && state.getValue(SlabBlock.TYPE) == SlabType.TOP;
    }

    /**
     * @return 该方块状态是否为“下移（ON_SLAB=true）的方块”（火把/灯笼/栅栏/墙）。
     */
    public static boolean isOnSlab(BlockState state) {
        return state.hasProperty(ModBlockStateProperties.ON_SLAB) && state.getValue(ModBlockStateProperties.ON_SLAB);
    }

    /**
     * @return 该方块状态是否为“上移（UNDER_TOP_SLAB=true）的方块”（灯笼/墙，贴合上台阶底面）。
     */
    public static boolean isUnderTopSlab(BlockState state) {
        return state.hasProperty(ModBlockStateProperties.UNDER_TOP_SLAB)
                && state.getValue(ModBlockStateProperties.UNDER_TOP_SLAB);
    }

    /**
     * @return pos 处的方块是否被本模组整体下移了半格（下台阶上的栅栏/墙等）。
     * 这类方块的“上表面”比它所在格子低半格，放在它上面的火把/灯笼也要跟着下移半格才不悬空。
     */
    public static boolean isLoweredBlock(BlockGetter level, BlockPos pos) {
        return isOnSlab(level.getBlockState(pos));
    }

    /**
     * @return pos 处的方块是否被本模组整体上移了半格（上台阶下的栅栏/墙等）。
     * 这类方块的“下表面”比它所在格子高半格，挂在它下面的灯笼也要跟着上移半格才不悬空。
     */
    public static boolean isRaisedBlock(BlockGetter level, BlockPos pos) {
        return isUnderTopSlab(level.getBlockState(pos));
    }

    /**
     * @return 本格是下移半格的方块，且正上方也是下移半格的方块
     * （例如下台阶上的栅栏/墙上再放火把、灯笼：上方方块整体下移后有一部分伸进本格）。
     */
    public static boolean hasLoweredBlockAbove(BlockGetter level, BlockPos pos) {
        return isOnSlab(level.getBlockState(pos)) && isOnSlab(level.getBlockState(pos.above()));
    }

    /**
     * @return 本格是上移半格的方块，且正下方也是上移半格的方块
     * （例如上台阶下的墙下面再挂灯笼：下方方块整体上移后有一部分伸进本格）。
     */
    public static boolean hasRaisedBlockBelow(BlockGetter level, BlockPos pos) {
        return isUnderTopSlab(level.getBlockState(pos)) && isUnderTopSlab(level.getBlockState(pos.below()));
    }

    /**
     * 叠在本格上方/下方、且同样位移了半格的方块伸进本格的那部分形状（换算到本格坐标）。
     *
     * <p>用途与半砖的“舒适框”一致：这种方块真正的碰撞箱有一半落在相邻格子里，
     * 只在它自己的那一格查形状会漏掉，所以并入本格的拾取形状，指着这半格时也能被准星选中。
     *
     * @return 需要并入的形状；没有这种叠放关系时返回 null
     */
    public static VoxelShape stackedNeighbourShape(BlockGetter level, BlockPos pos, CollisionContext context) {
        BlockState self = level.getBlockState(pos);
        if (isOnSlab(self)) {
            BlockPos abovePos = pos.above();
            BlockState above = level.getBlockState(abovePos);
            if (isOnSlab(above)) {
                return above.getShape(level, abovePos, context).move(0.0, 1.0, 0.0);
            }
        }
        if (isUnderTopSlab(self)) {
            BlockPos belowPos = pos.below();
            BlockState below = level.getBlockState(belowPos);
            if (isUnderTopSlab(below)) {
                return below.getShape(level, belowPos, context).move(0.0, -1.0, 0.0);
            }
        }
        return null;
    }

    /**
     * @return 该方块是否是“竖着放”的火把类（普通火把、灵魂火把、红石火把）。
     * 墙火把（含墙红石火把）贴在侧面，不跟着下方支撑面下移，所以不算。
     */
    public static boolean isStandingTorch(Block block) {
        return (block instanceof TorchBlock && !(block instanceof WallTorchBlock))
                || (block instanceof RedstoneTorchBlock && !(block instanceof RedstoneWallTorchBlock));
    }

    /**
     * 位移后的形状只由方块状态决定（调用点传进来的都是该状态对应的固定形状表项），
     * 而 getShape / getCollisionShape / getOcclusionShape 是碰撞与准星射线的高频路径，
     * 每次 {@code move} 都会新建一个形状，所以这里按状态缓存一份。
     */
    private static final Map<BlockState, VoxelShape> DOWN_SHIFTED_SHAPES = new ConcurrentHashMap<>();
    private static final Map<BlockState, VoxelShape> UP_SHIFTED_SHAPES = new ConcurrentHashMap<>();

    /**
     * 把方块形状整体下移半格（8/16 单位），用于“放在下台阶上并贴齐”的方块。
     */
    public static VoxelShape shiftDownHalf(BlockState state, VoxelShape shape) {
        return DOWN_SHIFTED_SHAPES.computeIfAbsent(state, key -> shape.move(0.0, -0.5, 0.0));
    }

    /**
     * 把方块形状整体上移半格（8/16 单位），用于“放在上台阶下方并贴齐”的方块。
     */
    public static VoxelShape shiftUpHalf(BlockState state, VoxelShape shape) {
        return UP_SHIFTED_SHAPES.computeIfAbsent(state, key -> shape.move(0.0, 0.5, 0.0));
    }

    /**
     * @return pos 上方的方块是否为“下移（ON_SLAB=true）的方块”（火把/灯笼/栅栏/墙等）。
     */
    public static boolean isLoweredOnSlabAbove(BlockGetter level, BlockPos pos) {
        BlockState above = level.getBlockState(pos.above());
        return above.hasProperty(ModBlockStateProperties.ON_SLAB)
                && above.getValue(ModBlockStateProperties.ON_SLAB);
    }

    /**
     * @return pos 下方的方块是否为“上移（UNDER_TOP_SLAB=true）的方块”（灯笼/墙，贴在上台阶下方）。
     */
    public static boolean isRaisedUnderTopSlabBelow(BlockGetter level, BlockPos pos) {
        return isUnderTopSlab(level.getBlockState(pos.below()));
    }

    /**
     * @return pos 处的方块是否为含熔岩的台阶（FLUID_TYPE == LAVA）。
     * 含水台阶不算（灯笼防水、火把火焰高于水面，仍可放置）；含熔岩的台阶不允许放置。
     */
    public static boolean isLavaSlab(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof SlabBlock
                && state.hasProperty(ModBlockStateProperties.FLUID_TYPE)
                && state.getValue(ModBlockStateProperties.FLUID_TYPE) == FluidType.LAVA;
    }
}
