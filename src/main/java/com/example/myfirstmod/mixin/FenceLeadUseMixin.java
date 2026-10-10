package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.FenceRopeLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 待连接状态下（拴绳一头已经挂在栅栏上、另一头在玩家手上），点另一根栅栏就完成这条连接，
 * <b>不要求手里还拿着拴绳</b>。
 *
 * <p>"一头已经挂上、另一头在手"本身就是拴绳正在使用的状态，换成别的物品（甚至空手）也该能接着连。
 * 因此这里挂在栅栏自己的 {@code useItemOn} 上，而不是 {@code LeadItem.useOn}：
 * 只有"确实处于待连接状态、而且点的不是待连接那根"时才接管，
 * 其余情况（没在连接、点的是同一根、手里就是拴绳）全部保持原样。</p>
 *
 * <p>客户端只回一个"这次右键不预测放方块"的结果，真正的连接在服务端做（和拴绳那条路一致）。</p>
 */
@Mixin(FenceBlock.class)
public abstract class FenceLeadUseMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$completePendingWithAnyItem(ItemStack stack, BlockState state, Level level,
                                                            BlockPos pos, Player player, InteractionHand hand,
                                                            BlockHitResult hitResult,
                                                            CallbackInfoReturnable<ItemInteractionResult> cir) {
        // 手里拿着拴绳：走原来的那条路（开始连接 / 再点一次取消 / 第二次点击完成）
        if (player == null || stack.is(Items.LEAD)) {
            return;
        }
        BlockPos pending = FenceRopeLogic.getPending(player);
        // 没有待连接，或点的就是待连接的那根：不接管，保持原版行为（该放方块放方块）
        if (pending == null || pending.equals(pos)) {
            return;
        }
        if (level.isClientSide()) {
            // 服务端会把这条待连接用掉并清空状态,客户端镜像也要一起清(否则之后点栅栏一直被它吃掉)
            FenceRopeLogic.clearPendingWithKnot(player, level);
            cir.setReturnValue(ItemInteractionResult.SUCCESS);
            cir.cancel();
            return;
        }
        boolean creative = player.getAbilities().instabuild;
        FenceRopeLogic.connect(level, pending, pos, !creative);
        FenceRopeLogic.clearPendingWithKnot(player, level);
        level.playSound(null, pos, SoundEvents.LEASH_KNOT_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        cir.setReturnValue(ItemInteractionResult.SUCCESS);
        cir.cancel();
    }
}
