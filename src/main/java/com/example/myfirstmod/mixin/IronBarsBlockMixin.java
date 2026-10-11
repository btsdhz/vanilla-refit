package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.ModBlockStateProperties;
import com.example.myfirstmod.util.PaneConnection;
import com.example.myfirstmod.util.PaneCornerSupport;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
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
 * 给原版的玻璃板 / 铁栏杆注册 8 个附加部件属性：
 * 四个角 btsdhz_ne / se / nw / sw，四个上半 btsdhz_north_up / east_up / south_up / west_up。
 *
 * 注入范围只限原版命名空间的玻璃板 / 铁栏杆（判定见 PaneCornerSupport.shouldInjectPaneProperties）：
 * 第三方模组的玻璃板 / 铁栏杆一个属性都不加，状态数（32）、外观、碰撞箱与连接规则全部保持原版——
 * 加了也没有功能，却会让每个这样的方块状态数变成原版的 256 倍（判定依据见那个方法的注释）。
 *
 * 属性都是“默认 false”的，缺少该属性时就是原版玻璃板，不会改变老存档里已有玻璃板的形态。
 */
@Mixin(IronBarsBlock.class)
public abstract class IronBarsBlockMixin {

    @Inject(method = "createBlockStateDefinition", at = @At("RETURN"), remap = false)
    private void btsdhz_original$createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder,
                                                            CallbackInfo ci) {
        if (com.example.myfirstmod.debug.MemProbe.disabled(com.example.myfirstmod.debug.MemProbe.Cat.PANE)) {
            return;
        }
        if (!PaneCornerSupport.shouldInjectPaneProperties()) {
            return;
        }
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

    /**
     * 放置时按周围环境算好 12 个部件属性（覆盖原版那套只设置东西南北的逻辑）。
     * 周围什么都没有时全是 false，也就是原版那根棍。
     */
    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$getStateForPlacement(BlockPlaceContext context,
                                                      CallbackInfoReturnable<BlockState> cir) {
        BlockState state = cir.getReturnValue();
        if (state != null) {
            cir.setReturnValue(PaneConnection.update(state, context.getLevel(), context.getClickedPos()));
        }
    }

    /**
     * 邻居变化时重算：旁放/拆掉方块、上下方出现或消失方块、旁边放拆玻璃板都会走到这里，
     * 顺带把新放玻璃板对旁边玻璃板的角上面片影响也一并刷新。
     */
    @Inject(method = "updateShape", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$updateShape(BlockState state, Direction facing, BlockState facingState,
                                             LevelAccessor level, BlockPos currentPos, BlockPos facingPos,
                                             CallbackInfoReturnable<BlockState> cir) {
        cir.setReturnValue(PaneConnection.update(cir.getReturnValue(), level, currentPos));
    }
}
