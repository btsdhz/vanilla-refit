package com.example.myfirstmod.mixin;

import com.example.myfirstmod.config.BtsdhzConfig;
import com.example.myfirstmod.util.ModBlockStateProperties;
import com.example.myfirstmod.util.SlabSupport;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 普通火把、灵魂火把（都是 TorchBlock 实例）放置时，
 * 新增 btsdhz_on_slab 属性并判断其下方是否为下台阶。
 *
 * 只影响“竖立在地面上”的火把（TorchBlock），
 * 墙火把（WallTorchBlock）、红石火把（RedstoneTorchBlock）不受影响。
 */
@Mixin(Block.class)
public abstract class BlockOnSlabStateMixin {

    // 给横放火把增加 btsdhz_on_slab 属性
    @Inject(method = "createBlockStateDefinition", at = @At("RETURN"), remap = false)
    private void btsdhz_original$addOnSlab(StateDefinition.Builder<Block, BlockState> builder, CallbackInfo ci) {
        if ((Object)this instanceof TorchBlock && !((Object)this instanceof WallTorchBlock)) {
            builder.add(ModBlockStateProperties.ON_SLAB);
        }
    }

    // 放置时，根据下方方块是否为下台阶，设置 ON_SLAB
    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$torchOnPlacement(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
        // 只有竖着放的火把跟着支撑面下移；墙火把（含墙红石火把）贴的是侧面，不参与
        if (!SlabSupport.isStandingTorch((Block) (Object) this)) {
            return;
        }
        BlockState state = cir.getReturnValue();
        if (state == null) {
            return;
        }
        // 下方是普通下半台阶，或下方是被本模组整体下移了半格的方块（下台阶上的栅栏/墙）时，
        // 火把都要跟着下移半格，否则会悬空在支撑物的上表面上。
        boolean onSlab = BtsdhzConfig.TORCH_LANTERN_ON_SLAB.get()
                && (SlabSupport.isBottomSlab(context.getLevel(), context.getClickedPos().below())
                    || SlabSupport.isLoweredBlock(context.getLevel(), context.getClickedPos().below()));
        cir.setReturnValue(state.setValue(ModBlockStateProperties.ON_SLAB, onSlab));
    }
}
