package com.example.myfirstmod.mixin;

import com.example.myfirstmod.client.ModelKeyPruner;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code ModelKeyPruner} 把"每个状态一条顶层模型位置"的冗余键删了，这里补一条兜底：
 * 如果按状态 MRL 查不到（说明那条键被我们删了），就把属性串解析回状态、走
 * {@code BlockModelShaper.getBlockModel(state)}（运行期真正用的那条路）返回同一个模型。
 *
 * <p>只在**查不到**的时候才动，所以正常命中的调用、物品栏模型、独立模型一律不受影响。
 */
@Mixin(ModelManager.class)
public abstract class ModelManagerGetModelMixin {

    @Inject(method = "getModel", at = @At("RETURN"), cancellable = true, remap = false)
    private void btsdhz_original$resolvePrunedStateKey(ModelResourceLocation location,
                                                       CallbackInfoReturnable<BakedModel> cir) {
        ModelManager self = (ModelManager) (Object) this;
        if (cir.getReturnValue() != self.getMissingModel()) {
            return;   // 命中了就不用管
        }
        BlockState state = ModelKeyPruner.stateFromVariant(location);
        if (state == null) {
            return;
        }
        BakedModel resolved = self.getBlockModelShaper().getBlockModel(state);
        if (resolved != null && resolved != self.getMissingModel()) {
            cir.setReturnValue(resolved);
        }
    }
}
