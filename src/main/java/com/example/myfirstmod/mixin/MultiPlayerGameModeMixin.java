package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.SlabSupport;
import com.example.myfirstmod.block.MixedSlabBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 客户端“拆方块”预测也做与半砖舒适框一致的改判。
 *
 * 服务端已把“点到带下移火把/灯笼的半砖”改判为“拆上方火把/灯笼”；但客户端在创造模式
 * 会立即在本地预测“拆半砖”，与服务端动作不一致，导致半砖闪回。这里让客户端在预测
 * 拆半砖时也把目标改到上方火把/灯笼，使两边一致，消除闪回。
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    @Shadow(remap = false)
    private Minecraft minecraft;

    @Shadow(remap = false)
    public abstract boolean destroyBlock(BlockPos pos);

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void btsdhz_original$redirectDestroy(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (this.minecraft.level == null) {
            return;
        }
        // 堆叠半砖（混合半砖或原版 DOUBLE）：不在客户端预测“拆半格”，交给服务端权威决定（避免闪回）
        var blockState = this.minecraft.level.getBlockState(pos);
        boolean isStackedSlab = blockState.getBlock() instanceof MixedSlabBlock
                || (blockState.getBlock() instanceof SlabBlock
                && blockState.hasProperty(SlabBlock.TYPE)
                && blockState.getValue(SlabBlock.TYPE) == SlabType.DOUBLE);
        if (isStackedSlab) {
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }
        if (SlabSupport.isBottomSlab(this.minecraft.level, pos)
                && SlabSupport.isLoweredTorchOrLanternAbove(this.minecraft.level, pos)) {
            // 改拆上方的火把/灯笼，客户端预测与服务端一致
            cir.setReturnValue(this.destroyBlock(pos.above()));
            cir.cancel();
        }
    }
}
