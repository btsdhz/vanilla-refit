package com.example.myfirstmod.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SlabBlock;
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
}
