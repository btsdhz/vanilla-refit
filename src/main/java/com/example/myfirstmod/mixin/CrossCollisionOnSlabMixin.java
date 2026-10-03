package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.SlabSupport;
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
 * CrossCollisionBlock（栅栏等）形状按台阶位移半格。
 *
 * 栅栏继承了 CrossCollisionBlock 的 getShape / getCollisionShape（不覆写），
 * 所以形状位移要注入到基类；下台阶上的栅栏（btsdhz_on_slab=true）下移半格、
 * 上台阶下的栅栏（btsdhz_under_top_slab=true）上移半格，
 * 铁栅栏等其它子类没有该属性，自然不会位移。
 */
@Mixin(CrossCollisionBlock.class)
public abstract class CrossCollisionOnSlabMixin {

    @Inject(method = "getShape", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
                                          CallbackInfoReturnable<VoxelShape> cir) {
        VoxelShape base = cir.getReturnValue();
        VoxelShape shifted = shiftForSlab(state, base);
        // 上方/下方还有同样位移了半格的方块时，它有一部分伸进本格，并入本格的拾取形状（舒适框）。
        // 碰撞形状（getCollisionShape）保持不变，只有准星/拾取用得到这个形状。
        VoxelShape stacked = SlabSupport.stackedNeighbourShape(level, pos, context);
        if (stacked != null) {
            cir.setReturnValue(Shapes.or(shifted != null ? shifted : base, stacked));
            return;
        }
        if (shifted != null) {
            cir.setReturnValue(shifted);
        }
    }

    @Inject(method = "getCollisionShape", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
                                                   CallbackInfoReturnable<VoxelShape> cir) {
        VoxelShape shifted = shiftForSlab(state, cir.getReturnValue());
        if (shifted != null) {
            cir.setReturnValue(shifted);
        }
    }

    /** 贴下台阶则下移半格，贴上台阶下方则上移半格；不贴台阶返回 null（保持原版形状）。 */
    private static VoxelShape shiftForSlab(BlockState state, VoxelShape shape) {
        if (SlabSupport.isOnSlab(state)) {
            return SlabSupport.shiftDownHalf(state, shape);
        }
        return SlabSupport.isUnderTopSlab(state) ? SlabSupport.shiftUpHalf(state, shape) : null;
    }
}
