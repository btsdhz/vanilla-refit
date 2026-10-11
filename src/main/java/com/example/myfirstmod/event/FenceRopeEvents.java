package com.example.myfirstmod.event;

import com.example.myfirstmod.BtsdhzOriginal;
import com.example.myfirstmod.entity.FenceKnotEntity;
import com.example.myfirstmod.util.FenceRopeLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 拴绳"先点栅栏、再点动物"这条路。
 *
 * <p>两个方向都能连：先拿拴绳点动物（动物跟到玩家手上），再点栅栏把动物拴上去；
 * 或者先点栅栏开始一条连接（绳结已经挂在栅栏上、另一头在玩家手上），再拿拴绳点动物，
 * 动物会拴到那根栅栏的绳结上，和点第二根栅栏一样消耗这次连接。</p>
 *
 * <p>只有手里拿着拴绳时才接管：其它物品点动物可能是喂食、繁殖、骑乘等原版行为，不能抢。</p>
 */
@EventBusSubscriber(modid = BtsdhzOriginal.MOD_ID)
public final class FenceRopeEvents {

    private FenceRopeEvents() {
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (!stack.is(Items.LEAD) || !(event.getTarget() instanceof Leashable leashable)) {
            return;
        }
        BlockPos pending = FenceRopeLogic.getPending(player);
        if (pending == null || !leashable.canHaveALeashAttachedToIt()) {
            return;
        }
        Level level = event.getLevel();
        if (!level.isClientSide()) {
            FenceKnotEntity knot = FenceRopeLogic.getOrCreateKnot(level, pending);
            leashable.setLeashedTo(knot, true);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(null, pending, SoundEvents.LEASH_KNOT_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        // 两端都清：服务端清真正的状态并清掉绳结上"拴到手上"的标记（否则手上会残留一条拴绳），
        // 客户端清镜像（见 FenceRopeLogic.mirrorPending）
        FenceRopeLogic.clearPendingWithKnot(player, level);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
