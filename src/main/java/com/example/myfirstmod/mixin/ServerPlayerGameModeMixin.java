package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.MixedSlabBreakHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * 服务端破坏方块。
 *
 * 潜行（按住 Shift）时：若目标是堆叠半砖（混合半砖或原版 DOUBLE），只拆被准星命中的
 * 那一半，保留另一半。非潜行时走原版“整个拆除”，会掉落整格的所有半砖物品。
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {

    @Shadow(remap = false)
    @Final
    protected ServerPlayer player;

    @Shadow(remap = false)
    protected net.minecraft.server.level.ServerLevel level;

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$breakMixedHalf(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        // 默认不潜行 -> 原版整个拆除；仅潜行时走“只拆一半”
        if (!this.player.isShiftKeyDown()) {
            return;
        }
        BlockState state = this.level.getBlockState(pos);
        if (MixedSlabBreakHandler.tryBreakHalf(this.level, pos, state, this.player)) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}
