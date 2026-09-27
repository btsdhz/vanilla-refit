package com.example.myfirstmod.event;

import com.example.myfirstmod.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 半砖上的“舒适框”改判：左键点到带下移方块（火把/灯笼/栅栏/墙）的半砖时，
 * 拆掉上方方块而不是半砖。
 *
 * 当普通下半台阶上方有一个下移（ON_SLAB=true）的方块时，半砖的拾取形状
 * 会并入一个对应宽度/柱宽的舒适框（见 SlabBlockMixin），从而让“瞄准方块底座”也能命中半砖。
 * 这里再把这次拆半砖的操作改判到上方的方块——避免误拆半砖。
 *
 * <p>规则：按命中高度区分——点在“舒适框”（半砖格上半格，y&gt;0.5）才改判拆上方方块；
 * 点在半砖本体（下半格）保持原版，拆的就是半砖自己。
 */
@EventBusSubscriber(modid = "btsdhz_original")
public class TorchComfortEvents {

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        // 只在服务端改判（客户端无法真正拆除方块）
        if (event.getLevel().isClientSide()) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);
        if (!SlabSupport.isBottomSlab(event.getLevel(), pos)
                || !SlabSupport.isLoweredOnSlabAbove(event.getLevel(), pos)) {
            return;
        }

        // 只有点到半砖上半格（舒适框区域）才算“瞄准上方方块”；
        // 点到半砖本体（下半格，含顶面 y=0.5）保持原版，拆半砖本身。
        // NeoForge 的 LeftClickBlock 事件不带命中点，这里用玩家视线自己做一次方块拾取。
        Player player = event.getEntity();
        HitResult pick = player.pick(player.blockInteractionRange(), 0.0F, false);
        if (!(pick instanceof BlockHitResult blockHit)
                || !blockHit.getBlockPos().equals(pos)
                || blockHit.getLocation().y - pos.getY() <= 0.5) {
            return;
        }

        // 取消拆半砖，改为拆上方方块。
        // 这里必须走 ServerPlayerGameMode#destroyBlock（原版玩家破坏路径）：它不会广播 2001 事件，
        // 所以破坏音效/粒子只由客户端预测生成一次；同时保留工具耐久、掉落物、统计等原版行为。
        // 早前用的 Level#destroyBlock 是非玩家破坏路径，会额外广播一次 2001，导致音效和粒子各播两遍。
        event.setCanceled(true);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.gameMode.destroyBlock(pos.above());
        }
    }
}
