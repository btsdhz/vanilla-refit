package com.example.myfirstmod.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

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
     * @return pos 处是否为普通水平下半台阶
     */
    public static boolean isBottomSlab(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof SlabBlock
                && state.hasProperty(ModBlockStateProperties.MODE)
                && state.getValue(ModBlockStateProperties.MODE) == VerticalSlabMode.SLAB
                && state.hasProperty(SlabBlock.TYPE)
                && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
    }

    /**
     * @return pos 上方的方块是否为“下移（ON_SLAB=true）的普通火把/灵魂火把/灯笼/灵魂灯笼”
     */
    public static boolean isLoweredTorchOrLanternAbove(BlockGetter level, BlockPos pos) {
        BlockState above = level.getBlockState(pos.above());
        Block b = above.getBlock();
        boolean isTorch = b instanceof TorchBlock && !(b instanceof WallTorchBlock);
        boolean isLantern = b instanceof LanternBlock;
        return (isTorch || isLantern)
                && above.hasProperty(ModBlockStateProperties.ON_SLAB)
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
