package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.ModBlockStateProperties;
import com.example.myfirstmod.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 栅栏放在“下台阶”（下半台阶）上时，新增 btsdhz_on_slab 属性并让模型与碰撞箱下移半格，
 * 使栅栏贴齐下台阶的上表面，不再悬空。
 *
 * 只做“栅栏是否在下台阶上”的判定与位移；栅栏之间的连接关系不受影响。
 */
@Mixin(FenceBlock.class)
public abstract class FenceOnSlabMixin {

    // 给栅栏增加 btsdhz_on_slab 属性
    @Inject(method = "createBlockStateDefinition", at = @At("RETURN"), remap = false)
    private void btsdhz_original$addOnSlab(StateDefinition.Builder<Block, BlockState> builder, CallbackInfo ci) {
        builder.add(ModBlockStateProperties.ON_SLAB);
    }

    // 放置时：下方是下台阶则置 ON_SLAB=true
    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$getStateForPlacement(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
        BlockState state = cir.getReturnValue();
        if (state == null) {
            return;
        }
        boolean onSlab = SlabSupport.isBottomSlab(context.getLevel(), context.getClickedPos().below());
        cir.setReturnValue(state.setValue(ModBlockStateProperties.ON_SLAB, onSlab));
    }

    // 邻居更新时（含下方放台阶/拆台阶）重新同步 ON_SLAB，保证反向场景（先放栅栏后放台阶）也生效
    @Inject(method = "updateShape", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$updateShape(BlockState state, Direction facing, BlockState facingState,
                                             LevelAccessor level, BlockPos currentPos, BlockPos facingPos,
                                             CallbackInfoReturnable<BlockState> cir) {
        if (state.hasProperty(ModBlockStateProperties.ON_SLAB)) {
            boolean onSlab = SlabSupport.isBottomSlab(level, currentPos.below());
            cir.setReturnValue(cir.getReturnValue().setValue(ModBlockStateProperties.ON_SLAB, onSlab));
        }
    }

}
