package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.MixedSlabBreakHandler;
import com.example.myfirstmod.block.MixedSlabBlock;
import com.example.myfirstmod.event.CreateSymmetryCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.common.CommonHooks;
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
 *
 * 因为这两条路都是本模组完全接管原版 {@code destroyBlock} 的，原版发出的
 * {@link net.neoforged.neoforge.event.level.BlockEvent.BreakEvent} 就不会触发，
 * 这里在接管前自己补发一次（别的模组和插件靠这个事件联动，例如机械动力对称之杖
 * 就是靠它把镜像那边的方块一起拆掉；不发就“断联”）。
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {

    @Shadow(remap = false)
    @Final
    protected ServerPlayer player;

    @Shadow(remap = false)
    protected net.minecraft.server.level.ServerLevel level;

    @Shadow(remap = false)
    private GameType gameModeForPlayer;

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$breakMixedHalf(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockState state = this.level.getBlockState(pos);
        boolean sneaking = this.player.isShiftKeyDown();
        boolean mixedSlab = state.getBlock() instanceof MixedSlabBlock;
        boolean vanillaDouble = state.getBlock() instanceof SlabBlock
                && state.hasProperty(SlabBlock.TYPE)
                && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE;
        // 只有这两种情况本模组才接管；其余方块完全走原版（连带原版自己的 BreakEvent）
        boolean takesOver = sneaking ? (mixedSlab || vanillaDouble) : mixedSlab;
        if (!takesOver) {
            return;
        }
        // 破坏前先把镜像那边的情况记下来（此刻源格与镜像格的方块都还在）
        CreateSymmetryCompat.beginBreak(this.level, this.player, pos, state, sneaking);
        // 接管前补发 BreakEvent：它同时还会做“手上物品能不能攻击方块 / 权限限制 / 冒险模式”等判断，
        // 与原版 destroyBlock 开头的顺序一致。
        if (CommonHooks.fireBlockBreak(this.level, this.gameModeForPlayer, this.player, pos, state).isCanceled()) {
            CreateSymmetryCompat.endBreak();
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }
        boolean handled = sneaking
                // 潜行：只拆被准星命中的那一半
                ? MixedSlabBreakHandler.tryBreakHalf(this.level, pos, state, this.player)
                // 非潜行：原版整块拆除；混合半砖无掉落表、原版产不出掉落，这里手动接管整挖掉落。
                : MixedSlabBreakHandler.dropWholeMixed(this.level, pos, state, this.player);
        CreateSymmetryCompat.endBreak();
        if (handled) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}
