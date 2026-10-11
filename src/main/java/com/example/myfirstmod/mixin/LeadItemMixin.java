package com.example.myfirstmod.mixin;

import com.example.myfirstmod.entity.FenceKnotEntity;
import com.example.myfirstmod.util.FenceRopeLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让拴绳能连接两根栅栏。
 *
 * <p>第一次点击栅栏开始建立连接(在该栅栏生成绕绳结,并把绳结渲染到玩家手部);
 * 第二次点击另一根栅栏即完成一条拴绳。每根栅栏只有一个绕绳结,可同时挂多条拴绳。</p>
 */
@Mixin(LeadItem.class)
public abstract class LeadItemMixin {

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$useOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(BlockTags.FENCES)) {
            return;
        }
        Player player = context.getPlayer();
        if (player == null) {
            return;
        }

        // 客户端只负责挥动手臂与本地预测,实际逻辑在服务端执行。
        // 但"待连接"这份状态要镜像一份:不镜像的话,客户端不知道自己在待连接,
        // 手里拿着方块点第二根栅栏时会先预测放一个方块,再被服务端回滚 —— 看起来就是闪一下。
        if (level.isClientSide) {
            if (FenceRopeLogic.hasPlayerLeashedMobs(player, level, pos)) {
                FenceRopeLogic.clearPending(player);
            } else {
                FenceRopeLogic.mirrorPending(player, pos);
            }
            return;
        }

        // 玩家正牵着动物时保持原版行为。
        if (FenceRopeLogic.hasPlayerLeashedMobs(player, level, pos)) {
            // 这条待连接交给原版(把牵着的动物拴到本栅栏),绳结上的"拴到手上"标记也要一起清
            FenceRopeLogic.clearPendingWithKnot(player, level);
            return;
        }

        BlockPos pending = FenceRopeLogic.getPending(player);
        if (pending == null) {
            // 第一次点击:开始建立连接,在栅栏上放一个待连接绳结。
            FenceRopeLogic.setPending(player, pos);
            FenceKnotEntity knot = FenceRopeLogic.getOrCreateKnot(level, pos);
            knot.setPendingPlayer(player.getUUID());
            level.playSound(null, pos, SoundEvents.LEASH_KNOT_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            cir.setReturnValue(InteractionResult.SUCCESS);
            cir.cancel();
        } else if (pending.equals(pos)) {
            // 再次点击同一栅栏:取消建立状态。
            FenceRopeLogic.clearPending(player);
            FenceKnotEntity knot = FenceRopeLogic.findKnot(level, pos);
            if (knot != null) {
                knot.setPendingPlayer(null);
                if (!knot.hasPartners()) {
                    knot.discard();
                }
            }
            cir.setReturnValue(InteractionResult.SUCCESS);
            cir.cancel();
        } else {
            // 第二次点击:完成一条连接。
            boolean creative = player.getAbilities().instabuild;
            FenceRopeLogic.connect(level, pending, pos, !creative);
            FenceRopeLogic.clearPending(player);
            if (!creative && !context.getItemInHand().isEmpty()) {
                context.getItemInHand().shrink(1);
            }
            level.playSound(null, pos, SoundEvents.LEASH_KNOT_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            cir.setReturnValue(InteractionResult.SUCCESS);
            cir.cancel();
        }
    }
}
