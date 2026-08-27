package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.ModBlockStateProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 火把/灵魂火把放在下台阶上时，碰撞箱整体下移半格（8/16 单位），
 * 使其与下台阶的上表面对齐。
 *
 * 只需要处理 BaseTorchBlock（普通火把、灵魂火把都直接是 TorchBlock 实例）。
 * 红石火把没有 btsdhz_on_slab 属性，所以不影响；墙火把单独覆盖 getShape，
 * 也不会走到这里。
 */
@Mixin(BaseTorchBlock.class)
public abstract class TorchOnSlabMixin {

    // 原版火把碰撞箱：Block.box(6, 0, 6, 10, 10, 10)，整体下移 8 格
    private static final VoxelShape SHAPE_ON_SLAB = Block.box(6.0, -8.0, 6.0, 10.0, 2.0, 10.0);

    @Inject(method = "getShape", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$getShape(BlockState state, BlockGetter level, BlockPos pos,
                                          CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (state.hasProperty(ModBlockStateProperties.ON_SLAB)
                && state.getValue(ModBlockStateProperties.ON_SLAB)) {
            cir.setReturnValue(SHAPE_ON_SLAB);
        }
    }
}
