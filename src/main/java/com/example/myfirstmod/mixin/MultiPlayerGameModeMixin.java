package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.SlabSupport;
import com.example.myfirstmod.block.MixedSlabBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 客户端“拆方块”预测也做与半砖舒适框一致的改判。
 *
 * 服务端已把“点到半砖上半格（舒适框）”改判为“拆上方火把/灯笼/栅栏/墙”；但客户端在创造
 * 模式会立即在本地预测“拆半砖”，与服务端动作不一致，导致半砖闪回。这里让客户端在预测
 * 拆半砖时也做同样的命中高度判断并改判到上方方块，使两边一致，消除闪回。
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
        // 潜行拆堆叠半砖：客户端不预测“拆半格”，交给服务端权威返回“保留的那半”，避免闪回。
        // 非潜行走原版整个拆除，客户端预测与服务端一致。
        var blockState = this.minecraft.level.getBlockState(pos);
        boolean isStackedSlab = blockState.getBlock() instanceof MixedSlabBlock
                || (blockState.getBlock() instanceof SlabBlock
                && blockState.hasProperty(SlabBlock.TYPE)
                && blockState.getValue(SlabBlock.TYPE) == SlabType.DOUBLE);
        if (this.minecraft.player != null && this.minecraft.player.isShiftKeyDown() && isStackedSlab) {
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }
        if (SlabSupport.isBottomSlab(this.minecraft.level, pos)
                && SlabSupport.isLoweredOnSlabAbove(this.minecraft.level, pos)
                && btsdhz_original$aimsAboveSlabTop(pos)) {
            // 改拆上方的火把/灯笼/栅栏/墙，客户端预测与服务端一致
            cir.setReturnValue(this.destroyBlock(pos.above()));
            cir.cancel();
        }
    }

    /**
     * 当前瞄的是不是半砖格上半格（舒适框区域）。
     * 下半格属于半砖本体，保持原版“拆半砖”。
     */
    @Unique
    private boolean btsdhz_original$aimsAboveSlabTop(BlockPos pos) {
        HitResult hit = this.minecraft.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || !blockHit.getBlockPos().equals(pos)) {
            return false;
        }
        return blockHit.getLocation().y - pos.getY() > 0.5;
    }
}
