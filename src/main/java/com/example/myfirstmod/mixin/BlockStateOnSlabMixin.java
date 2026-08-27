package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.ModBlockStateProperties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让“下台阶上”的火把/灯笼的碰撞箱在相邻格子也能被检测到。
 *
 * 原版碰撞查询只对“边缘格子(FACE)”检查 hasLargeCollisionShape==true 的方块；
 * 火把/灯笼是细长方块，默认该值 false。当我们把碰撞箱整体下移半格后，
 * 有一部分进入了下方台阶所在格子，那段碰撞在这些边缘格子中会漏检，
 * 造成“只有上半部分有效”的问题。这里把下台阶状态的火把/灯笼标记为
 * hasLargeCollisionShape=true，使它们在这些格子中也被参与碰撞判定。
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateOnSlabMixin {

    @Inject(method = "hasLargeCollisionShape", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$hasLargeCollisionShape(CallbackInfoReturnable<Boolean> cir) {
        BlockState state = (BlockState) (Object) this;
        if (!state.hasProperty(ModBlockStateProperties.ON_SLAB)
                || !state.getValue(ModBlockStateProperties.ON_SLAB)) {
            return;
        }
        Block block = state.getBlock();
        if ((block instanceof TorchBlock && !(block instanceof WallTorchBlock))
                || block instanceof LanternBlock) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}
