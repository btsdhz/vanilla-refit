package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.SlabOffset;
import com.example.myfirstmod.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
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
 * 射线判定（投掷物、生物视线）在位移方块上改用视觉形状。
 *
 * <p>原版里墙、栅栏本来就"玩家和投掷物不是一套箱子"：
 * 玩家走动走的是按包围盒逐格取碰撞形状（{@code BlockCollisions}），拿到的是完整碰撞箱——
 * 墙的碰撞立柱 24 像素（1.5 格），比 16 像素（1 格）高的模型高出半格，那半格是看不见的阻挡；
 * 而箭矢之类的投掷物走的是逐格射线（{@code ClipContext.Block.COLLIDER}），
 * 只检查射线真正穿过的那一格的形状，碰撞箱伸出格子外的那半格根本查不到，
 * 所以原版里投掷物实际撞到的高度和视觉形状一致。
 *
 * <p>方块整体位移半格后，那条"多出来的半格"从格子外被挪进了格子内，
 * 射线于是开始撞到它——表现就是"箭矢停在墙模型顶端以上的空气里"。
 * 这里在射线取形状时把位移形态的方块换回视觉形状，恢复原版那种效果；
 * 玩家走动的路径（{@code BlockCollisions}）不经过这里，碰撞箱照旧是完整的位移碰撞箱。
 *
 * <p>本来就没有碰撞箱的方块（火把类）不受影响：射线应该照样穿过去。
 */
@Mixin(ClipContext.class)
public abstract class ClipContextShapeMixin {

    @Shadow
    @Final
    private ClipContext.Block block;

    @Shadow
    @Final
    private CollisionContext collisionContext;

    @Inject(method = "getBlockShape", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$getBlockShape(BlockState state, BlockGetter level, BlockPos pos,
                                               CallbackInfoReturnable<VoxelShape> cir) {
        // 只处理"按碰撞箱取形状"的射线；拾取轮廓用的是 OUTLINE，本来就是视觉形状
        if (this.block != ClipContext.Block.COLLIDER) {
            return;
        }
        // 位移了半格的方块：火把类看 btsdhz_on_slab 布尔，墙/栅栏/灯笼看贴台阶枚举
        if (SlabSupport.offset(state) == SlabOffset.NONE) {
            return;
        }
        // 火把类本来就没有碰撞箱：射线应该照样穿过去，不能因为改成视觉形状而多出一个箱子
        if (state.getCollisionShape(level, pos, this.collisionContext).isEmpty()) {
            return;
        }
        cir.setReturnValue(state.getShape(level, pos, this.collisionContext));
        cir.cancel();
    }
}
