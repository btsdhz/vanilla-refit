package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.PaneCornerSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 玻璃板的碰撞箱与拾取形状：12 个部件属性全为 false 时保持原版，
 * 有一个为 true 时形状就换成由 12 个部件拼出来的形状（不再有中间那根立柱）。
 *
 * 玻璃板没有覆写 CrossCollisionBlock 的 getShape / getCollisionShape（形状来自构造期算好的
 * shapeByIndex 表），所以和栅栏的台阶位移一样注入到基类；只有玻璃板会命中
 * {@link PaneCornerSupport#adjustShape(VoxelShape, VoxelShape, BlockState)} 的判定，
 * 栅栏与铁栏杆保持原版形状。
 */
@Mixin(CrossCollisionBlock.class)
public abstract class GlassPaneCornerMixin {

    /** 原版形状表，用来把台阶舒适框叠加进返回值的那部分分离出来。 */
    @Shadow
    @Final
    protected VoxelShape[] shapeByIndex;

    @Shadow
    @Final
    protected VoxelShape[] collisionShapeByIndex;

    @Shadow
    protected abstract int getAABBIndex(BlockState state);

    @Inject(method = "getShape", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
                                          CallbackInfoReturnable<VoxelShape> cir) {
        VoxelShape adjusted = PaneCornerSupport.adjustShape(
                cir.getReturnValue(), this.shapeByIndex[this.getAABBIndex(state)], state);
        if (adjusted != cir.getReturnValue()) {
            cir.setReturnValue(adjusted);
        }
    }

    @Inject(method = "getCollisionShape", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                                   CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        VoxelShape adjusted = PaneCornerSupport.adjustShape(
                cir.getReturnValue(), this.collisionShapeByIndex[this.getAABBIndex(state)], state);
        if (adjusted != cir.getReturnValue()) {
            cir.setReturnValue(adjusted);
        }
    }
}
