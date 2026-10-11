package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.FenceKnotOffset;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 栅栏的贴台阶状态可能**在绳结已经存在之后**才变化（先拴好，再在栅栏上下放/拆台阶），
 * 那时没有任何一次 {@code recalculateBoundingBox} 会把绳结搬到新位置。
 *
 * <p>所以挂在所有挂墙实体每 tick 的末尾，给绕绳结做一次“位置对不上就重摆”的自检。
 * 两端都跑：服务端的判定框、客户端的绳结与拴绳渲染都由各自这一格的方块状态现算，
 * 不依赖位置同步包（绳结实体的同步间隔是 MAX_VALUE，本来就不发位置）。</p>
 */
@Mixin(net.minecraft.world.entity.decoration.BlockAttachedEntity.class)
public abstract class BlockAttachedKnotOffsetMixin {

    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void btsdhz_original$refreshKnotOffset(CallbackInfo ci) {
        if ((Object) this instanceof LeashFenceKnotEntity knot) {
            FenceKnotOffset.refresh(knot);
        }
    }
}
