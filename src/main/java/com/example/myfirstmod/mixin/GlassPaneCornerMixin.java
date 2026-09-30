package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.PaneCornerSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 玻璃板四角水平面片的碰撞箱与拾取形状。
 *
 * 玻璃板没有覆写 CrossCollisionBlock 的 getShape / getCollisionShape（形状来自构造期算好的
 * shapeByIndex 表），所以和栅栏的台阶位移一样注入到基类；只有玻璃板会命中
 * {@link PaneCornerSupport#cornerShape(BlockState)} 的判定，栅栏与铁栏杆保持原版形状。
 */
@Mixin(CrossCollisionBlock.class)
public abstract class GlassPaneCornerMixin {

    @Inject(method = "getShape", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
                                          CallbackInfoReturnable<VoxelShape> cir) {
        VoxelShape corners = PaneCornerSupport.cornerShape(state);
        if (corners != null) {
            cir.setReturnValue(Shapes.or(cir.getReturnValue(), corners));
        }
    }

    @Inject(method = "getCollisionShape", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                                   CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        VoxelShape corners = PaneCornerSupport.cornerShape(state);
        if (corners != null) {
            cir.setReturnValue(Shapes.or(cir.getReturnValue(), corners));
        }
    }
}
