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
 * 服务端破坏方块：若目标是混合半砖，则只拆被准星命中的那一半，保留另一半。
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
        BlockState state = this.level.getBlockState(pos);
        if (MixedSlabBreakHandler.tryBreakHalf(this.level, pos, state, this.player)) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}
