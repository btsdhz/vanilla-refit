package com.example.myfirstmod.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
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
     * 把方块形状整体下移半格（8/16 单位），用于“放在下台阶上并贴齐”的方块。
     */
    public static VoxelShape shiftDownHalf(VoxelShape shape) {
        return shape.move(0.0, -0.5, 0.0);
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
