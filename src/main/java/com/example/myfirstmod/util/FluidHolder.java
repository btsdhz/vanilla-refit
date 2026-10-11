package com.example.myfirstmod.util;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * 台阶 / 楼梯"含的是什么液体"的统一读写入口。
 *
 * <p>三种承载方式（同一个方法内部按方块实际带的属性分派）：
 * <ol>
 *   <li><b>原版台阶 / 楼梯</b>（本模组注入过属性）：水在原版 {@code waterlogged}，其它液体在
 *       {@link ModBlockStateProperties#EXTRA_FLUID}，两者互斥；</li>
 *   <li><b>本模组的混合半砖</b>（自有方块，没有原版 {@code waterlogged}）：仍用 3 值的
 *       {@link ModBlockStateProperties#FLUID_TYPE}；</li>
 *   <li>只带原版 {@code waterlogged} 的其它方块：按水处理。</li>
 * </ol>
 * 所有读写都走这里，调用方就不用关心"这块方块的水到底存在哪个属性里"。
 */
public final class FluidHolder {

    private FluidHolder() {
    }

    /** 这块方块状态能不能装本模组接管的液体（水或熔岩）。 */
    public static boolean hasHolder(BlockState state) {
        return state.hasProperty(ModBlockStateProperties.EXTRA_FLUID)
                || state.hasProperty(ModBlockStateProperties.FLUID_TYPE);
    }

    /** 读出当前含的液体；不含液体返回 {@link FluidType#NONE}。 */
    public static FluidType of(BlockState state) {
        if (state.hasProperty(ModBlockStateProperties.EXTRA_FLUID)) {
            if (state.getValue(ModBlockStateProperties.EXTRA_FLUID) == ExtraFluid.LAVA) {
                return FluidType.LAVA;
            }
            return isWaterlogged(state) ? FluidType.WATER : FluidType.NONE;
        }
        if (state.hasProperty(ModBlockStateProperties.FLUID_TYPE)) {
            return state.getValue(ModBlockStateProperties.FLUID_TYPE);
        }
        return isWaterlogged(state) ? FluidType.WATER : FluidType.NONE;
    }

    /** 写入液体，两个属性自动保持互斥（水只写原版 waterlogged，熔岩只写本模组属性）。 */
    public static BlockState with(BlockState state, FluidType fluid) {
        if (state.hasProperty(ModBlockStateProperties.EXTRA_FLUID)) {
            state = state.setValue(ModBlockStateProperties.EXTRA_FLUID,
                    fluid == FluidType.LAVA ? ExtraFluid.LAVA : ExtraFluid.NONE);
            if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
                state = state.setValue(BlockStateProperties.WATERLOGGED, fluid == FluidType.WATER);
            }
            return state;
        }
        if (state.hasProperty(ModBlockStateProperties.FLUID_TYPE)) {
            return state.setValue(ModBlockStateProperties.FLUID_TYPE, fluid);
        }
        return state;
    }

    /** 是不是"含熔岩"（黑名单、流体 tick 等地方用）。 */
    public static boolean isLava(BlockState state) {
        return of(state) == FluidType.LAVA;
    }

    private static boolean isWaterlogged(BlockState state) {
        return state.hasProperty(BlockStateProperties.WATERLOGGED)
                && state.getValue(BlockStateProperties.WATERLOGGED);
    }
}
