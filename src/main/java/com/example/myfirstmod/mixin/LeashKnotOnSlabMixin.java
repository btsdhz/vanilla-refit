package com.example.myfirstmod.mixin;

import com.example.myfirstmod.util.FenceKnotOffset;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 绕绳结跟着栅栏的贴台阶位移一起走。
 *
 * <p>原版把绳结摆在“方块中心 + 0.375”，它不知道栅栏被本模组下移/上移了半格。
 * 这里在原版算完之后再叠加同样的位移：位置、判定框、拴绳握绳点会一起跟着动，
 * 所以动物拴绳（原版）与栅栏之间的拴绳（本模组）都能对上栅栏，而不是停在原格中心。</p>
 */
@Mixin(LeashFenceKnotEntity.class)
public abstract class LeashKnotOnSlabMixin {

    @Inject(method = "recalculateBoundingBox", at = @At("TAIL"), remap = false)
    private void btsdhz_original$shiftForSlabOffset(CallbackInfo ci) {
        LeashFenceKnotEntity self = (LeashFenceKnotEntity) (Object) this;
        double dy = FenceKnotOffset.verticalOffset(self.level(), self.getPos());
        if (dy == 0.0) {
            return;
        }
        double x = self.getX();
        double y = self.getY();
        double z = self.getZ();
        self.setPosRaw(x, y + dy, z);
        self.setBoundingBox(self.getBoundingBox().move(0.0, dy, 0.0));
    }
}
