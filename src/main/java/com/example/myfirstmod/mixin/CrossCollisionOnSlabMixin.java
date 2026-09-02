package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * CrossCollisionBlock（栅栏等）形状下移半格。
 *
 * 栅栏继承了 CrossCollisionBlock 的 getShape / getCollisionShape（不覆写），
 * 所以形状位移要注入到基类；只有带 btsdhz_on_slab=true 的状态才位移，
 * 铁栅栏等其它子类没有该属性，自然不会位移。
 */
@Mixin(CrossCollisionBlock.class)
public abstract class CrossCollisionOnSlabMixin {

    @Inject(method = "getShape", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
                                          CallbackInfoReturnable<VoxelShape> cir) {
        if (SlabSupport.isOnSlab(state)) {
            cir.setReturnValue(SlabSupport.shiftDownHalf(cir.getReturnValue()));
        }
    }

    @Inject(method = "getCollisionShape", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
                                                   CallbackInfoReturnable<VoxelShape> cir) {
        if (SlabSupport.isOnSlab(state)) {
            cir.setReturnValue(SlabSupport.shiftDownHalf(cir.getReturnValue()));
        }
    }
}
