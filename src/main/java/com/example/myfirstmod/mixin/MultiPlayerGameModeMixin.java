package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.SlabSupport;
import com.example.myfirstmod.block.MixedSlabBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 客户端“到底在挖哪个方块”的改判。
 *
 * <p>半砖的拾取形状里并入了一个“舒适框”，让鼠标指着半砖上半格时也命中半砖。此时玩家真正
 * 想挖的是半砖上方的下移方块（火把/灯笼/栅栏/墙）。改判放在“选择挖掘目标”的阶段
 * （{@code startDestroyBlock} / {@code continueDestroyBlock}）而不是破坏瞬间：
 * <ul>
 *     <li>挖掘进度、裂纹动画、破坏音效都按上方方块正常走，生存模式不会“秒破”；</li>
 *     <li>客户端直接把目标改判成上方方块并发包，服务端无需介入，行为与原版一致。</li>
 * </ul>
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    @Shadow(remap = false)
    private Minecraft minecraft;

    @Shadow(remap = false)
    public abstract boolean destroyBlock(BlockPos pos);

    @Shadow(remap = false)
    public abstract boolean startDestroyBlock(BlockPos pos, Direction face);

    @Shadow(remap = false)
    public abstract boolean continueDestroyBlock(BlockPos pos, Direction face);

    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$redirectStartTarget(BlockPos pos, Direction face,
                                                     CallbackInfoReturnable<Boolean> cir) {
        BlockPos target = btsdhz_original$redirectTarget(pos);
        if (target != pos) {
            // 目标不是下台阶本体（而是它上方的下移方块），按新目标重新走一遍；
            // 新目标不会再次命中改判，所以不会递归下去。
            cir.setReturnValue(this.startDestroyBlock(target, face));
            cir.cancel();
        }
    }

    @Inject(method = "continueDestroyBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$redirectContinueTarget(BlockPos pos, Direction face,
                                                        CallbackInfoReturnable<Boolean> cir) {
        BlockPos target = btsdhz_original$redirectTarget(pos);
        if (target != pos) {
            cir.setReturnValue(this.continueDestroyBlock(target, face));
            cir.cancel();
        }
    }

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
    }

    /**
     * 指向下台阶“舒适框”时，把挖掘目标换成正上方的下移方块；
     * 指向半砖本体（下半格）时原样返回，保持原版“挖半砖”。
     */
    @Unique
    private BlockPos btsdhz_original$redirectTarget(BlockPos pos) {
        if (this.minecraft.level == null || this.minecraft.player == null) {
            return pos;
        }
        if (!SlabSupport.isBottomSlab(this.minecraft.level, pos)
                || !SlabSupport.isLoweredOnSlabAbove(this.minecraft.level, pos)
                || !btsdhz_original$aimsAboveSlabTop(pos)) {
            return pos;
        }
        return pos.above();
    }

    /**
     * 当前瞄的是不是半砖格上半格（舒适框区域）。
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
