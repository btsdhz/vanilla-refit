package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.ModBlockStateProperties;
import com.example.myfirstmod.util.PaneCornerSupport;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 给玻璃板（IronBarsBlock）注册四个方向属性：btsdhz_ne / se / nw / sw。
 *
 * 属性是“默认 false”的，缺少该属性时就是原版玻璃板，不会改变老存档里已有玻璃板的形态。
 * 铁栏杆与原版玻璃板同属 IronBarsBlock，无法单独排除，因此铁栏杆也会带上这四个属性，
 * 但不参与渲染与碰撞（见 util/PaneCornerSupport）。
 */
@Mixin(IronBarsBlock.class)
public abstract class IronBarsBlockMixin {

    @Inject(method = "createBlockStateDefinition", at = @At("RETURN"), remap = false)
    private void btsdhz_original$createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder,
                                                            CallbackInfo ci) {
        builder.add(
                ModBlockStateProperties.PANE_NORTH_EAST,
                ModBlockStateProperties.PANE_SOUTH_EAST,
                ModBlockStateProperties.PANE_NORTH_WEST,
                ModBlockStateProperties.PANE_SOUTH_WEST,
                ModBlockStateProperties.PANE_NORTH_UP,
                ModBlockStateProperties.PANE_EAST_UP,
                ModBlockStateProperties.PANE_SOUTH_UP,
                ModBlockStateProperties.PANE_WEST_UP);
    }

    /**
     * 原版玻璃板规则：上下相邻还是同类玻璃板时，顶面/底面直接不渲染（因为原版玻璃板是满高的）。
     * 本模组的面片在中间高度，上下相邻的玻璃板并不会挡住它，所以启用面片时取消这条剔除，
     * 否则在一整面玻璃墙里（上下都有玻璃板）面片会被整个剔掉、看起来像没实现。
     */
    @Inject(method = "skipRendering", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$skipRendering(BlockState state, BlockState adjacentState, Direction side,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (!side.getAxis().isHorizontal() && PaneCornerSupport.hasCorner(state)) {
            cir.setReturnValue(false);
        }
    }
}
