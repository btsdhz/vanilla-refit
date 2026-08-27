package com.example.myfirstmod.event;

import com.example.myfirstmod.util.ModBlockStateProperties;
import com.example.myfirstmod.util.VerticalSlabMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 半砖上的“舒适框”改判：左键点到带下移火把/灯笼的半砖时，拆掉上方火把而不是半砖。
 *
 * 当普通下半台阶上方有一个下移（ON_SLAB=true）的火把/灯笼时，半砖的拾取形状
 * 会并入一个火把立柱宽的舒适框（见 SlabBlockMixin），从而让“瞄准火把底座”也能命中半砖。
 * 这里再把这次拆半砖的操作改判到上方的火把/灯笼——避免误拆半砖。
 *
 * <p>规则：半砖上方有下移火把/灯笼时，左键点半砖的任意区域都会先拆掉上方的火把/灯笼
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
        if (!isBottomSlabWithLoweredTorchAbove(event.getLevel(), pos, state)) {
            return;
        }

        // 取消拆半砖，改为拆上方火把/灯笼（会掉落对应物品）
        event.setCanceled(true);
        boolean creative = event.getEntity() instanceof Player p && p.getAbilities().instabuild;
        event.getLevel().destroyBlock(pos.above(), !creative);
    }

    private static boolean isBottomSlabWithLoweredTorchAbove(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof SlabBlock)
                || !state.hasProperty(ModBlockStateProperties.MODE)
                || state.getValue(ModBlockStateProperties.MODE) != VerticalSlabMode.SLAB
                || !state.hasProperty(SlabBlock.TYPE)
                || state.getValue(SlabBlock.TYPE) != SlabType.BOTTOM) {
            return false;
        }
        BlockState above = level.getBlockState(pos.above());
        Block b = above.getBlock();
        boolean isTorch = b instanceof TorchBlock && !(b instanceof WallTorchBlock);
        boolean isLantern = b instanceof LanternBlock;
        return (isTorch || isLantern)
                && above.hasProperty(ModBlockStateProperties.ON_SLAB)
                && above.getValue(ModBlockStateProperties.ON_SLAB);
    }
}
