package com.example.myfirstmod.mixin;

import com.example.myfirstmod.client.model.VerticalSlabRuntimeModel;
import com.example.myfirstmod.util.ModBlockStateProperties;
import com.example.myfirstmod.util.VerticalSlabMode;
import java.util.Map;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import com.example.myfirstmod.client.model.RuntimeVerticalSlabCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 客户端运行期支持其它模组的“原版基底台阶”：当某个台阶的竖放状态
 * （btsdhz_mode=vertical_*）没有显式 blockstate 变体、被解析成平放模型时，
 * 用该方块自身的平放模型旋成竖放模型呈现。
 *
 * <p>通过 {@link BlockModelShaper#getBlockModel(BlockState)} 拦截：只有
 * “竖放状态解析到的模型 == 同 TYPE 的平放模型”（即部分匹配、说明该模组没有提供
 * 竖放变体）时才包装；已经存在专属竖放模型的（本模组的石头/木质/砂岩等）保持原样。
 */
@Mixin(BlockModelShaper.class)
public abstract class RuntimeVerticalSlabModelMixin {

    @Shadow(remap = false)
    private Map<BlockState, BakedModel> modelByStateCache;

    @Inject(method = "getBlockModel", at = @At("HEAD"), cancellable = true, remap = false)
    private void btsdhz_original$getBlockModel(BlockState state, CallbackInfoReturnable<BakedModel> cir) {
        if (!(state.getBlock() instanceof SlabBlock)
                || !state.hasProperty(ModBlockStateProperties.MODE)) {
            return;
        }
        VerticalSlabMode mode = state.getValue(ModBlockStateProperties.MODE);
        if (mode == VerticalSlabMode.SLAB) {
            return;
        }

        BakedModel cached = RuntimeVerticalSlabCache.get(state);
        if (cached != null) {
            cir.setReturnValue(cached);
            return;
        }

        BlockState flatState = state.setValue(ModBlockStateProperties.MODE, VerticalSlabMode.SLAB);
        BakedModel flat = modelByStateCache.get(flatState);
        if (flat == null) {
            return;
        }
        BakedModel existing = modelByStateCache.get(state);
        if (existing != null && existing != flat) {
            // 该台阶已有专属竖放模型（本模组自带的/手工的），不覆盖
            return;
        }

        BakedModel vertical = new VerticalSlabRuntimeModel(flat, mode);
        RuntimeVerticalSlabCache.put(state, vertical);
        cir.setReturnValue(vertical);
    }
}
