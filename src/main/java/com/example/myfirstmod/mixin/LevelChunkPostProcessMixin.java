package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.PaneConnection;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 区块生成后，把里面的玻璃板/铁栏杆再刷到稳定状态。
 *
 * 原版的后处理只对每个标记过的位置按任意顺序跑一次 updateFromNeighbourShapes；而本模组的规则
 * 会读邻居的属性，顺序不对时先算的方块就停在“邻居还没修正”的结果上。这里在原版后处理结束后
 * 再做一次局部收敛（见 PaneConnection.refreshChunk），保证世界生成出来的玻璃板/铁栏杆全部正确。
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkPostProcessMixin {

    @Inject(method = "postProcessGeneration", at = @At("HEAD"), remap = false)
    private void btsdhz_original$beginPostProcess(CallbackInfo ci) {
        PaneConnection.beginPostProcess();
    }

    @Inject(method = "postProcessGeneration", at = @At("RETURN"), remap = false)
    private void btsdhz_original$refreshPanesAtEnd(CallbackInfo ci) {
        LevelChunk self = (LevelChunk) (Object) this;
        if (PaneConnection.endPostProcess()) {
            PaneConnection.refreshChunk(self, self.getLevel());
        }
    }
}
