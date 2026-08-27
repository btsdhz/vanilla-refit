package com.example.myfirstmod.mixin;

import com.example.myfirstmod.config.BtsdhzConfig;
import com.example.myfirstmod.util.ModBlockStateProperties;
import com.example.myfirstmod.util.VerticalSlabMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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
        if (!((Object)this instanceof TorchBlock) || ((Object)this instanceof WallTorchBlock)) {
            return;
        }
        BlockState state = cir.getReturnValue();
        if (state == null) {
            return;
        }
        boolean onSlab = BtsdhzConfig.TORCH_LANTERN_ON_SLAB.get()
                && isBottomSlab(context.getLevel(), context.getClickedPos());
        cir.setReturnValue(state.setValue(ModBlockStateProperties.ON_SLAB, onSlab));
    }

    // 判断 pos 上方的方块下方的方块是否为“下台阶”（普通水平下半台阶，非竖台阶、非双台阶）
    @Unique
    private boolean isBottomSlab(BlockGetter level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return below.getBlock() instanceof SlabBlock
                && below.hasProperty(ModBlockStateProperties.MODE)
                && below.getValue(ModBlockStateProperties.MODE) == VerticalSlabMode.SLAB
                && below.hasProperty(SlabBlock.TYPE)
                && below.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
    }
}
