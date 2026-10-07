package com.example.myfirstmod.util;

import com.example.myfirstmod.config.BtsdhzConfig;
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
import net.minecraft.world.phys.shapes.Shapes;
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
     * @return 该台阶方块是否属于本模组支持的范围（原版 / 本模组注册的 / 打开开关后的其它模组台阶）。
     * 其它模组的台阶默认不参与本模组的竖放、液体与火把贴合逻辑，保持原版行为。
     *
     * <p>配置里打开 {@code allowModdedSlabs} 之后，其它模组的台阶也算"支持"：
     * 竖形态由客户端在模型烘焙后按本模组的模板几何包裹生成
     * （见 {@code client/model/RetexturedTemplateModel}），不落盘、不预生成模型。
     */
    public static boolean isSupportedSlab(Block block) {
        String namespace = BuiltInRegistries.BLOCK.getKey(block).getNamespace();
        if (namespace.equals("minecraft") || namespace.equals("btsdhz_original")) {
            return true;
        }
        return allowModdedSlabs();
    }

    /**
     * @return 该楼梯方块是否属于本模组支持的范围（原版 / 本模组注册的 / 打开开关后的其它模组楼梯）。
     *
     * <p>其它模组的楼梯在客户端会改用「本模组的模板几何 + 对方的贴图」渲染
     * （见 {@code client/model/RetexturedTemplateModel}），所以连接形态同样可用。
     * 配置里关掉 {@code allowModdedStairs} 时它们保持原版行为。
     */
    public static boolean isSupportedStair(Block block) {
        String namespace = BuiltInRegistries.BLOCK.getKey(block).getNamespace();
        if (namespace.equals("minecraft") || namespace.equals("btsdhz_original")) {
            return true;
        }
        return allowModdedStairs();
    }

    /** 读取实验开关；配置还没加载时按关闭处理。 */
    private static boolean allowModdedSlabs() {
        try {
            return BtsdhzConfig.ALLOW_MODDED_SLABS.get();
        } catch (IllegalStateException notLoadedYet) {
            return false;
        }
    }

    /** 读取实验开关；配置还没加载时按关闭处理。 */
    private static boolean allowModdedStairs() {
        try {
            return BtsdhzConfig.ALLOW_MODDED_STAIRS.get();
        } catch (IllegalStateException notLoadedYet) {
            return false;
        }
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
     * @return 该状态的“贴台阶”情况：墙/栅栏/灯笼读 {@code btsdhz_slab_offset} 三值枚举，
     * 火把类读 {@code btsdhz_on_slab} 布尔（它们只有“下台阶上”这一种情况）。
     */
    public static SlabOffset offset(BlockState state) {
        if (state.hasProperty(ModBlockStateProperties.SLAB_OFFSET)) {
            return state.getValue(ModBlockStateProperties.SLAB_OFFSET);
        }
        if (state.hasProperty(ModBlockStateProperties.ON_SLAB) && state.getValue(ModBlockStateProperties.ON_SLAB)) {
            return SlabOffset.LOWERED;
        }
        return SlabOffset.NONE;
    }

    /**
     * @return 该方块状态是否为“整体下移了半格的方块”（下台阶上的火把/灯笼/栅栏/墙）。
     */
    public static boolean isOnSlab(BlockState state) {
        return offset(state) == SlabOffset.LOWERED;
    }

    /**
     * @return 该方块状态是否为“整体上移了半格的方块”（上台阶下的灯笼/栅栏/墙）。
     */
    public static boolean isUnderTopSlab(BlockState state) {
        return offset(state) == SlabOffset.RAISED;
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
     * @return 该位置的方块"贴台阶"情况：下方是下台阶 → {@link SlabOffset#LOWERED}；
     * 否则上方是上台阶 → {@link SlabOffset#RAISED}；都不是 → {@link SlabOffset#NONE}。
     * 一个方块不可能同时贴合上下两个台阶（位移方向互斥），所以下台阶优先。
     */
    public static SlabOffset offsetAt(BlockGetter level, BlockPos pos) {
        if (isBottomSlab(level, pos.below())) {
            return SlabOffset.LOWERED;
        }
        return isTopSlab(level, pos.above()) ? SlabOffset.RAISED : SlabOffset.NONE;
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
     * 叠在本格上方/下方、且同样位移了半格的方块：方块状态 + 已换算到本格坐标的形状。
     *
     * <p>用途与半砖的“舒适框”一致：这种方块真正的碰撞箱有一半落在相邻格子里，
     * 只在它自己的那一格查形状会漏掉，所以并入本格的拾取形状，指着这半格时也能被准星选中。
     */
    public record StackedNeighbour(BlockState state, VoxelShape shape, VoxelShape collision) {
    }

    /**
     * @return 需要并入本格的邻居方块；没有“上下叠着位移方块”这种关系时返回 null
     */
    public static StackedNeighbour stackedNeighbour(BlockGetter level, BlockPos pos, CollisionContext context) {
        BlockState self = level.getBlockState(pos);
        if (isOnSlab(self)) {
            BlockPos abovePos = pos.above();
            BlockState above = level.getBlockState(abovePos);
            if (isOnSlab(above)) {
                return new StackedNeighbour(above,
                        fromAboveShape(above, above.getShape(level, abovePos, context)),
                        fromAboveShape(above, above.getCollisionShape(level, abovePos, context)));
            }
        }
        if (isUnderTopSlab(self)) {
            BlockPos belowPos = pos.below();
            BlockState below = level.getBlockState(belowPos);
            if (isUnderTopSlab(below)) {
                return new StackedNeighbour(below,
                        fromBelowShape(below, below.getShape(level, belowPos, context)),
                        fromBelowShape(below, below.getCollisionShape(level, belowPos, context)));
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
     * 位移缓存：方块状态 → （来源形状 → 位移后的形状）。
     *
     * <p>getShape / getCollisionShape / getOcclusionShape 都是碰撞与准星射线的高频路径，
     * 每次 {@code move} 都会新建一个形状，所以这里缓存起来。
     *
     * <p>为什么不能只按方块状态缓存：同一个状态会被问三种形状——遮挡形状、交互形状（getShape）
     * 与碰撞形状（getCollisionShape），墙的视觉立柱是 16 像素高、碰撞立柱是 24 像素高
     * （原版 WallBlock 就是这么定义的）。只按状态缓存的话，先算出来的那种会把另一种顶掉：
     * 方块状态构造时会先问遮挡形状，于是位移后的墙只剩 16 像素的碰撞，少掉上面 8 像素——
     * 表现就是“位移方块的碰撞箱少了超出的那一段”。按来源形状分开存，三种形状各拿各的。
     */
    private static final Map<BlockState, Map<VoxelShape, VoxelShape>> DOWN_SHIFTED_SHAPES = new ConcurrentHashMap<>();
    private static final Map<BlockState, Map<VoxelShape, VoxelShape>> UP_SHIFTED_SHAPES = new ConcurrentHashMap<>();

    /**
     * 把方块形状整体下移半格（8/16 单位），用于“放在下台阶上并贴齐”的方块。
     */
    public static VoxelShape shiftDownHalf(BlockState state, VoxelShape shape) {
        return movedOnce(DOWN_SHIFTED_SHAPES, state, shape, -0.5);
    }

    /**
     * 把方块形状整体上移半格（8/16 单位），用于“放在上台阶下方并贴齐”的方块。
     */
    public static VoxelShape shiftUpHalf(BlockState state, VoxelShape shape) {
        return movedOnce(UP_SHIFTED_SHAPES, state, shape, 0.5);
    }

    /**
     * “把邻居格子的形状换算到本格坐标”（整体 ±1 格）与“把邻居伸进来的部分并入本格形状”的缓存。
     *
     * <p>被并入的方块（火把/灯笼/栅栏/墙/玻璃板）形状都只由方块状态决定，但换算与合并每调用一次
     * 都会新建形状对象，而 getShape 是准星每帧、碰撞每实体每 tick 都要走的高频路径，所以这里缓存结果。
     * 缓存键里带上来源形状：来源换了就重新算，不会返回过期形状，也不会让同一状态的不同形状互相顶掉。
     */
    private static final Map<BlockState, Map<VoxelShape, VoxelShape>> FROM_ABOVE_SHAPES = new ConcurrentHashMap<>();
    private static final Map<BlockState, Map<VoxelShape, VoxelShape>> FROM_BELOW_SHAPES = new ConcurrentHashMap<>();
    private static final Map<VoxelShape, Map<VoxelShape, VoxelShape>> MERGED_SHAPES = new ConcurrentHashMap<>();
    private static final int MERGED_HOST_LIMIT = 4096;
    /** 每个状态最多记多少种来源形状，超过就整块丢掉重来（正常只有遮挡/交互/碰撞三种）。 */
    private static final int SHAPE_FLAVOR_LIMIT = 8;

    /** 把“正上方格子”的方块形状换算到本格坐标（整体上移一格）。 */
    public static VoxelShape fromAboveShape(BlockState state, VoxelShape shape) {
        return movedOnce(FROM_ABOVE_SHAPES, state, shape, 1.0);
    }

    /** 把“正下方格子”的方块形状换算到本格坐标（整体下移一格）。 */
    public static VoxelShape fromBelowShape(BlockState state, VoxelShape shape) {
        return movedOnce(FROM_BELOW_SHAPES, state, shape, -1.0);
    }

    private static VoxelShape movedOnce(Map<BlockState, Map<VoxelShape, VoxelShape>> cache,
                                        BlockState state, VoxelShape shape, double dy) {
        Map<VoxelShape, VoxelShape> bySource = cache.computeIfAbsent(state, key -> new ConcurrentHashMap<>());
        VoxelShape cached = bySource.get(shape);
        if (cached != null) {
            return cached;
        }
        if (bySource.size() >= SHAPE_FLAVOR_LIMIT) {
            bySource.clear();
        }
        VoxelShape moved = shape.move(0.0, dy, 0.0);
        bySource.put(shape, moved);
        return moved;
    }

    /**
     * 把邻居伸进本格的那部分形状并入本格形状（准星拾取与投掷物的射线判定用）。
     *
     * <p>合并结果只由这两个形状决定，所以缓存也按形状存：同一格的“交互形状 + 舒适框”与
     * “碰撞形状 + 邻居碰撞形状”是两组不同的形状，各缓存一份，不会互相顶掉。
     */
    public static VoxelShape mergedWithNeighbour(VoxelShape hostShape, VoxelShape neighbourShape) {
        if (MERGED_SHAPES.size() >= MERGED_HOST_LIMIT) {
            MERGED_SHAPES.clear();
        }
        Map<VoxelShape, VoxelShape> byNeighbour =
                MERGED_SHAPES.computeIfAbsent(hostShape, key -> new ConcurrentHashMap<>());
        VoxelShape cached = byNeighbour.get(neighbourShape);
        if (cached != null) {
            return cached;
        }
        if (byNeighbour.size() >= SHAPE_FLAVOR_LIMIT) {
            byNeighbour.clear();
        }
        VoxelShape merged = Shapes.or(hostShape, neighbourShape);
        byNeighbour.put(neighbourShape, merged);
        return merged;
    }

    /**
     * @return pos 上方的方块是否为"整体下移了半格的方块"（火把用布尔 on_slab，
     * 墙/栅栏/灯笼用贴台阶枚举，所以这里必须走 {@link #isLoweredBlock} 而不是直接读属性）。
     */
    public static boolean isLoweredOnSlabAbove(BlockGetter level, BlockPos pos) {
        return isLoweredBlock(level, pos.above());
    }

    /**
     * @return pos 下方的方块是否为"整体上移了半格的方块"（灯笼/墙/栅栏，贴在上台阶下方）。
     */
    public static boolean isRaisedUnderTopSlabBelow(BlockGetter level, BlockPos pos) {
        return isRaisedBlock(level, pos.below());
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
