package com.example.myfirstmod.event;

import com.example.myfirstmod.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
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
 * <p>规则：半砖上方有下移方块时，左键点半砖的任意区域都会先拆掉上方的方块
 * （先清掉上面的东西才能拆半砖），这符合直觉，也无需细分命中位置。
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

        // 取消拆半砖，改为拆上方方块（会掉落对应物品）
        event.setCanceled(true);
        boolean creative = event.getEntity() instanceof Player p && p.getAbilities().instabuild;
        event.getLevel().destroyBlock(pos.above(), !creative);
    }
}
