package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.PaneCornerSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让玻璃板/铁栏杆走一次原版的“世界生成后处理”。
 *
 * 世界生成放置方块走的是 LevelGenRegion.setBlock，既不会调用放置逻辑，也不会触发邻居更新，
 * 因此自然生成的玻璃板/铁栅栏（例如末地黑曜石柱上的铁栏杆）会保持原版那套
 * “只有东西南北”的属性，剩下的 8 个部件属性全是假，看起来就只有下半截。
 *
 * 原版为此提供了后处理：标记过的位置会在区块生成结束时由 LevelChunk.postProcessGeneration
 * 依次调用 Block.updateFromNeighbourShapes，从而走到 IronBarsBlock.updateShape，
 * 于是本模组就能在那里把 12 个部件属性重算一遍。
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStatePostProcessMixin {

    @Inject(method = "hasPostProcess", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$hasPostProcess(BlockGetter level, BlockPos pos,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (PaneCornerSupport.isSupportedPane((BlockState) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}
